package com.ae2powertools.mixin.interfaceterminal;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.container.AEBaseContainer;
import appeng.container.implementations.ContainerInterfaceTerminal;
import appeng.container.slot.AppEngSlot;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketInventoryAction;
import appeng.core.sync.packets.PacketCompressedNBT;
import appeng.helpers.DualityInterface;
import appeng.helpers.IInterfaceHost;
import appeng.helpers.InventoryAction;
import appeng.parts.misc.PartInterface;
import appeng.parts.reporting.PartInterfaceTerminal;
import appeng.tile.misc.TileInterface;
import appeng.util.Platform;

import com.ae2powertools.features.crafter.TileAutoCrafter;
import com.ae2powertools.features.crafter.terminal.AutoCrafterPatternActions;
import com.ae2powertools.features.crafter.terminal.AutoCrafterTerminalClientState;
import com.ae2powertools.features.crafter.terminal.AutoCrafterTerminalTracker;


@Mixin(value = ContainerInterfaceTerminal.class, remap = false)
public abstract class MixinContainerInterfaceTerminal {

    @Shadow private IGrid grid;

    @Shadow private NBTTagCompound data;

    @Shadow private void regenList(NBTTagCompound data) {}

    // Server-side mirror of the synthetic Interface Terminal rows representing AutoCrafters.
    @Unique
    private final Map<Long, AutoCrafterTerminalTracker> ae2powertools$autoCrafterTrackers = new LinkedHashMap<>();

    /**
     * Queue the first AutoCrafter payload through AE2's pending NBT field so the initial update
     * is delivered after the client screen exists.
     */
    @Inject(method = "<init>(Lnet/minecraft/entity/player/InventoryPlayer;Lappeng/parts/reporting/PartInterfaceTerminal;)V", at = @At("RETURN"))
    private void ae2powertools$queueInitialAutoCrafterRows(InventoryPlayer inventoryPlayer, PartInterfaceTerminal anchor,
            CallbackInfo ci) {
        if (Platform.isClient()) return;
        if (ae2powertools$countVanillaInterfaceRows() > 0) return;

        Map<Long, AutoCrafterTerminalTracker> rebuilt = ae2powertools$rebuildTrackers();
        if (rebuilt.isEmpty()) return;

        NBTTagCompound fullData = new NBTTagCompound();
        fullData.setBoolean("clear", true);

        for (AutoCrafterTerminalTracker tracker : rebuilt.values()) {
            tracker.appendFullSync(fullData);
        }

        this.data = fullData;
    }

    /**
     * Append the full state of all AutoCrafter rows to the outgoing packet, so the client can
     * reconstruct the row model.
     */
    @Inject(method = "regenList", at = @At("RETURN"))
    private void ae2powertools$appendAutoCrafterRows(NBTTagCompound data, CallbackInfo ci) {
        ae2powertools$rebuildTrackers();

        for (AutoCrafterTerminalTracker tracker : ae2powertools$autoCrafterTrackers.values()) {
            tracker.appendFullSync(data);
        }
    }

    /**
     * Handle actions performed on AutoCrafter rows in the Interface Terminal,
     * such as inserting or extracting patterns.
     * <p>
      * PLACE_SINGLE: Sent when the player shift-clicks a pattern in their inventory into
      *               a disconnected terminal slot, so we treat it as "insert into first free row slot".
      * PICKUP_OR_SET_DOWN: Left-click the row slot, using the held stack when present or extracting
      *                     the current pattern to the cursor when the hand is empty.
      * SPLIT_OR_PLACE_SINGLE: Right-click the row slot. For pattern rows we keep the same behavior as
      *                        left-click because the AutoCrafter stores at most one pattern per entry.
     * SHIFT_CLICK: Triggered when the player shift-clicks a pattern in the AutoCrafter row,
     *              extracting it to their inventory.
     * CREATIVE_DUPLICATE: Triggered when the player middle-clicks a pattern in the AutoCrafter row,
     *                     duplicating it to their hand. Only allowed in creative mode.
     */
    @Inject(method = "doAction", at = @At("HEAD"), cancellable = true)
    private void ae2powertools$handleAutoCrafterActions(EntityPlayerMP player, InventoryAction action, int slot, long id,
            CallbackInfo ci) {
        AutoCrafterTerminalTracker tracker = ae2powertools$autoCrafterTrackers.get(id);
        if (tracker == null) return;

        switch (action) {
            case PLACE_SINGLE:
                ae2powertools$insertFromPlayerSlot(player, slot, tracker);
                break;
            case PICKUP_OR_SET_DOWN:
            case SPLIT_OR_PLACE_SINGLE:
                ae2powertools$handlePickup(player, tracker, slot);
                break;
            case SHIFT_CLICK:
                ae2powertools$extractToInventory(player, tracker, slot);
                break;
            case CREATIVE_DUPLICATE:
                ae2powertools$duplicateToHand(player, tracker, slot);
                break;
            default:
                break;
        }

        NBTTagCompound actionData = new NBTTagCompound();
        tracker.appendFullSync(actionData);
        ae2powertools$sendData(actionData);

        ae2powertools$baseAccessor().ae2powertools$invokeUpdateHeld(player);
        ci.cancel();
    }

    /**
     * Skip disabled slots when the player shift-clicks in their inventory,
     * so that the action does not try to place a pattern into a disabled slot,
     * which would be rejected by the server and do nothing on the client.
     */
    @Inject(method = "transferStackInSlot", at = @At("HEAD"), cancellable = true)
    private void ae2powertools$skipDisabledSlotsOnShiftClick(EntityPlayer player, int idx,
            CallbackInfoReturnable<ItemStack> cir) {
        if (!Platform.isClient()) return;

        Slot playerSlot = ae2powertools$baseContainer().inventorySlots.get(idx);
        if (!(playerSlot instanceof AppEngSlot)) return;

        AppEngSlot playerAppEngSlot = (AppEngSlot) playerSlot;
        if (!playerAppEngSlot.isPlayerSide()) return;

        for (Slot slot : ae2powertools$baseContainer().inventorySlots) {
            if (!(slot instanceof appeng.client.me.SlotDisconnected)) continue;

            appeng.client.me.SlotDisconnected disconnected = (appeng.client.me.SlotDisconnected) slot;
            if (AutoCrafterTerminalClientState.isDisabledSlot(
                    disconnected.getSlot().getId(),
                    disconnected.getSlotIndex())) {
                continue;
            }

            if (slot.getHasStack()) continue;

            NetworkHandler.instance().sendToServer(new PacketInventoryAction(
                    InventoryAction.PLACE_SINGLE,
                    playerAppEngSlot.slotNumber,
                    disconnected.getSlot().getId()));
            cir.setReturnValue(ItemStack.EMPTY);
            return;
        }
    }

    @Unique
    private Map<Long, AutoCrafterTerminalTracker> ae2powertools$rebuildTrackersInner() {
        Map<Long, AutoCrafterTerminalTracker> rebuilt = new LinkedHashMap<>();

        if (grid == null) return rebuilt;

        IActionHost host = ae2powertools$baseAccessor().ae2powertools$invokeGetActionHost();
        if (host == null) return rebuilt;

        IGridNode actionableNode = host.getActionableNode();
        if (actionableNode == null || !actionableNode.isActive()) return rebuilt;

        // AE2 indexes machine sets by the node's exact runtime class, so scanning the class buckets is
        // more reliable than assuming every AutoCrafter lives under one exact key.
        for (Class<? extends IGridHost> machineClass : grid.getMachinesClasses()) {
            if (!TileAutoCrafter.class.isAssignableFrom(machineClass)) continue;

            for (IGridNode node : grid.getMachines(machineClass)) {
                if (!node.isActive()) continue;

                Object machine = node.getMachine();
                if (!(machine instanceof TileAutoCrafter)) continue;

                TileAutoCrafter tile = (TileAutoCrafter) machine;
                AutoCrafterTerminalTracker tracker = new AutoCrafterTerminalTracker(tile);
                rebuilt.put(tracker.getId(), tracker);
            }
        }

        return rebuilt;
    }

    /**
     * Rebuild the server-side model of AutoCrafter rows, returning true if the structure has changed.
     * This ensures the mixin doesn't affect the Interface Terminal's row model when no AutoCrafters are present.
     */
    @Unique
    private Map<Long, AutoCrafterTerminalTracker> ae2powertools$rebuildTrackers() {
        Map<Long, AutoCrafterTerminalTracker> rebuilt = ae2powertools$rebuildTrackersInner();

        ae2powertools$autoCrafterTrackers.clear();
        ae2powertools$autoCrafterTrackers.putAll(rebuilt);

        return rebuilt;
    }

    @Unique
    private int ae2powertools$countVanillaInterfaceRows() {
        if (grid == null) return 0;

        int total = 0;
        total += ae2powertools$countVanillaInterfaceRows(TileInterface.class);
        total += ae2powertools$countVanillaInterfaceRows(PartInterface.class);
        return total;
    }

    @Unique
    private int ae2powertools$countVanillaInterfaceRows(Class<? extends IGridHost> machineClass) {
        int total = 0;

        for (IGridNode node : grid.getMachines(machineClass)) {
            if (!node.isActive()) continue;

            Object machine = node.getMachine();
            if (!(machine instanceof IInterfaceHost)) continue;

            DualityInterface duality = ((IInterfaceHost) machine).getInterfaceDuality();
            if (duality.getConfigManager().getSetting(Settings.INTERFACE_TERMINAL) == YesNo.NO) {
                continue;
            }

            total++;
        }

        return total;
    }

    @Unique
    private void ae2powertools$sendData(NBTTagCompound data) {
        if (data.isEmpty()) return;

        if (!(ae2powertools$baseContainer().getPlayerInv().player instanceof EntityPlayerMP)) {
            return;
        }

        try {
            NetworkHandler.instance().sendTo(
                new PacketCompressedNBT(data),
                (EntityPlayerMP) ae2powertools$baseContainer().getPlayerInv().player
            );
        } catch (IOException ignored) {
        }
    }

    /**
     * Insert a pattern from the player's inventory into the AutoCrafter row.
     * The pattern must follow the contract defined by AutoCrafterPatternActions.isValidPattern(),
     * but anything regarding catalysts or other stuff is handled by the AutoCrafter itself,
     * not the Interface Terminal.
     */
    @Unique
    private void ae2powertools$insertFromPlayerSlot(EntityPlayerMP player, int playerSlotNumber,
            AutoCrafterTerminalTracker tracker) {

        AppEngSlot playerSlot;
        try {
            Slot slot = ((ContainerInterfaceTerminal) (Object) this).inventorySlots.get(playerSlotNumber);
            if (!(slot instanceof AppEngSlot)) return;

            playerSlot = (AppEngSlot) slot;
        } catch (IndexOutOfBoundsException ignored) {
            return;
        }

        if (!playerSlot.isPlayerSide() || !playerSlot.getHasStack()) return;

        ItemStack playerStack = playerSlot.getStack();
        if (!AutoCrafterPatternActions.isValidPattern(tracker.getTile(), playerStack)) {
            return;
        }

        for (int entryIndex = 0; entryIndex < tracker.getActiveSlotCount(); entryIndex++) {
            if (!tracker.getStack(entryIndex).isEmpty()) continue;

            ItemStack toInsert = playerStack.splitStack(1);
            playerSlot.putStack(playerStack.isEmpty() ? ItemStack.EMPTY : playerStack);
            AutoCrafterPatternActions.setPattern(tracker.getTile(), entryIndex, toInsert);
            ae2powertools$baseContainer().detectAndSendChanges();

            return;
        }
    }

    /**
     * Handle inserting or extracting a pattern from the AutoCrafter row,
     * depending on whether the player is holding a pattern.
     * If the player is holding a pattern, it will be inserted into the row.
     * If the player is not holding a pattern, the pattern in the row will be extracted to the player's hand.
     * <p>
     * Unlike ae2powertools$insertFromPlayerSlot(), this method WILL affect catalysts,
     * as to fulfill our pattern contract. The catalysts will be ejected to the player
     * when a pattern is extracted or replaced.
     */
    @Unique
    private void ae2powertools$handlePickup(EntityPlayer player, AutoCrafterTerminalTracker tracker, int entryIndex) {
        if (!ae2powertools$isValidTerminalSlot(entryIndex, tracker)) return;

        ItemStack held = player.inventory.getItemStack();
        ItemStack existing = tracker.getStack(entryIndex);

        // Pick-up
        if (held.isEmpty()) {
            if (existing.isEmpty()) return;

            player.inventory.setItemStack(existing.copy());
            ae2powertools$clearPattern(tracker, entryIndex, player);
            return;
        }

        // Insert
        if (!AutoCrafterPatternActions.isValidPattern(tracker.getTile(), held)) {
            return;
        }

        if (existing.isEmpty()) {
            ItemStack toInsert = held.splitStack(1);
            ae2powertools$setPattern(tracker, entryIndex, player, toInsert);
            player.inventory.setItemStack(held.isEmpty() ? ItemStack.EMPTY : held);
            return;
        }

        // Swap is only allowed if the player is holding a single pattern,
        // as per the normal swap contract in Minecraft GUIs (limited to the slot's stack size).
        if (held.getCount() != 1) return;
        ae2powertools$setPattern(tracker, entryIndex, player, held.copy());
        player.inventory.setItemStack(existing.copy());

    }

    /**
     * Extract the pattern from the AutoCrafter row to the player's inventory.
     * See ae2powertools$handlePickup() for information, as it is a similar operation.
     */
    @Unique
    private void ae2powertools$extractToInventory(EntityPlayer player, AutoCrafterTerminalTracker tracker, int entryIndex) {
        if (!ae2powertools$isValidTerminalSlot(entryIndex, tracker)) return;

        ItemStack existing = tracker.getStack(entryIndex);
        if (existing.isEmpty()) return;

        ItemStack extracted = existing.copy();
        ae2powertools$clearPattern(tracker, entryIndex, player);

        if (!player.inventory.addItemStackToInventory(extracted)) {
            player.dropItem(extracted, false);
        }
    }

    /**
     * Duplicate the pattern from the AutoCrafter row to the player's hand.
     * This is only allowed in creative mode.
     */
    @Unique
    private void ae2powertools$duplicateToHand(EntityPlayer player, AutoCrafterTerminalTracker tracker, int entryIndex) {
        if (!player.capabilities.isCreativeMode || !player.inventory.getItemStack().isEmpty()) {
            return;
        }

        if (!ae2powertools$isValidTerminalSlot(entryIndex, tracker)) return;

        ItemStack existing = tracker.getStack(entryIndex);
        if (existing.isEmpty()) return;

        player.inventory.setItemStack(existing.copy());
    }

    @Unique
    private void ae2powertools$clearPattern(AutoCrafterTerminalTracker tracker, int entryIndex, EntityPlayer player) {
        ae2powertools$setPattern(tracker, entryIndex, player, null);
    }

    /**
     * Set the pattern in the AutoCrafter row to the given pattern, ejecting catalysts if necessary.
     * If the pattern is null or empty, the row will be cleared.
     */
    @Unique
    private void ae2powertools$setPattern(AutoCrafterTerminalTracker tracker, int entryIndex, EntityPlayer player,
            @Nullable ItemStack patternStack) {
        if (patternStack != null && patternStack.isEmpty()) patternStack = null;

        ItemStack existing = tracker.getStack(entryIndex);
        if (!existing.isEmpty()) {
            AutoCrafterPatternActions.ejectCatalystsToPlayer(tracker.getTile(), entryIndex, player);
        }

        AutoCrafterPatternActions.setPattern(tracker.getTile(), entryIndex, patternStack);
    }

    @Unique
    private boolean ae2powertools$isValidTerminalSlot(int entryIndex, AutoCrafterTerminalTracker tracker) {
        return entryIndex >= 0 && entryIndex < tracker.getActiveSlotCount();
    }

    @Unique
    private AEBaseContainer ae2powertools$baseContainer() {
        return (AEBaseContainer) (Object) this;
    }

    @Unique
    private MixinAEBaseContainerAccessor ae2powertools$baseAccessor() {
        return (MixinAEBaseContainerAccessor) this;
    }
}