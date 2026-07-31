package com.ae2powertools.integration.theoneprobe;

import java.util.function.Function;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.event.FMLInterModComms;

import mcjty.theoneprobe.api.IProbeHitData;
import mcjty.theoneprobe.api.IProbeInfo;
import mcjty.theoneprobe.api.IProbeInfoProvider;
import mcjty.theoneprobe.api.ITheOneProbe;
import mcjty.theoneprobe.api.ProbeMode;

import com.ae2powertools.Tags;
import com.ae2powertools.features.crafter.AutoCrafterProbeHelper;
import com.ae2powertools.features.crafter.TileAutoCrafter;


public class PowerToolsTheOneProbePlugin implements Function<ITheOneProbe, Void>, IProbeInfoProvider {

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
        return Tags.MODID + ":auto_crafter";
    }

    @Override
    public void addProbeInfo(ProbeMode mode,
                             IProbeInfo probeInfo,
                             EntityPlayer player,
                             World world,
                             IBlockState blockState,
                             IProbeHitData data) {
        TileEntity tile = world.getTileEntity(data.getPos());
        if (!(tile instanceof TileAutoCrafter)) return;

        TileAutoCrafter crafter = (TileAutoCrafter) tile;
        if (probeInfo == null) return;

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
    }
}