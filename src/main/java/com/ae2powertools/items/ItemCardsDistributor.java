package com.ae2powertools.items;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.IItemHandler;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.Upgrades;
import appeng.api.definitions.IItemDefinition;
import appeng.api.features.IWirelessTermHandler;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.util.AEPartLocation;
import appeng.api.util.IConfigManager;
import appeng.core.CreativeTab;
import appeng.core.localization.GuiText;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.me.helpers.BaseActionSource;
import appeng.tile.crafting.TileMolecularAssembler;
import appeng.util.ConfigManager;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;
import net.minecraftforge.fml.common.network.IGuiHandler;
import appeng.api.networking.energy.IEnergyGrid;

import com.ae2powertools.Tags;


/**
 * Cards Distributor - distributes cards from player inventory
 * to Molecular Assemblers (and similar machines) on the network.
 *
 * Usage:
 * - Right-click on network component: Distribute cards to all assemblers on network
 */
public class ItemCardsDistributor extends Item implements IWirelessTermHandler {

    // TODO: support for CrazyAE assemblers

    public ItemCardsDistributor() {
        this.setRegistryName(Tags.MODID, "cards_distributor");
        this.setTranslationKey(Tags.MODID + ".cards_distributor");
        this.setMaxStackSize(1);
        this.setCreativeTab(CreativeTab.instance);
    }

    // IWirelessTermHandler implementation
    @Override
    public boolean canHandle(ItemStack is) {
        return is != null && is.getItem() == this;
    }

    @Override
    public boolean usePower(EntityPlayer player, double amount, ItemStack is) {
        return true;
    }

    @Override
    public boolean hasPower(EntityPlayer player, double amount, ItemStack is) {
        return true;
    }

    @Override
    public IConfigManager getConfigManager(ItemStack target) {
        final ConfigManager out = new ConfigManager((manager, settingName, newValue) -> {
            NBTTagCompound data = Platform.openNbtData(target);
            manager.writeToNBT(data);
        });

        // Keep defaults similar to AE2 wireless for compatibility
        out.readFromNBT(Platform.openNbtData(target).copy());
        return out;
    }

    @Override
    public IGuiHandler getGuiHandler(ItemStack is) {
        return null;
    }

    @Override
    public String getEncryptionKey(ItemStack item) {
        final NBTTagCompound tag = Platform.openNbtData(item);
        return tag.getString("encryptionKey");
    }

    @Override
    public void setEncryptionKey(ItemStack item, String encKey, String name) {
        final NBTTagCompound tag = Platform.openNbtData(item);
        tag.setString("encryptionKey", encKey);
        tag.setString("name", name);
    }

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos, EnumFacing side,
            float hitX, float hitY, float hitZ, EnumHand hand) {
        // Return SUCCESS on client to prevent onItemRightClick from also firing
        if (world.isRemote) return EnumActionResult.SUCCESS;

        // Try to get grid from the clicked block
        IGrid grid = getGridFromPosition(world, pos, side);

        if (grid == null) {
            player.sendMessage(new TextComponentTranslation("item.ae2powertools.cards_distributor.no_network"));

            return EnumActionResult.FAIL;
        }

        // Find and distribute acceleration cards
        ItemStack distributorStack = player.getHeldItem(hand);
        DistributionResult result = distributeAccelerationCards(player, grid, distributorStack);

        // Report results
        if (result.cardsUsed > 0) {
            player.sendMessage(new TextComponentTranslation(
                "item.ae2powertools.cards_distributor.success_accelerator",
                result.cardsUsed,
                result.assemblersUpgraded
            ));
        }

        if (result.cardsNeeded > 0) {
            player.sendMessage(new TextComponentTranslation(
                "item.ae2powertools.cards_distributor.still_needed",
                result.cardsNeeded,
                result.assemblersNeedingCards
            ));
        }

        if (result.cardsUsed == 0 && result.cardsNeeded == 0) {
            player.sendMessage(new TextComponentTranslation(
                "item.ae2powertools.cards_distributor.all_full"
            ));
        }

        if (result.cardsFromAE2 > 0) {
            player.sendMessage(new TextComponentTranslation(
                "item.ae2powertools.cards_distributor.pulled_from_ae2",
                result.cardsFromAE2
            ));
        }

        return EnumActionResult.SUCCESS;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);

        if (!world.isRemote) {
            player.sendMessage(new TextComponentTranslation("item.ae2powertools.cards_distributor.use_on_network"));
        }

        return new ActionResult<>(EnumActionResult.PASS, stack);
    }

    /**
     * Result of distributing acceleration cards.
     */
    private static class DistributionResult {
        int cardsUsed = 0;
        int assemblersUpgraded = 0;
        int cardsNeeded = 0;
        int assemblersNeedingCards = 0;
        int cardsFromAE2 = 0;
        int cardsFromInventory = 0;
    }

    /**
     * Distribute acceleration cards from player inventory to assemblers on the network.
     */
    private DistributionResult distributeAccelerationCards(EntityPlayer player, IGrid grid, ItemStack distributorStack) {
        DistributionResult result = new DistributionResult();

        // Get the acceleration card definition
        IItemDefinition cardSpeedDef = AEApi.instance().definitions().materials().cardSpeed();
        ItemStack cardSpeedTemplate = cardSpeedDef.maybeStack(1).orElse(ItemStack.EMPTY);
        if (cardSpeedTemplate.isEmpty()) return result;

        // Find all assemblers that can accept more cards
        List<AssemblerInfo> assemblersToUpgrade = new ArrayList<>();

        // Get all Molecular Assemblers from the grid
        for (IGridNode node : grid.getMachines(TileMolecularAssembler.class)) {
            Object machine = node.getMachine();
            if (!(machine instanceof TileMolecularAssembler)) continue;

            TileMolecularAssembler assembler = (TileMolecularAssembler) machine;
            IItemHandler upgradeInv = assembler.getInventoryByName("upgrades");
            if (upgradeInv == null) continue;

            // Derive max cards from upgrade inventory size
            int maxCards = upgradeInv.getSlots();
            int currentCards = assembler.getInstalledUpgrades(Upgrades.SPEED);
            int slotsAvailable = maxCards - currentCards;

            if (slotsAvailable > 0) {
                assemblersToUpgrade.add(new AssemblerInfo(assembler, upgradeInv, maxCards, slotsAvailable));
            }
        }

        if (assemblersToUpgrade.isEmpty()) return result;

        // Count available cards in player inventory
        int availableCards = countAccelerationCards(player, cardSpeedTemplate);

        // Distribute cards from player inventory first (round-robin style)
        while (availableCards > 0 && !assemblersToUpgrade.isEmpty()) {
            List<AssemblerInfo> stillNeedCards = new ArrayList<>();

            int cardsBeforeRound = availableCards;
            for (AssemblerInfo info : assemblersToUpgrade) {
                if (availableCards <= 0) break;
                if (info.slotsRemaining <= 0) continue;

                // Try to insert one card
                if (insertOneCard(player, info.upgradeInventory, cardSpeedTemplate)) {
                    availableCards--;
                    info.slotsRemaining--;
                    result.cardsUsed++;
                    info.cardsInserted++;
                    result.cardsFromInventory++;

                    if (info.slotsRemaining > 0) stillNeedCards.add(info);
                }
            }

            // If no cards were inserted in this round, break to avoid infinite loop
            if (cardsBeforeRound == availableCards) break;

            assemblersToUpgrade = stillNeedCards;
        }

        // If more cards are needed, pull from AE2 via wireless link
        int remainingNeeded = 0;
        for (AssemblerInfo info : assemblersToUpgrade) remainingNeeded += Math.max(0, info.slotsRemaining);

        if (remainingNeeded > 0) {
            int pulled = pullCardsFromNetwork(player, distributorStack, remainingNeeded, cardSpeedTemplate);

            while (pulled > 0 && !assemblersToUpgrade.isEmpty()) {
                List<AssemblerInfo> stillNeedCards = new ArrayList<>();
                int cardsBeforeRound = pulled;

                for (AssemblerInfo info : assemblersToUpgrade) {
                    if (pulled <= 0) break;
                    if (info.slotsRemaining <= 0) continue;

                    if (insertOneCardDirect(info.upgradeInventory, cardSpeedTemplate)) {
                        pulled--;
                        info.slotsRemaining--;
                        result.cardsUsed++;
                        result.cardsFromAE2++;
                        info.cardsInserted++;

                        if (info.slotsRemaining > 0) stillNeedCards.add(info);
                    }
                }

                if (cardsBeforeRound == pulled) break;
                assemblersToUpgrade = stillNeedCards;
            }
        }

        // Count assemblers that received cards
        for (AssemblerInfo info : assemblersToUpgrade) {
            if (info.cardsInserted > 0) result.assemblersUpgraded++;
        }

        // Also count assemblers we upgraded that are no longer in the list
        result.assemblersUpgraded = (int) assemblersToUpgrade.stream()
            .filter(i -> i.cardsInserted > 0 || i.slotsRemaining < (i.maxSlots - i.originalSlots))
            .count();

        // Actually, let's recalculate properly
        result.assemblersUpgraded = 0;
        result.cardsNeeded = 0;
        result.assemblersNeedingCards = 0;

        // Rescan to get accurate counts
        for (IGridNode node : grid.getMachines(TileMolecularAssembler.class)) {
            Object machine = node.getMachine();
            if (!(machine instanceof TileMolecularAssembler)) continue;

            TileMolecularAssembler assembler = (TileMolecularAssembler) machine;
            IItemHandler upgradeInv = assembler.getInventoryByName("upgrades");
            if (upgradeInv == null) continue;

            int maxCards = upgradeInv.getSlots();
            int currentCards = assembler.getInstalledUpgrades(Upgrades.SPEED);
            int slotsNeeded = maxCards - currentCards;

            if (slotsNeeded > 0) {
                result.cardsNeeded += slotsNeeded;
                result.assemblersNeedingCards++;
            }
        }

        // Count how many assemblers we actually upgraded
        result.assemblersUpgraded = result.cardsUsed > 0 ?
            Math.min(result.cardsUsed, grid.getMachines(TileMolecularAssembler.class).size()) : 0;

        return result;
    }

    /**
     * Helper class to track assembler upgrade state.
     */
    private static class AssemblerInfo {
        final TileMolecularAssembler assembler;
        final IItemHandler upgradeInventory;
        final int maxSlots;
        final int originalSlots;
        int slotsRemaining;
        int cardsInserted = 0;

        AssemblerInfo(TileMolecularAssembler assembler, IItemHandler upgradeInv, int maxSlots, int slotsAvailable) {
            this.assembler = assembler;
            this.upgradeInventory = upgradeInv;
            this.maxSlots = maxSlots;
            this.originalSlots = slotsAvailable;
            this.slotsRemaining = slotsAvailable;
        }
    }

    /**
     * Count acceleration cards in player inventory.
     */
    private int countAccelerationCards(EntityPlayer player, ItemStack template) {
        int count = 0;

        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (!stack.isEmpty() && ItemStack.areItemsEqual(stack, template)) {
                count += stack.getCount();
            }
        }

        return count;
    }

    /**
     * Try to insert one acceleration card from player inventory into the upgrade inventory.
     * Returns true if successful.
     */
    private boolean insertOneCard(EntityPlayer player, IItemHandler upgradeInv, ItemStack template) {
        // Find a card in player inventory
        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (stack.isEmpty() || !ItemStack.areItemsEqual(stack, template)) continue;

            // Try to insert into upgrade inventory
            ItemStack toInsert = stack.copy();
            toInsert.setCount(1);

            for (int slot = 0; slot < upgradeInv.getSlots(); slot++) {
                ItemStack remainder = upgradeInv.insertItem(slot, toInsert, false);
                if (remainder.isEmpty()) {
                    // Successfully inserted
                    stack.shrink(1);
                    if (stack.isEmpty()) player.inventory.setInventorySlotContents(i, ItemStack.EMPTY);

                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Insert one acceleration card directly into the upgrade inventory.
     */
    private boolean insertOneCardDirect(IItemHandler upgradeInv, ItemStack template) {
        ItemStack toInsert = template.copy();
        toInsert.setCount(1);

        for (int slot = 0; slot < upgradeInv.getSlots(); slot++) {
            ItemStack remainder = upgradeInv.insertItem(slot, toInsert, false);
            if (remainder.isEmpty()) return true;
        }

        return false;
    }

    /**
     * Pull up to `needed` acceleration cards from the ME network
     */
    private int pullCardsFromNetwork(EntityPlayer player, ItemStack distributorStack, int needed, ItemStack cardTemplate) {
        if (distributorStack.isEmpty() || distributorStack.getItem() != this) return 0;

        String encKey = getEncryptionKey(distributorStack);
        if (encKey == null || encKey.isEmpty()) return 0; // unlinked: no-op

        WirelessTerminalGuiObject wtg = new WirelessTerminalGuiObject(this, distributorStack, player, player.world, -1, 0, 0);
        if (!wtg.rangeCheck()) return 0;

        IMEMonitor<IAEItemStack> inv = wtg.getInventory(AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));
        if (inv == null) return 0;

        IAEItemStack req = AEItemStack.fromItemStack(cardTemplate.copy());
        if (req == null) return 0;
        req.setStackSize(needed);

        // Drain energy from the ME network
        IGridNode node = wtg.getActionableNode();
        if (node == null || node.getGrid() == null) return 0;
        IEnergyGrid eg = node.getGrid().getCache(IEnergyGrid.class);
        if (eg == null) return 0;

        IAEItemStack extracted = Platform.poweredExtraction(eg, inv, req, new BaseActionSource(), Actionable.MODULATE);
        if (extracted == null) return 0;

        return (int) Math.min(Integer.MAX_VALUE, extracted.getStackSize());
    }

    /**
     * Get the IGrid from a block position.
     */
    private IGrid getGridFromPosition(World world, BlockPos pos, EnumFacing side) {
        TileEntity te = world.getTileEntity(pos);
        if (te == null) return null;

        // Check if it's a part host (cable bus)
        if (te instanceof IPartHost) {
            IPartHost partHost = (IPartHost) te;

            // Try the side that was clicked
            AEPartLocation aeSide = AEPartLocation.fromFacing(side);
            IPart part = partHost.getPart(aeSide);

            if (part instanceof IGridHost) {
                IGridNode node = ((IGridHost) part).getGridNode(AEPartLocation.INTERNAL);
                if (node != null && node.getGrid() != null) return node.getGrid();
            }

            // Try the cable in the center
            IPart cable = partHost.getPart(AEPartLocation.INTERNAL);
            if (cable instanceof IGridHost) {
                IGridNode node = ((IGridHost) cable).getGridNode(AEPartLocation.INTERNAL);
                if (node != null && node.getGrid() != null) return node.getGrid();
            }

            // Try all sides
            for (AEPartLocation loc : AEPartLocation.values()) {
                IPart p = partHost.getPart(loc);
                if (p instanceof IGridHost) {
                    IGridNode node = ((IGridHost) p).getGridNode(AEPartLocation.INTERNAL);
                    if (node != null && node.getGrid() != null) return node.getGrid();
                }
            }
        }

        // Check if the tile itself is a grid host
        if (te instanceof IGridHost) {
            IGridHost host = (IGridHost) te;

            // Try all possible node locations
            for (AEPartLocation loc : AEPartLocation.values()) {
                IGridNode node = host.getGridNode(loc);
                if (node != null && node.getGrid() != null) return node.getGrid();
            }
        }

        return null;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, World world, List<String> tooltip, ITooltipFlag flag) {
        super.addInformation(stack, world, tooltip, flag);

        String encKey = null;
        if (stack.hasTagCompound()) {
            NBTTagCompound tag = Platform.openNbtData(stack);
            encKey = tag.getString("encryptionKey");
        }

        if (encKey == null || encKey.isEmpty()) {
            tooltip.add(TextFormatting.RED + GuiText.Unlinked.getLocal());
        } else {
            tooltip.add(TextFormatting.GREEN + GuiText.Linked.getLocal());
        }

        String tip1 = encKey != null && !encKey.isEmpty() ?
            I18n.format("item.ae2powertools.cards_distributor.tip1bis") :
            I18n.format("item.ae2powertools.cards_distributor.tip1");

        tooltip.add("");
        tooltip.add(TextFormatting.AQUA + tip1);
        tooltip.add(TextFormatting.GRAY + I18n.format("item.ae2powertools.cards_distributor.tip2"));
    }
}
