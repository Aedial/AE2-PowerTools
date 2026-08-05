package com.ae2powertools.util;

import java.util.function.Consumer;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IContainerListener;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

import com.ae2powertools.network.PowerToolsNetwork;


/**
 * Small helper for container-driven sync paths that only target the players actually
 * viewing the container. The containers still decide when to send inside their own
 * detectAndSendChanges methods; this helper only removes the repeated listener casting.
 */
public final class ContainerListenerSync {

    private ContainerListenerSync() {}

    @Nullable
    public static EntityPlayerMP getPlayerListener(Object listener) {
        return listener instanceof EntityPlayerMP ? (EntityPlayerMP) listener : null;
    }

    public static void forEachPlayerListener(Iterable<?> listeners, Consumer<EntityPlayerMP> action) {
        for (Object listener : listeners) {
            EntityPlayerMP player = getPlayerListener(listener);
            if (player == null) continue;

            action.accept(player);
        }
    }

    public static void sendToPlayerListeners(Iterable<?> listeners, IMessage packet) {
        forEachPlayerListener(listeners, player -> PowerToolsNetwork.INSTANCE.sendTo(packet, player));
    }

    public static void sendWindowProperties(Container container, Iterable<IContainerListener> listeners, int... values) {
        for (IContainerListener listener : listeners) {
            for (int propertyId = 0; propertyId < values.length; propertyId++) {
                listener.sendWindowProperty(container, propertyId, values[propertyId]);
            }
        }
    }
}