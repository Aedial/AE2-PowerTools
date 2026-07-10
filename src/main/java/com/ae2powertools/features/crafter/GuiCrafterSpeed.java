package com.ae2powertools.features.crafter;

import java.io.IOException;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;

import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.GuiTabButton;

import com.ae2powertools.network.PowerToolsNetwork;
import com.ae2powertools.util.TimeAdjustmentButtons;


/**
 * GUI for configuring the speed of the AutoCrafter.
 * Has +/- buttons for 1s, 1m, 1h, 1d. Holding shift multiplies by 10.
 */
public class GuiCrafterSpeed extends AEBaseGui {

    private GuiTabButton backBtn;

    private final TimeAdjustmentButtons timeButtons = new TimeAdjustmentButtons();

    private final ContainerCrafterSpeed container;

    public GuiCrafterSpeed(ContainerCrafterSpeed container) {
        super(container);
        this.container = container;
    }

    @Override
    public void initGui() {
        super.initGui();

        this.timeButtons.addTo(this.buttonList, this.guiLeft, this.guiTop);

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
        TimeAdjustmentButtons.drawCenteredTimeValue(
            this.fontRenderer,
            TimeAdjustmentButtons.formatTimeValue(container.speedTicks),
            0,
            this.xSize,
            57,
            0xFFFFFF);
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

        int adjustedValue = this.timeButtons.getAdjustedValue(btn, container.speedTicks, TileAutoCrafter.MIN_SPEED_TICKS);
        if (adjustedValue == Integer.MIN_VALUE) return;

        PowerToolsNetwork.INSTANCE.sendToServer(new PacketSetCrafterSpeed(container.getTile().getPos(), adjustedValue));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);

        // Keep the button labels in sync with the active shift multiplier.
        this.timeButtons.updateLabels();
    }
}
