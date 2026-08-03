package com.ae2powertools.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.ae2powertools.features.monitor.dependent.IStorageMonitorHost;
import com.ae2powertools.features.monitor.dependent.StorageMonitorHostResolver;
import com.ae2powertools.features.monitor.emitter.IEmitterCardHost;
import com.ae2powertools.util.upgrade.ISelectableUpgradeInventory;
import com.ae2powertools.util.upgrade.UpgradeInventoryUtil;


/**
 * Client -> server packet that installs a compatible upgrade card from the player's
 * inventory into a storage monitor emitter slot, or removes the installed card back
 * to the player inventory.
 */
public class PacketModifyStorageMonitorUpgradeSlot implements IMessage {

    public enum Action {
        INSTALL_FROM_PLAYER_SLOT,
        REMOVE
    }

    private BlockPos pos;
    private byte side;
    private byte upgradeSlot;
    private byte playerSlot;
    private Action action;

    public PacketModifyStorageMonitorUpgradeSlot() {}

    public PacketModifyStorageMonitorUpgradeSlot(
            IStorageMonitorHost host,
            Action action,
            int upgradeSlot,
            int playerSlot) {
        this.pos = host.getHostPos();
        this.side = StorageMonitorHostResolver.encodeSide(host.getHostSide());
        this.upgradeSlot = (byte) upgradeSlot;
        this.playerSlot = (byte) playerSlot;
        this.action = action;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        pos = BlockPos.fromLong(buf.readLong());
        side = buf.readByte();
        upgradeSlot = buf.readByte();
        playerSlot = buf.readByte();

        int actionOrdinal = buf.readUnsignedByte();
        action = actionOrdinal >= 0 && actionOrdinal < Action.values().length
            ? Action.values()[actionOrdinal]
            : Action.REMOVE;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(pos.toLong());
        buf.writeByte(side);
        buf.writeByte(upgradeSlot);
        buf.writeByte(playerSlot);
        buf.writeByte(action.ordinal());
    }

    public static class Handler implements IMessageHandler<PacketModifyStorageMonitorUpgradeSlot, IMessage> {

        @Override
        public IMessage onMessage(PacketModifyStorageMonitorUpgradeSlot message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;

            player.getServerWorld().addScheduledTask(() -> {
                IStorageMonitorHost host = StorageMonitorHostResolver.resolve(player.world, message.pos, message.side);
                if (!(host instanceof IEmitterCardHost)) return;

                ISelectableUpgradeInventory inventory = ((IEmitterCardHost) host).getSelectableUpgradeInventory();
                if (inventory == null) return;

                boolean changed;
                switch (message.action) {
                    case INSTALL_FROM_PLAYER_SLOT:
                        changed = UpgradeInventoryUtil.installFromPlayerInventory(
                            inventory,
                            player,
                            message.upgradeSlot,
                            message.playerSlot);
                        break;

                    case REMOVE:
                    default:
                        changed = UpgradeInventoryUtil.removeToPlayerInventory(inventory, player, message.upgradeSlot);
                        break;
                }

                if (changed) {
                    player.openContainer.detectAndSendChanges();
                }
            });

            return null;
        }
    }
}