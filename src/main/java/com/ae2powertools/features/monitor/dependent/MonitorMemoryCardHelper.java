package com.ae2powertools.features.monitor.dependent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Optional.Method;

import appeng.api.AEApi;
import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.implementations.items.IMemoryCard;
import appeng.api.implementations.items.MemoryCardMessages;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;
import appeng.items.misc.ItemEncodedPattern;
import appeng.fluids.util.AEFluidStack;

import com.ae2powertools.Tags;
import com.ae2powertools.features.crafter.BlockAutoCrafter;
import com.ae2powertools.features.maintainer.BlockBetterLevelMaintainer;
import com.ae2powertools.features.maintainer.MaintainerEntry;
import com.ae2powertools.features.monitor.MonitoredEntry;
import com.ae2powertools.features.monitor.MonitoredResource;
import com.ae2powertools.features.monitor.alarm.BlockLevelMonitorAlarm;
import com.ae2powertools.features.monitor.display.BlockStorageDisplay;
import com.ae2powertools.features.monitor.emitter.BlockStorageLevelEmitter;
import com.ae2powertools.util.Ae2FluidCraftingCompat;
import com.ae2powertools.util.Ae2FluidCraftingGasCompat;


/**
 * Shared custom memory-card handling for the monitor family.
 * Supports same-family copies plus alarm imports from the maintainer and AutoCrafter.
 * This code is not meant to be used outside of the monitor family,
 * as it expects memory/ hosts and converts the data to this specific format.
 */
public final class MonitorMemoryCardHelper {

    private static final String NBT_REFRESH_RATE = "RefreshRate";
    private static final String NBT_ENTRIES = "Entries";
    private static final String NBT_MATCH_MODE = "MatchMode";
    private static final String NBT_HYSTERESIS_ENABLED = "HysteresisEnabled";
    private static final String NBT_PATTERNS = "patterns";
    private static final String NBT_PATTERN = "patternNBT";

    private static final String NAME_ALARM = getBlockMemoryCardName(BlockLevelMonitorAlarm.NAME);
    private static final String NAME_DISPLAY = getBlockMemoryCardName(BlockStorageDisplay.NAME);
    private static final String NAME_EMITTER = getBlockMemoryCardName(BlockStorageLevelEmitter.NAME);
    private static final String NAME_MAINTAINER = getBlockMemoryCardName(BlockBetterLevelMaintainer.NAME);
    private static final String NAME_AUTO_CRAFTER = getBlockMemoryCardName(BlockAutoCrafter.NAME);

    private MonitorMemoryCardHelper() {}

    public static boolean handleMemoryCard(World world, EntityPlayer player, ItemStack heldItem, IStorageMonitorHost host) {
        if (heldItem.isEmpty() || !(heldItem.getItem() instanceof IMemoryCard)) return false;
        if (world == null || world.isRemote) return true;

        IMemoryCard memoryCard = (IMemoryCard) heldItem.getItem();
        if (player.isSneaking()) {
            saveToMemoryCard(player, heldItem, memoryCard, host);
        } else {
            loadFromMemoryCard(world, player, heldItem, memoryCard, host);
        }

        return true;
    }

    private static void saveToMemoryCard(EntityPlayer player, ItemStack memCardStack, IMemoryCard memoryCard,
            IStorageMonitorHost host) {
        NBTTagCompound data = new NBTTagCompound();
        data.setInteger(NBT_REFRESH_RATE, host.getRefreshRate());
        data.setInteger(NBT_MATCH_MODE, host.getMatchMode().getId());
        data.setBoolean(NBT_HYSTERESIS_ENABLED, host.isHysteresisEnabled());
        data.setString("tooltip", host.getMemoryCardTooltipKey());

        NBTTagList entries = new NBTTagList();
        for (MonitoredEntry entry : host.getEntries()) entries.appendTag(entry.writeToNBT());

        data.setTag(NBT_ENTRIES, entries);

        memoryCard.setMemoryCardContents(memCardStack, host.getMemoryCardName(), data);
        memoryCard.notifyUser(player, MemoryCardMessages.SETTINGS_SAVED);
    }

    private static void loadFromMemoryCard(World world, EntityPlayer player, ItemStack memCardStack,
            IMemoryCard memoryCard, IStorageMonitorHost host) {
        String savedName = memoryCard.getSettingsName(memCardStack);
        NBTTagCompound data = memoryCard.getData(memCardStack);

        if (isMonitorCard(savedName, data)) {
            applyMonitorData(host, data);
            memoryCard.notifyUser(player, MemoryCardMessages.SETTINGS_LOADED);
            return;
        }

        if (host.getHostType() == MonitorHostType.ALARM && isMaintainerCard(savedName, data)) {
            importMaintainerTargets(host, data);
            memoryCard.notifyUser(player, MemoryCardMessages.SETTINGS_LOADED);
            return;
        }

        if (host.getHostType() == MonitorHostType.ALARM && isAutoCrafterCard(savedName, data)) {
            importAutoCrafterPatterns(world, host, data);
            memoryCard.notifyUser(player, MemoryCardMessages.SETTINGS_LOADED);
            return;
        }

        memoryCard.notifyUser(player, MemoryCardMessages.INVALID_MACHINE);
    }

    private static boolean isMonitorCard(String savedName, NBTTagCompound data) {
        if (data == null || !data.hasKey(NBT_ENTRIES)) return false;

        return NAME_EMITTER.equals(savedName)
            || NAME_DISPLAY.equals(savedName)
            || NAME_ALARM.equals(savedName);
    }

    private static boolean isMaintainerCard(String savedName, NBTTagCompound data) {
        return NAME_MAINTAINER.equals(savedName) && data != null && data.hasKey("entries");
    }

    private static boolean isAutoCrafterCard(String savedName, NBTTagCompound data) {
        return NAME_AUTO_CRAFTER.equals(savedName) && data != null && data.hasKey(NBT_PATTERNS);
    }

    private static void applyMonitorData(IStorageMonitorHost host, NBTTagCompound data) {
        if (data.hasKey(NBT_REFRESH_RATE)) host.setRefreshRate(data.getInteger(NBT_REFRESH_RATE));
        if (data.hasKey(NBT_HYSTERESIS_ENABLED)) host.setHysteresisEnabled(data.getBoolean(NBT_HYSTERESIS_ENABLED));
        if (host.supportsMatchMode() && data.hasKey(NBT_MATCH_MODE)) {
            host.setMatchMode(MatchMode.fromId(data.getInteger(NBT_MATCH_MODE)));
        }

        NBTTagList entryList = data.getTagList(NBT_ENTRIES, Constants.NBT.TAG_COMPOUND);
        List<MonitoredEntry> entries = new ArrayList<>(entryList.tagCount());
        for (int i = 0; i < entryList.tagCount(); i++) {
            MonitoredEntry entry = MonitoredEntry.readFromNBT(entryList.getCompoundTagAt(i));
            if (entry != null) entries.add(entry);
        }

        host.setEntries(mergeEntries(host.getEntries(), entries, true));
    }

    private static void importMaintainerTargets(IStorageMonitorHost host, NBTTagCompound data) {
        NBTTagList entryList = data.getTagList("entries", Constants.NBT.TAG_COMPOUND);
        List<MonitoredEntry> entries = new ArrayList<>(entryList.tagCount());

        for (int i = 0; i < entryList.tagCount(); i++) {
            MaintainerEntry entry = new MaintainerEntry();
            entry.readFromNBT(entryList.getCompoundTagAt(i));
            if (!entry.hasRecipe()) continue;

            MonitoredResource resource = createResourceFromAeItem(entry.getTargetItem());
            if (resource == null) continue;

            entries.add(new MonitoredEntry(resource, ComparisonMode.LESS, entry.getTargetQuantity(), entry.getTargetQuantity(), entry.isEnabled()));
        }

        host.setEntries(mergeEntries(host.getEntries(), entries, false));
    }

    private static void importAutoCrafterPatterns(World world, IStorageMonitorHost host, NBTTagCompound data) {
        Optional<ItemStack> encodedPatternTemplate = AEApi.instance().definitions().items().encodedPattern().maybeStack(1);
        if (!encodedPatternTemplate.isPresent()) return;

        NBTTagList patternList = data.getTagList(NBT_PATTERNS, 10);
        List<MonitoredEntry> entries = new ArrayList<>(patternList.tagCount());

        for (int i = 0; i < patternList.tagCount(); i++) {
            NBTTagCompound entryTag = patternList.getCompoundTagAt(i);
            if (!entryTag.hasKey(NBT_PATTERN)) continue;

            ItemStack patternStack = encodedPatternTemplate.get().copy();
            patternStack.setTagCompound(entryTag.getCompoundTag(NBT_PATTERN).copy());

            MonitoredResource resource = createResourceFromPattern(patternStack, world);
            if (resource == null) continue;

            entries.add(new MonitoredEntry(resource, ComparisonMode.LESS, 0, 0, true));
        }

        host.setEntries(mergeEntries(host.getEntries(), entries, false));
    }

    /**
     * Merge imported targets into the existing monitor grid without losing current slots.
     * Existing slots keep their position, duplicate resources collapse to the first slot,
     * and same-family imports may refresh an already-configured resource in place.
     */
    private static List<MonitoredEntry> mergeEntries(
            List<MonitoredEntry> existingEntries,
            List<MonitoredEntry> importedEntries,
            boolean replaceExisting) {
        List<MonitoredEntry> merged = new ArrayList<>(Math.max(existingEntries.size(), MonitorLogic.GRID_CAPACITY));
        merged.addAll(existingEntries);

        while (merged.size() < MonitorLogic.GRID_CAPACITY) merged.add(MonitoredEntry.empty());

        Map<MonitoredResource.MonitoredResourceKey, Integer> occupiedSlots = new LinkedHashMap<>();
        List<Integer> emptySlots = new ArrayList<>();

        for (int i = 0; i < merged.size(); i++) {
            MonitoredEntry entry = merged.get(i);
            if (!entry.hasResource()) {
                emptySlots.add(i);
                continue;
            }

            Integer existingIndex = occupiedSlots.putIfAbsent(entry.getResource().toKey(), i);
            if (existingIndex == null) continue;

            merged.set(i, MonitoredEntry.empty());
            emptySlots.add(i);
        }

        for (MonitoredEntry imported : deduplicateImportedEntries(importedEntries)) {
            Integer existingIndex = occupiedSlots.get(imported.getResource().toKey());
            if (existingIndex != null) {
                if (!replaceExisting) continue;

                carryTransientState(merged.get(existingIndex), imported);
                merged.set(existingIndex, imported);
                continue;
            }

            if (emptySlots.isEmpty()) break;

            int targetIndex = emptySlots.remove(0);
            merged.set(targetIndex, imported);
            occupiedSlots.put(imported.getResource().toKey(), targetIndex);
        }

        return merged;
    }

    private static List<MonitoredEntry> deduplicateImportedEntries(List<MonitoredEntry> importedEntries) {
        Map<MonitoredResource.MonitoredResourceKey, MonitoredEntry> deduplicated = new LinkedHashMap<>();

        for (MonitoredEntry imported : importedEntries) {
            if (imported == null || !imported.hasResource()) continue;

            deduplicated.putIfAbsent(imported.getResource().toKey(), imported);
        }

        return new ArrayList<>(deduplicated.values());
    }

    private static void carryTransientState(MonitoredEntry existing, MonitoredEntry imported) {
        imported.setLastQuantity(existing.getLastQuantity());
        imported.setLastConditionMet(existing.isLastConditionMet());
    }

    @Nullable
    private static MonitoredResource createResourceFromPattern(ItemStack patternStack, @Nullable World world) {
        if (patternStack.isEmpty()) return null;

        ItemStack output = ItemStack.EMPTY;
        if (patternStack.getItem() instanceof ItemEncodedPattern) {
            output = ((ItemEncodedPattern) patternStack.getItem()).getOutput(patternStack);
        }

        if (output.isEmpty() && patternStack.getItem() instanceof ICraftingPatternItem) {
            ICraftingPatternDetails details = ((ICraftingPatternItem) patternStack.getItem()).getPatternForItem(patternStack, world);
            if (details == null || !details.isCraftable()) return null;

            for (IAEItemStack aeOutput : details.getOutputs()) {
                if (aeOutput != null) {
                    output = aeOutput.createItemStack();
                    break;
                }
            }
        }

        if (output.isEmpty()) return null;

        IAEItemStack aeOutput = AEItemStack.fromItemStack(output);
        return createResourceFromAeItem(aeOutput);
    }

    @Nullable
    private static MonitoredResource createResourceFromAeItem(@Nullable IAEItemStack itemStack) {
        if (itemStack == null) return null;

        FluidStack fluid = Ae2FluidCraftingCompat.extractFluid(itemStack);
        if (fluid != null) {
            IAEFluidStack aeFluid = AEFluidStack.fromFluidStack(fluid);
            return MonitoredResource.ofFluid(aeFluid);
        }

        if (Loader.isModLoaded("mekeng")) {
            MonitoredResource gas = createGasResource(itemStack);
            if (gas != null) return gas;
        }

        // No Essentia handling here as this part is content from patterns.
        // Might revisit it if we have essentia patterns later on.

        return MonitoredResource.ofItem(itemStack);
    }

    private static String getBlockMemoryCardName(String blockName) {
        return "tile." + Tags.MODID + "." + blockName;
    }

    @Nullable
    @Method(modid = "mekeng")
    private static MonitoredResource createGasResource(IAEItemStack itemStack) {
        mekanism.api.gas.GasStack gas = Ae2FluidCraftingGasCompat.extractGas(itemStack);
        if (gas == null || gas.getGas() == null) return null;

        com.mekeng.github.common.me.data.IAEGasStack aeGas = com.mekeng.github.common.me.data.impl.AEGasStack.of(gas);
        return aeGas != null ? MonitoredResource.ofGas(aeGas, gas.getGas().getLocalizedName()) : null;
    }
}