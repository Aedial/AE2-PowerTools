package com.ae2powertools.features.scanner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.ae2powertools.features.scanner.ScannerClientState.ChunkLocationClient;
import com.ae2powertools.features.scanner.ScannerClientState.ChokeLocationClient;
import com.ae2powertools.features.scanner.ScannerClientState.LoopLocationClient;
import com.ae2powertools.features.scanner.ScannerClientState.MissingDeviceClient;
import com.ae2powertools.features.scanner.ScannerClientState.Tab;
import com.ae2powertools.network.PacketScannerCancel;
import com.ae2powertools.network.PowerToolsNetwork;


/**
 * GUI for displaying network health scan results with tabs and dimension categories.
 */
@SideOnly(Side.CLIENT)
public class GuiNetworkHealthScanner extends GuiScreen {

    // GUI dimensions (min values in pixels, max as screen percentage)
    private static final int MIN_GUI_WIDTH = 200;
    private static final float MAX_GUI_WIDTH_PERCENT = 0.75f;   // 75% of screen width
    private static final int MIN_GUI_HEIGHT = 120;
    private static final float MAX_GUI_HEIGHT_PERCENT = 0.80f;  // 80% of screen height
    private static final int TAB_SIZE = 24;           // Icon tabs are square
    private static final int HEADER_HEIGHT = 28;      // Title + buttons
    private static final int FOOTER_HEIGHT = 18;      // Status bar
    private static final int ROW_HEIGHT = 14;
    private static final int SCROLLBAR_WIDTH = 8;
    private static final int PADDING = 4;

    // Colors - AE2 inspired theme (purple/gray)
    private static final int COLOR_HEADER_BG = 0xC0101016;      // Dark purple-gray header
    private static final int COLOR_BG = 0xC0181820;             // Dark purple-gray background
    private static final int COLOR_BORDER = 0xFF8B8B9B;         // Light purple-gray border
    private static final int COLOR_CATEGORY_BG = 0xC0282838;    // Slightly lighter category
    private static final int COLOR_CATEGORY_TEXT = 0xFF4AC3FF;  // AE2 blue text
    private static final int COLOR_ROW_HOVER = 0x40FFFFFF;      // White hover highlight
    private static final int COLOR_ROW_SELECTED = 0x604AC3FF;   // Blue selected highlight
    private static final int COLOR_TEXT = 0xFFFFFFFF;           // White text
    private static final int COLOR_TEXT_DIM = 0xFFAAAAAA;       // Dimmed text
    private static final int COLOR_SCROLLBAR_BG = 0xFF181820;   // Dark scrollbar bg
    private static final int COLOR_SCROLLBAR_FG = 0xFF4AC3FF;   // AE2 blue scrollbar
    private static final int COLOR_TREE_LINE = 0xFF4A4A5A;      // Purple-gray tree lines
    private static final int COLOR_TAB_BG = 0xC0181820;         // Tab background
    private static final int COLOR_TAB_SELECTED = 0xC0282838;   // Selected tab
    private static final int COLOR_TAB_HOVER = 0xC0202030;      // Hovered tab

    // Dynamic dimensions
    private int guiWidth;
    private int guiHeight;
    private int guiLeft;
    private int guiTop;
    private int scrollOffset = 0;
    private int maxScroll = 0;
    private boolean isDraggingScrollbar = false;

    // Collapsed dimensions
    private final Map<String, Boolean> collapsedDimensions = new HashMap<>();

    // Cached display rows and max text width
    private List<DisplayRow> displayRows = null;
    private int maxTextWidth = 0;
    private int hoveredRowIndex = -1;
    private int hoveredTabIndex = -1;

    // Buttons
    private GuiButton selectAllButton;
    private GuiButton deselectAllButton;
    private GuiButton cancelButton;

    /**
     * Represents a row in the display list.
     */
    private static class DisplayRow {
        enum Type { CATEGORY, LOOP_ENTRY, CHUNK_ENTRY, MISSING_ENTRY, CHOKE_ENTRY }

        final Type type;
        final String text;
        final String dimensionKey;    // For categories
        final int locationIndex;       // For entries
        final LoopLocationClient loopLocation;
        final ChunkLocationClient chunkLocation;
        final MissingDeviceClient missingDevice;
        final ChokeLocationClient chokeLocation;
        final boolean isLastInCategory;

        // Category constructor
        DisplayRow(String dimensionKey, String text) {
            this.type = Type.CATEGORY;
            this.dimensionKey = dimensionKey;
            this.text = text;
            this.locationIndex = -1;
            this.loopLocation = null;
            this.chunkLocation = null;
            this.missingDevice = null;
            this.chokeLocation = null;
            this.isLastInCategory = false;
        }

        // Loop entry constructor
        DisplayRow(int index, LoopLocationClient location, String text, boolean isLastInCategory) {
            this.type = Type.LOOP_ENTRY;
            this.dimensionKey = null;
            this.text = text;
            this.locationIndex = index;
            this.loopLocation = location;
            this.chunkLocation = null;
            this.missingDevice = null;
            this.chokeLocation = null;
            this.isLastInCategory = isLastInCategory;
        }

        // Chunk entry constructor
        DisplayRow(int index, ChunkLocationClient location, String text, boolean isLastInCategory) {
            this.type = Type.CHUNK_ENTRY;
            this.dimensionKey = null;
            this.text = text;
            this.locationIndex = index;
            this.loopLocation = null;
            this.chunkLocation = location;
            this.missingDevice = null;
            this.chokeLocation = null;
            this.isLastInCategory = isLastInCategory;
        }

        // Choke entry constructor
        DisplayRow(int index, ChokeLocationClient location, String text, boolean isLastInCategory) {
            this.type = Type.CHOKE_ENTRY;
            this.dimensionKey = null;
            this.text = text;
            this.locationIndex = index;
            this.loopLocation = null;
            this.chunkLocation = null;
            this.missingDevice = null;
            this.chokeLocation = location;
            this.isLastInCategory = isLastInCategory;
        }

        // Missing device entry constructor
        DisplayRow(int index, MissingDeviceClient device, String text, boolean isLastInCategory) {
            this.type = Type.MISSING_ENTRY;
            this.dimensionKey = null;
            this.text = text;
            this.locationIndex = index;
            this.loopLocation = null;
            this.chunkLocation = null;
            this.missingDevice = device;
            this.chokeLocation = null;
            this.isLastInCategory = isLastInCategory;
        }
    }

    @Override
    public void initGui() {
        super.initGui();

        // Build rows first to calculate dimensions
        rebuildDisplayRows();
        calculateDynamicSize();

        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;

        // Add buttons in header
        int buttonY = guiTop + 6;
        int buttonWidth = 60;
        int buttonHeight = 14;
        int spacing = 4;

        selectAllButton = new GuiButton(0, guiLeft + TAB_SIZE + PADDING + 2, buttonY, buttonWidth, buttonHeight,
            I18n.format("gui.ae2powertools.scanner.select_all"));
        deselectAllButton = new GuiButton(1, guiLeft + TAB_SIZE + PADDING + 2 + buttonWidth + spacing, buttonY, buttonWidth, buttonHeight,
            I18n.format("gui.ae2powertools.scanner.deselect_all"));
        cancelButton = new GuiButton(2, guiLeft + guiWidth - buttonWidth - 6, buttonY, buttonWidth, buttonHeight,
            I18n.format("gui.ae2powertools.scanner.cancel"));

        buttonList.add(selectAllButton);
        buttonList.add(deselectAllButton);
        buttonList.add(cancelButton);

        scrollOffset = 0;
    }

    /**
     * Calculate the GUI size based on content.
     */
    private void calculateDynamicSize() {
        // Calculate max dimensions based on screen size
        int maxGuiWidth = (int) (width * MAX_GUI_WIDTH_PERCENT);
        int maxGuiHeight = (int) (height * MAX_GUI_HEIGHT_PERCENT);

        // Calculate width based on max text width
        // Text in drawRow starts at: contentLeft + 2 + 28 = guiLeft + TAB_SIZE + PADDING + 2 + 28
        // Content ends at: guiLeft + guiWidth - SCROLLBAR_WIDTH
        // So we need: TAB_SIZE + PADDING + 2 + 28 + maxTextWidth + margin + SCROLLBAR_WIDTH
        int leftOffset = TAB_SIZE + PADDING + 2 + 28;  // Space from guiLeft to where text starts
        int rightMargin = 8;  // Padding after text before scrollbar
        int contentWidth = leftOffset + maxTextWidth + rightMargin + SCROLLBAR_WIDTH;
        guiWidth = Math.max(MIN_GUI_WIDTH, Math.min(maxGuiWidth, contentWidth));

        // Also ensure minimum width for buttons
        guiWidth = Math.max(guiWidth, TAB_SIZE + PADDING + 60 + 4 + 60 + 4 + 60 + 10);

        // Minimum height to fit all 4 tabs: HEADER_HEIGHT + 4 tabs + 3 spacers (2px each)
        int minTabsHeight = HEADER_HEIGHT + 4 * TAB_SIZE + 3 * 2;

        // Calculate height based on number of rows (fit as many as possible up to max)
        int availableContentHeight = maxGuiHeight - HEADER_HEIGHT - FOOTER_HEIGHT - PADDING * 2;
        int maxVisibleRows = availableContentHeight / ROW_HEIGHT;
        int visibleRows = Math.min(displayRows.size(), maxVisibleRows);
        visibleRows = Math.max(3, visibleRows); // At least 3 rows
        int contentHeight = visibleRows * ROW_HEIGHT;
        guiHeight = HEADER_HEIGHT + contentHeight + FOOTER_HEIGHT + PADDING * 2;
        guiHeight = Math.max(MIN_GUI_HEIGHT, Math.min(maxGuiHeight, guiHeight));

        // Ensure GUI is tall enough to fit all tabs
        guiHeight = Math.max(guiHeight, minTabsHeight);

        // Recalculate max scroll
        int viewHeight = guiHeight - HEADER_HEIGHT - FOOTER_HEIGHT - PADDING * 2;
        int totalContentHeight = displayRows.size() * ROW_HEIGHT;
        maxScroll = Math.max(0, totalContentHeight - viewHeight);
        scrollOffset = Math.min(scrollOffset, maxScroll);
    }

    /**
     * Recalculate GUI dimensions and reposition elements after content changes.
     */
    private void recalculateLayout() {
        calculateDynamicSize();
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;

        // Reposition buttons
        int buttonY = guiTop + 6;
        int buttonWidth = 60;
        int spacing = 4;

        selectAllButton.x = guiLeft + TAB_SIZE + PADDING + 2;
        selectAllButton.y = buttonY;
        deselectAllButton.x = guiLeft + TAB_SIZE + PADDING + 2 + buttonWidth + spacing;
        deselectAllButton.y = buttonY;
        cancelButton.x = guiLeft + guiWidth - buttonWidth - 6;
        cancelButton.y = buttonY;
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case 0: // Select All
                ScannerClientState.selectAll();
                break;

            case 1: // Deselect All
                ScannerClientState.deselectAll();
                break;

            case 2: // Cancel - cancels the scan session
                PowerToolsNetwork.INSTANCE.sendToServer(new PacketScannerCancel());
                ScannerClientState.setActiveSession(false);
                mc.displayGuiScreen(null);
                break;
        }
    }

    private void rebuildDisplayRows() {
        displayRows = new ArrayList<>();
        maxTextWidth = 0;
        Tab currentTab = ScannerClientState.getCurrentTab();

        if (currentTab == Tab.LOOPS) {
            rebuildLoopRows();
        } else if (currentTab == Tab.UNLOADED_CHUNKS) {
            rebuildChunkRows();
        } else if (currentTab == Tab.MISSING_CHANNELS) {
            rebuildMissingRows();
        } else {
            rebuildChokeRows();
        }

        // Calculate max scroll based on current GUI size
        if (guiHeight > 0) {
            int viewHeight = guiHeight - HEADER_HEIGHT - FOOTER_HEIGHT - PADDING * 2;
            int totalContentHeight = displayRows.size() * ROW_HEIGHT;
            maxScroll = Math.max(0, totalContentHeight - viewHeight);
            scrollOffset = Math.min(scrollOffset, maxScroll);
        }
    }

    private void rebuildLoopRows() {
        List<LoopLocationClient> sorted = ScannerClientState.getSortedLoopLocations();
        List<LoopLocationClient> original = ScannerClientState.getLoopLocations();
        if (sorted.isEmpty()) return;

        // Group by dimension (counting only)
        Map<String, Integer> dimCounts = new HashMap<>();
        for (LoopLocationClient loc : sorted) {
            String dimKey = I18n.format("gui.ae2powertools.scanner.dimension_format", loc.dimensionName, loc.dimension);
            dimCounts.merge(dimKey, 1, Integer::sum);
        }

        // Build rows in order
        String lastDimKey = null;
        for (int i = 0; i < sorted.size(); i++) {
            LoopLocationClient loc = sorted.get(i);
            // Find original index for proper selection tracking
            int originalIndex = original.indexOf(loc);
            String dimKey = I18n.format("gui.ae2powertools.scanner.dimension_format", loc.dimensionName, loc.dimension);

            // Add category header if new dimension
            if (!dimKey.equals(lastDimKey)) {
                int count = dimCounts.getOrDefault(dimKey, 0);
                String catText = dimKey + " (" + count + ")";
                displayRows.add(new DisplayRow(dimKey, catText));
                maxTextWidth = Math.max(maxTextWidth, fontRenderer.getStringWidth(catText));
                lastDimKey = dimKey;
            }

            // Skip entries if dimension is collapsed
            if (collapsedDimensions.getOrDefault(dimKey, false)) continue;

            // Check if this is the last entry in its category
            boolean isLast = isLastLoopInCategory(sorted, i, loc.dimensionName, loc.dimension);

            // Format entry text
            BlockPos pos = loc.pos;
            String posStr = String.format("[%d, %d, %d]", pos.getX(), pos.getY(), pos.getZ());

            double distance = 0;
            if (mc.player != null && mc.player.dimension == loc.dimension) {
                distance = loc.getDistanceFrom(mc.player.getPosition());
            }
            String distStr = distance > 0 ? String.format(" - %.0fm", distance) : "";
            String text = loc.description + " " + posStr + distStr;

            displayRows.add(new DisplayRow(originalIndex, loc, text, isLast));
            maxTextWidth = Math.max(maxTextWidth, fontRenderer.getStringWidth(text));
        }
    }

    private void rebuildChunkRows() {
        List<ChunkLocationClient> sorted = ScannerClientState.getSortedChunkLocations();
        List<ChunkLocationClient> original = ScannerClientState.getChunkLocations();
        if (sorted.isEmpty()) return;

        // Group by dimension (counting only)
        Map<String, Integer> dimCounts = new HashMap<>();
        for (ChunkLocationClient loc : sorted) {
            String dimKey = I18n.format("gui.ae2powertools.scanner.dimension_format", loc.dimensionName, loc.dimension);
            dimCounts.merge(dimKey, 1, Integer::sum);
        }

        // Build rows in order
        String lastDimKey = null;
        for (int i = 0; i < sorted.size(); i++) {
            ChunkLocationClient loc = sorted.get(i);
            // Find original index for proper selection tracking
            int originalIndex = original.indexOf(loc);
            String dimKey = I18n.format("gui.ae2powertools.scanner.dimension_format", loc.dimensionName, loc.dimension);

            // Add category header if new dimension
            if (!dimKey.equals(lastDimKey)) {
                int count = dimCounts.getOrDefault(dimKey, 0);
                String catText = dimKey + " (" + count + ")";
                displayRows.add(new DisplayRow(dimKey, catText));
                maxTextWidth = Math.max(maxTextWidth, fontRenderer.getStringWidth(catText));
                lastDimKey = dimKey;
            }

            // Skip entries if dimension is collapsed
            if (collapsedDimensions.getOrDefault(dimKey, false)) continue;

            // Check if this is the last entry in its category
            boolean isLast = isLastChunkInCategory(sorted, i, loc.dimensionName, loc.dimension);

            // Format entry text
            double distance = 0;
            if (mc.player != null && mc.player.dimension == loc.dimension) {
                distance = loc.getDistanceFrom(mc.player.getPosition());
            }
            String distStr = distance > 0 ? String.format(" - %.0fm", distance) : "";
            String text = I18n.format("gui.ae2powertools.scanner.chunk_entry", loc.chunkX, loc.chunkZ) + distStr;

            displayRows.add(new DisplayRow(originalIndex, loc, text, isLast));
            maxTextWidth = Math.max(maxTextWidth, fontRenderer.getStringWidth(text));
        }
    }

    private boolean isLastLoopInCategory(List<LoopLocationClient> sorted, int index, String dimName, int dim) {
        for (int j = index + 1; j < sorted.size(); j++) {
            LoopLocationClient nextLoc = sorted.get(j);
            if (nextLoc.dimensionName.equals(dimName) && nextLoc.dimension == dim) return false;
            break;
        }

        return true;
    }

    private boolean isLastChunkInCategory(List<ChunkLocationClient> sorted, int index, String dimName, int dim) {
        for (int j = index + 1; j < sorted.size(); j++) {
            ChunkLocationClient nextLoc = sorted.get(j);
            if (nextLoc.dimensionName.equals(dimName) && nextLoc.dimension == dim) return false;
            break;
        }

        return true;
    }

    private void rebuildMissingRows() {
        List<MissingDeviceClient> sorted = ScannerClientState.getSortedMissingDevices();
        List<MissingDeviceClient> original = ScannerClientState.getMissingDevices();
        if (sorted.isEmpty()) return;

        // Group by dimension (counting only)
        Map<String, Integer> dimCounts = new HashMap<>();
        for (MissingDeviceClient loc : sorted) {
            String dimKey = I18n.format("gui.ae2powertools.scanner.dimension_format", loc.dimensionName, loc.dimension);
            dimCounts.merge(dimKey, 1, Integer::sum);
        }

        // Build rows in order
        String lastDimKey = null;
        for (int i = 0; i < sorted.size(); i++) {
            MissingDeviceClient loc = sorted.get(i);
            // Find original index for proper selection tracking
            int originalIndex = original.indexOf(loc);
            String dimKey = I18n.format("gui.ae2powertools.scanner.dimension_format", loc.dimensionName, loc.dimension);

            // Add category header if new dimension
            if (!dimKey.equals(lastDimKey)) {
                int count = dimCounts.getOrDefault(dimKey, 0);
                String catText = dimKey + " (" + count + ")";
                displayRows.add(new DisplayRow(dimKey, catText));
                maxTextWidth = Math.max(maxTextWidth, fontRenderer.getStringWidth(catText));
                lastDimKey = dimKey;
            }

            // Skip entries if dimension is collapsed
            if (collapsedDimensions.getOrDefault(dimKey, false)) continue;

            // Check if this is the last entry in its category
            boolean isLast = isLastMissingInCategory(sorted, i, loc.dimensionName, loc.dimension);

            // Format entry text: display name [x,y,z] - distance
            BlockPos pos = loc.pos;
            String posStr = String.format("[%d, %d, %d]", pos.getX(), pos.getY(), pos.getZ());

            double distance = 0;
            if (mc.player != null && mc.player.dimension == loc.dimension) {
                distance = loc.getDistanceFrom(mc.player.getPosition());
            }
            String distStr = distance > 0 ? String.format(" - %.0fm", distance) : "";
            String text = loc.getDisplayName() + " " + posStr + distStr;

            displayRows.add(new DisplayRow(originalIndex, loc, text, isLast));
            maxTextWidth = Math.max(maxTextWidth, fontRenderer.getStringWidth(text));
        }
    }

    private boolean isLastMissingInCategory(List<MissingDeviceClient> sorted, int index, String dimName, int dim) {
        for (int j = index + 1; j < sorted.size(); j++) {
            MissingDeviceClient nextLoc = sorted.get(j);
            if (nextLoc.dimensionName.equals(dimName) && nextLoc.dimension == dim) return false;
            break;
        }

        return true;
    }

    private void rebuildChokeRows() {
        List<ChokeLocationClient> sorted = ScannerClientState.getSortedChokeLocations();
        List<ChokeLocationClient> original = ScannerClientState.getChokeLocations();
        if (sorted.isEmpty()) return;

        // Group by dimension (counting only)
        Map<String, Integer> dimCounts = new HashMap<>();
        for (ChokeLocationClient loc : sorted) {
            String dimKey = I18n.format("gui.ae2powertools.scanner.dimension_format", loc.dimensionName, loc.dimension);
            dimCounts.merge(dimKey, 1, Integer::sum);
        }

        // Build rows in order
        String lastDimKey = null;
        for (int i = 0; i < sorted.size(); i++) {
            ChokeLocationClient loc = sorted.get(i);
            // Find original index for proper selection tracking
            int originalIndex = original.indexOf(loc);
            String dimKey = I18n.format("gui.ae2powertools.scanner.dimension_format", loc.dimensionName, loc.dimension);

            // Add category header if new dimension
            if (!dimKey.equals(lastDimKey)) {
                int count = dimCounts.getOrDefault(dimKey, 0);
                String catText = dimKey + " (" + count + ")";
                displayRows.add(new DisplayRow(dimKey, catText));
                maxTextWidth = Math.max(maxTextWidth, fontRenderer.getStringWidth(catText));
                lastDimKey = dimKey;
            }

            // Skip entries if dimension is collapsed
            if (collapsedDimensions.getOrDefault(dimKey, false)) continue;

            // Check if this is the last entry in its category
            boolean isLast = isLastChokeInCategory(sorted, i, loc.dimensionName, loc.dimension);

            // Format entry text: description [x,y,z] demanded/capacity (-excess) - distance
            BlockPos pos = loc.pos;
            String posStr = String.format("[%d, %d, %d]", pos.getX(), pos.getY(), pos.getZ());
            String channelStr = loc.getChannelString();
            int excess = loc.getExcessChannels();
            String excessStr = excess > 0 ? " (-" + excess + ")" : "";

            double distance = 0;
            if (mc.player != null && mc.player.dimension == loc.dimension) {
                distance = loc.getDistanceFrom(mc.player.getPosition());
            }
            String distStr = distance > 0 ? String.format(" - %.0fm", distance) : "";
            String text = loc.description + " " + posStr + " " + channelStr + excessStr + distStr;

            displayRows.add(new DisplayRow(originalIndex, loc, text, isLast));
            maxTextWidth = Math.max(maxTextWidth, fontRenderer.getStringWidth(text));
        }
    }

    private boolean isLastChokeInCategory(List<ChokeLocationClient> sorted, int index, String dimName, int dim) {
        for (int j = index + 1; j < sorted.size(); j++) {
            ChokeLocationClient nextLoc = sorted.get(j);
            if (nextLoc.dimensionName.equals(dimName) && nextLoc.dimension == dim) return false;
            break;
        }

        return true;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        // Draw darkened background
        drawDefaultBackground();

        // Calculate content area
        int contentLeft = guiLeft + TAB_SIZE + PADDING;
        int contentTop = guiTop + HEADER_HEIGHT;
        int contentRight = guiLeft + guiWidth - SCROLLBAR_WIDTH;
        int contentBottom = guiTop + guiHeight - FOOTER_HEIGHT;
        int contentWidth = contentRight - contentLeft;
        int contentHeight = contentBottom - contentTop;

        // Draw outer border (AE2 style - double border)
        drawRect(guiLeft + TAB_SIZE - 2, guiTop - 2, guiLeft + guiWidth + 2, guiTop + guiHeight + 2, COLOR_BORDER);
        drawRect(guiLeft + TAB_SIZE - 1, guiTop - 1, guiLeft + guiWidth + 1, guiTop + guiHeight + 1, 0xFF101016);

        // Draw GUI background
        drawRect(guiLeft + TAB_SIZE, guiTop, guiLeft + guiWidth, guiTop + guiHeight, COLOR_BG);

        // Draw header with gradient effect
        drawGradientRect(guiLeft + TAB_SIZE, guiTop, guiLeft + guiWidth, guiTop + HEADER_HEIGHT, 0xC0202030, COLOR_HEADER_BG);

        // Draw icon tabs on the left side
        drawIconTabs(mouseX, mouseY);

        // Draw footer bar
        drawRect(guiLeft + TAB_SIZE, guiTop + guiHeight - FOOTER_HEIGHT, guiLeft + guiWidth, guiTop + guiHeight, COLOR_HEADER_BG);

        // Draw status in footer
        String status = ScannerClientState.getStatusMessage();
        if (!status.isEmpty()) {
            String truncatedStatus = fontRenderer.trimStringToWidth(status, guiWidth - TAB_SIZE - 8);
            fontRenderer.drawString(truncatedStatus, contentLeft + 2, guiTop + guiHeight - FOOTER_HEIGHT + 4, COLOR_TEXT_DIM);
        }

        // Update hover states
        hoveredRowIndex = -1;
        if (mouseX >= contentLeft && mouseX < contentRight &&
                mouseY >= contentTop && mouseY < contentBottom) {
            int relY = mouseY - contentTop + scrollOffset;
            hoveredRowIndex = relY / ROW_HEIGHT;
        }

        // Enable scissoring for content area
        ScaledResolution res = new ScaledResolution(mc);
        int scaleFactor = res.getScaleFactor();
        int scissorX = contentLeft * scaleFactor;
        int scissorY = (height - contentBottom) * scaleFactor;
        int scissorW = contentWidth * scaleFactor;
        int scissorH = contentHeight * scaleFactor;

        GlStateManager.pushMatrix();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(scissorX, scissorY, scissorW, scissorH);

        // Draw rows
        if (displayRows != null) {
            int y = contentTop - scrollOffset;

            for (int i = 0; i < displayRows.size(); i++) {
                if (y + ROW_HEIGHT < contentTop || y > contentBottom) {
                    y += ROW_HEIGHT;
                    continue;
                }

                DisplayRow row = displayRows.get(i);
                drawRow(row, contentLeft + 2, y, contentWidth - 4, i == hoveredRowIndex);
                y += ROW_HEIGHT;
            }
        }

        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GlStateManager.popMatrix();

        // Draw scrollbar
        drawScrollbar(guiLeft + guiWidth - SCROLLBAR_WIDTH, contentTop, SCROLLBAR_WIDTH, contentHeight);

        // Draw buttons
        super.drawScreen(mouseX, mouseY, partialTicks);

        // Draw tab tooltips
        drawTabTooltips(mouseX, mouseY);
    }

    private void drawRow(DisplayRow row, int x, int y, int width, boolean hovered) {
        if (row.type == DisplayRow.Type.CATEGORY) {
            // Draw category background
            drawRect(x - 2, y, x + width + 2, y + ROW_HEIGHT, COLOR_CATEGORY_BG);

            // Draw collapse indicator
            boolean collapsed = collapsedDimensions.getOrDefault(row.dimensionKey, false);
            String indicator = collapsed ? "▶" : "▼";
            fontRenderer.drawString(indicator, x + 2, y + (ROW_HEIGHT - fontRenderer.FONT_HEIGHT) / 2,
                COLOR_CATEGORY_TEXT);

            // Draw category text
            fontRenderer.drawString(row.text, x + 14, y + (ROW_HEIGHT - fontRenderer.FONT_HEIGHT) / 2,
                COLOR_CATEGORY_TEXT);
        } else {
            // Draw entry
            boolean selected = ScannerClientState.isSelected(row.locationIndex);

            // Background
            if (selected) {
                drawRect(x - 2, y, x + width + 2, y + ROW_HEIGHT, COLOR_ROW_SELECTED);
            } else if (hovered) {
                drawRect(x - 2, y, x + width + 2, y + ROW_HEIGHT, COLOR_ROW_HOVER);
            }

            // Tree lines
            int treeX = x + 6;
            int lineY = y + ROW_HEIGHT / 2;

            // Vertical line
            if (!row.isLastInCategory) {
                drawVerticalLine(treeX, y - 1, y + ROW_HEIGHT, COLOR_TREE_LINE);
            } else {
                drawVerticalLine(treeX, y - 1, lineY, COLOR_TREE_LINE);
            }

            // Horizontal line
            drawHorizontalLine(treeX, treeX + 8, lineY, COLOR_TREE_LINE);

            // Selection indicator
            String selectIndicator = selected ? "●" : "○";
            fontRenderer.drawString(selectIndicator, x + 18, y + (ROW_HEIGHT - fontRenderer.FONT_HEIGHT) / 2,
                selected ? COLOR_CATEGORY_TEXT : COLOR_TEXT_DIM);

            int textX = x + 28;
            // For missing device entries, draw the item icon
            // FIXME: Needs to be connected inventory, not AE2 part item
            //        Also needs to be ajusted to fit properly
            /*
            if (row.type == DisplayRow.Type.MISSING_ENTRY && row.missingDevice != null) {
                ItemStack itemStack = row.missingDevice.itemStack;
                if (!itemStack.isEmpty()) {
                    // Draw small item icon (8x8 scaled from 16x16)
                    GlStateManager.pushMatrix();
                    RenderHelper.enableGUIStandardItemLighting();
                    GlStateManager.translate(x + 28, y - 1, 0);
                    GlStateManager.scale(0.5f, 0.5f, 1.0f);
                    mc.getRenderItem().renderItemIntoGUI(itemStack, 0, 0);
                    RenderHelper.disableStandardItemLighting();
                    GlStateManager.popMatrix();
                    textX = x + 38;  // Offset text past the icon
                }
            }*/

            // Entry text
            fontRenderer.drawString(row.text, textX, y + (ROW_HEIGHT - fontRenderer.FONT_HEIGHT) / 2, COLOR_TEXT);
        }
    }

    private void drawScrollbar(int x, int y, int width, int height) {
        // Background
        drawRect(x, y, x + width, y + height, COLOR_SCROLLBAR_BG);

        if (maxScroll <= 0) return;

        // Calculate thumb position and size
        int contentHeight = displayRows != null ? displayRows.size() * ROW_HEIGHT : 0;
        if (contentHeight <= 0) return;

        int thumbHeight = Math.max(20, (int) ((float) height * height / contentHeight));
        int maxThumbY = height - thumbHeight;
        int thumbY = (int) ((float) scrollOffset / maxScroll * maxThumbY);

        // Draw thumb
        drawRect(x + 1, y + thumbY, x + width - 1, y + thumbY + thumbHeight, COLOR_SCROLLBAR_FG);
    }

    /**
     * Draw icon tabs on the left side of the GUI.
     */
    private void drawIconTabs(int mouseX, int mouseY) {
        Tab currentTab = ScannerClientState.getCurrentTab();
        hoveredTabIndex = -1;

        // TODO: refactor

        // Tab 0: Loops
        int tab0Y = guiTop + HEADER_HEIGHT;
        boolean loop0Hovered = mouseX >= guiLeft && mouseX < guiLeft + TAB_SIZE &&
                              mouseY >= tab0Y && mouseY < tab0Y + TAB_SIZE;
        boolean loopSelected = currentTab == Tab.LOOPS;

        if (loop0Hovered) hoveredTabIndex = 0;

        int loopBg = loopSelected ? COLOR_TAB_SELECTED : (loop0Hovered ? COLOR_TAB_HOVER : COLOR_TAB_BG);
        drawRect(guiLeft, tab0Y, guiLeft + TAB_SIZE, tab0Y + TAB_SIZE, loopBg);
        if (loopSelected) {
            drawRect(guiLeft + TAB_SIZE - 2, tab0Y, guiLeft + TAB_SIZE, tab0Y + TAB_SIZE, COLOR_CATEGORY_TEXT);
        }

        // Draw loop icon (∞ symbol for infinity/loop)
        int loopCount = ScannerClientState.getLoopLocations().size();
        String loopIcon = "∞";
        int iconColor = loopSelected ? COLOR_CATEGORY_TEXT : (loop0Hovered ? COLOR_TEXT : COLOR_TEXT_DIM);
        fontRenderer.drawString(loopIcon, guiLeft + (TAB_SIZE - fontRenderer.getStringWidth(loopIcon)) / 2,
            tab0Y + 4, iconColor);
        // Draw count below icon
        String countStr = String.valueOf(loopCount);
        fontRenderer.drawString(countStr, guiLeft + (TAB_SIZE - fontRenderer.getStringWidth(countStr)) / 2,
            tab0Y + TAB_SIZE - fontRenderer.FONT_HEIGHT - 2, loopCount > 0 ? 0xFFFF6666 : COLOR_TEXT_DIM);

        // Tab 1: Unloaded chunks
        int tab1Y = tab0Y + TAB_SIZE + 2;
        boolean chunk1Hovered = mouseX >= guiLeft && mouseX < guiLeft + TAB_SIZE &&
                               mouseY >= tab1Y && mouseY < tab1Y + TAB_SIZE;
        boolean chunkSelected = currentTab == Tab.UNLOADED_CHUNKS;

        if (chunk1Hovered) hoveredTabIndex = 1;

        int chunkBg = chunkSelected ? COLOR_TAB_SELECTED : (chunk1Hovered ? COLOR_TAB_HOVER : COLOR_TAB_BG);
        drawRect(guiLeft, tab1Y, guiLeft + TAB_SIZE, tab1Y + TAB_SIZE, chunkBg);
        if (chunkSelected) {
            drawRect(guiLeft + TAB_SIZE - 2, tab1Y, guiLeft + TAB_SIZE, tab1Y + TAB_SIZE, COLOR_CATEGORY_TEXT);
        }

        // Draw chunk icon (▦ for grid/chunk)
        int chunkCount = ScannerClientState.getChunkLocations().size();
        String chunkIcon = "▦";
        iconColor = chunkSelected ? COLOR_CATEGORY_TEXT : (chunk1Hovered ? COLOR_TEXT : COLOR_TEXT_DIM);
        fontRenderer.drawString(chunkIcon, guiLeft + (TAB_SIZE - fontRenderer.getStringWidth(chunkIcon)) / 2,
            tab1Y + 4, iconColor);
        // Draw count below icon
        countStr = String.valueOf(chunkCount);
        fontRenderer.drawString(countStr, guiLeft + (TAB_SIZE - fontRenderer.getStringWidth(countStr)) / 2,
            tab1Y + TAB_SIZE - fontRenderer.FONT_HEIGHT - 2, chunkCount > 0 ? 0xFFFFAA00 : COLOR_TEXT_DIM);

        // Tab 2: Missing channels
        int tab2Y = tab1Y + TAB_SIZE + 2;
        boolean missing2Hovered = mouseX >= guiLeft && mouseX < guiLeft + TAB_SIZE &&
                               mouseY >= tab2Y && mouseY < tab2Y + TAB_SIZE;
        boolean missingSelected = currentTab == Tab.MISSING_CHANNELS;

        if (missing2Hovered) hoveredTabIndex = 2;

        int missingBg = missingSelected ? COLOR_TAB_SELECTED : (missing2Hovered ? COLOR_TAB_HOVER : COLOR_TAB_BG);
        drawRect(guiLeft, tab2Y, guiLeft + TAB_SIZE, tab2Y + TAB_SIZE, missingBg);
        if (missingSelected) {
            drawRect(guiLeft + TAB_SIZE - 2, tab2Y, guiLeft + TAB_SIZE, tab2Y + TAB_SIZE, COLOR_CATEGORY_TEXT);
        }

        // Draw missing icon (✗ for missing/disconnected)
        int missingCount = ScannerClientState.getMissingDevices().size();
        String missingIcon = "✗";
        iconColor = missingSelected ? COLOR_CATEGORY_TEXT : (missing2Hovered ? COLOR_TEXT : COLOR_TEXT_DIM);
        fontRenderer.drawString(missingIcon, guiLeft + (TAB_SIZE - fontRenderer.getStringWidth(missingIcon)) / 2,
            tab2Y + 4, iconColor);
        // Draw count below icon
        countStr = String.valueOf(missingCount);
        fontRenderer.drawString(countStr, guiLeft + (TAB_SIZE - fontRenderer.getStringWidth(countStr)) / 2,
            tab2Y + TAB_SIZE - fontRenderer.FONT_HEIGHT - 2, missingCount > 0 ? 0xFFFF6666 : COLOR_TEXT_DIM);

        // Tab 3: Channel chokepoints
        int tab3Y = tab2Y + TAB_SIZE + 2;
        boolean choke3Hovered = mouseX >= guiLeft && mouseX < guiLeft + TAB_SIZE &&
                               mouseY >= tab3Y && mouseY < tab3Y + TAB_SIZE;
        boolean chokeSelected = currentTab == Tab.CHOKEPOINTS;

        if (choke3Hovered) hoveredTabIndex = 3;

        int chokeBg = chokeSelected ? COLOR_TAB_SELECTED : (choke3Hovered ? COLOR_TAB_HOVER : COLOR_TAB_BG);
        drawRect(guiLeft, tab3Y, guiLeft + TAB_SIZE, tab3Y + TAB_SIZE, chokeBg);
        if (chokeSelected) {
            drawRect(guiLeft + TAB_SIZE - 2, tab3Y, guiLeft + TAB_SIZE, tab3Y + TAB_SIZE, COLOR_CATEGORY_TEXT);
        }

        // Draw chokepoint icon (⚡ for channel/bottleneck)
        int chokeCount = ScannerClientState.getChokeLocations().size();
        String chokeIcon = "⚡";
        iconColor = chokeSelected ? COLOR_CATEGORY_TEXT : (choke3Hovered ? COLOR_TEXT : COLOR_TEXT_DIM);
        fontRenderer.drawString(chokeIcon, guiLeft + (TAB_SIZE - fontRenderer.getStringWidth(chokeIcon)) / 2,
            tab3Y + 4, iconColor);
        // Draw count below icon
        countStr = String.valueOf(chokeCount);
        fontRenderer.drawString(countStr, guiLeft + (TAB_SIZE - fontRenderer.getStringWidth(countStr)) / 2,
            tab3Y + TAB_SIZE - fontRenderer.FONT_HEIGHT - 2, chokeCount > 0 ? 0xFF66AAFF : COLOR_TEXT_DIM);
    }

    /**
     * Draw tooltips for hovered tabs.
     */
    private void drawTabTooltips(int mouseX, int mouseY) {
        if (hoveredTabIndex < 0) return;

        List<String> tooltip = new ArrayList<>();
        if (hoveredTabIndex == 0) {
            int count = ScannerClientState.getLoopLocations().size();
            tooltip.add(I18n.format("gui.ae2powertools.scanner.tab_loops", count));
        } else if (hoveredTabIndex == 1) {
            int count = ScannerClientState.getChunkLocations().size();
            tooltip.add(I18n.format("gui.ae2powertools.scanner.tab_chunks", count));
        } else if (hoveredTabIndex == 2) {
            int count = ScannerClientState.getMissingDevices().size();
            tooltip.add(I18n.format("gui.ae2powertools.scanner.tab_missing", count));
        } else if (hoveredTabIndex == 3) {
            int count = ScannerClientState.getChokeLocations().size();
            tooltip.add(I18n.format("gui.ae2powertools.scanner.tab_chokepoints", count));
        }

        if (!tooltip.isEmpty()) {
            drawHoveringText(tooltip, mouseX, mouseY);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);

        if (mouseButton != 0) return;

        // Check icon tab click
        int tab0Y = guiTop + HEADER_HEIGHT;
        int tab1Y = tab0Y + TAB_SIZE + 2;
        int tab2Y = tab1Y + TAB_SIZE + 2;
        int tab3Y = tab2Y + TAB_SIZE + 2;

        if (mouseX >= guiLeft && mouseX < guiLeft + TAB_SIZE) {
            if (mouseY >= tab0Y && mouseY < tab0Y + TAB_SIZE) {
                if (ScannerClientState.getCurrentTab() != Tab.LOOPS) {
                    ScannerClientState.setCurrentTab(Tab.LOOPS);
                    scrollOffset = 0;
                    rebuildDisplayRows();
                    recalculateLayout();
                }

                return;
            }

            if (mouseY >= tab1Y && mouseY < tab1Y + TAB_SIZE) {
                if (ScannerClientState.getCurrentTab() != Tab.UNLOADED_CHUNKS) {
                    ScannerClientState.setCurrentTab(Tab.UNLOADED_CHUNKS);
                    scrollOffset = 0;
                    rebuildDisplayRows();
                    recalculateLayout();
                }

                return;
            }

            if (mouseY >= tab2Y && mouseY < tab2Y + TAB_SIZE) {
                if (ScannerClientState.getCurrentTab() != Tab.MISSING_CHANNELS) {
                    ScannerClientState.setCurrentTab(Tab.MISSING_CHANNELS);
                    scrollOffset = 0;
                    rebuildDisplayRows();
                    recalculateLayout();
                }

                return;
            }

            if (mouseY >= tab3Y && mouseY < tab3Y + TAB_SIZE) {
                if (ScannerClientState.getCurrentTab() != Tab.CHOKEPOINTS) {
                    ScannerClientState.setCurrentTab(Tab.CHOKEPOINTS);
                    scrollOffset = 0;
                    rebuildDisplayRows();
                    recalculateLayout();
                }

                return;
            }
        }

        // Calculate content area
        int contentLeft = guiLeft + TAB_SIZE + PADDING;
        int contentTop = guiTop + HEADER_HEIGHT;
        int contentRight = guiLeft + guiWidth - SCROLLBAR_WIDTH;
        int contentBottom = guiTop + guiHeight - FOOTER_HEIGHT;

        // Check scrollbar click
        int scrollbarX = guiLeft + guiWidth - SCROLLBAR_WIDTH;
        if (mouseX >= scrollbarX && mouseX < guiLeft + guiWidth &&
                mouseY >= contentTop && mouseY < contentBottom) {
            isDraggingScrollbar = true;
            updateScrollFromMouse(mouseY, contentTop, contentBottom - contentTop);

            return;
        }

        // Check row click
        if (hoveredRowIndex >= 0 && hoveredRowIndex < displayRows.size()) {
            DisplayRow row = displayRows.get(hoveredRowIndex);

            if (row.type == DisplayRow.Type.CATEGORY) {
                // Toggle category collapse
                boolean collapsed = collapsedDimensions.getOrDefault(row.dimensionKey, false);
                collapsedDimensions.put(row.dimensionKey, !collapsed);
                rebuildDisplayRows();
            } else {
                // Selection logic: if all or none selected, focus only this one
                // Otherwise, toggle selection
                int totalCount = ScannerClientState.getCurrentTabItemCount();
                int selectedCount = ScannerClientState.getSelectedIndices().size();

                if (selectedCount == 0 || selectedCount == totalCount) {
                    // All or none selected - focus only this one
                    ScannerClientState.selectOnly(row.locationIndex);
                } else {
                    // Some selected - toggle this one
                    ScannerClientState.toggleSelection(row.locationIndex);
                }
            }
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);

        if (isDraggingScrollbar) {
            int contentTop = guiTop + HEADER_HEIGHT;
            int contentHeight = guiHeight - HEADER_HEIGHT - FOOTER_HEIGHT;
            updateScrollFromMouse(mouseY, contentTop, contentHeight);
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);
        isDraggingScrollbar = false;
    }

    private void updateScrollFromMouse(int mouseY, int contentTop, int contentHeight) {
        if (maxScroll <= 0) return;

        float ratio = (float) (mouseY - contentTop) / contentHeight;
        scrollOffset = (int) (ratio * maxScroll);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();

        int scroll = Mouse.getEventDWheel();
        if (scroll != 0) {
            scrollOffset -= scroll > 0 ? ROW_HEIGHT * 3 : -ROW_HEIGHT * 3;
            scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void updateScreen() {
        super.updateScreen();

        // Refresh display rows periodically to handle new data
        if (mc.player != null && mc.player.ticksExisted % 20 == 0) rebuildDisplayRows();
    }
}
