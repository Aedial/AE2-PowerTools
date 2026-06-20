package com.ae2powertools.features.monitor.alarm;

import io.netty.buffer.ByteBuf;

import net.minecraft.util.math.BlockPos;


/**
 * Lightweight client/server payload describing one active alarm position.
 */
public class AlarmLocation {

    private final int dimensionId;
    private final BlockPos pos;

    public AlarmLocation(int dimensionId, BlockPos pos) {
        this.dimensionId = dimensionId;
        this.pos = pos;
    }

    public int getDimensionId() {
        return dimensionId;
    }

    public BlockPos getPos() {
        return pos;
    }

    public void writeToBuf(ByteBuf buf) {
        buf.writeInt(dimensionId);
        buf.writeLong(pos.toLong());
    }

    public static AlarmLocation readFromBuf(ByteBuf buf) {
        return new AlarmLocation(buf.readInt(), BlockPos.fromLong(buf.readLong()));
    }
}