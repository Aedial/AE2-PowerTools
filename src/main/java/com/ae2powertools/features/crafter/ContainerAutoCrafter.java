package com.ae2powertools.features.crafter;

import javax.annotation.Nonnull;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import net.minecraftforge.items.IItemHandler;

import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.container.AEBaseContainer;
import appeng.container.guisync.GuiSync;
import appeng.container.slot.AppEngSlot;
import appeng.container.slot.SlotFake;
import appeng.util.Platform;

import com.ae2powertools.items.ItemCrafterSpeedUpgrade;
import com.ae2powertools.network.PowerToolsNetwork;


/**
 * Container for the AE2 AutoCrafter GUI.
 * Manages the pattern slot and catalyst inventory for a single recipe entry.
 */
public class ContainerAutoCrafter extends AEBaseContainer {

    private final TileAutoCrafter tile;
    private final InventoryPlayer playerInv;

    /**
     * Tick counter for periodic tile sync while GUI is open.
     * This ensures metrics data (occupancy, error rate) sync in real-time.
     */
    private int tickCounter = 0;
    private static final int SYNC_INTERVAL = 10; // Sync tile data every 10 ticks (0.5 sec)

    /**
     * Currently viewed entry index (0-11).
     * Synced from tile via GuiSync.
     */
    @GuiSync(3)
    public int syncCurrentPage = 0;

    /**
     * Whether we're in overview mode.
     */
    @GuiSync(0)
    public boolean overviewMode = false;

    @GuiSync(1)
    public int syncSpeedTicks = TileAutoCrafter.DEFAULT_SPEED_TICKS;

    @GuiSync(2)
    public int syncBatchSize = TileAutoCrafter.DEFAULT_BATCH_SIZE;

    /**
     * Effective batch size (base * user batch * upgrade multiplier).
     * This is the actual number of crafts performed per operation.
     * Synced from server since it depends on upgrades and config.
     */
    @GuiSync(4)
    public int syncEffectiveBatchSize = TileAutoCrafter.DEFAULT_BATCH_SIZE;

    // Slots for the current entry
    private SlotPattern patternSlot;
    private SlotCatalyst[] catalystSlots;
    private SlotUpgrade[] upgradeSlots;

    // Upgrade slots
    private static final int UPGRADE_START_X = 187;
    private static final int UPGRADE_START_Y = 8;
    private static final int UPGRADE_SLOT_SIZE = 18;

    public ContainerAutoCrafter(InventoryPlayer playerInv, TileAutoCrafter tile) {
        super(playerInv, tile, null);
        this.tile = tile;
        this.playerInv = playerInv;

        // Initialize synced values from tile immediately (server-side)
        // This ensures the first sync sends correct values to client
        if (Platform.isServer()) {
            this.syncSpeedTicks = tile.getSpeedTicks();
            this.syncBatchSize = tile.getBatchSize();
        }

        // Get initial page from tile
        int initialPage = tile.getCurrentPage();

        // Pattern slot - uses tile + entryIndex directly
        this.patternSlot = new SlotPattern(this, tile, initialPage, 17, 43);
        addSlotToContainer(patternSlot);

        // Catalyst slots (9 slots) - 1:1 mapping with crafting grid
        this.catalystSlots = new SlotCatalyst[CrafterEntry.CATALYST_SLOTS];
        for (int i = 0; i < CrafterEntry.CATALYST_SLOTS; i++) {
            int x = 8 + (i % 9) * 18;
            int y = 90;
            catalystSlots[i] = new SlotCatalyst(this, tile, i, initialPage, x, y);
            addSlotToContainer(catalystSlots[i]);
        }

        // Upgrade slots (4 slots) - uses tile directly (not per-entry)
        this.upgradeSlots = new SlotUpgrade[TileAutoCrafter.UPGRADE_SLOTS];
        for (int i = 0; i < TileAutoCrafter.UPGRADE_SLOTS; i++) {
            int x = UPGRADE_START_X;
            int y = UPGRADE_START_Y + i * UPGRADE_SLOT_SIZE;
            upgradeSlots[i] = new SlotUpgrade(this, tile, i, x, y);
            addSlotToContainer(upgradeSlots[i]);
        }

        // Player inventory (starting at y=167)
        this.bindPlayerInventory(playerInv, 0, 167);
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();

        if (Platform.isServer()) {
            this.syncSpeedTicks = tile.getSpeedTicks();
            this.syncBatchSize = tile.getBatchSize();
            this.syncEffectiveBatchSize = tile.getEffectiveMaxBatchSize();

            int newPage = tile.getCurrentPage();
            if (newPage != this.syncCurrentPage) {
                this.syncCurrentPage = newPage;
                updateSlotsForCurrentPage();
            }

            // Periodically send full state packet for reliable sync.
            // This replaces the old markForUpdate() approach with packet-based sync.
            tickCounter++;
            if (tickCounter >= SYNC_INTERVAL) {
                tickCounter = 0;
                sendStatePacketToPlayer();
            }
        }
    }

    /**
     * Sends a full state sync packet to the player viewing this container.
     * This provides reliable, immediate sync for metrics and other data.
     */
    private void sendStatePacketToPlayer() {
        if (playerInv.player instanceof EntityPlayerMP) {
            PacketCrafterStateSync syncPacket = PacketCrafterStateSync.fromTile(tile);
            PowerToolsNetwork.INSTANCE.sendTo(syncPacket, (EntityPlayerMP) playerInv.player);
        }
    }

    public TileAutoCrafter getTile() {
        return tile;
    }

    public int getCurrentEntryIndex() {
        return syncCurrentPage;
    }

    public void setCurrentEntryIndex(int index) {
        if (index < 0 || index >= TileAutoCrafter.ENTRY_COUNT) return;

        this.syncCurrentPage = index;
        updateSlotsForCurrentPage();
    }

    public CrafterEntry getCurrentEntry() {
        return tile.getEntry(syncCurrentPage);
    }

    /**
     * Update slot entry references when page changes.
     */
    private void updateSlotsForCurrentPage() {
        patternSlot.setEntryIndex(syncCurrentPage);

        for (SlotCatalyst slot : catalystSlots) slot.setEntryIndex(syncCurrentPage);
    }

    @Override
    public ItemStack slotClick(int slotId, int dragType, ClickType clickType, EntityPlayer player) {
        if (slotId >= 0 && slotId < inventorySlots.size()) {
            Slot slot = inventorySlots.get(slotId);

            if (slot instanceof SlotPattern) {
                return handlePatternSlotClick((SlotPattern) slot, dragType, clickType, player);
            }

            if (slot instanceof SlotCatalyst) {
                return handleCatalystSlotClick((SlotCatalyst) slot, dragType, clickType, player);
            }

            if (slot instanceof SlotUpgrade) {
                return handleUpgradeSlotClick((SlotUpgrade) slot, dragType, clickType, player);
            }
        }

        return super.slotClick(slotId, dragType, clickType, player);
    }

    private ItemStack handlePatternSlotClick(SlotPattern slot, int dragType, ClickType clickType, EntityPlayer player) {
        CrafterEntry entry = getCurrentEntry();
        if (entry == null) return ItemStack.EMPTY;

        ItemStack held = player.inventory.getItemStack();

        // Right-click to extract
        if (dragType == 1 && clickType == ClickType.PICKUP) {
            if (entry.hasPattern()) {
                ItemStack pattern = entry.getPatternStack().copy();
                entry.setPatternStack(null);
                tile.simulatePattern(syncCurrentPage, null);

                if (held.isEmpty()) {
                    player.inventory.setItemStack(pattern);
                } else {
                    // Try to add to inventory
                    if (!player.inventory.addItemStackToInventory(pattern)) player.dropItem(pattern, false);
                }
                return ItemStack.EMPTY;
            }
            return ItemStack.EMPTY;
        }

        // Left-click to insert/swap
        if (clickType == ClickType.PICKUP) {
            if (held.isEmpty()) {
                // Extract existing pattern
                if (entry.hasPattern()) {
                    ItemStack pattern = entry.getPatternStack().copy();
                    entry.setPatternStack(null);
                    tile.simulatePattern(syncCurrentPage, null);
                    player.inventory.setItemStack(pattern);
                }
            } else {
                // Check if held item is a valid pattern
                if (isValidPattern(held)) {
                    ItemStack existing = entry.hasPattern() ? entry.getPatternStack().copy() : ItemStack.EMPTY;
                    ItemStack toInsert = held.splitStack(1);
                    entry.setPatternStack(toInsert);
                    tile.simulatePattern(syncCurrentPage, toInsert);

                    if (!existing.isEmpty()) {
                        if (held.isEmpty()) {
                            player.inventory.setItemStack(existing);
                        } else {
                            if (!player.inventory.addItemStackToInventory(existing)) {
                                player.dropItem(existing, false);
                            }
                        }
                    }
                }
            }
            return ItemStack.EMPTY;
        }

        // Shift-click
        if (clickType == ClickType.QUICK_MOVE) {
            if (entry.hasPattern()) {
                ItemStack pattern = entry.getPatternStack().copy();
                entry.setPatternStack(null);
                tile.simulatePattern(syncCurrentPage, null);

                if (!player.inventory.addItemStackToInventory(pattern)) player.dropItem(pattern, false);
            }
            return ItemStack.EMPTY;
        }

        return ItemStack.EMPTY;
    }

    /**
     * Handles catalyst slot click interactions.
     * 
     * Catalyst slots hold REUSABLE and DURABILITY items:
     * - Each slot holds exactly 1 item (crafting recipes use 1 item per slot)
     * - Only items matching the expected catalyst type can be inserted
     * - DURABILITY items: any item of the same type is valid (durability may vary)
     * - REUSABLE items: exact match required (same Item, metadata, NBT)
     */
    private ItemStack handleCatalystSlotClick(SlotCatalyst slot, int dragType, ClickType clickType, EntityPlayer player) {
        CrafterEntry entry = getCurrentEntry();
        if (entry == null) return ItemStack.EMPTY;

        int catalystIndex = slot.getCatalystIndex();
        ItemStack held = player.inventory.getItemStack();
        ItemStack existing = entry.getCatalystStack(catalystIndex);

        // Check what item is expected in this slot
        ItemStack expectedItem = getExpectedCatalystItem(catalystIndex);

        // Right-click to extract
        if (dragType == 1 && clickType == ClickType.PICKUP) {
            if (!existing.isEmpty()) {
                // Catalyst slots only hold 1 item, extract it
                if (held.isEmpty()) {
                    player.inventory.setItemStack(existing.copy());
                } else {
                    if (!player.inventory.addItemStackToInventory(existing.copy())) {
                        player.dropItem(existing.copy(), false);
                    }
                }
                entry.setCatalystStack(catalystIndex, ItemStack.EMPTY);
                tile.validateCatalysts(syncCurrentPage);
                tile.markDirty();
            }
            return ItemStack.EMPTY;
        }

        // Left-click to insert/swap
        if (clickType == ClickType.PICKUP) {
            if (held.isEmpty()) {
                // Extract all
                if (!existing.isEmpty()) {
                    player.inventory.setItemStack(existing.copy());
                    entry.setCatalystStack(catalystIndex, ItemStack.EMPTY);
                    tile.validateCatalysts(syncCurrentPage);
                    tile.markDirty();
                }
            } else {
                // If no expected item, this slot is not a catalyst slot - reject all items
                if (expectedItem.isEmpty()) return ItemStack.EMPTY;

                // Check if item matches expected catalyst
                boolean canInsert = isValidCatalystItem(held, expectedItem, catalystIndex);

                if (canInsert) {
                    // Swap: put held item in slot, return existing to player
                    ItemStack toInsert = held.copy();
                    toInsert.setCount(1); // Catalyst slots only accept 1 item
                    held.shrink(1);
                    
                    if (!existing.isEmpty()) {
                        // Return existing to player
                        if (held.isEmpty()) {
                            player.inventory.setItemStack(existing.copy());
                        } else if (!player.inventory.addItemStackToInventory(existing.copy())) {
                            player.dropItem(existing.copy(), false);
                        }
                    }
                    
                    entry.setCatalystStack(catalystIndex, toInsert);
                    tile.validateCatalysts(syncCurrentPage);
                    tile.markDirty();
                }
            }
            return ItemStack.EMPTY;
        }

        // Shift-click to move to player inventory
        if (clickType == ClickType.QUICK_MOVE) {
            if (!existing.isEmpty()) {
                if (!player.inventory.addItemStackToInventory(existing.copy())) {
                    player.dropItem(existing.copy(), false);
                }
                entry.setCatalystStack(catalystIndex, ItemStack.EMPTY);
                tile.validateCatalysts(syncCurrentPage);
                tile.markDirty();
            }
            return ItemStack.EMPTY;
        }

        return ItemStack.EMPTY;
    }

    /**
     * Handles upgrade slot click interactions.
     * 
     * Simple vanilla-style slot behavior:
     * - Only speed upgrades are accepted
     * - Only ONE speed upgrade can be installed across all slots
     * - Click with valid upgrade: insert or swap
     * - Click with empty hand: extract
     * - Shift-click: move to player inventory
     */
    private ItemStack handleUpgradeSlotClick(SlotUpgrade slot, int dragType, ClickType clickType, EntityPlayer player) {
        int upgradeIndex = slot.getUpgradeIndex();
        ItemStack held = player.inventory.getItemStack();
        ItemStack existing = tile.getUpgradeStack(upgradeIndex);

        // Shift-click: move existing upgrade to player inventory
        if (clickType == ClickType.QUICK_MOVE) {
            if (existing.isEmpty()) return ItemStack.EMPTY;

            ItemStack toMove = existing.copy();
            tile.setUpgradeStack(upgradeIndex, ItemStack.EMPTY);

            if (!player.inventory.addItemStackToInventory(toMove)) player.dropItem(toMove, false);

            return ItemStack.EMPTY;
        }

        // Regular click (left or right) - vanilla behavior: swap/insert/extract
        if (clickType != ClickType.PICKUP) return ItemStack.EMPTY;

        // Empty hand: extract existing upgrade
        if (held.isEmpty()) {
            if (existing.isEmpty()) return ItemStack.EMPTY;

            player.inventory.setItemStack(existing.copy());
            tile.setUpgradeStack(upgradeIndex, ItemStack.EMPTY);

            return ItemStack.EMPTY;
        }

        // Holding something - check if it's a valid speed upgrade
        if (!ItemCrafterSpeedUpgrade.isSpeedUpgrade(held)) return ItemStack.EMPTY;

        // Check if another slot already has a speed upgrade (only 1 allowed total)
        for (int i = 0; i < TileAutoCrafter.UPGRADE_SLOTS; i++) {
            if (i == upgradeIndex) continue;

            ItemStack otherSlot = tile.getUpgradeStack(i);
            if (ItemCrafterSpeedUpgrade.isSpeedUpgrade(otherSlot)) {
                // Already have a speed upgrade in another slot - not allowed
                // Player must remove it first
                return ItemStack.EMPTY;
            }
        }

        // Insert new upgrade, swap with existing if any
        ItemStack toInsert = held.splitStack(1);
        tile.setUpgradeStack(upgradeIndex, toInsert);

        if (!existing.isEmpty()) {
            // Put existing upgrade into player's hand (vanilla swap behavior)
            if (held.isEmpty()) {
                player.inventory.setItemStack(existing.copy());
            } else if (!player.inventory.addItemStackToInventory(existing.copy())) {
                    // Hand not empty after split, add to inventory or drop
                    player.dropItem(existing.copy(), false);
            }
        }

        return ItemStack.EMPTY;
    }

    /**
     * Checks if an item is valid for a catalyst slot.
     * Uses 1:1 slot mapping: catalyst slot index = recipe slot index.
     * For REUSABLE/DUPLICATION items, uses inclusive NBT matching.
     */
    private boolean isValidCatalystItem(ItemStack candidate, ItemStack expected, int catalystIndex) {
        if (candidate.isEmpty() || expected.isEmpty()) return true;

        CrafterEntry entry = getCurrentEntry();
        if (entry == null || !entry.hasValidRecipeInfo()) return true;

        CrafterRecipeInfo info = entry.getRecipeInfo();
        if (info == null) return true;

        // Find the ingredient whose slot index matches the catalyst index (1:1 mapping)
        for (CrafterRecipeInfo.IngredientInfo catalyst : info.getCatalystSlots()) {
            if (catalyst.getSlotIndex() == catalystIndex) {
                // For REUSABLE/DUPLICATION items, use inclusive NBT matching
                // The candidate must have at least the expected NBT but may have extra tags
                return areItemStacksMatchingInclusive(expected, candidate);
            }
        }

        // Slot is not a catalyst slot according to recipe, don't allow any item (shouldn't happen normally)
        return false;
    }

    /**
     * Checks if an item matches a requirement using inclusive NBT matching.
     * The requirement's NBT must be a subset of the actual item's NBT.
     * This allows items with additional NBT tags (e.g., from enchantments or mod data)
     * to still match the recipe requirement.
     * 
     * @param required The requirement (what the recipe expects)
     * @param actual The actual item (what the player provides)
     * @return true if actual matches required, including NBT subset check
     */
    private boolean areItemStacksMatchingInclusive(ItemStack required, ItemStack actual) {
        if (required.isEmpty() && actual.isEmpty()) return true;
        if (required.isEmpty() || actual.isEmpty()) return false;
        if (required.getItem() != actual.getItem()) return false;
        if (required.getMetadata() != actual.getMetadata()) return false;

        // Inclusive NBT check: required NBT must be subset of actual NBT
        NBTTagCompound requiredTag = required.getTagCompound();
        NBTTagCompound actualTag = actual.getTagCompound();

        // If requirement has no NBT, any item NBT is acceptable
        if (requiredTag == null || requiredTag.isEmpty()) return true;

        // If requirement has NBT but item has none, fail
        if (actualTag == null) return false;

        // Check that all keys in requiredTag exist in actualTag with same values
        return isNbtSubset(requiredTag, actualTag);
    }

    /**
     * Checks if 'subset' NBT is contained within 'superset' NBT.
     * All keys in subset must exist in superset with equal values.
     */
    private boolean isNbtSubset(NBTTagCompound subset, NBTTagCompound superset) {
        for (String key : subset.getKeySet()) {
            if (!superset.hasKey(key)) return false;
            if (subset.getTagId(key) != superset.getTagId(key)) return false;
            if (!subset.getTag(key).equals(superset.getTag(key))) return false;
        }

        return true;
    }

    /**
     * Gets the expected catalyst item for a slot based on recipe info.
     * Uses 1:1 slot mapping: catalyst slot index = recipe slot index.
     */
    private ItemStack getExpectedCatalystItem(int catalystIndex) {
        CrafterEntry entry = getCurrentEntry();
        if (entry == null || !entry.hasValidRecipeInfo()) return ItemStack.EMPTY;

        CrafterRecipeInfo info = entry.getRecipeInfo();
        if (info == null) return ItemStack.EMPTY;

        // Find the catalyst whose slot index matches the catalyst inventory index (1:1 mapping)
        for (CrafterRecipeInfo.IngredientInfo catalyst : info.getCatalystSlots()) {
            if (catalyst.getSlotIndex() == catalystIndex) {
                return catalyst.getItem() != null ? catalyst.getItem().createItemStack() : ItemStack.EMPTY;
            }
        }

        return ItemStack.EMPTY;
    }

    private boolean isValidPattern(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (!(stack.getItem() instanceof ICraftingPatternItem)) return false;

        ICraftingPatternItem patternItem = (ICraftingPatternItem) stack.getItem();
        ICraftingPatternDetails details = patternItem.getPatternForItem(stack, tile.getWorld());

        // Only crafting patterns, not processing patterns
        return details != null && details.isCraftable();
    }

    /**
     * Gets slot indices:
     * - Slot 0: Pattern
     * - Slots 1-9: Catalysts
     * - Slots 10-13: Upgrades
     * - Slots 14+: Player inventory
     */
    private static final int SLOT_PATTERN = 0;
    private static final int SLOT_CATALYST_START = 1;
    private static final int SLOT_UPGRADE_START = 10;
    private static final int SLOT_PLAYER_START = 14;

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int slotIndex) {
        Slot slot = inventorySlots.get(slotIndex);
        if (slot == null || !slot.getHasStack()) return ItemStack.EMPTY;

        ItemStack stack = slot.getStack();
        ItemStack original = stack.copy();

        // From player inventory
        if (slotIndex >= SLOT_PLAYER_START) {
            // Try pattern slot first
            if (isValidPattern(stack)) {
                CrafterEntry entry = getCurrentEntry();
                if (entry != null && !entry.hasPattern()) {
                    ItemStack toInsert = stack.splitStack(1);
                    entry.setPatternStack(toInsert);
                    tile.simulatePattern(syncCurrentPage, toInsert);
                    tile.markDirty();
                    return original;
                }
            }

            // Try upgrade slots (speed upgrades) - only if no speed upgrade already installed
            if (ItemCrafterSpeedUpgrade.isSpeedUpgrade(stack)) {
                // Check if any slot already has a speed upgrade
                boolean hasSpeedUpgrade = false;
                for (int i = 0; i < TileAutoCrafter.UPGRADE_SLOTS; i++) {
                    if (ItemCrafterSpeedUpgrade.isSpeedUpgrade(tile.getUpgradeStack(i))) {
                        hasSpeedUpgrade = true;
                        break;
                    }
                }

                // Only insert if no speed upgrade already installed
                if (!hasSpeedUpgrade) {
                    for (int i = 0; i < TileAutoCrafter.UPGRADE_SLOTS; i++) {
                        if (tile.getUpgradeStack(i).isEmpty()) {
                            ItemStack toInsert = stack.splitStack(1);
                            tile.setUpgradeStack(i, toInsert);
                            return original;
                        }
                    }
                }
            }

            // Try catalyst slots (only if item matches expected for that slot)
            CrafterEntry entry = getCurrentEntry();
            if (entry != null && entry.hasValidRecipeInfo()) {
                CrafterRecipeInfo info = entry.getRecipeInfo();
                if (info != null) {
                    for (CrafterRecipeInfo.IngredientInfo catalyst : info.getCatalystSlots()) {
                        int slotIdx = catalyst.getSlotIndex();
                        if (slotIdx >= 0 && slotIdx < CrafterEntry.CATALYST_SLOTS) {
                            if (entry.getCatalystStack(slotIdx).isEmpty()) {
                                ItemStack expected = catalyst.getItem() != null
                                        ? catalyst.getItem().createItemStack()
                                        : ItemStack.EMPTY;
                                if (!expected.isEmpty() && isValidCatalystItem(stack, expected, slotIdx)) {
                                    ItemStack toInsert = stack.splitStack(1);
                                    entry.setCatalystStack(slotIdx, toInsert);
                                    tile.validateCatalysts(syncCurrentPage);
                                    tile.markDirty();
                                    return original;
                                }
                            }
                        }
                    }
                }
            }

            return ItemStack.EMPTY;
        }

        // From container slots to player inventory
        if (slotIndex == SLOT_PATTERN) {
            CrafterEntry entry = getCurrentEntry();
            if (entry != null && entry.hasPattern()) {
                ItemStack pattern = entry.getPatternStack().copy();
                if (player.inventory.addItemStackToInventory(pattern)) {
                    entry.setPatternStack(null);
                    tile.simulatePattern(syncCurrentPage, null);
                    tile.markDirty();
                    return pattern;
                }
            }
        } else if (slotIndex >= SLOT_CATALYST_START && slotIndex < SLOT_UPGRADE_START) {
            int catalystIdx = slotIndex - SLOT_CATALYST_START;
            CrafterEntry entry = getCurrentEntry();
            if (entry != null) {
                ItemStack catalyst = entry.getCatalystStack(catalystIdx);
                if (!catalyst.isEmpty()) {
                    if (player.inventory.addItemStackToInventory(catalyst.copy())) {
                        entry.setCatalystStack(catalystIdx, ItemStack.EMPTY);
                        tile.validateCatalysts(syncCurrentPage);
                        tile.markDirty();
                        return catalyst;
                    }
                }
            }
        } else if (slotIndex >= SLOT_UPGRADE_START && slotIndex < SLOT_PLAYER_START) {
            int upgradeIdx = slotIndex - SLOT_UPGRADE_START;
            ItemStack upgrade = tile.getUpgradeStack(upgradeIdx);
            if (!upgrade.isEmpty()) {
                if (player.inventory.addItemStackToInventory(upgrade.copy())) {
                    tile.setUpgradeStack(upgradeIdx, ItemStack.EMPTY);
                    return upgrade;
                }
            }
        }

        return ItemStack.EMPTY;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return tile.getWorld().getTileEntity(tile.getPos()) == tile &&
                player.getDistanceSq(tile.getPos().getX() + 0.5,
                        tile.getPos().getY() + 0.5,
                        tile.getPos().getZ() + 0.5) <= 64.0;
    }

    // ==================== CUSTOM SLOTS ====================

    /**
     * IItemHandler for pattern slot that reads/writes from tile's entry.
     * Properly implements insertItem/extractItem for working slot mechanics.
     */
    private static class PatternItemHandler implements IItemHandler {
        private final ContainerAutoCrafter container;
        private final TileAutoCrafter tile;
        private int entryIndex;

        PatternItemHandler(ContainerAutoCrafter container, TileAutoCrafter tile, int entryIndex) {
            this.container = container;
            this.tile = tile;
            this.entryIndex = entryIndex;
        }

        void setEntryIndex(int index) {
            this.entryIndex = index;
        }

        private CrafterEntry getEntry() {
            return tile.getEntry(entryIndex);
        }

        @Override
        public int getSlots() {
            return 1;
        }

        @Nonnull
        @Override
        public ItemStack getStackInSlot(int slot) {
            CrafterEntry entry = getEntry();
            return entry != null && entry.hasPattern() ? entry.getPatternStack() : ItemStack.EMPTY;
        }

        @Nonnull
        @Override
        public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
            if (!isItemValid(slot, stack)) return stack;

            CrafterEntry entry = getEntry();
            if (entry == null || entry.hasPattern()) return stack;

            if (!simulate) {
                ItemStack toInsert = stack.copy();
                toInsert.setCount(1);
                entry.setPatternStack(toInsert);
                tile.simulatePattern(entryIndex, toInsert);
                tile.markDirty();
            }

            ItemStack remaining = stack.copy();
            remaining.shrink(1);
            return remaining;
        }

        @Nonnull
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            CrafterEntry entry = getEntry();
            if (entry == null || !entry.hasPattern()) return ItemStack.EMPTY;

            ItemStack pattern = entry.getPatternStack().copy();
            if (!simulate) {
                entry.setPatternStack(null);
                tile.simulatePattern(entryIndex, null);
                tile.markDirty();
            }

            return pattern;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            return container.isValidPattern(stack);
        }
    }

    /**
     * IItemHandler for catalyst slot that reads/writes from tile's entry.
     * Strictly validates items against the recipe's expected catalyst for this slot.
     */
    private static class CatalystItemHandler implements IItemHandler {
        private final ContainerAutoCrafter container;
        private final TileAutoCrafter tile;
        private final int catalystIndex;
        private int entryIndex;

        CatalystItemHandler(ContainerAutoCrafter container, TileAutoCrafter tile, int catalystIndex, int entryIndex) {
            this.container = container;
            this.tile = tile;
            this.catalystIndex = catalystIndex;
            this.entryIndex = entryIndex;
        }

        void setEntryIndex(int index) {
            this.entryIndex = index;
        }

        private CrafterEntry getEntry() {
            return tile.getEntry(entryIndex);
        }

        @Override
        public int getSlots() {
            return 1;
        }

        @Nonnull
        @Override
        public ItemStack getStackInSlot(int slot) {
            CrafterEntry entry = getEntry();
            return entry != null ? entry.getCatalystStack(catalystIndex) : ItemStack.EMPTY;
        }

        @Nonnull
        @Override
        public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
            if (!isItemValid(slot, stack)) return stack;

            CrafterEntry entry = getEntry();
            if (entry == null) return stack;

            ItemStack existing = entry.getCatalystStack(catalystIndex);
            if (!existing.isEmpty()) return stack; // Slot occupied

            if (!simulate) {
                ItemStack toInsert = stack.copy();
                toInsert.setCount(1);
                entry.setCatalystStack(catalystIndex, toInsert);
                tile.validateCatalysts(entryIndex);
                tile.markDirty();
            }

            ItemStack remaining = stack.copy();
            remaining.shrink(1);
            return remaining;
        }

        @Nonnull
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            CrafterEntry entry = getEntry();
            if (entry == null) return ItemStack.EMPTY;

            ItemStack existing = entry.getCatalystStack(catalystIndex);
            if (existing.isEmpty()) return ItemStack.EMPTY;

            ItemStack result = existing.copy();
            if (!simulate) {
                entry.setCatalystStack(catalystIndex, ItemStack.EMPTY);
                tile.validateCatalysts(entryIndex);
                tile.markDirty();
            }

            return result;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            if (stack.isEmpty()) return true; // Empty is always valid

            CrafterEntry entry = getEntry();
            if (entry == null || !entry.hasValidRecipeInfo()) return false;

            CrafterRecipeInfo info = entry.getRecipeInfo();
            if (info == null) return false;

            // Find expected item for this catalyst slot (1:1 mapping)
            ItemStack expected = ItemStack.EMPTY;
            for (CrafterRecipeInfo.IngredientInfo catalyst : info.getCatalystSlots()) {
                if (catalyst.getSlotIndex() == catalystIndex) {
                    expected = catalyst.getItem() != null ? catalyst.getItem().createItemStack() : ItemStack.EMPTY;
                    break;
                }
            }

            // No expected item = slot is not a catalyst slot, reject all items
            if (expected.isEmpty()) return false;

            // Validate item matches expected (inclusive NBT matching)
            return container.isValidCatalystItem(stack, expected, catalystIndex);
        }
    }

    /**
     * IItemHandler for upgrade slot.
     * Only accepts speed upgrades. Properly implements insertItem/extractItem.
     */
    private static class UpgradeItemHandler implements IItemHandler {
        private final TileAutoCrafter tile;
        private final int upgradeIndex;

        UpgradeItemHandler(TileAutoCrafter tile, int upgradeIndex) {
            this.tile = tile;
            this.upgradeIndex = upgradeIndex;
        }

        @Override
        public int getSlots() {
            return 1;
        }

        @Nonnull
        @Override
        public ItemStack getStackInSlot(int slot) {
            return tile.getUpgradeStack(upgradeIndex);
        }

        @Nonnull
        @Override
        public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
            if (!isItemValid(slot, stack)) return stack;

            ItemStack existing = tile.getUpgradeStack(upgradeIndex);
            if (!existing.isEmpty()) return stack; // Slot occupied

            if (!simulate) {
                ItemStack toInsert = stack.copy();
                toInsert.setCount(1);
                tile.setUpgradeStack(upgradeIndex, toInsert);
            }

            ItemStack remaining = stack.copy();
            remaining.shrink(1);
            return remaining;
        }

        @Nonnull
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            ItemStack existing = tile.getUpgradeStack(upgradeIndex);
            if (existing.isEmpty()) return ItemStack.EMPTY;

            ItemStack result = existing.copy();
            if (!simulate) {
                tile.setUpgradeStack(upgradeIndex, ItemStack.EMPTY);
            }

            return result;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            return ItemCrafterSpeedUpgrade.isSpeedUpgrade(stack);
        }
    }

    /**
     * Slot for the pattern item.
     */
    public static class SlotPattern extends AppEngSlot {
        private final PatternItemHandler handler;

        public SlotPattern(ContainerAutoCrafter container, TileAutoCrafter tile, int entryIndex, int x, int y) {
            super(new PatternItemHandler(container, tile, entryIndex), 0, x, y);
            this.handler = (PatternItemHandler) this.getItemHandler();
        }

        public void setEntryIndex(int index) {
            handler.setEntryIndex(index);
        }

        @Override
        public int getSlotStackLimit() {
            return 1;
        }
    }

    /**
     * Slot for catalyst items.
     * Strictly filters to only accept items matching the recipe's expected catalyst.
     */
    public static class SlotCatalyst extends AppEngSlot {
        private final CatalystItemHandler handler;
        private final int catalystIndex;

        public SlotCatalyst(ContainerAutoCrafter container, TileAutoCrafter tile, int catalystIndex, int entryIndex, int x, int y) {
            super(new CatalystItemHandler(container, tile, catalystIndex, entryIndex), 0, x, y);
            this.handler = (CatalystItemHandler) this.getItemHandler();
            this.catalystIndex = catalystIndex;
        }

        public void setEntryIndex(int index) {
            handler.setEntryIndex(index);
        }

        public int getCatalystIndex() {
            return catalystIndex;
        }

        @Override
        public int getSlotStackLimit() {
            // Crafting recipes only use 1 item per slot, so catalyst slots should only hold 1 item.
            // If a recipe requires multiple of the same catalyst (e.g., 3 Blood Orbs), 
            // they would go in separate slots (one per input slot that uses them).
            return 1;
        }
    }

    /**
     * Slot for upgrade items.
     * Only accepts speed upgrades.
     */
    public static class SlotUpgrade extends AppEngSlot {
        // UPGRADES icon index from SlotRestrictedInput.PlacableItemType: 13 * 16 + 15 = 223
        private static final int UPGRADE_ICON = 13 * 16 + 15;

        private final int upgradeIndex;

        public SlotUpgrade(ContainerAutoCrafter container, TileAutoCrafter tile, int upgradeIndex, int x, int y) {
            super(new UpgradeItemHandler(tile, upgradeIndex), 0, x, y);
            this.upgradeIndex = upgradeIndex;

            // Set the icon to show when slot is empty (AE2's "insert upgrade" icon)
            this.setIIcon(UPGRADE_ICON);
        }

        public int getUpgradeIndex() {
            return upgradeIndex;
        }

        @Override
        public int getSlotStackLimit() {
            return 1;
        }
    }
}
