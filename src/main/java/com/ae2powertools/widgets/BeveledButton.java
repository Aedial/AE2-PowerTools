package com.ae2powertools.widgets;

import com.ae2powertools.client.gui.VanillaButtonRenderer;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;


/**
 * Lightweight shared button that uses the mod's small beveled renderer.
 */
@SideOnly(Side.CLIENT)
public class BeveledButton extends PressableWidget {

    private String label;

    public BeveledButton(int x, int y, int width, int height, String label) {
        super(x, y, width, height);
        this.label = label;
    }

    public BeveledButton(int x, int y, int size, String label) {
        this(x, y, size, size, label);
    }

    @Override
    protected void drawWidget(WidgetContext context, int mouseX, int mouseY) {
        VanillaButtonRenderer.drawBeveledButton(
            context.getWidgetFontRenderer(),
            getX(),
            getY(),
            getWidth(),
            getHeight(),
            label,
            isEnabled(),
            isHovered());
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label == null ? "" : label;
    }
}