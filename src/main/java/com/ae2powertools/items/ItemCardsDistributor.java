package com.ae2powertools.items;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

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
import com.ae2powertools.PowerToolsCreativeTab;
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
 * <p>
 * Usage:
 * - Right-click on network component: Distribute cards to all assemblers on network
 */
public class ItemCardsDistributor extends Item implements IWirelessTermHandler {

    // TODO: support for CrazyAE assemblers

    public ItemCardsDistributor() {
        this.setRegistryName(Tags.MODID, "cards_distributor");
        this.setTranslationKey(Tags.MODID + ".cards_distributor");
        this.setMaxStackSize(1);
        this.setCreativeTab(PowerToolsCreativeTab.instance);
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
    @Nonnull
    public EnumActionResult onItemUseFirst(@Nonnull EntityPlayer player, World world, @Nonnull BlockPos pos,
            @Nonnull EnumFacing side, float hitX, float hitY, float hitZ, @Nonnull EnumHand hand) {
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
    @Nonnull
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, @Nonnull EnumHand hand) {
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

        IItemDefinition cardSpeedDef = AEApi.instance().definitions().materials().cardSpeed();
        ItemStack cardSpeedTemplate = cardSpeedDef.maybeStack(1).orElse(ItemStack.EMPTY);
        if (cardSpeedTemplate.isEmpty()) return result;

        List<AssemblerInfo> assemblersToUpgrade = collectAssemblersNeedingCards(grid);
        if (assemblersToUpgrade.isEmpty()) return result;

        result.cardsFromInventory = distributeCardsRoundRobin(
            assemblersToUpgrade,
            countAccelerationCards(player, cardSpeedTemplate),
            info -> insertOneCard(player, info.upgradeInventory, cardSpeedTemplate)
        );
        result.cardsUsed += result.cardsFromInventory;

        int remainingNeeded = countRemainingCards(assemblersToUpgrade);
        if (remainingNeeded > 0) {
            int pulled = pullCardsFromNetwork(player, distributorStack, remainingNeeded, cardSpeedTemplate);
            result.cardsFromAE2 = distributeCardsRoundRobin(
                assemblersToUpgrade,
                pulled,
                info -> insertOneCardDirect(info.upgradeInventory, cardSpeedTemplate)
            );
            result.cardsUsed += result.cardsFromAE2;
        }

        summarizeDistribution(result, assemblersToUpgrade);
        return result;
    }

    /**
     * Collect all assemblers that can still accept acceleration cards.
     */
    private List<AssemblerInfo> collectAssemblersNeedingCards(IGrid grid) {
        List<AssemblerInfo> assemblersToUpgrade = new ArrayList<>();

        for (IGridNode node : grid.getMachines(TileMolecularAssembler.class)) {
            Object machine = node.getMachine();
            if (!(machine instanceof TileMolecularAssembler)) continue;

            TileMolecularAssembler assembler = (TileMolecularAssembler) machine;
            IItemHandler upgradeInv = assembler.getInventoryByName("upgrades");
            if (upgradeInv == null) continue;

            int maxCards = upgradeInv.getSlots();
            int currentCards = assembler.getInstalledUpgrades(Upgrades.SPEED);
            int slotsNeeded = maxCards - currentCards;
            if (slotsNeeded > 0) assemblersToUpgrade.add(new AssemblerInfo(upgradeInv, slotsNeeded));
        }

        return assemblersToUpgrade;
    }

    /**
     * Count how many cards are still needed across all tracked assemblers.
     */
    private int countRemainingCards(List<AssemblerInfo> assemblersToUpgrade) {
        int remainingNeeded = 0;

        for (AssemblerInfo info : assemblersToUpgrade) {
            remainingNeeded += Math.max(0, info.slotsRemaining);
        }

        return remainingNeeded;
    }

    /**
     * Distribute cards one per assembler per round to keep upgrades spread evenly.
     */
    private int distributeCardsRoundRobin(List<AssemblerInfo> assemblersToUpgrade, int availableCards,
            AssemblerCardInserter inserter) {
        int insertedCards = 0;

        while (availableCards > 0) {
            int insertedThisRound = 0;

            for (AssemblerInfo info : assemblersToUpgrade) {
                if (availableCards <= 0) break;
                if (info.slotsRemaining <= 0) continue;
                if (!inserter.insert(info)) continue;

                availableCards--;
                insertedCards++;
                insertedThisRound++;
                info.slotsRemaining--;
                info.cardsInserted++;
            }

            if (insertedThisRound == 0) return insertedCards;
        }

        return insertedCards;
    }

    /**
     * Derive all user-visible counters from the tracked assembler state.
     */
    private void summarizeDistribution(DistributionResult result, List<AssemblerInfo> assemblersToUpgrade) {
        for (AssemblerInfo info : assemblersToUpgrade) {
            if (info.cardsInserted > 0) result.assemblersUpgraded++;
            if (info.slotsRemaining <= 0) continue;

            result.cardsNeeded += info.slotsRemaining;
            result.assemblersNeedingCards++;
        }
    }

    @FunctionalInterface
    private interface AssemblerCardInserter {
        boolean insert(AssemblerInfo info);
    }

    /**
     * Helper class to track assembler upgrade state.
     */
    private static class AssemblerInfo {
        final IItemHandler upgradeInventory;
        int slotsRemaining;
        int cardsInserted = 0;

        AssemblerInfo(IItemHandler upgradeInv, int slotsAvailable) {
            this.upgradeInventory = upgradeInv;
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
        node.getGrid();
        IEnergyGrid eg = node.getGrid().getCache(IEnergyGrid.class);

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
                if (node != null) {
                    node.getGrid();
                    return node.getGrid();
                }
            }

            // Try the cable in the center
            IPart cable = partHost.getPart(AEPartLocation.INTERNAL);
            if (cable instanceof IGridHost) {
                IGridNode node = ((IGridHost) cable).getGridNode(AEPartLocation.INTERNAL);
                if (node != null) {
                    node.getGrid();
                    return node.getGrid();
                }
            }

            // Try all sides
            for (AEPartLocation loc : AEPartLocation.values()) {
                IPart p = partHost.getPart(loc);
                if (p instanceof IGridHost) {
                    IGridNode node = ((IGridHost) p).getGridNode(AEPartLocation.INTERNAL);
                    if (node != null) {
                        node.getGrid();
                        return node.getGrid();
                    }
                }
            }
        }

        // Check if the tile itself is a grid host
        if (te instanceof IGridHost) {
            IGridHost host = (IGridHost) te;

            // Try all possible node locations
            for (AEPartLocation loc : AEPartLocation.values()) {
                IGridNode node = host.getGridNode(loc);
                if (node != null) {
                    node.getGrid();
                    return node.getGrid();
                }
            }
        }

        return null;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(@Nonnull ItemStack stack, World world, @Nonnull List<String> tooltip,
            @Nonnull ITooltipFlag flag) {
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
