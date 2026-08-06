package com.ae2powertools.features.tuner;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.util.AEPartLocation;
import appeng.helpers.IPriorityHost;

import com.ae2powertools.items.ItemPriorityTuner;
import com.ae2powertools.network.PacketPriorityApplied;
import com.ae2powertools.network.PowerToolsNetwork;


/**
 * Event handler for Priority Tuner auto-apply feature.
 * When a player places a block that supports priority (IPriorityHost) while
 * holding a Priority Tuner in their off-hand, automatically apply the stored priority.
 */
public class PriorityTunerEventHandler {

    @SubscribeEvent(priority = EventPriority.NORMAL)
    @SuppressWarnings("deprecation")
    public void onBlockPlace(BlockEvent.PlaceEvent event) {
        EntityPlayer player = event.getPlayer();
        if (player == null || player.world.isRemote) return;

        // Check if player has Priority Tuner in off-hand
        ItemStack offHandStack = player.getHeldItem(EnumHand.OFF_HAND);
        if (offHandStack.isEmpty() || !(offHandStack.getItem() instanceof ItemPriorityTuner)) return;

        // Schedule a delayed check for the TileEntity (it may not exist immediately after placement)
        World world = event.getWorld();
        BlockPos pos = event.getPos();

        // Use server scheduled task to check on next tick when TileEntity should exist
        if (player instanceof EntityPlayerMP) {
            EntityPlayerMP serverPlayer = (EntityPlayerMP) player;

            serverPlayer.getServerWorld().addScheduledTask(() -> {
                TileEntity te = world.getTileEntity(pos);
                if (te == null) return;

                ItemPriorityTuner tuner = (ItemPriorityTuner) offHandStack.getItem();
                int storedPriority = tuner.getStoredPriority(offHandStack);
                boolean applied = false;

                // Check if this is a part host (cable bus) - parts like buses can have priority
                if (te instanceof IPartHost) {
                    IPartHost partHost = (IPartHost) te;

                    for (AEPartLocation side : AEPartLocation.values()) {
                        IPart part = partHost.getPart(side);
                        if (!(part instanceof IPriorityHost)) continue;

                        IPriorityHost priorityHost = (IPriorityHost) part;
                        priorityHost.setPriority(storedPriority);
                        applied = true;
                    }
                }

                // Check if the tile entity itself is a priority host
                if (te instanceof IPriorityHost) {
                    IPriorityHost priorityHost = (IPriorityHost) te;
                    priorityHost.setPriority(storedPriority);
                    applied = true;
                }

                if (!applied) return;

                te.markDirty();

                // Send visual feedback
                PowerToolsNetwork.INSTANCE.sendTo(
                    new PacketPriorityApplied(pos, world.provider.getDimension(), storedPriority),
                    serverPlayer
                );
            });
        }
    }
}
