package com.ae2powertools.features.crafter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;

import io.netty.buffer.ByteBuf;

import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;


/**
 * Immutable per-entry recipe data sent from server to client.
 * <p>
 * Used by the recipe view (current page) to render:
 * - 3x3 input grid ghost items
 * - catalyst slot expectations (ghost overlays)
 * - current state (recipe view also displays state info)
 * - error details (verbose tooltip)
 * <p>
 * The pattern itself lives in a real Slot and is sync'd by vanilla container code.
 * We sync the derived recipe info because it lives entirely on the server and the
 * client cannot reconstruct it without recipe lookup logic.
 */
public final class CrafterRecipeSnapshot {

    public static final CrafterRecipeSnapshot EMPTY = new CrafterRecipeSnapshot(
        new IAEItemStack[9],
        Collections.emptyList(),
        Collections.emptyList(),
        false
    );

    /**
     * Per-slot expected catalyst entry. slotIndex maps to the crafting grid slot
     * (0-8). expectedItem is the catalyst item the recipe expects in that slot.
     */
    public static final class CatalystExpectation {
        public final int slotIndex;
        @Nullable public final IAEItemStack expectedItem;

        public CatalystExpectation(int slotIndex, @Nullable IAEItemStack expectedItem) {
            this.slotIndex = slotIndex;
            this.expectedItem = expectedItem;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof CatalystExpectation)) return false;

            CatalystExpectation that = (CatalystExpectation) o;
            if (slotIndex != that.slotIndex) return false;

            return aeItemEquals(this.expectedItem, that.expectedItem);
        }

        @Override
        public int hashCode() {
            return Objects.hash(slotIndex, expectedItem == null ? 0 : expectedItem.getItem());
        }
    }

    private final IAEItemStack[] inputGrid;
    private final List<CatalystExpectation> catalysts;
    private final List<String> errorDetails;
    private final boolean hasDisplayData;

    public CrafterRecipeSnapshot(IAEItemStack[] inputGrid,
                                 List<CatalystExpectation> catalysts,
                                 List<String> errorDetails,
                                 boolean hasDisplayData) {
        this.inputGrid = inputGrid;
        this.catalysts = catalysts;
        this.errorDetails = errorDetails;
        this.hasDisplayData = hasDisplayData;
    }

    /**
     * Captures the current recipe state of an entry on the server.
     */
    public static CrafterRecipeSnapshot fromEntry(CrafterEntry entry) {
        boolean hasDisplay = entry.hasPattern() && entry.hasValidRecipeInfo();
        if (!hasDisplay) {
            // Even when there is no recipe info, we still want to send error details
            // (the entry might be in SIMULATION_FAILED state with helpful messages).
            return new CrafterRecipeSnapshot(new IAEItemStack[9], Collections.emptyList(),
                copyStrings(entry.getErrorDetails()), false);
        }

        IAEItemStack[] grid = new IAEItemStack[9];
        IAEItemStack[] sourceGrid = entry.getInputGrid();
        if (sourceGrid != null) {
            for (int i = 0; i < 9 && i < sourceGrid.length; i++) {
                grid[i] = sourceGrid[i] != null ? sourceGrid[i].copy() : null;
            }
        }

        List<CatalystExpectation> catalysts = new ArrayList<>();
        CrafterRecipeInfo info = entry.getRecipeInfo();
        if (info != null) {
            for (CrafterRecipeInfo.IngredientInfo ing : info.getCatalystSlots()) {
                IAEItemStack expected = ing.getItem() != null ? ing.getItem().copy() : null;
                catalysts.add(new CatalystExpectation(ing.getSlotIndex(), expected));
            }
        }

        return new CrafterRecipeSnapshot(grid, catalysts, copyStrings(entry.getErrorDetails()), true);
    }

    private static List<String> copyStrings(List<String> source) {
        return source.isEmpty() ? Collections.emptyList() : new ArrayList<>(source);
    }

    public void writeToBuf(ByteBuf buf) throws IOException {
        buf.writeBoolean(hasDisplayData);

        // Input grid (9 slots, presence-flagged)
        for (int i = 0; i < 9; i++) {
            IAEItemStack stack = i < inputGrid.length ? inputGrid[i] : null;
            buf.writeBoolean(stack != null);
            if (stack != null) stack.writeToPacket(buf);
        }

        // Catalyst expectations
        buf.writeByte(catalysts.size());
        for (CatalystExpectation cat : catalysts) {
            buf.writeByte(cat.slotIndex);
            buf.writeBoolean(cat.expectedItem != null);
            if (cat.expectedItem != null) cat.expectedItem.writeToPacket(buf);
        }

        // Error details
        buf.writeShort(errorDetails.size());
        for (String s : errorDetails) writeString(buf, s);
    }

    public static CrafterRecipeSnapshot readFromBuf(ByteBuf buf) throws IOException {
        boolean hasDisplay = buf.readBoolean();

        IAEItemStack[] grid = new IAEItemStack[9];
        for (int i = 0; i < 9; i++) {
            grid[i] = buf.readBoolean() ? AEItemStack.fromPacket(buf) : null;
        }

        int catCount = buf.readByte() & 0xFF;
        List<CatalystExpectation> catalysts = new ArrayList<>(catCount);
        for (int i = 0; i < catCount; i++) {
            int slotIndex = buf.readByte() & 0xFF;
            IAEItemStack expected = buf.readBoolean() ? AEItemStack.fromPacket(buf) : null;
            catalysts.add(new CatalystExpectation(slotIndex, expected));
        }

        int errCount = buf.readShort() & 0xFFFF;
        List<String> errors = new ArrayList<>(errCount);
        for (int i = 0; i < errCount; i++) errors.add(readString(buf));

        return new CrafterRecipeSnapshot(grid, catalysts, errors, hasDisplay);
    }

    private static void writeString(ByteBuf buf, String s) {
        byte[] bytes = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        buf.writeShort(bytes.length);
        buf.writeBytes(bytes);
    }

    private static String readString(ByteBuf buf) {
        int len = buf.readShort() & 0xFFFF;
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    public IAEItemStack[] getInputGrid() { return inputGrid; }
    public List<CatalystExpectation> getCatalysts() { return catalysts; }
    public List<String> getErrorDetails() { return errorDetails; }
    public boolean hasDisplayData() { return hasDisplayData; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CrafterRecipeSnapshot)) return false;

        CrafterRecipeSnapshot that = (CrafterRecipeSnapshot) o;
        if (hasDisplayData != that.hasDisplayData) return false;
        if (!errorDetails.equals(that.errorDetails)) return false;
        if (!catalysts.equals(that.catalysts)) return false;

        // Compare input grids
        for (int i = 0; i < 9; i++) {
            IAEItemStack a = i < this.inputGrid.length ? this.inputGrid[i] : null;
            IAEItemStack b = i < that.inputGrid.length ? that.inputGrid[i] : null;
            if (!aeItemEquals(a, b)) return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        // Hash should be stable enough for diff caching; exact hash precision not critical.
        int h = Boolean.hashCode(hasDisplayData);
        h = 31 * h + errorDetails.hashCode();
        h = 31 * h + catalysts.hashCode();
        for (IAEItemStack s : inputGrid) h = 31 * h + (s == null ? 0 : s.getItem().hashCode());

        return h;
    }

    private static boolean aeItemEquals(@Nullable IAEItemStack a, @Nullable IAEItemStack b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;

        return a.isSameType(b) && a.getStackSize() == b.getStackSize();
    }
}
