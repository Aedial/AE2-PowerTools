package com.ae2powertools.features.tuner;

import java.io.IOException;

import javax.annotation.Nonnull;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;

import appeng.client.gui.widgets.GuiNumberBox;

import com.ae2powertools.network.PacketSetTunerPriority;
import com.ae2powertools.network.PowerToolsNetwork;
import com.ae2powertools.util.StepAdjustmentButtons;


/**
 * GUI for setting the Priority Tuner's stored priority value.
 * Similar to AE2's priority GUI but for the Tuner itself.
 */
public class GuiPriorityTuner extends GuiContainer {

    // Use AE2's priority texture for now (same layout)
    private static final ResourceLocation TEXTURE = new ResourceLocation("appliedenergistics2", "textures/guis/priority.png");

    private GuiNumberBox priorityField;
    private final StepAdjustmentButtons priorityButtons = StepAdjustmentButtons.forNumeric(1, 10, 100, 1000);

    private final ContainerPriorityTuner container;

    public GuiPriorityTuner(InventoryPlayer playerInventory, EnumHand hand) {
        super(new ContainerPriorityTuner(playerInventory, hand));
        this.container = (ContainerPriorityTuner) inventorySlots;
        this.xSize = 176;
        this.ySize = 107;
    }

    @Override
    public void initGui() {
        super.initGui();

        // Priority adjustment buttons
        this.priorityButtons.addTo(this.buttonList, this.guiLeft, this.guiTop);

        // Priority text field
        this.priorityField = new GuiNumberBox(this.fontRenderer, this.guiLeft + 62, this.guiTop + 57, 59, this.fontRenderer.FONT_HEIGHT, Long.class);
        this.priorityField.setEnableBackgroundDrawing(false);
        this.priorityField.setMaxStringLength(16);
        this.priorityField.setTextColor(0xFFFFFF);
        this.priorityField.setVisible(true);
        this.priorityField.setFocused(true);
        this.priorityField.setText(String.valueOf(container.getPriority()));
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        String title = I18n.format("gui.ae2powertools.priority_tuner.title");
        this.fontRenderer.drawString(title, 8, 6, 0x404040);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        this.mc.getTextureManager().bindTexture(TEXTURE);
        this.drawTexturedModalRect(this.guiLeft, this.guiTop, 0, 0, this.xSize, this.ySize);

        this.priorityField.drawTextBox();
    }

    @Override
    protected void actionPerformed(@Nonnull GuiButton button) throws IOException {
        super.actionPerformed(button);

        if (!this.priorityButtons.manages(button)) return;

        applyAdjustedPriority(button);
    }

    private void applyAdjustedPriority(GuiButton button) {
        try {
            long adjustedValue = this.priorityButtons.getAdjustedValue(
                button,
                parseCurrentPriorityField(),
                Integer.MIN_VALUE,
                Integer.MAX_VALUE);
            setPriorityValue((int) adjustedValue);
        } catch (NumberFormatException e) {
            // Ignore invalid input
        }
    }

    private long parseCurrentPriorityField() {
        String text = this.priorityField.getText();
        if (text.isEmpty()) text = "0";

        return Long.parseLong(text);
    }

    private void setPriorityValue(int priority) {
        this.priorityField.setText(String.valueOf(priority));
        sendPriorityUpdate(priority);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (this.priorityField.textboxKeyTyped(typedChar, keyCode)) {
            // Send update when typing
            try {
                String text = this.priorityField.getText();
                if (text.isEmpty() || text.equals("-")) return;

                long value = Long.parseLong(text);
                value = Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, value));
                sendPriorityUpdate((int) value);
            } catch (NumberFormatException e) {
                // Ignore invalid input
            }

            return;
        }

        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        this.priorityField.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private void sendPriorityUpdate(int priority) {
        PowerToolsNetwork.INSTANCE.sendToServer(new PacketSetTunerPriority(
            container.getHand() == EnumHand.MAIN_HAND ? 0 : 1,
            priority
        ));
    }
}
