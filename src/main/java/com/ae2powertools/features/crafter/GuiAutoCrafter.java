package com.ae2powertools.features.crafter;

import java.awt.Rectangle;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.lwjgl.input.Mouse;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.client.config.GuiUtils;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.storage.data.IAEItemStack;
import appeng.util.ReadableNumberConverter;

import com.ae2powertools.Tags;
import com.ae2powertools.features.crafter.widgets.CrafterOverviewOverlay;
import com.ae2powertools.features.crafter.pmt.PMTManager;
import com.ae2powertools.features.crafter.pmt.PMTRenderer;
import com.ae2powertools.features.crafter.pmt.PMTSlot;
import com.ae2powertools.network.PowerToolsNetwork;
import com.ae2powertools.util.FormatUtil;
import com.ae2powertools.widgets.BeveledButton;
import com.ae2powertools.widgets.QueuedItemRenderer;
import com.ae2powertools.widgets.TexturedButton;
import com.ae2powertools.widgets.WidgetContext;
import com.ae2powertools.widgets.WidgetDrawHelper;


/**
 * GUI for the AE2 AutoCrafter.
 * Recipe view (per-entry) with Overview modal overlay.
 * 
 * Modal system: the extracted overview overlay replaces the covered recipe/inventory
 * area while keeping the PMT panel visible to the left.
 * 
 * When NAE2 is installed and the player has a Pattern Multi-Tool, displays the PMT
 * panel to the left of the main GUI for convenient pattern storage access.
 */
@SideOnly(Side.CLIENT)
public class GuiAutoCrafter extends GuiContainer implements WidgetContext {

    private static final ResourceLocation RECIPE_TEXTURE = new ResourceLocation(
            Tags.MODID, "textures/guis/crafter_recipe.png");
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
    private static final int STATE_INDICATOR_WIDTH = 140;
    private static final int STATE_INDICATOR_HEIGHT = 12;
    private static final int PAGE_LEFT_X = 7;
    private static final int PAGE_LEFT_Y = 137;
    private static final int PAGE_RIGHT_X = 157;
    private static final int PAGE_RIGHT_Y = 137;
    private static final int UPGRADE_START_X = 187;
    private static final int UPGRADE_START_Y = 8;
    private static final int UPGRADE_SLOT_SIZE = 18;
    private static final int OVERVIEW_ROW_WIDTH = 162;

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

    // Pattern Multi-Tool panel offsets (when NAE2 is installed and player has PMT)
    // Positions the PMT panel to the left of the main GUI
    private static final int PMT_OFFSET_X = -86 - 4;
    private static final int PMT_OFFSET_Y = 25;

    private final ContainerAutoCrafter container;
    private final CrafterOverviewOverlay overviewOverlay;
    private final BeveledButton overviewButton = new BeveledButton(0, 0, PAGE_BTN_SIZE, PAGE_BTN_SIZE, "<");
    private final BeveledButton pagePrevButton = new BeveledButton(0, 0, PAGE_BTN_SIZE, PAGE_BTN_SIZE, "<");
    private final BeveledButton pageNextButton = new BeveledButton(0, 0, PAGE_BTN_SIZE, PAGE_BTN_SIZE, ">");
    private final TexturedButton batchButton = new TexturedButton(
        0, 0, TAB_BTN_SIZE, TAB_BTN_SIZE, BATCH_BUTTON_TEXTURE, 0, 0, TAB_BTN_SIZE, TAB_BTN_SIZE);
    private final TexturedButton speedButton = new TexturedButton(
        0, 0, TAB_BTN_SIZE, TAB_BTN_SIZE, SPEED_BUTTON_TEXTURE, 0, 0, TAB_BTN_SIZE, TAB_BTN_SIZE);

    // Hovered elements
    private int hoveredRecipeSlot = -1;
    private boolean hoveredResult = false;
    private boolean hoveredStateIndicator = false;

    // Track last known page for client-side slot updates when synced page changes
    private int lastKnownPage = -1;

    // ==================== PACKET-SYNCED CLIENT STATE ====================
    // These are populated by handleOverviewSync / handleRecipeSync when diff packets
    // arrive from server. Per-entry overview data is sync'd for all 12 entries; recipe
    // data (input grid, catalyst expectations, error details) is only sync'd for the
    // current page (server-authoritative).

    private boolean hasOverviewData = false;
    private boolean hasRecipeData = false;

    // Per-entry overview data (always populated for all 12 entries after first sync)
    private final CrafterState[] syncedStates = new CrafterState[TileAutoCrafter.ENTRY_COUNT];
    private final boolean[] syncedHasDisplayData = new boolean[TileAutoCrafter.ENTRY_COUNT];
    private final IAEItemStack[] syncedOutputItems = new IAEItemStack[TileAutoCrafter.ENTRY_COUNT];
    private final List<List<ITextComponent>> syncedOverviewErrorDetails = new ArrayList<>(TileAutoCrafter.ENTRY_COUNT);
    private final long[] syncedMetricsTotal = new long[TileAutoCrafter.ENTRY_COUNT];
    private final double[] syncedOccupancy = new double[TileAutoCrafter.ENTRY_COUNT];
    private final double[] syncedErrorRate = new double[TileAutoCrafter.ENTRY_COUNT];

    // Recipe data only kept for the entry the server told us is current.
    // recipeEntryIndex is checked when reading to guard against stale draws after a
    // page change, before the server's fresh recipe packet arrives.
    private int recipeEntryIndex = -1;
    private IAEItemStack[] syncedInputGrid = new IAEItemStack[9];
    private ItemStack syncedPatternStack = ItemStack.EMPTY;
    private final ItemStack[] syncedCatalystStacks = new ItemStack[CrafterEntry.CATALYST_SLOTS];
    private final List<CatalystInfo> syncedCatalystInfo = new ArrayList<>();
    private List<ITextComponent> syncedErrorDetails = new ArrayList<>();

    /** Simple holder for catalyst slot info (slot index + expected ghost item). */
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

        this.overviewOverlay = new CrafterOverviewOverlay(
            this,
            this::hasDisplayData,
            this::getSyncedState,
            this::getSyncedOutput,
            this::getSyncedErrorDetails,
            this::getSyncedOccupancy,
            this::getSyncedErrorRate,
            this::getSyncedMetricsTotal,
            this::getEntryOverviewInfo,
            state -> state.getTranslated(),
            this::setCurrentPage,
            this::toggleEntryFromOverview);

        overviewButton.setOnClick(overviewOverlay::open);
        overviewButton.setTooltipProvider(() -> Collections.singletonList(I18n.format("gui.ae2powertools.crafter.overview")));
        pagePrevButton.setOnClick(() -> {
            if (getCurrentPage() > 0) setCurrentPage(getCurrentPage() - 1);
        });
        pagePrevButton.setTooltipProvider(() -> Collections.singletonList(I18n.format("gui.ae2powertools.crafter.page.previous")));
        pageNextButton.setOnClick(() -> {
            if (getCurrentPage() < TileAutoCrafter.ENTRY_COUNT - 1) setCurrentPage(getCurrentPage() + 1);
        });
        pageNextButton.setTooltipProvider(() -> Collections.singletonList(I18n.format("gui.ae2powertools.crafter.page.next")));
        batchButton.setOnClick(this::openBatchDialog);
        batchButton.setTooltipProvider(this::buildBatchButtonTooltip);
        speedButton.setOnClick(this::openSpeedDialog);
        speedButton.setTooltipProvider(this::buildSpeedButtonTooltip);

        // Initialize synced overview arrays so the GUI renders cleanly before the first
        // packet arrives (it should arrive on the same tick the GUI opens, but be safe).
        for (int i = 0; i < TileAutoCrafter.ENTRY_COUNT; i++) {
            syncedStates[i] = CrafterState.NO_PATTERN;
            syncedOverviewErrorDetails.add(Collections.emptyList());
        }

        for (int i = 0; i < syncedCatalystStacks.length; i++) {
            syncedCatalystStacks[i] = ItemStack.EMPTY;
        }
    }

    /**
     * Returns the underlying container. Used by network handlers (e.g. PacketCrafterPageInit)
     * that need to mutate container state on the client thread.
     */
    public ContainerAutoCrafter getContainer() {
        return container;
    }

    /**
     * Records that the client has acknowledged the server's current page so that the
     * drawScreen mismatch-detection loop does not redundantly call setCurrentEntryIndex
     * on the next render. Called by PacketCrafterPageInit after it has applied the page.
     */
    public void acknowledgeServerPage(int page) {
        this.lastKnownPage = page;
    }

    /**
     * Gets the current page - the container's @GuiSync field is the authoritative source.
     * The container syncs this from {@link TileAutoCrafter#getCurrentPage()} via @GuiSync.
     */
    private int getCurrentPage() {
        return container.getCurrentEntryIndex();
    }

    @Override
    public void initGui() {
        super.initGui();

        buttonList.clear();

        overviewOverlay.initGui(guiLeft, guiTop);
    }

    /**
     * Sets the current page and syncs to server for persistence.
     * Updates local container state immediately for responsive UI; the next server sync
     * will overwrite it if the server rejects the change (e.g., out-of-range index).
     */
    private void setCurrentPage(int page) {
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

        // Draw PMT slot hover highlights manually
        // Vanilla's drawScreen doesn't draw hover for disabled slots, so we need to handle it
        drawPMTSlotHovers(mouseX, mouseY);

        if (overviewOverlay.isOpen()) {
            overviewOverlay.draw(mouseX, mouseY, partialTicks);
            overviewOverlay.drawTooltip(mouseX, mouseY);
            return;  // Modal blocks other interactions
        }

        renderHoveredToolTipWithPatternWarning(mouseX, mouseY);
        drawRecipeTooltips(mouseX, mouseY);
        drawAE2ButtonTooltips(mouseX, mouseY);
    }

    /**
     * Custom tooltip rendering that adds processing pattern warnings.
     * Processing patterns are incompatible with the AutoCrafter, so we warn the player
     * on ALL processing patterns visible in the GUI (inventory, PMT, crafter slots).
     */
    private void renderHoveredToolTipWithPatternWarning(int mouseX, int mouseY) {
        if (mc.player.inventory.getItemStack().isEmpty() && getSlotUnderMouse() != null) {
            Slot slot = getSlotUnderMouse();
            ItemStack stack = getDisplayedSlotStack(slot);

            if (!stack.isEmpty()) {
                List<String> tooltip = stack.getTooltip(mc.player, mc.gameSettings.advancedItemTooltips
                        ? ITooltipFlag.TooltipFlags.ADVANCED : ITooltipFlag.TooltipFlags.NORMAL);

                // Add processing pattern warning if applicable
                PMTRenderer.addProcessingPatternWarning(stack, tooltip);

                GuiUtils.drawHoveringText(tooltip, mouseX, mouseY, width, height, -1, fontRenderer);
                return;
            }
        }

        // Fallback to default behavior
        renderHoveredToolTip(mouseX, mouseY);
    }

    /**
     * Draws hover highlights for PMT slots.
     * 
     * Vanilla's drawScreen skips hover highlighting for disabled slots, but we want
     * to show hover for both.
     */
    private void drawPMTSlotHovers(int mouseX, int mouseY) {
        PMTManager pmtManager = container.getPMTManager();
        if (pmtManager == null || !pmtManager.hasPMT()) return;

        for (Slot slot : container.inventorySlots) {
            if (!(slot instanceof PMTSlot)) continue;

            PMTSlot pmtSlot = (PMTSlot) slot;
            int slotX = guiLeft + slot.xPos;
            int slotY = guiTop + slot.yPos;

            // Check if mouse is over this slot and it's not enabled
            if (mouseX >= slotX && mouseX < slotX + 16 &&
                mouseY >= slotY && mouseY < slotY + 16 &&
                !pmtSlot.isSlotEnabled()) {

                GlStateManager.disableLighting();
                GlStateManager.disableDepth();
                GlStateManager.colorMask(true, true, true, false);

                // Normal white hover (same as vanilla)
                drawRect(slotX, slotY, slotX + 16, slotY + 16, 0x80FFFFFF);

                GlStateManager.colorMask(true, true, true, true);
                GlStateManager.enableDepth();
                GlStateManager.enableLighting();

                // Only one slot can be hovered at a time
                break;
            }
        }
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        // Draw Pattern Multi-Tool panel (if player has PMT)
        PMTManager pmtManager = container.getPMTManager();
        PMTRenderer.drawBackground(this, pmtManager, guiLeft, guiTop, PMT_OFFSET_X, PMT_OFFSET_Y);

        // Reset GL state after PMT drawing
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        // Always draw recipe texture as base
        mc.getTextureManager().bindTexture(RECIPE_TEXTURE);
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, GUI_WIDTH, GUI_HEIGHT);

        // Draw PMT slot backgrounds (pattern icons for empty slots)
        PMTRenderer.drawSlotBackgrounds(this, pmtManager, guiLeft, guiTop);

        // Draw upgrade slot icons (for empty slots)
        drawUpgradeSlotIcons();

        // Short circuit to skip drawing recipe/inventory if overview is open.
        // This avoids content underneath leaking GL state to the overview (eating item renders, etc).
        if (overviewOverlay.isOpen()) return;

        // Draw pattern slot icon (for empty slot)
        drawPatternSlotIcon();

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
        for (int i = 0; i < TileAutoCrafter.UPGRADE_SLOTS; i++) {
            ItemStack slotContent = container.getTile().getUpgradeStack(i);
            if (slotContent.isEmpty()) {
                // Draw the upgrade icon for empty slots with AE2-style transparency
                int x = guiLeft + UPGRADE_START_X;
                int y = guiTop + UPGRADE_START_Y + i * UPGRADE_SLOT_SIZE;
                WidgetDrawHelper.drawUpgradePlaceholder(mc, x, y);
            }
        }
    }

    /**
     * Draws the pattern slot background icon when the pattern slot is empty.
     * Uses AE2's states.png texture for the "insert pattern" icon.
     * Applies 0.4f opacity to match AE2's grayed-out style for empty slots.
     */
    private void drawPatternSlotIcon() {
        // Check if the current entry has a pattern
        int currentPage = getCurrentPage();
        ItemStack patternStack = container.getTile().getEntry(currentPage).getPatternStack();
        if (patternStack != null && !patternStack.isEmpty()) return;
        int x = guiLeft + PATTERN_SLOT_X;
        int y = guiTop + PATTERN_SLOT_Y;
        WidgetDrawHelper.drawPatternPlaceholder(mc, x, y);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        PMTManager pmtManager = container.getPMTManager();
        if (overviewOverlay.isOpen()) {
            PMTRenderer.drawSlotOverlays(this, pmtManager);
            return;
        }

        // Draw title (shifted right to make room for overview/back button on the left)
        String title = I18n.format("gui.ae2powertools.crafter.title");
        fontRenderer.drawString(title, OVERVIEW_BTN_X + PAGE_BTN_SIZE + 4, 7, 0x404040);

        // Draw page indicator in recipe mode
        String pageText = I18n.format("gui.ae2powertools.crafter.page", getCurrentPage() + 1, TileAutoCrafter.ENTRY_COUNT);
        int pageWidth = fontRenderer.getStringWidth(pageText);
        int centerX = (PAGE_LEFT_X + 12 + PAGE_RIGHT_X) / 2 - pageWidth / 2;
        fontRenderer.drawString(pageText, centerX, PAGE_LEFT_Y + 2, 0x404040);

        // Draw PMT slot count overlays (AE2 handles item rendering via getStack()/getDisplayStack())
        PMTRenderer.drawSlotOverlays(this, pmtManager);
    }

    /**
     * Override drawSlot to handle PMTSlot rendering specially.
     * For PMTSlots, we render the pattern OUTPUT instead of the encoded pattern item.
     * This is normally done by AEBaseGui, but since we extend GuiContainer directly,
     * we need to do it ourselves.
     * 
     * IMPORTANT: We must NOT call super.drawSlot for PMTSlots at all, as it may
     * trigger unexpected validation rendering from parent classes.
     */
    @Override
    public void drawSlot(Slot slotIn) {
        // Refuse to render inventory slots when overview modal is open
        if (overviewOverlay.isOpen() && overviewOverlay.coversRelativeRegion(slotIn.xPos, slotIn.yPos, 16, 16)) {
            return;
        }

        if (slotIn instanceof ContainerAutoCrafter.SlotPattern) {
            drawCrafterSlotItem(getDisplayedPatternStack(getCurrentPage()), slotIn.xPos, slotIn.yPos);
            return;
        }

        if (slotIn instanceof ContainerAutoCrafter.SlotCatalyst) {
            ContainerAutoCrafter.SlotCatalyst catalystSlot = (ContainerAutoCrafter.SlotCatalyst) slotIn;
            drawCrafterSlotItem(getDisplayedCatalystStack(getCurrentPage(), catalystSlot.getCatalystIndex()), slotIn.xPos, slotIn.yPos);
            return;
        }

        if (!(slotIn instanceof PMTSlot)) {
            super.drawSlot(slotIn);
            return;
        }

        PMTSlot pmtSlot = (PMTSlot) slotIn;
        int x = slotIn.xPos;
        int y = slotIn.yPos;

        // Get the display stack (pattern output) instead of the actual stack
        ItemStack displayStack = pmtSlot.getDisplayStack();

        // For disabled slots, don't render any item
        if (!pmtSlot.isSlotEnabled()) return;

        // For empty enabled slots, we don't render anything (slot is just empty)
        if (displayStack.isEmpty()) return;

        // Render the display stack (output item) at the slot position
        GlStateManager.enableDepth();
        RenderHelper.enableGUIStandardItemLighting();
        this.itemRender.renderItemAndEffectIntoGUI(mc.player, displayStack, x, y);
        // Don't render vanilla stack count - we do it ourselves in drawSlotOverlays
        RenderHelper.disableStandardItemLighting();
    }

    /**
     * Draws custom buttons: Overview toggle, page navigation, batch and speed.
     * All buttons are custom drawn (not GuiButton) for consistent styling.
     */
    private void drawAE2Buttons(int mouseX, int mouseY) {
        overviewButton.setPosition(guiLeft + OVERVIEW_BTN_X, guiTop + OVERVIEW_BTN_Y);
        overviewButton.setEnabled(true);
        overviewButton.draw(this, mouseX, mouseY);

        if (!overviewOverlay.isOpen()) {
            pagePrevButton.setPosition(guiLeft + PAGE_LEFT_X, guiTop + PAGE_LEFT_Y);
            pagePrevButton.setEnabled(getCurrentPage() > 0);
            pagePrevButton.draw(this, mouseX, mouseY);

            pageNextButton.setPosition(guiLeft + PAGE_RIGHT_X, guiTop + PAGE_RIGHT_Y);
            pageNextButton.setEnabled(getCurrentPage() < TileAutoCrafter.ENTRY_COUNT - 1);
            pageNextButton.draw(this, mouseX, mouseY);

            batchButton.setPosition(guiLeft + BATCH_BTN_X, guiTop + BATCH_BTN_Y);
            batchButton.draw(this, mouseX, mouseY);

            speedButton.setPosition(guiLeft + SPEED_BTN_X, guiTop + SPEED_BTN_Y);
            speedButton.draw(this, mouseX, mouseY);
        }
    }

    /**
     * Draws tooltips for custom buttons.
     */
    private void drawAE2ButtonTooltips(int mouseX, int mouseY) {
        overviewButton.drawTooltip(this, mouseX, mouseY);
        pagePrevButton.drawTooltip(this, mouseX, mouseY);
        pageNextButton.drawTooltip(this, mouseX, mouseY);
        batchButton.drawTooltip(this, mouseX, mouseY);
        speedButton.drawTooltip(this, mouseX, mouseY);
    }

    // ==================== RECIPE VIEW ====================

    private void drawRecipeContent(int mouseX, int mouseY) {
        int currentPage = getCurrentPage();
        QueuedItemRenderer itemQueue = new QueuedItemRenderer();

        // Don't update hover state if overview modal is open
        if (!overviewOverlay.isOpen()) {
            hoveredRecipeSlot = -1;
            hoveredResult = false;
            hoveredStateIndicator = false;
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
                        if (!overviewOverlay.isOpen() && mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                            hoveredRecipeSlot = slotIndex;
                        }

                        // Draw item
                        if (inputGrid[slotIndex] != null) {
                            queueItemStack(itemQueue, inputGrid[slotIndex].createItemStack(), x, y);
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
                if (!overviewOverlay.isOpen() && mouseX >= slotX && mouseX < slotX + RECIPE_RESULT_SIZE &&
                        mouseY >= slotY && mouseY < slotY + RECIPE_RESULT_SIZE) {
                    hoveredResult = true;
                }

                queueItemStack(itemQueue, outputItem.createItemStack(), itemX, itemY);
            }

            // Draw catalyst ghost items using synced catalyst data
            drawCatalystGhosts(currentPage, itemQueue);
            itemQueue.flush(this);

            // Draw speed info (under catalyst slots) using synced values
            drawSpeedInfo(currentPage, guiLeft + SPEED_INFO_X, guiTop + SPEED_INFO_Y);
        }

        // ALWAYS draw state indicator - shows NO_PATTERN for empty entries
        int stateX = guiLeft + STATE_INDICATOR_X;
        int stateY = guiTop + STATE_INDICATOR_Y;
        hoveredStateIndicator = mouseX >= stateX && mouseX < stateX + STATE_INDICATOR_WIDTH
            && mouseY >= stateY && mouseY < stateY + STATE_INDICATOR_HEIGHT;
        drawStateIndicator(currentPage, stateX, stateY);
    }

    /**
     * Draws catalyst ghost items using synced recipe data.
     * Shows ghost items for empty catalyst slots based on the recipe requirements,
     * while using the explicitly-synced current catalyst contents to decide whether the
     * real item is present.
     */
    private void drawCatalystGhosts(int entryIndex, QueuedItemRenderer itemQueue) {
        List<CatalystInfo> catalysts = getSyncedCatalystInfo(entryIndex);
        if (catalysts.isEmpty()) return;

        for (CatalystInfo catalyst : catalysts) {
            int slotIndex = catalyst.slotIndex;
            if (slotIndex < 0 || slotIndex >= CrafterEntry.CATALYST_SLOTS) continue;

            ItemStack current = getDisplayedCatalystStack(entryIndex, slotIndex);

            // Position based on actual slot index, not iteration index
            int x = guiLeft + CATALYST_START_X + slotIndex * 18;
            int y = guiTop + CATALYST_START_Y;

            if ((current == null || current.isEmpty()) && catalyst.expectedItem != null) {
                queueGhostItemStack(itemQueue, catalyst.expectedItem.createItemStack(), x, y);
            }
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

        // Calculate throughput using @GuiSync values from container
        long speedTicks = container.syncSpeedTicks;
        long batchSize = container.syncBatchSize;
        long outputCount = output.getStackSize();

        long itemsPerCraft = CrafterMath.saturatingMultiply(container.syncEffectiveBatchSize, outputCount);
        String itemsPerCraftStr = ReadableNumberConverter.INSTANCE.toWideReadableForm(itemsPerCraft);
        String timePerOperation = FormatUtil.formatTimeTicks(CrafterMath.saturatingMultiply(speedTicks, batchSize));
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

        if ((bgColor & 0xFF000000) != 0) drawRect(x, y, x + STATE_INDICATOR_WIDTH, y + STATE_INDICATOR_HEIGHT, bgColor);

        fontRenderer.drawString(state.getTranslated(), x + 2, y + 2, textColor);
    }

    private void drawRecipeTooltips(int mouseX, int mouseY) {
        int currentPage = getCurrentPage();
        if (hasDisplayData(currentPage)) {
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
                ItemStack patternStack = getDisplayedPatternStack(currentPage);
                if (!patternStack.isEmpty()) drawItemTooltip(patternStack, mouseX, mouseY);
            }
        }

        drawStateIndicatorTooltip(currentPage, mouseX, mouseY);
    }

    private void drawStateIndicatorTooltip(int entryIndex, int mouseX, int mouseY) {
        if (!hoveredStateIndicator) return;

        CrafterState state = getSyncedState(entryIndex);
        List<ITextComponent> errorDetails = getSyncedErrorDetails(entryIndex);
        if (!state.isError() && state != CrafterState.HOLDING_OUTPUT && errorDetails.isEmpty()) return;

        List<String> tooltip = new ArrayList<>();
        tooltip.add(TextFormatting.GRAY + I18n.format("gui.ae2powertools.crafter.state")
                + ": " + TextFormatting.RESET + state.getTranslated());

        if (!errorDetails.isEmpty()) {
            tooltip.add("");
            for (ITextComponent detail : errorDetails) {
                tooltip.add(TextFormatting.GRAY + "- " + detail.getFormattedText());
            }
        }

        GuiUtils.drawHoveringText(tooltip, mouseX, mouseY, width, height, -1, fontRenderer);
    }

    private String getEntryOverviewInfo(int index) {
        // Use synced data
        if (!hasDisplayData(index)) {
            CrafterState state = getSyncedState(index);
            if (state == CrafterState.NO_PATTERN) return I18n.format("gui.ae2powertools.crafter.empty_slot", index + 1);

            return fontRenderer.trimStringToWidth(state.getTranslated(), OVERVIEW_ROW_WIDTH - 24);
        }

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

    // ==================== INPUT HANDLING ====================

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (overviewOverlay.keyTyped(typedChar, keyCode)) return;

        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (overviewOverlay.mouseClicked(mouseX, mouseY, mouseButton)) return;

        // Custom button handling (overview toggle, page navigation, batch/speed)
        if (mouseButton == 0) {
            if (overviewButton.mouseClicked(mouseX, mouseY, mouseButton)) return;
            if (pagePrevButton.mouseClicked(mouseX, mouseY, mouseButton)) return;
            if (pageNextButton.mouseClicked(mouseX, mouseY, mouseButton)) return;
            if (batchButton.mouseClicked(mouseX, mouseY, mouseButton)) return;
            if (speedButton.mouseClicked(mouseX, mouseY, mouseButton)) return;
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

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();

        int scroll = Mouse.getEventDWheel();
        if (scroll == 0) return;

        if (overviewOverlay.handleMouseWheel(scroll)) return;

        // In recipe mode, scroll through pages
        if (scroll > 0 && getCurrentPage() > 0) {
            setCurrentPage(getCurrentPage() - 1);
        } else if (scroll < 0 && getCurrentPage() < TileAutoCrafter.ENTRY_COUNT - 1) {
            setCurrentPage(getCurrentPage() + 1);
        }
    }

    // ==================== HELPERS ====================

    @Override
    protected boolean isPointInRegion(int rectX, int rectY, int rectWidth, int rectHeight, int pointX, int pointY) {
        // Refuse all inventory slots interactions when overview modal is open, to prevent rendering
        if (overviewOverlay.isOpen() && overviewOverlay.coversRelativeRegion(rectX, rectY, rectWidth, rectHeight)) {
            return false;
        }

        return super.isPointInRegion(rectX, rectY, rectWidth, rectHeight, pointX, pointY);
    }

    private void drawItemTooltip(ItemStack stack, int mouseX, int mouseY) {
        if (stack.isEmpty()) return;

        List<String> tooltip = stack.getTooltip(mc.player, mc.gameSettings.advancedItemTooltips
                ? ITooltipFlag.TooltipFlags.ADVANCED : ITooltipFlag.TooltipFlags.NORMAL);

        GuiUtils.drawHoveringText(tooltip, mouseX, mouseY, width, height, -1, fontRenderer);
    }

    // ==================== PACKET-BASED STATE SYNC ====================

    /**
     * Handles incoming overview diff packet from server.
     * Each entry index in the map is updated; entries not present in the packet keep
     * their previously-cached values.
     */
    public void handleOverviewSync(Map<Integer, CrafterOverviewSnapshot> snapshots) {
        for (Map.Entry<Integer, CrafterOverviewSnapshot> e : snapshots.entrySet()) {
            int i = e.getKey();
            if (i < 0 || i >= TileAutoCrafter.ENTRY_COUNT) continue;

            CrafterOverviewSnapshot snap = e.getValue();
            int stateOrdinal = snap.getStateOrdinal();
            if (stateOrdinal >= 0 && stateOrdinal < CrafterState.values().length) {
                syncedStates[i] = CrafterState.values()[stateOrdinal];
            } else {
                syncedStates[i] = CrafterState.NO_PATTERN;
            }

            syncedHasDisplayData[i] = snap.hasDisplayData();
            syncedOutputItems[i] = snap.getOutput();
            syncedOverviewErrorDetails.set(i, new ArrayList<>(snap.getErrorDetails()));

            // Calculate rates from raw metrics
            long total = snap.getMetricsTotal();
            long error = snap.getMetricsError();
            long actualCrafted = snap.getMetricsTotalActualCrafted();
            long maxPossible = snap.getMetricsTotalMaxPossible();
            syncedMetricsTotal[i] = total;
            syncedErrorRate[i] = total > 0 ? (error * 100.0) / total : 0.0;
            syncedOccupancy[i] = maxPossible > 0 ? (actualCrafted * 100.0) / maxPossible : 0.0;
        }

        hasOverviewData = true;
    }

    /**
     * Handles incoming recipe diff packet from server.
     * The packet is tagged with the entryIndex it describes; if it doesn't match the
     * page the GUI currently believes is active, we still accept it (server is
     * authoritative on which page should be shown - the @GuiSync field will catch up
     * shortly).
     */
    public void handleRecipeSync(int entryIndex, CrafterRecipeSnapshot snapshot) {
        recipeEntryIndex = entryIndex;

        IAEItemStack[] srcGrid = snapshot.getInputGrid();
        // Defensive copy with fixed length 9 so out-of-bounds indices don't bite us
        for (int i = 0; i < 9; i++) syncedInputGrid[i] = i < srcGrid.length ? srcGrid[i] : null;

        ItemStack patternStack = snapshot.getPatternStack();
        syncedPatternStack = patternStack.isEmpty() ? ItemStack.EMPTY : patternStack.copy();

        ItemStack[] catalystStacks = snapshot.getCatalystStacks();
        for (int i = 0; i < syncedCatalystStacks.length; i++) {
            ItemStack stack = i < catalystStacks.length && catalystStacks[i] != null ? catalystStacks[i] : ItemStack.EMPTY;
            syncedCatalystStacks[i] = stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
        }

        syncedCatalystInfo.clear();
        for (CrafterRecipeSnapshot.CatalystExpectation cat : snapshot.getCatalysts()) {
            syncedCatalystInfo.add(new CatalystInfo(cat.slotIndex, cat.expectedItem));
        }

        syncedErrorDetails = new ArrayList<>(snapshot.getErrorDetails());

        hasRecipeData = true;
    }

    // ==================== SYNCED DATA ACCESSORS ====================

    /** Gets the synced state for an entry. */
    private CrafterState getSyncedState(int entryIndex) {
        if (!hasOverviewData || entryIndex < 0 || entryIndex >= TileAutoCrafter.ENTRY_COUNT) {
            return CrafterState.NO_PATTERN;
        }
        return syncedStates[entryIndex];
    }

    /** Gets whether an entry has display data. */
    private boolean hasDisplayData(int entryIndex) {
        if (!hasOverviewData || entryIndex < 0 || entryIndex >= TileAutoCrafter.ENTRY_COUNT) return false;
        return syncedHasDisplayData[entryIndex];
    }

    /** Gets the synced output item for an entry. */
    private IAEItemStack getSyncedOutput(int entryIndex) {
        if (!hasOverviewData || entryIndex < 0 || entryIndex >= TileAutoCrafter.ENTRY_COUNT) return null;
        return syncedOutputItems[entryIndex];
    }

    /**
     * Gets the synced input grid for an entry. Recipe data is only sync'd for the
     * current page, so this returns null for any entry other than {@link #recipeEntryIndex}.
     * The recipe view only ever calls this for the current page, so that's the only
     * case that matters in practice.
     */
    @Nullable
    private IAEItemStack[] getSyncedInputGrid(int entryIndex) {
        if (!hasRecipeData || entryIndex != recipeEntryIndex) return null;
        return syncedInputGrid;
    }

    /**
     * Gets the synced catalyst expectations for an entry. Same scoping as
     * {@link #getSyncedInputGrid(int)}: only valid for the current page.
     */
    private List<CatalystInfo> getSyncedCatalystInfo(int entryIndex) {
        if (!hasRecipeData || entryIndex != recipeEntryIndex) return Collections.emptyList();
        return syncedCatalystInfo;
    }

    /** Gets the synced error details for an entry. */
    private List<ITextComponent> getSyncedErrorDetails(int entryIndex) {
        if (hasRecipeData && entryIndex == recipeEntryIndex) return syncedErrorDetails;
        if (!hasOverviewData || entryIndex < 0 || entryIndex >= TileAutoCrafter.ENTRY_COUNT) return Collections.emptyList();
        return syncedOverviewErrorDetails.get(entryIndex);
    }

    private ItemStack getDisplayedPatternStack(int entryIndex) {
        if (hasRecipeData && entryIndex == recipeEntryIndex) return syncedPatternStack;

        CrafterEntry entry = container.getCurrentEntry();
        if (entry == null || !entry.hasPattern()) return ItemStack.EMPTY;

        ItemStack patternStack = entry.getPatternStack();
        return patternStack == null ? ItemStack.EMPTY : patternStack;
    }

    private ItemStack getDisplayedCatalystStack(int entryIndex, int catalystIndex) {
        if (catalystIndex < 0 || catalystIndex >= syncedCatalystStacks.length) return ItemStack.EMPTY;
        if (hasRecipeData && entryIndex == recipeEntryIndex) return syncedCatalystStacks[catalystIndex];

        return container.getCatalystSlotStack(catalystIndex);
    }

    private ItemStack getDisplayedSlotStack(Slot slot) {
        if (slot instanceof ContainerAutoCrafter.SlotPattern) {
            return getDisplayedPatternStack(getCurrentPage());
        }

        if (slot instanceof ContainerAutoCrafter.SlotCatalyst) {
            ContainerAutoCrafter.SlotCatalyst catalystSlot = (ContainerAutoCrafter.SlotCatalyst) slot;
            return getDisplayedCatalystStack(getCurrentPage(), catalystSlot.getCatalystIndex());
        }

        return slot.getStack();
    }

    /** Gets the synced occupancy for an entry. */
    private double getSyncedOccupancy(int entryIndex) {
        if (!hasOverviewData || entryIndex < 0 || entryIndex >= TileAutoCrafter.ENTRY_COUNT) return 0.0;
        return syncedOccupancy[entryIndex];
    }

    /** Gets the synced error rate for an entry. */
    private double getSyncedErrorRate(int entryIndex) {
        if (!hasOverviewData || entryIndex < 0 || entryIndex >= TileAutoCrafter.ENTRY_COUNT) return 0.0;
        return syncedErrorRate[entryIndex];
    }

    /** Gets the synced metrics total for an entry. */
    private long getSyncedMetricsTotal(int entryIndex) {
        if (!hasOverviewData || entryIndex < 0 || entryIndex >= TileAutoCrafter.ENTRY_COUNT) return 0L;
        return syncedMetricsTotal[entryIndex];
    }

    /**
     * Returns the Pattern Multi-Tool panel bounds so JEI/HEI keeps its sidebar clear
     * when the player has a PMT available.
     */
    public List<Rectangle> getJEIExclusionArea() {
        PMTManager pmtManager = container.getPMTManager();
        if (pmtManager == null || !pmtManager.hasPMT()) return Collections.emptyList();

        return Collections.singletonList(new Rectangle(
                guiLeft + PMT_OFFSET_X,
                guiTop + PMT_OFFSET_Y,
                PMTRenderer.PMT_WIDTH,
                PMTRenderer.PMT_HEIGHT));
    }

    /**
     * Checks if the player clicked outside the GUI area.
     * Extended to include the Pattern Multi-Tool panel when player has PMT.
     */
    @Override
    protected boolean hasClickedOutside(int mouseX, int mouseY, int guiLeft, int guiTop) {
        // Check if clicked inside the PMT panel
        PMTManager pmtManager = container.getPMTManager();
        if (pmtManager != null && pmtManager.hasPMT()) {
            if (PMTRenderer.isMouseInsidePanel(mouseX, mouseY, guiLeft, guiTop, PMT_OFFSET_X, PMT_OFFSET_Y)) {
                return false;
            }
        }

        return super.hasClickedOutside(mouseX, mouseY, guiLeft, guiTop);
    }

    private void toggleEntryFromOverview(int entryIndex) {
        CrafterEntry entry = container.getTile().getEntry(entryIndex);
        if (entry == null || !entry.hasPattern()) return;

        PowerToolsNetwork.INSTANCE.sendToServer(new PacketToggleCrafterEntry(
            container.getTile().getPos(),
            entryIndex));
    }

    private List<String> buildBatchButtonTooltip() {
        List<String> tooltip = new ArrayList<>();
        tooltip.add(I18n.format("gui.ae2powertools.crafter.batch.title"));
        tooltip.add(TextFormatting.GRAY + I18n.format("gui.ae2powertools.crafter.batch.desc", container.syncBatchSize));
        tooltip.add("");
        tooltip.add(TextFormatting.DARK_GRAY + I18n.format("gui.ae2powertools.crafter.batch.explanation"));
        return tooltip;
    }

    private List<String> buildSpeedButtonTooltip() {
        List<String> tooltip = new ArrayList<>();
        tooltip.add(I18n.format("gui.ae2powertools.crafter.speed.title"));
        tooltip.add(TextFormatting.GRAY + I18n.format(
            "gui.ae2powertools.crafter.speed.desc",
            FormatUtil.formatTimeTicks(container.syncSpeedTicks)));
        tooltip.add("");
        tooltip.add(TextFormatting.DARK_GRAY + I18n.format("gui.ae2powertools.crafter.speed.explanation"));
        return tooltip;
    }

    private void drawCrafterSlotItem(ItemStack stack, int x, int y) {
        if (stack == null || stack.isEmpty()) return;

        GlStateManager.enableDepth();
        RenderHelper.enableGUIStandardItemLighting();
        this.itemRender.renderItemAndEffectIntoGUI(mc.player, stack, x, y);
        this.itemRender.renderItemOverlayIntoGUI(this.fontRenderer, stack, x, y, null);
        RenderHelper.disableStandardItemLighting();
    }

    private void queueItemStack(QueuedItemRenderer itemQueue, ItemStack stack, int x, int y) {
        // Queueing keeps the dense recipe surfaces readable in code and ensures they all render under
        // one shared lighting setup rather than bouncing GL state for every individual stack.
        itemQueue.queue(context -> {
            context.getWidgetItemRenderer().renderItemAndEffectIntoGUI(stack, x, y);
            context.getWidgetItemRenderer().renderItemOverlayIntoGUI(context.getWidgetFontRenderer(), stack, x, y, null);
        });
    }

    private void queueGhostItemStack(QueuedItemRenderer itemQueue, ItemStack stack, int x, int y) {
        itemQueue.queue(context -> {
            context.getWidgetItemRenderer().renderItemAndEffectIntoGUI(stack, x, y);
            context.getWidgetItemRenderer().renderItemOverlayIntoGUI(context.getWidgetFontRenderer(), stack, x, y, null);

            RenderHelper.disableStandardItemLighting();
            GlStateManager.disableLighting();
            GlStateManager.disableDepth();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            drawRect(x, y, x + 16, y + 16, 0xAA555555);
            GlStateManager.disableBlend();
            GlStateManager.enableDepth();
            RenderHelper.enableGUIStandardItemLighting();
        });
    }

    @Override
    public Minecraft getWidgetMinecraft() {
        return mc;
    }

    @Override
    public FontRenderer getWidgetFontRenderer() {
        return fontRenderer;
    }

    @Override
    public RenderItem getWidgetItemRenderer() {
        return itemRender;
    }

    @Override
    public int getWidgetWidth() {
        return width;
    }

    @Override
    public int getWidgetHeight() {
        return height;
    }

}
