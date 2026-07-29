package com.ae2powertools.util;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Optional;

import appeng.util.Platform;

import baubles.api.BaublesApi;

import com.ae2powertools.AE2PowerTools;


/**
 * Common access rules for item-backed devices that store a unique device ID in NBT.
 */
public class DeviceItemAccess {

    private static final String NBT_DEVICE_ID = "DeviceId";
    private static final int RESCAN_INTERVAL_TICKS = 20;

    /** The class of the item this access object is responsible for. */
    private final Class<? extends Item> itemClass;
    /** Whether the item can be stored in a Baubles slot. */
    private final boolean shouldScanBaubles;
    /** Whether we should scan the player's inventory on top of hands and baubles. */
    private final boolean canScanInventory;
    /** Whether we should cache the player's locations. Anything more than hands is cached. */
    private final boolean cachePlayerLocations;

    private final Map<NBTTagCompound, Long> deviceIdCache = new IdentityHashMap<>();
    private final Map<LocationCacheKey, CachedLocation> locationCache = new HashMap<>();
    private final Map<ScanThrottleKey, Long> lastScanTicks = new HashMap<>();
    private boolean invalidUseWarningLogged = false;

    private enum CachedInventoryType {
        MAIN,
        OFFHAND,
        BAUBLES
    }

    private enum SearchScope {
        HELD,
        ON_PLAYER
    }

    private static final class LocationCacheKey {

        private final UUID playerId;
        private final long deviceId;

        private LocationCacheKey(UUID playerId, long deviceId) {
            this.playerId = playerId;
            this.deviceId = deviceId;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof LocationCacheKey)) return false;

            LocationCacheKey other = (LocationCacheKey) obj;
            return this.deviceId == other.deviceId && this.playerId.equals(other.playerId);
        }

        @Override
        public int hashCode() {
            return 31 * this.playerId.hashCode() + Long.hashCode(this.deviceId);
        }
    }

    private static final class ScanThrottleKey {

        private final UUID playerId;
        private final long deviceId;
        private final SearchScope scope;

        private ScanThrottleKey(UUID playerId, long deviceId, SearchScope scope) {
            this.playerId = playerId;
            this.deviceId = deviceId;
            this.scope = scope;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof ScanThrottleKey)) return false;

            ScanThrottleKey other = (ScanThrottleKey) obj;
            return this.deviceId == other.deviceId
                && this.scope == other.scope
                && this.playerId.equals(other.playerId);
        }

        @Override
        public int hashCode() {
            int result = this.playerId.hashCode();
            result = 31 * result + Long.hashCode(this.deviceId);
            result = 31 * result + this.scope.hashCode();
            return result;
        }
    }

    private static final class CachedLocation {

        private final CachedInventoryType inventory;
        private final int slotIndex;

        private CachedLocation(CachedInventoryType inventory, int slotIndex) {
            this.inventory = inventory;
            this.slotIndex = slotIndex;
        }

        private ItemStack resolve(DeviceItemAccess access, EntityPlayer player) {
            if (this.inventory == CachedInventoryType.MAIN) {
                if (this.slotIndex < 0 || this.slotIndex >= player.inventory.mainInventory.size()) return ItemStack.EMPTY;
                return player.inventory.mainInventory.get(this.slotIndex);
            }

            if (this.inventory == CachedInventoryType.OFFHAND) {
                if (this.slotIndex < 0 || this.slotIndex >= player.inventory.offHandInventory.size()) {
                    return ItemStack.EMPTY;
                }

                return player.inventory.offHandInventory.get(this.slotIndex);
            }

            if (access.canUseBaubles()) return access.getBaubleStack(player, this.slotIndex);

            return ItemStack.EMPTY;
        }
    }

    /**
     * Allows the caller to find and access items of the given class that store a unique device ID in NBT.
     * If you need inventory scanning on top of hands and baubles, use the second constructor.
     */
    public DeviceItemAccess(Class<? extends Item> itemClass) {
        this.itemClass = itemClass;
        this.shouldScanBaubles = canStoreInBaubles(itemClass);
        this.canScanInventory = false;
        this.cachePlayerLocations = this.shouldScanBaubles;
    }

    /**
     * Allows the caller to find and access items of the given class that store a unique device ID in NBT.
     * If shouldScanInventory is true, this will scan the player's main and offhand inventory for the device.
     * If shouldScanInventory is false, only the player's hands and baubles slots will be scanned.
     */
    public DeviceItemAccess(Class<? extends Item> itemClass, boolean shouldScanInventory) {
        this.itemClass = itemClass;
        this.shouldScanBaubles = canStoreInBaubles(itemClass);
        this.canScanInventory = shouldScanInventory;
        this.cachePlayerLocations = this.shouldScanBaubles || shouldScanInventory;
    }

    /**
     * Returns the device ID stored in the given item stack, or generates a new one if it doesn't exist.
     * A device ID of 0 is a universal invalid device ID. It can safely be used to indicate we have no device ID.
     */
    public long getOrCreateDeviceId(ItemStack stack) {
        if (stack.isEmpty()) return 0L;

        NBTTagCompound tag = stack.getTagCompound();
        if (tag != null) {
            Long cached = this.deviceIdCache.get(tag);
            if (cached != null) return cached;
        }

        tag = Platform.openNbtData(stack);
        if (!tag.hasKey(NBT_DEVICE_ID)) tag.setLong(NBT_DEVICE_ID, System.nanoTime());

        long deviceId = tag.getLong(NBT_DEVICE_ID);
        this.deviceIdCache.put(tag, deviceId);
        return deviceId;
    }

    public boolean hasDeviceId(ItemStack stack) {
        if (stack.isEmpty()) return false;

        NBTTagCompound tag = stack.getTagCompound();
        return tag != null && tag.hasKey(NBT_DEVICE_ID);
    }

    /**
     * Finds the first held device of the given type in the player's hands or baubles slot.
     * This should only ever be used when no device ID is known or the device is not a bauble.
     */
    public ItemStack findHeldDevice(EntityPlayer player) {
        if (player == null) return ItemStack.EMPTY;

        ItemStack mainHand = player.getHeldItemMainhand();
        if (this.matches(mainHand)) {
            this.cacheLocation(player, this.getOrCreateDeviceId(mainHand), CachedInventoryType.MAIN, player.inventory.currentItem);
            return mainHand;
        }

        ItemStack offHand = player.getHeldItemOffhand();
        if (this.matches(offHand)) {
            this.cacheLocation(player, this.getOrCreateDeviceId(offHand), CachedInventoryType.OFFHAND, 0);
            return offHand;
        }

        if (this.canUseBaubles()) return this.findBaubleDevice(player, 0L, null);

        return ItemStack.EMPTY;
    }

    public ItemStack findHeldDeviceById(EntityPlayer player, long deviceId) {
        if (player == null || deviceId == 0L) return ItemStack.EMPTY;

        LocationCacheKey cacheKey = new LocationCacheKey(player.getUniqueID(), deviceId);
        ItemStack cachedStack = this.findCachedDevice(player, cacheKey, SearchScope.HELD);
        if (!cachedStack.isEmpty()) return cachedStack;

        if (this.cachePlayerLocations && !this.canScan(player, deviceId, SearchScope.HELD)) return ItemStack.EMPTY;

        ItemStack mainHand = player.getHeldItemMainhand();
        if (this.matchesDeviceId(mainHand, deviceId)) {
            this.cacheLocation(cacheKey, CachedInventoryType.MAIN, player.inventory.currentItem);
            return mainHand;
        }

        ItemStack offHand = player.getHeldItemOffhand();
        if (this.matchesDeviceId(offHand, deviceId)) {
            this.cacheLocation(cacheKey, CachedInventoryType.OFFHAND, 0);
            return offHand;
        }

        if (this.canUseBaubles()) return this.findBaubleDevice(player, deviceId, cacheKey);

        return ItemStack.EMPTY;
    }

    public ItemStack findDeviceOnPlayerById(EntityPlayer player, long deviceId) {
        if (player == null || deviceId == 0L) return ItemStack.EMPTY;

        LocationCacheKey cacheKey = new LocationCacheKey(player.getUniqueID(), deviceId);
        ItemStack cachedStack = this.findCachedDevice(player, cacheKey, SearchScope.ON_PLAYER);
        if (!cachedStack.isEmpty()) return cachedStack;

        if (this.cachePlayerLocations && !this.canScan(player, deviceId, SearchScope.ON_PLAYER)) return ItemStack.EMPTY;

        ItemStack mainHand = player.getHeldItemMainhand();
        if (this.matchesDeviceId(mainHand, deviceId)) {
            this.cacheLocation(cacheKey, CachedInventoryType.MAIN, player.inventory.currentItem);
            return mainHand;
        }

        ItemStack offHand = player.getHeldItemOffhand();
        if (this.matchesDeviceId(offHand, deviceId)) {
            this.cacheLocation(cacheKey, CachedInventoryType.OFFHAND, 0);
            return offHand;
        }

        if (this.canUseBaubles()) {
            ItemStack baubleStack = this.findBaubleDevice(player, deviceId, cacheKey);
            if (!baubleStack.isEmpty()) return baubleStack;
        }

        if (!this.canScanInventory) {
            this.logInvalidInventoryScan();
            return ItemStack.EMPTY;
        }

        ItemStack mainInventoryMatch = this.findDeviceInInventory(
            player.inventory.mainInventory,
            deviceId,
            cacheKey,
            CachedInventoryType.MAIN);
        if (!mainInventoryMatch.isEmpty()) return mainInventoryMatch;

        ItemStack offHandInventoryMatch = this.findDeviceInInventory(
            player.inventory.offHandInventory,
            deviceId,
            cacheKey,
            CachedInventoryType.OFFHAND);
        if (!offHandInventoryMatch.isEmpty()) return offHandInventoryMatch;

        this.locationCache.remove(cacheKey);
        return ItemStack.EMPTY;
    }

    public void clearCachedLocation(UUID playerId, long deviceId) {
        this.locationCache.remove(new LocationCacheKey(playerId, deviceId));
        this.lastScanTicks.remove(new ScanThrottleKey(playerId, deviceId, SearchScope.HELD));
        this.lastScanTicks.remove(new ScanThrottleKey(playerId, deviceId, SearchScope.ON_PLAYER));
    }

    private boolean matches(ItemStack stack) {
        return !stack.isEmpty() && this.itemClass.isInstance(stack.getItem());
    }

    private boolean matchesDeviceId(ItemStack stack, long deviceId) {
        if (!this.matches(stack)) return false;

        return this.getOrCreateDeviceId(stack) == deviceId;
    }

    private ItemStack findCachedDevice(EntityPlayer player, LocationCacheKey cacheKey, SearchScope scope) {
        if (!this.cachePlayerLocations) return ItemStack.EMPTY;

        CachedLocation cachedLocation = this.locationCache.get(cacheKey);
        if (cachedLocation == null) return ItemStack.EMPTY;

        if (scope == SearchScope.HELD && !this.isHeldLocation(player, cachedLocation)) return ItemStack.EMPTY;

        ItemStack cachedStack = cachedLocation.resolve(this, player);
        if (this.matchesDeviceId(cachedStack, cacheKey.deviceId)) return cachedStack;

        this.locationCache.remove(cacheKey);
        return ItemStack.EMPTY;
    }

    private boolean isHeldLocation(EntityPlayer player, CachedLocation cachedLocation) {
        if (cachedLocation.inventory == CachedInventoryType.BAUBLES) return true;
        if (cachedLocation.inventory == CachedInventoryType.OFFHAND) return cachedLocation.slotIndex == 0;

        return cachedLocation.slotIndex == player.inventory.currentItem;
    }

    /**
     * Determines if the player can scan for the device with the given ID.
     * This is to throttle full inventory scans, which are fairly expensive.
     */
    private boolean canScan(EntityPlayer player, long deviceId, SearchScope scope) {
        ScanThrottleKey throttleKey = new ScanThrottleKey(player.getUniqueID(), deviceId, scope);
        long currentTick = player.world.getTotalWorldTime();
        Long lastScanTick = this.lastScanTicks.get(throttleKey);
        if (lastScanTick != null && currentTick - lastScanTick < RESCAN_INTERVAL_TICKS) return false;

        this.lastScanTicks.put(throttleKey, currentTick);
        return true;
    }

    private void cacheLocation(EntityPlayer player, long deviceId, CachedInventoryType inventory, int slotIndex) {
        if (deviceId == 0L) return;

        this.cacheLocation(new LocationCacheKey(player.getUniqueID(), deviceId), inventory, slotIndex);
    }

    private void cacheLocation(LocationCacheKey cacheKey, CachedInventoryType inventory, int slotIndex) {
        if (!this.cachePlayerLocations) return;

        this.locationCache.put(cacheKey, new CachedLocation(inventory, slotIndex));
    }

    private void logInvalidInventoryScan() {
        if (this.invalidUseWarningLogged) return;

        this.invalidUseWarningLogged = true;
        AE2PowerTools.LOGGER.warn(
            "Tried to scan the player inventory for {}, but this device access only supports held lookups.",
            this.itemClass.getName());
    }

    private ItemStack findDeviceInInventory(List<ItemStack> inventory, long deviceId, LocationCacheKey cacheKey,
            CachedInventoryType cachedInventory) {
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.get(slot);
            if (!this.matchesDeviceId(stack, deviceId)) continue;

            this.cacheLocation(cacheKey, cachedInventory, slot);
            return stack;
        }

        return ItemStack.EMPTY;
    }

    private boolean canUseBaubles() {
        return this.shouldScanBaubles;
    }

    private static boolean canStoreInBaubles(Class<? extends Item> itemClass) {
        return Loader.isModLoaded("baubles") && implementsBauble(itemClass);
    }

    @Optional.Method(modid = "baubles")
    private static boolean implementsBauble(Class<? extends Item> itemClass) {
        return baubles.api.IBauble.class.isAssignableFrom(itemClass);
    }

    @Optional.Method(modid = "baubles")
    private ItemStack findBaubleDevice(EntityPlayer player, long deviceId, @Nullable LocationCacheKey cacheKey) {
        baubles.api.cap.IBaublesItemHandler baubles = BaublesApi.getBaublesHandler(player);
        for (int slot = 0; slot < baubles.getSlots(); slot++) {
            ItemStack stack = baubles.getStackInSlot(slot);
            if (deviceId == 0L) {
                if (!this.matches(stack)) continue;
            } else if (!this.matchesDeviceId(stack, deviceId)) {
                continue;
            }

            long resolvedDeviceId = deviceId == 0L ? this.getOrCreateDeviceId(stack) : deviceId;
            if (cacheKey == null) cacheKey = new LocationCacheKey(player.getUniqueID(), resolvedDeviceId);
            this.cacheLocation(cacheKey, CachedInventoryType.BAUBLES, slot);

            return stack;
        }

        return ItemStack.EMPTY;
    }

    @Optional.Method(modid = "baubles")
    private ItemStack getBaubleStack(EntityPlayer player, int slotIndex) {
        baubles.api.cap.IBaublesItemHandler baubles = BaublesApi.getBaublesHandler(player);
        if (slotIndex < 0 || slotIndex >= baubles.getSlots()) return ItemStack.EMPTY;

        return baubles.getStackInSlot(slotIndex);
    }
}