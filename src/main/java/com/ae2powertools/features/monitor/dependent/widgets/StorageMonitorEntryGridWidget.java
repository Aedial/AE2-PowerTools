package com.ae2powertools.features.monitor.dependent.widgets;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import org.lwjgl.input.Keyboard;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.client.config.GuiUtils;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.util.ReadableNumberConverter;

import com.ae2powertools.features.monitor.MonitoredEntry;
import com.ae2powertools.features.monitor.MonitoredResource;
import com.ae2powertools.features.monitor.client.MonitoredResourceRenderer;
import com.ae2powertools.features.monitor.dependent.ComparisonMode;
import com.ae2powertools.features.monitor.dependent.ContainerStorageMonitor;
import com.ae2powertools.integration.jei.JeiTooltipBridge;
import com.ae2powertools.widgets.FormattedNumberFieldHelper;
import com.ae2powertools.widgets.WidgetContext;


/**
 * Feature-local grid widget for the storage monitor's entry surface.
 */
@SideOnly(Side.CLIENT)
public class StorageMonitorEntryGridWidget extends Gui {

    public interface SelectorOpener {

        void openSelector(int targetIndex);
    }

    public interface EntryUpdateSender {

        void send(int entryIndex, ComparisonMode comparison, long threshold, long lowerThreshold, boolean enabled);
    }

    private enum CellZone { NONE, COMPARATOR, SELECTOR, UPPER_THRESHOLD, LOWER_THRESHOLD }

    private enum ThresholdField { UPPER, LOWER }

    private static final class GridHit {

        private final int index;
        private final CellZone zone;

        private GridHit(int index, CellZone zone) {
            this.index = index;
            this.zone = zone;
        }
    }

    private static final ResourceLocation CMP_TEXTURE = new ResourceLocation(
        "ae2powertools", "textures/guis/comparison_arrows.png");

    private static final int GRID_COLS = 4;
    private static final int GRID_ROWS = 6;
    private static final int GRID_CAPACITY = GRID_COLS * GRID_ROWS;
    private static final int INNER_W = 50;
    private static final int INNER_H = 22;
    private static final int CELL_W = INNER_W + 1;
    private static final int CELL_H = INNER_H + 1;
    private static final int GRID_X = 9;
    private static final int GRID_Y = 19;

    private static final int ICON_X = 3;
    private static final int ICON_Y = 3;
    private static final int ICON_SIZE = 16;
    private static final int CMP_X = 20;
    private static final int CMP_Y = 6;
    private static final int CMP_SIZE = 10;
    private static final int NUM_X = 31;
    private static final int NUM_Y = 3;
    private static final int NUM_SIZE = 16;
    private static final int LEFT_RIGHT_SPLIT = 25;

    private static final int CMP_SHEET_SIZE = 64;
    private static final int CMP_TILE_SIZE = 20;

    private static final int COUNT_FIELD_X = 8;
    private static final int COUNT_FIELD_Y = 163;
    private static final int COUNT_FIELD_W = 203;
    private static final int COUNT_FIELD_H = 10;

    private final WidgetContext context;
    private final ContainerStorageMonitor container;
    private final SelectorOpener selectorOpener;
    private final EntryUpdateSender entryUpdateSender;

    private GuiTextField countField;
    private int countFieldEntryIndex = -1;
    private ThresholdField countFieldTarget = ThresholdField.UPPER;

    public StorageMonitorEntryGridWidget(WidgetContext context, ContainerStorageMonitor container,
            SelectorOpener selectorOpener, EntryUpdateSender entryUpdateSender) {
        this.context = context;
        this.container = container;
        this.selectorOpener = selectorOpener;
        this.entryUpdateSender = entryUpdateSender;
    }

    public void initGui(int guiLeft, int guiTop) {
        countField = new GuiTextField(
            50,
            context.getWidgetFontRenderer(),
            guiLeft + COUNT_FIELD_X,
            guiTop + COUNT_FIELD_Y,
            COUNT_FIELD_W,
            COUNT_FIELD_H);
        countField.setMaxStringLength(20);
        countField.setEnableBackgroundDrawing(false);
        countField.setTextColor(0xFFFFFF);
        countField.setVisible(false);
        countField.setFocused(false);
    }

    public void updateScreen() {
        if (countField != null && countField.getVisible()) countField.updateCursorCounter();
    }

    public boolean isEditingCountField() {
        return countField != null && countField.getVisible();
    }

    public void draw(int guiLeft, int guiTop, boolean modalOpen, int mouseX, int mouseY) {
        // The parent GUI owns higher-level overlays such as the shared selector and upgrade picker.
        // This widget only renders the entry surface and the inline threshold field that belongs to it.
        drawEntries(guiLeft, guiTop, modalOpen, mouseX, mouseY);

        // Count field overlay (background tint + text). Drawn here so it sits above
        // the entry grid but below the modal selector.
        drawCountField();
    }

    public void drawTooltip(int guiLeft, int guiTop, int mouseX, int mouseY) {
        GridHit hit = getGridHit(guiLeft, guiTop, mouseX, mouseY);
        if (hit == null) return;

        List<MonitoredEntry> entries = container.getHost().getEntries();
        if (hit.index < 0 || hit.index >= entries.size()) return;

        List<String> tooltip = buildEntryTooltip(entries.get(hit.index));
        if (tooltip.isEmpty()) return;

        GuiUtils.drawHoveringText(
            tooltip,
            mouseX,
            mouseY,
            context.getWidgetWidth(),
            context.getWidgetHeight(),
            -1,
            context.getWidgetFontRenderer());
    }

    public boolean mouseClicked(int guiLeft, int guiTop, int mouseX, int mouseY, int mouseButton) {
        if (countField != null && countField.getVisible()) {
            int fieldX = countField.x;
            int fieldY = countField.y;
            boolean insideField = mouseX >= fieldX && mouseX < fieldX + COUNT_FIELD_W
                && mouseY >= fieldY && mouseY < fieldY + COUNT_FIELD_H;
            if (insideField) {
                countField.mouseClicked(mouseX, mouseY, mouseButton);
                return true;
            }

            hideCountField(true);
        }

        GridHit hit = getGridHit(guiLeft, guiTop, mouseX, mouseY);
        if (hit == null) return false;

        handleEntryClick(hit.index, hit.zone, mouseButton);
        return true;
    }

    public boolean keyTyped(char typedChar, int keyCode) {
        if (countField == null || !countField.getVisible() || !countField.isFocused()) return false;

        if (keyCode == Keyboard.KEY_ESCAPE) {
            hideCountField(false);
            return true;
        }

        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            hideCountField(true);
            return true;
        }

        if (Character.isDigit(typedChar) || isEditingKey(keyCode)) {
            if (countField.textboxKeyTyped(typedChar, keyCode)) {
                // Live update: refresh the active threshold as the user types.
                sendCountFieldUpdate();
                return true;
            }
        }

        return true;
    }

    public boolean handleMouseWheel(int guiLeft, int guiTop, int mouseX, int mouseY, int wheelDelta, boolean shiftDown) {
        if (!shiftDown || wheelDelta == 0) return false;

        GridHit hit = getGridHit(guiLeft, guiTop, mouseX, mouseY);
        if (hit == null) return false;

        List<MonitoredEntry> entries = container.getHost().getEntries();
        if (hit.index < 0 || hit.index >= entries.size()) return false;

        MonitoredEntry entry = entries.get(hit.index);
        if (!entry.hasResource()) return false;

        adjustEntryThreshold(
            hit.index,
            getScrollTargetField(guiLeft, guiTop, mouseX, mouseY),
            wheelDelta > 0);
        return true;
    }

    private List<String> buildEntryTooltip(MonitoredEntry entry) {
        List<String> tooltip = buildResourceTooltip(entry);
        if (!tooltip.isEmpty()) tooltip.add("");

        String prefix = "gui.ae2powertools.storage_emitter.";
        if (entry.hasResource()) {
            String symbol = entry.getComparison().getSymbol();
            tooltip.add(TextFormatting.GRAY + I18n.format(
                prefix + "current_quantity", FormattedNumberFieldHelper.formatWithCommas(entry.getLastQuantity())));

            if (container.isSyncHysteresisEnabled()) {
                tooltip.add(TextFormatting.GRAY + I18n.format(
                    prefix + "active_target",
                    symbol,
                    FormattedNumberFieldHelper.formatWithCommas(entry.getActiveThreshold(true)),
                    formatTargetProgress(entry)));
                tooltip.add(TextFormatting.GRAY + I18n.format(
                    prefix + "increasing_target",
                    symbol,
                    FormattedNumberFieldHelper.formatWithCommas(entry.getThreshold())));
                tooltip.add(TextFormatting.GRAY + I18n.format(
                    prefix + "decreasing_target",
                    symbol,
                    FormattedNumberFieldHelper.formatWithCommas(entry.getLowerThreshold())));
            } else {
                tooltip.add(TextFormatting.GRAY + I18n.format(
                    prefix + "current_target",
                    symbol,
                    FormattedNumberFieldHelper.formatWithCommas(entry.getThreshold()),
                    formatTargetProgress(entry)));
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
                    context.getWidgetMinecraft().player,
                    context.getWidgetMinecraft().gameSettings.advancedItemTooltips
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
        if (threshold == 0) return 0;
        return calculatePercent(threshold, quantity);
    }

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

    private void drawEntries(int guiLeft, int guiTop, boolean modalOpen, int mouseX, int mouseY) {
        List<MonitoredEntry> entries = container.getHost().getEntries();
        for (int index = 0; index < GRID_CAPACITY; index++) {
            int col = index % GRID_COLS;
            int row = index / GRID_COLS;
            int x = guiLeft + GRID_X + col * CELL_W;
            int y = guiTop + GRID_Y + row * CELL_H;

            MonitoredEntry entry = index < entries.size() ? entries.get(index) : null;
            drawEntryBackground(x, y, entry);
            if (!modalOpen) drawZoneHover(x, y, mouseX, mouseY);
            drawEntryContent(x, y, entry, modalOpen);
        }
    }

    private void drawEntryBackground(int x, int y, MonitoredEntry entry) {
        if (entry == null) return;
        if (!entry.hasResource()) {
            drawRect(x, y, x + INNER_W, y + INNER_H, 0x30606060);
            return;
        }

        int color;
        if (!entry.isEnabled()) {
            color = 0x60808080;
        } else if (entry.isLastConditionMet()) {
            color = 0x6044BB44;
        } else {
            color = 0x60BB4444;
        }

        drawRect(x, y, x + INNER_W, y + INNER_H, color);
    }

    private void drawZoneHover(int x, int y, int mouseX, int mouseY) {
        CellZone zone = pickZone(
            x,
            y,
            mouseX,
            mouseY,
            container.getHost().supportsEntryComparison(),
            container.isSyncHysteresisEnabled());
        if (zone == CellZone.NONE) return;

        int highlight = 0x40FFFFFF;
        switch (zone) {
            case COMPARATOR:
                drawRect(x + CMP_X, y + CMP_Y, x + CMP_X + CMP_SIZE, y + CMP_Y + CMP_SIZE, highlight);
                return;

            case SELECTOR:
                drawRect(x, y, x + LEFT_RIGHT_SPLIT, y + INNER_H, highlight);
                return;

            case UPPER_THRESHOLD:
                drawRect(
                    x + LEFT_RIGHT_SPLIT,
                    y,
                    x + INNER_W,
                    y + (container.isSyncHysteresisEnabled() ? INNER_H / 2 : INNER_H),
                    highlight);
                return;

            case LOWER_THRESHOLD:
                drawRect(x + LEFT_RIGHT_SPLIT, y + INNER_H / 2, x + INNER_W, y + INNER_H, highlight);
                return;

            default:
                return;
        }
    }

    private static CellZone pickZone(int x, int y, int mouseX, int mouseY,
            boolean comparisonEnabled, boolean hysteresisEnabled) {
        if (mouseX < x || mouseX >= x + INNER_W || mouseY < y || mouseY >= y + INNER_H) return CellZone.NONE;

        int localX = mouseX - x;
        int localY = mouseY - y;
        if (comparisonEnabled
                && localX >= CMP_X && localX < CMP_X + CMP_SIZE
                && localY >= CMP_Y && localY < CMP_Y + CMP_SIZE) {
            return CellZone.COMPARATOR;
        }

        if (localX < LEFT_RIGHT_SPLIT) return CellZone.SELECTOR;
        if (!hysteresisEnabled) return CellZone.UPPER_THRESHOLD;
        return localY < INNER_H / 2 ? CellZone.UPPER_THRESHOLD : CellZone.LOWER_THRESHOLD;
    }

    private void drawEntryContent(int x, int y, MonitoredEntry entry, boolean modalOpen) {
        if (entry != null) {
            drawComparison(x + CMP_X, y + CMP_Y, entry.getComparison());
            drawEntryNumbers(x + NUM_X, y + NUM_Y, entry);
        }

        drawEntryIcon(x + ICON_X, y + ICON_Y, entry, modalOpen);
        if (entry != null && entry.hasResource() && !modalOpen) {
            drawCurrentQuantity(x + ICON_X, y + ICON_Y, entry);
        }
    }

    private void drawComparison(int x, int y, ComparisonMode mode) {
        // 64x64 sheet, 2x2 grid of 20x20 arrow tiles.
        // Spec: x-axis = above/below threshold, y-axis = and-equal / strictly.
        int u = 0;
        int v = 0;
        switch (mode) {
            case LESS_EQUAL:
                u = CMP_TILE_SIZE;
                break;

            case GREATER:
                v = CMP_TILE_SIZE;
                break;

            case LESS:
                u = CMP_TILE_SIZE;
                v = CMP_TILE_SIZE;
                break;

            default:
                break;
        }

        // Restore GL state in case a previous pass left lighting/blend dirty.
        GlStateManager.enableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
            GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
            GlStateManager.SourceFactor.ONE,
            GlStateManager.DestFactor.ZERO);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        context.getWidgetMinecraft().getTextureManager().bindTexture(CMP_TEXTURE);

        Gui.drawScaledCustomSizeModalRect(
            x,
            y,
            (float) u,
            (float) v,
            CMP_TILE_SIZE,
            CMP_TILE_SIZE,
            CMP_SIZE,
            CMP_SIZE,
            CMP_SHEET_SIZE,
            CMP_SHEET_SIZE);
    }

    private void drawEntryIcon(int x, int y, MonitoredEntry entry, boolean modalOpen) {
        // Empty and resource-less slots intentionally share the same "+" affordance so the click target
        // stays obvious even before the server has synced a concrete resource into the slot.
        if (entry == null || !entry.hasResource()) {
            drawRect(x, y, x + ICON_SIZE, y + ICON_SIZE, 0x40000000);
            GlStateManager.enableTexture2D();
            String plus = "+";
            int width = context.getWidgetFontRenderer().getStringWidth(plus);
            context.getWidgetFontRenderer().drawString(plus, x + (ICON_SIZE - width) / 2, y + (ICON_SIZE - 8) / 2, 0xFFAAAAAA);
            return;
        }

        // Keep a dark plate behind every icon so translucent resources and overlay text
        // stay readable regardless of the slot tint below them.
        drawRect(x, y, x + ICON_SIZE, y + ICON_SIZE, 0x20000000);

        // See the maintainer GUI for the rationale: rendering through the modal invites GL leaks.
        if (modalOpen) return;

        GlStateManager.enableTexture2D();

        // Delegate to the unified resource renderer; it handles items, fluids, gas, and essentia
        // with proper GL state management.
        MonitoredResourceRenderer.renderIcon(entry.getResource(), x, y, ICON_SIZE);
    }

    private void drawCurrentQuantity(int x, int y, MonitoredEntry entry) {
        String current = ReadableNumberConverter.INSTANCE.toSlimReadableForm(entry.getLastQuantity());
        int color = entry.isEnabled() ? 0xFFFFFFFF : 0xFF808080;

        int textWidth = context.getWidgetFontRenderer().getStringWidth(current);
        int scaledIconSize = ICON_SIZE * 2;
        int textX = scaledIconSize - textWidth;
        int textY = scaledIconSize - context.getWidgetFontRenderer().FONT_HEIGHT + 1;

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        GlStateManager.scale(0.5F, 0.5F, 1.0F);
        context.getWidgetFontRenderer().drawStringWithShadow(current, textX, textY, color);
        GlStateManager.popMatrix();
    }

    private void drawEntryNumbers(int x, int y, MonitoredEntry entry) {
        boolean hysteresisEnabled = container.isSyncHysteresisEnabled();
        String upper = ReadableNumberConverter.INSTANCE.toSlimReadableForm(entry.getThreshold());
        String lower = ReadableNumberConverter.INSTANCE.toSlimReadableForm(entry.getLowerThreshold());
        int activeColor = entry.isEnabled() ? 0xFFFFFFFF : 0xFF808080;
        int inactiveColor = entry.isEnabled() ? 0xFFC0C0C0 : 0xFF707070;

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        GlStateManager.scale(0.5F, 0.5F, 1.0F);

        int scaledBoxWidth = NUM_SIZE * 2;
        if (!hysteresisEnabled) {
            int thresholdWidth = context.getWidgetFontRenderer().getStringWidth(upper);
            context.getWidgetFontRenderer().drawStringWithShadow(upper, (scaledBoxWidth - thresholdWidth) / 2f, 12, activeColor);
            GlStateManager.popMatrix();
            return;
        }

        boolean upperActive = entry.usesUpperThreshold(true);
        int upperWidth = context.getWidgetFontRenderer().getStringWidth(upper);
        int lowerWidth = context.getWidgetFontRenderer().getStringWidth(lower);

        context.getWidgetFontRenderer().drawStringWithShadow(
            upper,
            (scaledBoxWidth - upperWidth) / 2f,
            4,
            upperActive ? activeColor : inactiveColor);
        context.getWidgetFontRenderer().drawStringWithShadow(
            lower,
            (scaledBoxWidth - lowerWidth) / 2f,
            18,
            upperActive ? inactiveColor : activeColor);
        GlStateManager.popMatrix();
    }

    private void drawCountField() {
        if (countField == null || !countField.getVisible()) return;

        // The field is drawn manually over the grid so the active threshold editor reads like a temporary
        // inline pop-over instead of getting lost against the colored entry backgrounds.
        drawRect(
            countField.x - 1,
            countField.y - 1,
            countField.x + COUNT_FIELD_W + 1,
            countField.y + COUNT_FIELD_H + 1,
            0xFF000000);

        String label = getCountFieldLabel();
        String text = countField.getText();
        int textWidth = context.getWidgetFontRenderer().getStringWidth(text);
        int textY = countField.y + (COUNT_FIELD_H - 8) / 2 + 1;
        context.getWidgetFontRenderer().drawString(label, countField.x + 4, textY, 0xFFAAAAAA);

        int labelRight = countField.x + context.getWidgetFontRenderer().getStringWidth(label) + 8;
        int textX = Math.max(labelRight, countField.x + (COUNT_FIELD_W - textWidth) / 2);
        context.getWidgetFontRenderer().drawString(text, textX, textY, 0xFFFFFFFF);

        if (countField.isFocused() && (System.currentTimeMillis() / 500) % 2 == 0) {
            int cursorPos = countField.getCursorPosition();
            String beforeCursor = text.substring(0, Math.min(cursorPos, text.length()));
            int caretX = textX + context.getWidgetFontRenderer().getStringWidth(beforeCursor);
            drawRect(caretX, textY - 1, caretX + 1, textY + 9, 0xFFFFFFFF);
        }
    }

    private String getCountFieldLabel() {
        String prefix = "gui.ae2powertools.storage_emitter.";
        if (!container.isSyncHysteresisEnabled()) return I18n.format(prefix + "target_label");

        return I18n.format(countFieldTarget == ThresholdField.UPPER
            ? prefix + "increasing_label"
            : prefix + "decreasing_label");
    }

    private void handleEntryClick(int index, CellZone zone, int mouseButton) {
        List<MonitoredEntry> entries = container.getHost().getEntries();
        if (index < 0 || index >= entries.size()) return;

        MonitoredEntry entry = entries.get(index);
        if (mouseButton == 1) {
            entryUpdateSender.send(index, entry.getComparison(), entry.getThreshold(), entry.getLowerThreshold(), !entry.isEnabled());
            return;
        }

        if (mouseButton != 0) return;

        // Hover highlighting and click routing share the same zone hitscan so the visual
        // always matches the action that will fire when the user clicks.
        switch (zone) {
            case COMPARATOR:
                cycleComparison(index);
                return;

            case SELECTOR:
                selectorOpener.openSelector(index);
                return;

            case UPPER_THRESHOLD:
                showCountField(index, ThresholdField.UPPER);
                return;

            case LOWER_THRESHOLD:
                showCountField(index, ThresholdField.LOWER);
                return;

            default:
                return;
        }
    }

    private GridHit getGridHit(int guiLeft, int guiTop, int mouseX, int mouseY) {
        int relativeX = mouseX - guiLeft;
        int relativeY = mouseY - guiTop;
        if (relativeX < GRID_X || relativeY < GRID_Y) return null;

        int gridX = relativeX - GRID_X;
        int gridY = relativeY - GRID_Y;
        int col = gridX / CELL_W;
        int row = gridY / CELL_H;
        if (col >= GRID_COLS || row >= GRID_ROWS) return null;

        int localX = gridX - col * CELL_W;
        int localY = gridY - row * CELL_H;
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

    private ThresholdField getScrollTargetField(int guiLeft, int guiTop, int mouseX, int mouseY) {
        if (!container.isSyncHysteresisEnabled()) return ThresholdField.UPPER;

        int relativeX = mouseX - guiLeft;
        int relativeY = mouseY - guiTop;
        int gridX = relativeX - GRID_X;
        int gridY = relativeY - GRID_Y;
        if (gridX < 0 || gridY < 0) return ThresholdField.UPPER;

        int col = gridX / CELL_W;
        int row = gridY / CELL_H;
        if (col >= GRID_COLS || row >= GRID_ROWS) return ThresholdField.UPPER;

        int localY = gridY - row * CELL_H;
        if (localY < 0 || localY >= INNER_H) return ThresholdField.UPPER;

        return localY < INNER_H / 2 ? ThresholdField.UPPER : ThresholdField.LOWER;
    }

    private void cycleComparison(int index) {
        List<MonitoredEntry> entries = container.getHost().getEntries();
        if (index < 0 || index >= entries.size()) return;

        MonitoredEntry entry = entries.get(index);
        entryUpdateSender.send(index, entry.getComparison().next(), entry.getThreshold(), entry.getLowerThreshold(), entry.isEnabled());
    }

    private void adjustEntryThreshold(int index, ThresholdField target, boolean doubleTarget) {
        List<MonitoredEntry> entries = container.getHost().getEntries();
        if (index < 0 || index >= entries.size()) return;

        MonitoredEntry entry = entries.get(index);
        long oldThreshold = getThresholdValue(entry, target);
        long newThreshold = doubleTarget ? doubleThreshold(oldThreshold) : oldThreshold / 2;
        if (newThreshold == oldThreshold) return;

        setThresholdValue(entry, target, newThreshold);
        if (countField != null && countField.getVisible() && countFieldEntryIndex == index && countFieldTarget == target) {
            countField.setText(FormattedNumberFieldHelper.formatWithCommas(newThreshold));
            countField.setCursorPositionEnd();
        }

        entryUpdateSender.send(index, entry.getComparison(), entry.getThreshold(), entry.getLowerThreshold(), entry.isEnabled());
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

    private void showCountField(int index, ThresholdField target) {
        List<MonitoredEntry> entries = container.getHost().getEntries();
        if (countField == null || index < 0 || index >= entries.size()) return;

        countFieldEntryIndex = index;
        countFieldTarget = target;
        countField.setVisible(true);
        countField.setFocused(true);
        countField.setText(FormattedNumberFieldHelper.formatWithCommas(getThresholdValue(entries.get(index), target)));
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

        long value = FormattedNumberFieldHelper.parseDigits(countField.getText());
        MonitoredEntry entry = entries.get(countFieldEntryIndex);
        setThresholdValue(entry, countFieldTarget, value);
        entryUpdateSender.send(
            countFieldEntryIndex,
            entry.getComparison(),
            entry.getThreshold(),
            entry.getLowerThreshold(),
            entry.isEnabled());
    }

    private static boolean isEditingKey(int keyCode) {
        return keyCode == Keyboard.KEY_BACK
            || keyCode == Keyboard.KEY_DELETE
            || keyCode == Keyboard.KEY_LEFT
            || keyCode == Keyboard.KEY_RIGHT
            || keyCode == Keyboard.KEY_HOME
            || keyCode == Keyboard.KEY_END;
    }
}