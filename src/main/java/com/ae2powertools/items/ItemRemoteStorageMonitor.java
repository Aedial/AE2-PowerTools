package com.ae2powertools.items;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.fml.common.network.IGuiHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.AEApi;
import appeng.api.features.ILocatable;
import appeng.api.features.IWirelessTermHandler;
import appeng.api.util.IConfigManager;
import appeng.core.localization.GuiText;
import appeng.core.localization.PlayerMessages;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.util.ConfigManager;
import appeng.util.Platform;

import baubles.api.BaubleType;
import baubles.api.IBauble;

import com.ae2powertools.PowerToolsCreativeTab;
import com.ae2powertools.Tags;
import com.ae2powertools.features.monitor.MonitoredResource;
import com.ae2powertools.features.remotemonitor.RemoteMonitorSessionManager;
import com.ae2powertools.network.PacketRemoteMonitorOpenGui;
import com.ae2powertools.network.PacketRemoteMonitorSync;
import com.ae2powertools.network.PowerToolsNetwork;
import com.ae2powertools.util.DeviceItemAccess;
import com.ae2powertools.util.FormatUtil;


/**
 * Wireless item that opens the Remote Storage Monitor GUI and keeps a RAM-only
 * monitoring session alive for the player's selected resources.
 */
@Optional.Interface(iface = "baubles.api.IBauble", modid = "baubles")
public class ItemRemoteStorageMonitor extends Item implements IWirelessTermHandler, IBauble {

    private static final String NBT_REFRESH_RATE = "RefreshRate";
    private static final String NBT_SLIDING_WINDOW = "SlidingWindow";
    private static final String NBT_RESOURCES = "Resources";

    private static final DeviceItemAccess DEVICE_ACCESS = new DeviceItemAccess(
        ItemRemoteStorageMonitor.class, true);

    public ItemRemoteStorageMonitor() {
        this.setRegistryName(Tags.MODID, "remote_storage_monitor");
        this.setTranslationKey(Tags.MODID + ".remote_storage_monitor");
        this.setMaxStackSize(1);
        this.setCreativeTab(PowerToolsCreativeTab.instance);
    }

    public static long getDeviceId(ItemStack stack) {
        return DEVICE_ACCESS.getOrCreateDeviceId(stack);
    }

    public static ItemStack getMonitorInInventory(EntityPlayer player, long deviceId) {
        return DEVICE_ACCESS.findDeviceOnPlayerById(player, deviceId);
    }

    public static ItemStack getHeldMonitor(EntityPlayer player, long deviceId) {
        return DEVICE_ACCESS.findHeldDeviceById(player, deviceId);
    }

    public static ItemStack getHeldMonitor(EntityPlayer player) {
        return DEVICE_ACCESS.findHeldDevice(player);
    }

    public static void clearMonitorLocationCache(UUID playerId, long deviceId) {
        DEVICE_ACCESS.clearCachedLocation(playerId, deviceId);
    }

    public static void syncToClient(EntityPlayerMP player, long deviceId) {
        RemoteMonitorSessionManager.RemoteMonitorSession session = RemoteMonitorSessionManager.getSession(player, deviceId);
        if (session == null) return;

        PowerToolsNetwork.INSTANCE.sendTo(
            new PacketRemoteMonitorSync(
                deviceId,
                session.getRefreshRate(),
                session.getSlidingWindow(),
                session.copyResources(),
                session.copyDeltas(),
                session.copyCurrentQuantities()),
            player);
    }

    public static int getStoredRefreshRate(ItemStack stack) {
        if (stack.isEmpty()) return RemoteMonitorSessionManager.DEFAULT_REFRESH_RATE;

        NBTTagCompound tag = Platform.openNbtData(stack);
        int refreshRate = tag.hasKey(NBT_REFRESH_RATE)
            ? tag.getInteger(NBT_REFRESH_RATE)
            : RemoteMonitorSessionManager.DEFAULT_REFRESH_RATE;

        return Math.max(RemoteMonitorSessionManager.MIN_REFRESH_RATE, refreshRate);
    }

    public static void setStoredRefreshRate(ItemStack stack, int refreshRate) {
        if (stack.isEmpty()) return;

        Platform.openNbtData(stack).setInteger(
            NBT_REFRESH_RATE,
            Math.max(RemoteMonitorSessionManager.MIN_REFRESH_RATE, refreshRate));
    }

    public static int getStoredSlidingWindow(ItemStack stack) {
        if (stack.isEmpty()) return RemoteMonitorSessionManager.DEFAULT_SLIDING_WINDOW;

        NBTTagCompound tag = Platform.openNbtData(stack);
        int slidingWindow = tag.hasKey(NBT_SLIDING_WINDOW)
            ? tag.getInteger(NBT_SLIDING_WINDOW)
            : getStoredRefreshRate(stack);

        return Math.max(RemoteMonitorSessionManager.MIN_REFRESH_RATE, slidingWindow);
    }

    public static void setStoredSlidingWindow(ItemStack stack, int slidingWindow) {
        if (stack.isEmpty()) return;

        Platform.openNbtData(stack).setInteger(
            NBT_SLIDING_WINDOW,
            Math.max(RemoteMonitorSessionManager.MIN_REFRESH_RATE, slidingWindow));
    }

    public static MonitoredResource[] getStoredResources(ItemStack stack) {
        MonitoredResource[] resources = new MonitoredResource[RemoteMonitorSessionManager.SLOT_COUNT];
        if (stack.isEmpty()) return resources;

        NBTTagCompound tag = Platform.openNbtData(stack);
        if (!tag.hasKey(NBT_RESOURCES)) return resources;

        NBTTagCompound resourcesTag = tag.getCompoundTag(NBT_RESOURCES);
        for (int slot = 0; slot < resources.length; slot++) {
            String slotKey = Integer.toString(slot);
            if (!resourcesTag.hasKey(slotKey)) continue;

            NBTTagCompound slotTag = resourcesTag.getCompoundTag(slotKey);
            resources[slot] = MonitoredResource.readFromNBT(slotTag);
        }

        return resources;
    }

    public static void setStoredResource(ItemStack stack, int slotIndex, @Nullable MonitoredResource resource) {
        if (stack.isEmpty() || slotIndex < 0 || slotIndex >= RemoteMonitorSessionManager.SLOT_COUNT) return;

        MonitoredResource[] resources = getStoredResources(stack);
        resources[slotIndex] = resource;
        setStoredResources(stack, resources);
    }

    public static void setStoredResources(ItemStack stack, MonitoredResource[] resources) {
        if (stack.isEmpty()) return;

        NBTTagCompound tag = Platform.openNbtData(stack);
        NBTTagCompound resourcesTag = new NBTTagCompound();

        for (int slotIndex = 0; slotIndex < resources.length; slotIndex++) {
            MonitoredResource resource = resources[slotIndex];
            if (resource == null) continue;

            NBTTagCompound slotTag = resource.writeToNBT();
            resourcesTag.setTag(Integer.toString(slotIndex), slotTag);
        }

        if (resourcesTag.getKeySet().isEmpty()) {
            tag.removeTag(NBT_RESOURCES);
            return;
        }

        tag.setTag(NBT_RESOURCES, resourcesTag);
    }

    @Override
    public void onUpdate(@Nonnull ItemStack stack, World world, @Nonnull Entity entity, int slot, boolean isHeld) {
        if (!world.isRemote && !stack.isEmpty()) getDeviceId(stack);
    }

    @Override
    @Nonnull
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, @Nonnull EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);

        if (world.isRemote) return new ActionResult<>(EnumActionResult.SUCCESS, stack);

        if (!validateWirelessAccess(player, stack)) {
            return new ActionResult<>(EnumActionResult.FAIL, stack);
        }

        long deviceId = getDeviceId(stack);
        RemoteMonitorSessionManager.getOrCreateSession(this, (EntityPlayerMP) player, stack, deviceId);
        syncToClient((EntityPlayerMP) player, deviceId);
        PowerToolsNetwork.INSTANCE.sendTo(new PacketRemoteMonitorOpenGui(deviceId), (EntityPlayerMP) player);

        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }

    private boolean validateWirelessAccess(EntityPlayer player, ItemStack stack) {
        String encryptionKey = getEncryptionKey(stack);
        if (encryptionKey == null || encryptionKey.isEmpty()) {
            player.sendMessage(PlayerMessages.DeviceNotLinked.get());
            return false;
        }

        try {
            long parsedKey = Long.parseLong(encryptionKey);
            ILocatable securityStation = AEApi.instance().registries().locatable().getLocatableBy(parsedKey);
            if (securityStation == null) {
                player.sendMessage(PlayerMessages.StationCanNotBeLocated.get());
                return false;
            }
        } catch (NumberFormatException e) {
            player.sendMessage(PlayerMessages.DeviceNotLinked.get());
            return false;
        }

        WirelessTerminalGuiObject wireless = new WirelessTerminalGuiObject(this, stack, player, player.world, -1, 0, 0);
        if (!wireless.rangeCheck()) {
            player.sendMessage(PlayerMessages.OutOfRange.get());
            return false;
        }

        return true;
    }

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
        ConfigManager out = new ConfigManager((manager, settingName, newValue) -> {
            NBTTagCompound data = Platform.openNbtData(target);
            manager.writeToNBT(data);
        });
        out.readFromNBT(Platform.openNbtData(target).copy());
        return out;
    }

    @Override
    public IGuiHandler getGuiHandler(ItemStack is) {
        return null;
    }

    @Override
    public String getEncryptionKey(ItemStack item) {
        NBTTagCompound tag = Platform.openNbtData(item);
        return tag.getString("encryptionKey");
    }

    @Override
    public void setEncryptionKey(ItemStack item, String encKey, String name) {
        NBTTagCompound tag = Platform.openNbtData(item);
        tag.setString("encryptionKey", encKey);
        tag.setString("name", name);
    }

    @Override
    public boolean shouldCauseReequipAnimation(@Nonnull ItemStack oldStack, @Nonnull ItemStack newStack, boolean slotChanged) {
        return slotChanged;
    }

    @Optional.Method(modid = "baubles")
    @Override
    public BaubleType getBaubleType(ItemStack itemStack) {
        return BaubleType.TRINKET;
    }

    @Optional.Method(modid = "baubles")
    @Override
    public void onWornTick(ItemStack itemstack, EntityLivingBase player) {
        // The remote monitor has no passive bauble-side ticking beyond normal inventory updates.
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(@Nonnull ItemStack stack, World world, @Nonnull List<String> tooltip,
            @Nonnull ITooltipFlag flag) {
        super.addInformation(stack, world, tooltip, flag);

        String encryptionKey = null;
        if (stack.hasTagCompound()) {
            encryptionKey = Platform.openNbtData(stack).getString("encryptionKey");
        }

        if (encryptionKey == null || encryptionKey.isEmpty()) {
            tooltip.add(TextFormatting.RED + GuiText.Unlinked.getLocal());
        } else {
            tooltip.add(TextFormatting.GREEN + GuiText.Linked.getLocal());
        }

        tooltip.add("");
        tooltip.add(TextFormatting.AQUA + I18n.format("item.ae2powertools.remote_storage_monitor.tip1"));
        tooltip.add(TextFormatting.GRAY + I18n.format("item.ae2powertools.remote_storage_monitor.tip2"));
        tooltip.add(TextFormatting.GRAY + I18n.format(
            "item.ae2powertools.remote_storage_monitor.tip3",
            FormatUtil.formatTimeTicks(getStoredRefreshRate(stack)),
            FormatUtil.formatTimeTicks(getStoredSlidingWindow(stack))));
    }
}