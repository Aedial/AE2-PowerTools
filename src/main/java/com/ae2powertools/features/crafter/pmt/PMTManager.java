package com.ae2powertools.features.crafter.pmt;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.items.IItemHandler;

import appeng.api.config.Upgrades;
import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.implementations.items.IUpgradeModule;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.Platform;
import appeng.util.inv.IAEAppEngInventory;
import appeng.util.inv.InvOperation;
import appeng.util.inv.filter.IAEItemFilter;


/**
 * Manages Pattern Multi-Tool inventory for the AutoCrafter GUI.
 * <p>
 * This class provides a self-contained PMT implementation that doesn't rely on NAE2's mixins.
 * It finds the PMT in the player's inventory, manages its pattern storage, and handles
 * capacity upgrades.
 * <p>
 * The PMT has:
 * - 36 pattern slots (4 columns x 9 rows)
 * - Column 0 is always enabled
 * - Columns 1-3 are enabled by capacity upgrades
 * - 3 upgrade slots for capacity upgrades
 */
public class PMTManager implements IAEAppEngInventory {

    // PMT inventory sizes
    public static final int PATTERN_SLOTS = 36;  // 4 columns x 9 rows
    public static final int UPGRADE_SLOTS = 3;
    public static final int ROWS = 9;
    public static final int COLUMNS = 4;

    // Mod IDs
    private static final String NAE2_MOD_ID = "nae2";
    private static final String BAUBLES_MOD_ID = "baubles";

    // The PMT ItemStack from player inventory
    private final ItemStack pmtStack;

    // Internal inventories (loaded from NBT)
    private final AppEngInternalInventory patternInventory;
    private final AppEngInternalInventory upgradeInventory;

    // Cached upgrade count
    private int installedCapacityUpgrades = 0;

    /**
     * Creates a PMTManager for the given PMT ItemStack.
     * Private - use findPMT() to get a manager.
     */
    private PMTManager(ItemStack pmtStack) {
        this.pmtStack = pmtStack;
        this.patternInventory = new AppEngInternalInventory(this, PATTERN_SLOTS);
        this.patternInventory.setFilter(new PatternFilter());
        this.upgradeInventory = new AppEngInternalInventory(this, UPGRADE_SLOTS);

        // Load data from NBT
        loadFromNBT();
    }

    /**
     * Checks if NAE2 is loaded.
     */
    public static boolean isNAE2Loaded() {
        return Loader.isModLoaded(NAE2_MOD_ID);
    }

    /**
     * Checks if Baubles is loaded.
     */
    public static boolean isBaublesLoaded() {
        return Loader.isModLoaded(BAUBLES_MOD_ID);
    }

    /**
     * Finds a Pattern Multi-Tool in the player's inventory.
     * Searches baubles inventory first (if available), then player inventory.
     * 
     * @param player The player to search
     * @return PMTManager if found, null otherwise
     */
    @Nullable
    public static PMTManager findPMT(EntityPlayer player) {
        if (!isNAE2Loaded()) return null;

        // Search baubles inventory first (if baubles is loaded)
        if (isBaublesLoaded()) {
            ItemStack baublesStack = findPMTInBaubles(player);
            if (baublesStack != null && !baublesStack.isEmpty()) {
                return new PMTManager(baublesStack);
            }
        }

        // Search player inventory
        InventoryPlayer inv = player.inventory;
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (isPMTItem(stack)) return new PMTManager(stack);
        }

        return null;
    }

    /**
     * Searches the Baubles inventory for a PMT.
     * Uses @Optional.Method to avoid class loading issues when Baubles isn't installed.
     */
    @Nullable
    @Optional.Method(modid = "baubles")
    private static ItemStack findPMTInBaubles(EntityPlayer player) {
        try {
            IInventory baublesInv = getBaublesInventory(player);

            for (int i = 0; i < baublesInv.getSizeInventory(); i++) {
                ItemStack stack = baublesInv.getStackInSlot(i);
                if (isPMTItem(stack)) return stack;
            }
        } catch (NoSuchMethodError ignored) {
            // Baubles API changed, ignore
        }

        return null;
    }

    /**
     * Gets the Baubles inventory for the player.
     * Separate method to isolate Baubles API references.
     */
    @SuppressWarnings("deprecation")
    @Optional.Method(modid = "baubles")
    private static IInventory getBaublesInventory(EntityPlayer player) {
        return baubles.api.BaublesApi.getBaubles(player);
    }

    /**
     * Checks if an ItemStack is a Pattern Multi-Tool.
     */
    public static boolean isPMTItem(ItemStack stack) {
        if (stack.isEmpty()) return false;

        // Check by class name to avoid hard dependency on NAE2
        String className = stack.getItem().getClass().getName();
        return className.equals("co.neeve.nae2.common.items.patternmultitool.ToolPatternMultiTool");
    }

    /**
     * Loads pattern and upgrade inventories from the PMT's NBT data.
     */
    private void loadFromNBT() {
        if (!pmtStack.hasTagCompound()) return;

        NBTTagCompound data = pmtStack.getTagCompound();
        if (data == null) return;

        // Remove "Size" tag that can cause issues (same as ObjPatternMultiTool does)
        if (data.hasKey("inv")) {
            NBTTagCompound invTag = data.getCompoundTag("inv");
            invTag.removeTag("Size");
            patternInventory.readFromNBT(data, "inv");
        }

        if (data.hasKey("upgrades")) {
            NBTTagCompound upgradesTag = data.getCompoundTag("upgrades");
            upgradesTag.removeTag("Size");
            upgradeInventory.readFromNBT(data, "upgrades");
        }

        // Calculate installed capacity upgrades
        updateCapacityUpgradeCount();
    }

    /**
     * Saves changes back to the PMT's NBT data.
     */
    public void saveChanges() {
        NBTTagCompound data = Platform.openNbtData(pmtStack);
        patternInventory.writeToNBT(data, "inv");
        upgradeInventory.writeToNBT(data, "upgrades");
    }

    /**
     * Updates the cached count of installed capacity upgrades.
     */
    private void updateCapacityUpgradeCount() {
        installedCapacityUpgrades = 0;

        for (int i = 0; i < upgradeInventory.getSlots(); i++) {
            ItemStack upgrade = upgradeInventory.getStackInSlot(i);
            if (upgrade.isEmpty()) continue;
            if (!(upgrade.getItem() instanceof IUpgradeModule)) continue;

            // Check if it's a capacity upgrade using AE2's IUpgradeModule interface
            IUpgradeModule upgradeModule = (IUpgradeModule) upgrade.getItem();
            if (upgradeModule.getType(upgrade) == Upgrades.CAPACITY) {
                installedCapacityUpgrades += upgrade.getCount();
            }
        }

        // Cap at 3 (maximum capacity upgrade count)
        installedCapacityUpgrades = Math.min(installedCapacityUpgrades, 3);
    }

    /**
     * Checks if this manager has a valid PMT.
     */
    public boolean hasPMT() {
        return !pmtStack.isEmpty() && isPMTItem(pmtStack);
    }

    /**
     * Gets the number of installed capacity upgrades (0-3).
     */
    public int getInstalledCapacityUpgrades() {
        return installedCapacityUpgrades;
    }

    /**
     * Checks if a column is enabled based on capacity upgrades.
     * Column 0 is always enabled. Columns 1-3 require upgrades.
     * 
     * @param column Column index (0-3)
     * @return true if column is enabled
     */
    public boolean isColumnEnabled(int column) {
        if (column == 0) return true;
        return column <= installedCapacityUpgrades;
    }

    /**
     * Gets the pattern inventory.
     */
    public IItemHandler getPatternInventory() {
        return patternInventory;
    }

    /**
     * Gets the upgrade inventory.
     */
    public IItemHandler getUpgradeInventory() {
        return upgradeInventory;
    }

    /**
     * Gets the PMT ItemStack.
     */
    public ItemStack getPMTStack() {
        return pmtStack;
    }

    // ==================== IAEAppEngInventory ====================

    @Override
    public void onChangeInventory(IItemHandler inv, int slot, InvOperation mc, ItemStack removedStack, ItemStack newStack) {
        // Update upgrade count when upgrade inventory changes
        if (inv == upgradeInventory) updateCapacityUpgradeCount();

        // Save changes back to NBT
        saveChanges();
    }

    // ==================== Pattern Filter ====================

    /**
     * Filter that only allows patterns in the pattern inventory.
     */
    private static class PatternFilter implements IAEItemFilter {
        @Override
        public boolean allowExtract(IItemHandler inv, int slot, int amount) {
            return true;
        }

        @Override
        public boolean allowInsert(IItemHandler inv, int slot, ItemStack stack) {
            return stack.getItem() instanceof ICraftingPatternItem;
        }
    }
}
