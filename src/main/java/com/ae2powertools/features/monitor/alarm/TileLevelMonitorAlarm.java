package com.ae2powertools.features.monitor.alarm;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import io.netty.buffer.ByteBuf;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.me.GridAccessException;
import appeng.api.networking.ticking.ITickManager;

import com.ae2powertools.features.monitor.MonitoredEntry;
import com.ae2powertools.features.monitor.dependent.MatchMode;
import com.ae2powertools.features.monitor.dependent.ComparisonMode;
import com.ae2powertools.features.monitor.dependent.MonitorHostType;
import com.ae2powertools.features.monitor.dependent.TileStorageMonitorBase;


/**
 * Alarm variant of the storage monitor. Instead of emitting redstone, it warns subscribed players
 * whenever any configured entry trips.
 * Unlike the other monitor types, the effect is triggered when ANY entry is red (not green).
 * All green (or disabled) = we are good, any red = panic. This allows for intuitive feedback.
 * The alarm therefore treats the AND of all enabled entries as the good state, then rings when
 * that shared good state is broken by any entry going red.
 * It is meant to act as a safety net for important resources you aim to maintain.
 * Getting under a threshold means your automation is either broken or cannot keep up.
 */
public class TileLevelMonitorAlarm extends TileStorageMonitorBase {

    private static final String NBT_REGISTERED_PLAYERS = "RegisteredPlayers";
    private static final String NBT_PLAYER_MOST = "Most";
    private static final String NBT_PLAYER_LEAST = "Least";

    private final Set<UUID> registeredPlayers = new HashSet<>();
    private boolean alarming;
    private boolean monitorStateKnown;

    public TileLevelMonitorAlarm() {
        setMatchMode(MatchMode.AND);
        normalizeEntriesInPlace();
    }

    @Override
    public void validate() {
        super.validate();
        if (world != null && !world.isRemote) {
            LevelMonitorAlarmManager.register(this);
        }
    }

    @Override
    public void invalidate() {
        if (world != null && !world.isRemote) {
            LevelMonitorAlarmManager.unregister(this);
        }

        super.invalidate();
    }

    @Override
    public void onChunkUnload() {
        if (world != null && !world.isRemote) {
            LevelMonitorAlarmManager.unregister(this);
        }

        super.onChunkUnload();
    }

    // TODO: Wait until network is stable (like Maintainer does)
    //       This would avoid briefly alarming on network reloads

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (world == null) return TickRateModulation.IDLE;
        if (!hasConnectedRegisteredPlayers()) return TickRateModulation.SLEEP;

        if (!refreshAndUpdateAlarming(false)) return TickRateModulation.IDLE;

        return TickRateModulation.SAME;
    }

    @Override
    public void onConditionChanged(boolean oldMet, boolean newMet) {
        updateAlarmingFromMonitorState(true);
    }

    @Override
    public MonitorHostType getHostType() {
        return MonitorHostType.ALARM;
    }

    @Override
    public boolean supportsMatchMode() {
        return false;
    }

    @Override
    public boolean supportsPlayerRegistration() {
        return true;
    }

    @Override
    public boolean supportsHysteresis() {
        return false;
    }

    @Override
    public boolean supportsEntryComparison() {
        return false;
    }

    @Override
    public boolean shouldRefreshWhileGuiOpen() {
        return true;
    }

    @Override
    public MatchMode getMatchMode() {
        return MatchMode.AND;
    }

    @Override
    public void setMatchMode(MatchMode mode) {
        if (monitorLogic.getMatchMode() != MatchMode.AND) {
            monitorLogic.setMatchMode(MatchMode.AND);
        }
    }

    @Override
    public boolean isHysteresisEnabled() {
        return false;
    }

    @Override
    public void setHysteresisEnabled(boolean hysteresisEnabled) {
        if (!monitorLogic.isHysteresisEnabled()) return;

        monitorLogic.setHysteresisEnabled(false);
    }

    @Override
    public void setEntries(List<MonitoredEntry> entries) {
        super.setEntries(normalizeEntries(entries));
    }

    @Override
    public void setEntry(int index, MonitoredEntry entry) {
        super.setEntry(index, normalizeEntry(entry));
    }

    @Override
    public boolean isPlayerRegistered(@Nullable EntityPlayer player) {
        return player != null && registeredPlayers.contains(player.getUniqueID());
    }

    public boolean isActiveFor(UUID playerId) {
        return alarming && registeredPlayers.contains(playerId);
    }

    @Override
    public boolean togglePlayerRegistration(@Nullable EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP) || world == null || world.isRemote) {
            return isPlayerRegistered(player);
        }

        boolean hadConnectedPlayers = hasConnectedRegisteredPlayers();
        UUID playerId = player.getUniqueID();
        boolean registered;

        if (registeredPlayers.contains(playerId)) {
            registeredPlayers.remove(playerId);
            registered = false;
        } else {
            registeredPlayers.add(playerId);
            registered = true;
        }

        markDirtyAndSave();

        boolean hasConnectedPlayers = hasConnectedRegisteredPlayers();
        if (!hadConnectedPlayers && hasConnectedPlayers) {
            wakeAndRefreshNow();
        } else if (hadConnectedPlayers && !hasConnectedPlayers) {
            sleepTickable();
        }

        LevelMonitorAlarmManager.syncPlayer((EntityPlayerMP) player);
        return registered;
    }

    public void onRegisteredPlayerAvailabilityChanged() {
        if (world == null || world.isRemote) return;

        if (hasConnectedRegisteredPlayers()) {
            wakeAndRefreshNow();
            return;
        }

        sleepTickable();
    }

    public List<EntityPlayerMP> getConnectedRegisteredPlayers() {
        List<EntityPlayerMP> connected = new ArrayList<>();
        if (world == null || world.getMinecraftServer() == null) return connected;

        for (EntityPlayerMP player : world.getMinecraftServer().getPlayerList().getPlayers()) {
            if (registeredPlayers.contains(player.getUniqueID())) connected.add(player);
        }

        return connected;
    }

    public boolean isOn() {
        return alarming;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);

        monitorLogic.readFromNBT(tag);
        setMatchMode(MatchMode.AND);
        setHysteresisEnabled(false);
        normalizeEntriesInPlace();
        registeredPlayers.clear();

        NBTTagList playerList = tag.getTagList(NBT_REGISTERED_PLAYERS, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < playerList.tagCount(); i++) {
            NBTTagCompound playerTag = playerList.getCompoundTagAt(i);
            registeredPlayers.add(new UUID(playerTag.getLong(NBT_PLAYER_MOST), playerTag.getLong(NBT_PLAYER_LEAST)));
        }

        alarming = false;
        monitorStateKnown = false;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        monitorLogic.writeToNBT(tag);

        NBTTagList playerList = new NBTTagList();
        for (UUID playerId : registeredPlayers) {
            NBTTagCompound playerTag = new NBTTagCompound();
            playerTag.setLong(NBT_PLAYER_MOST, playerId.getMostSignificantBits());
            playerTag.setLong(NBT_PLAYER_LEAST, playerId.getLeastSignificantBits());
            playerList.appendTag(playerTag);
        }

        tag.setTag(NBT_REGISTERED_PLAYERS, playerList);
        return tag;
    }

    @Override
    protected void writeToStream(ByteBuf data) throws IOException {
        super.writeToStream(data);
        data.writeBoolean(alarming);
    }

    @Override
    protected boolean readFromStream(ByteBuf data) throws IOException {
        boolean changed = super.readFromStream(data);
        boolean oldAlarming = alarming;
        alarming = data.readBoolean();
        return changed || oldAlarming != alarming;
    }

    private List<MonitoredEntry> normalizeEntries(List<MonitoredEntry> entries) {
        List<MonitoredEntry> normalized = new ArrayList<>(entries.size());

        for (MonitoredEntry entry : entries) normalized.add(normalizeEntry(entry));

        return normalized;
    }

    private void normalizeEntriesInPlace() {
        List<MonitoredEntry> entries = monitorLogic.getEntries();

        for (int i = 0; i < entries.size(); i++) {
            entries.set(i, normalizeEntry(entries.get(i)));
        }
    }

    private MonitoredEntry normalizeEntry(MonitoredEntry entry) {
        MonitoredEntry normalized = new MonitoredEntry(
            entry.getResource(),
            ComparisonMode.GREATER,
            entry.getThreshold(),
            entry.getThreshold(),
            entry.isEnabled()
        );

        normalized.setLastQuantity(entry.getLastQuantity());
        normalized.setLastConditionMet(entry.isLastConditionMet());

        return normalized;
    }

    private boolean hasConnectedRegisteredPlayers() {
        return !getConnectedRegisteredPlayers().isEmpty();
    }

    private void wakeAndRefreshNow() {
        wakeTickable();
        refreshAndUpdateAlarming(true);
    }

    private boolean refreshAndUpdateAlarming(boolean syncRegisteredPlayers) {
        if (!monitorLogic.refresh()) {
            clearAlarmingWhileStateUnknown(syncRegisteredPlayers);
            return false;
        }

        if (!monitorStateKnown) updateAlarmingFromMonitorState(syncRegisteredPlayers);

        return true;
    }

    private void updateAlarmingFromMonitorState(boolean syncRegisteredPlayers) {
        monitorStateKnown = true;

        boolean newAlarming = monitorLogic.hasEnabledEntries() && !monitorLogic.isConditionMet();
        if (alarming != newAlarming) {
            alarming = newAlarming;
            notifyStateChanged();
        }

        if (syncRegisteredPlayers) LevelMonitorAlarmManager.syncRegisteredPlayers(this);
    }

    // Keep the alarm neutral until a real network sample confirms the monitor state.
    private void clearAlarmingWhileStateUnknown(boolean syncRegisteredPlayers) {
        boolean wasAlarming = alarming;

        monitorStateKnown = false;
        alarming = false;

        if (wasAlarming) notifyStateChanged();

        if (syncRegisteredPlayers && wasAlarming) {
            LevelMonitorAlarmManager.syncRegisteredPlayers(this);
        }
    }

    private void wakeTickable() {
        if (!gridProxy.isReady()) return;

        try {
            ITickManager tickManager = gridProxy.getTick();
            tickManager.wakeDevice(gridProxy.getNode());
        } catch (GridAccessException e) {
            // The network is not ready yet. The regular tick registration will pick the tile up later.
        }
    }

    private void sleepTickable() {
        if (!gridProxy.isReady()) return;

        try {
            ITickManager tickManager = gridProxy.getTick();
            tickManager.sleepDevice(gridProxy.getNode());
        } catch (GridAccessException e) {
            // The network is not ready yet, so there is nothing to put to sleep.
        }
    }

    private void notifyStateChanged() {
        if (world == null) return;

        markForUpdate();

        IBlockState state = world.getBlockState(pos);
        world.notifyBlockUpdate(pos, state, state, 3);
    }
}