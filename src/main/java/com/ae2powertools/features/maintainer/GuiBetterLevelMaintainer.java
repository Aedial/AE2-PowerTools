package com.ae2powertools.features.maintainer;

import java.awt.Rectangle;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.lwjgl.input.Keyboard;

import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.storage.data.IAEItemStack;

import com.ae2powertools.Tags;
import com.ae2powertools.client.PowerToolsClientConfig;
import com.ae2powertools.features.maintainer.widgets.MaintainerEntryEditorOverlay;
import com.ae2powertools.features.maintainer.widgets.MaintainerEntryViewport;
import com.ae2powertools.network.PacketSelectRecipe;
import com.ae2powertools.network.PacketUpdateMaintainerEntry;
import com.ae2powertools.network.PowerToolsNetwork;
import com.ae2powertools.widgets.Ae2Button;
import com.ae2powertools.widgets.WidgetAnchor;
import com.ae2powertools.widgets.WidgetGui;


/**
 * Main GUI for the Better Level Maintainer.
 */
@SideOnly(Side.CLIENT)
public class GuiBetterLevelMaintainer extends WidgetGui {

    private static final ResourceLocation BACKGROUND = new ResourceLocation(
        Tags.MODID, "textures/guis/maintainer_gui.png");
    private static final ResourceLocation BACKGROUND_TALL = new ResourceLocation(
        Tags.MODID, "textures/guis/maintainer_gui_tall.png");

    private static final int GUI_WIDTH = 238;
    private static final int GUI_HEIGHT = 206;

    private static final int SEARCH_X = 130;
    private static final int SEARCH_Y = 4;
    private static final int SEARCH_WIDTH = 80;
    private static final int SEARCH_HEIGHT = 12;

    private static final int TALL_GUI_WIDTH = 238;
    private static final int TALL_GUI_BASE_HEIGHT = 92;
    private static final int TALL_SLICE_START_Y = 19;
    private static final int TALL_SLICE_END_Y = 42;
    private static final int TALL_SLICE_HEIGHT = TALL_SLICE_END_Y - TALL_SLICE_START_Y;
    private static final int TALL_MARGIN = 10;

    private static final int STYLE_BUTTON_SIZE = 16;
    private static final int STYLE_ICON_TALL = 13 * 16;
    private static final int STYLE_ICON_SMALL = 13 * 16 + 1;

    private final ContainerBetterLevelMaintainer container;
    private final MaintainerEntryViewport entryViewport;
    private final MaintainerEntryEditorOverlay entryEditorOverlay;
    private final Ae2Button styleButton = new Ae2Button(0, 0, STYLE_BUTTON_SIZE);

    private GuiTextField searchField;
    private boolean useTallView;
    private int tallVisibleRows = 6;
    private int tallScrollbarHeight;

    public GuiBetterLevelMaintainer(ContainerBetterLevelMaintainer container) {
        super(container, GUI_WIDTH, GUI_HEIGHT, null);

        this.container = container;
        this.entryViewport = new MaintainerEntryViewport(this, container);
        this.entryEditorOverlay = new MaintainerEntryEditorOverlay(
            this,
            container::getCraftableItems,
            this::selectRecipe,
            this::sendModalEntryUpdate);
        registerModal(this.entryEditorOverlay, WidgetAnchor.SCREEN_CENTER, 0, 0);

        this.useTallView = PowerToolsClientConfig.maintainer.isUseTallView();

        styleButton.setOnClick(this::toggleViewStyle);
        styleButton.setTooltipProvider(this::buildStyleButtonTooltip);
        registerWidget(styleButton, -STYLE_BUTTON_SIZE - 2, SEARCH_Y);
    }

    @Override
    public void initGui() {
        if (useTallView) {
            int availableHeight = this.height - TALL_MARGIN * 2;
            int headerHeight = TALL_SLICE_START_Y;
            int footerHeight = TALL_GUI_BASE_HEIGHT - TALL_SLICE_END_Y;
            int contentHeight = availableHeight - headerHeight - footerHeight;
            tallVisibleRows = Math.max(3, contentHeight / TALL_SLICE_HEIGHT) + 1;
            this.ySize = headerHeight + ((tallVisibleRows - 1) * TALL_SLICE_HEIGHT) + footerHeight;
            this.xSize = TALL_GUI_WIDTH;
            tallScrollbarHeight = tallVisibleRows * TALL_SLICE_HEIGHT - 2;
        } else {
            this.ySize = GUI_HEIGHT;
            this.xSize = GUI_WIDTH;
        }

        super.initGui();
    }

    @Override
    protected void afterWidgetGuiInit() {
        searchField = new GuiTextField(0, fontRenderer, guiLeft + SEARCH_X, guiTop + SEARCH_Y, SEARCH_WIDTH, SEARCH_HEIGHT);
        searchField.setMaxStringLength(50);
        searchField.setEnableBackgroundDrawing(true);
        searchField.setTextColor(0xFFFFFF);

        styleButton.setAe2TextureIcon(useTallView ? STYLE_ICON_TALL : STYLE_ICON_SMALL);

        entryViewport.setTallLayout(tallVisibleRows, tallScrollbarHeight);
        entryViewport.updateScrollLimits(useTallView);
    }

    private List<String> buildStyleButtonTooltip() {
        return Arrays.asList(
            I18n.format("gui.ae2powertools.maintainer.style.title"),
            "§7" + I18n.format("gui.ae2powertools.maintainer.style." + (useTallView ? "tall" : "small")),
            "§7" + I18n.format("gui.ae2powertools.maintainer.style.click_toggle")
        );
    }

    private void toggleViewStyle() {
        // Toggling the layout also resets the list scroll so the player does not land in a visually
        // confusing half-way position when the number of visible rows changes.
        useTallView = !useTallView;
        PowerToolsClientConfig.maintainer.setUseTallView(useTallView);
        entryViewport.resetScroll();
        initGui();
    }

    @Override
    protected void updateWidgetGuiScreen() {
        searchField.updateCursorCounter();
        entryViewport.updateScrollLimits(useTallView);
    }

    @Override
    protected void drawWidgetGuiTooltips(int mouseX, int mouseY) {
        if (isManagedModalOpen()) return;

        renderHoveredToolTip(mouseX, mouseY);
        entryViewport.drawTooltips(useTallView, guiLeft, guiTop, ySize, false, mouseX, mouseY);
    }

    @Override
    protected void drawWidgetGuiBackgroundContents(float partialTicks, int mouseX, int mouseY) {
        boolean modalOpen = isManagedModalOpen();

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        if (useTallView) {
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
        } else {
            mc.getTextureManager().bindTexture(BACKGROUND);
            drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize);
        }

        searchField.drawTextBox();

        entryViewport.draw(
            useTallView,
            guiLeft,
            guiTop,
            xSize,
            ySize,
            searchField.getText(),
            modalOpen,
            modalOpen,
            mouseX,
            mouseY);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fontRenderer.drawString(I18n.format("gui.ae2powertools.maintainer.title"), 8, 6, 0x000000);
    }

    @Override
    protected boolean handleWidgetGuiKeyTyped(char typedChar, int keyCode) {
        return searchField.textboxKeyTyped(typedChar, keyCode);
    }

    @Override
    protected void afterWidgetGuiMouseClicked(int mouseX, int mouseY, int mouseButton) {
        searchField.mouseClicked(mouseX, mouseY, mouseButton);

        if (entryViewport.beginScrollbarDrag(useTallView, guiLeft, guiTop, mouseX, mouseY)) return;

        int clickedEntry = entryViewport.getEntryAtPosition(
            useTallView,
            guiLeft,
            guiTop,
            xSize,
            searchField.getText(),
            mouseX,
            mouseY);
        if (clickedEntry < 0) return;

        if (mouseButton == 0) {
            MaintainerEntry entry = container.getMaintainer().getEntry(clickedEntry);
            if (entry != null) entryEditorOverlay.open(clickedEntry, entry);
            return;
        }

        if (mouseButton == 1) {
            MaintainerEntry entry = container.getMaintainer().getEntry(clickedEntry);
            if (entry != null && entry.hasRecipe()) {
                sendEntryUpdate(
                    clickedEntry,
                    entry,
                    entry.getTargetQuantity(),
                    entry.getBatchSize(),
                    entry.getFrequencySeconds(),
                    !entry.isEnabled());
            }
        }
    }

    @Override
    protected void afterWidgetGuiMouseReleased(int mouseX, int mouseY, int state) {
        entryViewport.mouseReleased();
    }

    @Override
    protected boolean handleWidgetGuiMouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        return entryViewport.mouseClickMove(useTallView, guiTop, mouseY);
    }

    @Override
    protected void handleWidgetGuiMouseWheel(int mouseX, int mouseY, int scroll) {
        int hoveredEntry = entryViewport.getEntryAtPosition(
            useTallView,
            guiLeft,
            guiTop,
            xSize,
            searchField.getText(),
            mouseX,
            mouseY);

        if (hoveredEntry >= 0) {
            MaintainerEntry entry = container.getMaintainer().getEntry(hoveredEntry);
            if (entry != null && entry.hasRecipe()) {
                boolean shiftDown = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
                boolean ctrlDown = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);

                if (shiftDown) {
                    // Shift + scroll adjusts the target quantity of the entry
                    long newQuantity = entry.getTargetQuantity();
                    if (scroll > 0) {
                        newQuantity = newQuantity <= Long.MAX_VALUE / 2 ? newQuantity * 2 : Long.MAX_VALUE;
                    } else {
                        newQuantity = Math.max(1, newQuantity / 2);
                    }

                    sendEntryUpdate(
                        hoveredEntry,
                        entry,
                        newQuantity,
                        entry.getBatchSize(),
                        entry.getFrequencySeconds(),
                        entry.isEnabled());
                    return;
                }

                if (ctrlDown) {
                    // Ctrl + scroll adjusts the frequency of the entry
                    int newFrequency = entry.getFrequencySeconds();
                    if (scroll > 0) {
                        newFrequency = newFrequency <= Integer.MAX_VALUE / 2 ? newFrequency * 2 : Integer.MAX_VALUE;
                    } else {
                        newFrequency = Math.max(1, newFrequency / 2);
                    }

                    sendEntryUpdate(
                        hoveredEntry,
                        entry,
                        entry.getTargetQuantity(),
                        entry.getBatchSize(),
                        newFrequency,
                        entry.isEnabled());
                    return;
                }
            }
        }

        entryViewport.handleScroll(scroll);
    }

    private void selectRecipe(int entryIndex, IAEItemStack item) {
        PowerToolsNetwork.INSTANCE.sendToServer(new PacketSelectRecipe(
            container.getMaintainer().getPos(),
            entryIndex,
            item));
    }

    private void sendModalEntryUpdate(int entryIndex, IAEItemStack targetItem, long targetQty,
            long batchSize, int frequency, boolean enabled) {
        PowerToolsNetwork.INSTANCE.sendToServer(new PacketUpdateMaintainerEntry(
            container.getMaintainer().getPos(),
            entryIndex,
            targetItem,
            targetQty,
            batchSize,
            frequency,
            enabled));
    }

    private void sendEntryUpdate(int entryIndex, MaintainerEntry entry, long targetQty,
            long batchSize, int frequency, boolean enabled) {
        sendModalEntryUpdate(entryIndex, entry.getTargetItem(), targetQty, batchSize, frequency, enabled);
    }

    public List<Rectangle> getJEIExclusionArea() {
        return Collections.singletonList(styleButton.getBounds());
    }

}