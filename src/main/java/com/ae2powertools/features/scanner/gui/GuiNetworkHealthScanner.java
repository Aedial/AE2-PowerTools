package com.ae2powertools.features.scanner.gui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.ae2powertools.Tags;
import com.ae2powertools.features.scanner.client.ScannerClientState;
import com.ae2powertools.features.scanner.client.ScannerSession;
import com.ae2powertools.features.scanner.data.ScannerTabId;
import com.ae2powertools.network.PacketScannerCancel;
import com.ae2powertools.network.PacketScannerToggleSubnet;
import com.ae2powertools.network.PowerToolsNetwork;
import com.ae2powertools.widgets.SmallVanillaButton;
import com.ae2powertools.widgets.WidgetContext;
import com.ae2powertools.widgets.WidgetList;


/**
 * GUI for the Network Health Scanner results, with tabs and grouped categories.
 * <p>
 * Scroll and collapsed-group state are kept separately for each tab.
 * {@link ScannerSession} supplies the rows, selection state, and footer text for the active tab.
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
    private static final int MIN_FOOTER_HEIGHT = 14;  // Minimum footer height (single line)
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

    // AE2 states.png texture for sort button icons
    private static final ResourceLocation STATES_TEXTURE =
        new ResourceLocation("appliedenergistics2", "textures/guis/states.png");
    private static final ResourceLocation PATTERN_ICON =
        new ResourceLocation(Tags.MODID, "textures/guis/pattern_icon.png");
    private static final int SORT_BUTTON_SIZE = 16;
    // Icon grid positions in states.png (column * 16, row * 16)
    private static final int ICON_COORDS_U = 6 * 16;    // "Coords" icon at column 6, row 6
    private static final int ICON_COORDS_V = 6 * 16;
    private static final int ICON_NAME_U = 0;           // "Name" icon at column 0, row 4
    private static final int ICON_NAME_V = 4 * 16;
    private static final int ICON_SUBNET_U = 5 * 16;    // Wireless icon for subnet toggle
    private static final int ICON_SUBNET_V = 0;

    private static final class TabViewState {
        // Scroll position and collapsed groups are kept per tab
        private int scrollOffset;
        private final Set<ScannerGroupKey> collapsedGroups = new HashSet<>();
    }

    // Scanner session for the device that opened this GUI
    private final long deviceId;
    private final ScannerSession session;
    private final Map<ScannerTabId, TabViewState> tabViewStates = new EnumMap<>(ScannerTabId.class);

    // Dynamic dimensions
    private int guiWidth;
    private int guiHeight;
    private int guiLeft;
    private int guiTop;
    private int scrollOffset;
    private int maxScroll;
    private boolean isDraggingScrollbar;

    // Current tab rows and the widest row text
    private List<ScannerListRow> displayRows = new ArrayList<>();
    private int maxTextWidth;

    // Hovered row and tab
    private int hoveredRowIndex = -1;
    private ScannerTabId hoveredTabId;

    // Dynamic footer
    private int footerHeight = MIN_FOOTER_HEIGHT;
    private final List<String> footerLines = new ArrayList<>();

    // Buttons
    private final WidgetList headerButtons = new WidgetList();
    private final WidgetContext widgetContext = WidgetContext.of(this);
    private SmallVanillaButton selectAllButton;
    private SmallVanillaButton deselectAllButton;
    private SmallVanillaButton cancelButton;

    // Sort button (custom-drawn icon button, right side of GUI)
    private boolean sortButtonHovered;

    // Subnet toggle button (right side, below sort)
    private boolean subnetButtonHovered;

    public GuiNetworkHealthScanner(long deviceId) {
        this.deviceId = deviceId;
        this.session = ScannerClientState.getSession(deviceId);
        if (session == null) {
            throw new IllegalArgumentException("No scanner session for device " + deviceId);
        }
    }

    private TabViewState getActiveViewState() {
        return tabViewStates.computeIfAbsent(session.getActiveTabId(), key -> new TabViewState());
    }

    private ScannerViewContext getViewContext() {
        if (mc.player == null) {
            return ScannerViewContext.empty();
        }

        return ScannerViewContext.of(mc.player.dimension, mc.player.getPosition());
    }

    private void rememberScrollOffset() {
        getActiveViewState().scrollOffset = scrollOffset;
    }

    private void cancelScan() {
        PowerToolsNetwork.INSTANCE.sendToServer(new PacketScannerCancel(deviceId));
        ScannerClientState.removeSession(deviceId);
        mc.displayGuiScreen(null);
    }

    @Override
    public void initGui() {
        super.initGui();
        buttonList.clear();

        // Build rows first to calculate dimensions
        rebuildDisplayRows();
        calculateDynamicSize();

        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;
        initHeaderButtons();
    }

    private void initHeaderButtons() {
        int buttonY = guiTop + 6;
        int buttonHeight = 14;
        int spacing = 4;
        int buttonPadding = 8;  // Padding inside button around text

        // Calculate button widths based on text
        String selectAllText = I18n.format("gui.ae2powertools.scanner.select_all");
        String deselectAllText = I18n.format("gui.ae2powertools.scanner.deselect_all");
        String cancelText = I18n.format("gui.ae2powertools.scanner.cancel");

        int selectAllWidth = fontRenderer.getStringWidth(selectAllText) + buttonPadding;
        int deselectAllWidth = fontRenderer.getStringWidth(deselectAllText) + buttonPadding;
        int cancelWidth = fontRenderer.getStringWidth(cancelText) + buttonPadding;

        headerButtons.clear();
        selectAllButton = headerButtons.add(new SmallVanillaButton(
            0,
            guiLeft + TAB_SIZE + PADDING + 2,
            buttonY,
            selectAllWidth,
            buttonHeight,
            selectAllText));
        selectAllButton.setOnClick(session::selectAllCurrent);

        deselectAllButton = headerButtons.add(new SmallVanillaButton(
            1,
            guiLeft + TAB_SIZE + PADDING + 2 + selectAllWidth + spacing,
            buttonY,
            deselectAllWidth,
            buttonHeight,
            deselectAllText));
        deselectAllButton.setOnClick(session::deselectAllCurrent);

        cancelButton = headerButtons.add(new SmallVanillaButton(
            2,
            guiLeft + guiWidth - cancelWidth - 6,
            buttonY,
            cancelWidth,
            buttonHeight,
            cancelText));
        cancelButton.setOnClick(this::cancelScan);
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

        // Also ensure minimum width for buttons (calculate based on text width)
        int buttonPadding = 8;
        int selectWidth = fontRenderer.getStringWidth(I18n.format("gui.ae2powertools.scanner.select_all"))
            + buttonPadding;
        int deselectWidth = fontRenderer.getStringWidth(I18n.format("gui.ae2powertools.scanner.deselect_all"))
            + buttonPadding;
        int cancelWidth = fontRenderer.getStringWidth(I18n.format("gui.ae2powertools.scanner.cancel"))
            + buttonPadding;
        int minButtonsWidth = TAB_SIZE + PADDING + selectWidth + 4 + deselectWidth + 10 + cancelWidth + 10;
        guiWidth = Math.max(guiWidth, minButtonsWidth);

        // Minimum height fits every registered tab and its spacing
        // Calculate height based on number of rows (fit as many as possible up to max)
        int tabCount = ScannerTabRegistry.getDisplayTabs().size();
        int minTabsHeight = HEADER_HEIGHT + tabCount * TAB_SIZE + Math.max(0, tabCount - 1) * 2;
        int availableContentHeight = maxGuiHeight - HEADER_HEIGHT - footerHeight - PADDING * 2;
        int maxVisibleRows = Math.max(1, availableContentHeight / ROW_HEIGHT);
        int visibleRows = Math.max(3, Math.min(displayRows.size(), maxVisibleRows));
        guiHeight = HEADER_HEIGHT + visibleRows * ROW_HEIGHT + footerHeight + PADDING * 2;
        guiHeight = Math.max(MIN_GUI_HEIGHT, Math.min(maxGuiHeight, guiHeight));
        // Ensure GUI is tall enough to fit all tabs
        guiHeight = Math.max(guiHeight, minTabsHeight);

        // Recalculate max scroll
        int viewHeight = guiHeight - HEADER_HEIGHT - footerHeight - PADDING * 2;
        maxScroll = Math.max(0, displayRows.size() * ROW_HEIGHT - viewHeight);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
        rememberScrollOffset();
    }

    /**
     * Recalculate GUI dimensions and reposition controls after content changes.
     */
    private void recalculateLayout() {
        calculateDynamicSize();
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;
        if (selectAllButton == null || deselectAllButton == null || cancelButton == null) {
            return;
        }

        // Reposition buttons (widths already set based on text)
        int buttonY = guiTop + 6;
        int spacing = 4;

        selectAllButton.setPosition(guiLeft + TAB_SIZE + PADDING + 2, buttonY);
        deselectAllButton.setPosition(selectAllButton.getX() + selectAllButton.getWidth() + spacing, buttonY);
        cancelButton.setPosition(guiLeft + guiWidth - cancelButton.getWidth() - 6, buttonY);
    }

    private void rebuildDisplayRows() {
        TabViewState viewState = getActiveViewState();
        displayRows = session.buildCurrentRows(ScannerClientState.getSortMode(session.getActiveTabId()),
            getViewContext(), viewState.collapsedGroups);
        maxTextWidth = 0;
        for (ScannerListRow row : displayRows) {
            maxTextWidth = Math.max(maxTextWidth, fontRenderer.getStringWidth(row.getText()));
        }

        // Calculate footer content and height
        rebuildFooter();

        // Calculate max scroll based on current GUI size
        if (guiHeight > 0) {
            int viewHeight = guiHeight - HEADER_HEIGHT - footerHeight - PADDING * 2;
            maxScroll = Math.max(0, displayRows.size() * ROW_HEIGHT - viewHeight);
            scrollOffset = Math.min(scrollOffset, maxScroll);
            rememberScrollOffset();
        }
    }

    private void rebuildFooter() {
        footerLines.clear();

        // Calculate available width for footer text
        int footerTextWidth = guiWidth > 0 ? guiWidth - TAB_SIZE - 8 : 200;
        for (String line : session.getCurrentFooterLines()) {
            footerLines.addAll(fontRenderer.listFormattedStringToWidth(line, footerTextWidth));
        }

        footerHeight = Math.max(MIN_FOOTER_HEIGHT, Math.max(1, footerLines.size()) * fontRenderer.FONT_HEIGHT + 4);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        // Draw darkened background
        drawDefaultBackground();

        // Calculate content area
        int contentLeft = guiLeft + TAB_SIZE + PADDING;
        int contentTop = guiTop + HEADER_HEIGHT;
        int contentRight = guiLeft + guiWidth - SCROLLBAR_WIDTH;
        int contentBottom = guiTop + guiHeight - footerHeight;
        int contentWidth = contentRight - contentLeft;
        int contentHeight = contentBottom - contentTop;

        // Draw outer border (AE2 style - double border)
        drawRect(guiLeft + TAB_SIZE - 2, guiTop - 2, guiLeft + guiWidth + 2, guiTop + guiHeight + 2, COLOR_BORDER);
        drawRect(guiLeft + TAB_SIZE - 1, guiTop - 1, guiLeft + guiWidth + 1, guiTop + guiHeight + 1, 0xFF101016);

        // Draw GUI background
        drawRect(guiLeft + TAB_SIZE, guiTop, guiLeft + guiWidth, guiTop + guiHeight, COLOR_BG);

        // Draw header with gradient effect
        drawGradientRect(guiLeft + TAB_SIZE, guiTop, guiLeft + guiWidth, guiTop + HEADER_HEIGHT,
            0xC0202030, COLOR_HEADER_BG);

        // Draw icon tabs on the left side
        drawIconTabs(mouseX, mouseY);

        // Draw sort button on the right side (mirrors left tabs)
        drawSortButton(mouseX, mouseY);
        drawSubnetButton(mouseX, mouseY);

        // Draw footer bar
        drawRect(guiLeft + TAB_SIZE, guiTop + guiHeight - footerHeight,
            guiLeft + guiWidth, guiTop + guiHeight, COLOR_HEADER_BG);

        // Draw footer content (pre-calculated wrapped lines)
        int footerTextY = guiTop + guiHeight - footerHeight + 2;
        for (int index = 0; index < footerLines.size(); index++) {
            fontRenderer.drawString(footerLines.get(index), contentLeft + 2,
                footerTextY + index * fontRenderer.FONT_HEIGHT, COLOR_TEXT_DIM);
        }

        // Update hover states
        hoveredRowIndex = -1;
        if (mouseX >= contentLeft && mouseX < contentRight && mouseY >= contentTop && mouseY < contentBottom) {
            int index = (mouseY - contentTop + scrollOffset) / ROW_HEIGHT;
            if (index >= 0 && index < displayRows.size()) hoveredRowIndex = index;
        }

        // Limit row drawing to the scrollable content area
        ScaledResolution resolution = new ScaledResolution(mc);
        int scaleFactor = resolution.getScaleFactor();
        GlStateManager.pushMatrix();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        try {
            GL11.glScissor(contentLeft * scaleFactor, (height - contentBottom) * scaleFactor,
                contentWidth * scaleFactor, contentHeight * scaleFactor);
            int rowY = contentTop - scrollOffset;
            for (int index = 0; index < displayRows.size(); index++) {
                if (rowY + ROW_HEIGHT >= contentTop && rowY <= contentBottom) {
                    drawRow(displayRows.get(index), contentLeft + 2, rowY, contentWidth - 4,
                        index == hoveredRowIndex);
                }
                rowY += ROW_HEIGHT;
            }
        } finally {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GlStateManager.popMatrix();
        }

        // Draw scrollbar
        drawScrollbar(guiLeft + guiWidth - SCROLLBAR_WIDTH, contentTop, SCROLLBAR_WIDTH, contentHeight);

        super.drawScreen(mouseX, mouseY, partialTicks);
        headerButtons.draw(widgetContext, mouseX, mouseY);

        // Draw tooltips last (on top of everything)
        drawTabTooltip(mouseX, mouseY);
        drawSortButtonTooltip(mouseX, mouseY);
        drawSubnetButtonTooltip(mouseX, mouseY);
        drawRowTooltip(mouseX, mouseY);
    }

    private void drawRow(ScannerListRow row, int x, int y, int width, boolean hovered) {
        if (row.getType() == ScannerListRow.Type.CATEGORY) {
            // Draw category background
            drawRect(x - 2, y, x + width + 2, y + ROW_HEIGHT, COLOR_CATEGORY_BG);
            // Draw collapse indicator
            boolean collapsed = getActiveViewState().collapsedGroups.contains(row.getGroupKey());
            String indicator = collapsed ? "▶" : "▼";
            fontRenderer.drawString(indicator, x + 2, y + (ROW_HEIGHT - fontRenderer.FONT_HEIGHT) / 2,
                COLOR_CATEGORY_TEXT);

            // Draw category text
            fontRenderer.drawString(row.getText(), x + 14, y + (ROW_HEIGHT - fontRenderer.FONT_HEIGHT) / 2,
                COLOR_CATEGORY_TEXT);
            return;
        }

        // Draw selection and hover background
        boolean selected = session.isCurrentSelection(row.getIssueKey());
        if (selected) {
            drawRect(x - 2, y, x + width + 2, y + ROW_HEIGHT, COLOR_ROW_SELECTED);
        } else if (hovered) {
            drawRect(x - 2, y, x + width + 2, y + ROW_HEIGHT, COLOR_ROW_HOVER);
        }

        // Tree lines
        int treeX = x + 6;
        int lineY = y + ROW_HEIGHT / 2;
        if (row.isLastInGroup()) {
            drawVerticalLine(treeX, y - 1, lineY, COLOR_TREE_LINE);
        } else {
            drawVerticalLine(treeX, y - 1, y + ROW_HEIGHT, COLOR_TREE_LINE);
        }
        drawHorizontalLine(treeX, treeX + 8, lineY, COLOR_TREE_LINE);

        // Draw selection indicator and entry text
        fontRenderer.drawString(selected ? "●" : "○", x + 18, y + (ROW_HEIGHT - fontRenderer.FONT_HEIGHT) / 2,
            selected ? COLOR_CATEGORY_TEXT : COLOR_TEXT_DIM);
        fontRenderer.drawString(row.getText(), x + 28, y + (ROW_HEIGHT - fontRenderer.FONT_HEIGHT) / 2, COLOR_TEXT);
    }

    private void drawScrollbar(int x, int y, int width, int height) {
        // Background
        drawRect(x, y, x + width, y + height, COLOR_SCROLLBAR_BG);

        if (maxScroll <= 0 || displayRows.isEmpty()) return;

        // Calculate thumb position and size
        int contentHeight = displayRows.size() * ROW_HEIGHT;
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
        hoveredTabId = null;
        int tabY = guiTop + HEADER_HEIGHT;
        for (ScannerTab<?> tab : ScannerTabRegistry.getDisplayTabs()) {
            ScannerTabDescriptor descriptor = tab.getDescriptor();
            int count = session.getEntryCount(descriptor.getId());
            drawSingleTab(mouseX, mouseY, tabY, descriptor, count);
            tabY += TAB_SIZE + 2;
        }
    }

    /**
     * Draw a single icon tab using the tab descriptor and current issue count.
     */
    private void drawSingleTab(int mouseX, int mouseY, int tabY, ScannerTabDescriptor descriptor, int count) {
        boolean hovered = mouseX >= guiLeft && mouseX < guiLeft + TAB_SIZE
            && mouseY >= tabY && mouseY < tabY + TAB_SIZE;
        boolean selected = descriptor.getId() == session.getActiveTabId();
        if (hovered) hoveredTabId = descriptor.getId();

        // Draw tab background
        int bgColor = selected ? COLOR_TAB_SELECTED : (hovered ? COLOR_TAB_HOVER : COLOR_TAB_BG);
        drawRect(guiLeft, tabY, guiLeft + TAB_SIZE, tabY + TAB_SIZE, bgColor);

        // Draw selection indicator
        if (selected) {
            drawRect(guiLeft + TAB_SIZE - 2, tabY, guiLeft + TAB_SIZE, tabY + TAB_SIZE, COLOR_CATEGORY_TEXT);
        }

        // Draw icon
        int iconColor = selected ? COLOR_CATEGORY_TEXT : (hovered ? COLOR_TEXT : COLOR_TEXT_DIM);
        if (descriptor.getIconType() == ScannerTabDescriptor.IconType.PATTERN_TEXTURE) {
            GlStateManager.pushMatrix();
            try {
                mc.getTextureManager().bindTexture(PATTERN_ICON);
                GlStateManager.enableBlend();
                GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
                drawScaledCustomSizeModalRect(guiLeft + 8, tabY + 3, 0, 0, SORT_BUTTON_SIZE, SORT_BUTTON_SIZE,
                    8, 8, SORT_BUTTON_SIZE, SORT_BUTTON_SIZE);
            } finally {
                GlStateManager.popMatrix();
            }
        } else {
            String icon = descriptor.getIconText();
            fontRenderer.drawString(icon, guiLeft + (TAB_SIZE - fontRenderer.getStringWidth(icon)) / 2,
                tabY + 4, iconColor);
        }

        // Draw count below icon
        String countText = String.valueOf(count);
        fontRenderer.drawString(countText, guiLeft + (TAB_SIZE - fontRenderer.getStringWidth(countText)) / 2,
            tabY + TAB_SIZE - fontRenderer.FONT_HEIGHT - 2, descriptor.getCountColor(count, COLOR_TEXT_DIM));
    }

    /**
     * Draw the tooltip for the hovered tab.
     */
    private void drawTabTooltip(int mouseX, int mouseY) {
        if (hoveredTabId == null) return;

        ScannerTabDescriptor descriptor = ScannerTabRegistry.get(hoveredTabId).getDescriptor();
        drawHoveringText(Collections.singletonList(I18n.format(descriptor.getTooltipKey(),
            session.getEntryCount(hoveredTabId))), mouseX, mouseY);
    }

    /**
     * Draw the tooltip supplied by the hovered row or category.
     */
    private void drawRowTooltip(int mouseX, int mouseY) {
        if (hoveredRowIndex < 0 || hoveredRowIndex >= displayRows.size()) return;

        ScannerListRow row = displayRows.get(hoveredRowIndex);
        String tooltip = row.getType() == ScannerListRow.Type.CATEGORY
            ? row.getTooltip() : session.getCurrentEntryTooltip(row.getIssueKey());
        if (tooltip == null || tooltip.isEmpty()) return;

        List<String> lines = fontRenderer.listFormattedStringToWidth(tooltip, 240);
        if (!lines.isEmpty()) drawHoveringText(lines, mouseX, mouseY);
    }

    /**
     * Draw the sort mode toggle button on the right side of the GUI,
     * mirroring the icon tabs on the left. Uses an inverted icon from states.png.
     */
    private void drawSortButton(int mouseX, int mouseY) {
        int x = guiLeft + guiWidth;
        int y = guiTop + HEADER_HEIGHT;
        sortButtonHovered = mouseX >= x && mouseX < x + TAB_SIZE && mouseY >= y && mouseY < y + TAB_SIZE;
        drawRect(x, y, x + TAB_SIZE, y + TAB_SIZE, sortButtonHovered ? COLOR_TAB_HOVER : COLOR_TAB_BG);
        drawRect(x, y, x + 2, y + TAB_SIZE, COLOR_CATEGORY_TEXT);

        ScannerSortMode mode = ScannerClientState.getSortMode(session.getActiveTabId());
        drawInvertedIcon(x + (TAB_SIZE - SORT_BUTTON_SIZE) / 2, y + (TAB_SIZE - SORT_BUTTON_SIZE) / 2,
            mode == ScannerSortMode.DISTANCE ? ICON_COORDS_U : ICON_NAME_U,
            mode == ScannerSortMode.DISTANCE ? ICON_COORDS_V : ICON_NAME_V);
    }

    /**
     * Draw an icon from states.png with colors replaced by the vertex color (white),
     * preserving only the texture's alpha channel. This makes dark icons visible
     * on the dark GUI background.
     */
    private void drawInvertedIcon(int x, int y, int u, int v) {
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(x, y, 0.0f);
            mc.getTextureManager().bindTexture(STATES_TEXTURE);
            GlStateManager.enableBlend();
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL13.GL_COMBINE);
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_COMBINE_RGB, GL11.GL_REPLACE);
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE0_RGB, GL13.GL_PRIMARY_COLOR);
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_COMBINE_ALPHA, GL11.GL_REPLACE);
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE0_ALPHA, GL11.GL_TEXTURE);
            drawTexturedModalRect(0, 0, u, v, SORT_BUTTON_SIZE, SORT_BUTTON_SIZE);
        } finally {
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
            GlStateManager.popMatrix();
        }
    }

    /**
     * Draw tooltip for the sort button when hovered.
     */
    private void drawSortButtonTooltip(int mouseX, int mouseY) {
        if (!sortButtonHovered) return;

        ScannerSortMode mode = ScannerClientState.getSortMode(session.getActiveTabId());
        String suffix = mode == ScannerSortMode.DISTANCE ? "distance" : "name";
        String modeText = I18n.format("gui.ae2powertools.scanner." + suffix + "_sort_mode");
        String tooltipText = I18n.format("gui.ae2powertools.scanner.sort_tooltip", modeText);
        drawHoveringText(Collections.singletonList(tooltipText), mouseX, mouseY);
    }

    /**
     * Draw the subnet scan toggle below the sort button.
     */
    private void drawSubnetButton(int mouseX, int mouseY) {
        int x = guiLeft + guiWidth;
        int y = guiTop + HEADER_HEIGHT + TAB_SIZE + 2;
        subnetButtonHovered = mouseX >= x && mouseX < x + TAB_SIZE && mouseY >= y && mouseY < y + TAB_SIZE;
        drawRect(x, y, x + TAB_SIZE, y + TAB_SIZE, subnetButtonHovered ? COLOR_TAB_HOVER : COLOR_TAB_BG);
        drawRect(x, y, x + 2, y + TAB_SIZE, COLOR_CATEGORY_TEXT);

        // Brighten the dark wireless icon to show the subnet scan state
        int stateColor = session.isSubnetScanEnabled() ? 0xA000FF00 : 0xA0FFFFFF;
        drawRect(x + 2, y + 1, x + TAB_SIZE - 1, y + TAB_SIZE - 1, stateColor);
        drawTexturedModalRect(x + (TAB_SIZE - SORT_BUTTON_SIZE) / 2 + 1,
            y + (TAB_SIZE - SORT_BUTTON_SIZE) / 2, ICON_SUBNET_U, ICON_SUBNET_V, SORT_BUTTON_SIZE, SORT_BUTTON_SIZE);
        if (subnetButtonHovered) {
            drawRect(x + 2, y + 1, x + TAB_SIZE - 1, y + TAB_SIZE - 1, 0x40FFFFFF);
        }
    }

    /**
     * Draw the tooltip for the subnet scan toggle.
     */
    private void drawSubnetButtonTooltip(int mouseX, int mouseY) {
        if (!subnetButtonHovered) return;

        String status = session.isSubnetScanEnabled() ? "enabled" : "disabled";

        List<String> tooltip = new ArrayList<>();
        tooltip.add(I18n.format("gui.ae2powertools.scanner.subnet.title"));
        tooltip.add(I18n.format("gui.ae2powertools.scanner.subnet." + status));
        tooltip.add("");
        tooltip.add(I18n.format("gui.ae2powertools.scanner.subnet.click_toggle"));
        tooltip.add(I18n.format("gui.ae2powertools.scanner.subnet.hint"));

        drawHoveringText(tooltip, mouseX, mouseY);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (headerButtons.mouseClicked(mouseX, mouseY, mouseButton)) return;

        super.mouseClicked(mouseX, mouseY, mouseButton);

        if (mouseButton != 0) return;

        // Check sort button click (right side tab)
        if (sortButtonHovered) {
            ScannerClientState.toggleSortMode(session);
            rebuildDisplayRows();
            recalculateLayout();
            return;
        }

        if (subnetButtonHovered) {
            PowerToolsNetwork.INSTANCE.sendToServer(new PacketScannerToggleSubnet());
            return;
        }

        // Check icon tab click
        if (mouseX >= guiLeft && mouseX < guiLeft + TAB_SIZE) {
            int tabY = guiTop + HEADER_HEIGHT;
            for (ScannerTab<?> tab : ScannerTabRegistry.getDisplayTabs()) {
                if (mouseY >= tabY && mouseY < tabY + TAB_SIZE) {
                    switchTab(tab.getDescriptor().getId());
                    return;
                }
                tabY += TAB_SIZE + 2;
            }
        }

        int contentTop = guiTop + HEADER_HEIGHT;
        int contentBottom = guiTop + guiHeight - footerHeight;
        int scrollbarX = guiLeft + guiWidth - SCROLLBAR_WIDTH;

        // Check scrollbar click
        if (mouseX >= scrollbarX && mouseX < guiLeft + guiWidth && mouseY >= contentTop && mouseY < contentBottom) {
            isDraggingScrollbar = true;
            updateScrollFromMouse(mouseY, contentTop, contentBottom - contentTop);
            return;
        }

        if (hoveredRowIndex < 0 || hoveredRowIndex >= displayRows.size()) return;

        // Check row click
        ScannerListRow row = displayRows.get(hoveredRowIndex);
        if (row.getType() == ScannerListRow.Type.CATEGORY) {
            Set<ScannerGroupKey> collapsedGroups = getActiveViewState().collapsedGroups;
            if (!collapsedGroups.add(row.getGroupKey())) collapsedGroups.remove(row.getGroupKey());

            rebuildDisplayRows();
            recalculateLayout();
            return;
        }

        // All or no selected results focus the clicked result
        int selectedCount = session.getCurrentSelectedCount();
        if (selectedCount == 0 || selectedCount == session.getCurrentEntryCount()) {
            session.selectOnlyCurrent(row.getIssueKey());
        } else {
            session.toggleCurrentSelection(row.getIssueKey());
        }
    }

    private void switchTab(ScannerTabId tabId) {
        if (tabId == session.getActiveTabId()) return;

        rememberScrollOffset();
        session.setActiveTabId(tabId);
        scrollOffset = getActiveViewState().scrollOffset;
        rebuildDisplayRows();
        recalculateLayout();
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);

        if (isDraggingScrollbar) {
            updateScrollFromMouse(mouseY, guiTop + HEADER_HEIGHT, guiHeight - HEADER_HEIGHT - footerHeight);
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
        scrollOffset = Math.max(0, Math.min((int) (ratio * maxScroll), maxScroll));
        rememberScrollOffset();
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();

        int scroll = Mouse.getEventDWheel();
        if (scroll == 0) return;

        scrollOffset = Math.max(0, Math.min(
            scrollOffset - (scroll > 0 ? ROW_HEIGHT * 3 : -ROW_HEIGHT * 3), maxScroll));
        rememberScrollOffset();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (!ScannerClientState.hasSession(deviceId)) {
            mc.displayGuiScreen(null);
            return;
        }

        // Refresh display rows periodically to handle incoming scan data
        if (mc.player != null && mc.player.ticksExisted % 20 == 0) {
            rebuildDisplayRows();
            recalculateLayout();
        }
    }
}
