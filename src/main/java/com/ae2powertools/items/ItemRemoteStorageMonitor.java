package com.ae2powertools.items;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

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
import net.minecraftforge.fml.common.Loader;
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
import baubles.api.BaublesApi;
import baubles.api.IBauble;

import com.ae2powertools.PowerToolsCreativeTab;
import com.ae2powertools.Tags;
import com.ae2powertools.features.monitor.MonitoredResource;
import com.ae2powertools.features.remotemonitor.RemoteMonitorSessionManager;
import com.ae2powertools.network.PacketRemoteMonitorOpenGui;
import com.ae2powertools.network.PacketRemoteMonitorSync;
import com.ae2powertools.network.PowerToolsNetwork;
import com.ae2powertools.util.PollingRateUtils;


/**
 * Wireless item that opens the Remote Storage Monitor GUI and keeps a RAM-only
 * monitoring session alive for the player's selected resources.
 */
@Optional.Interface(iface = "baubles.api.IBauble", modid = "baubles")
public class ItemRemoteStorageMonitor extends Item implements IWirelessTermHandler, IBauble {

    private static final String NBT_DEVICE_ID = "DeviceId";
    private static final String NBT_REFRESH_RATE = "RefreshRate";
    private static final String NBT_RESOURCES = "Resources";

    private static final Map<NBTTagCompound, Long> DEVICE_ID_CACHE = new IdentityHashMap<>();

    public ItemRemoteStorageMonitor() {
        this.setRegistryName(Tags.MODID, "remote_storage_monitor");
        this.setTranslationKey(Tags.MODID + ".remote_storage_monitor");
        this.setMaxStackSize(1);
        this.setCreativeTab(PowerToolsCreativeTab.instance);
    }

    public static long getDeviceId(ItemStack stack) {
        if (stack.isEmpty()) return 0L;

        NBTTagCompound nbt = stack.getTagCompound();
        if (nbt != null) {
            Long cached = DEVICE_ID_CACHE.get(nbt);
            if (cached != null) return cached;
        }

        if (nbt == null) {
            nbt = new NBTTagCompound();
            stack.setTagCompound(nbt);
        }

        if (!nbt.hasKey(NBT_DEVICE_ID)) nbt.setLong(NBT_DEVICE_ID, System.nanoTime());

        long deviceId = nbt.getLong(NBT_DEVICE_ID);
        DEVICE_ID_CACHE.put(nbt, deviceId);
        return deviceId;
    }

    public static ItemStack findMonitorByDeviceId(EntityPlayer player, long deviceId) {
        if (deviceId == 0L) return ItemStack.EMPTY;

        for (ItemStack stack : player.inventory.mainInventory) {
            if (isMatchingMonitor(stack, deviceId)) return stack;
        }

        for (ItemStack stack : player.inventory.offHandInventory) {
            if (isMatchingMonitor(stack, deviceId)) return stack;
        }

        if (Loader.isModLoaded("baubles")) {
            ItemStack baubleStack = findMonitorInBaubles(player, deviceId);
            if (!baubleStack.isEmpty()) return baubleStack;
        }

        return ItemStack.EMPTY;
    }

    private static boolean isMatchingMonitor(ItemStack stack, long deviceId) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ItemRemoteStorageMonitor)) return false;
        return getDeviceId(stack) == deviceId;
    }

    @Optional.Method(modid = "baubles")
    private static ItemStack findMonitorInBaubles(EntityPlayer player, long deviceId) {
        for (int slot = 0; slot < BaublesApi.getBaublesHandler(player).getSlots(); slot++) {
            ItemStack stack = BaublesApi.getBaublesHandler(player).getStackInSlot(slot);
            if (isMatchingMonitor(stack, deviceId)) return stack;
        }

        return ItemStack.EMPTY;
    }

    public static void syncToClient(EntityPlayerMP player, long deviceId) {
        RemoteMonitorSessionManager.RemoteMonitorSession session = RemoteMonitorSessionManager.getSession(player, deviceId);
        if (session == null) return;

        PowerToolsNetwork.INSTANCE.sendTo(
            new PacketRemoteMonitorSync(deviceId, session.getRefreshRate(), session.copyResources(), session.copyDeltas()),
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

            NBTTagCompound slotTag = new NBTTagCompound();
            slotTag = resource.writeToNBT();
            resourcesTag.setTag(Integer.toString(slotIndex), slotTag);
        }

        if (resourcesTag.getKeySet().isEmpty()) {
            tag.removeTag(NBT_RESOURCES);
            return;
        }

        tag.setTag(NBT_RESOURCES, resourcesTag);
    }

    @Override
    public void onUpdate(ItemStack stack, World world, Entity entity, int slot, boolean isHeld) {
        if (!world.isRemote && !stack.isEmpty()) getDeviceId(stack);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);

        if (world.isRemote) return new ActionResult<>(EnumActionResult.SUCCESS, stack);

        if (!validateWirelessAccess(player, stack)) {
            return new ActionResult<>(EnumActionResult.FAIL, stack);
        }

        long deviceId = getDeviceId(stack);
        RemoteMonitorSessionManager.RemoteMonitorSession session = RemoteMonitorSessionManager.getOrCreateSession(
            this,
            (EntityPlayerMP) player,
            stack,
            deviceId);
        session.noteSyncRequest(this, player, stack);
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
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
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
    public void addInformation(ItemStack stack, World world, List<String> tooltip, ITooltipFlag flag) {
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
        tooltip.add(TextFormatting.GRAY + I18n.format(
            "item.ae2powertools.remote_storage_monitor.tip2",
            PollingRateUtils.format(getStoredRefreshRate(stack))));
    }
}