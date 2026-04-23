package com.ae2powertools.features.crafter;

import java.io.IOException;

import org.lwjgl.input.Keyboard;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;

import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.GuiTabButton;

import com.ae2powertools.network.PowerToolsNetwork;


/**
 * GUI for configuring the speed of the AutoCrafter.
 * Has +/- buttons for 1s, 1m, 1h, 1d. Holding shift multiplies by 10.
 */
public class GuiCrafterSpeed extends AEBaseGui {

    // Time constants
    private static final int TICKS_PER_SECOND = 20;
    private static final int TICKS_PER_MINUTE = TICKS_PER_SECOND * 60;
    private static final int TICKS_PER_HOUR = TICKS_PER_MINUTE * 60;
    private static final int TICKS_PER_DAY = TICKS_PER_HOUR * 24;

    private GuiTabButton backBtn;

    private GuiButton plusSec;
    private GuiButton plusMin;
    private GuiButton plusHour;
    private GuiButton plusDay;
    private GuiButton minusSec;
    private GuiButton minusMin;
    private GuiButton minusHour;
    private GuiButton minusDay;

    private final ContainerCrafterSpeed container;

    public GuiCrafterSpeed(ContainerCrafterSpeed container) {
        super(container);
        this.container = container;
    }

    @Override
    public void initGui() {
        super.initGui();

        // Plus buttons (top row)
        this.buttonList.add(this.plusSec = new GuiButton(0, this.guiLeft + 20, this.guiTop + 32, 28, 20, "+1s"));
        this.buttonList.add(this.plusMin = new GuiButton(1, this.guiLeft + 54, this.guiTop + 32, 28, 20, "+1m"));
        this.buttonList.add(this.plusHour = new GuiButton(2, this.guiLeft + 88, this.guiTop + 32, 28, 20, "+1h"));
        this.buttonList.add(this.plusDay = new GuiButton(3, this.guiLeft + 122, this.guiTop + 32, 28, 20, "+1d"));

        // Minus buttons (bottom row)
        this.buttonList.add(this.minusSec = new GuiButton(4, this.guiLeft + 20, this.guiTop + 69, 28, 20, "-1s"));
        this.buttonList.add(this.minusMin = new GuiButton(5, this.guiLeft + 54, this.guiTop + 69, 28, 20, "-1m"));
        this.buttonList.add(this.minusHour = new GuiButton(6, this.guiLeft + 88, this.guiTop + 69, 28, 20, "-1h"));
        this.buttonList.add(this.minusDay = new GuiButton(7, this.guiLeft + 122, this.guiTop + 69, 28, 20, "-1d"));

        // Back button
        TileAutoCrafter tile = container.getTile();
        this.buttonList.add(this.backBtn = new GuiTabButton(
            this.guiLeft + 154,
            this.guiTop,
            new ItemStack(tile.getBlockType()),
            I18n.format("gui.ae2powertools.crafter.title"),
            this.itemRender
        ));
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.fontRenderer.drawString(I18n.format("gui.ae2powertools.crafter.speed.title"), 8, 6, 0x404040);

        // Draw the current speed centered in the display area
        String timeStr = formatTicks(container.speedTicks);
        int textWidth = this.fontRenderer.getStringWidth(timeStr);
        int centerX = (this.xSize - textWidth) / 2;
        this.fontRenderer.drawString(timeStr, centerX, 57, 0xFFFFFF);
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
        // Use AE2's priority texture as base
        this.bindTexture("guis/priority.png");
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, this.ySize);
    }

    @Override
    protected void actionPerformed(GuiButton btn) throws IOException {
        super.actionPerformed(btn);

        if (btn == this.backBtn) {
            // Send packet to server to return to main crafter GUI
            // This ensures proper sync of values on first frame
            TileAutoCrafter tile = container.getTile();
            PowerToolsNetwork.INSTANCE.sendToServer(new PacketReturnToCrafterGui(tile.getPos()));
            return;
        }

        int delta = getButtonDelta(btn);
        if (delta != 0) addSpeed(delta);
    }

    private int getButtonDelta(GuiButton btn) {
        boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
        int multiplier = shift ? 10 : 1;

        if (btn == this.plusSec) return TICKS_PER_SECOND * multiplier;
        if (btn == this.plusMin) return TICKS_PER_MINUTE * multiplier;
        if (btn == this.plusHour) return TICKS_PER_HOUR * multiplier;
        if (btn == this.plusDay) return TICKS_PER_DAY * multiplier;

        if (btn == this.minusSec) return -TICKS_PER_SECOND * multiplier;
        if (btn == this.minusMin) return -TICKS_PER_MINUTE * multiplier;
        if (btn == this.minusHour) return -TICKS_PER_HOUR * multiplier;
        if (btn == this.minusDay) return -TICKS_PER_DAY * multiplier;

        return 0;
    }

    private void addSpeed(int delta) {
        long result = container.speedTicks + delta;
        result = Math.max(TileAutoCrafter.MIN_SPEED_TICKS, Math.min(Integer.MAX_VALUE, result));
        PowerToolsNetwork.INSTANCE.sendToServer(new PacketSetCrafterSpeed(container.getTile().getPos(), (int) result));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);

        // Update button labels based on shift state
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

    /**
     * Formats ticks into a human-readable time string.
     */
    public static String formatTicks(int ticks) {
        if (ticks <= 0) return "0";

        int days = ticks / TICKS_PER_DAY;
        ticks %= TICKS_PER_DAY;
        int hours = ticks / TICKS_PER_HOUR;
        ticks %= TICKS_PER_HOUR;
        int minutes = ticks / TICKS_PER_MINUTE;
        ticks %= TICKS_PER_MINUTE;
        int seconds = ticks / TICKS_PER_SECOND;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (seconds > 0 || sb.length() == 0) sb.append(seconds).append("s");

        return sb.toString().trim();
    }
}
