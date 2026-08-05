package com.ae2powertools.features.monitor.dependent;

import java.io.IOException;

import javax.annotation.Nonnull;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;

import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.GuiTabButton;

import com.ae2powertools.network.PacketReturnToStorageMonitorGui;
import com.ae2powertools.network.PacketSetRefreshRate;
import com.ae2powertools.network.PowerToolsNetwork;
import com.ae2powertools.util.TimeAdjustmentButtons;


/**
 * Polling-rate sub-GUI for the ME Storage Level Emitter / Display.
 * Modeled after AE2's GuiPriority: a separate priority-style screen
 * with +/- buttons for sec/min/hour/day (x10 with shift),
 * plus a {@link GuiTabButton} that returns to the main host GUI.
 */
public class GuiStorageMonitorPollingRate extends AEBaseGui {

    private final ContainerStorageMonitorPollingRate container;

    private GuiTabButton backBtn;
    private final TimeAdjustmentButtons timeButtons = new TimeAdjustmentButtons();

    public GuiStorageMonitorPollingRate(ContainerStorageMonitorPollingRate container) {
        super(container);
        this.container = container;
    }

    @Override
    public void initGui() {
        super.initGui();

        this.timeButtons.addTo(this.buttonList, this.guiLeft, this.guiTop);

        // Back button: shows the host's block as the icon, like AE2 / CELLS does.
        this.buttonList.add(this.backBtn = new GuiTabButton(
            this.guiLeft + 154,
            this.guiTop,
            container.getHost().getBackButtonStack(),
            I18n.format(container.getHost().getHostType().getTitleLangKey()),
            this.itemRender
        ));
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
    }

    @Override
    protected void actionPerformed(@Nonnull final GuiButton btn) throws IOException {
        super.actionPerformed(btn);

        if (btn == this.backBtn) {
            // Return to the main GUI via a server round-trip so synced fields land before render.
            PowerToolsNetwork.INSTANCE.sendToServer(
                new PacketReturnToStorageMonitorGui(container.getHost()));
            return;
        }

        int adjustedValue = this.timeButtons.getAdjustedValue(btn, container.refreshRate, MonitorLogic.MIN_REFRESH_RATE);
        if (adjustedValue == Integer.MIN_VALUE) return;

        PowerToolsNetwork.INSTANCE.sendToServer(
            new PacketSetRefreshRate(container.getHost(), adjustedValue));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);

        // Update labels live so the user always sees the actual delta a click will apply.
        this.timeButtons.updateLabels();
    }
}
