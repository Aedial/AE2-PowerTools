package com.ae2powertools.util;

import java.util.List;

import org.lwjgl.input.Keyboard;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;


/**
 * Shared controller for the standard 2x4 time adjustment buttons used by the time GUIs.
 */
public final class TimeAdjustmentButtons {

    private static final int BUTTON_WIDTH = 28;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_COUNT = 8;

    private static final int[] X_OFFSETS = { 20, 54, 88, 122, 20, 54, 88, 122 };
    private static final int[] Y_OFFSETS = { 32, 32, 32, 32, 69, 69, 69, 69 };
    private static final int[] BASE_DELTAS = {
        PollingRateUtils.TICKS_PER_SECOND,
        PollingRateUtils.TICKS_PER_MINUTE,
        PollingRateUtils.TICKS_PER_HOUR,
        PollingRateUtils.TICKS_PER_DAY,
        -PollingRateUtils.TICKS_PER_SECOND,
        -PollingRateUtils.TICKS_PER_MINUTE,
        -PollingRateUtils.TICKS_PER_HOUR,
        -PollingRateUtils.TICKS_PER_DAY
    };
    private static final String[] UNITS = { "s", "m", "h", "d", "s", "m", "h", "d" };

    private final GuiButton[] buttons = new GuiButton[BUTTON_COUNT];

    public void addTo(final List<GuiButton> buttonList, final int guiLeft, final int guiTop) {
        for (int id = 0; id < BUTTON_COUNT; id++) {
            GuiButton button = new GuiButton(
                id,
                guiLeft + X_OFFSETS[id],
                guiTop + Y_OFFSETS[id],
                BUTTON_WIDTH,
                BUTTON_HEIGHT,
                getButtonLabel(id, 1));
            this.buttons[id] = button;
            buttonList.add(button);
        }
    }

    public int getDelta(final GuiButton button) {
        if (!isManagedButton(button)) return 0;

        int multiplier = isShiftDown() ? 10 : 1;
        return BASE_DELTAS[button.id] * multiplier;
    }

    public int getAdjustedValue(final GuiButton button, final long currentValue, final int minimumValue) {
        int delta = getDelta(button);
        if (delta == 0) return Integer.MIN_VALUE;

        long result = currentValue + delta;
        result = Math.max(minimumValue, Math.min(Integer.MAX_VALUE, result));
        return (int) result;
    }

    public void updateLabels() {
        int multiplier = isShiftDown() ? 10 : 1;

        for (int id = 0; id < BUTTON_COUNT; id++) {
            GuiButton button = this.buttons[id];
            if (button == null) continue;

            button.displayString = getButtonLabel(id, multiplier);
        }
    }

    public static void drawCenteredTimeValue(final FontRenderer fontRenderer, final String value, final int guiLeft,
            final int guiWidth, final int y, final int color) {
        int textWidth = fontRenderer.getStringWidth(value);
        int x = guiLeft + (guiWidth - textWidth) / 2;
        fontRenderer.drawString(value, x, y, color);
    }

    public static String formatTimeValue(final long ticks) {
        return PollingRateUtils.format(ticks);
    }

    private boolean isManagedButton(final GuiButton button) {
        if (button == null) return false;
        if (button.id < 0 || button.id >= BUTTON_COUNT) return false;

        return this.buttons[button.id] == button;
    }

    private static String getButtonLabel(final int id, final int multiplier) {
        String sign = id < 4 ? "+" : "-";
        return sign + multiplier + UNITS[id % 4];
    }

    private static boolean isShiftDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
    }
}