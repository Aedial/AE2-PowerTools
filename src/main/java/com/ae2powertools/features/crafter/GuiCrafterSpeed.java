package com.ae2powertools.features.crafter;

import java.io.IOException;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;

import appeng.client.gui.AEBaseGui;

import com.ae2powertools.network.PowerToolsNetwork;
import com.ae2powertools.util.TimeAdjustmentButtons;
import com.ae2powertools.widgets.ItemTabButton;
import com.ae2powertools.widgets.SmallVanillaButton;
import com.ae2powertools.widgets.WidgetContext;
import com.ae2powertools.widgets.WidgetList;


/**
 * GUI for configuring the speed of the AutoCrafter.
 * Has +/- buttons for 1s, 1m, 1h, 1d. Holding shift multiplies by 10.
 */
public class GuiCrafterSpeed extends AEBaseGui {

    private ItemTabButton backButton;

    private final TimeAdjustmentButtons timeButtons = new TimeAdjustmentButtons();
    private final WidgetList widgets = new WidgetList();
    private final WidgetContext widgetContext = WidgetContext.of(this);

    private final ContainerCrafterSpeed container;

    public GuiCrafterSpeed(ContainerCrafterSpeed container) {
        super(container);
        this.container = container;
    }

    @Override
    public void initGui() {
        super.initGui();
        this.buttonList.clear();
        this.widgets.clear();

        this.timeButtons.addTo(this.widgets, this.guiLeft, this.guiTop, this::applyAdjustedSpeed);

        // Back button
        TileAutoCrafter tile = container.getTile();
        this.backButton = this.widgets.add(new ItemTabButton(
            this.guiLeft + 154,
            this.guiTop,
            new ItemStack(tile.getBlockType()),
            I18n.format("gui.ae2powertools.crafter.title")));
        this.backButton.setOnClick(this::returnToCrafterGui);
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.fontRenderer.drawString(I18n.format("gui.ae2powertools.crafter.speed.title"), 8, 6, 0x404040);

        // Draw the current speed centered in the display area
        TimeAdjustmentButtons.drawCenteredValue(
            this.fontRenderer,
            TimeAdjustmentButtons.formatValue(container.speedTicks),
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

        this.timeButtons.updateLabels();
        this.widgets.draw(this.widgetContext, mouseX, mouseY);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        this.widgets.drawTooltips(this.widgetContext, mouseX, mouseY);
    }

    private void applyAdjustedSpeed(SmallVanillaButton button) {
        int adjustedValue = this.timeButtons.getAdjustedValue(button, container.speedTicks, TileAutoCrafter.MIN_SPEED_TICKS);
        if (adjustedValue == Integer.MIN_VALUE) return;

        PowerToolsNetwork.INSTANCE.sendToServer(new PacketSetCrafterSpeed(container.getTile().getPos(), adjustedValue));
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (this.widgets.mouseClicked(mouseX, mouseY, mouseButton)) return;

        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private void returnToCrafterGui() {
        TileAutoCrafter tile = container.getTile();
        PowerToolsNetwork.INSTANCE.sendToServer(new PacketReturnToCrafterGui(tile.getPos()));
    }
}
