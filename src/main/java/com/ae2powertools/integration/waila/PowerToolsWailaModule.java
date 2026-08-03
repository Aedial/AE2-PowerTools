package com.ae2powertools.integration.waila;

import java.util.List;

import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.event.FMLInterModComms;

import appeng.integration.modules.waila.BaseWailaDataProvider;

import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import mcp.mobius.waila.api.IWailaRegistrar;

import com.ae2powertools.features.crafter.AutoCrafterProbeHelper;
import com.ae2powertools.features.crafter.TileAutoCrafter;
import com.ae2powertools.features.maintainer.MaintainerProbeHelper;
import com.ae2powertools.features.maintainer.TileBetterLevelMaintainer;


public final class PowerToolsWailaModule {

    private PowerToolsWailaModule() {}

    public static void register() {
        FMLInterModComms.sendMessage(
            "waila",
            "register",
            PowerToolsWailaModule.class.getName() + ".register");
    }

    public static void register(IWailaRegistrar registrar) {
        registrar.registerBodyProvider(new AutoCrafterWailaDataProvider(), TileAutoCrafter.class);
        registrar.registerBodyProvider(new MaintainerWailaDataProvider(), TileBetterLevelMaintainer.class);
    }

    private static final class AutoCrafterWailaDataProvider extends BaseWailaDataProvider {

        @Override
        public List<String> getWailaBody(ItemStack itemStack,
                                         List<String> currentToolTip,
                                         IWailaDataAccessor accessor,
                                         IWailaConfigHandler config) {
            NBTTagCompound tag = accessor.getNBTData();
            AutoCrafterProbeHelper.ProbeData probeData = AutoCrafterProbeHelper.readWailaData(tag);
            if (!probeData.isValid()) return currentToolTip;

            // Separation line
            currentToolTip.add("");

            if (probeData.hasErrorLine()) {
                currentToolTip.add(I18n.format(probeData.getErrorKey()));
            }

            // We always show how many patterns we have, even if completely empty
            currentToolTip.add(I18n.format(
                probeData.getPatternSummaryKey(),
                (Object[]) probeData.getPatternSummaryArgs()));

            // Next operation countdown, always shown regardless of whether anything will run
            currentToolTip.add(I18n.format(
                AutoCrafterProbeHelper.NEXT_OPERATION_TOOLTIP_KEY,
                AutoCrafterProbeHelper.formatRemainingTime(probeData.getTicksUntilNextOperation())));

            currentToolTip.add(I18n.format(
                probeData.getTimingKey(),
                (Object[]) probeData.getTimingArgs()));

            return currentToolTip;
        }

        @Override
        public NBTTagCompound getNBTData(EntityPlayerMP player,
                                         TileEntity te,
                                         NBTTagCompound tag,
                                         World world,
                                         BlockPos pos) {
            if (te instanceof TileAutoCrafter) {
                AutoCrafterProbeHelper.writeWailaData((TileAutoCrafter) te, tag);
            }

            return tag;
        }
    }

    private static final class MaintainerWailaDataProvider extends BaseWailaDataProvider {

        @Override
        public List<String> getWailaBody(ItemStack itemStack,
                                         List<String> currentToolTip,
                                         IWailaDataAccessor accessor,
                                         IWailaConfigHandler config) {
            NBTTagCompound tag = accessor.getNBTData();
            MaintainerProbeHelper.ProbeData probeData = MaintainerProbeHelper.readWailaData(tag);
            if (!probeData.isValid()) return currentToolTip;

            // Separation line
            currentToolTip.add("");

            currentToolTip.add(I18n.format(
                probeData.getTimingKey(),
                (Object[]) probeData.getTimingArgs()));

            return currentToolTip;
        }

        @Override
        public NBTTagCompound getNBTData(EntityPlayerMP player,
                                         TileEntity te,
                                         NBTTagCompound tag,
                                         World world,
                                         BlockPos pos) {
            if (te instanceof TileBetterLevelMaintainer) {
                MaintainerProbeHelper.writeWailaData((TileBetterLevelMaintainer) te, tag);
            }

            return tag;
        }
    }
}