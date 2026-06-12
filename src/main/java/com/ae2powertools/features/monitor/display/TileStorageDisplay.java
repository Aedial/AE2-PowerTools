package com.ae2powertools.features.monitor.display;

import java.io.IOException;

import io.netty.buffer.ByteBuf;

import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;

import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.TickRateModulation;

import com.ae2powertools.features.monitor.dependent.DisplayLogic;
import com.ae2powertools.features.monitor.dependent.MonitorHostType;
import com.ae2powertools.features.monitor.dependent.TileStorageMonitorBase;


/**
 * Tile entity for the ME Storage Display block.
 * Shows the configured resource icon and quantity on its facing face,
 * with a corner color indicator based on whether the condition is met.
 */
public class TileStorageDisplay extends TileStorageMonitorBase {

    private final DisplayLogic displayLogic;

    /** Facing direction for TESR rendering. Stored in tile rather than blockstate for simplicity. */
    private EnumFacing facing = EnumFacing.NORTH;

    public TileStorageDisplay() {
        this.displayLogic = new DisplayLogic(monitorLogic);
    }

    // --- AE2 Grid ticking ---

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (world == null) return TickRateModulation.IDLE;

        monitorLogic.refresh();
        boolean colorChanged = displayLogic.evaluate();

        if (colorChanged) {
            // Corner color changed, force block re-render for tinting update
            IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 3);
        }

        // Push a fresh client sync ONLY when something the client cares about (resource,
        // quantity, corner color) actually changed.
        if (displayLogic.pollSyncDirty()) markForUpdate();

        return TickRateModulation.SAME;
    }

    // --- Accessors ---

    public DisplayLogic getDisplayLogic() {
        return displayLogic;
    }

    /**
     * Returns the facing direction. Reads from the blockstate when possible so it stays
     * in sync with the client without us having to override AE2's writeToStream/readFromStream
     * (which are private to AEBaseTile and require canBeRotated() = true). The cached field is
     * a fallback for when the world isn't available yet (e.g. during early NBT load).
     */
    public EnumFacing getFacing() {
        if (world != null) {
            IBlockState state = world.getBlockState(pos);
            if (state.getBlock() instanceof BlockStorageDisplay) {
                return state.getValue(BlockStorageDisplay.FACING);
            }
        }
        return facing;
    }

    public void setFacing(EnumFacing facing) {
        this.facing = facing;
        markDirty();
    }

    // --- IMonitorLogicHost ---

    @Override
    public void onConditionChanged(boolean oldMet, boolean newMet) {
        boolean colorChanged = displayLogic.evaluate();

        if (world != null) {
            // Mark for TESR rendering update (item icon + quantity text)
            world.markBlockRangeForRenderUpdate(pos, pos);

            // If the corner color changed, force a block re-render so the tinted overlay updates
            if (colorChanged) {
                IBlockState state = world.getBlockState(pos);
                world.notifyBlockUpdate(pos, state, state, 3);
            }
        }
    }

    @Override
    public MonitorHostType getHostType() {
        return MonitorHostType.DISPLAY;
    }

    // --- NBT ---

    private static final String NBT_FACING = "Facing";

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        displayLogic.readFromNBT(tag);
        facing = EnumFacing.byIndex(tag.getInteger(NBT_FACING));
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        displayLogic.writeToNBT(tag);
        tag.setInteger(NBT_FACING, facing.getIndex());
        return tag;
    }

    // --- Network sync ---
    // We piggy-back on AEBaseTile's writeUpdateData() / handleUpdateTag() chain. Calling
    // super.writeToStream lets AE2 serialise its own canBeRotated() orientation byte; we
    // then append the small (resource, quantity, color) snapshot that the TESR / baked
    // model rely on. The client populates DisplayLogic.clientResource/quantity from this,
    // which is what makes the display visible without needing the full entries list (only
    // synced while the GUI is open).

    @Override
    protected void writeToStream(ByteBuf data) throws IOException {
        super.writeToStream(data);
        displayLogic.writeToStream(data);
    }

    @Override
    protected boolean readFromStream(ByteBuf data) throws IOException {
        super.readFromStream(data);
        displayLogic.readFromStream(data);
        // Always return true: the corner color tint and the displayed icon both come from
        // this stream, so we want a re-render every time the snapshot updates - even if
        // AE2's own orientation byte was unchanged.
        return true;
    }
}
