package com.ae2powertools.items;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
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
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.util.AEPartLocation;

import com.ae2powertools.PowerToolsCreativeTab;
import com.ae2powertools.Tags;
import com.ae2powertools.features.scanner.ScanSessionManager;
import com.ae2powertools.features.scanner.client.ScannerClientState;
import com.ae2powertools.features.scanner.gui.GuiNetworkHealthScanner;
import com.ae2powertools.network.PacketScannerSync;
import com.ae2powertools.network.PowerToolsNetwork;
import com.ae2powertools.util.DeviceItemAccess;


/**
 * Network Health Scanner - detects loops and unloaded chunks in AE2 networks.
 * <p>
 * Usage:
 * - Right-click on network component: Start network scan
 * - Right-click in air: Open results GUI
 * - Shift-right-click: Toggle overlay display
 */
public class ItemNetworkHealthScanner extends Item {

    private static final String NBT_OVERLAY_ENABLED = "OverlayEnabled";
    private static final String NBT_SUBNET_SCAN = "SubnetScan";

    // Cache overlay state by device ID
    private static final Map<Long, Boolean> overlayCache = new IdentityHashMap<>();

    private static final DeviceItemAccess DEVICE_ACCESS = new DeviceItemAccess(ItemNetworkHealthScanner.class, true);

    public ItemNetworkHealthScanner() {
        this.setRegistryName(Tags.MODID, "network_health_scanner");
        this.setTranslationKey(Tags.MODID + ".network_health_scanner");
        this.setMaxStackSize(1);
        this.setCreativeTab(PowerToolsCreativeTab.instance);
    }

    /**
     * Get or create a unique device ID for this scanner.
     * Uses nanosecond timestamp on first creation for uniqueness.
     * Values are cached to avoid repeated NBT lookups.
     */
    public static long getDeviceId(ItemStack stack) {
        return DEVICE_ACCESS.getOrCreateDeviceId(stack);
    }

    public static ItemStack getHeldScanner(EntityPlayer player) {
        return DEVICE_ACCESS.findHeldDevice(player);
    }

    /**
     * Check if overlay is enabled for this scanner.
     * Defaults to true if not set.
     * Values are cached by device ID to avoid repeated NBT lookups.
     */
    public static boolean isOverlayEnabled(ItemStack stack) {
        if (stack.isEmpty()) return false;

        long deviceId = getDeviceId(stack);
        if (deviceId == 0L) return false;

        // Check cache first
        Boolean cached = overlayCache.get(deviceId);
        if (cached != null) return cached;

        // Read from NBT
        NBTTagCompound nbt = stack.getTagCompound();
        boolean enabled = (nbt == null || !nbt.hasKey(NBT_OVERLAY_ENABLED)) || nbt.getBoolean(NBT_OVERLAY_ENABLED);

        overlayCache.put(deviceId, enabled);

        return enabled;
    }

    /**
     * Set overlay enabled state for this scanner.
     * Updates the cache immediately.
     */
    public static void setOverlayEnabled(ItemStack stack, boolean enabled) {
        if (stack.isEmpty()) return;

        NBTTagCompound nbt = stack.getTagCompound();
        if (nbt == null) {
            nbt = new NBTTagCompound();
            stack.setTagCompound(nbt);
        }

        nbt.setBoolean(NBT_OVERLAY_ENABLED, enabled);

        // Update cache
        long deviceId = getDeviceId(stack);
        if (deviceId != 0L) overlayCache.put(deviceId, enabled);
    }

    /**
     * Toggle overlay enabled state for this scanner.
     * @return The new overlay enabled state.
     */
    public static boolean toggleOverlayEnabled(ItemStack stack) {
        boolean newState = !isOverlayEnabled(stack);
        setOverlayEnabled(stack, newState);

        return newState;
    }

    /**
     * Check if subnet scanning is enabled for this scanner.
     * Defaults to false if not set.
     */
    public static boolean isSubnetScanEnabled(ItemStack stack) {
        if (stack.isEmpty()) return false;

        NBTTagCompound nbt = stack.getTagCompound();

        return nbt != null && nbt.getBoolean(NBT_SUBNET_SCAN);
    }

    public static void setSubnetScanEnabled(ItemStack stack, boolean enabled) {
        if (stack.isEmpty()) return;

        NBTTagCompound nbt = stack.getTagCompound();
        if (nbt == null) {
            nbt = new NBTTagCompound();
            stack.setTagCompound(nbt);
        }

        nbt.setBoolean(NBT_SUBNET_SCAN, enabled);
    }

    public static boolean toggleSubnetScan(ItemStack stack) {
        boolean newState = !isSubnetScanEnabled(stack);
        setSubnetScanEnabled(stack, newState);

        return newState;
    }

    /**
     * Get the persisted subnet scan state for a scanner device in the player's inventory.
     * Falls back to the supplied value when the matching scanner item cannot be located.
     */
    public static boolean getSubnetScanEnabled(EntityPlayer player, long deviceId, boolean fallbackValue) {
        ItemStack scannerStack = DEVICE_ACCESS.findDeviceOnPlayerById(player, deviceId);
        if (scannerStack.isEmpty()) return fallbackValue;

        return isSubnetScanEnabled(scannerStack);
    }

    /**
     * Check if a stack has a device ID assigned.
     */
    public static boolean hasDeviceId(ItemStack stack) {
        return DEVICE_ACCESS.hasDeviceId(stack);
    }

    @Override
    public void onUpdate(@Nonnull ItemStack stack, World world, @Nonnull Entity entity, int slot, boolean isHeld) {
        // Ensure device ID is assigned on first inventory tick
        if (!world.isRemote && !stack.isEmpty()) getDeviceId(stack);
    }

    @Override
    @Nonnull
    public EnumActionResult onItemUseFirst(@Nonnull EntityPlayer player, World world, @Nonnull BlockPos pos,
            @Nonnull EnumFacing side, float hitX, float hitY, float hitZ, @Nonnull EnumHand hand) {
        if (world.isRemote) return EnumActionResult.PASS;

        ItemStack stack = player.getHeldItem(hand);
        long deviceId = getDeviceId(stack);

        // Try to get grid from the clicked block
        IGrid grid = getGridFromPosition(world, pos, side);

        if (grid == null) {
            player.sendMessage(new TextComponentTranslation("item.ae2powertools.network_health_scanner.no_network"));

            return EnumActionResult.FAIL;
        }

        // Start network scan with device ID
        boolean includeSubnets = isSubnetScanEnabled(stack);
        ScanSessionManager.startSession(player, grid, deviceId, includeSubnets);
        player.sendMessage(new TextComponentTranslation("item.ae2powertools.network_health_scanner.started"));

        // Send initial sync packet
        syncToClient((EntityPlayerMP) player, deviceId);

        return EnumActionResult.SUCCESS;
    }

    @Override
    @Nonnull
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, @Nonnull EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);

        if (world.isRemote) {
            long deviceId = getDeviceId(stack);

            if (player.isSneaking()) {
                // Shift-right-click: toggle overlay
                boolean enabled = toggleOverlayEnabled(stack);
                String key = enabled ?
                    "item.ae2powertools.network_health_scanner.overlay_enabled" :
                    "item.ae2powertools.network_health_scanner.overlay_disabled";
                player.sendMessage(new TextComponentTranslation(key));
            } else {
                // Regular right-click in air: open GUI
                if (ScannerClientState.hasSession(deviceId)) {
                    openGui(deviceId, isSubnetScanEnabled(stack));
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
    private void openGui(long deviceId, boolean subnetScanEnabled) {
        ScannerClientState.initSubnetState(deviceId, subnetScanEnabled);
        Minecraft.getMinecraft().displayGuiScreen(new GuiNetworkHealthScanner(deviceId));
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

    /**
     * Sync scan state to client for a specific device.
     */
    public static void syncToClient(EntityPlayerMP player, long deviceId) {
        ScanSessionManager.ScanSession session = ScanSessionManager.getSession(player, deviceId);
        boolean subnetScanEnabled = session != null && session.getScanner().isSubnetScanEnabled();

        // The current scan keeps its original subnet scope until the next rescan, but the GUI
        // should still reflect the persisted toggle stored on the scanner item immediately.
        subnetScanEnabled = getSubnetScanEnabled(player, deviceId, subnetScanEnabled);

        PacketScannerSync packet = session == null ?
            PacketScannerSync.noSession(deviceId, subnetScanEnabled) :
            new PacketScannerSync(session, deviceId, subnetScanEnabled);
        PowerToolsNetwork.INSTANCE.sendTo(packet, player);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(@Nonnull ItemStack stack, World world, @Nonnull List<String> tooltip,
            @Nonnull ITooltipFlag flag) {
        super.addInformation(stack, world, tooltip, flag);

        tooltip.add("");
        tooltip.add(I18n.format("item.ae2powertools.network_health_scanner.tip1"));
        tooltip.add(I18n.format("item.ae2powertools.network_health_scanner.tip2"));

        // Show current overlay state in tip3 with colored ON/OFF
        boolean overlayEnabled = isOverlayEnabled(stack);
        String overlayState = I18n.format(overlayEnabled ?
            "item.ae2powertools.network_health_scanner.overlay_on" :
            "item.ae2powertools.network_health_scanner.overlay_off");
        tooltip.add(I18n.format("item.ae2powertools.network_health_scanner.tip3", overlayState));
    }
}
