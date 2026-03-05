package com.ae2powertools.features.crafter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.client.config.GuiUtils;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.storage.data.IAEItemStack;
import appeng.util.ReadableNumberConverter;
import appeng.util.item.AEItemStack;

import com.ae2powertools.Tags;
import com.ae2powertools.network.PowerToolsNetwork;
import com.ae2powertools.util.FormatUtil;


/**
 * GUI for the AE2 AutoCrafter.
 * Recipe view (per-entry) with Overview modal overlay.
 * 
 * Modal system: Overview is drawn on top of the recipe view when overviewMode=true.
 * Upgrade slots and Batch/Speed buttons remain accessible in both modes.
 */
@SideOnly(Side.CLIENT)
public class GuiAutoCrafter extends GuiContainer {

    private static final ResourceLocation RECIPE_TEXTURE = new ResourceLocation(
            Tags.MODID, "textures/guis/crafter_recipe.png");
    private static final ResourceLocation OVERVIEW_TEXTURE = new ResourceLocation(
            Tags.MODID, "textures/guis/crafter_overview.png");
    private static final ResourceLocation AE2_STATES = new ResourceLocation(
            "appliedenergistics2", "textures/guis/states.png");
    private static final ResourceLocation BATCH_BUTTON_TEXTURE = new ResourceLocation(
            Tags.MODID, "textures/guis/batch_button.png");
    private static final ResourceLocation SPEED_BUTTON_TEXTURE = new ResourceLocation(
            Tags.MODID, "textures/guis/speed_button.png");

    // GUI dimensions
    private static final int GUI_WIDTH = 211;
    private static final int GUI_HEIGHT = 248;

    // Recipe view layout
    private static final int PATTERN_SLOT_X = 17;
    private static final int PATTERN_SLOT_Y = 43;
    private static final int RECIPE_GRID_X = 47;
    private static final int RECIPE_GRID_Y = 25;
    private static final int RECIPE_RESULT_X = 136;
    private static final int RECIPE_RESULT_Y = 40;
    private static final int RECIPE_RESULT_SIZE = 22;  // 22x22 slot area
    private static final int CATALYST_START_X = 8;
    private static final int CATALYST_START_Y = 90;
    private static final int SPEED_INFO_X = 6;
    private static final int SPEED_INFO_Y = 110;      // Under catalyst slots (90 + 18 + 2)
    private static final int STATE_INDICATOR_X = 6;
    private static final int STATE_INDICATOR_Y = 122;  // Under speed info (110 + 12)
    private static final int PAGE_LEFT_X = 7;
    private static final int PAGE_LEFT_Y = 137;
    private static final int PAGE_RIGHT_X = 157;
    private static final int PAGE_RIGHT_Y = 137;
    private static final int UPGRADE_START_X = 187;
    private static final int UPGRADE_START_Y = 8;
    private static final int UPGRADE_SLOT_SIZE = 18;

    // Overview modal layout (centered on top of recipe view)
    private static final int OVERVIEW_MODAL_WIDTH = 176;
    private static final int OVERVIEW_MODAL_HEIGHT = 248;
    private static final int OVERVIEW_ROW_X = 7;
    private static final int OVERVIEW_ROW_Y = 25;
    private static final int OVERVIEW_ROW_WIDTH = 162;
    private static final int OVERVIEW_ROW_HEIGHT = 18;

    // Button positions (top right) - AE2 style buttons
    private static final int BATCH_BTN_X = 132;
    private static final int BATCH_BTN_Y = 0;
    private static final int SPEED_BTN_X = 154;
    private static final int SPEED_BTN_Y = 0;
    private static final int TAB_BTN_SIZE = 22;

    // Custom page navigation buttons (square, 12x12) - vanilla style
    private static final int PAGE_BTN_SIZE = 12;
    // Overview/back button position (top LEFT, before title) - same position for both modes
    private static final int OVERVIEW_BTN_X = 5;
    private static final int OVERVIEW_BTN_Y = 5;

    private final ContainerAutoCrafter container;

    // View state (currentPage is synced from tile)
    private boolean overviewMode = false;

    // Custom image buttons for batch and speed (drawn manually, not GuiButton)
    private boolean batchButtonHovered = false;
    private boolean speedButtonHovered = false;

    // Custom drawn elements (no GuiButton for page nav - drawn manually)
    private boolean pagePrevHovered = false;
    private boolean pageNextHovered = false;
    private boolean overviewBtnHovered = false;
    private boolean overviewCloseBtnHovered = false;

    // Ignore NBT toggle button
    private static final int IGNORE_NBT_BTN_X = 150;
    private static final int IGNORE_NBT_BTN_Y = STATE_INDICATOR_Y;

    // Hover state for Ignore NBT button
    private boolean ignoreNbtBtnHovered = false;

    // Hovered elements
    private int hoveredRecipeSlot = -1;
    private int hoveredOverviewRow = -1;
    private boolean hoveredResult = false;

    // Overview modal position
    private int overviewLeft, overviewTop;

    // Track last known page for client-side slot updates when synced page changes
    private int lastKnownPage = -1;

    // ==================== PACKET-SYNCED CLIENT STATE ====================
    // These are populated by handleStateSync() when packets arrive from server.
    // This replaces the unreliable AE2 stream-based syncing.

    private boolean hasSyncedData = false;
    private int syncedSpeedTicks = TileAutoCrafter.DEFAULT_SPEED_TICKS;
    private int syncedBatchSize = TileAutoCrafter.DEFAULT_BATCH_SIZE;
    private int syncedEffectiveBatchSize = TileAutoCrafter.DEFAULT_BATCH_SIZE;
    private int syncedCurrentPage = 0;

    // Per-entry synced data
    private final CrafterState[] syncedStates = new CrafterState[TileAutoCrafter.ENTRY_COUNT];
    private final boolean[] syncedHasDisplayData = new boolean[TileAutoCrafter.ENTRY_COUNT];
    private final IAEItemStack[] syncedOutputItems = new IAEItemStack[TileAutoCrafter.ENTRY_COUNT];
    private final IAEItemStack[][] syncedInputGrids = new IAEItemStack[TileAutoCrafter.ENTRY_COUNT][9];
    private final long[] syncedMetricsTotal = new long[TileAutoCrafter.ENTRY_COUNT];
    private final double[] syncedOccupancy = new double[TileAutoCrafter.ENTRY_COUNT];
    private final double[] syncedErrorRate = new double[TileAutoCrafter.ENTRY_COUNT];
    private final List<List<String>> syncedErrorDetails = new ArrayList<>();
    private final boolean[] syncedIgnoreNbt = new boolean[TileAutoCrafter.ENTRY_COUNT];

    // Catalyst info per entry (slot index -> expected item)
    private final List<List<CatalystInfo>> syncedCatalystInfo = new ArrayList<>();
    // Actual catalyst inventory per entry
    private final ItemStack[][] syncedCatalystInventory = new ItemStack[TileAutoCrafter.ENTRY_COUNT][CrafterEntry.CATALYST_SLOTS];

    /** Simple holder for catalyst slot info. */
    private static class CatalystInfo {
        final int slotIndex;
        final IAEItemStack expectedItem;
        CatalystInfo(int slotIndex, IAEItemStack expectedItem) {
            this.slotIndex = slotIndex;
            this.expectedItem = expectedItem;
        }
    }

    public GuiAutoCrafter(ContainerAutoCrafter container) {
        super(container);
        this.container = container;
        this.xSize = GUI_WIDTH;
        this.ySize = GUI_HEIGHT;

        // Initialize synced data arrays
        for (int i = 0; i < TileAutoCrafter.ENTRY_COUNT; i++) {
            syncedStates[i] = CrafterState.NO_PATTERN;
            syncedErrorDetails.add(new ArrayList<>());
            syncedCatalystInfo.add(new ArrayList<>());
            for (int j = 0; j < CrafterEntry.CATALYST_SLOTS; j++) {
                syncedCatalystInventory[i][j] = ItemStack.EMPTY;
            }
        }
    }

    /**
     * Gets the current page - uses packet-synced data if available.
     */
    private int getCurrentPage() {
        return hasSyncedData ? syncedCurrentPage : container.getCurrentEntryIndex();
    }

    @Override
    public void initGui() {
        super.initGui();

        buttonList.clear();

        // Batch and Speed buttons are now custom drawn (no GuiButton)

        // Calculate overview modal position (top-left aligned with main GUI)
        overviewLeft = guiLeft;
        overviewTop = guiTop;

        // Request full state sync from server
        requestStateSync();
    }

    /**
     * Request a full state sync from the server.
     * Called on GUI open and when switching to overview mode.
     */
    private void requestStateSync() {
        PowerToolsNetwork.INSTANCE.sendToServer(new PacketRequestCrafterSync(
                container.getTile().getPos()));
    }

    /**
     * Sets the current page and syncs to server for persistence.
     * Updates local synced state immediately for responsive UI.
     */
    private void setCurrentPage(int page) {
        syncedCurrentPage = page;  // Update local state immediately for instant response
        container.setCurrentEntryIndex(page);
        PowerToolsNetwork.INSTANCE.sendToServer(new PacketSetCrafterPage(
                container.getTile().getPos(), page));
    }

    private void openBatchDialog() {
        // Send packet to open batch GUI
        PowerToolsNetwork.INSTANCE.sendToServer(new PacketOpenCrafterSubGui(
                container.getTile().getPos(), PacketOpenCrafterSubGui.SubGui.BATCH));
    }

    private void openSpeedDialog() {
        // Send packet to open speed GUI
        PowerToolsNetwork.INSTANCE.sendToServer(new PacketOpenCrafterSubGui(
                container.getTile().getPos(), PacketOpenCrafterSubGui.SubGui.SPEED));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();

        // Detect when synced page changes from server and update container slots.
        // This handles the case where the GUI opens before the page sync arrives.
        int currentSyncedPage = container.getCurrentEntryIndex();
        if (lastKnownPage != currentSyncedPage) {
            lastKnownPage = currentSyncedPage;
            container.setCurrentEntryIndex(currentSyncedPage);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);

        // Draw overview modal on top if visible
        if (overviewMode) {
            drawOverviewModal(mouseX, mouseY, partialTicks);
            drawOverviewTooltips(mouseX, mouseY);
            return;  // Modal blocks other interactions
        }

        renderHoveredToolTip(mouseX, mouseY);
        drawRecipeTooltips(mouseX, mouseY);
        drawAE2ButtonTooltips(mouseX, mouseY);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        // Always draw recipe texture as base
        mc.getTextureManager().bindTexture(RECIPE_TEXTURE);
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, GUI_WIDTH, GUI_HEIGHT);

        // Draw upgrade slot icons (for empty slots)
        drawUpgradeSlotIcons();

        // Draw recipe content (always visible, upgrade slots always accessible)
        drawRecipeContent(mouseX, mouseY);

        // Draw AE2-style buttons (always visible)
        drawAE2Buttons(mouseX, mouseY);
    }

    /**
     * Draws the upgrade slot background icons for empty slots.
     * Uses AE2's states.png texture for the "insert upgrade" icon.
     * Applies 0.4f opacity to match AE2's grayed-out style for empty slots.
     */
    private void drawUpgradeSlotIcons() {
        // UPGRADES icon index: 13 * 16 + 15 = 223
        final int UPGRADE_ICON = 13 * 16 + 15;
        final int uv_y = UPGRADE_ICON / 16;
        final int uv_x = UPGRADE_ICON % 16;
        final float ICON_OPACITY = 0.4f;  // AE2's default opacity for slot icons

        mc.getTextureManager().bindTexture(AE2_STATES);

        for (int i = 0; i < TileAutoCrafter.UPGRADE_SLOTS; i++) {
            ItemStack slotContent = container.getTile().getUpgradeStack(i);
            if (slotContent.isEmpty()) {
                // Draw the upgrade icon for empty slots with AE2-style transparency
                int x = guiLeft + UPGRADE_START_X;
                int y = guiTop + UPGRADE_START_Y + i * UPGRADE_SLOT_SIZE;

                GlStateManager.enableBlend();
                GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
                GlStateManager.color(1.0f, 1.0f, 1.0f, ICON_OPACITY);
                drawTexturedModalRect(x, y, uv_x * 16, uv_y * 16, 16, 16);
                GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);  // Reset color
                GlStateManager.disableBlend();
            }
        }
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        // Draw title (shifted right to make room for overview/back button on the left)
        String title = I18n.format("gui.ae2powertools.crafter.title");
        fontRenderer.drawString(title, OVERVIEW_BTN_X + PAGE_BTN_SIZE + 4, 7, 0x404040);

        // Draw page indicator in recipe mode (not in overview)
        if (!overviewMode) {
            String pageText = I18n.format("gui.ae2powertools.crafter.page", getCurrentPage() + 1, TileAutoCrafter.ENTRY_COUNT);
            int pageWidth = fontRenderer.getStringWidth(pageText);
            int centerX = (PAGE_LEFT_X + 12 + PAGE_RIGHT_X) / 2 - pageWidth / 2;
            fontRenderer.drawString(pageText, centerX, PAGE_LEFT_Y + 2, 0x404040);
        }
    }

    // ==================== CUSTOM BUTTONS ====================

    /**
     * Draws custom buttons: Overview toggle, page navigation, batch and speed.
     * All buttons are custom drawn (not GuiButton) for consistent styling.
     */
    private void drawAE2Buttons(int mouseX, int mouseY) {
        // Overview button (top left) - small AE2 style button
        drawOverviewButton(mouseX, mouseY);

        // Page navigation buttons (only when not in overview mode)
        if (!overviewMode) {
            drawPageNavigationButtons(mouseX, mouseY);
            drawBatchSpeedButtons(mouseX, mouseY);
            drawIgnoreNbtButton(mouseX, mouseY);
        }
    }

    /**
     * Draws the batch and speed buttons using custom textures.
     * Each texture is a single 22x22 image. Hover state is drawn as an overlay.
     */
    private void drawBatchSpeedButtons(int mouseX, int mouseY) {
        // Reset GL state before drawing buttons to prevent state pollution
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);

        // Batch button
        int batchX = guiLeft + BATCH_BTN_X;
        int batchY = guiTop + BATCH_BTN_Y;
        batchButtonHovered = mouseX >= batchX && mouseX < batchX + TAB_BTN_SIZE &&
                             mouseY >= batchY && mouseY < batchY + TAB_BTN_SIZE;

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        mc.getTextureManager().bindTexture(BATCH_BUTTON_TEXTURE);
        drawModalRectWithCustomSizedTexture(batchX, batchY, 0, 0, TAB_BTN_SIZE, TAB_BTN_SIZE, TAB_BTN_SIZE, TAB_BTN_SIZE);

        // Draw hover overlay for batch button
        if (batchButtonHovered) {
            drawRect(batchX, batchY, batchX + TAB_BTN_SIZE, batchY + TAB_BTN_SIZE, 0x40FFFFFF);
        }

        // Speed button
        int speedX = guiLeft + SPEED_BTN_X;
        int speedY = guiTop + SPEED_BTN_Y;
        speedButtonHovered = mouseX >= speedX && mouseX < speedX + TAB_BTN_SIZE &&
                             mouseY >= speedY && mouseY < speedY + TAB_BTN_SIZE;

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        mc.getTextureManager().bindTexture(SPEED_BUTTON_TEXTURE);
        drawModalRectWithCustomSizedTexture(speedX, speedY, 0, 0, TAB_BTN_SIZE, TAB_BTN_SIZE, TAB_BTN_SIZE, TAB_BTN_SIZE);

        // Draw hover overlay for speed button
        if (speedButtonHovered) {
            drawRect(speedX, speedY, speedX + TAB_BTN_SIZE, speedY + TAB_BTN_SIZE, 0x40FFFFFF);
        }

        GlStateManager.disableBlend();
    }

    /**
     * Draws the overview toggle button (< to open, > to close).
     */
    private void drawOverviewButton(int mouseX, int mouseY) {
        // Overview button (top left, same position as back button in modal)
        int ovX = guiLeft + OVERVIEW_BTN_X;
        int ovY = guiTop + OVERVIEW_BTN_Y;
        overviewBtnHovered = mouseX >= ovX && mouseX < ovX + PAGE_BTN_SIZE &&
                             mouseY >= ovY && mouseY < ovY + PAGE_BTN_SIZE;

        // Draw vanilla-style button with < arrow (opens overview)
        drawSquareButton(ovX, ovY, PAGE_BTN_SIZE, "<", true, overviewBtnHovered);
    }

    /**
     * Draws custom square page navigation buttons.
     * These are vanilla-style square buttons, not the rounded GuiButton.
     */
    private void drawPageNavigationButtons(int mouseX, int mouseY) {
        int prevX = guiLeft + PAGE_LEFT_X;
        int prevY = guiTop + PAGE_LEFT_Y;
        int nextX = guiLeft + PAGE_RIGHT_X;
        int nextY = guiTop + PAGE_RIGHT_Y;

        boolean canGoPrev = getCurrentPage() > 0;
        boolean canGoNext = getCurrentPage() < TileAutoCrafter.ENTRY_COUNT - 1;

        // Check hover state
        pagePrevHovered = canGoPrev && mouseX >= prevX && mouseX < prevX + PAGE_BTN_SIZE &&
                          mouseY >= prevY && mouseY < prevY + PAGE_BTN_SIZE;
        pageNextHovered = canGoNext && mouseX >= nextX && mouseX < nextX + PAGE_BTN_SIZE &&
                          mouseY >= nextY && mouseY < nextY + PAGE_BTN_SIZE;

        // Draw previous button
        drawSquareButton(prevX, prevY, PAGE_BTN_SIZE, "<", canGoPrev, pagePrevHovered);

        // Draw next button
        drawSquareButton(nextX, nextY, PAGE_BTN_SIZE, ">", canGoNext, pageNextHovered);
    }

    /**
     * Draws a vanilla-style square button with proper 3D beveled edges.
     * Uses darker colors matching vanilla Minecraft button style.
     */
    private void drawSquareButton(int x, int y, int size, String text, boolean enabled, boolean hovered) {
        // Vanilla-style button colors (darker, more muted)
        int bgColor;
        if (!enabled) {
            bgColor = 0xFF606060; // Disabled: dark gray
        } else if (hovered) {
            bgColor = 0xFF7090B0; // Hovered: slightly brighter blue-gray
        } else {
            bgColor = 0xFF808080; // Normal: medium gray (vanilla button base)
        }

        // Draw button background
        drawRect(x + 1, y + 1, x + size - 1, y + size - 1, bgColor);

        // Draw beveled border (vanilla 3D style)
        int borderLight = enabled ? 0xFFAAAAAA : 0xFF808080;  // Top/left highlight
        int borderDark = enabled ? 0xFF404040 : 0xFF505050;   // Bottom/right shadow
        int borderOuter = 0xFF000000;  // Outer edge

        // Outer black border
        drawHorizontalLine(x, x + size - 1, y, borderOuter);
        drawHorizontalLine(x, x + size - 1, y + size - 1, borderOuter);
        drawVerticalLine(x, y, y + size - 1, borderOuter);
        drawVerticalLine(x + size - 1, y, y + size - 1, borderOuter);

        // Inner beveled edges (light top/left, dark bottom/right)
        drawHorizontalLine(x + 1, x + size - 2, y + 1, borderLight);
        drawVerticalLine(x + 1, y + 1, y + size - 2, borderLight);
        drawHorizontalLine(x + 1, x + size - 2, y + size - 2, borderDark);
        drawVerticalLine(x + size - 2, y + 1, y + size - 2, borderDark);

        // Draw text centered with vanilla colors and shadow
        int textColor = enabled ? (hovered ? 0xFFFFFFA0 : 0xFFE0E0E0) : 0xFFA0A0A0;
        int textWidth = fontRenderer.getStringWidth(text);
        int textX = x + (size - textWidth) / 2 + 1;
        int textY = y + (size - fontRenderer.FONT_HEIGHT) / 2 + 1;
        fontRenderer.drawStringWithShadow(text, textX, textY, textColor);
    }

    /**
     * Draws the per-entry Ignore NBT toggle button.
     * This is a small square vanilla-style button that toggles fuzzy NBT matching for the current recipe entry.
     */
    private void drawIgnoreNbtButton(int mouseX, int mouseY) {
        int x = guiLeft + IGNORE_NBT_BTN_X;
        int y = guiTop + IGNORE_NBT_BTN_Y;

        int page = getCurrentPage();

        // Only enable if this entry has a recipe/pattern to act on
        boolean enabled = hasDisplayData(page);

        ignoreNbtBtnHovered = enabled
                && mouseX >= x && mouseX < x + PAGE_BTN_SIZE
                && mouseY >= y && mouseY < y + PAGE_BTN_SIZE;

        // Draw button with "N" label (NBT)
        drawSquareButton(x, y, PAGE_BTN_SIZE, "N", enabled, ignoreNbtBtnHovered);

        // Visual ON indicator (subtle green overlay)
        if (enabled && isIgnoreNbtEnabled(page)) {
            drawRect(x + 1, y + 1, x + PAGE_BTN_SIZE - 1, y + PAGE_BTN_SIZE - 1, 0x3000FF00);
        }
    }

    /**
     * Draws tooltips for custom buttons.
     */
    private void drawAE2ButtonTooltips(int mouseX, int mouseY) {
        List<String> tooltip = new ArrayList<>();

        if (overviewBtnHovered) {
            tooltip.add(I18n.format("gui.ae2powertools.crafter.overview"));
        } else if (pagePrevHovered) {
            tooltip.add(I18n.format("gui.ae2powertools.crafter.page.previous"));
        } else if (pageNextHovered) {
            tooltip.add(I18n.format("gui.ae2powertools.crafter.page.next"));
        } else if (batchButtonHovered) {
            tooltip.add(I18n.format("gui.ae2powertools.crafter.batch.title"));
            tooltip.add(TextFormatting.GRAY + I18n.format("gui.ae2powertools.crafter.batch.desc",
                    container.syncBatchSize));
            tooltip.add("");
            tooltip.add(TextFormatting.DARK_GRAY + I18n.format("gui.ae2powertools.crafter.batch.explanation"));
        } else if (speedButtonHovered) {
            tooltip.add(I18n.format("gui.ae2powertools.crafter.speed.title"));
            tooltip.add(TextFormatting.GRAY + I18n.format("gui.ae2powertools.crafter.speed.desc",
                    FormatUtil.formatTimeTicks(container.syncSpeedTicks)));
            tooltip.add("");
            tooltip.add(TextFormatting.DARK_GRAY + I18n.format("gui.ae2powertools.crafter.speed.explanation"));
        } else if (ignoreNbtBtnHovered) {
            boolean on = isIgnoreNbtEnabled(getCurrentPage());
            tooltip.add(I18n.format(on
                    ? "gui.ae2powertools.crafter.nbt_matching.ignored"
                    : "gui.ae2powertools.crafter.nbt_matching.strict"));
            tooltip.add(TextFormatting.DARK_GRAY + I18n.format("gui.ae2powertools.crafter.ignore_nbt.desc"));
        }

        if (!tooltip.isEmpty()) {
            GuiUtils.drawHoveringText(tooltip, mouseX, mouseY, width, height, -1, fontRenderer);
        }
    }

    // ==================== RECIPE VIEW ====================

    private void drawRecipeContent(int mouseX, int mouseY) {
        int currentPage = getCurrentPage();

        // Don't update hover state if overview modal is open
        if (!overviewMode) {
            hoveredRecipeSlot = -1;
            hoveredResult = false;
        }

        // Only draw recipe items if we have display data (pattern present)
        if (hasDisplayData(currentPage)) {
            // Draw recipe preview grid (3x3) using synced input grid
            IAEItemStack[] inputGrid = getSyncedInputGrid(currentPage);
            if (inputGrid != null) {
                for (int row = 0; row < 3; row++) {
                    for (int col = 0; col < 3; col++) {
                        int slotIndex = row * 3 + col;
                        int x = guiLeft + RECIPE_GRID_X + col * 18;
                        int y = guiTop + RECIPE_GRID_Y + row * 18;

                        // Check hover (only if not in overview mode)
                        if (!overviewMode && mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                            hoveredRecipeSlot = slotIndex;
                        }

                        // Draw item
                        if (inputGrid[slotIndex] != null) {
                            drawItemStack(inputGrid[slotIndex].createItemStack(), x, y);
                        }
                    }
                }
            }

            // Draw recipe result - 22x22 slot, item centered (+3 offset for 16x16 in 22x22)
            IAEItemStack outputItem = getSyncedOutput(currentPage);
            if (outputItem != null) {
                int slotX = guiLeft + RECIPE_RESULT_X;
                int slotY = guiTop + RECIPE_RESULT_Y;
                int itemX = slotX + 3;  // Center 16x16 item in 22x22 slot
                int itemY = slotY + 3;

                // Hover detection covers full 22x22 slot area
                if (!overviewMode && mouseX >= slotX && mouseX < slotX + RECIPE_RESULT_SIZE &&
                        mouseY >= slotY && mouseY < slotY + RECIPE_RESULT_SIZE) {
                    hoveredResult = true;
                }

                drawItemStack(outputItem.createItemStack(), itemX, itemY);
            }

            // Draw catalyst ghost items using synced catalyst data
            drawCatalystGhosts(currentPage, mouseX, mouseY);

            // Draw speed info (under catalyst slots) using synced values
            drawSpeedInfo(currentPage, guiLeft + SPEED_INFO_X, guiTop + SPEED_INFO_Y);
        }

        // ALWAYS draw state indicator - shows NO_PATTERN for empty entries
        drawStateIndicator(currentPage, guiLeft + STATE_INDICATOR_X, guiTop + STATE_INDICATOR_Y);
    }

    /**
     * Draws catalyst ghost items using synced data.
     * Shows ghost items for empty catalyst slots based on the recipe requirements.
     */
    private void drawCatalystGhosts(int entryIndex, int mouseX, int mouseY) {
        // Use synced catalyst info for immediate accuracy
        List<CatalystInfo> catalysts = syncedCatalystInfo.get(entryIndex);
        if (catalysts == null || catalysts.isEmpty()) return;

        // Collect positions that need ghost overlay rendering (drawn after all items)
        List<int[]> ghostOverlayPositions = new ArrayList<>();

        for (CatalystInfo catalyst : catalysts) {
            int slotIndex = catalyst.slotIndex;
            if (slotIndex < 0 || slotIndex >= CrafterEntry.CATALYST_SLOTS) continue;

            // Use synced catalyst inventory
            ItemStack current = syncedCatalystInventory[entryIndex][slotIndex];

            // Position based on actual slot index, not iteration index
            int x = guiLeft + CATALYST_START_X + slotIndex * 18;
            int y = guiTop + CATALYST_START_Y;

            if ((current == null || current.isEmpty()) && catalyst.expectedItem != null) {
                // Draw ghost item showing what's needed in this slot
                drawItemStack(catalyst.expectedItem.createItemStack(), x, y);

                // Record position for ghost overlay (drawn later in one batch)
                ghostOverlayPositions.add(new int[]{x, y});
            }
        }

        // Draw all ghost overlays in a single batch AFTER item rendering
        // This prevents GL state from item rendering from leaking into other parts
        if (!ghostOverlayPositions.isEmpty()) {
            // Fully reset GL state before drawing overlays
            // Item rendering leaves various states enabled that can affect 2D drawing
            RenderHelper.disableStandardItemLighting();
            GlStateManager.disableLighting();
            GlStateManager.disableDepth();
            GlStateManager.disableAlpha();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(
                    GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ZERO);
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);

            // Draw all ghost overlays
            for (int[] pos : ghostOverlayPositions) {
                // Semi-transparent light gray overlay to create "ghost" effect
                drawRect(pos[0], pos[1], pos[0] + 16, pos[1] + 16, 0x99CCCCCC);
            }

            // Restore GL state for subsequent GUI rendering
            GlStateManager.disableBlend();
            GlStateManager.enableAlpha();
            GlStateManager.enableDepth();

            // Re-bind the GUI texture since drawRect may have changed it
            mc.getTextureManager().bindTexture(RECIPE_TEXTURE);
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        }
    }

    /**
     * Draws speed/throughput info under the catalyst slots.
     * Shows estimated throughput like "~1.2k items/5h" based on current speed and batch size.
     * Uses synced data for immediate accuracy.
     */
    private void drawSpeedInfo(int entryIndex, int x, int y) {
        // Use synced data for immediate accuracy
        if (!hasDisplayData(entryIndex)) return;

        // Get output count from synced data
        IAEItemStack output = getSyncedOutput(entryIndex);
        if (output == null) return;

        // Calculate throughput using synced values
        int speedTicks = getSyncedSpeedTicks();
        int batchSize = getSyncedBatchSize();
        long outputCount = output.getStackSize();

        long itemsPerCraft = getSyncedEffectiveBatchSize() * outputCount;
        String itemsPerCraftStr = ReadableNumberConverter.INSTANCE.toWideReadableForm(itemsPerCraft);
        String timePerOperation = FormatUtil.formatTimeTicks(speedTicks * batchSize);
        String throughput = I18n.format("gui.ae2powertools.crafter.crafts_per_operation",
                itemsPerCraftStr, timePerOperation);

        fontRenderer.drawString(throughput, x + 2, y + 2, 0x606060);
    }

    /**
     * Draws the state indicator (IDLE, MISSING_INPUT, etc.) at the given position.
     * Uses packet-synced state for immediate accuracy.
     */
    private void drawStateIndicator(int entryIndex, int x, int y) {
        CrafterState state = getSyncedState(entryIndex);
        int bgColor = state.getBackgroundColor();
        int textColor = state.getTextColor();

        if ((bgColor & 0xFF000000) != 0) drawRect(x, y, x + 140, y + 12, bgColor);

        String stateText = getStateText(state);
        fontRenderer.drawString(stateText, x + 2, y + 2, textColor);
    }

    private String getStateText(CrafterState state) {
        switch (state) {
            case NO_PATTERN:
                return I18n.format("gui.ae2powertools.crafter.state.no_pattern");
            case DISABLED:
                return I18n.format("gui.ae2powertools.crafter.state.disabled");
            case IDLE:
                return I18n.format("gui.ae2powertools.crafter.state.idle");
            case MISSING_CATALYST:
                return I18n.format("gui.ae2powertools.crafter.state.missing_catalyst");
            case MISSING_INPUT:
                return I18n.format("gui.ae2powertools.crafter.state.missing_input");
            case NO_OUTPUT_SPACE:
                return I18n.format("gui.ae2powertools.crafter.state.no_output_space");
            case SIMULATION_FAILED:
                return I18n.format("gui.ae2powertools.crafter.state.simulation_failed");
            case HOLDING_OUTPUT:
                return I18n.format("gui.ae2powertools.crafter.state.holding_output");
            default:
                return state.name();
        }
    }

    private void drawRecipeTooltips(int mouseX, int mouseY) {
        int currentPage = getCurrentPage();
        if (!hasDisplayData(currentPage)) return;

        IAEItemStack[] inputGrid = getSyncedInputGrid(currentPage);

        // Recipe grid tooltip
        if (hoveredRecipeSlot >= 0 && hoveredRecipeSlot < 9 && inputGrid != null) {
            IAEItemStack item = inputGrid[hoveredRecipeSlot];
            if (item != null) drawItemTooltip(item.createItemStack(), mouseX, mouseY);
        }

        // Result tooltip with toggle hint
        if (hoveredResult) {
            IAEItemStack outputItem = getSyncedOutput(currentPage);
            if (outputItem != null) {
                List<String> tooltip = new ArrayList<>();
                tooltip.addAll(outputItem.createItemStack().getTooltip(mc.player,
                        mc.gameSettings.advancedItemTooltips
                                ? ITooltipFlag.TooltipFlags.ADVANCED
                                : ITooltipFlag.TooltipFlags.NORMAL));
                tooltip.add("");
                tooltip.add(TextFormatting.GRAY + I18n.format("gui.ae2powertools.crafter.right_click_toggle"));

                GuiUtils.drawHoveringText(tooltip, mouseX, mouseY, width, height, -1, fontRenderer);
            }
        }

        // Pattern slot tooltip - needs actual entry for the pattern stack
        // Fall back to container here since pattern display is less critical
        int patternX = guiLeft + PATTERN_SLOT_X;
        int patternY = guiTop + PATTERN_SLOT_Y;
        if (mouseX >= patternX && mouseX < patternX + 16 && mouseY >= patternY && mouseY < patternY + 16) {
            CrafterEntry entry = container.getCurrentEntry();
            if (entry != null && entry.hasPattern()) {
                drawItemTooltip(entry.getPatternStack(), mouseX, mouseY);
            }
        }
    }

    // ==================== OVERVIEW MODAL ====================

    /**
     * Draws the overview modal on top of the recipe view.
     * This is a floating window that shows all 12 entries at once.
     */
    private void drawOverviewModal(int mouseX, int mouseY, float partialTicks) {
        // Fully reset GL state before drawing modal.
        // Item rendering from recipe view leaves various GL states enabled that cause
        // texture bleeding (e.g., pattern slot texture leaking into 2nd overview row).
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.disableAlpha();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        // Draw modal background - bind texture AFTER GL state reset
        mc.getTextureManager().bindTexture(OVERVIEW_TEXTURE);
        drawTexturedModalRect(overviewLeft, overviewTop, 0, 0, OVERVIEW_MODAL_WIDTH, OVERVIEW_MODAL_HEIGHT);

        // Re-enable alpha for subsequent drawing
        GlStateManager.enableAlpha();

        // Draw close/back button (> arrow to go back to recipe view)
        // Same position as overview button in recipe view (top left)
        int closeX = overviewLeft + OVERVIEW_BTN_X;
        int closeY = overviewTop + OVERVIEW_BTN_Y;
        overviewCloseBtnHovered = mouseX >= closeX && mouseX < closeX + PAGE_BTN_SIZE &&
                                  mouseY >= closeY && mouseY < closeY + PAGE_BTN_SIZE;

        // Draw vanilla-style button with > arrow (goes back to recipe view)
        drawSquareButton(closeX, closeY, PAGE_BTN_SIZE, ">", true, overviewCloseBtnHovered);

        // Draw title (shifted right to make room for back button on the left)
        String title = I18n.format("gui.ae2powertools.crafter.overview");
        fontRenderer.drawString(title, overviewLeft + OVERVIEW_BTN_X + PAGE_BTN_SIZE + 4, overviewTop + 7, 0x404040);

        // Draw entries
        drawOverviewEntries(mouseX, mouseY);

        GlStateManager.enableDepth();
    }

    /**
     * Draws all entries in the overview modal.
     * Uses packet-synced data for immediate accuracy.
     */
    private void drawOverviewEntries(int mouseX, int mouseY) {
        hoveredOverviewRow = -1;

        for (int i = 0; i < TileAutoCrafter.ENTRY_COUNT; i++) {
            int rowX = overviewLeft + OVERVIEW_ROW_X;
            int rowY = overviewTop + OVERVIEW_ROW_Y + i * OVERVIEW_ROW_HEIGHT;

            // Check hover
            if (mouseX >= rowX && mouseX < rowX + OVERVIEW_ROW_WIDTH &&
                    mouseY >= rowY && mouseY < rowY + OVERVIEW_ROW_HEIGHT) {
                hoveredOverviewRow = i;
            }

            // Draw background based on state (use packet-synced state)
            CrafterState state = getSyncedState(i);
            int bgColor = state.getBackgroundColor();
            if ((bgColor & 0xFF000000) != 0) {
                drawRect(rowX, rowY, rowX + OVERVIEW_ROW_WIDTH, rowY + OVERVIEW_ROW_HEIGHT, bgColor);
            }

            // Draw hover highlight
            if (hoveredOverviewRow == i) {
                drawRect(rowX, rowY, rowX + OVERVIEW_ROW_WIDTH, rowY + OVERVIEW_ROW_HEIGHT, 0x40FFFFFF);
            }

            // Draw item preview using synced data
            // Note: Reset GL state before each item render to prevent texture bleeding from recipe view
            if (hasDisplayData(i)) {
                IAEItemStack outputItem = getSyncedOutput(i);
                if (outputItem != null) {
                    // Reset GL state completely to prevent texture bleeding
                    GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
                    GlStateManager.enableDepth();
                    GlStateManager.enableRescaleNormal();
                    drawItemStack(outputItem.createItemStack(), rowX + 1, rowY + 1);
                    GlStateManager.disableRescaleNormal();
                    GlStateManager.disableDepth();
                    // Rebind overview texture after item rendering to restore state
                    mc.getTextureManager().bindTexture(OVERVIEW_TEXTURE);
                    GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
                }
            }

            // Draw entry info with metrics
            String info = getEntryOverviewInfo(i);
            fontRenderer.drawString(info, rowX + 20, rowY + 5, state.getTextColor());

            // Draw metrics on the right side if entry has data
            if (hasDisplayData(i) && getSyncedMetricsTotal(i) > 0) {
                String metrics = I18n.format("gui.ae2powertools.crafter.metrics_format",
                        String.format("%.0f", getSyncedOccupancy(i)),
                        String.format("%.0f", getSyncedErrorRate(i)));
                int metricsWidth = fontRenderer.getStringWidth(metrics);
                // Use contrasting colors: white with shadow for colored backgrounds, gray for idle
                int metricsColor = state == CrafterState.IDLE ? 0x707070 : 0xFFFFFF;
                if (state == CrafterState.IDLE) {
                    fontRenderer.drawString(metrics, rowX + OVERVIEW_ROW_WIDTH - metricsWidth - 2, rowY + 5, metricsColor);
                } else {
                    // Use shadow for better contrast on colored backgrounds
                    fontRenderer.drawStringWithShadow(metrics, rowX + OVERVIEW_ROW_WIDTH - metricsWidth - 2, rowY + 5, metricsColor);
                }
            }
        }
    }

    private String getEntryOverviewInfo(int index) {
        // Use synced data
        if (!hasDisplayData(index)) return I18n.format("gui.ae2powertools.crafter.empty_slot", index + 1);

        IAEItemStack output = getSyncedOutput(index);
        String itemName = output != null ? output.createItemStack().getDisplayName() : "???";

        // Truncate name if too long to leave room for metrics
        // Calculate available width: total row width minus metrics space (~50 pixels) minus item icon (~20 pixels)
        int availableWidth = OVERVIEW_ROW_WIDTH - 70;
        String truncated = fontRenderer.trimStringToWidth(itemName, availableWidth);
        if (!truncated.equals(itemName)) {
            itemName = truncated.substring(0, Math.max(0, truncated.length() - 2)) + "...";
        }

        return itemName;
    }

    private void drawOverviewTooltips(int mouseX, int mouseY) {
        if (hoveredOverviewRow < 0 || hoveredOverviewRow >= TileAutoCrafter.ENTRY_COUNT) return;

        int entryIndex = hoveredOverviewRow;

        // Check if hovering over metrics area (right side of row)
        int rowX = overviewLeft + OVERVIEW_ROW_X;
        int metricsX = rowX + OVERVIEW_ROW_WIDTH - 60; // Approximate metrics start position
        boolean hoveringMetrics = mouseX >= metricsX;

        IAEItemStack output = getSyncedOutput(entryIndex);

        List<String> tooltip = new ArrayList<>();

        // Empty slot tooltip using synced data
        if (!hasDisplayData(entryIndex)) {
            tooltip.add(I18n.format("gui.ae2powertools.crafter.empty_slot", entryIndex + 1));
            tooltip.add("");
            tooltip.add(TextFormatting.DARK_GRAY + I18n.format("gui.ae2powertools.crafter.click_to_view"));
            GuiUtils.drawHoveringText(tooltip, mouseX, mouseY, width, height, -1, fontRenderer);
            return;
        }

        if (output == null) return;

        if (hoveringMetrics && getSyncedMetricsTotal(entryIndex) > 0) {
            // Occupancy explanation
            String occupancy = String.format("%.1f%%", getSyncedOccupancy(entryIndex));
            tooltip.add(TextFormatting.GREEN + I18n.format("gui.ae2powertools.crafter.occupancy", occupancy));
            tooltip.add(TextFormatting.GRAY + I18n.format("gui.ae2powertools.crafter.occupancy_desc"));
            tooltip.add("");

            // Error rate explanation
            TextFormatting errorColor = TextFormatting.GREEN;
            double errorRate = getSyncedErrorRate(entryIndex);
            if (errorRate > 10) {
                errorColor = TextFormatting.RED;
            } else if (errorRate > 0) {
                errorColor = TextFormatting.YELLOW;
            }

            String errorRateStr = String.format("%.1f%%", errorRate);
            tooltip.add(errorColor + I18n.format("gui.ae2powertools.crafter.error_rate", errorRateStr));
            tooltip.add(TextFormatting.GRAY + I18n.format("gui.ae2powertools.crafter.error_rate_desc"));
        } else {
            // Show detailed tooltip for the entry
            tooltip.add(output.createItemStack().getDisplayName());
            tooltip.add("");

            // Status using packet-synced state
            tooltip.add(TextFormatting.GRAY + I18n.format("gui.ae2powertools.crafter.state") + ": " 
                    + TextFormatting.RESET + getStateText(getSyncedState(entryIndex)));

            // Error details using synced data
            List<String> errorDetails = getSyncedErrorDetails(entryIndex);
            if (!errorDetails.isEmpty()) {
                tooltip.add("");
                tooltip.add(TextFormatting.RED + I18n.format("gui.ae2powertools.crafter.issues") + ":");
                for (String detail : errorDetails) tooltip.add(TextFormatting.GRAY + "  - " + detail);
            }

            // Metrics summary with explanations
            if (getSyncedMetricsTotal(entryIndex) > 0) {
                tooltip.add("");

                // Occupancy
                String occupancy = String.format("%.1f%%", getSyncedOccupancy(entryIndex));
                tooltip.add(TextFormatting.GREEN + I18n.format("gui.ae2powertools.crafter.occupancy", occupancy));
                tooltip.add(TextFormatting.DARK_GRAY + "  " + I18n.format("gui.ae2powertools.crafter.occupancy_desc"));

                tooltip.add("");

                // Error rate
                TextFormatting errorColor = TextFormatting.GREEN;
                double errorRate = getSyncedErrorRate(entryIndex);
                if (errorRate > 10) {
                    errorColor = TextFormatting.RED;
                } else if (errorRate > 0) {
                    errorColor = TextFormatting.YELLOW;
                }

                String errorRateStr = String.format("%.1f%%", errorRate);
                tooltip.add(errorColor + I18n.format("gui.ae2powertools.crafter.error_rate", errorRateStr));
                tooltip.add(TextFormatting.DARK_GRAY + "  " + I18n.format("gui.ae2powertools.crafter.error_rate_desc"));
            }

            tooltip.add("");
            tooltip.add(TextFormatting.AQUA + I18n.format("gui.ae2powertools.crafter.click_to_view"));
            tooltip.add(TextFormatting.AQUA + I18n.format("gui.ae2powertools.crafter.right_click_toggle"));
        }

        GuiUtils.drawHoveringText(tooltip, mouseX, mouseY, width, height, -1, fontRenderer);
    }

    // ==================== INPUT HANDLING ====================

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        // Escape closes overview modal
        if (overviewMode && keyCode == Keyboard.KEY_ESCAPE) {
            overviewMode = false;
            return;
        }

        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        // Overview modal takes priority
        if (overviewMode) {
            handleOverviewModalClick(mouseX, mouseY, mouseButton);
            return;
        }

        // Custom button handling (overview toggle, page navigation, batch/speed)
        if (mouseButton == 0) {
            if (overviewBtnHovered) {
                overviewMode = true;
                return;
            }

            // Page navigation buttons
            if (pagePrevHovered && getCurrentPage() > 0) {
                setCurrentPage(getCurrentPage() - 1);
                return;
            }

            if (pageNextHovered && getCurrentPage() < TileAutoCrafter.ENTRY_COUNT - 1) {
                setCurrentPage(getCurrentPage() + 1);
                return;
            }

            // Batch/Speed buttons
            if (batchButtonHovered) {
                openBatchDialog();
                return;
            }

            if (speedButtonHovered) {
                openSpeedDialog();
                return;
            }

            // Ignore NBT toggle button
            if (ignoreNbtBtnHovered) {
                int page = getCurrentPage();
                PowerToolsNetwork.INSTANCE.sendToServer(new PacketToggleCrafterIgnoreNbt(
                        container.getTile().getPos(), page));
                return;
            }
        }

        // Handle result right-click to disable
        if (mouseButton == 1 && hoveredResult) {
            CrafterEntry entry = container.getCurrentEntry();
            if (entry != null && entry.hasPattern()) {
                PowerToolsNetwork.INSTANCE.sendToServer(new PacketToggleCrafterEntry(
                        container.getTile().getPos(), getCurrentPage()));
                return;
            }
        }

        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    /**
     * Handles clicks within the overview modal.
     */
    private void handleOverviewModalClick(int mouseX, int mouseY, int mouseButton) {
        // Close button click
        if (mouseButton == 0 && overviewCloseBtnHovered) {
            overviewMode = false;
            return;
        }

        // Click outside modal closes it
        if (mouseX < overviewLeft || mouseX >= overviewLeft + OVERVIEW_MODAL_WIDTH ||
                mouseY < overviewTop || mouseY >= overviewTop + OVERVIEW_MODAL_HEIGHT) {
            overviewMode = false;
            return;
        }

        // Handle row clicks
        if (hoveredOverviewRow >= 0) {
            if (mouseButton == 0) {
                // Left-click: go to that page and close modal
                setCurrentPage(hoveredOverviewRow);
                overviewMode = false;
            } else if (mouseButton == 1) {
                // Right-click: toggle enabled
                CrafterEntry entry = container.getTile().getEntry(hoveredOverviewRow);
                if (entry != null && entry.hasPattern()) {
                    PowerToolsNetwork.INSTANCE.sendToServer(new PacketToggleCrafterEntry(
                            container.getTile().getPos(), hoveredOverviewRow));
                }
            }
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();

        int scroll = Mouse.getEventDWheel();
        if (scroll == 0) return;

        // In overview mode, scrolling does nothing
        if (overviewMode) return;

        // In recipe mode, scroll through pages
        if (scroll > 0 && getCurrentPage() > 0) {
            setCurrentPage(getCurrentPage() - 1);
        } else if (scroll < 0 && getCurrentPage() < TileAutoCrafter.ENTRY_COUNT - 1) {
            setCurrentPage(getCurrentPage() + 1);
        }
    }

    // ==================== HELPERS ====================

    private void drawItemStack(ItemStack stack, int x, int y) {
        GlStateManager.pushMatrix();
        RenderHelper.enableGUIStandardItemLighting();

        itemRender.renderItemAndEffectIntoGUI(stack, x, y);
        itemRender.renderItemOverlayIntoGUI(fontRenderer, stack, x, y, null);

        RenderHelper.disableStandardItemLighting();
        GlStateManager.popMatrix();
    }

    private void drawItemTooltip(ItemStack stack, int mouseX, int mouseY) {
        if (stack.isEmpty()) return;

        List<String> tooltip = stack.getTooltip(mc.player, mc.gameSettings.advancedItemTooltips
                ? ITooltipFlag.TooltipFlags.ADVANCED : ITooltipFlag.TooltipFlags.NORMAL);

        GuiUtils.drawHoveringText(tooltip, mouseX, mouseY, width, height, -1, fontRenderer);
    }

    // ==================== PACKET-BASED STATE SYNC ====================

    /**
     * Handles incoming state sync packet from server.
     * This is called by the packet handler when data arrives.
     */
    public void handleStateSync(NBTTagCompound data) {
        // Global settings
        syncedSpeedTicks = data.getInteger("speedTicks");
        syncedBatchSize = data.getInteger("batchSize");
        syncedEffectiveBatchSize = data.getInteger("effectiveBatchSize");
        syncedCurrentPage = data.getInteger("currentPage");

        // Update container's page to match
        if (container.getCurrentEntryIndex() != syncedCurrentPage) {
            container.setCurrentEntryIndex(syncedCurrentPage);
            lastKnownPage = syncedCurrentPage;
        }

        // Parse entries
        NBTTagList entriesList = data.getTagList("entries", 10);
        for (int i = 0; i < Math.min(entriesList.tagCount(), TileAutoCrafter.ENTRY_COUNT); i++) {
            NBTTagCompound entryTag = entriesList.getCompoundTagAt(i);

            // State
            int stateOrdinal = entryTag.getInteger("state");
            if (stateOrdinal >= 0 && stateOrdinal < CrafterState.values().length) {
                syncedStates[i] = CrafterState.values()[stateOrdinal];
            } else {
                syncedStates[i] = CrafterState.NO_PATTERN;
            }

            // Per-entry ignore NBT setting
            syncedIgnoreNbt[i] = entryTag.getBoolean("ignoreNbt");

            // Metrics
            syncedMetricsTotal[i] = entryTag.getLong("metricsTotal");
            long metricsError = entryTag.getLong("metricsError");
            long metricsTotalActualCrafted = entryTag.getLong("metricsTotalActualCrafted");
            long metricsTotalMaxPossible = entryTag.getLong("metricsTotalMaxPossible");

            // Calculate rates
            syncedErrorRate[i] = syncedMetricsTotal[i] > 0 
                    ? (metricsError * 100.0) / syncedMetricsTotal[i] : 0.0;
            syncedOccupancy[i] = metricsTotalMaxPossible > 0 
                    ? (metricsTotalActualCrafted * 100.0) / metricsTotalMaxPossible : 0.0;

            // Display data
            syncedHasDisplayData[i] = entryTag.getBoolean("hasDisplayData");

            if (syncedHasDisplayData[i]) {
                // Output item
                if (entryTag.hasKey("output")) {
                    ItemStack outputStack = new ItemStack(entryTag.getCompoundTag("output"));
                    syncedOutputItems[i] = AEItemStack.fromItemStack(outputStack);
                } else {
                    syncedOutputItems[i] = null;
                }

                // Input grid
                if (entryTag.hasKey("inputGrid")) {
                    NBTTagList gridList = entryTag.getTagList("inputGrid", 10);
                    for (int j = 0; j < Math.min(gridList.tagCount(), 9); j++) {
                        NBTTagCompound slotTag = gridList.getCompoundTagAt(j);
                        if (!slotTag.isEmpty()) {
                            ItemStack gridStack = new ItemStack(slotTag);
                            syncedInputGrids[i][j] = AEItemStack.fromItemStack(gridStack);
                        } else {
                            syncedInputGrids[i][j] = null;
                        }
                    }
                }

                // Catalyst info (expected items for ghost rendering)
                syncedCatalystInfo.get(i).clear();
                if (entryTag.hasKey("catalysts")) {
                    NBTTagList catalystList = entryTag.getTagList("catalysts", 10);
                    for (int j = 0; j < catalystList.tagCount(); j++) {
                        NBTTagCompound catTag = catalystList.getCompoundTagAt(j);
                        int slotIndex = catTag.getInteger("slot");
                        IAEItemStack expectedItem = null;
                        if (catTag.hasKey("item")) {
                            ItemStack itemStack = new ItemStack(catTag.getCompoundTag("item"));
                            expectedItem = AEItemStack.fromItemStack(itemStack);
                        }
                        syncedCatalystInfo.get(i).add(new CatalystInfo(slotIndex, expectedItem));
                    }
                }

                // Catalyst inventory (actual items in slots)
                if (entryTag.hasKey("catalystInventory")) {
                    NBTTagList catalystInvList = entryTag.getTagList("catalystInventory", 10);
                    for (int j = 0; j < Math.min(catalystInvList.tagCount(), CrafterEntry.CATALYST_SLOTS); j++) {
                        NBTTagCompound slotTag = catalystInvList.getCompoundTagAt(j);
                        if (!slotTag.isEmpty()) {
                            syncedCatalystInventory[i][j] = new ItemStack(slotTag);
                        } else {
                            syncedCatalystInventory[i][j] = ItemStack.EMPTY;
                        }
                    }
                }
            } else {
                syncedOutputItems[i] = null;
                for (int j = 0; j < 9; j++) syncedInputGrids[i][j] = null;
                syncedCatalystInfo.get(i).clear();
                for (int j = 0; j < CrafterEntry.CATALYST_SLOTS; j++) {
                    syncedCatalystInventory[i][j] = ItemStack.EMPTY;
                }
            }

            // Error details
            syncedErrorDetails.get(i).clear();
            if (entryTag.hasKey("errors")) {
                NBTTagList errorList = entryTag.getTagList("errors", 10);
                for (int j = 0; j < errorList.tagCount(); j++) {
                    NBTTagCompound errorTag = errorList.getCompoundTagAt(j);
                    syncedErrorDetails.get(i).add(errorTag.getString("msg"));
                }
            }
        }

        hasSyncedData = true;
    }

    // ==================== SYNCED DATA ACCESSORS ====================

    /** Gets the synced state for an entry. */
    private CrafterState getSyncedState(int entryIndex) {
        if (!hasSyncedData || entryIndex < 0 || entryIndex >= TileAutoCrafter.ENTRY_COUNT) {
            return CrafterState.NO_PATTERN;  // Fallback before first packet arrives
        }
        return syncedStates[entryIndex];
    }

    /** Gets whether an entry has display data. */
    private boolean hasDisplayData(int entryIndex) {
        if (!hasSyncedData || entryIndex < 0 || entryIndex >= TileAutoCrafter.ENTRY_COUNT) {
            return container.getTile().getEntry(entryIndex).hasDisplayData();
        }
        return syncedHasDisplayData[entryIndex];
    }

    /** Gets the synced output item for an entry. */
    private IAEItemStack getSyncedOutput(int entryIndex) {
        if (!hasSyncedData || entryIndex < 0 || entryIndex >= TileAutoCrafter.ENTRY_COUNT) {
            return container.getTile().getEntry(entryIndex).getOutputItem();
        }
        return syncedOutputItems[entryIndex];
    }

    /** Gets the synced input grid for an entry. */
    private IAEItemStack[] getSyncedInputGrid(int entryIndex) {
        if (!hasSyncedData || entryIndex < 0 || entryIndex >= TileAutoCrafter.ENTRY_COUNT) {
            return container.getTile().getEntry(entryIndex).getInputGrid();
        }
        return syncedInputGrids[entryIndex];
    }

    /** Gets the synced error details for an entry. */
    private List<String> getSyncedErrorDetails(int entryIndex) {
        if (!hasSyncedData || entryIndex < 0 || entryIndex >= TileAutoCrafter.ENTRY_COUNT) {
            return container.getTile().getEntry(entryIndex).getErrorDetails();
        }
        return syncedErrorDetails.get(entryIndex);
    }

    /** Gets the synced occupancy for an entry. */
    private double getSyncedOccupancy(int entryIndex) {
        if (!hasSyncedData || entryIndex < 0 || entryIndex >= TileAutoCrafter.ENTRY_COUNT) {
            return container.getTile().getEntry(entryIndex).getOccupancy();
        }
        return syncedOccupancy[entryIndex];
    }

    /** Gets the synced error rate for an entry. */
    private double getSyncedErrorRate(int entryIndex) {
        if (!hasSyncedData || entryIndex < 0 || entryIndex >= TileAutoCrafter.ENTRY_COUNT) {
            return container.getTile().getEntry(entryIndex).getErrorRate();
        }
        return syncedErrorRate[entryIndex];
    }

    /** Gets the synced metrics total for an entry. */
    private long getSyncedMetricsTotal(int entryIndex) {
        if (!hasSyncedData || entryIndex < 0 || entryIndex >= TileAutoCrafter.ENTRY_COUNT) {
            return container.getTile().getEntry(entryIndex).getMetricsTotal();
        }
        return syncedMetricsTotal[entryIndex];
    }

    /** Gets synced speed ticks. */
    private int getSyncedSpeedTicks() {
        return hasSyncedData ? syncedSpeedTicks : container.syncSpeedTicks;
    }

    /** Gets synced batch size. */
    private int getSyncedBatchSize() {
        return hasSyncedData ? syncedBatchSize : container.syncBatchSize;
    }

    /** Gets synced effective batch size. */
    private int getSyncedEffectiveBatchSize() {
        return hasSyncedData ? syncedEffectiveBatchSize : container.syncEffectiveBatchSize;
    }

    /** Gets whether Ignore NBT is enabled for an entry. */
    private boolean isIgnoreNbtEnabled(int entryIndex) {
        if (hasSyncedData && entryIndex >= 0 && entryIndex < TileAutoCrafter.ENTRY_COUNT) {
            return syncedIgnoreNbt[entryIndex];
        }

        CrafterEntry entry = container.getTile().getEntry(entryIndex);
        return entry != null && entry.isIgnoreNbt();
    }
}
