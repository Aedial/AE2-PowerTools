package com.ae2powertools.features.monitor.dependent;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.client.config.GuiUtils;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.config.RedstoneMode;
import appeng.api.config.Settings;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.util.ReadableNumberConverter;

import com.ae2powertools.Tags;
import com.ae2powertools.features.monitor.MonitoredEntry;
import com.ae2powertools.features.monitor.MonitoredResource;
import com.ae2powertools.features.monitor.client.MonitoredResourceRenderer;
import com.ae2powertools.features.monitor.emitter.EmitterRedstoneStrength;
import com.ae2powertools.integration.jei.JeiTooltipBridge;
import com.ae2powertools.network.PacketOpenStorageMonitorPollingRate;
import com.ae2powertools.network.PacketRequestMonitorContents;
import com.ae2powertools.network.PacketSelectMonitorContent;
import com.ae2powertools.network.PacketSetEmitterRedstoneStrength;
import com.ae2powertools.network.PacketSetMatchMode;
import com.ae2powertools.network.PacketUpdateMonitorEntry;
import com.ae2powertools.network.PowerToolsNetwork;
import com.ae2powertools.util.PollingRateUtils;


/**
 * Shared GUI for both ME Storage Level Emitter and ME Storage Display.
 * <p>
 * Layout (modeled after {@code GuiBetterLevelMaintainer}):
 * - Fixed 4 cols x 6 rows = 24 entry cells. The grid never grows or shrinks.
 *   Empty cells are still interactive: clicking the left half of an empty cell
 *   opens the content selector to assign a new resource at that index.
 * - Each cell is rendered with an INNER area of 50x22 plus a 1 px right/bottom
 *   border that is left uncolored (so cells don't visually merge). Cell pitch
 *   is therefore 51x23.
 * - Per-cell zones:
 *     - Icon zone:   16x16 at local (3, 3)
 *     - Comparison:  10x10 at local (20, 6), straddles left/right halves
 *     - Numbers:     16x16+ at local (31, 3), current quantity / threshold
 *   Background tint covers the inner 50x22 area:
 *     - Grey       if entry is disabled
 *     - Translucent green if condition met
 *     - Translucent red   if condition not met
 *   Empty cells get no tint.
 * <p>
 * Click hit detection (priority order, left-click only):
 *   1. If a count field is visible and the click is OUTSIDE it: dismiss-and-save first,
 *      then continue with normal hit detection on the underlying click.
 *   2. Inside the comparison 10x10 region: cycle the comparison.
 *   3. Otherwise inside the left half (localX < 25): open content selector for that cell.
 *   4. Otherwise inside the right half (localX >= 25): show count field for that cell.
 * <p>
 * Hover feedback uses the same three-zone partition: whichever zone the mouse
 * is in gets a 0x40FFFFFF white overlay. This makes interactive areas obvious.
 * <p>
 * Right-click on a filled cell toggles the entry's enabled flag.
 * <p>
 * The Storage Display variant ({@link MonitorHostType#DISPLAY}) shares the SAME interactive
 * surface as the Emitter.
 * <p>
 * Hover tooltips summarize the resource, exact values, and per-cell controls.
 */
@SideOnly(Side.CLIENT)
public class GuiStorageMonitor extends GuiContainer {

    private static final ResourceLocation BACKGROUND = new ResourceLocation(
        Tags.MODID, "textures/guis/emitter_gui.png");
    private static final ResourceLocation COMPARISON_ARROWS = new ResourceLocation(
        Tags.MODID, "textures/guis/comparison_arrows.png");
    /** Reuse the maintainer's selector background so the modal layout matches the Maintainer GUI. */
    private static final ResourceLocation SELECTOR_BACKGROUND = new ResourceLocation(
        Tags.MODID, "textures/guis/recipe_selector.png");
    /** AE2's states.png used for the standard side-button background frame. */
    private static final ResourceLocation AE2_STATES = new ResourceLocation(
        "appliedenergistics2", "textures/guis/states.png");

    // --- Main GUI dimensions ---
    private static final int GUI_WIDTH = 221;
    private static final int GUI_HEIGHT = 184;

    // --- Entry grid layout ---
    private static final int GRID_COLS = 4;
    private static final int GRID_ROWS = 6;
    private static final int GRID_CAPACITY = GRID_COLS * GRID_ROWS;
    /** Inner cell width (the colored / interactive area). */
    private static final int INNER_W = 50;
    /** Inner cell height. */
    private static final int INNER_H = 22;
    /** Cell pitch = inner + 1 px border, so cells don't visually merge. */
    private static final int CELL_W = INNER_W + 1;
    private static final int CELL_H = INNER_H + 1;
    /** Top-left of the first cell's INNER area, relative to guiLeft / guiTop. */
    private static final int GRID_X = 9;
    private static final int GRID_Y = 19;

    // --- Per-cell hit zones (relative to the cell's inner top-left) ---
    private static final int ICON_X = 3, ICON_Y = 3, ICON_SIZE = 16;
    private static final int CMP_X = 20, CMP_Y = 6, CMP_SIZE = 10;
    private static final int NUM_X = 31, NUM_Y = 3, NUM_SIZE = 16;
    /** X coordinate that splits the cell into left (icon/selector) vs right (threshold) halves. */
    private static final int LEFT_RIGHT_SPLIT = 25;

    // --- Comparison arrow texture sheet (64x64, 2x2 grid of 20x20 arrows) ---
    private static final int CMP_SHEET_SIZE = 64;
    private static final int CMP_TILE_SIZE = 20;

    // --- Bottom count field (threshold editor) ---
    private static final int COUNT_FIELD_X = 8;
    private static final int COUNT_FIELD_Y = 163;
    private static final int COUNT_FIELD_W = 203;
    private static final int COUNT_FIELD_H = 10;

    // --- Match-mode side button (sits OUTSIDE the GUI on the left) ---
    private static final int SIDE_BTN_SIZE = 16;
    private static final int SIDE_BTN_X_OFFSET = -SIDE_BTN_SIZE - 2;
    private static final int MATCH_MODE_BTN_Y = 4;
    private static final int REDSTONE_SIGNAL_BTN_Y = MATCH_MODE_BTN_Y + SIDE_BTN_SIZE + 4;

    // --- Wrench tab button (polling rate sub-GUI launcher) ---
    /**
     * AE2 states.png icon index for the wrench-style polling-rate icon.
     * Mirrors CELLS' {@code AbstractResourceInterfaceGui.pollingRateButton}
     * which uses the exact same value.
     */
    private static final int WRENCH_ICON_INDEX = 2 + 5 * 16;

    // --- Selector modal (content picker) ---
    private static final int SELECTOR_WIDTH = 195;
    private static final int SELECTOR_HEIGHT = 186;
    private static final int SELECTOR_GRID_X = 8;
    private static final int SELECTOR_GRID_Y = 17;
    private static final int SELECTOR_COLS = 9;
    private static final int SELECTOR_ROWS = 9;
    private static final int SELECTOR_SLOT_SIZE = 18;
    private static final int SELECTOR_SEARCH_X = 80;
    private static final int SELECTOR_SEARCH_Y = 4;
    private static final int SELECTOR_SEARCH_W = 90;
    private static final int SELECTOR_SEARCH_H = 12;

    // --- Selector scrollbar (mirrors GuiBetterLevelMaintainer's layout) ---
    /** Vanilla creative-tab scrollbar texture. Has both the active thumb (232,0) and disabled thumb (244,0). */
    private static final ResourceLocation SCROLLBAR_TEXTURE = new ResourceLocation(
        "minecraft", "textures/gui/container/creative_inventory/tabs.png");
    private static final int SELECTOR_SCROLL_X = 175;
    private static final int SELECTOR_SCROLL_Y = 18;
    /** Total height of the scrollbar track, used to interpolate the thumb position. */
    private static final int SELECTOR_SCROLL_TRACK_H = 162;
    private static final int SELECTOR_SCROLL_THUMB_W = 12;
    private static final int SELECTOR_SCROLL_THUMB_H = 15;

    /** Identifies which sub-zone of a cell a coordinate falls into. */
    private enum CellZone { NONE, COMPARATOR, LEFT, RIGHT }

    /** Precomputed hit test result for a grid cell hover/click. */
    private static final class GridHit {

        private final int index;
        private final CellZone zone;

        private GridHit(int index, CellZone zone) {
            this.index = index;
            this.zone = zone;
        }
    }

    private final ContainerStorageMonitor container;

    /** Match-mode side button (drawn / hit-tested manually since it sits OUTSIDE guiLeft). */
    private int matchBtnX, matchBtnY;
    private boolean matchBtnHovered;

    /** Emitter-only redstone-strength button. */
    private GuiImgButton redstoneSignalBtn;

    /** Wrench tab button (vanilla GuiButton via AE2's GuiTabButton). */
    private GuiTabButton pollingRateBtn;

    // --- Count field (threshold editor) ---
    private GuiTextField countField;
    /** Index of the entry currently being edited via the count field, or -1 when hidden. */
    private int countFieldEntryIndex = -1;

    // --- Selector modal state ---
    private boolean selectorOpen;
    private int selectorLeft, selectorTop;
    private GuiTextField selectorSearchField;
    private int selectorScrollOffset;
    private List<MonitoredResource> selectorResources = new ArrayList<>();
    private List<MonitoredResource> filteredResources = new ArrayList<>();
    /** Display-slot index (0..SELECTOR_COLS*SELECTOR_ROWS-1) the cursor is on, or -1 if none. */
    private int selectorHoveredSlot = -1;
    /** Index to replace the resource at; -1 means "append a new entry". */
    private int selectorTargetIndex = -1;

    /** Static reference for receiving async packet data from the server. */
    private static GuiStorageMonitor activeInstance;

    public GuiStorageMonitor(ContainerStorageMonitor container) {
        super(container);
        this.container = container;
        this.xSize = GUI_WIDTH;
        this.ySize = GUI_HEIGHT;
    }

    @Override
    public void initGui() {
        super.initGui();
        activeInstance = this;

        matchBtnX = guiLeft + SIDE_BTN_X_OFFSET;
        matchBtnY = guiTop + MATCH_MODE_BTN_Y;

        if (container.supportsEmitterRedstoneStrength()) {
            redstoneSignalBtn = new GuiImgButton(
                matchBtnX,
                guiTop + REDSTONE_SIGNAL_BTN_Y,
                Settings.REDSTONE_EMITTER,
                RedstoneMode.LOW_SIGNAL);
            this.buttonList.add(redstoneSignalBtn);
        }

        // Wrench tab button: top-right, sticking out beyond the GUI's right edge
        // like AE2's standard tab buttons. Width 22, height 22 (set by GuiTabButton itself).
        // We position it so the visible portion sits just at the corner.
        pollingRateBtn = new GuiTabButton(
            guiLeft + xSize - 3 - 20,
            guiTop,
            WRENCH_ICON_INDEX,
            I18n.format("gui.ae2powertools.storage_emitter.polling_rate.title"),
            this.itemRender);
        this.buttonList.add(pollingRateBtn);

        // Count field: hidden until the user clicks the right half of a cell.
        // Uses a black background drawn manually so the field clearly "pops" over the entries.
        countField = new GuiTextField(50, fontRenderer,
            guiLeft + COUNT_FIELD_X, guiTop + COUNT_FIELD_Y,
            COUNT_FIELD_W, COUNT_FIELD_H);
        countField.setMaxStringLength(20);
        countField.setEnableBackgroundDrawing(false);
        countField.setTextColor(0xFFFFFF);
        countField.setVisible(false);
        countField.setFocused(false);

        selectorLeft = (width - SELECTOR_WIDTH) / 2;
        selectorTop = (height - SELECTOR_HEIGHT) / 2;
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();

        if (activeInstance == this) activeInstance = null;
    }

    @Override
    public void updateScreen() {
        super.updateScreen();

        if (countField != null && countField.getVisible()) countField.updateCursorCounter();
        if (selectorOpen && selectorSearchField != null) selectorSearchField.updateCursorCounter();
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        super.actionPerformed(button);

        if (button == redstoneSignalBtn) {
            PowerToolsNetwork.INSTANCE.sendToServer(
                new PacketSetEmitterRedstoneStrength(
                    container.getHost(),
                    container.getSyncEmitterRedstoneSignalStrength().next()));
            return;
        }

        if (button == pollingRateBtn) {
            // Open the polling-rate sub-GUI via server round-trip (so synced fields
            // arrive in the new container before the first client render frame).
            // The host is passed (rather than just the pos) so the server can resolve
            // the right cable part from the bus when the host is a part.
            PowerToolsNetwork.INSTANCE.sendToServer(
                new PacketOpenStorageMonitorPollingRate(container.getHost()));
            return;
        }
    }

    // ====================== DRAWING ======================

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        drawDefaultBackground();

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        mc.getTextureManager().bindTexture(BACKGROUND);
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize);

        // Side button: drawn here (clean GL state) to avoid the lighting/blend leaks
        // that occur when drawing custom textures after super.drawScreen has rendered slots.
        drawSideButtons(mouseX, mouseY);

        // Entry grid: drawn during the background pass for the same reason.
        // Tooltips for empty entries / grid hover are still drawn from drawScreen.
        drawEntries(mouseX, mouseY);

        // Count field overlay (background tint + text). Drawn here so it sits above
        // the entry grid but below the modal selector.
        drawCountField();
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fontRenderer.drawString(
            I18n.format(container.getHostType().getTitleLangKey()),
            8, 6, 0x404040);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        syncEmitterRedstoneButton();
        super.drawScreen(mouseX, mouseY, partialTicks);

        // Tooltips and modal go on top of everything (after slot rendering).
        if (selectorOpen) {
            drawSelectorModal(mouseX, mouseY, partialTicks);
            drawSelectorTooltip(mouseX, mouseY);
            return;
        }

        // Side-button hover tooltip.
        if (matchBtnHovered) drawMatchModeTooltip(mouseX, mouseY);

        if (isRedstoneSignalButtonHovered(mouseX, mouseY)) {
            drawRedstoneSignalTooltip(mouseX, mouseY);
        }

        drawHoveredEntryTooltip(mouseX, mouseY);

        // Polling-rate tab-button tooltip, GuiContainer doesn't render it for us.
        if (pollingRateBtn != null && pollingRateBtn.visible
                && mouseX >= pollingRateBtn.x && mouseX < pollingRateBtn.x + pollingRateBtn.width
                && mouseY >= pollingRateBtn.y && mouseY < pollingRateBtn.y + pollingRateBtn.height) {

            String interval = PollingRateUtils.format(container.refreshRate);
            List<String> tt = new ArrayList<>();
            tt.add("§e" + I18n.format("gui.ae2powertools.storage_emitter.polling_rate.tooltip", interval) + "§r");
            tt.add("");
            tt.add("§7" + I18n.format("gui.ae2powertools.storage_emitter.polling_rate.description") + "§r");
            GuiUtils.drawHoveringText(tt, mouseX, mouseY, width, height, -1, fontRenderer);
        }
    }

    private void drawSideButtons(int mouseX, int mouseY) {
        matchBtnHovered = mouseX >= matchBtnX && mouseX < matchBtnX + SIDE_BTN_SIZE
            && mouseY >= matchBtnY && mouseY < matchBtnY + SIDE_BTN_SIZE;

        // Reset GL state so the button doesn't inherit lighting/depth from prior draws.
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        mc.getTextureManager().bindTexture(AE2_STATES);
        // Standard AE2 button frame (bottom-right cell of states.png).
        drawTexturedModalRect(matchBtnX, matchBtnY, 240, 240, SIDE_BTN_SIZE, SIDE_BTN_SIZE);

        // Letter label centered in the button to indicate the match mode
        String label = container.getSyncMatchMode().getSymbol();
        int labelW = fontRenderer.getStringWidth(label);
        fontRenderer.drawString(label,
            matchBtnX + (SIDE_BTN_SIZE - labelW) / 2,
            matchBtnY + (SIDE_BTN_SIZE - 8) / 2,
            0xFFFFFFFF);

        if (matchBtnHovered) {
            drawRect(matchBtnX + 1, matchBtnY + 1,
                matchBtnX + SIDE_BTN_SIZE - 1, matchBtnY + SIDE_BTN_SIZE - 1,
                0x40FFFFFF);
        }

        GlStateManager.enableDepth();
    }

    private void drawMatchModeTooltip(int mouseX, int mouseY) {
        List<String> tt = new ArrayList<>();
        tt.add(I18n.format("gui.ae2powertools.storage_emitter.match_mode",
            container.getSyncMatchMode().name()));
        tt.add("§7" + I18n.format("gui.ae2powertools.storage_emitter.match_mode.click_toggle") + "§r");
        GuiUtils.drawHoveringText(tt, mouseX, mouseY, width, height, -1, fontRenderer);
    }

    private void drawRedstoneSignalTooltip(int mouseX, int mouseY) {
        EmitterRedstoneStrength signalStrength = container.getSyncEmitterRedstoneSignalStrength();

        List<String> tt = new ArrayList<>();
        // TODO: add some color to the signal strength level. Not Green/Red, because both are "active" states.
        tt.add(I18n.format(
            "gui.ae2powertools.storage_emitter.redstone_signal",
            I18n.format(signalStrength.getLangKey())));
        tt.add("§7" + I18n.format("gui.ae2powertools.storage_emitter.redstone_signal.click_toggle") + "§r");
        GuiUtils.drawHoveringText(tt, mouseX, mouseY, width, height, -1, fontRenderer);
    }

    private void drawHoveredEntryTooltip(int mouseX, int mouseY) {
        GridHit hit = getGridHit(mouseX, mouseY);
        if (hit == null) return;

        List<MonitoredEntry> entries = container.getHost().getEntries();
        if (hit.index < 0 || hit.index >= entries.size()) return;

        List<String> tooltip = buildEntryTooltip(entries.get(hit.index));
        if (tooltip.isEmpty()) return;

        GuiUtils.drawHoveringText(tooltip, mouseX, mouseY, width, height, -1, fontRenderer);
    }

    private List<String> buildEntryTooltip(MonitoredEntry entry) {
        List<String> tooltip = buildResourceTooltip(entry);

        if (!tooltip.isEmpty()) tooltip.add("");

        if (entry.hasResource()) {
            tooltip.add(TextFormatting.GRAY + I18n.format(
                "gui.ae2powertools.storage_emitter.current_quantity",
                formatWithCommas(entry.getLastQuantity())));
            tooltip.add(TextFormatting.GRAY + I18n.format(
                "gui.ae2powertools.storage_emitter.current_target",
                entry.getComparison().getSymbol(),
                formatWithCommas(entry.getThreshold()),
                formatTargetProgress(entry)));
        } else {
            tooltip.add(TextFormatting.GRAY + I18n.format("gui.ae2powertools.storage_emitter.empty_slot"));
        }

        tooltip.add("");
        tooltip.add(TextFormatting.AQUA + I18n.format("gui.ae2powertools.storage_emitter.controls.title"));
        tooltip.add(TextFormatting.GRAY + I18n.format("gui.ae2powertools.storage_emitter.controls.scroll"));
        tooltip.add(TextFormatting.GRAY + I18n.format("gui.ae2powertools.storage_emitter.controls.toggle"));

        return tooltip;
    }

    private List<String> buildResourceTooltip(MonitoredEntry entry) {
        List<String> tooltip = new ArrayList<>();
        if (!entry.hasResource()) return tooltip;

        MonitoredResource resource = entry.getResource();
        IAEStack<?> stack = resource.getStack();
        if (stack instanceof IAEItemStack) {
            ItemStack itemStack = ((IAEItemStack) stack).getDefinition();
            if (!itemStack.isEmpty()) {
                tooltip.addAll(itemStack.getTooltip(
                    mc.player,
                    mc.gameSettings.advancedItemTooltips
                        ? ITooltipFlag.TooltipFlags.ADVANCED
                        : ITooltipFlag.TooltipFlags.NORMAL));
                return tooltip;
            }
        }

        tooltip.addAll(JeiTooltipBridge.buildTooltip(resource));
        return tooltip;
    }

    private String formatTargetProgress(MonitoredEntry entry) {
        int percent = getTargetProgressPercent(entry);
        return getProgressColor(percent) + Integer.toString(percent) + "%" + TextFormatting.GRAY;
    }

    private int getTargetProgressPercent(MonitoredEntry entry) {
        long quantity = Math.max(0, entry.getLastQuantity());
        long threshold = Math.max(0, entry.getThreshold());

        if (entry.getComparison() == ComparisonMode.GREATER
                || entry.getComparison() == ComparisonMode.GREATER_EQUAL) {
            if (threshold == 0) return 100;

            return calculatePercent(quantity, threshold);
        }

        if (quantity <= threshold) return 100;
        if (quantity == 0 || threshold == 0) return 0;

        return calculatePercent(threshold, quantity);
    }

    /**
     * Computes floor(numerator / denominator * 100), capped to 100, without overflowing when
     * either input is near Long.MAX_VALUE.
     */
    private int calculatePercent(long numerator, long denominator) {
        if (numerator <= 0 || denominator <= 0) return 0;
        if (numerator >= denominator) return 100;

        BigInteger scaled = BigInteger.valueOf(numerator).multiply(BigInteger.valueOf(100L));
        BigInteger percent = scaled.divide(BigInteger.valueOf(denominator));
        if (percent.signum() <= 0) return 0;

        return percent.min(BigInteger.valueOf(100L)).intValue();
    }

    private TextFormatting getProgressColor(int percent) {
        if (percent >= 100) return TextFormatting.GREEN;
        if (percent >= 75) return TextFormatting.YELLOW;
        if (percent >= 50) return TextFormatting.GOLD;

        return TextFormatting.RED;
    }

    private void drawEntries(int mouseX, int mouseY) {
        List<MonitoredEntry> entries = container.getHost().getEntries();

        for (int idx = 0; idx < GRID_CAPACITY; idx++) {
            int col = idx % GRID_COLS;
            int row = idx / GRID_COLS;
            // Cell origin: pitch is INNER + 1 (uncolored border on right and bottom).
            int x = guiLeft + GRID_X + col * CELL_W;
            int y = guiTop + GRID_Y + row * CELL_H;

            MonitoredEntry entry = (idx < entries.size()) ? entries.get(idx) : null;

            // Background tint covers the INNER 50x22 area only, leaving the +1 right/bottom border untinted.
            drawEntryBackground(x, y, entry);

            // Hover highlight per zone. Only suppressed when the modal selector is open.
            if (!selectorOpen) drawZoneHover(x, y, mouseX, mouseY, entry);

            // Foreground content: icon + (comparison + numbers if filled).
            drawEntryContent(x, y, entry);
        }
    }

    private void drawEntryBackground(int x, int y, MonitoredEntry entry) {
        if (entry == null) return;

        // Resource-less placeholders get a faint neutral tint regardless of enabled/condition,
        // since we have no resource to evaluate yet.
        if (!entry.hasResource()) {
            drawRect(x, y, x + INNER_W, y + INNER_H, 0x30606060);
            return;
        }

        int color;
        if (!entry.isEnabled()) {
            color = 0x60808080; // Grey for disabled
        } else if (entry.isLastConditionMet()) {
            color = 0x6044BB44; // Translucent green
        } else {
            color = 0x60BB4444; // Translucent red
        }

        // INNER area only, leaving the +1 right/bottom border untinted.
        drawRect(x, y, x + INNER_W, y + INNER_H, color);
    }

    /**
     * Highlights whichever zone (comparator / left / right) the mouse is currently over.
     * Comparator (10x10) wins over left/right when the cursor is inside it; otherwise
     * the full 25x22 half the cursor is in lights up. Visuals always match what a
     * click would do (see {@link #handleEntryClick}).
     */
    private void drawZoneHover(int x, int y, int mouseX, int mouseY, MonitoredEntry entry) {
        CellZone zone = pickZone(x, y, mouseX, mouseY);
        if (zone == CellZone.NONE) return;

        int hl = 0x40FFFFFF;
        switch (zone) {
            case COMPARATOR:
                drawRect(x + CMP_X, y + CMP_Y, x + CMP_X + CMP_SIZE, y + CMP_Y + CMP_SIZE, hl);
                break;
            case LEFT:
                drawRect(x, y, x + LEFT_RIGHT_SPLIT, y + INNER_H, hl);
                break;
            case RIGHT:
                drawRect(x + LEFT_RIGHT_SPLIT, y, x + INNER_W, y + INNER_H, hl);
                break;
            default:
                break;
        }
    }

    /**
     * Returns the zone the given screen-space mouse coordinate is in for a cell at (x,y).
     * Comparator takes priority over left/right because it visually overlaps both halves.
     */
    private static CellZone pickZone(int x, int y, int mouseX, int mouseY) {
        if (mouseX < x || mouseX >= x + INNER_W || mouseY < y || mouseY >= y + INNER_H) return CellZone.NONE;

        int localX = mouseX - x;
        int localY = mouseY - y;

        if (localX >= CMP_X && localX < CMP_X + CMP_SIZE
                && localY >= CMP_Y && localY < CMP_Y + CMP_SIZE) return CellZone.COMPARATOR;

        return localX < LEFT_RIGHT_SPLIT ? CellZone.LEFT : CellZone.RIGHT;
    }

    private void drawEntryContent(int x, int y, MonitoredEntry entry) {
        // Comparator + numbers are drawn for ALL entries (including resource-less placeholders)
        // because the user can pre-configure those before picking a resource for the slot.
        if (entry != null) {
            drawComparison(x + CMP_X, y + CMP_Y, entry.getComparison());
            drawEntryNumbers(x + NUM_X, y + NUM_Y, entry);
        }

        // Icon zone shows either the resource or a clickable "+" placeholder.
        drawEntryIcon(x + ICON_X, y + ICON_Y, entry);
    }

    private void drawComparison(int x, int y, ComparisonMode mode) {
        // 64x64 sheet, 2x2 grid of 20x20 arrow tiles.
        // Spec: x-axis = above/below threshold, y-axis = and-equal / strictly.
        //   GREATER_EQUAL -> (u=0,  v=0)
        //   LESS_EQUAL    -> (u=20, v=0)
        //   GREATER       -> (u=0,  v=20)
        //   LESS          -> (u=20, v=20)
        int u = 0, v = 0;
        switch (mode) {
            case GREATER_EQUAL: break;
            case LESS_EQUAL:    u = CMP_TILE_SIZE; break;
            case GREATER:       v = CMP_TILE_SIZE; break;
            case LESS:          u = CMP_TILE_SIZE; v = CMP_TILE_SIZE; break;
            default:            break;
        }

        // Restore GL state in case a previous pass left lighting/blend dirty.
        GlStateManager.enableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
            GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
            GlStateManager.SourceFactor.ONE,       GlStateManager.DestFactor.ZERO);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        mc.getTextureManager().bindTexture(COMPARISON_ARROWS);

        // drawScaledCustomSizeModalRect handles the 20x20 -> 10x10 down-scaling correctly.
        // The previous use of drawModalRectWithCustomSizedTexture only sampled a 10x10 sub-rect
        // of the 20x20 tile, leaving the arrow invisible.
        Gui.drawScaledCustomSizeModalRect(
            x, y,
            (float) u, (float) v,
            CMP_TILE_SIZE, CMP_TILE_SIZE,
            CMP_SIZE, CMP_SIZE,
            CMP_SHEET_SIZE, CMP_SHEET_SIZE);
    }

    private void drawEntryIcon(int x, int y, MonitoredEntry entry) {
        // Either no entry yet (initial sync state) or a placeholder slot with no resource:
        // both render the same "+" affordance to indicate this slot is clickable.
        if (entry == null || !entry.hasResource()) {
            drawRect(x, y, x + ICON_SIZE, y + ICON_SIZE, 0x40000000);
            GlStateManager.enableTexture2D();
            String plus = "+";
            int w = fontRenderer.getStringWidth(plus);
            fontRenderer.drawString(plus, x + (ICON_SIZE - w) / 2, y + (ICON_SIZE - 8) / 2, 0xFFAAAAAA);
            return;
        }

        GlStateManager.enableTexture2D();
        // Delegate to the unified resource renderer; it handles items, fluids, gas, and essentia
        // with proper GL state management.
        MonitoredResourceRenderer.renderIcon(entry.getResource(), x, y, ICON_SIZE);
    }

    private void drawEntryNumbers(int x, int y, MonitoredEntry entry) {
        // Slim form keeps numbers compact enough to fit in 16 px.
        String currentStr = ReadableNumberConverter.INSTANCE.toSlimReadableForm(entry.getLastQuantity());
        String thresholdStr = ReadableNumberConverter.INSTANCE.toSlimReadableForm(entry.getThreshold());
        int color = entry.isEnabled() ? 0xFFFFFFFF : 0xFF808080;

        // Half-size text: scale by 0.5 around the cell origin, so a 16x16 box has
        // 32x32 of drawable space in the scaled coord system. Stack the two numbers
        // vertically with a 1 px gap between them, centered horizontally.
        // We pop right after to avoid leaking the scaled matrix into other GUI passes.
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        GlStateManager.scale(0.5F, 0.5F, 1.0F);

        int scaledBoxW = NUM_SIZE * 2;
        int currentW = fontRenderer.getStringWidth(currentStr);
        int thresholdW = fontRenderer.getStringWidth(thresholdStr);

        // Vertical layout in the half-scaled space: ~3 px top padding, line, 1 px gap, line.
        int line1Y = 6;
        int line2Y = line1Y + fontRenderer.FONT_HEIGHT + 2;

        fontRenderer.drawStringWithShadow(currentStr, (scaledBoxW - currentW) / 2f, line1Y, color);
        fontRenderer.drawStringWithShadow(thresholdStr, (scaledBoxW - thresholdW) / 2f, line2Y, color);

        GlStateManager.popMatrix();
    }

    private void drawCountField() {
        if (countField == null || !countField.getVisible()) return;

        // Solid black background so the field is visually distinct over the cells.
        drawRect(countField.x - 1, countField.y - 1,
            countField.x + COUNT_FIELD_W + 1, countField.y + COUNT_FIELD_H + 1,
            0xFF000000);

        // GuiTextField doesn't support centering natively, so we manually draw the text on top.
        String txt = countField.getText();
        int textW = fontRenderer.getStringWidth(txt);
        int textX = countField.x + (COUNT_FIELD_W - textW) / 2;
        int textY = countField.y + (COUNT_FIELD_H - 8) / 2 + 1;
        fontRenderer.drawString(txt, textX, textY, 0xFFFFFFFF);

        // Blinking cursor approximation, positioned just after the text up to the cursor index.
        if (countField.isFocused() && (System.currentTimeMillis() / 500) % 2 == 0) {
            int cursorPos = countField.getCursorPosition();
            String beforeCursor = txt.substring(0, Math.min(cursorPos, txt.length()));
            int caretX = textX + fontRenderer.getStringWidth(beforeCursor);
            drawRect(caretX, textY - 1, caretX + 1, textY + 9, 0xFFFFFFFF);
        }
    }

    // ====================== SELECTOR MODAL ======================

    private void openSelector(int targetIndex) {
        selectorOpen = true;
        selectorTargetIndex = targetIndex;
        selectorScrollOffset = 0;
        selectorResources.clear();
        filteredResources.clear();

        selectorSearchField = new GuiTextField(100, fontRenderer,
            selectorLeft + SELECTOR_SEARCH_X, selectorTop + SELECTOR_SEARCH_Y,
            SELECTOR_SEARCH_W, SELECTOR_SEARCH_H);
        selectorSearchField.setMaxStringLength(50);
        selectorSearchField.setEnableBackgroundDrawing(true);
        selectorSearchField.setTextColor(0xFFFFFF);
        selectorSearchField.setFocused(true);

        PowerToolsNetwork.INSTANCE.sendToServer(
            new PacketRequestMonitorContents(container.getHost()));
    }

    /**
     * Called from the network thread (via PacketMonitorContentsSync) to provide
     * the list of available resources for the selector modal.
     */
    public static void handleContentsSync(List<MonitoredResource> resources) {
        if (activeInstance == null || !activeInstance.selectorOpen) return;

        activeInstance.selectorResources = new ArrayList<>(resources);
        activeInstance.filterSelectorResources();
    }

    /**
     * Called from the network thread (via PacketStorageEntryStateSync) to push
     * live per-entry quantities and condition flags into the open GUI.
     * The arrays correspond 1:1 with the host's entries list.
     */
    public static void handleEntryStateSync(long[] quantities, boolean[] conditions) {
        if (activeInstance == null) return;

        List<MonitoredEntry> entries = activeInstance.container.getHost().getEntries();
        int n = Math.min(entries.size(), Math.min(quantities.length, conditions.length));
        for (int i = 0; i < n; i++) {
            entries.get(i).setLastQuantity(quantities[i]);
            entries.get(i).setLastConditionMet(conditions[i]);
        }
    }

    /**
     * Called from the network thread (via PacketSyncMonitorEntries) to mirror the server's
     * entry list onto the client host. Required because the host TileEntity / Part doesn't
     * push the entries list through the standard NBT update packet path - without this,
     * the GUI would never see entries added or modified server-side (e.g. after picking
     * a resource in the selector or cycling a comparator).
     * <p>
     * The transient lastQuantity / lastConditionMet fields are NOT serialized by
     * MonitoredEntry, so a fresh sync will not make all entries flicker.
     */
    public static void handleEntriesSync(List<MonitoredEntry> entries) {
        if (activeInstance == null) return;

        // Preserve last quantity / condition for entries whose resource at the same
        // index is unchanged. Resource-less placeholders are skipped: their lastQuantity
        // is meaningless without a resource to look up.
        List<MonitoredEntry> oldEntries = activeInstance.container.getHost().getEntries();
        for (int i = 0; i < entries.size() && i < oldEntries.size(); i++) {
            MonitoredEntry oldE = oldEntries.get(i);
            MonitoredEntry newE = entries.get(i);
            if (!oldE.hasResource() || !newE.hasResource()) continue;
            if (oldE.getResource().toKey().equals(newE.getResource().toKey())) {
                newE.setLastQuantity(oldE.getLastQuantity());
                newE.setLastConditionMet(oldE.isLastConditionMet());
            }
        }

        activeInstance.container.getHost().setEntries(entries);
    }

    private void filterSelectorResources() {
        String search = selectorSearchField != null
            ? selectorSearchField.getText().toLowerCase().trim()
            : "";

        if (search.isEmpty()) {
            filteredResources = new ArrayList<>(selectorResources);
        } else {
            filteredResources = new ArrayList<>();
            for (MonitoredResource r : selectorResources) {
                if (r.getDisplayName().toLowerCase().contains(search)) filteredResources.add(r);
            }
        }

        clampSelectorScroll();
    }

    private void clampSelectorScroll() {
        int totalRows = (filteredResources.size() + SELECTOR_COLS - 1) / SELECTOR_COLS;
        int maxScroll = Math.max(0, totalRows - SELECTOR_ROWS);
        selectorScrollOffset = Math.max(0, Math.min(selectorScrollOffset, maxScroll));
    }

    private void drawSelectorModal(int mouseX, int mouseY, float partialTicks) {
        // Reset GL state - super.drawScreen leaves item lighting/depth on, which would
        // tint the modal texture and let entry icons bleed through.
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        // Solid textured background (mirrors maintainer's recipe selector layout exactly).
        mc.getTextureManager().bindTexture(SELECTOR_BACKGROUND);
        drawTexturedModalRect(selectorLeft, selectorTop, 0, 0, SELECTOR_WIDTH, SELECTOR_HEIGHT);

        // Title and search bar
        fontRenderer.drawString(I18n.format("gui.ae2powertools.storage_emitter.select_content"),
            selectorLeft + 8, selectorTop + 6, 0x000000);

        if (selectorSearchField != null) selectorSearchField.drawTextBox();

        // Reset state again because the text field renderer may have left things in an odd state.
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        selectorHoveredSlot = -1;
        for (int row = 0; row < SELECTOR_ROWS; row++) {
            for (int col = 0; col < SELECTOR_COLS; col++) {
                int displaySlot = row * SELECTOR_COLS + col;
                int index = (selectorScrollOffset + row) * SELECTOR_COLS + col;

                int slotX = selectorLeft + SELECTOR_GRID_X + col * SELECTOR_SLOT_SIZE;
                int slotY = selectorTop + SELECTOR_GRID_Y + row * SELECTOR_SLOT_SIZE;

                boolean hovered = mouseX >= slotX && mouseX < slotX + SELECTOR_SLOT_SIZE
                    && mouseY >= slotY && mouseY < slotY + SELECTOR_SLOT_SIZE;

                if (hovered) {
                    selectorHoveredSlot = displaySlot;
                    drawRect(slotX + 1, slotY + 1, slotX + SELECTOR_SLOT_SIZE - 1, slotY + SELECTOR_SLOT_SIZE - 1, 0x80FFFFFF);
                }

                if (index < 0 || index >= filteredResources.size()) continue;

                MonitoredResource resource = filteredResources.get(index);
                MonitoredResourceRenderer.renderIcon(resource, slotX + 1, slotY + 1, 16);

                // Reset GL state per slot - the resource renderer disables lighting/depth as needed,
                // but we re-enable here so the scrollbar renders cleanly.
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            }
        }

        drawSelectorScrollbar();

        // Final state restore so anything drawn after the modal (e.g. tooltip) starts clean.
        GlStateManager.enableDepth();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void drawSelectorScrollbar() {
        int sbX = selectorLeft + SELECTOR_SCROLL_X;
        int sbY = selectorTop + SELECTOR_SCROLL_Y;

        mc.getTextureManager().bindTexture(SCROLLBAR_TEXTURE);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        int totalRows = (filteredResources.size() + SELECTOR_COLS - 1) / SELECTOR_COLS;
        int maxScroll = Math.max(0, totalRows - SELECTOR_ROWS);

        if (maxScroll <= 0) {
            // Disabled thumb (right-shifted variant of the creative-tab scrollbar sprite).
            drawTexturedModalRect(sbX, sbY, 244, 0, SELECTOR_SCROLL_THUMB_W, SELECTOR_SCROLL_THUMB_H);
            return;
        }

        int thumbY = sbY + (SELECTOR_SCROLL_TRACK_H - SELECTOR_SCROLL_THUMB_H) * selectorScrollOffset / maxScroll;
        drawTexturedModalRect(sbX, thumbY, 232, 0, SELECTOR_SCROLL_THUMB_W, SELECTOR_SCROLL_THUMB_H);
    }

    private void drawSelectorTooltip(int mouseX, int mouseY) {
        if (selectorHoveredSlot < 0) return;

        int row = selectorHoveredSlot / SELECTOR_COLS;
        int col = selectorHoveredSlot % SELECTOR_COLS;
        int index = (selectorScrollOffset + row) * SELECTOR_COLS + col;
        if (index < 0 || index >= filteredResources.size()) return;

        MonitoredResource resource = filteredResources.get(index);

        // For items: defer to vanilla's GuiScreen.renderToolTip which honors rarity coloring
        // for the first line and forces secondary lines (ore-dict, NBT advanced, etc.) to grey.
        // Building the list manually + drawHoveringText would have to replicate that color
        // logic, and getting it wrong is exactly what made oredict lines render white.
        IAEStack<?> stack = resource.getStack();
        if (stack instanceof IAEItemStack) {
            ItemStack is = ((IAEItemStack) stack).getDefinition();
            this.renderToolTip(is, mouseX, mouseY);
            return;
        }

        // Non-item resources (fluid / gas / essentia): delegate to JEI's own ingredient
        // renderer when JEI is loaded, gives us identical-looking tooltips to JEI's
        // ingredient list overlay without us having to mirror per-mod formatting. Falls
        // back to a manual display name + mod source + type label tooltip when JEI is absent.
        List<String> tooltip = JeiTooltipBridge.buildTooltip(resource);

        // Use absolute screen coords - drawHoveringText offsets relative to gui origin
        // and would push the tooltip off into the wrong corner.
        GuiUtils.drawHoveringText(tooltip, mouseX, mouseY, width, height, -1, fontRenderer);
    }

    // ====================== INPUT HANDLING ======================

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (selectorOpen) {
            handleSelectorClick(mouseX, mouseY, mouseButton);
            return;
        }

        // If the count field is visible and the click is OUTSIDE it, save+dismiss FIRST,
        // then continue with normal hit detection so the user can interact with what they clicked.
        boolean dismissedField = false;
        if (countField != null && countField.getVisible()) {
            int fx = countField.x;
            int fy = countField.y;
            boolean insideField = mouseX >= fx && mouseX < fx + COUNT_FIELD_W
                && mouseY >= fy && mouseY < fy + COUNT_FIELD_H;

            if (insideField) {
                countField.mouseClicked(mouseX, mouseY, mouseButton);
                return;
            }

            hideCountField(true);
            dismissedField = true;
        }

        // Side button: match-mode toggle (lives outside guiLeft).
        if (matchBtnHovered && mouseButton == 0) {
            cycleMatchMode();
            return;
        }

        super.mouseClicked(mouseX, mouseY, mouseButton);

        // Per-cell hit detection. Reject clicks outside the grid early.
        int relX = mouseX - guiLeft;
        int relY = mouseY - guiTop;
        if (relX < GRID_X || relY < GRID_Y) return;

        int gx = relX - GRID_X;
        int gy = relY - GRID_Y;
        int col = gx / CELL_W;
        int row = gy / CELL_H;
        if (col < 0 || col >= GRID_COLS || row < 0 || row >= GRID_ROWS) return;

        // Reject clicks landing in the +1 right/bottom border (no interaction there).
        int localX = gx - col * CELL_W;
        int localY = gy - row * CELL_H;
        if (localX >= INNER_W || localY >= INNER_H) return;

        int idx = row * GRID_COLS + col;

        handleEntryClick(idx, localX, localY, mouseButton, dismissedField);
    }

    private void handleEntryClick(int idx, int localX, int localY, int mouseButton, boolean dismissedField) {
        List<MonitoredEntry> entries = container.getHost().getEntries();
        if (idx < 0 || idx >= entries.size()) return;

        MonitoredEntry entry = entries.get(idx);

        if (mouseButton == 1) {
            sendEntryUpdate(idx, entry.getComparison(), entry.getThreshold(), !entry.isEnabled());
            return;
        }

        if (mouseButton != 0) return;

        // Use the same zone picker as the hover highlight so click and visual feedback agree.
        // pickZone expects screen-space coords; we pass (0, 0) as the cell origin so localX/localY
        // are evaluated against the inner cell rect.
        CellZone zone = pickZone(0, 0, localX, localY);
        if (zone == CellZone.NONE) return;

        switch (zone) {
            case COMPARATOR:
                cycleComparison(idx);
                return;

            case LEFT:
                openSelector(idx);
                return;

            case RIGHT:
                showCountField(idx);
                return;

            default: return;
        }
    }

    private void handleSelectorClick(int mouseX, int mouseY, int mouseButton) {
        // Click outside the modal closes it.
        if (mouseX < selectorLeft || mouseX >= selectorLeft + SELECTOR_WIDTH
                || mouseY < selectorTop || mouseY >= selectorTop + SELECTOR_HEIGHT) {
            selectorOpen = false;
            return;
        }

        if (selectorSearchField != null) selectorSearchField.mouseClicked(mouseX, mouseY, mouseButton);

        // Use the hovered-slot tracker that drawSelectorModal already populated for cheap lookups
        // and to keep click hit detection in sync with the highlight.
        if (mouseButton == 0 && selectorHoveredSlot >= 0) {
            int row = selectorHoveredSlot / SELECTOR_COLS;
            int col = selectorHoveredSlot % SELECTOR_COLS;
            int index = (selectorScrollOffset + row) * SELECTOR_COLS + col;

            if (index >= 0 && index < filteredResources.size()) {
                MonitoredResource selected = filteredResources.get(index);
                PowerToolsNetwork.INSTANCE.sendToServer(
                    new PacketSelectMonitorContent(container.getHost(), selected, selectorTargetIndex));
                selectorOpen = false;
            }
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (selectorOpen) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                selectorOpen = false;
                return;
            }
            if (selectorSearchField != null && selectorSearchField.textboxKeyTyped(typedChar, keyCode)) {
                filterSelectorResources();
                return;
            }
            return;
        }

        // Count field captures all text input until dismissed.
        if (countField != null && countField.getVisible() && countField.isFocused()) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                hideCountField(false); // Esc cancels.
                return;
            }

            if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
                hideCountField(true);
                return;
            }

            // Filter to digits + editing keys.
            if (Character.isDigit(typedChar) || isEditingKey(keyCode)) {
                if (countField.textboxKeyTyped(typedChar, keyCode)) {
                    sendCountFieldUpdate(); // Live update, refreshes as the user types.
                    return;
                }
            }

            return;
        }

        super.keyTyped(typedChar, keyCode);
    }

    private static boolean isEditingKey(int keyCode) {
        return keyCode == Keyboard.KEY_BACK
            || keyCode == Keyboard.KEY_DELETE
            || keyCode == Keyboard.KEY_LEFT
            || keyCode == Keyboard.KEY_RIGHT
            || keyCode == Keyboard.KEY_HOME
            || keyCode == Keyboard.KEY_END;
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();

        int scroll = Mouse.getEventDWheel();
        if (scroll == 0) return;

        if (selectorOpen) {
            selectorScrollOffset -= Integer.signum(scroll);
            clampSelectorScroll();
            return;
        }

        if (!isShiftDown()) return;

        int mouseX = Mouse.getEventX() * width / mc.displayWidth;
        int mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1;

        GridHit hit = getGridHit(mouseX, mouseY);
        if (hit == null || hit.zone != CellZone.RIGHT) return;

        adjustEntryThreshold(hit.index, scroll > 0);
    }

    // ====================== STATE TRANSITIONS ======================

    private boolean isShiftDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
    }

    private GridHit getGridHit(int mouseX, int mouseY) {
        int relX = mouseX - guiLeft;
        int relY = mouseY - guiTop;
        if (relX < GRID_X || relY < GRID_Y) return null;

        int gx = relX - GRID_X;
        int gy = relY - GRID_Y;
        int col = gx / CELL_W;
        int row = gy / CELL_H;
        if (col < 0 || col >= GRID_COLS || row < 0 || row >= GRID_ROWS) return null;

        int localX = gx - col * CELL_W;
        int localY = gy - row * CELL_H;
        if (localX >= INNER_W || localY >= INNER_H) return null;

        CellZone zone = pickZone(0, 0, localX, localY);
        if (zone == CellZone.NONE) return null;

        return new GridHit(row * GRID_COLS + col, zone);
    }

    private void cycleMatchMode() {
        MatchMode next = container.getSyncMatchMode().next();
        PowerToolsNetwork.INSTANCE.sendToServer(
            new PacketSetMatchMode(container.getHost(), next));
    }

    private void syncEmitterRedstoneButton() {
        if (redstoneSignalBtn == null) return;

        // Reuse AE2's redstone-emitter button art while keeping this feature's
        // semantics local to AE2 Power Tools.
        redstoneSignalBtn.set(container.getSyncEmitterRedstoneSignalStrength() == EmitterRedstoneStrength.STRONG
            ? RedstoneMode.HIGH_SIGNAL
            : RedstoneMode.LOW_SIGNAL);
    }

    private boolean isRedstoneSignalButtonHovered(int mouseX, int mouseY) {
        return redstoneSignalBtn != null
            && redstoneSignalBtn.visible
            && mouseX >= redstoneSignalBtn.x
            && mouseX < redstoneSignalBtn.x + redstoneSignalBtn.width
            && mouseY >= redstoneSignalBtn.y
            && mouseY < redstoneSignalBtn.y + redstoneSignalBtn.height;
    }

    private void cycleComparison(int idx) {
        List<MonitoredEntry> entries = container.getHost().getEntries();
        if (idx < 0 || idx >= entries.size()) return;

        MonitoredEntry e = entries.get(idx);
        ComparisonMode next = e.getComparison().next();
        sendEntryUpdate(idx, next, e.getThreshold(), e.isEnabled());
    }

    private void adjustEntryThreshold(int idx, boolean doubleTarget) {
        List<MonitoredEntry> entries = container.getHost().getEntries();
        if (idx < 0 || idx >= entries.size()) return;

        MonitoredEntry entry = entries.get(idx);
        long oldThreshold = entry.getThreshold();
        long newThreshold = doubleTarget ? doubleThreshold(oldThreshold) : oldThreshold / 2;
        if (newThreshold == oldThreshold) return;

        entry.setThreshold(newThreshold);

        if (countField != null && countField.getVisible() && countFieldEntryIndex == idx) {
            countField.setText(formatWithCommas(newThreshold));
            countField.setCursorPositionEnd();
        }

        sendEntryUpdate(idx, entry.getComparison(), newThreshold, entry.isEnabled());
    }

    private long doubleThreshold(long threshold) {
        if (threshold <= 0) return 1;
        if (threshold > Long.MAX_VALUE / 2) return Long.MAX_VALUE;

        return threshold * 2;
    }

    private void sendEntryUpdate(int idx, ComparisonMode comparison, long threshold, boolean enabled) {
        PowerToolsNetwork.INSTANCE.sendToServer(
            new PacketUpdateMonitorEntry(container.getHost(), idx, comparison, threshold, enabled));
    }

    private void showCountField(int idx) {
        List<MonitoredEntry> entries = container.getHost().getEntries();
        if (idx < 0 || idx >= entries.size()) return;

        countFieldEntryIndex = idx;
        countField.setVisible(true);
        countField.setFocused(true);
        countField.setText(formatWithCommas(entries.get(idx).getThreshold()));
        countField.setCursorPositionEnd();

        Keyboard.enableRepeatEvents(true);
    }

    private void hideCountField(boolean save) {
        if (countField == null || !countField.getVisible()) return;

        if (save && countFieldEntryIndex >= 0) sendCountFieldUpdate();

        countField.setVisible(false);
        countField.setFocused(false);
        countFieldEntryIndex = -1;

        Keyboard.enableRepeatEvents(false);
    }

    /**
     * Parses the current count field value and sends an update for the active entry.
     * Empty / unparseable input is treated as "not accounted for" (per spec) and stored as 0.
     */
    private void sendCountFieldUpdate() {
        if (countFieldEntryIndex < 0) return;

        List<MonitoredEntry> entries = container.getHost().getEntries();
        if (countFieldEntryIndex >= entries.size()) return;

        long value = parseCommaNumber(countField.getText());
        MonitoredEntry e = entries.get(countFieldEntryIndex);
        sendEntryUpdate(countFieldEntryIndex, e.getComparison(), value, e.isEnabled());
    }

    /**
     * Parses a string into a long, ignoring everything but digits.
     * Returns 0 for empty input. Caps at Long.MAX_VALUE on overflow rather than throwing.
     */
    private static long parseCommaNumber(String txt) {
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < txt.length(); i++) {
            char c = txt.charAt(i);
            if (Character.isDigit(c)) digits.append(c);
        }

        if (digits.length() == 0) return 0;

        try {
            return Long.parseLong(digits.toString());
        } catch (NumberFormatException e) {
            // Number too big to fit in a long: cap to honor "up to Max Long" gracefully.
            return Long.MAX_VALUE;
        }
    }

    private static String formatWithCommas(long value) {
        return String.format("%,d", value);
    }

    // ====================== JEI INTEGRATION ======================

    /**
     * Exclusion zones for JEI's overlay so its sidebar doesn't paint over our
     * out-of-frame side button. Mirrors GuiBetterLevelMaintainer's helper of the same name.
     * <p>
     * The match-mode (AND/OR) button sits OUTSIDE guiLeft to the left, so JEI thinks
     * that area is free space and happily covers it with its filter UI; we have to
     * declare it explicitly. The polling-rate wrench tab button is inside the GUI
     * bounds and doesn't need to be listed.
     */
    public List<java.awt.Rectangle> getJEIExclusionArea() {
        List<java.awt.Rectangle> areas = new ArrayList<>();
        areas.add(new java.awt.Rectangle(matchBtnX, matchBtnY, SIDE_BTN_SIZE, SIDE_BTN_SIZE));
        return areas;
    }
}
