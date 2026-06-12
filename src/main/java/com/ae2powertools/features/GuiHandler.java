package com.ae2powertools.features;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;

import appeng.api.util.AEPartLocation;

import com.ae2powertools.features.crafter.ContainerAutoCrafter;
import com.ae2powertools.features.crafter.ContainerCrafterBatch;
import com.ae2powertools.features.crafter.ContainerCrafterSpeed;
import com.ae2powertools.features.crafter.CrafterGuiHandler;
import com.ae2powertools.features.crafter.GuiAutoCrafter;
import com.ae2powertools.features.crafter.GuiCrafterBatch;
import com.ae2powertools.features.crafter.GuiCrafterSpeed;
import com.ae2powertools.features.crafter.TileAutoCrafter;
import com.ae2powertools.features.maintainer.ContainerBetterLevelMaintainer;
import com.ae2powertools.features.maintainer.GuiBetterLevelMaintainer;
import com.ae2powertools.features.maintainer.TileBetterLevelMaintainer;
import com.ae2powertools.features.monitor.dependent.ContainerStorageMonitor;
import com.ae2powertools.features.monitor.dependent.ContainerStorageMonitorPollingRate;
import com.ae2powertools.features.monitor.dependent.GuiStorageMonitor;
import com.ae2powertools.features.monitor.dependent.GuiStorageMonitorPollingRate;
import com.ae2powertools.features.monitor.dependent.IStorageMonitorHost;
import com.ae2powertools.features.monitor.dependent.StorageMonitorHostResolver;


/**
 * GUI handler for AE2 PowerTools blocks.
 */
public class GuiHandler implements IGuiHandler {

    public static final int GUI_MAINTAINER = 0;
    public static final int GUI_STORAGE_MONITOR = 5;
    public static final int GUI_STORAGE_MONITOR_POLLING_RATE = 6;

    // --- Part GUI ID encoding ---
    // Cable parts can't be addressed by BlockPos alone (one cable bus may host several
    // monitor parts on different sides). We borrow the encoding pattern used by CELLS:
    // pack the AEPartLocation ordinal in the low 3 bits and the base GUI ID in the rest.
    // Tile-host GUIs continue to use plain IDs (the bottom 3 bits stay zero, which maps to
    // AEPartLocation.DOWN ordinal 0, but tiles never go through the part code paths so
    // there's no ambiguity in practice).
    /**
     * Encodes a base GUI ID together with a cable-part side, so the GuiHandler can resolve
     * the right part from the cable bus tile when reopening the GUI.
     * Mirrors {@code com.cells.gui.GuiIdUtils.encodePartGuiId}.
     */
    public static int encodePartGuiId(int baseGuiId, AEPartLocation side) {
        return (baseGuiId << 3) | (side.ordinal() & 0x07);
    }

    /** Bits 3+ of an encoded part GUI ID. Returns the base GUI ID for tiles unchanged when shifted right by zero. */
    private static int decodeBaseGuiId(int encodedId) {
        return encodedId >> 3;
    }

    /** Bits 0-2 of an encoded part GUI ID, mapped back to an AEPartLocation. */
    private static AEPartLocation decodeSide(int encodedId) {
        return AEPartLocation.fromOrdinal(encodedId & 0x07);
    }

    /**
     * Returns the storage-emitter host for the given encoded ID, if any.
     * If the ID is a part GUI (base >= 100), resolves the part on the encoded side.
     * Otherwise treats the tile at {@code pos} as the host.
     */
    private static IStorageMonitorHost resolveMonitorHost(int id, World world, BlockPos pos) {
        int base = decodeBaseGuiId(id);
        if (base >= 100) {
            return StorageMonitorHostResolver.resolve(world, pos, decodeSide(id));
        }

        TileEntity te = world.getTileEntity(pos);
        return te instanceof IStorageMonitorHost ? (IStorageMonitorHost) te : null;
    }

    // Part GUI IDs (encoded with side via {@link #encodePartGuiId}). The base values are >= 100
    // to stay outside the tile GUI ID range. Since the encoded form left-shifts by 3 bits, the
    // raw int sent over the wire is >= 800 (100 << 3), well clear of any tile ID we use.
    public static final int GUI_PART_STORAGE_MONITOR = 105;
    public static final int GUI_PART_STORAGE_MONITOR_POLLING_RATE = 106;

    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        TileEntity te = world.getTileEntity(pos);

        // Maintainer GUI
        if (id == GUI_MAINTAINER) {
            if (!(te instanceof TileBetterLevelMaintainer)) return null;

            TileBetterLevelMaintainer maintainer = (TileBetterLevelMaintainer) te;
            return new ContainerBetterLevelMaintainer(player.inventory, maintainer);
        }

        // Storage Emitter / Display main GUI (block tile or cable part).
        int base = decodeBaseGuiId(id);
        if (id == GUI_STORAGE_MONITOR || base == GUI_PART_STORAGE_MONITOR) {
            IStorageMonitorHost host = resolveMonitorHost(id, world, pos);
            return host != null ? new ContainerStorageMonitor(player.inventory, host) : null;
        }

        // Storage Emitter polling-rate sub-GUI (block tile or cable part).
        if (id == GUI_STORAGE_MONITOR_POLLING_RATE || base == GUI_PART_STORAGE_MONITOR_POLLING_RATE) {
            IStorageMonitorHost host = resolveMonitorHost(id, world, pos);
            return host != null ? new ContainerStorageMonitorPollingRate(player.inventory, host) : null;
        }

        // AutoCrafter GUIs
        if (te instanceof TileAutoCrafter) {
            TileAutoCrafter crafter = (TileAutoCrafter) te;

            switch (id) {
                case CrafterGuiHandler.GUI_CRAFTER:
                    return new ContainerAutoCrafter(player.inventory, crafter);
                case CrafterGuiHandler.GUI_CRAFTER_BATCH:
                    return new ContainerCrafterBatch(player.inventory, crafter);
                case CrafterGuiHandler.GUI_CRAFTER_SPEED:
                    return new ContainerCrafterSpeed(player.inventory, crafter);
            }
        }

        return null;
    }

    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        TileEntity te = world.getTileEntity(pos);

        // Maintainer GUI
        if (id == GUI_MAINTAINER) {
            if (!(te instanceof TileBetterLevelMaintainer)) return null;

            TileBetterLevelMaintainer maintainer = (TileBetterLevelMaintainer) te;
            return new GuiBetterLevelMaintainer(
                    new ContainerBetterLevelMaintainer(player.inventory, maintainer));
        }

        // Storage Emitter / Display main GUI (block tile or cable part).
        int base = decodeBaseGuiId(id);
        if (id == GUI_STORAGE_MONITOR || base == GUI_PART_STORAGE_MONITOR) {
            IStorageMonitorHost host = resolveMonitorHost(id, world, pos);
            return host != null
                ? new GuiStorageMonitor(new ContainerStorageMonitor(player.inventory, host))
                : null;
        }

        // Storage Emitter polling-rate sub-GUI (block tile or cable part).
        if (id == GUI_STORAGE_MONITOR_POLLING_RATE || base == GUI_PART_STORAGE_MONITOR_POLLING_RATE) {
            IStorageMonitorHost host = resolveMonitorHost(id, world, pos);
            return host != null
                ? new GuiStorageMonitorPollingRate(new ContainerStorageMonitorPollingRate(player.inventory, host))
                : null;
        }

        // AutoCrafter GUIs
        if (te instanceof TileAutoCrafter) {
            TileAutoCrafter crafter = (TileAutoCrafter) te;

            switch (id) {
                case CrafterGuiHandler.GUI_CRAFTER:
                    return new GuiAutoCrafter(new ContainerAutoCrafter(player.inventory, crafter));
                case CrafterGuiHandler.GUI_CRAFTER_BATCH:
                    return new GuiCrafterBatch(new ContainerCrafterBatch(player.inventory, crafter));
                case CrafterGuiHandler.GUI_CRAFTER_SPEED:
                    return new GuiCrafterSpeed(new ContainerCrafterSpeed(player.inventory, crafter));
            }
        }

        return null;
    }
}
