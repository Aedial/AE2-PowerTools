package com.ae2powertools.features.crafter.pmt;

import java.util.List;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.client.config.GuiUtils;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.client.resources.I18n;

import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.util.ReadableNumberConverter;


/**
 * Renders the Pattern Multi-Tool panel in the GUI.
 *
 * NOTE:
 * This is a re-implementation of the PMT feature, as the original implementation
 * relies on AEBaseGui (which itself has annoying limitations), instead of offering
 * a simple container we can attach to anything.
 * 
 * Features:
 * - Draws the PMT panel background texture
 * - Draws additional columns based on installed capacity upgrades
 * - Draws pattern slot backgrounds (pattern icon for empty slots)
 * - Draws output count overlay in bottom-right corner (AE2 handles item rendering)
 * - Draws PMT buttons with tooltips
 */
@SideOnly(Side.CLIENT)
public class PMTRenderer {

    // Panel dimensions
    public static final int PMT_WIDTH = 86;
    public static final int PMT_HEIGHT = 198;

    // Slot layout
    private static final int SLOT_SIZE = 18;
    private static final int SLOT_MARGIN = 8;  // Margin from panel edge to first slot
    private static final int ROWS = 9;

    // Textures
    // NAE2's PMT panel texture
    private static final ResourceLocation PMT_TEXTURE = new ResourceLocation(
            "nae2", "textures/gui/pattern_multiplier_toolbox.png");
    // AE2's states.png for pattern icon
    private static final ResourceLocation AE2_STATES = new ResourceLocation(
            "appliedenergistics2", "textures/guis/states.png");

    // Pattern icon position in states.png: BACKGROUND_PATTERN = row 9, col 16
    private static final int PATTERN_ICON_U = 15 * 16;
    private static final int PATTERN_ICON_V = 8 * 16;

    // Opacity for empty slot icons (matches AE2 style)
    private static final float SLOT_ICON_OPACITY = 0.4f;

    private PMTRenderer() {}

    /**
     * Draws the PMT panel background.
     * 
     * @param gui The parent GUI
     * @param pmtManager The PMT manager (null if PMT not available)
     * @param guiLeft Left position of main GUI
     * @param guiTop Top position of main GUI
     * @param offsetX X offset for PMT panel relative to guiLeft
     * @param offsetY Y offset for PMT panel relative to guiTop
     */
    public static void drawBackground(GuiContainer gui, PMTManager pmtManager, int guiLeft, int guiTop, int offsetX, int offsetY) {
        if (pmtManager == null || !pmtManager.hasPMT()) return;

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        gui.mc.getTextureManager().bindTexture(PMT_TEXTURE);

        int drawX = guiLeft + offsetX;
        int drawY = guiTop + offsetY;

        // Draw main panel background
        gui.drawTexturedModalRect(drawX, drawY, 0, 0, PMT_WIDTH, PMT_HEIGHT);

        // Draw additional column backgrounds based on capacity upgrades
        int columns = pmtManager.getInstalledCapacityUpgrades();
        for (int i = 1; i <= columns; i++) {
            gui.drawTexturedModalRect(
                    drawX + SLOT_MARGIN + (i * SLOT_SIZE),
                    drawY + SLOT_MARGIN,
                    8, 8, 16, SLOT_SIZE * ROWS);
        }
    }

    /**
     * Draws pattern icons for empty PMT slots.
     * 
     * @param gui The parent GUI
     * @param pmtManager The PMT manager
     * @param guiLeft Left position of main GUI
     * @param guiTop Top position of main GUI
     */
    public static void drawSlotBackgrounds(GuiContainer gui, PMTManager pmtManager, int guiLeft, int guiTop) {
        if (pmtManager == null || !pmtManager.hasPMT()) return;

        // Draw pattern icons for empty enabled slots
        gui.mc.getTextureManager().bindTexture(AE2_STATES);
        GlStateManager.enableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, SLOT_ICON_OPACITY);

        for (Slot slot : gui.inventorySlots.inventorySlots) {
            if (!(slot instanceof PMTSlot)) continue;

            PMTSlot pmtSlot = (PMTSlot) slot;

            // Only draw background icon for enabled, empty slots
            if (!pmtSlot.isSlotEnabled()) continue;

            ItemStack stack = pmtSlot.getItemHandler().getStackInSlot(pmtSlot.getSlotIndex());
            if (!stack.isEmpty()) continue;

            int x = guiLeft + slot.xPos;
            int y = guiTop + slot.yPos;
            gui.drawTexturedModalRect(x, y, PATTERN_ICON_U, PATTERN_ICON_V, 16, 16);
        }

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableBlend();
    }

    /**
     * Draws overlays for PMT slots:
     * - Stack count (top-right): how many patterns are stacked in this slot
     * - Output count (bottom-right): how many items the pattern produces
     * 
     * Call this in drawGuiContainerForegroundLayer (coordinates are relative to GUI).
     * 
     * @param gui The parent GUI
     * @param pmtManager The PMT manager
     */
    public static void drawSlotOverlays(GuiContainer gui, PMTManager pmtManager) {
        if (pmtManager == null || !pmtManager.hasPMT()) return;

        FontRenderer fontRenderer = gui.mc.fontRenderer;

        for (Slot slot : gui.inventorySlots.inventorySlots) {
            if (!(slot instanceof PMTSlot)) continue;

            PMTSlot pmtSlot = (PMTSlot) slot;
            if (!pmtSlot.isSlotEnabled()) continue;

            ItemStack stack = pmtSlot.getItemHandler().getStackInSlot(pmtSlot.getSlotIndex());
            if (stack.isEmpty()) continue;

            // Draw stack count (top-right) - how many patterns are stacked
            int stackCount = stack.getCount();
            if (stackCount > 1) {
                drawStackCount(fontRenderer, slot.xPos, slot.yPos + 1, stackCount);
            }

            // Draw output count (bottom-right) - how many items the pattern produces
            long outputCount = pmtSlot.getOutputCount();
            if (outputCount > 1) {
                drawOutputCount(fontRenderer, slot.xPos, slot.yPos, outputCount);
            }
        }
    }

    /**
     * Draws the stack count in the top-right corner of a slot.
     * This shows how many patterns are stacked in the slot.
     * Uses half scale (0.5) for a smaller, less intrusive display.
     */
    private static void drawStackCount(FontRenderer fontRenderer, int x, int y, int count) {
        String countStr = String.valueOf(count);

        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.disableBlend();

        // Use half scale for the count text
        GlStateManager.pushMatrix();
        float scale = 0.5f;
        GlStateManager.scale(scale, scale, 1.0f);

        // Calculate position at half scale - top-right corner
        int textWidth = fontRenderer.getStringWidth(countStr);
        int textX = (int) ((x + 16 - textWidth * scale) / scale);
        int textY = (int) ((y - 1) / scale);

        // Draw shadow then text
        fontRenderer.drawString(countStr, textX + 1, textY + 1, 0x000000);
        fontRenderer.drawString(countStr, textX, textY, 0xFFFFFF);

        GlStateManager.popMatrix();

        GlStateManager.enableDepth();
        GlStateManager.enableLighting();
    }

    /**
     * Draws the output count in the bottom-right corner of a slot.
     * Uses AE2's readable number format for large numbers.
     * Uses half scale (0.5) for a smaller, less intrusive display.
     */
    private static void drawOutputCount(FontRenderer fontRenderer, int x, int y, long count) {
        String countStr;
        if (count >= 1000) {
            countStr = ReadableNumberConverter.INSTANCE.toSlimReadableForm(count);
        } else {
            countStr = String.valueOf(count);
        }

        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.disableBlend();

        // Use half scale for the count text
        GlStateManager.pushMatrix();
        float scale = 0.5f;
        GlStateManager.scale(scale, scale, 1.0f);

        // Calculate position at half scale (multiply coords by 2 to compensate for scale)
        int textWidth = fontRenderer.getStringWidth(countStr);
        int textX = (int) ((x + 16 - textWidth * scale) / scale);
        int textY = (int) ((y + 12) / scale);

        // Draw shadow then text
        fontRenderer.drawString(countStr, textX + 1, textY + 1, 0x000000);
        fontRenderer.drawString(countStr, textX, textY, 0xFFFFFF);

        GlStateManager.popMatrix();

        GlStateManager.enableDepth();
        GlStateManager.enableLighting();
    }

    /**
     * Draws the tooltip for a hovered PMT slot.
     * Shows the full pattern details (input -> output).
     * 
     * @param gui The parent GUI
     * @param slot The hovered PMT slot
     * @param mouseX Mouse X position
     * @param mouseY Mouse Y position
     * @param screenWidth Screen width
     * @param screenHeight Screen height
     */
    public static void drawSlotTooltip(GuiContainer gui, PMTSlot slot, int mouseX, int mouseY, int screenWidth, int screenHeight) {
        if (!slot.getHasStack()) return;

        // Get the actual pattern stack for the tooltip (not the display stack)
        ItemStack patternStack = slot.getStack();
        if (patternStack.isEmpty()) return;

        List<String> tooltip = patternStack.getTooltip(
                gui.mc.player,
                gui.mc.gameSettings.advancedItemTooltips
                        ? ITooltipFlag.TooltipFlags.ADVANCED
                        : ITooltipFlag.TooltipFlags.NORMAL);

        GuiUtils.drawHoveringText(tooltip, mouseX, mouseY, screenWidth, screenHeight, -1, gui.mc.fontRenderer);
    }

    /**
     * Checks if a pattern stack is a processing pattern (not craftable).
     * Processing patterns are incompatible with the AutoCrafter.
     * 
     * @param stack The item stack to check
     * @return true if this is a processing pattern
     */
    public static boolean isProcessingPattern(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (!(stack.getItem() instanceof ICraftingPatternItem)) return false;

        ICraftingPatternItem patternItem = (ICraftingPatternItem) stack.getItem();
        ICraftingPatternDetails details = patternItem.getPatternForItem(stack, null);
        if (details == null) return false;

        // isCraftable() returns true for crafting patterns, false for processing patterns
        return !details.isCraftable();
    }

    /**
     * Adds a processing pattern warning to a tooltip if the stack is a processing pattern.
     * Call this after getting the base tooltip to append the warning.
     * 
     * @param stack The item stack being tooltipped
     * @param tooltip The tooltip list to modify
     */
    public static void addProcessingPatternWarning(ItemStack stack, List<String> tooltip) {
        if (!isProcessingPattern(stack)) return;

        tooltip.add("");
        tooltip.add(TextFormatting.RED + I18n.format("gui.ae2powertools.crafter.warning.processing_pattern_title"));
        tooltip.add(TextFormatting.RED + I18n.format("gui.ae2powertools.crafter.warning.processing_pattern_desc"));
    }

    /**
     * Checks if the mouse is inside the PMT panel area.
     * 
     * @param mouseX Mouse X position
     * @param mouseY Mouse Y position
     * @param guiLeft Left position of main GUI
     * @param guiTop Top position of main GUI
     * @param offsetX X offset for PMT panel
     * @param offsetY Y offset for PMT panel
     * @return true if mouse is inside PMT panel
     */
    public static boolean isMouseInsidePanel(int mouseX, int mouseY, int guiLeft, int guiTop, int offsetX, int offsetY) {
        int panelX = guiLeft + offsetX;
        int panelY = guiTop + offsetY;
        return mouseX >= panelX && mouseX < panelX + PMT_WIDTH &&
               mouseY >= panelY && mouseY < panelY + PMT_HEIGHT;
    }
}
