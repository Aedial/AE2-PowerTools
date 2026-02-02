package com.ae2powertools.items;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
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

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.util.AEPartLocation;
import appeng.core.CreativeTab;

import com.ae2powertools.Tags;
import com.ae2powertools.network.PacketScannerSync;
import com.ae2powertools.network.PowerToolsNetwork;
import com.ae2powertools.features.scanner.GuiNetworkHealthScanner;
import com.ae2powertools.features.scanner.ScannerClientState;
import com.ae2powertools.features.scanner.ScanSessionManager;


/**
 * Network Health Scanner - detects loops and unloaded chunks in AE2 networks.
 *
 * Usage:
 * - Right-click on network component: Start network scan
 * - Right-click in air: Open results GUI
 * - Shift-right-click: Toggle overlay display
 */
public class ItemNetworkHealthScanner extends Item {

    public ItemNetworkHealthScanner() {
        this.setRegistryName(Tags.MODID, "network_health_scanner");
        this.setTranslationKey(Tags.MODID + ".network_health_scanner");
        this.setMaxStackSize(1);
        this.setCreativeTab(CreativeTab.instance);
    }

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos, EnumFacing side,
            float hitX, float hitY, float hitZ, EnumHand hand) {
        if (world.isRemote) return EnumActionResult.PASS;

        // Try to get grid from the clicked block
        IGrid grid = getGridFromPosition(world, pos, side);

        if (grid == null) {
            player.sendMessage(new TextComponentTranslation("item.ae2powertools.network_health_scanner.no_network"));

            return EnumActionResult.FAIL;
        }

        // Start network scan
        ScanSessionManager.startSession(player, grid);
        player.sendMessage(new TextComponentTranslation("item.ae2powertools.network_health_scanner.started"));

        // Send initial sync packet
        syncToClient((EntityPlayerMP) player);

        return EnumActionResult.SUCCESS;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);

        if (world.isRemote) {
            if (player.isSneaking()) {
                // Shift-right-click: toggle overlay
                ScannerClientState.toggleOverlay();
                String key = ScannerClientState.isOverlayEnabled() ?
                    "item.ae2powertools.network_health_scanner.overlay_enabled" :
                    "item.ae2powertools.network_health_scanner.overlay_disabled";
                player.sendMessage(new TextComponentTranslation(key));
            } else {
                // Regular right-click in air: open GUI
                if (ScannerClientState.hasActiveSession()) {
                    openGui();
                } else {
                    player.sendMessage(new TextComponentTranslation("item.ae2powertools.network_health_scanner.no_session"));
                }
            }

            return new ActionResult<>(EnumActionResult.SUCCESS, stack);
        }

        // Server side: just acknowledge the action
        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }

    @SideOnly(Side.CLIENT)
    private void openGui() {
        Minecraft.getMinecraft().displayGuiScreen(new GuiNetworkHealthScanner());
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

    /**
     * Sync scan state to client.
     */
    public static void syncToClient(EntityPlayerMP player) {
        ScanSessionManager.ScanSession session = ScanSessionManager.getSession(player);

        PacketScannerSync packet = session == null ? PacketScannerSync.noSession() : new PacketScannerSync(session);
        PowerToolsNetwork.INSTANCE.sendTo(packet, player);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, World world, List<String> tooltip, ITooltipFlag flag) {
        super.addInformation(stack, world, tooltip, flag);

        tooltip.add("");
        tooltip.add(TextFormatting.AQUA + I18n.format("item.ae2powertools.network_health_scanner.tip1"));
        tooltip.add(TextFormatting.AQUA + I18n.format("item.ae2powertools.network_health_scanner.tip2"));

        // Show current overlay state in tip3 with colored ON/OFF
        boolean overlayEnabled = ScannerClientState.isOverlayEnabled();
        String overlayState = overlayEnabled ?
            TextFormatting.GREEN + I18n.format("item.ae2powertools.network_health_scanner.overlay_on") + TextFormatting.AQUA :
            TextFormatting.RED + I18n.format("item.ae2powertools.network_health_scanner.overlay_off") + TextFormatting.AQUA;
        tooltip.add(TextFormatting.AQUA + I18n.format("item.ae2powertools.network_health_scanner.tip3", overlayState));
    }
}
