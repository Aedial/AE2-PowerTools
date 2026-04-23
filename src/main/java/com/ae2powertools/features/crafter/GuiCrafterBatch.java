package com.ae2powertools.features.crafter;

import java.io.IOException;

import org.lwjgl.input.Keyboard;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;

import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.GuiTabButton;

import com.ae2powertools.network.PowerToolsNetwork;


/**
 * GUI for configuring the batch size of the AutoCrafter.
 * Similar to AE2's Priority GUI with +/- buttons and a number field.
 */
public class GuiCrafterBatch extends AEBaseGui {

    private GuiTextField sizeField;
    private GuiTabButton backBtn;

    private GuiButton plus1;
    private GuiButton plus10;
    private GuiButton plus100;
    private GuiButton plus1000;
    private GuiButton minus1;
    private GuiButton minus10;
    private GuiButton minus100;
    private GuiButton minus1000;

    private final ContainerCrafterBatch container;

    /**
     * Tracks when the server-synced value has been received.
     * Prevents overwriting user input while waiting for server round-trip.
     */
    private int lastSyncedValue = -1;

    public GuiCrafterBatch(ContainerCrafterBatch container) {
        super(container);
        this.container = container;
    }

    @Override
    public void initGui() {
        super.initGui();

        // Button increments
        final int a = 1;
        final int b = 10;
        final int c = 100;
        final int d = 1000;

        this.buttonList.add(this.plus1 = new GuiButton(0, this.guiLeft + 20, this.guiTop + 32, 22, 20, "+" + a));
        this.buttonList.add(this.plus10 = new GuiButton(1, this.guiLeft + 48, this.guiTop + 32, 28, 20, "+" + b));
        this.buttonList.add(this.plus100 = new GuiButton(2, this.guiLeft + 82, this.guiTop + 32, 32, 20, "+" + c));
        this.buttonList.add(this.plus1000 = new GuiButton(3, this.guiLeft + 120, this.guiTop + 32, 38, 20, "+" + d));

        this.buttonList.add(this.minus1 = new GuiButton(4, this.guiLeft + 20, this.guiTop + 69, 22, 20, "-" + a));
        this.buttonList.add(this.minus10 = new GuiButton(5, this.guiLeft + 48, this.guiTop + 69, 28, 20, "-" + b));
        this.buttonList.add(this.minus100 = new GuiButton(6, this.guiLeft + 82, this.guiTop + 69, 32, 20, "-" + c));
        this.buttonList.add(this.minus1000 = new GuiButton(7, this.guiLeft + 120, this.guiTop + 69, 38, 20, "-" + d));

        // Back button
        TileAutoCrafter tile = container.getTile();
        this.buttonList.add(this.backBtn = new GuiTabButton(
            this.guiLeft + 154,
            this.guiTop,
            new ItemStack(tile.getBlockType()),
            I18n.format("gui.ae2powertools.crafter.title"),
            this.itemRender
        ));

        // Number input field
        this.sizeField = new GuiTextField(0, this.fontRenderer, this.guiLeft + 62, this.guiTop + 57, 59, this.fontRenderer.FONT_HEIGHT);
        this.sizeField.setEnableBackgroundDrawing(false);
        this.sizeField.setMaxStringLength(16);
        this.sizeField.setTextColor(0xFFFFFF);
        this.sizeField.setVisible(true);
        // Don't focus initially - wait for server sync to populate the value first
        this.sizeField.setFocused(false);
        // Don't set text here - wait for drawFG to receive synced value
        this.sizeField.setText("");
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.fontRenderer.drawString(I18n.format("gui.ae2powertools.crafter.batch.title"), 8, 6, 0x404040);

        // Only update text field from synced value when:
        // 1. Initial state (lastSyncedValue == -1) - populate with server value
        // 2. Server value changed AND text field is not focused - external change
        // This prevents flickering during user input by not overwriting mid-edit
        int serverValue = container.batchSize;
        if (lastSyncedValue == -1) {
            // Initial sync - populate the field
            this.sizeField.setText(String.valueOf(serverValue));
            this.lastSyncedValue = serverValue;
        } else if (serverValue != lastSyncedValue && !this.sizeField.isFocused()) {
            // Server value changed externally while not editing
            this.sizeField.setText(String.valueOf(serverValue));
            this.lastSyncedValue = serverValue;
        }
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
        // Use AE2's priority texture as base
        this.bindTexture("guis/priority.png");
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, this.ySize);

        this.sizeField.drawTextBox();
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

        final boolean isPlus = btn == this.plus1 || btn == this.plus10 || btn == this.plus100 || btn == this.plus1000;
        final boolean isMinus = btn == this.minus1 || btn == this.minus10 || btn == this.minus100 || btn == this.minus1000;

        if (isPlus || isMinus) this.addQty(this.getButtonQty(btn));
    }

    private int getButtonQty(GuiButton btn) {
        if (btn == this.plus1) return 1;
        if (btn == this.plus10) return 10;
        if (btn == this.plus100) return 100;
        if (btn == this.plus1000) return 1000;

        if (btn == this.minus1) return -1;
        if (btn == this.minus10) return -10;
        if (btn == this.minus100) return -100;
        if (btn == this.minus1000) return -1000;

        return 0;
    }

    private void addQty(int delta) {
        try {
            String out = this.sizeField.getText();

            // Remove leading zeros
            while (out.startsWith("0") && out.length() > 1) out = out.substring(1);

            if (out.isEmpty()) out = "1";

            long parsed = Long.parseLong(out);
            parsed += delta;
            int result = (int) Math.max(TileAutoCrafter.MIN_BATCH_SIZE, Math.min(Integer.MAX_VALUE, parsed));

            this.sizeField.setText(Integer.toString(result));
            PowerToolsNetwork.INSTANCE.sendToServer(new PacketSetCrafterBatch(container.getTile().getPos(), result));
        } catch (NumberFormatException e) {
            this.sizeField.setText("1");
        }
    }

    @Override
    protected void keyTyped(char character, int key) throws IOException {
        if (!this.checkHotbarKeys(key)) {
            if ((key == Keyboard.KEY_DELETE || key == Keyboard.KEY_RIGHT
                || key == Keyboard.KEY_LEFT || key == Keyboard.KEY_BACK
                || Character.isDigit(character)) && this.sizeField.textboxKeyTyped(character, key)) {

                String out = this.sizeField.getText();

                // Remove leading zeros
                boolean fixed = false;
                while (out.startsWith("0") && out.length() > 1) {
                    out = out.substring(1);
                    fixed = true;
                }

                if (fixed) this.sizeField.setText(out);

                if (out.isEmpty()) out = "1";

                try {
                    long parsed = Long.parseLong(out);
                    int value = (int) Math.max(TileAutoCrafter.MIN_BATCH_SIZE, Math.min(Integer.MAX_VALUE, parsed));
                    PowerToolsNetwork.INSTANCE.sendToServer(new PacketSetCrafterBatch(container.getTile().getPos(), value));
                } catch (NumberFormatException e) {
                    // Ignore invalid input
                }
            } else {
                super.keyTyped(character, key);
            }
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        this.sizeField.mouseClicked(mouseX, mouseY, mouseButton);
    }
}
