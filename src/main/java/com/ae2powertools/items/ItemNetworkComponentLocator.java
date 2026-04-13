package com.ae2powertools.items;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

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
import com.ae2powertools.features.locator.ComponentScanner;
import com.ae2powertools.features.locator.GuiComponentLocator;
import com.ae2powertools.features.locator.LocatorClientState;
import com.ae2powertools.features.locator.PacketLocatorSync;
import com.ae2powertools.network.PowerToolsNetwork;


/**
 * Network Component Locator - scans the network and shows all components in a grid,
 * allowing you to click on a specific component type to see all its locations sorted by distance.
 * <p>
 * Usage:
 * - Right-click on network component: Scan network and open component grid GUI
 * - Right-click in air: Reopen GUI with last scan results
 * - Shift-right-click: Toggle overlay display
 */
public class ItemNetworkComponentLocator extends Item {

    private static final String NBT_DEVICE_ID = "DeviceId";
    private static final String NBT_OVERLAY_ENABLED = "OverlayEnabled";
    private static final String NBT_SUBNET_SCAN = "SubnetScan";

    // Cache device ID by NBT compound identity to avoid repeated lookups
    private static final Map<NBTTagCompound, Long> deviceIdCache = new IdentityHashMap<>();

    // Cache overlay state by device ID
    private static final Map<Long, Boolean> overlayCache = new IdentityHashMap<>();

    public ItemNetworkComponentLocator() {
        this.setRegistryName(Tags.MODID, "network_component_locator");
        this.setTranslationKey(Tags.MODID + ".network_component_locator");
        this.setMaxStackSize(1);
        this.setCreativeTab(CreativeTab.instance);
    }

    /**
     * Get or create a unique device ID for this locator.
     * Uses nanosecond timestamp on first creation for uniqueness.
     * Values are cached to avoid repeated NBT lookups.
     */
    public static long getDeviceId(ItemStack stack) {
        if (stack.isEmpty()) return 0L;

        NBTTagCompound nbt = stack.getTagCompound();

        // Check cache first (using NBT compound identity)
        if (nbt != null) {
            Long cached = deviceIdCache.get(nbt);
            if (cached != null) return cached;
        }

        // Create NBT if needed
        if (nbt == null) {
            nbt = new NBTTagCompound();
            stack.setTagCompound(nbt);
        }

        // Generate new ID if not present
        if (!nbt.hasKey(NBT_DEVICE_ID)) nbt.setLong(NBT_DEVICE_ID, System.nanoTime());

        long deviceId = nbt.getLong(NBT_DEVICE_ID);
        deviceIdCache.put(nbt, deviceId);

        return deviceId;
    }

    /**
     * Check if overlay is enabled for this locator.
     * Defaults to true if not set.
     */
    public static boolean isOverlayEnabled(ItemStack stack) {
        if (stack.isEmpty()) return false;

        long deviceId = getDeviceId(stack);
        if (deviceId == 0L) return false;

        Boolean cached = overlayCache.get(deviceId);
        if (cached != null) return cached;

        NBTTagCompound nbt = stack.getTagCompound();
        boolean enabled = (nbt == null || !nbt.hasKey(NBT_OVERLAY_ENABLED)) || nbt.getBoolean(NBT_OVERLAY_ENABLED);

        overlayCache.put(deviceId, enabled);

        return enabled;
    }

    public static void setOverlayEnabled(ItemStack stack, boolean enabled) {
        if (stack.isEmpty()) return;

        NBTTagCompound nbt = stack.getTagCompound();
        if (nbt == null) {
            nbt = new NBTTagCompound();
            stack.setTagCompound(nbt);
        }

        nbt.setBoolean(NBT_OVERLAY_ENABLED, enabled);

        long deviceId = getDeviceId(stack);
        if (deviceId != 0L) overlayCache.put(deviceId, enabled);
    }

    public static boolean toggleOverlayEnabled(ItemStack stack) {
        boolean newState = !isOverlayEnabled(stack);
        setOverlayEnabled(stack, newState);

        return newState;
    }

    /**
     * Check if subnet scanning is enabled for this locator.
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

    @Override
    public void onUpdate(ItemStack stack, World world, Entity entity, int slot, boolean isHeld) {
        // Ensure device ID is assigned on first inventory tick
        if (!world.isRemote && !stack.isEmpty()) getDeviceId(stack);
    }

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos, EnumFacing side,
            float hitX, float hitY, float hitZ, EnumHand hand) {
        if (world.isRemote) return EnumActionResult.PASS;

        ItemStack stack = player.getHeldItem(hand);
        long deviceId = getDeviceId(stack);

        // Try to get grid from the clicked block
        IGrid grid = getGridFromPosition(world, pos, side);

        if (grid == null) {
            player.sendMessage(new TextComponentTranslation("item.ae2powertools.network_component_locator.no_network"));

            return EnumActionResult.FAIL;
        }

        // Scan all network components
        boolean includeSubnets = isSubnetScanEnabled(stack);
        ComponentScanner.ScanResult result = ComponentScanner.scan(grid, player, includeSubnets);

        // Send results to client
        PacketLocatorSync packet = new PacketLocatorSync(deviceId, result, includeSubnets);
        PowerToolsNetwork.INSTANCE.sendTo(packet, (EntityPlayerMP) player);

        return EnumActionResult.SUCCESS;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);

        if (world.isRemote) {
            long deviceId = getDeviceId(stack);

            if (player.isSneaking()) {
                // Shift-right-click: toggle overlay
                boolean enabled = toggleOverlayEnabled(stack);
                String key = enabled ?
                    "item.ae2powertools.network_component_locator.overlay_enabled" :
                    "item.ae2powertools.network_component_locator.overlay_disabled";
                player.sendMessage(new TextComponentTranslation(key));
            } else {
                // Regular right-click in air: open/reopen GUI with last results
                openGui(deviceId, isSubnetScanEnabled(stack));
            }

            return new ActionResult<>(EnumActionResult.SUCCESS, stack);
        }

        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }

    @SideOnly(Side.CLIENT)
    private void openGui(long deviceId, boolean subnetScanEnabled) {
        LocatorClientState.setActiveDeviceId(deviceId);
        // Initialize subnet state immediately so the GUI shows the correct state
        // before any scan data is received (avoids jarring "off" -> "on" flash)
        LocatorClientState.initSubnetState(deviceId, subnetScanEnabled);
        Minecraft.getMinecraft().displayGuiScreen(new GuiComponentLocator());
    }

    /**
     * Get the IGrid from a block position.
     * Tries IPartHost parts first, then IGridHost, fallback to all sides.
     */
    private IGrid getGridFromPosition(World world, BlockPos pos, EnumFacing side) {
        TileEntity te = world.getTileEntity(pos);
        if (te == null) return null;

        // Check if it's a part host (cable bus)
        if (te instanceof IPartHost) {
            IPartHost partHost = (IPartHost) te;

            AEPartLocation aeSide = AEPartLocation.fromFacing(side);
            IPart part = partHost.getPart(aeSide);

            if (part instanceof IGridHost) {
                IGridNode node = ((IGridHost) part).getGridNode(AEPartLocation.INTERNAL);
                if (node != null && node.getGrid() != null) return node.getGrid();
            }

            IPart cable = partHost.getPart(AEPartLocation.INTERNAL);
            if (cable instanceof IGridHost) {
                IGridNode node = ((IGridHost) cable).getGridNode(AEPartLocation.INTERNAL);
                if (node != null && node.getGrid() != null) return node.getGrid();
            }

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

        tooltip.add("");
        tooltip.add(TextFormatting.AQUA + I18n.format("item.ae2powertools.network_component_locator.tip1"));
        tooltip.add(TextFormatting.AQUA + I18n.format("item.ae2powertools.network_component_locator.tip2"));

        boolean overlayEnabled = isOverlayEnabled(stack);
        String overlayState = overlayEnabled ?
            TextFormatting.GREEN + I18n.format("item.ae2powertools.network_component_locator.overlay_on") + TextFormatting.AQUA :
            TextFormatting.RED + I18n.format("item.ae2powertools.network_component_locator.overlay_off") + TextFormatting.AQUA;
        tooltip.add(TextFormatting.AQUA + I18n.format("item.ae2powertools.network_component_locator.tip3", overlayState));
    }
}
