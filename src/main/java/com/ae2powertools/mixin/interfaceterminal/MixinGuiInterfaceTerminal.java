package com.ae2powertools.mixin.interfaceterminal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.google.common.collect.HashMultimap;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.gui.Gui;
import net.minecraft.inventory.Container;
import net.minecraft.nbt.NBTTagCompound;

import appeng.client.gui.AEBaseGui;
import appeng.client.gui.implementations.GuiInterfaceTerminal;
import appeng.client.me.ClientDCInternalInv;
import appeng.client.me.SlotDisconnected;

import com.ae2powertools.features.crafter.terminal.AutoCrafterTerminalClientState;
import com.ae2powertools.features.crafter.terminal.AutoCrafterTerminalTracker;


@Mixin(value = GuiInterfaceTerminal.class, remap = false)
public abstract class MixinGuiInterfaceTerminal extends AEBaseGui {

    /** Client-side map from AE2 row id to the backing row inventory model. */
    @Shadow @Final private HashMap<Long, ClientDCInternalInv> byId;
    /** Name buckets used by AE2 to group rows under their translated terminal header. */
    @Shadow @Final private HashMultimap<String, ClientDCInternalInv> byName;
    /** Per-row extra-line count used by AE2 to render rows beyond the first 9 slots. */
    @Shadow @Final private Map<ClientDCInternalInv, Integer> numUpgradesMap;
    /** Sorted header names currently present in the terminal. */
    @Shadow @Final private ArrayList<String> names;
    /** Flattened header/row list that AE2 iterates for rendering and hit testing. */
    @Shadow @Final private ArrayList<Object> lines;
    /** AE2 refresh flag used to rebuild the grouped row list on the next update. */
    @Shadow private boolean refreshList;
    /** Whether the Interface Terminal should only show free slots. */
    @Shadow private boolean onlyShowWithSpace;
    /** The number of rows currently displayed in the Interface Terminal. */
    @Shadow private int rows;

    @Shadow private void setScrollBar() {}

    protected MixinGuiInterfaceTerminal(Container inventorySlotsIn) {
        super(inventorySlotsIn);
    }

    /**
     * Build the client-side AutoCrafter row model from the server-sent metadata.
     */
    @Inject(method = "postUpdate", at = @At("HEAD"))
    private void ae2powertools$captureAutoCrafterMetadata(NBTTagCompound in, CallbackInfo ci) {
        if (in.getBoolean("clear")) AutoCrafterTerminalClientState.clear();

        for (String key : in.getKeySet()) {
            if (!key.startsWith("=")) continue;

            try {
                long id = Long.parseLong(key.substring(1), Character.MAX_RADIX);
                NBTTagCompound invData = in.getCompoundTag(key);
                if (!invData.getBoolean(AutoCrafterTerminalTracker.TAG_AUTO_CRAFTER)) {
                    continue;
                }

                int activeSlotCount = invData.getInteger(AutoCrafterTerminalTracker.TAG_ACTIVE_SLOTS);
                AutoCrafterTerminalClientState.setActiveSlotCount(id, activeSlotCount);
            } catch (NumberFormatException ignored) {
            }
        }
    }

    /**
    * Inject an 18-slot client row model for AutoCrafters instead of AE2's normal interface-sized row model.
    * This keeps the two-row layout stable and lets the client preserve the final 6 disabled slots.
     */
    @Inject(method = "getById", at = @At("HEAD"), cancellable = true)
    private void ae2powertools$createSizedAutoCrafterRows(long id, long sortBy, String string,
            CallbackInfoReturnable<ClientDCInternalInv> cir) {
        if (!AutoCrafterTerminalClientState.isAutoCrafter(id)) return;

        ClientDCInternalInv current = byId.get(id);
        if (current == null || current.getInventory().getSlots() != AutoCrafterTerminalTracker.TOTAL_SLOT_COUNT) {
            current = new ClientDCInternalInv(AutoCrafterTerminalTracker.TOTAL_SLOT_COUNT, id, sortBy, string);
            byId.put(id, current);
            refreshList = true;
        }

        cir.setReturnValue(current);
    }

    /**
     * Filter out AutoCrafter rows that have no free slots when "only show with space" is enabled.
     * Just like ae2powertools$createSizedAutoCrafterRows, this ensures that the AutoCrafter rows
     * have the correct behavior despite their unusual number of slots.
     */
    @Inject(method = "refreshList", at = @At("RETURN"))
    private void ae2powertools$filterDisabledSlotsOutOfSpaceCheck(CallbackInfo ci) {
        if (!onlyShowWithSpace) return;

        boolean changed = false;
        ArrayList<ClientDCInternalInv> toRemove = new ArrayList<>();

        for (ClientDCInternalInv entry : byId.values()) {
            if (!AutoCrafterTerminalClientState.isAutoCrafter(entry.getId())) continue;

            int activeSlotCount = AutoCrafterTerminalClientState.getActiveSlotCount(entry.getId());
            if (activeSlotCount <= 0) continue;

            boolean hasFreeSlot = false;
            for (int slot = 0; slot < activeSlotCount; slot++) {
                if (entry.getInventory().getStackInSlot(slot).isEmpty()) {
                    hasFreeSlot = true;
                    break;
                }
            }

            if (!hasFreeSlot) toRemove.add(entry);
        }

        for (ClientDCInternalInv entry : toRemove) {
            changed |= byName.remove(entry.getName(), entry);
        }

        if (!changed) return;

        names.clear();
        names.addAll(byName.keySet());
        Collections.sort(names);

        lines.clear();
        lines.ensureCapacity(names.size() + byId.size());

        for (String name : names) {
            lines.add(name);

            ArrayList<ClientDCInternalInv> groupedEntries = new ArrayList<>(byName.get(name));
            Collections.sort(groupedEntries);
            lines.addAll(groupedEntries);
        }

        setScrollBar();
    }

    /**
     * Prevent the player from interacting with disabled AutoCrafter slots in the Interface Terminal.
     * Removing them during drawBG keeps them out of AE2's slot render and click handling for the frame.
     */
    @Inject(method = "drawBG(IIII)V", at = @At("RETURN"))
    private void ae2powertools$removeDisabledDisconnectedSlots(int offsetX, int offsetY, int mouseX, int mouseY,
            CallbackInfo ci) {
        this.inventorySlots.inventorySlots.removeIf(slot -> {
            if (!(slot instanceof SlotDisconnected)) return false;

            SlotDisconnected disconnected = (SlotDisconnected) slot;
            return AutoCrafterTerminalClientState.isDisabledSlot(
                disconnected.getSlot().getId(),
                disconnected.getSlotIndex()
            );
        });
    }

    /**
     * Draw a final overlay over the unused AutoCrafter cells so it covers vanilla hover highlights.
     */
    @Inject(method = "drawFG(IIII)V", at = @At("RETURN"))
    private void ae2powertools$decorateDisabledSlots(int offsetX, int offsetY, int mouseX, int mouseY,
            CallbackInfo ci) {
        boolean hoveredUnusedSpace = false;

        int y = 51;
        int currentScroll = this.getScrollBar().getCurrentScroll();
        int linesDraw = 0;

        for (int index = 0; index < rows && linesDraw < rows && currentScroll + index < lines.size(); index++) {
            Object lineObject = lines.get(currentScroll + index);
            if (!(lineObject instanceof ClientDCInternalInv)) {
                y += 18;
                linesDraw++;
                continue;
            }

            ClientDCInternalInv inventory = (ClientDCInternalInv) lineObject;
            int extraLines = numUpgradesMap.get(inventory);
            int activeSlotCount = AutoCrafterTerminalClientState.getActiveSlotCount(inventory.getId());

            for (int row = 0; row < 1 + extraLines && linesDraw < rows; row++) {
                int rowStartSlot = row * 9;
                int disabledFrom = Math.max(0, activeSlotCount - rowStartSlot);

                if (AutoCrafterTerminalClientState.isAutoCrafter(inventory.getId()) && disabledFrom < 9) {
                    for (int slot = disabledFrom; slot < 9; slot++) {
                        int x = 20 + slot * 18;
                        Gui.drawRect(x + 1, y, x + 18, y + 17, 0xDC242424);
                    }
                }

                y += 18;
                linesDraw++;
            }
        }
    }
}