package com.ae2powertools.util;

import java.util.Arrays;
import java.util.List;

import org.lwjgl.input.Keyboard;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;


/**
 * Shared controller for the standard increment/decrement button rows used by
 * the priority-style configuration screens.
 * <p>
 * The caller provides the absolute step values and a label formatter, and this helper
 * takes care of button creation, optional shift-based multiplication, label refreshes,
 * and clamped value adjustment.
 */
public class StepAdjustmentButtons {

    @FunctionalInterface
    public interface LabelFormatter {

        String formatLabel(int columnIndex, int multiplier, boolean positive);
    }

    private static final int START_X = 20;
    private static final int TOP_ROW_Y = 32;
    private static final int BOTTOM_ROW_Y = 69;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 6;
    private static final int BUTTON_SIDE_PADDING = 10;
    private static final int MIN_BUTTON_WIDTH = 22;

    private final int[] baseSteps;
    private final int shiftMultiplier;
    private final int[] columnXOffsets;
    private final LabelFormatter labelFormatter;
    private final GuiButton[] buttons;
    private final int[] columnWidths;

    public static StepAdjustmentButtons forNumeric(int... steps) {
        int[] copy = Arrays.copyOf(steps, steps.length);
        return new StepAdjustmentButtons(copy, 1, (columnIndex, multiplier, positive) -> {
            String sign = positive ? "+" : "-";
            return sign + copy[columnIndex] * multiplier;
        });
    }

    public StepAdjustmentButtons(int[] baseSteps, int shiftMultiplier, LabelFormatter labelFormatter) {
        this(baseSteps, shiftMultiplier, null, labelFormatter);
    }

    public StepAdjustmentButtons(int[] baseSteps, int shiftMultiplier, int[] columnXOffsets, LabelFormatter labelFormatter) {
        this.baseSteps = Arrays.copyOf(baseSteps, baseSteps.length);
        this.shiftMultiplier = Math.max(1, shiftMultiplier);
        this.columnXOffsets = columnXOffsets == null ? null : Arrays.copyOf(columnXOffsets, columnXOffsets.length);
        this.labelFormatter = labelFormatter;
        this.buttons = new GuiButton[this.baseSteps.length * 2];
        this.columnWidths = new int[this.baseSteps.length];

        for (int columnIndex = 0; columnIndex < this.baseSteps.length; columnIndex++) {
            this.columnWidths[columnIndex] = computeColumnWidth(columnIndex);
        }
    }

    public void addTo(List<GuiButton> buttonList, int guiLeft, int guiTop) {
        int x = guiLeft + START_X;

        for (int columnIndex = 0; columnIndex < baseSteps.length; columnIndex++) {
            int width = columnWidths[columnIndex];
            if (columnXOffsets != null && columnIndex < columnXOffsets.length) {
                x = guiLeft + columnXOffsets[columnIndex];
            }

            GuiButton positive = new GuiButton(
                columnIndex,
                x,
                guiTop + TOP_ROW_Y,
                width,
                BUTTON_HEIGHT,
                labelFormatter.formatLabel(columnIndex, 1, true));
            buttons[columnIndex] = positive;
            buttonList.add(positive);

            GuiButton negative = new GuiButton(
                columnIndex + baseSteps.length,
                x,
                guiTop + BOTTOM_ROW_Y,
                width,
                BUTTON_HEIGHT,
                labelFormatter.formatLabel(columnIndex, 1, false));
            buttons[columnIndex + baseSteps.length] = negative;
            buttonList.add(negative);

            if (columnXOffsets == null) x += width + BUTTON_GAP;
        }
    }

    public boolean manages(GuiButton button) {
        if (button == null) return false;
        if (button.id < 0 || button.id >= buttons.length) return false;

        return buttons[button.id] == button;
    }

    public int getDelta(GuiButton button) {
        if (!manages(button)) return 0;

        int columnIndex = button.id % baseSteps.length;
        int direction = button.id < baseSteps.length ? 1 : -1;
        return baseSteps[columnIndex] * getCurrentMultiplier() * direction;
    }

    public long getAdjustedValue(GuiButton button, long currentValue, long minimumValue, long maximumValue) {
        long result = currentValue + getDelta(button);
        return Math.max(minimumValue, Math.min(maximumValue, result));
    }

    public void updateLabels() {
        int multiplier = getCurrentMultiplier();

        for (int buttonIndex = 0; buttonIndex < buttons.length; buttonIndex++) {
            GuiButton button = buttons[buttonIndex];
            if (button == null) continue;

            int columnIndex = buttonIndex % baseSteps.length;
            boolean positive = buttonIndex < baseSteps.length;
            button.displayString = labelFormatter.formatLabel(columnIndex, multiplier, positive);
        }
    }

    public static void drawCenteredValue(FontRenderer fontRenderer, String value, int guiLeft, int guiWidth, int y, int color) {
        int textWidth = fontRenderer.getStringWidth(value);
        int x = guiLeft + (guiWidth - textWidth) / 2;
        fontRenderer.drawString(value, x, y, color);
    }

    private int computeColumnWidth(int columnIndex) {
        int width = MIN_BUTTON_WIDTH;
        width = Math.max(width, computeLabelWidth(labelFormatter.formatLabel(columnIndex, 1, true)));
        width = Math.max(width, computeLabelWidth(labelFormatter.formatLabel(columnIndex, 1, false)));

        if (shiftMultiplier > 1) {
            width = Math.max(width, computeLabelWidth(labelFormatter.formatLabel(columnIndex, shiftMultiplier, true)));
            width = Math.max(width, computeLabelWidth(labelFormatter.formatLabel(columnIndex, shiftMultiplier, false)));
        }

        return width;
    }

    private static int computeLabelWidth(String label) {
        return Math.max(MIN_BUTTON_WIDTH, BUTTON_SIDE_PADDING + label.length() * 6);
    }

    private int getCurrentMultiplier() {
        if (shiftMultiplier <= 1) return 1;

        return isShiftDown() ? shiftMultiplier : 1;
    }

    private static boolean isShiftDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
    }
}