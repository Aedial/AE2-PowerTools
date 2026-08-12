package com.ae2powertools.features.remotemonitor;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Optional;

import appeng.api.AEApi;
import appeng.api.features.IWirelessTermHandler;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.helpers.WirelessTerminalGuiObject;

import com.ae2powertools.features.monitor.MonitoredResource;


/**
 * Shared wireless/network lookup helpers for the Remote Storage Monitor.
 * Uses AE2's WirelessTerminalGuiObject as the network access bridge so the item
 * follows Security Station linking and range checks like other wireless tools.
 */
public final class RemoteMonitorNetworkHelper {

    private RemoteMonitorNetworkHelper() {}

    public static List<MonitoredResource> queryAllResources(IWirelessTermHandler handler, EntityPlayer player,
            ItemStack monitorStack) {
        List<MonitoredResource> resources = new ArrayList<>();
        WirelessTerminalGuiObject wireless = createWirelessObject(handler, player, monitorStack);
        if (wireless == null || !wireless.rangeCheck()) return resources;

        collectChannel(
            wireless.getInventory(AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class)),
            resources);
        collectChannel(
            wireless.getInventory(AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class)),
            resources);

        if (Loader.isModLoaded("mekeng")) collectGasChannel(wireless, resources);
        if (Loader.isModLoaded("thaumicenergistics")) collectEssentiaChannel(wireless, resources);

        return resources;
    }

    public static boolean hasAccess(IWirelessTermHandler handler, EntityPlayer player, ItemStack monitorStack) {
        WirelessTerminalGuiObject wireless = createWirelessObject(handler, player, monitorStack);
        if (wireless == null || !wireless.rangeCheck()) return false;

        return true;
    }

    public static long lookupQuantity(IWirelessTermHandler handler, EntityPlayer player, ItemStack monitorStack,
            MonitoredResource resource) {
        if (resource == null || resource.getStack() == null) return 0;

        WirelessTerminalGuiObject wireless = createWirelessObject(handler, player, monitorStack);
        if (wireless == null || !wireless.rangeCheck()) return 0;

        IAEStack<?> stack = resource.getStack();
        switch (resource.getType()) {
            case ITEM:
                return lookupItemQuantity(wireless, (IAEItemStack) stack);
            case FLUID:
                return lookupFluidQuantity(wireless, (IAEFluidStack) stack);
            case GAS:
                if (Loader.isModLoaded("mekeng")) return lookupGasQuantity(wireless, stack);
                return 0;
            case ESSENTIA:
                if (Loader.isModLoaded("thaumicenergistics")) return lookupEssentiaQuantity(wireless, stack);
                return 0;
            default:
                return 0;
        }
    }

    @Nullable
    private static WirelessTerminalGuiObject createWirelessObject(IWirelessTermHandler handler, EntityPlayer player,
            ItemStack monitorStack) {
        if (handler == null || player == null || monitorStack == null || monitorStack.isEmpty()) return null;

        return new WirelessTerminalGuiObject(handler, monitorStack, player, player.world, -1, 0, 0);
    }

    private static long lookupItemQuantity(WirelessTerminalGuiObject wireless, IAEItemStack stack) {
        IMEMonitor<IAEItemStack> monitor = wireless.getInventory(
            AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));
        if (monitor == null) return 0;

        IAEItemStack found = monitor.getStorageList().findPrecise(stack);
        return found != null ? found.getStackSize() : 0;
    }

    private static long lookupFluidQuantity(WirelessTerminalGuiObject wireless, IAEFluidStack stack) {
        IMEMonitor<IAEFluidStack> monitor = wireless.getInventory(
            AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class));
        if (monitor == null) return 0;

        IAEFluidStack found = monitor.getStorageList().findPrecise(stack);
        return found != null ? found.getStackSize() : 0;
    }

    @Optional.Method(modid = "mekeng")
    private static long lookupGasQuantity(WirelessTerminalGuiObject wireless, IAEStack<?> stack) {
        IMEMonitor<com.mekeng.github.common.me.data.IAEGasStack> monitor = wireless.getInventory(
            AEApi.instance().storage().getStorageChannel(
                com.mekeng.github.common.me.storage.IGasStorageChannel.class));
        if (monitor == null) return 0;

        com.mekeng.github.common.me.data.IAEGasStack found = monitor.getStorageList().findPrecise(
            (com.mekeng.github.common.me.data.IAEGasStack) stack);
        return found != null ? found.getStackSize() : 0;
    }

    @Optional.Method(modid = "thaumicenergistics")
    private static long lookupEssentiaQuantity(WirelessTerminalGuiObject wireless, IAEStack<?> stack) {
        IMEMonitor<thaumicenergistics.api.storage.IAEEssentiaStack> monitor = wireless.getInventory(
            AEApi.instance().storage().getStorageChannel(
                thaumicenergistics.api.storage.IEssentiaStorageChannel.class));
        if (monitor == null) return 0;

        thaumicenergistics.api.storage.IAEEssentiaStack found = monitor.getStorageList().findPrecise(
            (thaumicenergistics.api.storage.IAEEssentiaStack) stack);
        return found != null ? found.getStackSize() : 0;
    }

    private static <T extends IAEStack<T>> void collectChannel(@Nullable IMEMonitor<T> monitor,
            List<MonitoredResource> resources) {
        if (monitor == null) return;

        IItemList<T> storageList = monitor.getStorageList();
        for (T stack : storageList) {
            if (stack == null || stack.getStackSize() <= 0) continue;

            MonitoredResource resource = createMonitoredResource(stack);
            if (resource != null) resources.add(resource);
        }
    }

    @Nullable
    private static MonitoredResource createMonitoredResource(IAEStack<?> stack) {
        if (stack instanceof IAEItemStack) return MonitoredResource.ofItem((IAEItemStack) stack, false);
        if (stack instanceof IAEFluidStack) return MonitoredResource.ofFluid((IAEFluidStack) stack, false);
        return null;
    }

    @Optional.Method(modid = "mekeng")
    private static void collectGasChannel(WirelessTerminalGuiObject wireless, List<MonitoredResource> resources) {
        IMEMonitor<com.mekeng.github.common.me.data.IAEGasStack> monitor = wireless.getInventory(
            AEApi.instance().storage().getStorageChannel(
                com.mekeng.github.common.me.storage.IGasStorageChannel.class));
        if (monitor == null) return;

        for (com.mekeng.github.common.me.data.IAEGasStack stack : monitor.getStorageList()) {
            if (stack == null || stack.getStackSize() <= 0) continue;

            resources.add(MonitoredResource.ofGas(stack, null));
        }
    }

    @Optional.Method(modid = "thaumicenergistics")
    private static void collectEssentiaChannel(WirelessTerminalGuiObject wireless, List<MonitoredResource> resources) {
        IMEMonitor<thaumicenergistics.api.storage.IAEEssentiaStack> monitor = wireless.getInventory(
            AEApi.instance().storage().getStorageChannel(
                thaumicenergistics.api.storage.IEssentiaStorageChannel.class));
        if (monitor == null) return;

        for (thaumicenergistics.api.storage.IAEEssentiaStack stack : monitor.getStorageList()) {
            if (stack == null || stack.getStackSize() <= 0) continue;

            resources.add(MonitoredResource.ofEssentia(stack, null));
        }
    }
}