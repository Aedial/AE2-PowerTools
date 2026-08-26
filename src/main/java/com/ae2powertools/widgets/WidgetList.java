package com.ae2powertools.widgets;

import java.util.ArrayList;
import java.util.List;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;


/**
 * Lightweight widget host for screens that do not use WidgetGui.
 */
@SideOnly(Side.CLIENT)
public class WidgetList {

    private final List<PressableWidget> widgets = new ArrayList<>();

    public void clear() {
        widgets.clear();
    }

    public <T extends PressableWidget> T add(T widget) {
        widgets.add(widget);
        return widget;
    }

    public void draw(WidgetContext context, int mouseX, int mouseY) {
        for (PressableWidget widget : widgets) widget.draw(context, mouseX, mouseY);
    }

    public void drawTooltips(WidgetContext context, int mouseX, int mouseY) {
        for (PressableWidget widget : widgets) widget.drawTooltip(context, mouseX, mouseY);
    }

    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        for (PressableWidget widget : widgets) {
            if (widget.mouseClicked(mouseX, mouseY, mouseButton)) return true;
        }

        return false;
    }
}