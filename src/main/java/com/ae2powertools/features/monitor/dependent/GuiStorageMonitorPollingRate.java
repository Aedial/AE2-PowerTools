package com.ae2powertools.features.monitor.dependent;

import java.io.IOException;

import javax.annotation.Nonnull;

import org.lwjgl.input.Keyboard;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;

import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.GuiTabButton;

import com.ae2powertools.network.PacketReturnToStorageMonitorGui;
import com.ae2powertools.network.PacketSetRefreshRate;
import com.ae2powertools.network.PowerToolsNetwork;
import com.ae2powertools.util.PollingRateUtils;


/**
 * Polling-rate sub-GUI for the ME Storage Level Emitter / Display.
 * Modeled after AE2's GuiPriority: a separate priority-style screen
 * with +/- buttons for sec/min/hour/day (x10 with shift),
 * plus a {@link GuiTabButton} that returns to the main host GUI.
 */
public class GuiStorageMonitorPollingRate extends AEBaseGui {

    private final ContainerStorageMonitorPollingRate container;

    private GuiTabButton backBtn;
    private GuiButton plusSec, plusMin, plusHour, plusDay;
    private GuiButton minusSec, minusMin, minusHour, minusDay;

    public GuiStorageMonitorPollingRate(ContainerStorageMonitorPollingRate container) {
        super(container);
        this.container = container;
    }

    @Override
    public void initGui() {
        super.initGui();

        // +/- buttons in a 2x4 grid, mirroring AE2 GuiPriority's layout exactly.
        this.buttonList.add(this.plusSec  = new GuiButton(0, this.guiLeft + 20,  this.guiTop + 32, 28, 20, "+1s"));
        this.buttonList.add(this.plusMin  = new GuiButton(1, this.guiLeft + 54,  this.guiTop + 32, 28, 20, "+1m"));
        this.buttonList.add(this.plusHour = new GuiButton(2, this.guiLeft + 88,  this.guiTop + 32, 28, 20, "+1h"));
        this.buttonList.add(this.plusDay  = new GuiButton(3, this.guiLeft + 122, this.guiTop + 32, 28, 20, "+1d"));

        this.buttonList.add(this.minusSec  = new GuiButton(4, this.guiLeft + 20,  this.guiTop + 69, 28, 20, "-1s"));
        this.buttonList.add(this.minusMin  = new GuiButton(5, this.guiLeft + 54,  this.guiTop + 69, 28, 20, "-1m"));
        this.buttonList.add(this.minusHour = new GuiButton(6, this.guiLeft + 88,  this.guiTop + 69, 28, 20, "-1h"));
        this.buttonList.add(this.minusDay  = new GuiButton(7, this.guiLeft + 122, this.guiTop + 69, 28, 20, "-1d"));

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
        String timeStr = PollingRateUtils.format(container.refreshRate);
        int textWidth = this.fontRenderer.getStringWidth(timeStr);
        int centerX = (this.xSize - textWidth) / 2;
        this.fontRenderer.drawString(timeStr, centerX, 57, 0xFFFFFF);
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

        int delta = getButtonDelta(btn);
        if (delta != 0) addRefreshRate(delta);
    }

    private int getButtonDelta(final GuiButton btn) {
        boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
        int multiplier = shift ? 10 : 1;

        if (btn == this.plusSec)   return PollingRateUtils.TICKS_PER_SECOND * multiplier;
        if (btn == this.plusMin)   return PollingRateUtils.TICKS_PER_MINUTE * multiplier;
        if (btn == this.plusHour)  return PollingRateUtils.TICKS_PER_HOUR * multiplier;
        if (btn == this.plusDay)   return PollingRateUtils.TICKS_PER_DAY * multiplier;

        if (btn == this.minusSec)  return -PollingRateUtils.TICKS_PER_SECOND * multiplier;
        if (btn == this.minusMin)  return -PollingRateUtils.TICKS_PER_MINUTE * multiplier;
        if (btn == this.minusHour) return -PollingRateUtils.TICKS_PER_HOUR * multiplier;
        if (btn == this.minusDay)  return -PollingRateUtils.TICKS_PER_DAY * multiplier;

        return 0;
    }

    private void addRefreshRate(final int delta) {
        long result = (long) container.refreshRate + delta;
        // Clamp to [MIN_REFRESH_RATE, Integer.MAX_VALUE].
        result = Math.max(MonitorLogic.MIN_REFRESH_RATE, Math.min(Integer.MAX_VALUE, result));
        PowerToolsNetwork.INSTANCE.sendToServer(
            new PacketSetRefreshRate(container.getHost(), (int) result));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);

        // Update labels live so the user always sees the actual delta a click will apply.
        boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);

        this.plusSec.displayString  = shift ? "+10s" : "+1s";
        this.plusMin.displayString  = shift ? "+10m" : "+1m";
        this.plusHour.displayString = shift ? "+10h" : "+1h";
        this.plusDay.displayString  = shift ? "+10d" : "+1d";

        this.minusSec.displayString  = shift ? "-10s" : "-1s";
        this.minusMin.displayString  = shift ? "-10m" : "-1m";
        this.minusHour.displayString = shift ? "-10h" : "-1h";
        this.minusDay.displayString  = shift ? "-10d" : "-1d";
    }
}
