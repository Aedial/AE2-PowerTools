package com.ae2powertools.features.remotemonitor;

import java.io.IOException;

import org.lwjgl.input.Keyboard;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;

import appeng.client.gui.widgets.GuiTabButton;

import com.ae2powertools.ItemRegistry;
import com.ae2powertools.network.PacketRemoteMonitorSetRefreshRate;
import com.ae2powertools.network.PowerToolsNetwork;
import com.ae2powertools.util.PollingRateUtils;


/**
 * Standalone polling-rate screen for the Remote Storage Monitor.
 * TODO: Refactor it with the other polling rate GUI
 */
public class GuiRemoteMonitorPollingRate extends GuiScreen {

    private static final int GUI_WIDTH = 176;
    private static final int GUI_HEIGHT = 166;

    private final long deviceId;

    private int guiLeft;
    private int guiTop;
    private int currentRefreshRate;

    private GuiTabButton backBtn;
    private GuiButton plusSec;
    private GuiButton plusMin;
    private GuiButton plusHour;
    private GuiButton plusDay;
    private GuiButton minusSec;
    private GuiButton minusMin;
    private GuiButton minusHour;
    private GuiButton minusDay;

    public GuiRemoteMonitorPollingRate(long deviceId) {
        this.deviceId = deviceId;
        this.currentRefreshRate = RemoteMonitorClientState.getOrCreateState(deviceId).getRefreshRate();
    }

    @Override
    public void initGui() {
        super.initGui();

        this.guiLeft = (this.width - GUI_WIDTH) / 2;
        this.guiTop = (this.height - GUI_HEIGHT) / 2;

        this.buttonList.clear();
        this.buttonList.add(this.plusSec = new GuiButton(0, this.guiLeft + 20, this.guiTop + 32, 28, 20, "+1s"));
        this.buttonList.add(this.plusMin = new GuiButton(1, this.guiLeft + 54, this.guiTop + 32, 28, 20, "+1m"));
        this.buttonList.add(this.plusHour = new GuiButton(2, this.guiLeft + 88, this.guiTop + 32, 28, 20, "+1h"));
        this.buttonList.add(this.plusDay = new GuiButton(3, this.guiLeft + 122, this.guiTop + 32, 28, 20, "+1d"));

        this.buttonList.add(this.minusSec = new GuiButton(4, this.guiLeft + 20, this.guiTop + 69, 28, 20, "-1s"));
        this.buttonList.add(this.minusMin = new GuiButton(5, this.guiLeft + 54, this.guiTop + 69, 28, 20, "-1m"));
        this.buttonList.add(this.minusHour = new GuiButton(6, this.guiLeft + 88, this.guiTop + 69, 28, 20, "-1h"));
        this.buttonList.add(this.minusDay = new GuiButton(7, this.guiLeft + 122, this.guiTop + 69, 28, 20, "-1d"));

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
            this.mc.displayGuiScreen(new GuiRemoteMonitor(this.deviceId));
            return;
        }

        int delta = getButtonDelta(button);
        if (delta == 0) return;

        addRefreshRate(delta);
    }

    private int getButtonDelta(GuiButton button) {
        boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
        int multiplier = shift ? 10 : 1;

        if (button == this.plusSec) return PollingRateUtils.TICKS_PER_SECOND * multiplier;
        if (button == this.plusMin) return PollingRateUtils.TICKS_PER_MINUTE * multiplier;
        if (button == this.plusHour) return PollingRateUtils.TICKS_PER_HOUR * multiplier;
        if (button == this.plusDay) return PollingRateUtils.TICKS_PER_DAY * multiplier;
        if (button == this.minusSec) return -PollingRateUtils.TICKS_PER_SECOND * multiplier;
        if (button == this.minusMin) return -PollingRateUtils.TICKS_PER_MINUTE * multiplier;
        if (button == this.minusHour) return -PollingRateUtils.TICKS_PER_HOUR * multiplier;
        if (button == this.minusDay) return -PollingRateUtils.TICKS_PER_DAY * multiplier;

        return 0;
    }

    private void addRefreshRate(int delta) {
        long result = (long) this.currentRefreshRate + delta;
        result = Math.max(RemoteMonitorSessionManager.MIN_REFRESH_RATE, Math.min(Integer.MAX_VALUE, result));
        this.currentRefreshRate = (int) result;
        PowerToolsNetwork.INSTANCE.sendToServer(new PacketRemoteMonitorSetRefreshRate(this.deviceId, this.currentRefreshRate));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.currentRefreshRate = RemoteMonitorClientState.getOrCreateState(this.deviceId).getRefreshRate();

        this.drawDefaultBackground();
        this.mc.getTextureManager().bindTexture(new net.minecraft.util.ResourceLocation("appliedenergistics2", "textures/guis/priority.png"));
        this.drawTexturedModalRect(this.guiLeft, this.guiTop, 0, 0, GUI_WIDTH, GUI_HEIGHT);

        super.drawScreen(mouseX, mouseY, partialTicks);

        this.fontRenderer.drawString(I18n.format("gui.ae2powertools.remote_monitor.polling_rate.title"),
            this.guiLeft + 8, this.guiTop + 6, 0x404040);

        String timeStr = PollingRateUtils.format(this.currentRefreshRate);
        int textWidth = this.fontRenderer.getStringWidth(timeStr);
        int centerX = this.guiLeft + (GUI_WIDTH - textWidth) / 2;
        this.fontRenderer.drawString(timeStr, centerX, this.guiTop + 57, 0xFFFFFF);

        boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
        this.plusSec.displayString = shift ? "+10s" : "+1s";
        this.plusMin.displayString = shift ? "+10m" : "+1m";
        this.plusHour.displayString = shift ? "+10h" : "+1h";
        this.plusDay.displayString = shift ? "+10d" : "+1d";
        this.minusSec.displayString = shift ? "-10s" : "-1s";
        this.minusMin.displayString = shift ? "-10m" : "-1m";
        this.minusHour.displayString = shift ? "-10h" : "-1h";
        this.minusDay.displayString = shift ? "-10d" : "-1d";
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            this.mc.displayGuiScreen(new GuiRemoteMonitor(this.deviceId));
            return;
        }

        super.keyTyped(typedChar, keyCode);
    }
}