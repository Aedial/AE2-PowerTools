package com.ae2powertools.integration.waila;

import java.util.List;
import java.util.Optional;

import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.event.FMLInterModComms;

import appeng.api.parts.IPart;
import appeng.integration.modules.waila.BaseWailaDataProvider;
import appeng.integration.modules.waila.part.PartAccessor;
import appeng.integration.modules.waila.part.Tracer;
import appeng.tile.AEBaseTile;

import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import mcp.mobius.waila.api.IWailaRegistrar;

import com.ae2powertools.features.crafter.AutoCrafterProbeHelper;
import com.ae2powertools.features.crafter.TileAutoCrafter;
import com.ae2powertools.features.maintainer.MaintainerProbeHelper;
import com.ae2powertools.features.maintainer.TileBetterLevelMaintainer;
import com.ae2powertools.features.monitor.StorageMonitorProbeHelper;
import com.ae2powertools.features.monitor.StorageMonitorProbeHelper.AlarmProbeData;
import com.ae2powertools.features.monitor.StorageMonitorProbeHelper.EmitterProbeData;
import com.ae2powertools.features.monitor.alarm.TileLevelMonitorAlarm;
import com.ae2powertools.features.monitor.emitter.IEmitterCardHost;


public final class PowerToolsWailaModule {

    private PowerToolsWailaModule() {}

    public static void register() {
        FMLInterModComms.sendMessage(
            "waila",
            "register",
            PowerToolsWailaModule.class.getName() + ".register");
    }

    public static void register(IWailaRegistrar registrar) {
        AutoCrafterWailaDataProvider autoCrafterProvider = new AutoCrafterWailaDataProvider();
        MaintainerWailaDataProvider maintainerProvider = new MaintainerWailaDataProvider();
        StorageMonitorWailaDataProvider storageMonitorProvider = new StorageMonitorWailaDataProvider();

        registrar.registerBodyProvider(autoCrafterProvider, TileAutoCrafter.class);
        registrar.registerNBTProvider(autoCrafterProvider, TileAutoCrafter.class);

        registrar.registerBodyProvider(maintainerProvider, TileBetterLevelMaintainer.class);
        registrar.registerNBTProvider(maintainerProvider, TileBetterLevelMaintainer.class);

        registrar.registerBodyProvider(storageMonitorProvider, AEBaseTile.class);
        registrar.registerNBTProvider(storageMonitorProvider, AEBaseTile.class);
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
                MaintainerProbeHelper.STATUS_CPU_TOOLTIP_KEY,
                (Object[]) probeData.getCpuStatusArgs()));

            currentToolTip.add(I18n.format(
                MaintainerProbeHelper.STATUS_RECIPE_TOOLTIP_KEY,
                (Object[]) probeData.getRecipeStatusArgs()));

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

    private static final class StorageMonitorWailaDataProvider extends BaseWailaDataProvider {

        private final PartAccessor partAccessor = new PartAccessor();
        private final Tracer tracer = new Tracer();

        @Override
        public List<String> getWailaBody(ItemStack itemStack,
                                         List<String> currentToolTip,
                                         IWailaDataAccessor accessor,
                                         IWailaConfigHandler config) {
            NBTTagCompound tag = accessor.getNBTData();

            EmitterProbeData emitterProbeData = StorageMonitorProbeHelper.readEmitterWailaData(tag);
            if (emitterProbeData.isValid()) {
                currentToolTip.add("");

                if (emitterProbeData.hasFuzzyCard()) {
                    currentToolTip.add(I18n.format(StorageMonitorProbeHelper.EMITTER_FUZZY_CARD_TOOLTIP_KEY));
                }

                if (emitterProbeData.hasCraftingCard()) {
                    currentToolTip.add(I18n.format(StorageMonitorProbeHelper.EMITTER_CRAFTING_CARD_TOOLTIP_KEY));
                }

                currentToolTip.add(I18n.format(emitterProbeData.getRedstoneModeKey()));
                return currentToolTip;
            }

            AlarmProbeData alarmProbeData = StorageMonitorProbeHelper.readAlarmWailaData(tag);
            if (!alarmProbeData.isValid()) return currentToolTip;

            currentToolTip.add("");
            currentToolTip.add(I18n.format(alarmProbeData.getRegistrationKey()));
            return currentToolTip;
        }

        @Override
        public NBTTagCompound getNBTData(EntityPlayerMP player,
                                         TileEntity te,
                                         NBTTagCompound tag,
                                         World world,
                                         BlockPos pos) {
            if (te instanceof IEmitterCardHost) {
                StorageMonitorProbeHelper.writeEmitterWailaData((IEmitterCardHost) te, tag);
                return tag;
            }

            if (te instanceof TileLevelMonitorAlarm) {
                StorageMonitorProbeHelper.writeAlarmWailaData((TileLevelMonitorAlarm) te, player, tag);
                return tag;
            }

            RayTraceResult hit = this.tracer.retraceBlock(world, player, pos);
            if (hit == null) return tag;

            Optional<IPart> maybePart = this.partAccessor.getMaybePart(te, hit);
            if (!maybePart.isPresent()) return tag;

            IPart part = maybePart.get();
            if (part instanceof IEmitterCardHost) {
                StorageMonitorProbeHelper.writeEmitterWailaData((IEmitterCardHost) part, tag);
            }

            return tag;
        }
    }
}