package com.ae2powertools.widgets;

import org.lwjgl.input.Keyboard;

import net.minecraft.client.gui.Gui;


/**
 * Shared base class for simple GUI modals.
 * <p>
 * The contract is intentionally strict: opening makes the modal visible, pressing escape closes it,
 * and clicking outside its bounds closes it as well. Individual modals can add their own behaviors
 * by overriding and calling the super methods.
 */
public abstract class AbstractModalGui extends Gui {

    private boolean open;
    private final int width;
    private final int height;

    private int x;
    private int y;

    public AbstractModalGui(int width, int height) {
        this.open = false;
        this.width = width;
        this.height = height;
        this.x = 0;
        this.y = 0;
    }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    protected int getX() {
        return x;
    }

    protected int getY() {
        return y;
    }

    public int getModalWidth() {
        return width;
    }

    public int getModalHeight() {
        return height;
    }

    public boolean isOpen() {
        return open;
    }

    public void open() {
        open = true;
    }

    public void close() {
        open = false;
    }

    public boolean keyTyped(int keyCode) {
        if (!open || keyCode != Keyboard.KEY_ESCAPE) return false;

        close();
        return true;
    }

    public boolean mouseClicked(int mouseX, int mouseY) {
        if (!open || containsPoint(mouseX, mouseY)) return false;

        close();
        return true;
    }

    public void initGui() {
    }

    public void updateScreen() {
    }

    public void draw(int mouseX, int mouseY, float partialTicks) {
    }

    public void drawTooltip(int mouseX, int mouseY) {
    }

    public boolean keyTyped(char typedChar, int keyCode) {
        return keyTyped(keyCode);
    }

    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        return mouseClicked(mouseX, mouseY);
    }

    public void mouseReleased(int mouseX, int mouseY, int state) {
    }

    public boolean mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        return false;
    }

    public boolean handleMouseWheel(int wheelDelta) {
        return false;
    }

    public boolean containsPoint(int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}