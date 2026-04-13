package com.ae2powertools.features.locator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.lwjgl.input.Mouse;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.util.ReadableNumberConverter;

import com.ae2powertools.client.PowerToolsClientConfig;
import com.ae2powertools.features.locator.LocatorClientState.ComponentLocationClient;
import com.ae2powertools.features.locator.LocatorClientState.ComponentTypeClient;
import com.ae2powertools.network.PowerToolsNetwork;


/**
 * GUI for the Network Component Locator.
 * Two views in one screen:
 * - Grid view: shows all component types in a 5x4 grid (like AE2 Network Tool's networkstatus.png)
 * - Detail view: shows all locations of a selected component type in a scrollable list
 *
 * Uses AE2's networkstatus.png as the background texture.
 * Arrows texture from ae2powertools for the back button.
 */
@SideOnly(Side.CLIENT)
public class GuiComponentLocator extends GuiScreen {

    // ========== Texture Resources ==========
    private static final ResourceLocation NETWORK_STATUS_TEXTURE =
        new ResourceLocation("appliedenergistics2", "textures/guis/networkstatus.png");
    private static final ResourceLocation ARROWS_TEXTURE =
        new ResourceLocation("ae2powertools", "textures/guis/arrows.png");
    private static final ResourceLocation AE2_STATES_TEXTURE =
        new ResourceLocation("appliedenergistics2", "textures/guis/states.png");
    private static final ResourceLocation SCROLLBAR_TEXTURE =
        new ResourceLocation("minecraft", "textures/gui/container/creative_inventory/tabs.png");

    // ========== Grid View Layout (matching AE2 Network Tool) ==========
    // The networkstatus.png texture is drawn at (guiLeft, guiTop) with xSize=195, ySize=153
    private static final int X_SIZE = 195;
    private static final int Y_SIZE = 153;

    // Grid parameters: 5 columns x 4 rows (small view)
    private static final int GRID_COLS = 5;
    private static final int GRID_ROWS = 4;
    private static final int SECTION_LENGTH = 31; // Column spacing (px)
    private static final int ROW_HEIGHT = 18;     // Row spacing (px)

    // ========== Tall View Constants ==========
    // networkstatus.png layout: header=0..42, grid row slice=42..60 (18px), footer=114..153 (39px)
    private static final int HEADER_HEIGHT = 42;          // Top section of texture (title + info)
    private static final int FOOTER_HEIGHT = 39;          // Bottom section of texture (hints area)
    private static final int FOOTER_TEX_Y = 114;          // Y offset of footer in texture
    private static final int TALL_MARGIN = 10;            // Margin from screen edges
    private static final int STYLE_BUTTON_SIZE = 16;      // AE2 states.png button size
    private static final int GRID_X_OFFSET = 14;  // Grid start X inside GUI (matched to AE2's 14)
    private static final int GRID_Y_OFFSET = 41;  // Grid start Y inside GUI (matched to AE2's 41)

    // ========== Detail View Layout ==========
    private static final int DETAIL_ROW_HEIGHT = 14;
    private static final int DETAIL_LIST_TOP = 40;        // Y offset where list starts
    private static final int SCROLLBAR_WIDTH = 12;        // Vanilla scrollbar width
    private static final int SCROLLBAR_THUMB_HEIGHT = 15; // Vanilla scrollbar thumb height
    private static final int SCROLLBAR_X = 175;           // X offset of scrollbar inside GUI (matched to AE2)

    // ========== Colors (AE2 / DiskTerminal style) ==========
    private static final int COLOR_SELECTION = 0x405599DD;         // Light blue selection bg
    private static final int COLOR_HOVER = 0x40FFFFFF;             // White hover highlight
    private static final int COLOR_TEXT = 0x000000;                // Dark text (AE2 standard)
    private static final int COLOR_TEXT_BLACK = 0x000000;          // Black text (for selection)
    private static final int COLOR_TEXT_WHITE = 0xFFFFFF;          // White text (for buttons)
    private static final int COLOR_TEXT_DIM = 0x808080;            // Dimmed text

    // ========== Arrow Button Constants ==========
    // arrows.png is 24x24: left column = left arrow, right column = right arrow
    // top row = normal, bottom row = hovered
    private static final int ARROW_SIZE = 12;

    // ========== Select/Clear All Buttons (vanilla style, hardcoded positions) ==========
    private static final int ACTION_BUTTON_HEIGHT = 14;
    private static final int ACTION_BUTTON_WIDTH = 79;    // Each button width (hardcoded)
    private static final int ACTION_BUTTON_GAP = 4;       // Gap between buttons
    private static final int ACTION_BUTTON_Y_OFFSET = 12; // Y offset from footer top

    // ========== State ==========
    private int guiLeft;
    private int guiTop;
    private int gridScrollRow = 0;        // Scroll offset in grid view (in rows)
    private int detailScrollOffset = 0;   // Scroll offset in detail view (in rows)
    private int hoveredGridSlot = -1;     // Which grid slot is hovered (-1 = none)
    private int hoveredDetailRow = -1;    // Which detail row is hovered (-1 = none)
    private boolean hoveredBackArrow = false;

    // Tall view state
    private boolean useTallView;
    private int currentXSize;
    private int currentYSize;
    private int currentGridRows;          // Number of grid rows visible (4 in small, dynamic in tall)
    private int styleButtonX, styleButtonY;
    private boolean styleButtonHovered = false;
    private int subnetButtonX, subnetButtonY;
    private boolean subnetButtonHovered = false;

    // Select/Clear all button hover states
    private boolean selectAllButtonHovered = false;
    private boolean clearAllButtonHovered = false;

    // Drag scrolling state
    private boolean isDraggingGridScrollbar = false;
    private boolean isDraggingDetailScrollbar = false;

    // Cache sorted locations for detail view
    private List<ComponentLocationClient> sortedLocations = null;
    // Original index mapping: sortedLocations[i] came from type.locations[originalIndices[i]]
    private List<Integer> originalIndices = null;

    public GuiComponentLocator() {
        this.useTallView = PowerToolsClientConfig.locator.isUseTallView();
    }

    @Override
    public void initGui() {
        super.initGui();

        if (useTallView) {
            // Calculate how many grid rows fit on screen
            int availableHeight = this.height - TALL_MARGIN * 2;
            int contentHeight = availableHeight - HEADER_HEIGHT - FOOTER_HEIGHT;
            currentGridRows = Math.max(GRID_ROWS, contentHeight / ROW_HEIGHT);
            currentYSize = HEADER_HEIGHT + currentGridRows * ROW_HEIGHT + FOOTER_HEIGHT;
            currentXSize = X_SIZE;
        } else {
            currentGridRows = GRID_ROWS;
            currentXSize = X_SIZE;
            currentYSize = Y_SIZE;
        }

        guiLeft = (width - currentXSize) / 2;
        guiTop = (height - currentYSize) / 2;

        // Style toggle button position (left of GUI, aligned with header)
        styleButtonX = guiLeft - STYLE_BUTTON_SIZE - 2;
        styleButtonY = guiTop + 6;

        // Subnet toggle button position (below style button)
        subnetButtonX = guiLeft - STYLE_BUTTON_SIZE - 2;
        subnetButtonY = styleButtonY + STYLE_BUTTON_SIZE + 2;

        gridScrollRow = 0;
        hoveredGridSlot = -1;
        hoveredDetailRow = -1;
        hoveredBackArrow = false;
        styleButtonHovered = false;
        subnetButtonHovered = false;
        selectAllButtonHovered = false;
        clearAllButtonHovered = false;

        // Invalidate sorted locations cache on init
        sortedLocations = null;
        originalIndices = null;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    // ========== Drawing ==========

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();

        // Draw the networkstatus.png background
        mc.getTextureManager().bindTexture(NETWORK_STATUS_TEXTURE);

        if (useTallView) {
            // Tall view: draw header, repeated middle slices, then footer
            // Header (top 42px of texture)
            drawTexturedModalRect(guiLeft, guiTop, 0, 0, currentXSize, HEADER_HEIGHT);

            // Repeated grid row slices
            for (int row = 0; row < currentGridRows; row++) {
                int y = guiTop + HEADER_HEIGHT + row * ROW_HEIGHT;
                // Draw one row slice from the texture (using first grid row at y=42)
                drawTexturedModalRect(guiLeft, y, 0, HEADER_HEIGHT, currentXSize, ROW_HEIGHT);
            }

            // Footer (bottom section of texture)
            int footerY = guiTop + HEADER_HEIGHT + currentGridRows * ROW_HEIGHT;
            drawTexturedModalRect(guiLeft, footerY, 0, FOOTER_TEX_Y, currentXSize, FOOTER_HEIGHT);
        } else {
            // Small view: draw the whole texture as-is
            drawTexturedModalRect(guiLeft, guiTop, 0, 0, currentXSize, currentYSize);
        }

        // Draw style toggle button
        drawStyleButton(mouseX, mouseY);

        // Draw subnet toggle button
        drawSubnetButton(mouseX, mouseY);

        if (LocatorClientState.isInDetailView()) {
            drawDetailView(mouseX, mouseY);
        } else {
            drawGridView(mouseX, mouseY);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);

        // Draw tooltip last (on top of everything)
        if (LocatorClientState.isInDetailView()) {
            drawDetailTooltip(mouseX, mouseY);
        } else {
            drawGridTooltip(mouseX, mouseY);
        }

        // Style button tooltip (after other tooltips so it draws on top)
        drawStyleButtonTooltip(mouseX, mouseY);
        drawSubnetButtonTooltip(mouseX, mouseY);
    }

    // ========== Style Toggle Button ==========

    /**
     * Draw the style toggle button (small/tall) to the left of the GUI.
     * Uses AE2's states.png for the button frame and icon, following the maintainer pattern.
     */
    private void drawStyleButton(int mouseX, int mouseY) {
        styleButtonHovered = mouseX >= styleButtonX && mouseX < styleButtonX + STYLE_BUTTON_SIZE
            && mouseY >= styleButtonY && mouseY < styleButtonY + STYLE_BUTTON_SIZE;

        mc.getTextureManager().bindTexture(AE2_STATES_TEXTURE);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        // Standard AE2 button frame (bottom-right cell of the 16x16 grid in states.png)
        drawTexturedModalRect(styleButtonX, styleButtonY, 240, 240, STYLE_BUTTON_SIZE, STYLE_BUTTON_SIZE);

        // Terminal style icon: row 13 in states.png, column 0 = tall, column 1 = compact
        int iconU = useTallView ? 0 : 16;
        int iconV = 13 * 16;
        drawTexturedModalRect(styleButtonX, styleButtonY, iconU, iconV, STYLE_BUTTON_SIZE, STYLE_BUTTON_SIZE);

        if (styleButtonHovered) {
            drawRect(styleButtonX + 1, styleButtonY + 1,
                styleButtonX + STYLE_BUTTON_SIZE - 1, styleButtonY + STYLE_BUTTON_SIZE - 1,
                0x40FFFFFF);
        }
    }

    private void drawStyleButtonTooltip(int mouseX, int mouseY) {
        if (!styleButtonHovered) return;

        List<String> tooltip = new ArrayList<>();
        tooltip.add(I18n.format("gui.ae2powertools.locator.style.title"));
        if (useTallView) {
            tooltip.add("§7" + I18n.format("gui.ae2powertools.locator.style.tall") + "§r");
        } else {
            tooltip.add("§7" + I18n.format("gui.ae2powertools.locator.style.small") + "§r");
        }
        tooltip.add("§7" + I18n.format("gui.ae2powertools.locator.style.click_toggle") + "§r");

        drawHoveringText(tooltip, mouseX, mouseY);
    }

    // ========== Subnet Toggle Button ==========

    /**
     * Draw the subnet scan toggle button below the style button.
     * Uses a colored indicator to show whether subnet scanning is enabled.
     */
    private void drawSubnetButton(int mouseX, int mouseY) {
        subnetButtonHovered = mouseX >= subnetButtonX && mouseX < subnetButtonX + STYLE_BUTTON_SIZE
            && mouseY >= subnetButtonY && mouseY < subnetButtonY + STYLE_BUTTON_SIZE;

        boolean subnetEnabled = LocatorClientState.isSubnetScanEnabled();

        mc.getTextureManager().bindTexture(AE2_STATES_TEXTURE);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        // AE2 button frame
        drawTexturedModalRect(subnetButtonX, subnetButtonY, 240, 240, STYLE_BUTTON_SIZE, STYLE_BUTTON_SIZE);

        // Use wireless icon from states.png: row 0, column 5 (wireless/antenna icon)
        // When enabled, draw a green tint; when disabled, draw red tint
        int iconU = 5 * 16;
        int iconV = 0;
        drawTexturedModalRect(subnetButtonX, subnetButtonY, iconU, iconV, STYLE_BUTTON_SIZE, STYLE_BUTTON_SIZE);

        // Color overlay to indicate state
        int stateColor = subnetEnabled ? 0x3000FF00 : 0x30FF0000;
        drawRect(subnetButtonX + 1, subnetButtonY + 1,
            subnetButtonX + STYLE_BUTTON_SIZE - 1, subnetButtonY + STYLE_BUTTON_SIZE - 1,
            stateColor);

        if (subnetButtonHovered) {
            drawRect(subnetButtonX + 1, subnetButtonY + 1,
                subnetButtonX + STYLE_BUTTON_SIZE - 1, subnetButtonY + STYLE_BUTTON_SIZE - 1,
                0x40FFFFFF);
        }
    }

    private void drawSubnetButtonTooltip(int mouseX, int mouseY) {
        if (!subnetButtonHovered) return;

        boolean subnetEnabled = LocatorClientState.isSubnetScanEnabled();
        List<String> tooltip = new ArrayList<>();
        tooltip.add(I18n.format("gui.ae2powertools.locator.subnet.title"));
        if (subnetEnabled) {
            tooltip.add("§a" + I18n.format("gui.ae2powertools.locator.subnet.enabled") + "§r");
        } else {
            tooltip.add("§c" + I18n.format("gui.ae2powertools.locator.subnet.disabled") + "§r");
        }
        tooltip.add("§7" + I18n.format("gui.ae2powertools.locator.subnet.click_toggle") + "§r");
        tooltip.add("§7" + I18n.format("gui.ae2powertools.locator.subnet.hint") + "§r");

        drawHoveringText(tooltip, mouseX, mouseY);
    }

    /**
     * Toggle subnet scanning. Sends a packet to the server to persist the change in item NBT,
     * and updates local state for immediate visual feedback.
     */
    private void toggleSubnetScan() {
        LocatorClientState.toggleSubnetScanLocal();
        PowerToolsNetwork.INSTANCE.sendToServer(new PacketLocatorToggleSubnet());
    }

    /**
     * Toggles between small and tall view modes.
     */
    private void toggleViewStyle() {
        useTallView = !useTallView;
        PowerToolsClientConfig.locator.setUseTallView(useTallView);
        gridScrollRow = 0;
        detailScrollOffset = 0;
        initGui();
    }

    // ========== Grid View Drawing ==========

    private void drawGridView(int mouseX, int mouseY) {
        List<ComponentTypeClient> types = LocatorClientState.getComponentTypes();
        int totalNodes = LocatorClientState.getTotalNodes();

        // Title
        String title = I18n.format("gui.ae2powertools.locator.title");
        fontRenderer.drawString(title, guiLeft + 8, guiTop + 6, COLOR_TEXT);

        // Hints text where power info would be (lines at y=16, y=26)
        if (types.isEmpty()) {
            String hint = I18n.format("gui.ae2powertools.locator.hint_empty");
            fontRenderer.drawString(hint, guiLeft + 13, guiTop + 16, COLOR_TEXT);
        } else {
            String hint1 = I18n.format("gui.ae2powertools.locator.hint_components",
                ReadableNumberConverter.INSTANCE.toWideReadableForm(types.size()));
            String hint2 = I18n.format("gui.ae2powertools.locator.hint_nodes",
                ReadableNumberConverter.INSTANCE.toWideReadableForm(totalNodes));
            fontRenderer.drawString(hint1, guiLeft + 13, guiTop + 16, COLOR_TEXT);

            // Show subnet indicator (inline if enabled)
            if (LocatorClientState.isSubnetScanEnabled()) {
                String subnetHint = " " + I18n.format("gui.ae2powertools.locator.hint_subnets");
                fontRenderer.drawString(hint2 + subnetHint, guiLeft + 13, guiTop + 26, COLOR_TEXT);
            } else {
                fontRenderer.drawString(hint2, guiLeft + 13, guiTop + 26, COLOR_TEXT);
            }
        }

        // Bottom area hints (centered in footer)
        int footerTop = guiTop + currentYSize - FOOTER_HEIGHT;
        String hint3 = I18n.format("gui.ae2powertools.locator.hint_click");
        int hintWidth = fontRenderer.getStringWidth(hint3);
        int hintX = guiLeft + (currentXSize - hintWidth) / 2;
        fontRenderer.drawString(hint3, hintX, footerTop + 19, COLOR_TEXT);

        // Draw component grid items
        hoveredGridSlot = -1;
        int currentItemsPerPage = GRID_COLS * currentGridRows;
        int viewStart = gridScrollRow * GRID_COLS;
        int viewEnd = viewStart + currentItemsPerPage;

        int x = 0;
        int y = 0;

        for (int z = viewStart; z < Math.min(viewEnd, types.size()); z++) {
            ComponentTypeClient type = types.get(z);
            // Match AE2's positioning exactly: x * sectionLength + xo + sectionLength - 18
            int slotX = guiLeft + x * SECTION_LENGTH + GRID_X_OFFSET + SECTION_LENGTH - 18;
            int slotY = guiTop + y * ROW_HEIGHT + GRID_Y_OFFSET;

            // Check if this slot is hovered (using same bounds as AE2: minX+28, minY+20)
            if (mouseX >= slotX && mouseX < slotX + 28 && mouseY >= slotY && mouseY < slotY + 20) {
                hoveredGridSlot = z;
                // Draw selection highlight
                drawRect(slotX - 1, slotY - 1, slotX + 17, slotY + 17, COLOR_HOVER);
            }

            // Draw the item icon
            RenderHelper.enableGUIStandardItemLighting();
            itemRender.renderItemAndEffectIntoGUI(type.itemStack, slotX, slotY);
            RenderHelper.disableStandardItemLighting();

            // Draw count below the icon (half-scale, like AE2 Network Tool)
            // Use ReadableNumberConverter for large numbers
            String countStr = ReadableNumberConverter.INSTANCE.toSlimReadableForm(type.getCount());
            int selectedForType = LocatorClientState.getSelectedCountForTypeIndex(z);

            GlStateManager.pushMatrix();
            GlStateManager.scale(0.5f, 0.5f, 0.5f);

            // Base X position for count text (right-aligned to left of icon)
            int baseCountX = (int) ((x * SECTION_LENGTH + GRID_X_OFFSET + SECTION_LENGTH - 19) * 2);
            // Base Y position for count area (scaled coordinates)
            int baseCountY = (y * ROW_HEIGHT + GRID_Y_OFFSET + 6) * 2;
            int countWidth = fontRenderer.getStringWidth(countStr);

            if (selectedForType > 0) {
                // Split display: total on top, selected below in blue
                // Vertically center the two lines: shift up by 5 (half of 10px line spacing)
                int totalX = baseCountX - countWidth;
                int totalY = baseCountY - 5;
                fontRenderer.drawString(countStr, totalX + guiLeft * 2, totalY + guiTop * 2, COLOR_TEXT);

                String selectedStr = ReadableNumberConverter.INSTANCE.toSlimReadableForm(selectedForType);
                int selectedWidth = fontRenderer.getStringWidth(selectedStr);
                int selectedX = baseCountX - selectedWidth;
                int selectedY = baseCountY + 5;
                // Blue color for selected count (0x3366CC)
                fontRenderer.drawString(selectedStr, selectedX + guiLeft * 2, selectedY + guiTop * 2, 0x3366CC);
            } else {
                // Single count, right-aligned (same as split display)
                int countX = baseCountX - countWidth;
                fontRenderer.drawString(countStr, countX + guiLeft * 2, baseCountY + guiTop * 2, COLOR_TEXT);
            }

            GlStateManager.popMatrix();

            x++;
            if (x >= GRID_COLS) {
                y++;
                x = 0;
            }
        }

        // Draw scrollbar
        int totalRows = (types.size() + GRID_COLS - 1) / GRID_COLS;
        drawGridScrollbar(totalRows);
    }

    private void drawGridScrollbar(int totalRows) {
        int scrollbarX = guiLeft + SCROLLBAR_X;
        int scrollbarY = guiTop + GRID_Y_OFFSET - 2;
        int scrollbarHeight = currentGridRows * ROW_HEIGHT + 6;

        // Use vanilla scrollbar texture (like AE2's GuiScrollbar)
        mc.getTextureManager().bindTexture(SCROLLBAR_TEXTURE);
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);

        int maxScroll = Math.max(0, totalRows - currentGridRows);
        if (maxScroll == 0) {
            // Disabled scrollbar thumb (grayed out)
            drawTexturedModalRect(scrollbarX, scrollbarY, 232 + SCROLLBAR_WIDTH, 0, SCROLLBAR_WIDTH, SCROLLBAR_THUMB_HEIGHT);
        } else {
            // Active scrollbar thumb
            int thumbRange = scrollbarHeight - SCROLLBAR_THUMB_HEIGHT;
            int thumbY = scrollbarY + thumbRange * gridScrollRow / maxScroll;
            drawTexturedModalRect(scrollbarX, thumbY, 232, 0, SCROLLBAR_WIDTH, SCROLLBAR_THUMB_HEIGHT);
        }
    }

    // ========== Detail View Drawing ==========

    private void drawDetailView(int mouseX, int mouseY) {
        ComponentTypeClient type = LocatorClientState.getSelectedType();
        if (type == null) {
            LocatorClientState.backToGridKeepSelections();

            return;
        }

        // Rebuild sorted locations if needed
        if (sortedLocations == null) rebuildSortedLocations(type);

        // Draw back arrow (left side of title bar area)
        int arrowX = guiLeft + 4;
        int arrowY = guiTop + 4;
        hoveredBackArrow = mouseX >= arrowX && mouseX < arrowX + ARROW_SIZE
            && mouseY >= arrowY && mouseY < arrowY + ARROW_SIZE;

        mc.getTextureManager().bindTexture(ARROWS_TEXTURE);
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        // Left arrow: u=0, v=0 (normal) or v=12 (hovered)
        int arrowV = hoveredBackArrow ? ARROW_SIZE : 0;
        drawScaledCustomSizeModalRect(arrowX, arrowY, 0, arrowV, ARROW_SIZE, ARROW_SIZE,
            ARROW_SIZE, ARROW_SIZE, 24, 24);

        // Draw component icon and name next to back arrow (with truncation)
        int iconX = guiLeft + 20;
        int iconY = guiTop + 2;
        RenderHelper.enableGUIStandardItemLighting();
        itemRender.renderItemAndEffectIntoGUI(type.itemStack, iconX, iconY);
        RenderHelper.disableStandardItemLighting();

        String countSuffix = " (" + ReadableNumberConverter.INSTANCE.toSlimReadableForm(type.getCount()) + ")";
        int maxNameWidth = currentXSize - 50 - fontRenderer.getStringWidth(countSuffix);
        String componentName = truncateString(type.getDisplayName(), maxNameWidth) + countSuffix;
        fontRenderer.drawString(componentName, iconX + 20, guiTop + 6, COLOR_TEXT);

        // Subtitle: instruction hints (left aligned)
        String subtitle = I18n.format("gui.ae2powertools.locator.detail_hint");
        fontRenderer.drawString(subtitle, guiLeft + 13, guiTop + 26, COLOR_TEXT_DIM);

        // Summary of selected (right aligned, but on a different line area to avoid overlap)
        int selectedCount = 0;
        for (int i = 0; i < sortedLocations.size(); i++) {
            if (LocatorClientState.isLocationSelected(originalIndices.get(i))) {
                selectedCount++;
            }
        }

        if (selectedCount > 0) {
            String selectedStr = I18n.format("gui.ae2powertools.locator.selected_count",
                ReadableNumberConverter.INSTANCE.toSlimReadableForm(selectedCount));
            int selectedWidth = fontRenderer.getStringWidth(selectedStr);
            fontRenderer.drawString(selectedStr, guiLeft + currentXSize - selectedWidth - 10, guiTop + 16, COLOR_TEXT_DIM);
        }

        // Draw list of locations
        // List area: from DETAIL_LIST_TOP to footer start (not into footer)
        int listTop = guiTop + DETAIL_LIST_TOP;
        int footerTop = guiTop + currentYSize - FOOTER_HEIGHT;
        int listBottom = footerTop - 2;  // 2px margin before footer
        int listLeft = guiLeft + 9;
        int listRight = guiLeft + SCROLLBAR_X - 6;
        int visibleRows = (listBottom - listTop) / DETAIL_ROW_HEIGHT;

        // Clamp scroll
        int maxScroll = Math.max(0, sortedLocations.size() - visibleRows);
        detailScrollOffset = Math.max(0, Math.min(detailScrollOffset, maxScroll));

        hoveredDetailRow = -1;

        // Get player position for distance calculation
        BlockPos playerPos = mc.player != null ? mc.player.getPosition() : BlockPos.ORIGIN;

        for (int i = 0; i < visibleRows && (i + detailScrollOffset) < sortedLocations.size(); i++) {
            int dataIndex = i + detailScrollOffset;
            ComponentLocationClient loc = sortedLocations.get(dataIndex);
            int originalIdx = originalIndices.get(dataIndex);

            int rowY = listTop + i * DETAIL_ROW_HEIGHT;
            boolean isSelected = LocatorClientState.isLocationSelected(originalIdx);
            boolean isHovered = mouseX >= listLeft && mouseX < listRight
                && mouseY >= rowY && mouseY < rowY + DETAIL_ROW_HEIGHT;

            // Draw selection background (DiskTerminal style)
            if (isSelected) {
                drawRect(listLeft, rowY, listRight, rowY + DETAIL_ROW_HEIGHT - 1, COLOR_SELECTION);
            }
            if (isHovered) {
                hoveredDetailRow = dataIndex;
                drawRect(listLeft, rowY, listRight, rowY + DETAIL_ROW_HEIGHT - 1, COLOR_HOVER);
            }

            // Format: "[dim / x, y, z] - <distance>m"
            String coordStr = loc.getCoordString();
            double distance = loc.getDistanceFrom(playerPos);
            String distStr = formatDistance(distance);

            String displayStr = coordStr + " - " + distStr;

            // Use black text for both selected and unselected
            fontRenderer.drawString(displayStr, listLeft + 2, rowY + 3, COLOR_TEXT_BLACK);
        }

        // Draw scrollbar for detail list
        if (!sortedLocations.isEmpty()) {
            // empty locations is virtually impossible
            drawDetailScrollbar(visibleRows, sortedLocations.size());
        }

        // Draw Select All / Clear All buttons in the footer
        drawDetailFooterButtons(mouseX, mouseY, selectedCount);
    }

    /**
     * Draw Select All and Clear All buttons in the detail view footer using vanilla button style.
     */
    private void drawDetailFooterButtons(int mouseX, int mouseY, int selectedCount) {
        // Calculate button positions
        int footerTop = guiTop + currentYSize - FOOTER_HEIGHT;
        int buttonY = footerTop + ACTION_BUTTON_Y_OFFSET;
        int selectAllX = guiLeft + 8;
        int clearAllX = selectAllX + ACTION_BUTTON_WIDTH + ACTION_BUTTON_GAP;

        // Update button hover states
        selectAllButtonHovered = mouseX >= selectAllX && mouseX < selectAllX + ACTION_BUTTON_WIDTH
            && mouseY >= buttonY && mouseY < buttonY + ACTION_BUTTON_HEIGHT;
        clearAllButtonHovered = mouseX >= clearAllX && mouseX < clearAllX + ACTION_BUTTON_WIDTH
            && mouseY >= buttonY && mouseY < buttonY + ACTION_BUTTON_HEIGHT;

        // Draw Select All button using vanilla button texture
        String selectText = I18n.format("gui.ae2powertools.scanner.select_all");
        drawVanillaButton(selectAllX, buttonY, ACTION_BUTTON_WIDTH, ACTION_BUTTON_HEIGHT,
            selectText, selectAllButtonHovered);

        // Draw Clear All button using vanilla button texture
        String clearText = I18n.format("gui.ae2powertools.scanner.deselect_all");
        drawVanillaButton(clearAllX, buttonY, ACTION_BUTTON_WIDTH, ACTION_BUTTON_HEIGHT,
            clearText, clearAllButtonHovered);
    }

    /**
     * Draw a vanilla-style button using the widgets texture (like GuiButton).
     * Uses standard Minecraft button rendering with proper 9-slice.
     */
    private void drawVanillaButton(int x, int y, int width, int height, String text, boolean hovered) {
        mc.getTextureManager().bindTexture(new ResourceLocation("minecraft", "textures/gui/widgets.png"));
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);

        // Vanilla button texture layout in widgets.png:
        // y=46: disabled, y=66: normal, y=86: hovered
        // Each button is 200x20 pixels
        int texY = hovered ? 86 : 66;

        // Draw using vanilla's half-button approach (like GuiButton does)
        // Left half from texture start, right half from texture end
        int halfWidth = width / 2;

        // Left half of button
        drawTexturedModalRect(x, y, 0, texY, halfWidth, height);
        // Right half of button (from end of texture)
        drawTexturedModalRect(x + halfWidth, y, 200 - (width - halfWidth), texY, width - halfWidth, height);

        // Draw centered text with shadow (standard for buttons)
        int textX = x + (width - fontRenderer.getStringWidth(text)) / 2;
        int textY_ = y + (height - 8) / 2;
        fontRenderer.drawStringWithShadow(text, textX, textY_, hovered ? 0xFFFFA0 : COLOR_TEXT_WHITE);
    }

    private void drawDetailScrollbar(int visibleRows, int totalRows) {
        int scrollbarX = guiLeft + SCROLLBAR_X;
        int scrollbarY = guiTop + GRID_Y_OFFSET - 2;
        int scrollbarHeight = currentGridRows * ROW_HEIGHT + 6;

        // Use vanilla scrollbar texture (like AE2's GuiScrollbar)
        mc.getTextureManager().bindTexture(SCROLLBAR_TEXTURE);
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);

        int maxScroll = Math.max(0, totalRows - visibleRows);

        if (maxScroll <= 0) {
            // Disabled scrollbar - show thumb at top with disabled texture
            drawTexturedModalRect(scrollbarX, scrollbarY, 232 + SCROLLBAR_WIDTH, 0, SCROLLBAR_WIDTH, SCROLLBAR_THUMB_HEIGHT);
        } else {
            // Active scrollbar - position thumb based on scroll offset
            int thumbRange = scrollbarHeight - SCROLLBAR_THUMB_HEIGHT;
            int thumbY = scrollbarY + thumbRange * detailScrollOffset / maxScroll;
            drawTexturedModalRect(scrollbarX, thumbY, 232, 0, SCROLLBAR_WIDTH, SCROLLBAR_THUMB_HEIGHT);
        }
    }

    /**
     * Truncate a string to fit within maxWidth, adding "..." if needed.
     */
    private String truncateString(String str, int maxWidth) {
        if (fontRenderer.getStringWidth(str) <= maxWidth) return str;

        String ellipsis = "...";
        int ellipsisWidth = fontRenderer.getStringWidth(ellipsis);

        while (str.length() > 0 && fontRenderer.getStringWidth(str) + ellipsisWidth > maxWidth) {
            str = str.substring(0, str.length() - 1);
        }

        return str + ellipsis;
    }

    // ========== Tooltips ==========

    private void drawGridTooltip(int mouseX, int mouseY) {
        if (hoveredGridSlot < 0) return;

        List<ComponentTypeClient> types = LocatorClientState.getComponentTypes();
        if (hoveredGridSlot >= types.size()) return;

        ComponentTypeClient type = types.get(hoveredGridSlot);
        List<String> tooltip = new ArrayList<>();
        tooltip.add(type.getDisplayName());
        tooltip.add(I18n.format("gui.ae2powertools.locator.tooltip_count",
            ReadableNumberConverter.INSTANCE.toWideReadableForm(type.getCount())));

        int count = LocatorClientState.getSelectedCountForType(type);
        if (count > 0) {
            tooltip.add(I18n.format("gui.ae2powertools.locator.selected_count",
                ReadableNumberConverter.INSTANCE.toWideReadableForm(count)));
        }

        drawHoveringText(tooltip, mouseX, mouseY);
    }

    private void drawDetailTooltip(int mouseX, int mouseY) {
        if (hoveredBackArrow) {
            drawHoveringText(I18n.format("gui.ae2powertools.locator.back"), mouseX, mouseY);

            return;
        }

        if (hoveredDetailRow < 0 || sortedLocations == null || hoveredDetailRow >= sortedLocations.size()) return;

        ComponentLocationClient loc = sortedLocations.get(hoveredDetailRow);
        int originalIdx = originalIndices.get(hoveredDetailRow);

        List<String> tooltip = new ArrayList<>();
        tooltip.add(loc.getCoordString());
        tooltip.add(I18n.format("gui.ae2powertools.locator.dimension", loc.dimension));

        if (mc.player != null) {
            double distance = loc.getDistanceFrom(mc.player.getPosition());
            tooltip.add(formatDistance(distance));
        }

        boolean isSelected = LocatorClientState.isLocationSelected(originalIdx);
        tooltip.add(isSelected ?
            I18n.format("gui.ae2powertools.locator.click_deselect") :
            I18n.format("gui.ae2powertools.locator.click_select"));

        drawHoveringText(tooltip, mouseX, mouseY);
    }

    // ========== Input Handling ==========

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        // Style toggle button (always available)
        if (mouseButton == 0 && styleButtonHovered) {
            toggleViewStyle();

            return;
        }

        // Subnet toggle button (always available)
        if (mouseButton == 0 && subnetButtonHovered) {
            toggleSubnetScan();

            return;
        }

        // Check for scrollbar click to initiate dragging
        if (mouseButton == 0) {
            int scrollbarX = guiLeft + SCROLLBAR_X;
            if (mouseX >= scrollbarX && mouseX < scrollbarX + SCROLLBAR_WIDTH) {
                if (LocatorClientState.isInDetailView()) {
                    int scrollbarY = guiTop + GRID_Y_OFFSET - 2;
                    int scrollbarHeight = currentGridRows * ROW_HEIGHT + 6;
                    if (mouseY >= scrollbarY && mouseY < scrollbarY + scrollbarHeight) {
                        isDraggingDetailScrollbar = true;
                        updateDetailScrollFromMouse(mouseY);

                        return;
                    }
                } else {
                    // Grid scrollbar uses same bounds as drawGridScrollbar
                    int scrollbarY = guiTop + GRID_Y_OFFSET - 2;
                    int scrollbarHeight = currentGridRows * ROW_HEIGHT + 6;
                    if (mouseY >= scrollbarY && mouseY < scrollbarY + scrollbarHeight) {
                        isDraggingGridScrollbar = true;
                        updateGridScrollFromMouse(mouseY);

                        return;
                    }
                }
            }
        }

        if (LocatorClientState.isInDetailView()) {
            handleDetailClick(mouseX, mouseY, mouseButton);
        } else {
            handleGridClick(mouseX, mouseY, mouseButton);
        }

        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        isDraggingGridScrollbar = false;
        isDraggingDetailScrollbar = false;
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (isDraggingGridScrollbar) {
            updateGridScrollFromMouse(mouseY);
        } else if (isDraggingDetailScrollbar) {
            updateDetailScrollFromMouse(mouseY);
        }
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }

    /**
     * Update grid scroll position based on mouse Y coordinate.
     */
    private void updateGridScrollFromMouse(int mouseY) {
        List<ComponentTypeClient> types = LocatorClientState.getComponentTypes();
        int totalRows = (types.size() + GRID_COLS - 1) / GRID_COLS;
        int maxScroll = Math.max(0, totalRows - currentGridRows);
        if (maxScroll == 0) return;

        // Use same bounds as drawGridScrollbar: GRID_Y_OFFSET - 2, height + 6
        int scrollbarY = guiTop + GRID_Y_OFFSET - 2;
        int scrollbarHeight = currentGridRows * ROW_HEIGHT + 6;
        int thumbRange = scrollbarHeight - SCROLLBAR_THUMB_HEIGHT;

        // Calculate scroll position from mouse Y (thumb center)
        int relativeY = mouseY - scrollbarY - SCROLLBAR_THUMB_HEIGHT / 2;
        float scrollRatio = (float) relativeY / thumbRange;
        gridScrollRow = Math.max(0, Math.min(maxScroll, Math.round(scrollRatio * maxScroll)));
    }

    /**
     * Update detail scroll position based on mouse Y coordinate.
     */
    private void updateDetailScrollFromMouse(int mouseY) {
        if (sortedLocations == null) return;

        // Calculate visible rows from list area
        int listTop = guiTop + DETAIL_LIST_TOP;
        int footerTop = guiTop + currentYSize - FOOTER_HEIGHT;
        int listBottom = footerTop - 2;
        int visibleRows = (listBottom - listTop) / DETAIL_ROW_HEIGHT;
        int maxScroll = Math.max(0, sortedLocations.size() - visibleRows);
        if (maxScroll == 0) return;

        int scrollbarY = guiTop + GRID_Y_OFFSET - 2;
        int scrollbarHeight = currentGridRows * ROW_HEIGHT + 6;
        int thumbRange = scrollbarHeight - SCROLLBAR_THUMB_HEIGHT;

        // Calculate scroll position from mouse Y (thumb center)
        int relativeY = mouseY - scrollbarY - SCROLLBAR_THUMB_HEIGHT / 2;
        float scrollRatio = (float) relativeY / thumbRange;
        detailScrollOffset = Math.max(0, Math.min(maxScroll, Math.round(scrollRatio * maxScroll)));
    }

    private void handleGridClick(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton != 0) return;
        if (hoveredGridSlot < 0) return;

        List<ComponentTypeClient> types = LocatorClientState.getComponentTypes();
        if (hoveredGridSlot >= types.size()) return;

        // Select this component type and switch to detail view
        LocatorClientState.setSelectedTypeIndex(hoveredGridSlot);
        sortedLocations = null;  // Invalidate cache
        originalIndices = null;
        detailScrollOffset = 0;
    }

    private void handleDetailClick(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton != 0) return;

        // Back arrow
        if (hoveredBackArrow) {
            LocatorClientState.backToGridKeepSelections();
            sortedLocations = null;
            originalIndices = null;

            return;
        }

        // Select All button
        if (selectAllButtonHovered) {
            LocatorClientState.selectAll();

            return;
        }

        // Clear All button
        if (clearAllButtonHovered) {
            LocatorClientState.deselectAll();

            return;
        }

        // Location row toggle
        if (hoveredDetailRow >= 0 && sortedLocations != null && hoveredDetailRow < sortedLocations.size()) {
            int originalIdx = originalIndices.get(hoveredDetailRow);
            LocatorClientState.toggleLocationSelection(originalIdx);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        // Esc key
        if (keyCode == 1) {
            if (LocatorClientState.isInDetailView()) {
                // Go back to grid view instead of closing
                LocatorClientState.backToGridKeepSelections();
                sortedLocations = null;
                originalIndices = null;
            } else {
                // Close the GUI
                mc.displayGuiScreen(null);
            }

            return;
        }

        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();

        int scroll = Mouse.getEventDWheel();
        if (scroll == 0) return;

        // Scroll direction: positive = up, negative = down
        int direction = scroll > 0 ? -1 : 1;

        if (LocatorClientState.isInDetailView()) {
            // Scroll the detail list
            detailScrollOffset += direction;
            int listTop = guiTop + DETAIL_LIST_TOP;
            int footerTop = guiTop + currentYSize - FOOTER_HEIGHT;
            int listBottom = footerTop - 2;
            int visibleRows = (listBottom - listTop) / DETAIL_ROW_HEIGHT;
            int maxScroll = Math.max(0, (sortedLocations != null ? sortedLocations.size() : 0) - visibleRows);
            detailScrollOffset = Math.max(0, Math.min(detailScrollOffset, maxScroll));
        } else {
            // Scroll the grid
            List<ComponentTypeClient> types = LocatorClientState.getComponentTypes();
            int totalRows = (types.size() + GRID_COLS - 1) / GRID_COLS;
            int maxScroll = Math.max(0, totalRows - currentGridRows);
            gridScrollRow = Math.max(0, Math.min(gridScrollRow + direction, maxScroll));
        }
    }

    // ========== Helpers ==========

    /**
     * Rebuild the sorted locations cache and the original index mapping.
     * Locations are sorted by distance from the player.
     */
    private void rebuildSortedLocations(ComponentTypeClient type) {
        if (type == null) {
            sortedLocations = new ArrayList<>();
            originalIndices = new ArrayList<>();

            return;
        }

        BlockPos playerPos = mc.player != null ? mc.player.getPosition() : BlockPos.ORIGIN;

        // Build index pairs: (originalIndex, location)
        List<int[]> indexedLocations = new ArrayList<>();
        for (int i = 0; i < type.locations.size(); i++) indexedLocations.add(new int[]{i});

        // Sort by distance
        indexedLocations.sort((a, b) -> {
            double distA = type.locations.get(a[0]).getDistanceFrom(playerPos);
            double distB = type.locations.get(b[0]).getDistanceFrom(playerPos);

            return Double.compare(distA, distB);
        });

        sortedLocations = new ArrayList<>();
        originalIndices = new ArrayList<>();
        for (int[] entry : indexedLocations) {
            originalIndices.add(entry[0]);
            sortedLocations.add(type.locations.get(entry[0]));
        }
    }

    private static String formatDistance(double distance) {
        if (distance < 10) return String.format("%.1fm", distance);
        if (distance < 1000) return String.format("%.0fm", distance);

        return String.format("%.1fkm", distance / 1000.0);
    }
}
