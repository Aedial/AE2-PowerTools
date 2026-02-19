package com.ae2powertools.features.maintainer;

import java.awt.Rectangle;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.config.GuiUtils;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.storage.data.IAEItemStack;
import appeng.util.ReadableNumberConverter;
import appeng.util.Platform;

import com.ae2powertools.AE2PowerTools;
import com.ae2powertools.Tags;
import com.ae2powertools.client.PowerToolsClientConfig;
import com.ae2powertools.features.maintainer.MaintainerState;
import com.ae2powertools.network.PacketSelectRecipe;
import com.ae2powertools.network.PacketUpdateMaintainerEntry;
import com.ae2powertools.network.PowerToolsNetwork;

/**
 * Main GUI for the Better Level Maintainer.
 * Simple modal system: just visibility flags with early returns.
 */
@SideOnly(Side.CLIENT)
public class GuiBetterLevelMaintainer extends GuiContainer {

    private static final ResourceLocation BACKGROUND = new ResourceLocation(
            Tags.MODID, "textures/guis/maintainer_gui.png");
    private static final ResourceLocation BACKGROUND_TALL = new ResourceLocation(
            Tags.MODID, "textures/guis/maintainer_gui_tall.png");
    private static final ResourceLocation MODAL_BACKGROUND = new ResourceLocation(
            Tags.MODID, "textures/guis/maintainer_modal.png");
    private static final ResourceLocation SELECTOR_BACKGROUND = new ResourceLocation(
            Tags.MODID, "textures/guis/recipe_selector.png");
    private static final ResourceLocation SCROLLBAR_TEXTURE = new ResourceLocation(
            "minecraft", "textures/gui/container/creative_inventory/tabs.png");

    // Main GUI dimensions
    private static final int GUI_WIDTH = 238;
    private static final int GUI_HEIGHT = 206;

    // Entry display
    private static final int ENTRY_START_X = 9;
    private static final int ENTRY_START_Y = 18;
    private static final int ENTRY_WIDTH = 68;
    private static final int ENTRY_HEIGHT = 23;
    private static final int VISIBLE_ROWS = 6;
    private static final int COLUMNS = 3;

    // Scrollbar
    private static final int SCROLLBAR_X = 218;
    private static final int SCROLLBAR_Y = 19;
    private static final int SCROLLBAR_WIDTH = 12;
    private static final int SCROLLBAR_HEIGHT = 136;

    // Search bar
    private static final int SEARCH_X = 130;
    private static final int SEARCH_Y = 4;
    private static final int SEARCH_WIDTH = 80;
    private static final int SEARCH_HEIGHT = 12;

    // Status bar (small view)
    private static final int STATUS_Y = 163;

    // Tall view constants
    private static final int TALL_GUI_WIDTH = 238;
    private static final int TALL_GUI_BASE_HEIGHT = 92;
    private static final int TALL_SLICE_START_Y = 19;
    private static final int TALL_SLICE_END_Y = 42;
    private static final int TALL_SLICE_HEIGHT = TALL_SLICE_END_Y - TALL_SLICE_START_Y; // 23 pixels
    private static final int TALL_ENTRY_HEIGHT = TALL_SLICE_HEIGHT;
    private static final int TALL_MARGIN = 10; // margin from screen edges
    private static final int TALL_STATUS_OFFSET = 18; // offset from bottom of GUI to status bar

    // Style toggle button position (left of GUI, consistent position)
    private static final int STYLE_BUTTON_SIZE = 16;

    // Modal dimensions
    private static final int MODAL_WIDTH = 176;
    private static final int MODAL_HEIGHT = 107;

    // Selector dimensions
    private static final int SELECTOR_WIDTH = 195;
    private static final int SELECTOR_HEIGHT = 186;
    private static final int SELECTOR_GRID_X = 8;
    private static final int SELECTOR_GRID_Y = 17;
    private static final int SELECTOR_COLS = 9;
    private static final int SELECTOR_ROWS = 9;
    private static final int SLOT_SIZE = 18;

    private final ContainerBetterLevelMaintainer container;

    // Main GUI state
    private GuiTextField searchField;
    private int scrollOffset = 0;
    private int maxScroll = 0;
    private boolean isDraggingScrollbar = false;
    private int hoveredEntryIndex = -1;
    private int cpuX, cpuTextWidth, recipeX, recipeTextWidth;

    // === MODAL STATE (simple visibility flags) ===
    private boolean modalVisible = false;
    private int modalEntryIndex = -1;
    private MaintainerEntry modalEntry = null;
    private int modalLeft, modalTop;
    private GuiTextField modalTargetField, modalBatchField, modalFreqField;
    private int modalLastFrequency;
    private List<GuiButton> modalButtons = new ArrayList<>();

    // === SELECTOR STATE ===
    private boolean selectorVisible = false;
    private int selectorLeft, selectorTop;
    private GuiTextField selectorSearchField;
    private List<IAEItemStack> selectorItems = new ArrayList<>();
    private List<IAEItemStack> selectorFiltered = new ArrayList<>();
    private int selectorScroll = 0;
    private int selectorMaxScroll = 0;
    private boolean selectorDragging = false;
    private int selectorHoveredSlot = -1;

    // === TALL MODE STATE ===
    private boolean useTallView;
    private int tallVisibleRows = 6;
    private int tallScrollbarHeight;
    private int styleButtonX, styleButtonY;
    private boolean styleButtonHovered = false;
    private boolean jeiEnabled;

    public GuiBetterLevelMaintainer(ContainerBetterLevelMaintainer container) {
        super(container);
        this.container = container;
        this.useTallView = PowerToolsClientConfig.maintainer.isUseTallView();
        this.jeiEnabled = Platform.isModLoaded("jei");
        this.xSize = GUI_WIDTH;
        this.ySize = GUI_HEIGHT;
    }

    @Override
    public void initGui() {
        // Calculate tall mode dimensions first
        if (useTallView) {
            // Calculate how many rows fit on screen
            int availableHeight = this.height - TALL_MARGIN * 2;
            // Subtract header (19px) and footer area for status bar
            int headerHeight = TALL_SLICE_START_Y;
            int footerHeight = TALL_GUI_BASE_HEIGHT - TALL_SLICE_END_Y;
            int contentHeight = availableHeight - headerHeight - footerHeight;
            // +1 because the footer contains the last entry row (without separator line)
            tallVisibleRows = Math.max(3, contentHeight / TALL_SLICE_HEIGHT) + 1;
            this.ySize = headerHeight + ((tallVisibleRows - 1) * TALL_SLICE_HEIGHT) + footerHeight;
            this.xSize = TALL_GUI_WIDTH;
            tallScrollbarHeight = tallVisibleRows * TALL_SLICE_HEIGHT - 2;
        } else {
            this.ySize = GUI_HEIGHT;
            this.xSize = GUI_WIDTH;
        }

        super.initGui();

        searchField = new GuiTextField(0, fontRenderer, guiLeft + SEARCH_X, guiTop + SEARCH_Y, SEARCH_WIDTH, SEARCH_HEIGHT);
        searchField.setMaxStringLength(50);
        searchField.setEnableBackgroundDrawing(true);
        searchField.setTextColor(0xFFFFFF);

        // Calculate modal positions (centered)
        modalLeft = (width - MODAL_WIDTH) / 2;
        modalTop = (height - MODAL_HEIGHT) / 2;
        selectorLeft = (width - SELECTOR_WIDTH) / 2;
        selectorTop = (height - SELECTOR_HEIGHT) / 2;

        // Style toggle button position (left of GUI, aligned with header)
        styleButtonX = guiLeft - STYLE_BUTTON_SIZE - 2;
        styleButtonY = guiTop + SEARCH_Y;

        // Re-init modal fields if visible
        if (modalVisible) initModalFields();
        if (selectorVisible) initSelectorFields();

        updateScrollLimits();
    }

    /**
     * Toggles between small and tall view modes.
     */
    private void toggleViewStyle() {
        useTallView = !useTallView;
        PowerToolsClientConfig.maintainer.setUseTallView(useTallView);
        scrollOffset = 0;
        initGui();
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        searchField.updateCursorCounter();
        if (modalVisible && modalTargetField != null) {
            modalTargetField.updateCursorCounter();
            modalBatchField.updateCursorCounter();
        }

        if (selectorVisible && selectorSearchField != null) selectorSearchField.updateCursorCounter();

        updateScrollLimits();
    }

    private void updateScrollLimits() {
        TileBetterLevelMaintainer maintainer = container.getMaintainer();
        if (useTallView) {
            // In tall mode, 1 entry per row
            maxScroll = Math.max(0, maintainer.getOpenSlots() - tallVisibleRows);
        } else {
            // In small mode, COLUMNS entries per row
            int totalRows = (maintainer.getOpenSlots() + COLUMNS - 1) / COLUMNS;
            maxScroll = Math.max(0, totalRows - VISIBLE_ROWS);
        }
        scrollOffset = Math.min(scrollOffset, maxScroll);
    }

    // ==================== DRAWING ====================

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);

        // selector modal
        if (selectorVisible) {
            drawSelectorModal(mouseX, mouseY, partialTicks);
            drawSelectorTooltip(mouseX, mouseY);
            return;
        }

        // Entry modal
        if (modalVisible) {
            drawEntryModal(mouseX, mouseY, partialTicks);
            drawModalTooltip(mouseX, mouseY);
            return;
        }

        // Main GUI tooltips
        renderHoveredToolTip(mouseX, mouseY);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        if (useTallView) {
            drawTallBackground();
        } else {
            mc.getTextureManager().bindTexture(BACKGROUND);
            drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize);
        }

        // Draw style toggle button
        drawStyleButton(mouseX, mouseY);

        searchField.drawTextBox();
        if (useTallView) {
            drawEntriesTall(mouseX, mouseY);
            drawScrollbarTall();
            drawStatusBarTall();
        } else {
            drawEntries(mouseX, mouseY);
            drawScrollbar();
            drawStatusBar();
        }
    }

    /**
     * Draw the tall GUI background by slicing and duplicating texture regions.
     */
    private void drawTallBackground() {
        mc.getTextureManager().bindTexture(BACKGROUND_TALL);

        // Draw header (0 to TALL_SLICE_START_Y)
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, TALL_SLICE_START_Y);

        // Draw entry slices with separators (footer provides the last row without separator)
        for (int row = 0; row < tallVisibleRows - 1; row++) {
            int y = guiTop + TALL_SLICE_START_Y + row * TALL_SLICE_HEIGHT;
            drawTexturedModalRect(guiLeft, y, 0, TALL_SLICE_START_Y, xSize, TALL_SLICE_HEIGHT);
        }

        // Draw footer (last entry without separator + status bar)
        int footerY = guiTop + TALL_SLICE_START_Y + (tallVisibleRows - 1) * TALL_SLICE_HEIGHT;
        int footerHeight = TALL_GUI_BASE_HEIGHT - TALL_SLICE_END_Y;
        drawTexturedModalRect(guiLeft, footerY, 0, TALL_SLICE_END_Y, xSize, footerHeight);
    }

    /**
     * Draw the style toggle button (small/tall).
     */
    private void drawStyleButton(int mouseX, int mouseY) {
        styleButtonHovered = mouseX >= styleButtonX && mouseX < styleButtonX + STYLE_BUTTON_SIZE &&
                             mouseY >= styleButtonY && mouseY < styleButtonY + STYLE_BUTTON_SIZE;

        // Draw button background using AE2 states texture
        mc.getTextureManager().bindTexture(new ResourceLocation("appliedenergistics2", "textures/guis/states.png"));
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        // Standard AE2 button frame (bottom-right cell of the 16x16 grid in states.png)
        drawTexturedModalRect(styleButtonX, styleButtonY, 240, 240, STYLE_BUTTON_SIZE, STYLE_BUTTON_SIZE);

        // Terminal style icon: row 13 in states.png, column 0 = tall, column 1 = compact
        int iconU = useTallView ? 0 : 16;
        int iconV = 13 * 16;
        drawTexturedModalRect(styleButtonX, styleButtonY, iconU, iconV, STYLE_BUTTON_SIZE, STYLE_BUTTON_SIZE);

        // Hover highlight overlay
        if (styleButtonHovered) {
            drawRect(styleButtonX + 1, styleButtonY + 1,
                    styleButtonX + STYLE_BUTTON_SIZE - 1, styleButtonY + STYLE_BUTTON_SIZE - 1,
                    0x40FFFFFF);
        }
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        String title = I18n.format("gui.ae2powertools.maintainer.title");
        fontRenderer.drawString(title, 8, 6, 0x000000);

        // Only draw main GUI tooltips if no modal is visible
        if (!modalVisible && !selectorVisible) {
            if (useTallView) {
                drawEntryTooltipsTall(mouseX, mouseY);
            } else {
                drawEntryTooltips(mouseX, mouseY);
            }

            drawStatusBarTooltips(mouseX, mouseY);
            drawStyleButtonTooltip(mouseX, mouseY);
        }
    }

    /**
     * Draw tooltip for the style toggle button.
     */
    private void drawStyleButtonTooltip(int mouseX, int mouseY) {
        if (!styleButtonHovered) return;

        List<String> tooltip = new ArrayList<>();
        tooltip.add(I18n.format("gui.ae2powertools.maintainer.style.title"));
        if (useTallView) {
            tooltip.add("§7" + I18n.format("gui.ae2powertools.maintainer.style.tall") + "§r");
        } else {
            tooltip.add("§7" + I18n.format("gui.ae2powertools.maintainer.style.small") + "§r");
        }
        tooltip.add("§7" + I18n.format("gui.ae2powertools.maintainer.style.click_toggle") + "§r");

        // Undo the foreground layer translation since the button is outside the GUI area
        GlStateManager.pushMatrix();
        GlStateManager.translate(-guiLeft, -guiTop, 0);
        GuiUtils.drawHoveringText(tooltip, mouseX, mouseY, width, height, -1, fontRenderer);
        GlStateManager.popMatrix();
    }

    // ==================== ENTRY MODAL ====================

    private void openEntryModal(int entryIndex) {
        MaintainerEntry entry = container.getMaintainer().getEntry(entryIndex);
        if (entry == null) return;

        modalEntryIndex = entryIndex;
        modalEntry = entry.copy();
        modalLastFrequency = modalEntry.getFrequencySeconds();
        modalVisible = true;

        Keyboard.enableRepeatEvents(true);
        initModalFields();
    }

    private void closeEntryModal(boolean save) {
        if (save && modalEntry != null) {
            long targetQty = MaintainerEntry.parseQuantity(modalTargetField.getText());
            if (targetQty < 0) targetQty = modalEntry.getTargetQuantity();

            long batchSize = MaintainerEntry.parseQuantity(modalBatchField.getText());
            if (batchSize < 1) batchSize = modalEntry.getBatchSize();

            // Parse frequency from field text, fall back to modalLastFrequency if invalid
            int frequency = MaintainerEntry.parseTime(modalFreqField.getText());
            if (frequency < 1) frequency = modalLastFrequency;

            PacketUpdateMaintainerEntry packet = new PacketUpdateMaintainerEntry(
                    container.getMaintainer().getPos(),
                    modalEntryIndex,
                    modalEntry.getTargetItem(),
                    targetQty,
                    batchSize,
                    frequency,
                    modalEntry.isEnabled()
            );
            PowerToolsNetwork.INSTANCE.sendToServer(packet);
        }

        modalVisible = false;
        modalEntry = null;
        modalEntryIndex = -1;
        Keyboard.enableRepeatEvents(false);
    }

    private void initModalFields() {
        modalButtons.clear();

        modalTargetField = new GuiTextField(1, fontRenderer, modalLeft + 60, modalTop + 38, 110, 12);
        modalTargetField.setMaxStringLength(20);
        modalTargetField.setText(MaintainerEntry.formatQuantity(modalEntry.getTargetQuantity()));

        modalBatchField = new GuiTextField(2, fontRenderer, modalLeft + 60, modalTop + 64, 110, 12);
        modalBatchField.setMaxStringLength(20);
        modalBatchField.setText(MaintainerEntry.formatQuantity(modalEntry.getBatchSize()));

        modalFreqField = new GuiTextField(3, fontRenderer, modalLeft + 60, modalTop + 90, 110, 12);
        modalFreqField.setMaxStringLength(20);
        modalFreqField.setText(MaintainerEntry.formatTime(modalLastFrequency));

        // Frequency buttons
        int btnX = modalLeft + 3;
        int btnY = modalTop + 48;
        int id = 100;
        modalButtons.add(new GuiButton(id++, btnX, btnY, 26, 12, "-1s"));
        modalButtons.add(new GuiButton(id++, btnX + 28, btnY, 26, 12, "+1s"));
        btnY += 14;
        modalButtons.add(new GuiButton(id++, btnX, btnY, 26, 12, "-1m"));
        modalButtons.add(new GuiButton(id++, btnX + 28, btnY, 26, 12, "+1m"));
        btnY += 14;
        modalButtons.add(new GuiButton(id++, btnX, btnY, 26, 12, "-1h"));
        modalButtons.add(new GuiButton(id++, btnX + 28, btnY, 26, 12, "+1h"));
        btnY += 14;
        modalButtons.add(new GuiButton(id++, btnX, btnY, 26, 12, "-1d"));
        modalButtons.add(new GuiButton(id++, btnX + 28, btnY, 26, 12, "+1d"));
    }

    private void drawEntryModal(int mouseX, int mouseY, float partialTicks) {
        // Reset GL state completely before drawing modal
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        // Background
        mc.getTextureManager().bindTexture(MODAL_BACKGROUND);
        drawTexturedModalRect(modalLeft, modalTop, 0, 0, MODAL_WIDTH, MODAL_HEIGHT);

        // Hover highlights
        int localX = mouseX - modalLeft;
        int localY = mouseY - modalTop;
        if (localX >= 5 && localX < 23 && localY >= 5 && localY < 23) {
            drawRect(modalLeft + 5, modalTop + 5, modalLeft + 23, modalTop + 23, 0x40FFFFFF);
        }

        // Text fields
        modalTargetField.drawTextBox();
        modalBatchField.drawTextBox();
        modalFreqField.drawTextBox();

        // Buttons
        for (GuiButton btn : modalButtons) btn.drawButton(mc, mouseX, mouseY, partialTicks);

        // Labels
        fontRenderer.drawString(I18n.format("gui.ae2powertools.maintainer.modal.target"),
                modalLeft + 60, modalTop + 28, 0x404040);
        fontRenderer.drawString(I18n.format("gui.ae2powertools.maintainer.modal.batch"),
                modalLeft + 60, modalTop + 54, 0x404040);
        fontRenderer.drawString(I18n.format("gui.ae2powertools.maintainer.modal.frequency"),
                modalLeft + 60, modalTop + 80, 0x404040);

        // Draw item in slot with proper GL state
        if (modalEntry != null && modalEntry.getTargetItem() != null) {
            ItemStack stack = modalEntry.getTargetItemStack();

            GlStateManager.enableDepth();
            RenderHelper.enableGUIStandardItemLighting();
            itemRender.renderItemAndEffectIntoGUI(stack, modalLeft + 6, modalTop + 6);
            RenderHelper.disableStandardItemLighting();
            GlStateManager.disableDepth();

            String name = stack.getDisplayName();
            int maxW = 145;
            if (fontRenderer.getStringWidth(name) > maxW) {
                name = fontRenderer.trimStringToWidth(name, maxW - 6) + "...";
            }
            fontRenderer.drawString(name, modalLeft + 25, modalTop + 10,
                    modalEntry.isEnabled() ? 0x404040 : 0x808080);
        } else {
            fontRenderer.drawString(I18n.format("gui.ae2powertools.maintainer.modal.select_item"),
                    modalLeft + 25, modalTop + 10, 0x808080);
        }

        GlStateManager.enableDepth();
    }

    private void drawModalTooltip(int mouseX, int mouseY) {
        int localX = mouseX - modalLeft;
        int localY = mouseY - modalTop;
        List<String> tooltip = new ArrayList<>();

        // Item slot tooltip
        if (localX >= 5 && localX < 23 && localY >= 5 && localY < 23) {
            if (modalEntry != null && modalEntry.getTargetItem() != null) {
                ITooltipFlag flag = mc.gameSettings.advancedItemTooltips
                        ? ITooltipFlag.TooltipFlags.ADVANCED : ITooltipFlag.TooltipFlags.NORMAL;
                tooltip.addAll(modalEntry.getTargetItemStack().getTooltip(mc.player, flag));
                tooltip.add("");
            }

            tooltip.add("§7" + I18n.format("gui.ae2powertools.maintainer.modal.slot_left_click") + "§r");
            tooltip.add("§7" + I18n.format("gui.ae2powertools.maintainer.modal.slot_right_click") + "§r");
        }

        // Item name tooltip
        if (localX >= 25 && localX < 170 && localY >= 5 && localY < 23) {
            tooltip.add("§7" + I18n.format("gui.ae2powertools.maintainer.modal.name_click_toggle") + "§r");
        }

        if (!tooltip.isEmpty()) {
            GuiUtils.drawHoveringText(tooltip, mouseX, mouseY, width, height, -1, fontRenderer);
        }
    }

    // ==================== SELECTOR MODAL ====================

    private void openSelectorModal() {
        selectorItems.clear();
        selectorItems.addAll(container.getCraftableItems());
        selectorFiltered.clear();
        selectorFiltered.addAll(selectorItems);
        selectorScroll = 0;
        selectorVisible = true;
        initSelectorFields();
        updateSelectorScrollLimits();
    }

    private void closeSelectorModal() {
        selectorVisible = false;
    }

    private void initSelectorFields() {
        selectorSearchField = new GuiTextField(10, fontRenderer, selectorLeft + 80, selectorTop + 4, 90, 12);
        selectorSearchField.setMaxStringLength(50);
        selectorSearchField.setEnableBackgroundDrawing(true);
        selectorSearchField.setTextColor(0xFFFFFF);
        selectorSearchField.setFocused(true);
    }

    private void updateSelectorScrollLimits() {
        int totalRows = (selectorFiltered.size() + SELECTOR_COLS - 1) / SELECTOR_COLS;
        selectorMaxScroll = Math.max(0, totalRows - SELECTOR_ROWS);
        selectorScroll = Math.min(selectorScroll, selectorMaxScroll);
    }

    private void applySelectorFilter() {
        selectorFiltered.clear();
        String term = selectorSearchField.getText().toLowerCase().trim();

        if (term.isEmpty()) {
            selectorFiltered.addAll(selectorItems);
        } else {
            for (IAEItemStack stack : selectorItems) {
                if (stack.createItemStack().getDisplayName().toLowerCase().contains(term)) {
                    selectorFiltered.add(stack);
                }
            }
        }

        selectorScroll = 0;
        updateSelectorScrollLimits();
    }

    private void drawSelectorModal(int mouseX, int mouseY, float partialTicks) {
        // Reset GL state
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        // Background
        mc.getTextureManager().bindTexture(SELECTOR_BACKGROUND);
        drawTexturedModalRect(selectorLeft, selectorTop, 0, 0, SELECTOR_WIDTH, SELECTOR_HEIGHT);

        // Search field
        selectorSearchField.drawTextBox();

        // Title
        fontRenderer.drawString(I18n.format("gui.ae2powertools.maintainer.selector.title"),
                selectorLeft + 8, selectorTop + 6, 0x000000);

        // Grid
        selectorHoveredSlot = -1;
        for (int row = 0; row < SELECTOR_ROWS; row++) {
            for (int col = 0; col < SELECTOR_COLS; col++) {
                int slot = row * SELECTOR_COLS + col;
                int itemIdx = (selectorScroll + row) * SELECTOR_COLS + col;

                int x = selectorLeft + SELECTOR_GRID_X + col * SLOT_SIZE;
                int y = selectorTop + SELECTOR_GRID_Y + row * SLOT_SIZE;

                boolean hovered = mouseX >= x && mouseX < x + SLOT_SIZE &&
                        mouseY >= y && mouseY < y + SLOT_SIZE;

                if (hovered) {
                    selectorHoveredSlot = slot;
                    drawRect(x + 1, y + 1, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, 0x80FFFFFF);
                }

                if (itemIdx >= 0 && itemIdx < selectorFiltered.size()) {
                    IAEItemStack aeStack = selectorFiltered.get(itemIdx);
                    ItemStack stack = aeStack.createItemStack();

                    GlStateManager.enableDepth();
                    RenderHelper.enableGUIStandardItemLighting();
                    itemRender.renderItemAndEffectIntoGUI(stack, x + 1, y + 1);

                    long qty = aeStack.getStackSize();
                    if (qty > 1) {
                        String qtyStr = ReadableNumberConverter.INSTANCE.toWideReadableForm(qty);
                        itemRender.renderItemOverlayIntoGUI(fontRenderer, stack, x + 1, y + 1, qtyStr);
                    }

                    RenderHelper.disableStandardItemLighting();
                    GlStateManager.disableDepth();
                }
            }
        }

        // Scrollbar
        mc.getTextureManager().bindTexture(SCROLLBAR_TEXTURE);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        int sbX = selectorLeft + 175;
        int sbY = selectorTop + 18;
        if (selectorMaxScroll <= 0) {
            drawTexturedModalRect(sbX, sbY, 244, 0, 12, 15);
        } else {
            int thumbY = sbY + (162 - 15) * selectorScroll / selectorMaxScroll;
            drawTexturedModalRect(sbX, thumbY, 232, 0, 12, 15);
        }

        GlStateManager.enableDepth();
    }

    private void drawSelectorTooltip(int mouseX, int mouseY) {
        if (selectorHoveredSlot < 0) return;

        int itemIdx = selectorScroll * SELECTOR_COLS + selectorHoveredSlot;
        if (itemIdx < 0 || itemIdx >= selectorFiltered.size()) return;

        IAEItemStack aeStack = selectorFiltered.get(itemIdx);
        ItemStack stack = aeStack.createItemStack();

        List<String> tooltip = new ArrayList<>();
        ITooltipFlag flag = mc.gameSettings.advancedItemTooltips
                ? ITooltipFlag.TooltipFlags.ADVANCED : ITooltipFlag.TooltipFlags.NORMAL;
        tooltip.addAll(stack.getTooltip(mc.player, flag));

        GuiUtils.drawHoveringText(tooltip, mouseX, mouseY, width, height, -1, fontRenderer);
    }

    private void selectRecipe(IAEItemStack item) {
        PacketSelectRecipe packet = new PacketSelectRecipe(
                container.getMaintainer().getPos(),
                modalEntryIndex,
                item
        );
        PowerToolsNetwork.INSTANCE.sendToServer(packet);

        // Update local modal entry
        if (modalEntry != null) modalEntry.setTargetItem(item);

        closeSelectorModal();
    }

    // ==================== MAIN GUI DRAWING ====================

    private void drawEntries(int mouseX, int mouseY) {
        TileBetterLevelMaintainer maintainer = container.getMaintainer();
        String searchTerm = searchField.getText().toLowerCase();
        hoveredEntryIndex = -1;

        boolean checkHover = !modalVisible && !selectorVisible;

        int displayIndex = 0;
        for (int entryIdx = scrollOffset * COLUMNS; entryIdx < maintainer.getOpenSlots(); entryIdx++) {
            MaintainerEntry entry = maintainer.getEntry(entryIdx);
            if (entry == null) continue;

            if (!searchTerm.isEmpty() && entry.hasRecipe()) {
                String name = entry.getTargetItemStack().getDisplayName().toLowerCase();
                if (!name.contains(searchTerm)) continue;
            }

            int row = displayIndex / COLUMNS;
            int col = displayIndex % COLUMNS;
            if (row >= VISIBLE_ROWS) break;

            int x = guiLeft + ENTRY_START_X + col * ENTRY_WIDTH;
            int y = guiTop + ENTRY_START_Y + row * ENTRY_HEIGHT;

            boolean hovered = checkHover && mouseX >= x && mouseX < x + ENTRY_WIDTH &&
                    mouseY >= y && mouseY < y + ENTRY_HEIGHT;
            if (hovered) hoveredEntryIndex = entryIdx;

            // Background
            int bgColor = entry.getState().getBackgroundColor();
            if (bgColor != 0) drawRect(x, y + 1, x + ENTRY_WIDTH - 1, y + ENTRY_HEIGHT, bgColor);
            if (hovered) drawRect(x, y + 1, x + ENTRY_WIDTH - 1, y + ENTRY_HEIGHT, 0x40FFFFFF);

            // Content
            if (entry.hasRecipe()) {
                // Draw slot background for item icon (scaled 16x16 slot at 0.75 scale = 12x12)
                int slotX = x + 1;
                int slotY = y + 2;
                int slotSize = 12;
                drawRect(slotX, slotY, slotX + slotSize, slotY + slotSize, 0xFF373737);  // Slot border (top/left)
                drawRect(slotX + 1, slotY + 1, slotX + slotSize, slotY + slotSize, 0xFFFFFFFF);  // Slot border (bottom/right)
                drawRect(slotX + 1, slotY + 1, slotX + slotSize - 1, slotY + slotSize - 1, 0xFF8B8B8B);  // Slot inner

                GlStateManager.pushMatrix();
                float scale = 0.75F;
                GlStateManager.scale(scale, scale, 1.0F);

                ItemStack stack = entry.getTargetItemStack();
                RenderHelper.enableGUIStandardItemLighting();
                itemRender.renderItemIntoGUI(stack, (int) ((x + 1) / scale), (int) ((y + 2) / scale));

                GlStateManager.popMatrix();

                int maxW = ENTRY_WIDTH - 2;

                String qtyText = String.format("%s/%s",
                        ReadableNumberConverter.INSTANCE.toSlimReadableForm(entry.getCurrentQuantity()),
                        ReadableNumberConverter.INSTANCE.toSlimReadableForm(entry.getTargetQuantity()));
                int qtyX = x + maxW - fontRenderer.getStringWidth(qtyText);
                fontRenderer.drawString(qtyText, qtyX, y + 4, entry.isEnabled() ? 0x000000 : 0x808080);

                String freqText = entry.formatFrequency();
                if (fontRenderer.getStringWidth(freqText) > maxW) {
                    freqText = fontRenderer.trimStringToWidth(freqText, maxW - fontRenderer.getStringWidth("...")) + "...";
                }
                int freqX = maxW - fontRenderer.getStringWidth(freqText) + x;
                fontRenderer.drawString(freqText, freqX, y + 15, 0x000000);
            }

            displayIndex++;
        }
    }

    private void drawScrollbar() {
        int x = guiLeft + SCROLLBAR_X;
        int y = guiTop + SCROLLBAR_Y;

        mc.getTextureManager().bindTexture(SCROLLBAR_TEXTURE);
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);

        if (maxScroll <= 0) {
            drawTexturedModalRect(x, y, 232, 0, SCROLLBAR_WIDTH, 15);
        } else {
            int offset = scrollOffset * (SCROLLBAR_HEIGHT - 15) / maxScroll;
            drawTexturedModalRect(x, y + offset, 232, 0, SCROLLBAR_WIDTH, 15);
        }
    }

    private void drawStatusBar() {
        int y = guiTop + STATUS_Y;
        int halfW = (xSize - 16) / 2;
        int leftX = guiLeft + 8;
        int rightX = leftX + halfW;

        int activeCpus = container.getActiveCpuCount();
        int totalCpus = container.getTotalCpuCount();
        String cpuText = String.format("§a%d§r / §8%d§r", activeCpus, totalCpus);
        cpuTextWidth = fontRenderer.getStringWidth(String.format("%d / %d", activeCpus, totalCpus));
        cpuX = leftX + (halfW - cpuTextWidth) / 2;
        fontRenderer.drawStringWithShadow(cpuText, cpuX, y, 0xFFFFFF);

        int running = container.getRunningRecipeCount();
        int total = container.getTotalRecipeCount();
        int failed = container.getFailedRecipeCount();
        int postErr = container.getPostErrorRecipeCount();
        String recipeText = String.format("§a%d§r / §8%d§r / §c%d§r / §5%d§r", running, total, failed, postErr);
        recipeTextWidth = fontRenderer.getStringWidth(String.format("%d / %d / %d / %d", running, total, failed, postErr));
        recipeX = rightX + (halfW - recipeTextWidth) / 2;
        fontRenderer.drawStringWithShadow(recipeText, recipeX, y, 0xFFFFFF);
    }

    private void drawEntryTooltips(int mouseX, int mouseY) {
        if (hoveredEntryIndex < 0) return;

        MaintainerEntry entry = container.getMaintainer().getEntry(hoveredEntryIndex);
        if (entry == null) return;

        List<String> tooltip = new ArrayList<>();
        if (entry.hasRecipe()) {
            tooltip.add(entry.getTargetItemStack().getDisplayName());

            String countText = String.format("%s / %s", entry.getCurrentQuantity(), entry.getTargetQuantity());
            tooltip.add("§a" + countText + "§r");

            long batchSize = entry.getBatchSize();
            String batchText = I18n.format("gui.ae2powertools.maintainer.tooltip.batch_size", batchSize);
            tooltip.add("§7" + batchText + "§r");

            String freqText = I18n.format("gui.ae2powertools.maintainer.tooltip.frequency",
                    entry.formatFrequency());
            tooltip.add("§7" + freqText + "§r");

            tooltip.add("");
            MaintainerState state = entry.getState();
            String stateKey = "gui.ae2powertools.maintainer.state." + state.name().toLowerCase();
            String stateColor = getStateTextColor(state);
            tooltip.add(stateColor + I18n.format(stateKey) + "§r");

            // Show error message for error states
            if (state.isError() && entry.getErrorMessage() != null) {
                tooltip.add("§c" + I18n.format(entry.getErrorMessage()) + "§r");
            }

            tooltip.add("");
            tooltip.add("§7" + I18n.format("gui.ae2powertools.maintainer.tooltip.click_edit") + "§r");
            tooltip.add("§7" + I18n.format("gui.ae2powertools.maintainer.tooltip.right_click_toggle") + "§r");
            tooltip.add("§7" + I18n.format("gui.ae2powertools.maintainer.tooltip.shift_scroll_quantity") + "§r");
            tooltip.add("§7" + I18n.format("gui.ae2powertools.maintainer.tooltip.ctrl_scroll_frequency") + "§r");
        } else {
            tooltip.add(I18n.format("gui.ae2powertools.maintainer.tooltip.empty"));
            tooltip.add("§7" + I18n.format("gui.ae2powertools.maintainer.tooltip.click_add") + "§r");
        }

        drawHoveringText(tooltip, mouseX - guiLeft, mouseY - guiTop);
    }

    private void drawStatusBarTooltips(int mouseX, int mouseY) {
        int statusY = useTallView ? guiTop + ySize - TALL_STATUS_OFFSET : guiTop + STATUS_Y;
        if (mouseY < statusY || mouseY > statusY + 12) return;

        List<String> tooltip = new ArrayList<>();

        if (mouseX >= cpuX && mouseX < cpuX + cpuTextWidth) {
            tooltip.add("§e" + I18n.format("gui.ae2powertools.maintainer.status.cpu_title") + "§r");
            tooltip.add("§a" + I18n.format("gui.ae2powertools.maintainer.status.cpu_active", container.getActiveCpuCount()) + "§r");
            tooltip.add("§7" + I18n.format("gui.ae2powertools.maintainer.status.cpu_total", container.getTotalCpuCount()) + "§r");
        }

        if (mouseX >= recipeX && mouseX < recipeX + recipeTextWidth) {
            tooltip.add("§e" + I18n.format("gui.ae2powertools.maintainer.status.recipe_title") + "§r");
            tooltip.add("§a" + I18n.format("gui.ae2powertools.maintainer.status.recipe_running", container.getRunningRecipeCount()) + "§r");
            tooltip.add("§7" + I18n.format("gui.ae2powertools.maintainer.status.recipe_total", container.getTotalRecipeCount()) + "§r");
            tooltip.add("§c" + I18n.format("gui.ae2powertools.maintainer.status.recipe_failed", container.getFailedRecipeCount()) + "§r");
            tooltip.add("§5" + I18n.format("gui.ae2powertools.maintainer.status.recipe_post_error", container.getPostErrorRecipeCount()) + "§r");
        }

        if (!tooltip.isEmpty()) drawHoveringText(tooltip, mouseX - guiLeft, mouseY - guiTop);
    }

    // ==================== TALL MODE DRAWING ====================

    /**
     * Draw entries in tall mode (single column, more detailed).
     */
    private void drawEntriesTall(int mouseX, int mouseY) {
        TileBetterLevelMaintainer maintainer = container.getMaintainer();
        String searchTerm = searchField.getText().toLowerCase();
        hoveredEntryIndex = -1;

        boolean checkHover = !modalVisible && !selectorVisible;

        int displayIndex = 0;
        for (int entryIdx = scrollOffset; entryIdx < maintainer.getOpenSlots(); entryIdx++) {
            MaintainerEntry entry = maintainer.getEntry(entryIdx);
            if (entry == null) continue;

            if (!searchTerm.isEmpty() && entry.hasRecipe()) {
                String name = entry.getTargetItemStack().getDisplayName().toLowerCase();
                if (!name.contains(searchTerm)) continue;
            }

            if (displayIndex >= tallVisibleRows) break;

            int x = guiLeft + ENTRY_START_X;
            int y = guiTop + TALL_SLICE_START_Y + displayIndex * TALL_SLICE_HEIGHT;
            int entryW = xSize - ENTRY_START_X * 2 - SCROLLBAR_WIDTH - 4;

            boolean hovered = checkHover && mouseX >= x && mouseX < x + entryW &&
                    mouseY >= y && mouseY < y + TALL_SLICE_HEIGHT;
            if (hovered) hoveredEntryIndex = entryIdx;

            // Background
            int bgColor = entry.getState().getBackgroundColor();
            if (bgColor != 0) drawRect(x, y, x + entryW, y + TALL_SLICE_HEIGHT - 1, bgColor);
            if (hovered) drawRect(x, y, x + entryW, y + TALL_SLICE_HEIGHT - 1, 0x40FFFFFF);

            // Content
            if (entry.hasRecipe()) {
                // Draw full-size item icon (16x16)
                ItemStack stack = entry.getTargetItemStack();

                GlStateManager.enableDepth();
                RenderHelper.enableGUIStandardItemLighting();
                itemRender.renderItemAndEffectIntoGUI(stack, x + 2, y + 2);
                RenderHelper.disableStandardItemLighting();
                GlStateManager.disableDepth();

                // Item name
                String name = stack.getDisplayName();
                int nameMaxW = entryW - 24 - 100;
                if (fontRenderer.getStringWidth(name) > nameMaxW) {
                    name = fontRenderer.trimStringToWidth(name, nameMaxW - 6) + "...";
                }
                fontRenderer.drawString(name, x + 22, y + 3, entry.isEnabled() ? 0x000000 : 0x808080);

                // State indicator (short form to avoid overlapping with frequency)
                MaintainerState state = entry.getState();
                String stateText = I18n.format("gui.ae2powertools.maintainer.state.short." + state.name().toLowerCase());
                int stateColor = state.getTextColor();
                fontRenderer.drawString(stateText, x + 22, y + 12, stateColor);

                // Quantity and frequency on the right
                String qtyText = String.format("%s / %s",
                        ReadableNumberConverter.INSTANCE.toWideReadableForm(entry.getCurrentQuantity()),
                        ReadableNumberConverter.INSTANCE.toWideReadableForm(entry.getTargetQuantity()));
                int qtyX = x + entryW - fontRenderer.getStringWidth(qtyText) - 2;
                fontRenderer.drawString(qtyText, qtyX, y + 3, entry.isEnabled() ? 0x000000 : 0x808080);

                String freqText = entry.formatFrequency();
                int freqX = x + entryW - fontRenderer.getStringWidth(freqText) - 2;
                fontRenderer.drawString(freqText, freqX, y + 12, 0x606060);
            } else {
                // Empty slot text
                fontRenderer.drawString(I18n.format("gui.ae2powertools.maintainer.tooltip.empty"),
                        x + 4, y + 7, 0x808080);
            }

            displayIndex++;
        }
    }

    /**
     * Draw scrollbar in tall mode.
     */
    private void drawScrollbarTall() {
        int x = guiLeft + SCROLLBAR_X;
        int y = guiTop + TALL_SLICE_START_Y;

        mc.getTextureManager().bindTexture(SCROLLBAR_TEXTURE);
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);

        if (maxScroll <= 0) {
            drawTexturedModalRect(x, y, 232, 0, SCROLLBAR_WIDTH, 15);
        } else {
            int offset = scrollOffset * (tallScrollbarHeight - 15) / maxScroll;
            drawTexturedModalRect(x, y + offset, 232, 0, SCROLLBAR_WIDTH, 15);
        }
    }

    /**
     * Draw status bar in tall mode.
     */
    private void drawStatusBarTall() {
        int statusY = guiTop + ySize - TALL_STATUS_OFFSET;
        int halfW = (xSize - 16) / 2;
        int leftX = guiLeft + 8;
        int rightX = leftX + halfW;

        int activeCpus = container.getActiveCpuCount();
        int totalCpus = container.getTotalCpuCount();
        String cpuText = String.format("§a%d§r / §8%d§r", activeCpus, totalCpus);
        cpuTextWidth = fontRenderer.getStringWidth(String.format("%d / %d", activeCpus, totalCpus));
        cpuX = leftX + (halfW - cpuTextWidth) / 2;
        fontRenderer.drawStringWithShadow(cpuText, cpuX, statusY, 0xFFFFFF);

        int running = container.getRunningRecipeCount();
        int total = container.getTotalRecipeCount();
        int failed = container.getFailedRecipeCount();
        int postErr = container.getPostErrorRecipeCount();
        String recipeText = String.format("§a%d§r / §8%d§r / §c%d§r / §5%d§r", running, total, failed, postErr);
        recipeTextWidth = fontRenderer.getStringWidth(String.format("%d / %d / %d / %d", running, total, failed, postErr));
        recipeX = rightX + (halfW - recipeTextWidth) / 2;
        fontRenderer.drawStringWithShadow(recipeText, recipeX, statusY, 0xFFFFFF);
    }

    /**
     * Draw entry tooltips in tall mode (same as small mode).
     */
    private void drawEntryTooltipsTall(int mouseX, int mouseY) {
        drawEntryTooltips(mouseX, mouseY);
    }

    // ==================== INPUT HANDLING ====================

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        // Selector modal takes priority
        if (selectorVisible) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                closeSelectorModal();
                return;
            }

            if (selectorSearchField.textboxKeyTyped(typedChar, keyCode)) {
                applySelectorFilter();
                return;
            }

            return;
        }

        // Entry modal
        if (modalVisible) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                closeEntryModal(true);
                return;
            }

            if (modalTargetField.textboxKeyTyped(typedChar, keyCode)) {
                reformatQuantityField(modalTargetField);
                return;
            }

            if (modalBatchField.textboxKeyTyped(typedChar, keyCode)) {
                reformatQuantityField(modalBatchField);
                return;
            }

            return;
        }

        // Main GUI
        if (searchField.textboxKeyTyped(typedChar, keyCode)) return;
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        // Selector modal
        if (selectorVisible) {
            handleSelectorClick(mouseX, mouseY, mouseButton);
            return;
        }

        // Entry modal
        if (modalVisible) {
            handleModalClick(mouseX, mouseY, mouseButton);
            return;
        }

        // Style toggle button
        if (mouseButton == 0 && styleButtonHovered) {
            toggleViewStyle();
            return;
        }

        // Main GUI
        super.mouseClicked(mouseX, mouseY, mouseButton);
        searchField.mouseClicked(mouseX, mouseY, mouseButton);

        // Scrollbar (position differs between modes)
        if (maxScroll > 0) {
            int sbX, sbY, sbH;
            if (useTallView) {
                sbX = guiLeft + SCROLLBAR_X;
                sbY = guiTop + TALL_SLICE_START_Y;
                sbH = tallScrollbarHeight;
            } else {
                sbX = guiLeft + SCROLLBAR_X;
                sbY = guiTop + SCROLLBAR_Y;
                sbH = SCROLLBAR_HEIGHT;
            }

            if (mouseX >= sbX && mouseX < sbX + SCROLLBAR_WIDTH && mouseY >= sbY && mouseY < sbY + sbH) {
                isDraggingScrollbar = true;
                return;
            }
        }

        // Entry click
        int clicked = getEntryAtPosition(mouseX, mouseY);
        if (clicked >= 0) {
            if (mouseButton == 0) {
                openEntryModal(clicked);
            } else if (mouseButton == 1) {
                // Right-click: toggle enabled state
                MaintainerEntry entry = container.getMaintainer().getEntry(clicked);
                if (entry != null && entry.hasRecipe()) {
                    sendEntryUpdate(clicked, entry, entry.getTargetQuantity(), entry.getBatchSize(),
                            entry.getFrequencySeconds(), !entry.isEnabled());
                }
            }
        }
    }

    private void handleModalClick(int mouseX, int mouseY, int mouseButton) {
        // Click outside closes modal
        if (mouseX < modalLeft || mouseX >= modalLeft + MODAL_WIDTH ||
                mouseY < modalTop || mouseY >= modalTop + MODAL_HEIGHT) {
            closeEntryModal(true);
            return;
        }

        // Text fields
        modalTargetField.mouseClicked(mouseX, mouseY, mouseButton);
        modalBatchField.mouseClicked(mouseX, mouseY, mouseButton);

        // Buttons
        for (GuiButton btn : modalButtons) {
            if (btn.mousePressed(mc, mouseX, mouseY)) {
                handleFrequencyButton(btn);
                return;
            }
        }

        int localX = mouseX - modalLeft;
        int localY = mouseY - modalTop;

        // Item slot click
        if (localX >= 5 && localX < 23 && localY >= 5 && localY < 23) {
            if (mouseButton == 0) {
                openSelectorModal();
            } else if (mouseButton == 1 && modalEntry != null) {
                modalEntry.setTargetItem(null);
            }
        }

        // Name area click - toggle enabled
        if (localX >= 25 && localX < 170 && localY >= 5 && localY < 23) {
            if (modalEntry != null && modalEntry.getTargetItem() != null) {
                modalEntry.setEnabled(!modalEntry.isEnabled());
            }
        }
    }

    private void handleFrequencyButton(GuiButton btn) {
        int delta = 0;
        String label = btn.displayString;
        if (label.equals("-1s")) delta = -1;
        else if (label.equals("+1s")) delta = 1;
        else if (label.equals("-1m")) delta = -60;
        else if (label.equals("+1m")) delta = 60;
        else if (label.equals("-1h")) delta = -3600;
        else if (label.equals("+1h")) delta = 3600;
        else if (label.equals("-1d")) delta = -86400;
        else if (label.equals("+1d")) delta = 86400;

        if (delta != 0) {
            modalLastFrequency = Math.max(1, modalLastFrequency + delta);
            modalFreqField.setText(MaintainerEntry.formatTime(modalLastFrequency));
        }
    }

    private void handleSelectorClick(int mouseX, int mouseY, int mouseButton) {
        // Click outside closes
        if (mouseX < selectorLeft || mouseX >= selectorLeft + SELECTOR_WIDTH ||
                mouseY < selectorTop || mouseY >= selectorTop + SELECTOR_HEIGHT) {
            closeSelectorModal();
            return;
        }

        selectorSearchField.mouseClicked(mouseX, mouseY, mouseButton);

        // Scrollbar
        if (selectorMaxScroll > 0) {
            int sbX = selectorLeft + 175;
            int sbY = selectorTop + 18;
            if (mouseX >= sbX && mouseX < sbX + 12 && mouseY >= sbY && mouseY < sbY + 162) {
                selectorDragging = true;
                return;
            }
        }

        // Item slot click
        if (selectorHoveredSlot >= 0 && mouseButton == 0) {
            int itemIdx = selectorScroll * SELECTOR_COLS + selectorHoveredSlot;
            if (itemIdx >= 0 && itemIdx < selectorFiltered.size()) {
                selectRecipe(selectorFiltered.get(itemIdx));
            }
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);
        isDraggingScrollbar = false;
        selectorDragging = false;
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (selectorDragging && selectorMaxScroll > 0) {
            int sbY = selectorTop + 18;
            float ratio = (float) (mouseY - sbY) / 162;
            selectorScroll = Math.round(ratio * selectorMaxScroll);
            selectorScroll = Math.max(0, Math.min(selectorScroll, selectorMaxScroll));

            return;
        }

        if (isDraggingScrollbar && maxScroll > 0) {
            int sbY, sbH;
            if (useTallView) {
                sbY = guiTop + TALL_SLICE_START_Y;
                sbH = tallScrollbarHeight;
            } else {
                sbY = guiTop + SCROLLBAR_Y;
                sbH = SCROLLBAR_HEIGHT;
            }

            float ratio = (float) (mouseY - sbY) / sbH;
            scrollOffset = Math.round(ratio * maxScroll);
            scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

            return;
        }

        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();

        int scroll = Mouse.getEventDWheel();
        if (scroll == 0) return;

        if (selectorVisible) {
            selectorScroll += (scroll < 0) ? 1 : -1;
            selectorScroll = Math.max(0, Math.min(selectorScroll, selectorMaxScroll));
            return;
        }

        if (modalVisible) return;

        // Check for modifier-based scroll actions on hovered entry
        int mouseX = Mouse.getEventX() * width / mc.displayWidth;
        int mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        int hovered = getEntryAtPosition(mouseX, mouseY);

        if (hovered >= 0) {
            MaintainerEntry entry = container.getMaintainer().getEntry(hovered);
            if (entry != null && entry.hasRecipe()) {
                boolean shiftDown = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
                boolean ctrlDown = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);

                if (shiftDown) {
                    // Shift+scroll: double/halve target quantity
                    long newQty = entry.getTargetQuantity();
                    if (scroll > 0) {
                        // Scroll up: double (prevent overflow)
                        if (newQty <= Long.MAX_VALUE / 2) {
                            newQty *= 2;
                        } else {
                            newQty = Long.MAX_VALUE;
                        }
                    } else {
                        // Scroll down: halve (minimum 1)
                        newQty = Math.max(1, newQty / 2);
                    }

                    sendEntryUpdate(hovered, entry, newQty, entry.getBatchSize(), entry.getFrequencySeconds(), entry.isEnabled());
                    return;
                }

                if (ctrlDown) {
                    // Ctrl+scroll: double/halve frequency
                    int newFreq = entry.getFrequencySeconds();
                    if (scroll > 0) {
                        // Scroll up: double time (slower, prevent overflow)
                        if (newFreq <= Integer.MAX_VALUE / 2) {
                            newFreq *= 2;
                        } else {
                            newFreq = Integer.MAX_VALUE;
                        }
                    } else {
                        // Scroll down: halve time (faster, minimum 1)
                        newFreq = Math.max(1, newFreq / 2);
                    }

                    sendEntryUpdate(hovered, entry, entry.getTargetQuantity(), entry.getBatchSize(), newFreq, entry.isEnabled());
                    return;
                }
            }
        }

        // Default scroll behavior: scroll the entry list
        scrollOffset += (scroll < 0) ? 1 : -1;
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
    }

    /**
     * Sends an update packet for a maintainer entry.
     */
    private void sendEntryUpdate(int entryIndex, MaintainerEntry entry, long targetQty, long batchSize, int frequency, boolean enabled) {
        PacketUpdateMaintainerEntry packet = new PacketUpdateMaintainerEntry(
                container.getMaintainer().getPos(),
                entryIndex,
                entry.getTargetItem(),
                targetQty,
                batchSize,
                frequency,
                enabled
        );
        PowerToolsNetwork.INSTANCE.sendToServer(packet);
    }

    private int getEntryAtPosition(int mouseX, int mouseY) {
        TileBetterLevelMaintainer maintainer = container.getMaintainer();
        String searchTerm = searchField.getText().toLowerCase();

        if (useTallView) return getEntryAtPositionTall(mouseX, mouseY, maintainer, searchTerm);

        int displayIndex = 0;
        for (int entryIdx = scrollOffset * COLUMNS; entryIdx < maintainer.getOpenSlots(); entryIdx++) {
            MaintainerEntry entry = maintainer.getEntry(entryIdx);
            if (entry == null) continue;

            if (!searchTerm.isEmpty() && entry.hasRecipe()) {
                String name = entry.getTargetItemStack().getDisplayName().toLowerCase();
                if (!name.contains(searchTerm)) continue;
            }

            int row = displayIndex / COLUMNS;
            int col = displayIndex % COLUMNS;
            if (row >= VISIBLE_ROWS) break;

            int x = guiLeft + ENTRY_START_X + col * ENTRY_WIDTH;
            int y = guiTop + ENTRY_START_Y + row * ENTRY_HEIGHT;

            if (mouseX >= x && mouseX < x + ENTRY_WIDTH && mouseY >= y && mouseY < y + ENTRY_HEIGHT) {
                return entryIdx;
            }

            displayIndex++;
        }

        return -1;
    }

    /**
     * Get entry at position in tall mode.
     */
    private int getEntryAtPositionTall(int mouseX, int mouseY, TileBetterLevelMaintainer maintainer, String searchTerm) {
        int displayIndex = 0;
        int entryW = xSize - ENTRY_START_X * 2 - SCROLLBAR_WIDTH - 4;

        for (int entryIdx = scrollOffset; entryIdx < maintainer.getOpenSlots(); entryIdx++) {
            MaintainerEntry entry = maintainer.getEntry(entryIdx);
            if (entry == null) continue;

            if (!searchTerm.isEmpty() && entry.hasRecipe()) {
                String name = entry.getTargetItemStack().getDisplayName().toLowerCase();
                if (!name.contains(searchTerm)) continue;
            }

            if (displayIndex >= tallVisibleRows) break;

            int x = guiLeft + ENTRY_START_X;
            int y = guiTop + TALL_SLICE_START_Y + displayIndex * TALL_SLICE_HEIGHT;

            if (mouseX >= x && mouseX < x + entryW && mouseY >= y && mouseY < y + TALL_SLICE_HEIGHT) {
                return entryIdx;
            }

            displayIndex++;
        }

        return -1;
    }

    /**
     * Returns the Minecraft text color code for a maintainer state.
     */
    private String getStateTextColor(MaintainerState state) {
        switch (state) {
            case DISABLED:
                return "§8";  // Dark gray
            case IDLE:
                return "§7";  // Gray
            case SCHEDULED:
                return "§b";  // Aqua
            case RUNNING:
                return "§a";  // Green
            case STALLED:
                return "§e";  // Yellow
            case ERROR:
                return "§c";  // Red
            case POST_ERROR:
                return "§5";  // Purple
            default:
                return "§f";  // White
        }
    }

    /**
     * Reformats a quantity field with proper comma separators.
     * Preserves cursor position relative to digits.
     */
    private void reformatQuantityField(GuiTextField field) {
        String text = field.getText();
        int cursorPos = field.getCursorPosition();

        // Count digits before cursor in the original text
        int digitsBefore = 0;
        for (int i = 0; i < cursorPos && i < text.length(); i++) {
            if (Character.isDigit(text.charAt(i))) digitsBefore++;
        }

        // Parse and reformat
        long value = MaintainerEntry.parseQuantity(text);
        if (value < 0) return;  // Invalid input, don't reformat

        String formatted = MaintainerEntry.formatQuantity(value);
        field.setText(formatted);

        // Restore cursor position based on digit count
        int newCursor = 0;
        int digitsSeen = 0;
        for (int i = 0; i < formatted.length(); i++) {
            if (Character.isDigit(formatted.charAt(i))) {
                digitsSeen++;
                if (digitsSeen > digitsBefore) break;
            }
            newCursor = i + 1;
        }

        field.setCursorPosition(Math.min(newCursor, formatted.length()));
    }

    // ==================== JEI INTEGRATION ====================

    /**
     * Returns areas that JEI should not overlap with its panels.
     * Used for the style toggle button on the left side of the GUI.
     */
    public List<Rectangle> getJEIExclusionArea() {
        List<Rectangle> areas = new ArrayList<>();

        // Style toggle button
        areas.add(new Rectangle(styleButtonX, styleButtonY, STYLE_BUTTON_SIZE, STYLE_BUTTON_SIZE));

        return areas;
    }
}
