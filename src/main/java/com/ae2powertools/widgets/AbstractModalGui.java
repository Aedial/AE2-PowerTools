package com.ae2powertools.widgets;

import org.lwjgl.input.Keyboard;

import net.minecraft.client.gui.Gui;


/**
 * Shared base class for simple GUI modals.
 * <p>
 * The contract is intentionally strict: opening makes the modal visible, pressing escape closes it,
 * and clicking outside its bounds closes it as well. Individual modals keep their own save or
 * discard behavior by passing the appropriate close callback when they want custom teardown.
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

    public boolean containsPoint(int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}