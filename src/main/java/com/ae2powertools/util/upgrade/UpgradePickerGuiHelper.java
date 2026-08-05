package com.ae2powertools.util.upgrade;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.client.config.GuiUtils;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.container.slot.AppEngSlot;

import com.ae2powertools.widgets.AbstractModalGui;


/**
 * Shared client-side behavior for passive upgrade-card slots and the click-to-pick modal.
 * GUIs provide the slot positions and the inventory implementation, while this helper owns
 * the rendering, hover logic, and common tooltip text.
 */
@SideOnly(Side.CLIENT)
public class UpgradePickerGuiHelper extends AbstractModalGui {

    private static final ResourceLocation PLAYER_INVENTORY_BACKGROUND = new ResourceLocation(
        "minecraft", "textures/gui/container/generic_54.png");
    private static final ResourceLocation AE2_STATES = new ResourceLocation(
        "appliedenergistics2", "textures/guis/states.png");
    private static final String COMMON_PREFIX = "ae2powertools.common.upgrade";


    final int UPGRADE_ICON = 13 * 16 + 15;
    final float ICON_OPACITY = 0.4f;  // AE2's default opacity for slot icons

    private static final int PICKER_TEXTURE_V1 = 0;
    private static final int PICKER_TEXTURE_V2 = 138;
    private static final int PICKER_WIDTH = 176;
    private static final int PICKER_HEIGHT_HEADER = 17;
    private static final int PICKER_HEIGHT_BODY = 84;
    private static final int PICKER_HEIGHT = PICKER_HEIGHT_HEADER + PICKER_HEIGHT_BODY;
    private static final int PICKER_TITLE_Y = 6;
    private static final int PICKER_SLOT_X = 8;
    private static final int PICKER_SLOT_Y = PICKER_HEIGHT_HEADER + 2;

    private static final int SLOT_SPACING = 18;
    private static final int SLOT_SIZE = 16;
    private static final int SLOT_FRAME_SIZE = 18;
    private static final int SLOT_FRAME_OFFSET = 1;
    private static final int HOTBAR_OFFSET = 4;

    private static final int PLAYER_INV_COLS = 9;
    private static final int PLAYER_INV_ROWS = 4;
    private static final int HOTBAR_ROW = 3;
    private static final int PLAYER_INV_SLOT_COUNT = PLAYER_INV_COLS * PLAYER_INV_ROWS;

    private static final int DISABLED_SLOT_OVERLAY = 0xAA555555;
    private static final int HOVER_OVERLAY = 0x40FFFFFF;

    private int pickerLeft;
    private int pickerTop;
    private int targetUpgradeSlot = -1;

    public UpgradePickerGuiHelper() {
        super(PICKER_WIDTH, PICKER_HEIGHT);
    }

    public void centerIn(int screenWidth, int screenHeight) {
        pickerLeft = (screenWidth - PICKER_WIDTH) / 2;
        pickerTop = (screenHeight - PICKER_HEIGHT) / 2;
        super.setPosition(pickerLeft, pickerTop);
    }

    public void open(int slotIndex) {
        super.open();
        targetUpgradeSlot = slotIndex;
    }

    @Override
    public void close() {
        super.close();
        targetUpgradeSlot = -1;
    }

    public int getTargetUpgradeSlot() {
        return targetUpgradeSlot;
    }

    public int getUpgradeSlotAt(int mouseX, int mouseY, int guiLeft, int guiTop, List<AppEngSlot> slots) {
        for (int index = 0; index < slots.size(); index++) {
            AppEngSlot slot = slots.get(index);
            int frameX = guiLeft + slot.xPos - SLOT_FRAME_OFFSET;
            int frameY = guiTop + slot.yPos - SLOT_FRAME_OFFSET;

            if (mouseX >= frameX && mouseX < frameX + SLOT_FRAME_SIZE
                    && mouseY >= frameY && mouseY < frameY + SLOT_FRAME_SIZE) {
                return index;
            }
        }

        return -1;
    }

    public boolean isUpgradeSlotRegion(List<AppEngSlot> slots, int rectX, int rectY, int rectWidth, int rectHeight) {
        if (rectWidth != SLOT_SIZE || rectHeight != SLOT_SIZE) return false;

        for (AppEngSlot slot : slots) {
            if (slot.xPos == rectX && slot.yPos == rectY) return true;
        }

        return false;
    }

    public void drawUpgradeSlotIcons(Minecraft mc, int guiLeft, int guiTop, List<AppEngSlot> slots) {
        mc.getTextureManager().bindTexture(AE2_STATES);

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.color(1.0F, 1.0F, 1.0F, ICON_OPACITY);

        for (AppEngSlot slot : slots) {
            if (slot.getHasStack()) continue;
     
            int uv_x = UPGRADE_ICON % 16;
            int uv_y = UPGRADE_ICON / 16;
            int iconX = guiLeft + slot.xPos;
            int iconY = guiTop + slot.yPos;

            drawTexturedModalRect(iconX, iconY, uv_x * 16, uv_y * 16, 16, 16);
        }

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }


    public void drawUpgradeSlotHighlight(int mouseX, int mouseY, int guiLeft, int guiTop,
            List<AppEngSlot> slots) {
        int slotIndex = getUpgradeSlotAt(mouseX, mouseY, guiLeft, guiTop, slots);
        if (slotIndex < 0) return;

        AppEngSlot slot = slots.get(slotIndex);
        int frameX = guiLeft + slot.xPos - SLOT_FRAME_OFFSET;
        int frameY = guiTop + slot.yPos - SLOT_FRAME_OFFSET;

        prepareFlatGuiOverlay();
        drawRect(frameX, frameY, frameX + SLOT_FRAME_SIZE, frameY + SLOT_FRAME_SIZE, HOVER_OVERLAY);

        GlStateManager.enableDepth();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public void drawUpgradeSlotTooltip(
            ISelectableUpgradeInventory inventory,
            List<AppEngSlot> slots,
            int guiLeft,
            int guiTop,
            int mouseX,
            int mouseY,
            int screenWidth,
            int screenHeight,
            FontRenderer fontRenderer) {
        if (inventory == null) return;

        int slotIndex = getUpgradeSlotAt(mouseX, mouseY, guiLeft, guiTop, slots);
        if (slotIndex < 0) return;

        ItemStack stack = inventory.getStackInSlot(slotIndex);
        List<String> tooltip = stack.isEmpty()
            ? buildEmptyUpgradeSlotTooltip(inventory)
            : buildUpgradeCardTooltip(inventory, stack, true);
        if (tooltip.isEmpty()) return;

        GuiUtils.drawHoveringText(tooltip, mouseX, mouseY, screenWidth, screenHeight, -1, fontRenderer);
    }

    private int getPickerSlotRowOffset(int displaySlot) {
        int row = displaySlot / PLAYER_INV_COLS;
        int y = row * SLOT_SPACING;

        // The hotbar row has a small offset
        if (row == HOTBAR_ROW) return y + HOTBAR_OFFSET;

        return y;
    }

    public void drawPickerModal(
            Minecraft mc,
            ISelectableUpgradeInventory inventory,
            InventoryPlayer playerInventory,
            int mouseX,
            int mouseY) {
        prepareFlatGuiOverlay();

        mc.getTextureManager().bindTexture(PLAYER_INVENTORY_BACKGROUND);
        // Header
        drawTexturedModalRect(pickerLeft, pickerTop, 0, PICKER_TEXTURE_V1, PICKER_WIDTH, PICKER_HEIGHT_HEADER);
        // Body
        drawTexturedModalRect(pickerLeft, pickerTop + PICKER_HEIGHT_HEADER, 0, PICKER_TEXTURE_V2,
            PICKER_WIDTH, PICKER_HEIGHT_BODY);

        String title = I18n.format(COMMON_PREFIX + ".title");
        FontRenderer fontRenderer = mc.fontRenderer;
        int titleX = pickerLeft + (PICKER_WIDTH - fontRenderer.getStringWidth(title)) / 2;
        fontRenderer.drawString(title, titleX, pickerTop + PICKER_TITLE_Y, 0x404040);

        int hoveredPlayerSlot = getPickerPlayerSlotAt(mouseX, mouseY);
        for (int displaySlot = 0; displaySlot < PLAYER_INV_SLOT_COUNT; displaySlot++) {
            int playerSlot = toPlayerInventorySlot(displaySlot);
            int slotX = pickerLeft + PICKER_SLOT_X + (displaySlot % PLAYER_INV_COLS) * SLOT_SPACING;
            int slotY = pickerTop + PICKER_SLOT_Y + getPickerSlotRowOffset(displaySlot);

            ItemStack stack = playerInventory.mainInventory.get(playerSlot);
            if (!stack.isEmpty()) drawItemStack(mc, stack, slotX, slotY);

            boolean compatible = isSelectablePlayerSlot(inventory, playerInventory, playerSlot);
            if (!compatible) {
                drawRect(slotX, slotY, slotX + SLOT_SIZE, slotY + SLOT_SIZE, DISABLED_SLOT_OVERLAY);
            }

            if (compatible && hoveredPlayerSlot == playerSlot) {
                drawRect(slotX, slotY, slotX + SLOT_SIZE, slotY + SLOT_SIZE, HOVER_OVERLAY);
            }
        }

        GlStateManager.enableDepth();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public void drawPickerTooltip(
            Minecraft mc,
            ISelectableUpgradeInventory inventory,
            InventoryPlayer playerInventory,
            int mouseX,
            int mouseY,
            int screenWidth,
            int screenHeight) {
        if (inventory == null) return;

        int hoveredPlayerSlot = getPickerPlayerSlotAt(mouseX, mouseY);
        if (hoveredPlayerSlot < 0 || !isSelectablePlayerSlot(inventory, playerInventory, hoveredPlayerSlot)) return;

        ItemStack stack = playerInventory.mainInventory.get(hoveredPlayerSlot);
        List<String> tooltip = buildUpgradeCardTooltip(inventory, stack, false);
        if (tooltip.isEmpty()) return;

        GuiUtils.drawHoveringText(tooltip, mouseX, mouseY, screenWidth, screenHeight, -1, mc.fontRenderer);
    }

    public int handlePickerClick(
            int mouseX,
            int mouseY,
            int mouseButton,
            ISelectableUpgradeInventory inventory,
            InventoryPlayer playerInventory) {
        if (!isOpen()) return -1;
        if (super.mouseClicked(mouseX, mouseY)) return -1;

        if (mouseButton != 0 || inventory == null) return -1;

        int hoveredPlayerSlot = getPickerPlayerSlotAt(mouseX, mouseY);
        if (hoveredPlayerSlot < 0 || !isSelectablePlayerSlot(inventory, playerInventory, hoveredPlayerSlot)) return -1;

        return hoveredPlayerSlot;
    }

    private int getPickerPlayerSlotAt(int mouseX, int mouseY) {
        for (int displaySlot = 0; displaySlot < PLAYER_INV_SLOT_COUNT; displaySlot++) {
            int slotX = pickerLeft + PICKER_SLOT_X + (displaySlot % PLAYER_INV_COLS) * SLOT_SPACING;
            int slotY = pickerTop + PICKER_SLOT_Y + getPickerSlotRowOffset(displaySlot);

            if (mouseX >= slotX && mouseX < slotX + SLOT_SIZE && mouseY >= slotY && mouseY < slotY + SLOT_SIZE) {
                return toPlayerInventorySlot(displaySlot);
            }
        }

        return -1;
    }

    private boolean isSelectablePlayerSlot(ISelectableUpgradeInventory inventory, InventoryPlayer playerInventory, int playerSlot) {
        if (inventory == null || playerSlot < 0 || playerSlot >= playerInventory.mainInventory.size()) return false;
        if (targetUpgradeSlot < 0 || targetUpgradeSlot >= inventory.getSlots()) return false;

        return inventory.canInstallUpgrade(targetUpgradeSlot, playerInventory.mainInventory.get(playerSlot));
    }

    private List<String> buildEmptyUpgradeSlotTooltip(ISelectableUpgradeInventory inventory) {
        List<String> tooltip = new ArrayList<>();
        tooltip.add(I18n.format(COMMON_PREFIX + ".empty"));
        tooltip.add("");
        tooltip.add(TextFormatting.AQUA + I18n.format(COMMON_PREFIX + ".compatible"));

        for (UpgradeCardDefinition definition : inventory.getSupportedUpgradeCards()) {
            ItemStack preview = definition.getPreviewStack();
            if (preview.isEmpty()) continue;

            tooltip.add(TextFormatting.GRAY + "- " + preview.getDisplayName());
        }

        return tooltip;
    }

    private String getUpgradeDescriptionKey(ISelectableUpgradeInventory inventory, ItemStack stack) {
        UpgradeCardDefinition definition = inventory.findUpgradeDefinition(stack);
        if (definition == null) return "???";

        return inventory.getUpgradeTooltipPrefix() + ".upgrade." + definition.getTooltipSuffix() + ".description";
    }

    private List<String> buildUpgradeCardTooltip(ISelectableUpgradeInventory inventory, ItemStack stack, boolean includeActions) {
        List<String> tooltip = new ArrayList<>();
        if (stack.isEmpty()) return tooltip;

        tooltip.add(TextFormatting.AQUA + stack.getDisplayName());

        UpgradeCardDefinition definition = inventory.findUpgradeDefinition(stack);
        if (definition != null) {
            tooltip.add("");
            tooltip.add(TextFormatting.GRAY + I18n.format(getUpgradeDescriptionKey(inventory, stack)));
        }

        if (includeActions) {
            tooltip.add("");
            tooltip.add(TextFormatting.GRAY + I18n.format(COMMON_PREFIX + ".action.select"));
            tooltip.add(TextFormatting.GRAY + I18n.format(COMMON_PREFIX + ".action.remove"));
        }

        return tooltip;
    }

    private void drawItemStack(Minecraft mc, ItemStack stack, int x, int y) {
        if (stack.isEmpty()) return;

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.pushMatrix();
        RenderHelper.enableGUIStandardItemLighting();

        mc.getRenderItem().renderItemAndEffectIntoGUI(stack, x, y);
        mc.getRenderItem().renderItemOverlayIntoGUI(mc.fontRenderer, stack, x, y, null);

        RenderHelper.disableStandardItemLighting();
        GlStateManager.popMatrix();

        // Item rendering leaves depth and the last tint color enabled, which causes the
        // picker's per-slot overlays to render behind or tint later slots inconsistently.
        prepareFlatGuiOverlay();
    }

    private void prepareFlatGuiOverlay() {
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * As hotbar is slots 0-8 and main inventory is slots 9-35, we convert the display slots
     * to the player inventory slots so that the picker can work with the player's inventory.
     * @param displaySlot the slot index in the picker display (0-35)
     * @return the corresponding slot index in the player's inventory (0-35)
     */
    private static int toPlayerInventorySlot(int displaySlot) {
        return displaySlot < 27 ? displaySlot + 9 : displaySlot - 27;
    }
}
