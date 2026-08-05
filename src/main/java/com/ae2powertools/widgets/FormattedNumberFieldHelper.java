package com.ae2powertools.widgets;

import net.minecraft.client.gui.GuiTextField;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;


/**
 * Shared helpers for digit-focused text fields used by GUI widgets.
 */
@SideOnly(Side.CLIENT)
public final class FormattedNumberFieldHelper {

    @FunctionalInterface
    public interface LongParser {

        long parse(String text);
    }

    @FunctionalInterface
    public interface LongFormatter {

        String format(long value);
    }

    private FormattedNumberFieldHelper() {}

    public static void reformatPreservingDigits(GuiTextField field, LongParser parser, LongFormatter formatter) {
        String text = field.getText();
        int cursorPos = field.getCursorPosition();

        int digitsBeforeCursor = 0;
        for (int i = 0; i < cursorPos && i < text.length(); i++) {
            if (Character.isDigit(text.charAt(i))) digitsBeforeCursor++;
        }

        long value = parser.parse(text);
        if (value < 0) return;

        String formatted = formatter.format(value);
        field.setText(formatted);

        int newCursor = 0;
        int digitsSeen = 0;
        for (int i = 0; i < formatted.length(); i++) {
            if (Character.isDigit(formatted.charAt(i))) {
                digitsSeen++;
                if (digitsSeen > digitsBeforeCursor) break;
            }
            newCursor = i + 1;
        }

        field.setCursorPosition(Math.min(newCursor, formatted.length()));
    }

    /**
     * Parses a string into a long, ignoring everything but digits.
     * Returns 0 for empty input. Caps at Long.MAX_VALUE on overflow rather than throwing.
     */
    public static long parseDigits(String text) {
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (Character.isDigit(character)) digits.append(character);
        }

        if (digits.length() == 0) return 0;

        try {
            return Long.parseLong(digits.toString());
        } catch (NumberFormatException ignored) {
            // Number too big to fit in a long: cap to honor "up to Max Long" gracefully.
            return Long.MAX_VALUE;
        }
    }

    public static String formatWithCommas(long value) {
        return String.format("%,d", value);
    }
}