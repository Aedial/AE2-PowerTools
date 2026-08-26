package com.ae2powertools.features.monitor.dependent;

import java.io.IOException;

import net.minecraft.client.resources.I18n;

import appeng.client.gui.AEBaseGui;

import com.ae2powertools.network.PacketReturnToStorageMonitorGui;
import com.ae2powertools.network.PacketSetRefreshRate;
import com.ae2powertools.network.PowerToolsNetwork;
import com.ae2powertools.util.TimeAdjustmentButtons;
import com.ae2powertools.widgets.ItemTabButton;
import com.ae2powertools.widgets.SmallVanillaButton;
import com.ae2powertools.widgets.WidgetContext;
import com.ae2powertools.widgets.WidgetList;


/**
 * Polling-rate sub-GUI for the ME Storage Level Emitter / Display.
 * Modeled after AE2's GuiPriority: a separate priority-style screen
 * with +/- buttons for sec/min/hour/day (x10 with shift),
 * plus a {@link GuiTabButton} that returns to the main host GUI.
 */
public class GuiStorageMonitorPollingRate extends AEBaseGui {

    private final ContainerStorageMonitorPollingRate container;

    private ItemTabButton backButton;
    private final TimeAdjustmentButtons timeButtons = new TimeAdjustmentButtons();
    private final WidgetList widgets = new WidgetList();
    private final WidgetContext widgetContext = WidgetContext.of(this);

    public GuiStorageMonitorPollingRate(ContainerStorageMonitorPollingRate container) {
        super(container);
        this.container = container;
    }

    @Override
    public void initGui() {
        super.initGui();
        this.buttonList.clear();
        this.widgets.clear();

        this.timeButtons.addTo(this.widgets, this.guiLeft, this.guiTop, this::applyAdjustedRefreshRate);

        // Back button: shows the host's block as the icon, like AE2 / CELLS does.
        this.backButton = this.widgets.add(new ItemTabButton(
            this.guiLeft + 154,
            this.guiTop,
            container.getHost().getBackButtonStack(),
            I18n.format(container.getHost().getHostType().getTitleLangKey())));
        this.backButton.setOnClick(this::returnToStorageMonitorGui);
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.fontRenderer.drawString(
            I18n.format("gui.ae2powertools.storage_emitter.polling_rate.title"),
            8, 6, 0x404040);

        // Centered current rate display.
        TimeAdjustmentButtons.drawCenteredValue(
            this.fontRenderer,
            TimeAdjustmentButtons.formatValue(container.refreshRate),
            0,
            this.xSize,
            57,
            0xFFFFFF);
    }

    @Override
    public void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        // Reuse AE2's priority texture for layout.
        this.bindTexture("guis/priority.png");
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, this.ySize);

        this.timeButtons.updateLabels();
        this.widgets.draw(this.widgetContext, mouseX, mouseY);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        this.widgets.drawTooltips(this.widgetContext, mouseX, mouseY);
    }

    private void applyAdjustedRefreshRate(final SmallVanillaButton button) {
        int adjustedValue = this.timeButtons.getAdjustedValue(button, container.refreshRate, MonitorLogic.MIN_REFRESH_RATE);
        if (adjustedValue == Integer.MIN_VALUE) return;

        PowerToolsNetwork.INSTANCE.sendToServer(
            new PacketSetRefreshRate(container.getHost(), adjustedValue));
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (this.widgets.mouseClicked(mouseX, mouseY, mouseButton)) return;

        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private void returnToStorageMonitorGui() {
        PowerToolsNetwork.INSTANCE.sendToServer(
            new PacketReturnToStorageMonitorGui(container.getHost()));
    }
}
