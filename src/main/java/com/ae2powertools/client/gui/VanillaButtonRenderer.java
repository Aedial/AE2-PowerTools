package com.ae2powertools.client.gui;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;


/**
 * Shared renderer for small vanilla-style beveled buttons.
 * Uses simple fills and borders instead of the standard vanilla button texture, so that
 * small buttons stay readable without inheriting the large vanilla inner padding.
 */
@SideOnly(Side.CLIENT)
public final class VanillaButtonRenderer {

    private VanillaButtonRenderer() {}

    public static void drawBeveledButton(FontRenderer fontRenderer, int x, int y, int width, int height,
            String text, boolean enabled, boolean hovered) {
        int backgroundColor;
        if (!enabled) {
            backgroundColor = 0xFF606060;
        } else if (hovered) {
            backgroundColor = 0xFF7090B0;
        } else {
            backgroundColor = 0xFF808080;
        }

        Gui.drawRect(x + 1, y + 1, x + width - 1, y + height - 1, backgroundColor);

        int borderLight = enabled ? 0xFFAAAAAA : 0xFF808080;
        int borderDark = enabled ? 0xFF404040 : 0xFF505050;
        int borderOuter = 0xFF000000;

        Gui.drawRect(x, y, x + width, y + 1, borderOuter);
        Gui.drawRect(x, y + height - 1, x + width, y + height, borderOuter);
        Gui.drawRect(x, y, x + 1, y + height, borderOuter);
        Gui.drawRect(x + width - 1, y, x + width, y + height, borderOuter);

        Gui.drawRect(x + 1, y + 1, x + width - 1, y + 2, borderLight);
        Gui.drawRect(x + 1, y + 1, x + 2, y + height - 1, borderLight);
        Gui.drawRect(x + 1, y + height - 2, x + width - 1, y + height - 1, borderDark);
        Gui.drawRect(x + width - 2, y + 1, x + width - 1, y + height - 1, borderDark);

        String buttonText = text == null ? "" : text;
        int textColor = enabled ? (hovered ? 0xFFFFFFA0 : 0xFFE0E0E0) : 0xFFA0A0A0;
        int textX = x + (width - fontRenderer.getStringWidth(buttonText)) / 2 + 1;
        int textY = y + (height - fontRenderer.FONT_HEIGHT) / 2 + 1;
        fontRenderer.drawStringWithShadow(buttonText, textX, textY, textColor);
    }
}