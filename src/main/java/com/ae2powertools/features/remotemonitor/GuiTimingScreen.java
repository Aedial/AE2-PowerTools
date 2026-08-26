package com.ae2powertools.features.remotemonitor;

import java.io.IOException;

import org.lwjgl.input.Keyboard;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import com.ae2powertools.ItemRegistry;
import com.ae2powertools.util.TimeAdjustmentButtons;
import com.ae2powertools.widgets.ItemTabButton;
import com.ae2powertools.widgets.SmallVanillaButton;
import com.ae2powertools.widgets.WidgetContext;
import com.ae2powertools.widgets.WidgetList;


/**
 * Shared priority-style sub-screen for timing settings.
 */
public abstract class GuiTimingScreen extends GuiScreen {

    private static final int GUI_WIDTH = 176;
    private static final int GUI_HEIGHT = 166;

    private final long deviceId;
    private final TimeAdjustmentButtons timeButtons = new TimeAdjustmentButtons();
    private final WidgetList widgets = new WidgetList();
    private final WidgetContext widgetContext = WidgetContext.of(this);

    private int guiLeft;
    private int guiTop;
    private int currentValue;
    private ItemTabButton backButton;

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
        this.buttonList.clear();
        this.widgets.clear();

        this.guiLeft = (this.width - GUI_WIDTH) / 2;
        this.guiTop = (this.height - GUI_HEIGHT) / 2;

        this.timeButtons.addTo(this.widgets, this.guiLeft, this.guiTop, this::applyAdjustedValue);

        this.backButton = this.widgets.add(new ItemTabButton(
            this.guiLeft + 154,
            this.guiTop,
            new ItemStack(ItemRegistry.REMOTE_STORAGE_MONITOR),
            I18n.format("gui.ae2powertools.remote_monitor.title")));
        this.backButton.setOnClick(this::returnToParent);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.currentValue = getSyncedValue();

        this.timeButtons.updateLabels();

        this.drawDefaultBackground();
        this.mc.getTextureManager().bindTexture(new ResourceLocation("appliedenergistics2", "textures/guis/priority.png"));
        this.drawTexturedModalRect(this.guiLeft, this.guiTop, 0, 0, GUI_WIDTH, GUI_HEIGHT);
        this.widgets.draw(this.widgetContext, mouseX, mouseY);

        super.drawScreen(mouseX, mouseY, partialTicks);

        this.fontRenderer.drawString(I18n.format(getTitleKey()), this.guiLeft + 8, this.guiTop + 6, 0x404040);

        TimeAdjustmentButtons.drawCenteredValue(
            this.fontRenderer,
            TimeAdjustmentButtons.formatValue(this.currentValue),
            this.guiLeft,
            GUI_WIDTH,
            this.guiTop + 57,
            0xFFFFFF);

        this.widgets.drawTooltips(this.widgetContext, mouseX, mouseY);
    }

    private void applyAdjustedValue(SmallVanillaButton button) {
        int adjustedValue = this.timeButtons.getAdjustedValue(button, this.currentValue, getMinimumValue());
        if (adjustedValue == Integer.MIN_VALUE) return;

        sendUpdatedValue(adjustedValue);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            returnToParent();
            return;
        }

        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (this.widgets.mouseClicked(mouseX, mouseY, mouseButton)) return;

        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private void returnToParent() {
        this.mc.displayGuiScreen(new GuiRemoteMonitor(this.deviceId));
    }

    protected abstract int getSyncedValue();

    protected abstract int getMinimumValue();

    protected abstract String getTitleKey();

    protected abstract void sendUpdatedValue(int value);
}