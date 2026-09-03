package com.ae2powertools.features.crafter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;

import io.netty.buffer.ByteBuf;

import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.fml.common.network.ByteBufUtils;

import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;


/**
 * Immutable per-entry recipe data sent from server to client.
 * <p>
 * Used by the recipe view (current page) to render:
 * - 3x3 input grid ghost items
 * - the encoded pattern stack for the current page
 * - the current catalyst row contents for the current page
 * - catalyst slot expectations (ghost overlays)
 * - current state (recipe view also displays state info)
 * - error details (verbose tooltip)
 * <p>
 * We sync the derived recipe info because it lives entirely on the server and the
 * client cannot reconstruct it without recipe lookup logic. The current page's real
 * slot items are also mirrored here so rendering does not depend on vanilla slot-sync
 * timing during page changes or shift-click transfers.
 */
public final class CrafterRecipeSnapshot {

    public static final CrafterRecipeSnapshot EMPTY = new CrafterRecipeSnapshot(
        new IAEItemStack[9],
        Collections.emptyList(),
        Collections.emptyList(),
        Collections.emptyList(),
        false,
        false,
        true,
        ItemStack.EMPTY,
        new ItemStack[CrafterEntry.CATALYST_SLOTS]
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
    private final ItemStack patternStack;
    private final ItemStack[] catalystStacks;
    private final List<CatalystExpectation> catalysts;
    private final List<ITextComponent> errorDetails;
    private final List<ITextComponent> hints;
    private final boolean hasDisplayData;
    private final boolean hasDurabilityItems;
    private final boolean fuzzyDurabilityEnabled;

    public CrafterRecipeSnapshot(IAEItemStack[] inputGrid,
                                 List<CatalystExpectation> catalysts,
                                 List<ITextComponent> errorDetails,
                                 List<ITextComponent> hints,
                                 boolean hasDisplayData,
                                 boolean hasDurabilityItems,
                                 boolean fuzzyDurabilityEnabled,
                                 ItemStack patternStack,
                                 ItemStack[] catalystStacks) {
        this.inputGrid = inputGrid;
        this.patternStack = patternStack == null || patternStack.isEmpty() ? ItemStack.EMPTY : patternStack.copy();
        this.catalystStacks = copyCatalystStacks(catalystStacks);
        this.catalysts = catalysts;
        this.errorDetails = errorDetails;
        this.hints = hints;
        this.hasDisplayData = hasDisplayData;
        this.hasDurabilityItems = hasDurabilityItems;
        this.fuzzyDurabilityEnabled = fuzzyDurabilityEnabled;
    }

    /**
     * Captures the current recipe state of an entry on the server.
     */
    public static CrafterRecipeSnapshot fromEntry(CrafterEntry entry) {
        ItemStack patternStack = entry.hasPattern() ? entry.getPatternStack().copy() : ItemStack.EMPTY;
        ItemStack[] catalystStacks = copyCatalystStacks(entry);
        boolean hasDisplay = entry.hasPattern() && entry.hasValidRecipeInfo();
        if (!hasDisplay) {
            // Even when there is no recipe info, we still want to send error details
            // (the entry might be in SIMULATION_FAILED state with helpful messages).
            return new CrafterRecipeSnapshot(new IAEItemStack[9], Collections.emptyList(),
                copyComponents(entry.getErrorDetails()), copyComponents(entry.getHints()),
                false, false, true, patternStack, catalystStacks);
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

        return new CrafterRecipeSnapshot(
            grid,
            catalysts,
            copyComponents(entry.getErrorDetails()),
            copyComponents(entry.getHints()),
            true,
            info != null && info.hasDurabilityItems(),
            entry.isFuzzyDurabilityEnabled(),
            patternStack,
            catalystStacks);
    }

    private static List<ITextComponent> copyComponents(List<ITextComponent> source) {
        return source.isEmpty() ? Collections.emptyList() : new ArrayList<>(source);
    }

    private static ItemStack[] copyCatalystStacks(CrafterEntry entry) {
        ItemStack[] copy = new ItemStack[CrafterEntry.CATALYST_SLOTS];
        for (int i = 0; i < copy.length; i++) {
            ItemStack stack = entry.getCatalystStack(i);
            copy[i] = stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
        }

        return copy;
    }

    private static ItemStack[] copyCatalystStacks(ItemStack[] source) {
        ItemStack[] copy = new ItemStack[CrafterEntry.CATALYST_SLOTS];
        for (int i = 0; i < copy.length; i++) {
            ItemStack stack = source != null && i < source.length && source[i] != null ? source[i] : ItemStack.EMPTY;
            copy[i] = stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
        }

        return copy;
    }

    public void writeToBuf(ByteBuf buf) throws IOException {
        buf.writeBoolean(hasDisplayData);
        buf.writeBoolean(hasDurabilityItems);
        buf.writeBoolean(fuzzyDurabilityEnabled);

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

        ByteBufUtils.writeItemStack(buf, patternStack);
        for (ItemStack catalystStack : catalystStacks) {
            ByteBufUtils.writeItemStack(buf, catalystStack);
        }

        // Error details are serialized as JSON-encoded ITextComponents so the receiving
        // client can rebuild the same component tree (translation key + args) and resolve
        // it in its own locale.
        buf.writeShort(errorDetails.size());
        for (ITextComponent comp : errorDetails) writeString(buf, ITextComponent.Serializer.componentToJson(comp));

        buf.writeShort(hints.size());
        for (ITextComponent comp : hints) writeString(buf, ITextComponent.Serializer.componentToJson(comp));
    }

    public static CrafterRecipeSnapshot readFromBuf(ByteBuf buf) {
        boolean hasDisplay = buf.readBoolean();
        boolean hasDurabilityItems = buf.readBoolean();
        boolean fuzzyDurabilityEnabled = buf.readBoolean();

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

        ItemStack patternStack = ByteBufUtils.readItemStack(buf);
        ItemStack[] catalystStacks = new ItemStack[CrafterEntry.CATALYST_SLOTS];
        for (int i = 0; i < catalystStacks.length; i++) {
            ItemStack stack = ByteBufUtils.readItemStack(buf);
            catalystStacks[i] = stack.isEmpty() ? ItemStack.EMPTY : stack;
        }

        int errCount = buf.readShort() & 0xFFFF;
        List<ITextComponent> errors = new ArrayList<>(errCount);
        for (int i = 0; i < errCount; i++) errors.add(ITextComponent.Serializer.jsonToComponent(readString(buf)));

        int hintCount = buf.readShort() & 0xFFFF;
        List<ITextComponent> hints = new ArrayList<>(hintCount);
        for (int i = 0; i < hintCount; i++) hints.add(ITextComponent.Serializer.jsonToComponent(readString(buf)));

        return new CrafterRecipeSnapshot(grid, catalysts, errors, hints, hasDisplay,
            hasDurabilityItems, fuzzyDurabilityEnabled, patternStack, catalystStacks);
    }

    private static void writeString(ByteBuf buf, String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        buf.writeShort(bytes.length);
        buf.writeBytes(bytes);
    }

    private static String readString(ByteBuf buf) {
        int len = buf.readShort() & 0xFFFF;
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public IAEItemStack[] getInputGrid() { return inputGrid; }
    public ItemStack getPatternStack() { return patternStack; }
    public ItemStack[] getCatalystStacks() { return catalystStacks; }
    public List<CatalystExpectation> getCatalysts() { return catalysts; }
    public List<ITextComponent> getErrorDetails() { return errorDetails; }
    public List<ITextComponent> getHints() { return hints; }
    public boolean hasDisplayData() { return hasDisplayData; }
    public boolean hasDurabilityItems() { return hasDurabilityItems; }
    public boolean isFuzzyDurabilityEnabled() { return fuzzyDurabilityEnabled; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CrafterRecipeSnapshot)) return false;

        CrafterRecipeSnapshot that = (CrafterRecipeSnapshot) o;
        if (hasDisplayData != that.hasDisplayData) return false;
        if (hasDurabilityItems != that.hasDurabilityItems) return false;
        if (fuzzyDurabilityEnabled != that.fuzzyDurabilityEnabled) return false;
        if (!errorDetails.equals(that.errorDetails)) return false;
        if (!catalysts.equals(that.catalysts)) return false;
        if (!itemStackEquals(patternStack, that.patternStack)) return false;

        for (int i = 0; i < catalystStacks.length; i++) {
            if (!itemStackEquals(this.catalystStacks[i], that.catalystStacks[i])) return false;
        }

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
        h = 31 * h + Boolean.hashCode(hasDurabilityItems);
        h = 31 * h + Boolean.hashCode(fuzzyDurabilityEnabled);
        h = 31 * h + errorDetails.hashCode();
        h = 31 * h + catalysts.hashCode();
        h = 31 * h + itemStackHash(patternStack);
        for (ItemStack stack : catalystStacks) h = 31 * h + itemStackHash(stack);
        for (IAEItemStack s : inputGrid) h = 31 * h + (s == null ? 0 : s.getItem().hashCode());

        return h;
    }

    private static boolean aeItemEquals(@Nullable IAEItemStack a, @Nullable IAEItemStack b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;

        return a.isSameType(b) && a.getStackSize() == b.getStackSize();
    }

    private static boolean itemStackEquals(ItemStack a, ItemStack b) {
        if (a.isEmpty() && b.isEmpty()) return true;
        if (a.isEmpty() || b.isEmpty()) return false;
        if (!ItemStack.areItemsEqual(a, b)) return false;
        if (!ItemStack.areItemStackTagsEqual(a, b)) return false;

        return a.getCount() == b.getCount();
    }

    private static int itemStackHash(ItemStack stack) {
        if (stack.isEmpty()) return 0;

        return Objects.hash(stack.getItem(), stack.getMetadata(), stack.getCount(), stack.getTagCompound());
    }
}
