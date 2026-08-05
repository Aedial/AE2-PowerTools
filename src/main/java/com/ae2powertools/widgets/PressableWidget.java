package com.ae2powertools.widgets;

import java.awt.Rectangle;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import net.minecraft.client.gui.Gui;
import net.minecraftforge.fml.client.config.GuiUtils;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;


/**
 * Small shared base for widgets that own their own hover and click state.
 */
@SideOnly(Side.CLIENT)
public abstract class PressableWidget extends Gui {

    private int x;
    private int y;
    private int width;
    private int height;
    private boolean visible = true;
    private boolean enabled = true;
    private boolean hovered;
    private Runnable onClick = () -> {};
    private Supplier<List<String>> tooltipProvider = Collections::emptyList;

    protected PressableWidget(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public final void draw(WidgetContext context, int mouseX, int mouseY) {
        if (!visible) return;

        hovered = contains(mouseX, mouseY);
        drawWidget(context, mouseX, mouseY);
    }

    protected abstract void drawWidget(WidgetContext context, int mouseX, int mouseY);

    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton != 0 || !visible || !enabled || !contains(mouseX, mouseY)) return false;

        onClick.run();
        return true;
    }

    public boolean contains(int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    public Rectangle getBounds() {
        return new Rectangle(x, y, width, height);
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isHovered() {
        return hovered;
    }

    public void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    /**
     * Sets the position of the widget and makes it visible.
     * Use it if you want to make a widget appear conditionally without having to call setVisible(true) separately.
     * @param x The new x-coordinate of the widget.
     * @param y The new y-coordinate of the widget.
     */
    public void setVisiblePosition(int x, int y) {
        this.x = x;
        this.y = y;
        this.visible = true;
    }

    public void setOnClick(Runnable onClick) {
        this.onClick = Objects.requireNonNull(onClick);
    }

    public List<String> getTooltip() {
        return tooltipProvider.get();
    }

    public void setTooltipProvider(Supplier<List<String>> tooltipProvider) {
        this.tooltipProvider = tooltipProvider == null ? Collections::emptyList : tooltipProvider;
    }

    public void drawTooltip(WidgetContext context, int mouseX, int mouseY) {
        if (!visible || !hovered) return;

        List<String> tooltip = getTooltip();
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
}