package com.ae2powertools.integration.theoneprobe;

import java.util.Optional;
import java.util.function.Function;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.event.FMLInterModComms;

import appeng.api.parts.IPart;
import appeng.integration.modules.theoneprobe.part.PartAccessor;

import mcjty.theoneprobe.api.IProbeHitData;
import mcjty.theoneprobe.api.IProbeInfo;
import mcjty.theoneprobe.api.IProbeInfoProvider;
import mcjty.theoneprobe.api.ITheOneProbe;
import mcjty.theoneprobe.api.ProbeMode;

import com.ae2powertools.Tags;
import com.ae2powertools.features.crafter.AutoCrafterProbeHelper;
import com.ae2powertools.features.crafter.TileAutoCrafter;
import com.ae2powertools.features.maintainer.MaintainerProbeHelper;
import com.ae2powertools.features.maintainer.TileBetterLevelMaintainer;
import com.ae2powertools.features.monitor.StorageMonitorProbeHelper;
import com.ae2powertools.features.monitor.StorageMonitorProbeHelper.AlarmProbeData;
import com.ae2powertools.features.monitor.StorageMonitorProbeHelper.EmitterProbeData;
import com.ae2powertools.features.monitor.alarm.TileLevelMonitorAlarm;
import com.ae2powertools.features.monitor.emitter.IEmitterCardHost;


public class PowerToolsTheOneProbePlugin implements Function<ITheOneProbe, Void>, IProbeInfoProvider {

    private final PartAccessor partAccessor = new PartAccessor();

    public static void register() {
        FMLInterModComms.sendFunctionMessage(
            "theoneprobe",
            "getTheOneProbe",
            PowerToolsTheOneProbePlugin.class.getName());
    }

    @Override
    public Void apply(ITheOneProbe input) {
        TopTranslatedTextElement.register(input);
        input.registerProvider(this);
        return null;
    }

    @Override
    public String getID() {
        return Tags.MODID + ":ae2_powertools";
    }

    @Override
    public void addProbeInfo(ProbeMode mode,
                             IProbeInfo probeInfo,
                             EntityPlayer player,
                             World world,
                             IBlockState blockState,
                             IProbeHitData data) {
        if (probeInfo == null) return;

        TileEntity tile = world.getTileEntity(data.getPos());
        if (appendTileProbeInfo(probeInfo, player, tile)) return;
        if (tile == null) return;

        Optional<IPart> maybePart = this.partAccessor.getMaybePart(tile, data);
        if (!maybePart.isPresent()) return;

        IPart part = maybePart.get();
        if (part instanceof IEmitterCardHost) {
            addEmitterInfo(probeInfo, (IEmitterCardHost) part);
        }
    }

    private boolean appendTileProbeInfo(IProbeInfo probeInfo, EntityPlayer player, TileEntity tile) {
        if (tile instanceof TileAutoCrafter) {
            addAutoCrafterInfo(probeInfo, (TileAutoCrafter) tile);
            return true;
        }

        if (tile instanceof TileBetterLevelMaintainer) {
            addMaintainerInfo(probeInfo, (TileBetterLevelMaintainer) tile);
            return true;
        }

        if (tile instanceof IEmitterCardHost) {
            addEmitterInfo(probeInfo, (IEmitterCardHost) tile);
            return true;
        }

        if (tile instanceof TileLevelMonitorAlarm) {
            addAlarmInfo(probeInfo, player, (TileLevelMonitorAlarm) tile);
            return true;
        }

        return false;
    }

    private void addAutoCrafterInfo(IProbeInfo probeInfo, TileAutoCrafter crafter) {
        AutoCrafterProbeHelper.ProbeData probeData = AutoCrafterProbeHelper.collectData(crafter);

        // Separation line
        probeInfo.text("");

        if (probeData.hasErrorLine()) {
            probeInfo.element(new TopTranslatedTextElement(probeData.getErrorKey()));
        }

        // We always show how many patterns we have, even if completely empty
        probeInfo.element(new TopTranslatedTextElement(
            probeData.getPatternSummaryKey(),
            probeData.getPatternSummaryArgs()));

        // Next operation countdown, always shown regardless of whether anything will run
        probeInfo.element(new TopTranslatedTextElement(
            AutoCrafterProbeHelper.NEXT_OPERATION_TOOLTIP_KEY,
            AutoCrafterProbeHelper.formatRemainingTime(probeData.getTicksUntilNextOperation())));

        probeInfo.element(new TopTranslatedTextElement(
            probeData.getTimingKey(),
            probeData.getTimingArgs()));
    }

    private void addMaintainerInfo(IProbeInfo probeInfo, TileBetterLevelMaintainer maintainer) {
        MaintainerProbeHelper.ProbeData probeData = MaintainerProbeHelper.collectData(maintainer);

        // Separation line
        probeInfo.text("");

        probeInfo.element(new TopTranslatedTextElement(
            MaintainerProbeHelper.STATUS_CPU_TOOLTIP_KEY,
            probeData.getCpuStatusArgs()));

        probeInfo.element(new TopTranslatedTextElement(
            MaintainerProbeHelper.STATUS_RECIPE_TOOLTIP_KEY,
            probeData.getRecipeStatusArgs()));

        probeInfo.element(new TopTranslatedTextElement(
            probeData.getTimingKey(),
            probeData.getTimingArgs()));
    }

    private void addEmitterInfo(IProbeInfo probeInfo, IEmitterCardHost emitter) {
        EmitterProbeData probeData = StorageMonitorProbeHelper.collectEmitterData(emitter);
        if (!probeData.isValid()) return;

        // Separation line
        probeInfo.text("");

        if (probeData.hasFuzzyCard()) {
            probeInfo.element(new TopTranslatedTextElement(StorageMonitorProbeHelper.EMITTER_FUZZY_CARD_TOOLTIP_KEY));
        }

        if (probeData.hasCraftingCard()) {
            probeInfo.element(new TopTranslatedTextElement(StorageMonitorProbeHelper.EMITTER_CRAFTING_CARD_TOOLTIP_KEY));
        }

        probeInfo.element(new TopTranslatedTextElement(probeData.getRedstoneModeKey()));
    }

    private void addAlarmInfo(IProbeInfo probeInfo, EntityPlayer player, TileLevelMonitorAlarm alarm) {
        AlarmProbeData probeData = StorageMonitorProbeHelper.collectAlarmData(alarm, player);
        if (!probeData.isValid()) return;

        // Separation line
        probeInfo.text("");

        probeInfo.element(new TopTranslatedTextElement(probeData.getRegistrationKey()));
    }
}