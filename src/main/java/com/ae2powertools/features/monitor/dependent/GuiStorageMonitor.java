package com.ae2powertools.features.monitor.dependent;

import java.awt.Rectangle;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import net.minecraft.client.Minecraft;
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
import com.ae2powertools.client.gui.VanillaButtonRenderer;
import com.ae2powertools.features.monitor.MonitoredEntry;
import com.ae2powertools.features.monitor.MonitoredResource;
import com.ae2powertools.features.monitor.client.MonitoredResourceRenderer;
import com.ae2powertools.features.monitor.emitter.EmitterRedstonePower;
import com.ae2powertools.features.monitor.emitter.IEmitterRedstoneHost;
import com.ae2powertools.integration.jei.JeiTooltipBridge;
import com.ae2powertools.network.PacketOpenStorageMonitorPollingRate;
import com.ae2powertools.network.PacketRequestMonitorContents;
import com.ae2powertools.network.PacketSelectMonitorContent;
import com.ae2powertools.network.PacketSetEmitterRedstonePower;
import com.ae2powertools.network.PacketSetEmitterRedstoneStrength;
import com.ae2powertools.network.PacketSetHysteresisMode;
import com.ae2powertools.network.PacketSetMatchMode;
import com.ae2powertools.network.PacketToggleAlarmRegistration;
import com.ae2powertools.network.PacketUpdateMonitorEntry;
import com.ae2powertools.network.PowerToolsNetwork;
import com.ae2powertools.util.FormatUtil;


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
 *     - Icon zone:   16x16 at local (3, 3), with the current quantity rendered over it
 *     - Comparison:  10x10 at local (20, 6), straddles left/right halves
 *     - Thresholds:  16x16+ at local (31, 3), one threshold in normal mode and
 *       top/bottom increasing/decreasing thresholds in hysteresis mode
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
 *   4. Otherwise inside the threshold side (localX >= 25): show the count field for that
 *      threshold. Hysteresis mode splits that side into upper / lower halves.
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
    private static final ResourceLocation HYSTERESIS_ICON = new ResourceLocation(
        Tags.MODID, "textures/guis/hysteresis_icon.png");
    private static final ResourceLocation REDSTONE_STRENGTH_PANEL = new ResourceLocation(
        Tags.MODID, "textures/guis/redstone_strength_panel.png");
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
    /** X coordinate that splits the cell into selector vs threshold halves. */
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
    private static final int SIDE_BTN_SPACING = 4;
    private static final int MATCH_MODE_BTN_Y = 4;

    // --- Wrench tab button (polling rate sub-GUI launcher) ---
    /**
     * AE2 states.png icon index for the wrench-style polling-rate icon.
     * Mirrors CELLS' {@code AbstractResourceInterfaceGui.pollingRateButton}
     * which uses the exact same value.
     */
    private static final int WRENCH_ICON_INDEX = 2 + 5 * 16;

    // --- Emitter strength panel (drawn outside the GUI on the right) ---
    private static final int STRENGTH_PANEL_OFFSET_X = 2;
    private static final int STRENGTH_PANEL_PADDING = 3;
    private static final int STRENGTH_PANEL_WIDTH = 72;
    private static final int STRENGTH_PANEL_HEIGHT = 66;
    private static final int STRENGTH_BTN_Y_START = 12;
    private static final int STRENGTH_BTN_X_OFFSET = 40;
    private static final int STRENGTH_BTN_Y_OFFSET = 16;
    private static final int STRENGTH_BTN_WIDTH = 25;
    private static final int STRENGTH_BTN_HEIGHT = 14;

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
    private enum CellZone { NONE, COMPARATOR, SELECTOR, UPPER_THRESHOLD, LOWER_THRESHOLD }

    private enum ThresholdField { UPPER, LOWER }

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

    /** Host-level hysteresis toggle side button. */
    private int hysteresisBtnX, hysteresisBtnY;
    private boolean hysteresisBtnHovered;

    /** Emitter-only redstone-strength button. */
    private GuiImgButton redstoneSignalBtn;

    /** Wrench tab button (vanilla GuiButton via AE2's GuiTabButton). */
    private GuiTabButton pollingRateBtn;

    /** Alarm-only registration toggle button shown next to the polling-rate tab. */
    private GuiButton alarmRegistrationBtn;

    /** Compact strength controls drawn in the right-side panel. */
    private final List<CompactVanillaButton> emitterStrengthButtons = new ArrayList<>();
    private int emitterStrengthPanelX;
    private int emitterStrengthPanelY;
    private boolean emitterStrengthTextHovered = false;

    // --- Count field (threshold editor) ---
    private GuiTextField countField;
    /** Index of the entry currently being edited via the count field, or -1 when hidden. */
    private int countFieldEntryIndex = -1;
    private ThresholdField countFieldTarget = ThresholdField.UPPER;

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
        emitterStrengthButtons.clear();

        matchBtnX = guiLeft + SIDE_BTN_X_OFFSET;
        matchBtnY = guiTop + MATCH_MODE_BTN_Y;
        hysteresisBtnX = matchBtnX;
        emitterStrengthPanelX = guiLeft + GUI_WIDTH + STRENGTH_PANEL_OFFSET_X;
        emitterStrengthPanelY = guiTop;

        int nextSideButtonY = guiTop + MATCH_MODE_BTN_Y;

        if (container.supportsMatchMode()) {
            nextSideButtonY += SIDE_BTN_SIZE + SIDE_BTN_SPACING;
        }

        if (container.supportsEmitterRedstone()) {
            redstoneSignalBtn = new GuiImgButton(
                matchBtnX,
                nextSideButtonY,
                Settings.REDSTONE_EMITTER,
                RedstoneMode.LOW_SIGNAL);
            this.buttonList.add(redstoneSignalBtn);
            addEmitterStrengthButtons();

            nextSideButtonY = redstoneSignalBtn.y + SIDE_BTN_SIZE + SIDE_BTN_SPACING;
        }

        hysteresisBtnY = nextSideButtonY;

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

        if (container.supportsPlayerRegistration()) {
            alarmRegistrationBtn = new GuiButton(
                201,
                pollingRateBtn.x - 18,
                guiTop + 3,
                16,
                16,
                container.isSyncPlayerRegistered() ? "-" : "+");
            this.buttonList.add(alarmRegistrationBtn);
        }

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

        if (button instanceof CompactVanillaButton) {
            adjustEmitterStrength(((CompactVanillaButton) button).getDelta());
            return;
        }

        if (button == redstoneSignalBtn) {
            PowerToolsNetwork.INSTANCE.sendToServer(
                new PacketSetEmitterRedstonePower(
                    container.getHost(),
                    container.getSyncEmitterRedstonePower().next()));
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

        if (button == alarmRegistrationBtn) {
            PowerToolsNetwork.INSTANCE.sendToServer(
                new PacketToggleAlarmRegistration(container.getHost()));
        }
    }

    // ====================== DRAWING ======================

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        drawDefaultBackground();

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        mc.getTextureManager().bindTexture(BACKGROUND);
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize);

        if (container.supportsEmitterRedstone()) drawEmitterStrengthPanel(mouseX, mouseY);

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
        syncAlarmRegistrationButton();
        super.drawScreen(mouseX, mouseY, partialTicks);

        // Tooltips and modal go on top of everything (after slot rendering).
        if (selectorOpen) {
            drawSelectorModal(mouseX, mouseY, partialTicks);
            drawSelectorTooltip(mouseX, mouseY);
            return;
        }

        // Side-button hover tooltip.
        if (matchBtnHovered) drawMatchModeTooltip(mouseX, mouseY);

        if (hysteresisBtnHovered) drawHysteresisTooltip(mouseX, mouseY);

        if (isRedstoneSignalButtonHovered(mouseX, mouseY)) {
            drawRedstonePowerTooltip(mouseX, mouseY);
        }

        if (isAlarmRegistrationButtonHovered(mouseX, mouseY)) {
            drawAlarmRegistrationTooltip(mouseX, mouseY);
        }

        if (emitterStrengthTextHovered) drawRedstoneStrengthTooltip(mouseX, mouseY);

        drawHoveredEntryTooltip(mouseX, mouseY);

        // Polling-rate tab-button tooltip, GuiContainer doesn't render it for us.
        if (pollingRateBtn != null && pollingRateBtn.visible
                && mouseX >= pollingRateBtn.x && mouseX < pollingRateBtn.x + pollingRateBtn.width
                && mouseY >= pollingRateBtn.y && mouseY < pollingRateBtn.y + pollingRateBtn.height) {

            String interval = FormatUtil.formatTimeTicks(container.refreshRate);
            List<String> tt = new ArrayList<>();
            tt.add("§e" + I18n.format("gui.ae2powertools.storage_emitter.polling_rate.tooltip", interval) + "§r");
            tt.add("");
            tt.add("§7" + I18n.format("gui.ae2powertools.storage_emitter.polling_rate.description") + "§r");
            GuiUtils.drawHoveringText(tt, mouseX, mouseY, width, height, -1, fontRenderer);
        }
    }

    private void drawSideButtons(int mouseX, int mouseY) {
        matchBtnHovered = container.supportsMatchMode()
            && mouseX >= matchBtnX && mouseX < matchBtnX + SIDE_BTN_SIZE
            && mouseY >= matchBtnY && mouseY < matchBtnY + SIDE_BTN_SIZE;
        hysteresisBtnHovered = container.getHost().supportsHysteresis()
            && mouseX >= hysteresisBtnX && mouseX < hysteresisBtnX + SIDE_BTN_SIZE
            && mouseY >= hysteresisBtnY && mouseY < hysteresisBtnY + SIDE_BTN_SIZE;

        // Reset GL state so the button doesn't inherit lighting/depth from prior draws.
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        if (container.supportsMatchMode()) {
            drawLabeledSideButton(
                matchBtnX, matchBtnY,
                container.getSyncMatchMode().getSymbol(),
                0xFFFFFFFF,
                matchBtnHovered
            );
        }

        if (container.getHost().supportsHysteresis()) {
            drawTexturedSideButtonWithOffset(
                hysteresisBtnX, hysteresisBtnY,
                HYSTERESIS_ICON,
                container.isSyncHysteresisEnabled() ? 0 : 1, hysteresisBtnHovered ? 1 : 0,
                2, 2,
                hysteresisBtnHovered
            );
        }

        GlStateManager.enableDepth();
    }

    private void drawLabeledSideButton(int x, int y, String label, int color, boolean hovered) {
        mc.getTextureManager().bindTexture(AE2_STATES);
        drawTexturedModalRect(x, y, 240, 240, SIDE_BTN_SIZE, SIDE_BTN_SIZE);

        int labelW = fontRenderer.getStringWidth(label);
        fontRenderer.drawString(label,
            x + (SIDE_BTN_SIZE - labelW) / 2,
            y + (SIDE_BTN_SIZE - 8) / 2,
            color);

        if (hovered) {
            drawRect(x + 1, y + 1, x + SIDE_BTN_SIZE - 1, y + SIDE_BTN_SIZE - 1, 0x40FFFFFF);
        }
    }

    private void drawTexturedSideButtonWithOffset(int x, int y, ResourceLocation texture,
            int offsetX, int offsetY, int statesX, int statesY, boolean hovered) {
        mc.getTextureManager().bindTexture(AE2_STATES);
        drawTexturedModalRect(x, y, 15 * 16, 15 * 16, SIDE_BTN_SIZE, SIDE_BTN_SIZE);

        mc.getTextureManager().bindTexture(texture);
        drawScaledCustomSizeModalRect(x, y, SIDE_BTN_SIZE * offsetX, SIDE_BTN_SIZE * offsetY,
                                            SIDE_BTN_SIZE, SIDE_BTN_SIZE, SIDE_BTN_SIZE, SIDE_BTN_SIZE,
                                            SIDE_BTN_SIZE * statesX, SIDE_BTN_SIZE * statesY);

        if (hovered) {
            drawRect(x + 1, y + 1, x + SIDE_BTN_SIZE - 1, y + SIDE_BTN_SIZE - 1, 0x40FFFFFF);
        }
    }

    private void drawMatchModeTooltip(int mouseX, int mouseY) {
        List<String> tt = new ArrayList<>();
        tt.add(I18n.format("gui.ae2powertools.storage_emitter.match_mode",
            container.getSyncMatchMode().name()));
        tt.add("§7" + I18n.format("gui.ae2powertools.storage_emitter.match_mode.click_toggle") + "§r");
        GuiUtils.drawHoveringText(tt, mouseX, mouseY, width, height, -1, fontRenderer);
    }

    private void drawHysteresisTooltip(int mouseX, int mouseY) {
        String prefix = "gui.ae2powertools.storage_emitter.hysteresis";
        List<String> tt = new ArrayList<>();
        tt.add(I18n.format(
            prefix,
            I18n.format(prefix + (container.isSyncHysteresisEnabled() ? ".on" : ".off"))
        ));
        tt.add("§7" + I18n.format(prefix + ".click_toggle") + "§r");
        GuiUtils.drawHoveringText(tt, mouseX, mouseY, width, height, -1, fontRenderer);
    }

    private void drawRedstonePowerTooltip(int mouseX, int mouseY) {
        EmitterRedstonePower redstonePower = container.getSyncEmitterRedstonePower();

        List<String> tt = new ArrayList<>();
        tt.add(I18n.format(
            "gui.ae2powertools.storage_emitter.redstone_signal",
            I18n.format(redstonePower.getLangKey())));
        tt.add("§7" + I18n.format("gui.ae2powertools.storage_emitter.redstone_signal.click_toggle") + "§r");
        GuiUtils.drawHoveringText(tt, mouseX, mouseY, width, height, -1, fontRenderer);
    }

    private void drawRedstoneStrengthTooltip(int mouseX, int mouseY) {
        List<String> tt = new ArrayList<>();
        tt.add(I18n.format("gui.ae2powertools.storage_emitter.strength.tooltip"));
        tt.add("");
        tt.add("§7" + I18n.format("gui.ae2powertools.storage_emitter.strength.description") + "§r");
        GuiUtils.drawHoveringText(tt, mouseX, mouseY, width, height, -1, fontRenderer);
    }

    private void drawAlarmRegistrationTooltip(int mouseX, int mouseY) {
        String prefix = "gui.ae2powertools.level_monitor_alarm.registration";
        List<String> tt = new ArrayList<>();
        tt.add(I18n.format(container.isSyncPlayerRegistered() ? prefix + ".registered" : prefix + ".unregistered"));
        tt.add("§7" + I18n.format(container.isSyncPlayerRegistered()
            ? prefix + ".click_unregister"
            : prefix + ".click_register") + "§r");
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

        String prefix = "gui.ae2powertools.storage_emitter.";

        if (entry.hasResource()) {
            String symbol = entry.getComparison().getSymbol();

            tooltip.add(TextFormatting.GRAY + I18n.format(
                prefix + "current_quantity", formatWithCommas(entry.getLastQuantity())));


            if (container.isSyncHysteresisEnabled()) {
                tooltip.add(TextFormatting.GRAY + I18n.format(
                    prefix + "active_target", symbol,
                    formatWithCommas(entry.getActiveThreshold(true)), formatTargetProgress(entry)));
                tooltip.add(TextFormatting.GRAY + I18n.format(
                    prefix + "increasing_target", symbol, formatWithCommas(entry.getThreshold())));
                tooltip.add(TextFormatting.GRAY + I18n.format(
                    prefix + "decreasing_target", symbol, formatWithCommas(entry.getLowerThreshold())));
            } else {
                tooltip.add(TextFormatting.GRAY + I18n.format(
                    prefix + "current_target", symbol,
                    formatWithCommas(entry.getThreshold()), formatTargetProgress(entry)));
            }
        } else {
            tooltip.add(TextFormatting.GRAY + I18n.format(prefix + "empty_slot"));
        }

        tooltip.add("");
        tooltip.add(TextFormatting.AQUA + I18n.format(prefix + "controls.title"));
        tooltip.add(TextFormatting.GRAY + I18n.format(prefix + "controls.scroll"));
        tooltip.add(TextFormatting.GRAY + I18n.format(prefix + "controls.toggle"));

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
        long threshold = Math.max(0, entry.getActiveThreshold(container.isSyncHysteresisEnabled()));

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

            // Skip content when selector open. See Maintainer GUI for rationale (GL leaks).
            // We still draw the icon background, as it isn't fully covered by the selector modal.
            if (selectorOpen) {
                int color = (entry != null && entry.hasResource()) ? 0x20000000 : 0x40000000;
                drawRect(x, y, x + ICON_SIZE, y + ICON_SIZE, color);
                continue;
            }

            // Hover highlight per zone. Only suppressed when the modal selector is open.
            drawZoneHover(x, y, mouseX, mouseY, entry);

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
        CellZone zone = pickZone(
            x,
            y,
            mouseX,
            mouseY,
            container.getHost().supportsEntryComparison(),
            container.isSyncHysteresisEnabled());
        if (zone == CellZone.NONE) return;

        int hl = 0x40FFFFFF;
        switch (zone) {
            case COMPARATOR:
                drawRect(x + CMP_X, y + CMP_Y, x + CMP_X + CMP_SIZE, y + CMP_Y + CMP_SIZE, hl);
                break;
            case SELECTOR:
                drawRect(x, y, x + LEFT_RIGHT_SPLIT, y + INNER_H, hl);
                break;
            case UPPER_THRESHOLD:
                drawRect(
                    x + LEFT_RIGHT_SPLIT,
                    y,
                    x + INNER_W,
                    y + (container.isSyncHysteresisEnabled() ? INNER_H / 2 : INNER_H),
                    hl);
                break;
            case LOWER_THRESHOLD:
                drawRect(x + LEFT_RIGHT_SPLIT, y + INNER_H / 2, x + INNER_W, y + INNER_H, hl);
                break;
            default:
                break;
        }
    }

    /**
     * Returns the zone the given screen-space mouse coordinate is in for a cell at (x,y).
     * Comparator takes priority over left/right because it visually overlaps both halves.
     */
    private static CellZone pickZone(int x, int y, int mouseX, int mouseY,
            boolean comparisonEnabled, boolean hysteresisEnabled) {
        if (mouseX < x || mouseX >= x + INNER_W || mouseY < y || mouseY >= y + INNER_H) return CellZone.NONE;

        int localX = mouseX - x;
        int localY = mouseY - y;

        if (comparisonEnabled
                && localX >= CMP_X && localX < CMP_X + CMP_SIZE
                && localY >= CMP_Y && localY < CMP_Y + CMP_SIZE) return CellZone.COMPARATOR;

        if (localX < LEFT_RIGHT_SPLIT) return CellZone.SELECTOR;
        if (!hysteresisEnabled) return CellZone.UPPER_THRESHOLD;

        return localY < INNER_H / 2 ? CellZone.UPPER_THRESHOLD : CellZone.LOWER_THRESHOLD;
    }

    private void drawEntryContent(int x, int y, MonitoredEntry entry) {
        // Comparator + thresholds are drawn for ALL entries (including resource-less placeholders)
        // because the user can pre-configure those before picking a resource for the slot.
        if (entry != null) {
            drawComparison(x + CMP_X, y + CMP_Y, entry.getComparison());
            drawEntryNumbers(x + NUM_X, y + NUM_Y, entry);
        }

        // Icon zone shows either the resource or a clickable "+" placeholder.
        drawEntryIcon(x + ICON_X, y + ICON_Y, entry);

        if (entry != null && entry.hasResource()) {
            drawCurrentQuantity(x + ICON_X, y + ICON_Y, entry);
        }
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
        } else {
            // Keep a dark plate behind every icon so translucent resources and overlay text
            // stay readable regardless of the slot tint below them.
            drawRect(x, y, x + ICON_SIZE, y + ICON_SIZE, 0x20000000);
        }

        GlStateManager.enableTexture2D();
        // Delegate to the unified resource renderer; it handles items, fluids, gas, and essentia
        // with proper GL state management.
        MonitoredResourceRenderer.renderIcon(entry.getResource(), x, y, ICON_SIZE);
    }

    private void drawCurrentQuantity(int x, int y, MonitoredEntry entry) {
        String currentStr = ReadableNumberConverter.INSTANCE.toSlimReadableForm(entry.getLastQuantity());
        int color = entry.isEnabled() ? 0xFFFFFFFF : 0xFF808080;

        int textW = fontRenderer.getStringWidth(currentStr);
        int scaledIconSize = ICON_SIZE * 2;
        int textX = scaledIconSize - textW;
        int textY = scaledIconSize - fontRenderer.FONT_HEIGHT + 1;

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        GlStateManager.scale(0.5F, 0.5F, 1.0F);
        fontRenderer.drawStringWithShadow(currentStr, textX, textY, color);
        GlStateManager.popMatrix();
    }

    private void drawEntryNumbers(int x, int y, MonitoredEntry entry) {
        boolean hysteresisEnabled = container.isSyncHysteresisEnabled();
        String upperStr = ReadableNumberConverter.INSTANCE.toSlimReadableForm(entry.getThreshold());
        String lowerStr = ReadableNumberConverter.INSTANCE.toSlimReadableForm(entry.getLowerThreshold());
        int activeColor = entry.isEnabled() ? 0xFFFFFFFF : 0xFF808080;
        int inactiveColor = entry.isEnabled() ? 0xFFC0C0C0 : 0xFF707070;

        // Draw half-scale so we are not cramming 2x 8px height numbers into the 22px available
        // TODO: should we skip the 0.5x scale if we have hysteresis disabled and are only drawing one number?
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        GlStateManager.scale(0.5F, 0.5F, 1.0F);

        int scaledBoxW = NUM_SIZE * 2;
        if (!hysteresisEnabled) {
            int thresholdW = fontRenderer.getStringWidth(upperStr);
            int textY = 12;
            fontRenderer.drawStringWithShadow(upperStr, (scaledBoxW - thresholdW) / 2f, textY, activeColor);
            GlStateManager.popMatrix();
            return;
        }

        boolean upperActive = entry.usesUpperThreshold(true);
        int upperW = fontRenderer.getStringWidth(upperStr);
        int lowerW = fontRenderer.getStringWidth(lowerStr);

        // TODO: draw a line between the two numbers to separate them

        fontRenderer.drawStringWithShadow(
            upperStr,
            (scaledBoxW - upperW) / 2f,
            4,
            upperActive ? activeColor : inactiveColor);
        fontRenderer.drawStringWithShadow(
            lowerStr,
            (scaledBoxW - lowerW) / 2f,
            18,
            upperActive ? inactiveColor : activeColor);

        GlStateManager.popMatrix();
    }

    private void drawCountField() {
        if (countField == null || !countField.getVisible()) return;

        // Solid black background so the field is visually distinct over the cells.
        drawRect(countField.x - 1, countField.y - 1,
            countField.x + COUNT_FIELD_W + 1, countField.y + COUNT_FIELD_H + 1,
            0xFF000000);

        // GuiTextField doesn't support centering natively, so we manually draw the text on top.
        String label = getCountFieldLabel();
        String txt = countField.getText();
        int textW = fontRenderer.getStringWidth(txt);
        int textY = countField.y + (COUNT_FIELD_H - 8) / 2 + 1;
        fontRenderer.drawString(label, countField.x + 4, textY, 0xFFAAAAAA);

        int labelRight = countField.x + fontRenderer.getStringWidth(label) + 8;
        int textX = Math.max(labelRight, countField.x + (COUNT_FIELD_W - textW) / 2);
        fontRenderer.drawString(txt, textX, textY, 0xFFFFFFFF);

        // Blinking cursor approximation, positioned just after the text up to the cursor index.
        if (countField.isFocused() && (System.currentTimeMillis() / 500) % 2 == 0) {
            int cursorPos = countField.getCursorPosition();
            String beforeCursor = txt.substring(0, Math.min(cursorPos, txt.length()));
            int caretX = textX + fontRenderer.getStringWidth(beforeCursor);
            drawRect(caretX, textY - 1, caretX + 1, textY + 9, 0xFFFFFFFF);
        }
    }

    private String getCountFieldLabel() {
        String prefix = "gui.ae2powertools.storage_emitter.";
        if (!container.isSyncHysteresisEnabled()) {
            return I18n.format(prefix + "target_label");
        }

        return I18n.format(countFieldTarget == ThresholdField.UPPER
            ? prefix + "increasing_label" : prefix + "decreasing_label");
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
        }

        // Side button: match-mode toggle (lives outside guiLeft).
        if (matchBtnHovered && mouseButton == 0) {
            cycleMatchMode();
            return;
        }

        if (hysteresisBtnHovered && mouseButton == 0) {
            toggleHysteresisMode();
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

        handleEntryClick(idx, localX, localY, mouseButton);
    }

    private void handleEntryClick(int idx, int localX, int localY, int mouseButton) {
        List<MonitoredEntry> entries = container.getHost().getEntries();
        if (idx < 0 || idx >= entries.size()) return;

        MonitoredEntry entry = entries.get(idx);

        if (mouseButton == 1) {
            sendEntryUpdate(idx, entry.getComparison(), entry.getThreshold(), entry.getLowerThreshold(), !entry.isEnabled());
            return;
        }

        if (mouseButton != 0) return;

        // Use the same zone picker as the hover highlight so click and visual feedback agree.
        // pickZone expects screen-space coords; we pass (0, 0) as the cell origin so localX/localY
        // are evaluated against the inner cell rect.
        CellZone zone = pickZone(
            0,
            0,
            localX,
            localY,
            container.getHost().supportsEntryComparison(),
            container.isSyncHysteresisEnabled());
        if (zone == CellZone.NONE) return;

        switch (zone) {
            case COMPARATOR:
                cycleComparison(idx);
                return;

            case SELECTOR:
                openSelector(idx);
                return;

            case UPPER_THRESHOLD:
                showCountField(idx, ThresholdField.UPPER);
                return;

            case LOWER_THRESHOLD:
                showCountField(idx, ThresholdField.LOWER);
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
        if (hit == null) return;
        if (hit.zone != CellZone.UPPER_THRESHOLD && hit.zone != CellZone.LOWER_THRESHOLD) return;

        adjustEntryThreshold(
            hit.index,
            hit.zone == CellZone.LOWER_THRESHOLD ? ThresholdField.LOWER : ThresholdField.UPPER,
            scroll > 0);
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

        CellZone zone = pickZone(
            0,
            0,
            localX,
            localY,
            container.getHost().supportsEntryComparison(),
            container.isSyncHysteresisEnabled());
        if (zone == CellZone.NONE) return null;

        return new GridHit(row * GRID_COLS + col, zone);
    }

    private void cycleMatchMode() {
        if (!container.supportsMatchMode()) return;

        MatchMode next = container.getSyncMatchMode().next();
        PowerToolsNetwork.INSTANCE.sendToServer(
            new PacketSetMatchMode(container.getHost(), next));
    }

    private void toggleHysteresisMode() {
        PowerToolsNetwork.INSTANCE.sendToServer(
            new PacketSetHysteresisMode(container.getHost(), !container.isSyncHysteresisEnabled()));
    }

    private void adjustEmitterStrength(int delta) {
        if (!container.supportsEmitterRedstone()) return;

        int currentStrength = container.getSyncEmitterStrength();
        int nextStrength = Math.max(
            IEmitterRedstoneHost.MIN_REDSTONE_STRENGTH,
            Math.min(IEmitterRedstoneHost.MAX_REDSTONE_STRENGTH, currentStrength + delta));
        if (nextStrength == currentStrength) return;

        PowerToolsNetwork.INSTANCE.sendToServer(
            new PacketSetEmitterRedstoneStrength(container.getHost(), nextStrength));
    }

    private void syncEmitterRedstoneButton() {
        if (redstoneSignalBtn == null) return;

        // Reuse AE2's redstone-emitter button art while keeping this feature's
        // semantics local to AE2 Power Tools.
        redstoneSignalBtn.set(container.getSyncEmitterRedstonePower() == EmitterRedstonePower.STRONG
            ? RedstoneMode.HIGH_SIGNAL
            : RedstoneMode.LOW_SIGNAL);
    }

    private void syncAlarmRegistrationButton() {
        if (alarmRegistrationBtn == null) return;

        alarmRegistrationBtn.displayString = container.isSyncPlayerRegistered() ? "-" : "+";
    }

    private boolean isRedstoneSignalButtonHovered(int mouseX, int mouseY) {
        return redstoneSignalBtn != null
            && redstoneSignalBtn.visible
            && mouseX >= redstoneSignalBtn.x
            && mouseX < redstoneSignalBtn.x + redstoneSignalBtn.width
            && mouseY >= redstoneSignalBtn.y
            && mouseY < redstoneSignalBtn.y + redstoneSignalBtn.height;
    }

    private boolean isAlarmRegistrationButtonHovered(int mouseX, int mouseY) {
        return alarmRegistrationBtn != null
            && alarmRegistrationBtn.visible
            && mouseX >= alarmRegistrationBtn.x
            && mouseX < alarmRegistrationBtn.x + alarmRegistrationBtn.width
            && mouseY >= alarmRegistrationBtn.y
            && mouseY < alarmRegistrationBtn.y + alarmRegistrationBtn.height;
    }

    private void addEmitterStrengthButtons() {
        int btnY = emitterStrengthPanelY + STRENGTH_PANEL_PADDING + STRENGTH_BTN_Y_START;
        int btnId = 300;

        for (int y = 1; y < 4; y++) {  // 1, 2, 3
            int btnX = emitterStrengthPanelX + STRENGTH_PANEL_PADDING;

            for (int x = -1; x < 2; x += 2) {  // -1, 1
                int delta = x * y * 5;
                CompactVanillaButton button = new CompactVanillaButton(btnId, btnX, btnY, delta);
                emitterStrengthButtons.add(button);
                this.buttonList.add(button);

                btnId++;
                btnX += STRENGTH_BTN_X_OFFSET;
            }

            btnY += STRENGTH_BTN_Y_OFFSET;
        }
    }

    private void drawEmitterStrengthPanel(int mouseX, int mouseY) {
        mc.getTextureManager().bindTexture(REDSTONE_STRENGTH_PANEL);
        drawTexturedModalRect(emitterStrengthPanelX, emitterStrengthPanelY, 0, 0, STRENGTH_PANEL_WIDTH, STRENGTH_PANEL_HEIGHT);

        String title = I18n.format("gui.ae2powertools.storage_emitter.strength");
        int titleX = emitterStrengthPanelX + (STRENGTH_PANEL_WIDTH - fontRenderer.getStringWidth(title)) / 2;
        int titleY = emitterStrengthPanelY + STRENGTH_PANEL_PADDING + 2;
        fontRenderer.drawString(title, titleX, titleY, 0xFF000000);

        // We want the value to be at centered on the middle button row
        String value = Integer.toString(container.getSyncEmitterStrength());
        int width = fontRenderer.getStringWidth(value);
        int valueX = emitterStrengthPanelX + (STRENGTH_PANEL_WIDTH - width) / 2;
        int valueY = emitterStrengthPanelY + STRENGTH_PANEL_PADDING + STRENGTH_BTN_Y_START
                   + STRENGTH_BTN_Y_OFFSET + (STRENGTH_BTN_HEIGHT - fontRenderer.FONT_HEIGHT) / 2 + 1;
        fontRenderer.drawString(value, valueX, valueY, 0xFF000000);

        boolean emitterStrengthTitleHovered = mouseX >= titleX && mouseX < titleX + fontRenderer.getStringWidth(title)
            && mouseY >= titleY && mouseY < titleY + fontRenderer.FONT_HEIGHT;
        boolean emitterStrengthValueHovered = mouseX >= valueX && mouseX < valueX + width
            && mouseY >= valueY && mouseY < valueY + fontRenderer.FONT_HEIGHT;
        emitterStrengthTextHovered = emitterStrengthTitleHovered || emitterStrengthValueHovered;
    }

    private void cycleComparison(int idx) {
        List<MonitoredEntry> entries = container.getHost().getEntries();
        if (idx < 0 || idx >= entries.size()) return;

        MonitoredEntry e = entries.get(idx);
        ComparisonMode next = e.getComparison().next();
        sendEntryUpdate(idx, next, e.getThreshold(), e.getLowerThreshold(), e.isEnabled());
    }

    private void adjustEntryThreshold(int idx, ThresholdField target, boolean doubleTarget) {
        List<MonitoredEntry> entries = container.getHost().getEntries();
        if (idx < 0 || idx >= entries.size()) return;

        MonitoredEntry entry = entries.get(idx);
        long oldThreshold = getThresholdValue(entry, target);
        long newThreshold = doubleTarget ? doubleThreshold(oldThreshold) : oldThreshold / 2;
        if (newThreshold == oldThreshold) return;

        setThresholdValue(entry, target, newThreshold);

        if (countField != null && countField.getVisible() && countFieldEntryIndex == idx && countFieldTarget == target) {
            countField.setText(formatWithCommas(newThreshold));
            countField.setCursorPositionEnd();
        }

        sendEntryUpdate(idx, entry.getComparison(), entry.getThreshold(), entry.getLowerThreshold(), entry.isEnabled());
    }

    private long getThresholdValue(MonitoredEntry entry, ThresholdField target) {
        return target == ThresholdField.UPPER ? entry.getThreshold() : entry.getLowerThreshold();
    }

    private void setThresholdValue(MonitoredEntry entry, ThresholdField target, long value) {
        if (target == ThresholdField.UPPER) {
            entry.setThreshold(value);
            return;
        }

        entry.setLowerThreshold(value);
    }

    private long doubleThreshold(long threshold) {
        if (threshold <= 0) return 1;
        if (threshold > Long.MAX_VALUE / 2) return Long.MAX_VALUE;

        return threshold * 2;
    }

    private void sendEntryUpdate(int idx, ComparisonMode comparison, long threshold, long lowerThreshold, boolean enabled) {
        PowerToolsNetwork.INSTANCE.sendToServer(
            new PacketUpdateMonitorEntry(container.getHost(), idx, comparison, threshold, lowerThreshold, enabled));
    }

    private void showCountField(int idx, ThresholdField target) {
        List<MonitoredEntry> entries = container.getHost().getEntries();
        if (idx < 0 || idx >= entries.size()) return;

        countFieldEntryIndex = idx;
        countFieldTarget = target;
        countField.setVisible(true);
        countField.setFocused(true);
        countField.setText(formatWithCommas(getThresholdValue(entries.get(idx), target)));
        countField.setCursorPositionEnd();

        Keyboard.enableRepeatEvents(true);
    }

    private void hideCountField(boolean save) {
        if (countField == null || !countField.getVisible()) return;

        if (save && countFieldEntryIndex >= 0) sendCountFieldUpdate();

        countField.setVisible(false);
        countField.setFocused(false);
        countFieldEntryIndex = -1;
        countFieldTarget = ThresholdField.UPPER;

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
        if (countFieldTarget == ThresholdField.UPPER) {
            e.setThreshold(value);
        } else {
            e.setLowerThreshold(value);
        }

        sendEntryUpdate(countFieldEntryIndex, e.getComparison(), e.getThreshold(), e.getLowerThreshold(), e.isEnabled());
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
     * The side buttons sit OUTSIDE guiLeft to the left, so JEI thinks that area is
     * free space and happily covers it with its filter UI; we have to declare them
     * explicitly. The polling-rate wrench tab button is inside the GUI bounds and
     * doesn't need to be listed.
     */
    public List<Rectangle> getJEIExclusionArea() {
        List<Rectangle> areas = new ArrayList<>();
        areas.add(new Rectangle(matchBtnX, matchBtnY, SIDE_BTN_SIZE, SIDE_BTN_SIZE));
        if (container.getHost().supportsHysteresis()) {
            areas.add(new Rectangle(hysteresisBtnX, hysteresisBtnY, SIDE_BTN_SIZE, SIDE_BTN_SIZE));
        }
        if (redstoneSignalBtn != null) {
            areas.add(new Rectangle(redstoneSignalBtn.x, redstoneSignalBtn.y,
                                    redstoneSignalBtn.width, redstoneSignalBtn.height));
        }
        if (container.supportsEmitterRedstone()) {
            areas.add(new Rectangle(emitterStrengthPanelX, emitterStrengthPanelY,
                                    STRENGTH_PANEL_WIDTH, STRENGTH_PANEL_HEIGHT));
        }
        return areas;
    }

    private static class CompactVanillaButton extends GuiButton {

        private final int delta;

        private CompactVanillaButton(int id, int x, int y, int delta) {
            super(id, x, y, STRENGTH_BTN_WIDTH, STRENGTH_BTN_HEIGHT, formatDelta(delta));
            this.delta = delta;
        }

        private int getDelta() {
            return delta;
        }

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
            if (!this.visible) return;

            this.hovered = mouseX >= this.x && mouseY >= this.y
                && mouseX < this.x + this.width && mouseY < this.y + this.height;

            VanillaButtonRenderer.drawBeveledButton(
                mc.fontRenderer,
                this.x,
                this.y,
                this.width,
                this.height,
                this.displayString,
                this.enabled,
                this.hovered);

            this.mouseDragged(mc, mouseX, mouseY);
        }

        private static String formatDelta(int delta) {
            return delta > 0 ? "+" + delta : Integer.toString(delta);
        }
    }
}
