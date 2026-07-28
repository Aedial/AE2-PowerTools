package com.ae2powertools.features.remotemonitor;

import java.io.IOException;

import org.lwjgl.input.Keyboard;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import appeng.client.gui.widgets.GuiTabButton;

import com.ae2powertools.ItemRegistry;
import com.ae2powertools.util.TimeAdjustmentButtons;


/**
 * Shared priority-style sub-screen for timing settings.
 */
public abstract class GuiTimingScreen extends GuiScreen {

    private static final int GUI_WIDTH = 176;
    private static final int GUI_HEIGHT = 166;

    private final long deviceId;
    private final TimeAdjustmentButtons timeButtons = new TimeAdjustmentButtons();

    private int guiLeft;
    private int guiTop;
    private int currentValue;
    private GuiTabButton backBtn;

    protected GuiTimingScreen(long deviceId, int initialValue) {
        this.deviceId = deviceId;
        this.currentValue = initialValue;
    }

    protected final long getDeviceId() {
        return this.deviceId;
    }

    @Override
    public void initGui() {
        super.initGui();

        this.guiLeft = (this.width - GUI_WIDTH) / 2;
        this.guiTop = (this.height - GUI_HEIGHT) / 2;

        this.buttonList.clear();
        this.timeButtons.addTo(this.buttonList, this.guiLeft, this.guiTop);

        this.buttonList.add(this.backBtn = new GuiTabButton(
            this.guiLeft + 154,
            this.guiTop,
            new ItemStack(ItemRegistry.REMOTE_STORAGE_MONITOR),
            I18n.format("gui.ae2powertools.remote_monitor.title"),
            this.itemRender));
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        super.actionPerformed(button);

        if (button == this.backBtn) {
            returnToParent();
            return;
        }

        int adjustedValue = this.timeButtons.getAdjustedValue(button, this.currentValue, getMinimumValue());
        if (adjustedValue == Integer.MIN_VALUE) return;

        sendUpdatedValue(adjustedValue);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.currentValue = getSyncedValue();

        this.drawDefaultBackground();
        this.mc.getTextureManager().bindTexture(new ResourceLocation("appliedenergistics2", "textures/guis/priority.png"));
        this.drawTexturedModalRect(this.guiLeft, this.guiTop, 0, 0, GUI_WIDTH, GUI_HEIGHT);

        super.drawScreen(mouseX, mouseY, partialTicks);

        this.fontRenderer.drawString(I18n.format(getTitleKey()), this.guiLeft + 8, this.guiTop + 6, 0x404040);

        TimeAdjustmentButtons.drawCenteredTimeValue(
            this.fontRenderer,
            TimeAdjustmentButtons.formatTimeValue(this.currentValue),
            this.guiLeft,
            GUI_WIDTH,
            this.guiTop + 57,
            0xFFFFFF);

        this.timeButtons.updateLabels();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            returnToParent();
            return;
        }

        super.keyTyped(typedChar, keyCode);
    }

    private void returnToParent() {
        this.mc.displayGuiScreen(new GuiRemoteMonitor(this.deviceId));
    }

    protected abstract int getSyncedValue();

    protected abstract int getMinimumValue();

    protected abstract String getTitleKey();

    protected abstract void sendUpdatedValue(int value);
}