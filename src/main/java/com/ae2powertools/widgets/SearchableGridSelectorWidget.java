package com.ae2powertools.widgets;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;


/**
 * Shared searchable 9x9 selector modal used by multiple GUIs.
 */
@SideOnly(Side.CLIENT)
public class SearchableGridSelectorWidget<T> extends AbstractModalGui {

    @FunctionalInterface
    public interface SearchTextProvider<T> {

        String getSearchText(T item);
    }

    @FunctionalInterface
    public interface EntryRenderer<T> {

        void render(WidgetContext context, T item, int x, int y);
    }

    @FunctionalInterface
    public interface TooltipRenderer<T> {

        void renderTooltip(T item, int mouseX, int mouseY);
    }

    private static final int WIDTH = 195;
    private static final int HEIGHT = 186;
    private static final int GRID_X = 8;
    private static final int GRID_Y = 17;
    private static final int GRID_COLS = 9;
    private static final int GRID_ROWS = 9;
    private static final int SLOT_SIZE = 18;
    private static final int SEARCH_X = 80;
    private static final int SEARCH_Y = 4;
    private static final int SEARCH_W = 90;
    private static final int SEARCH_H = 12;
    private static final int SCROLL_X = 175;
    private static final int SCROLL_Y = 18;
    private static final int SCROLL_TRACK_H = 162;
    private static final int SCROLL_THUMB_W = 12;
    private static final int SCROLL_THUMB_H = 15;

    private final WidgetContext context;
    private final String titleKey;
    private final SearchTextProvider<T> searchTextProvider;
    private final EntryRenderer<T> entryRenderer;
    private final TooltipRenderer<T> tooltipRenderer;
    private final Consumer<T> selectionHandler;
    private final List<T> items = new ArrayList<>();
    private final List<T> filteredItems = new ArrayList<>();

    private boolean draggingScrollbar;
    private int scrollbarDragOffset;
    private int left;
    private int top;
    private int hoveredSlot = -1;
    private int scrollOffset;
    private String searchText = "";
    private GuiTextField searchField;

    public SearchableGridSelectorWidget(WidgetContext context, String titleKey,
            SearchTextProvider<T> searchTextProvider, EntryRenderer<T> entryRenderer,
            TooltipRenderer<T> tooltipRenderer, Consumer<T> selectionHandler) {
        super(WIDTH, HEIGHT);

        this.context = context;
        this.titleKey = titleKey;
        this.searchTextProvider = searchTextProvider;
        this.entryRenderer = entryRenderer;
        this.tooltipRenderer = tooltipRenderer;
        this.selectionHandler = selectionHandler;
    }

    public void initGui() {
        left = (context.getWidgetWidth() - WIDTH) / 2;
        top = (context.getWidgetHeight() - HEIGHT) / 2;
        super.setPosition(left, top);

        if (!isOpen()) return;

        initSearchField();
    }

    public void open(List<T> newItems) {
        super.open();
        draggingScrollbar = false;
        scrollbarDragOffset = 0;
        hoveredSlot = -1;
        scrollOffset = 0;
        setItems(newItems);
        initSearchField();
    }

    @Override
    public void close() {
        if (searchField != null) {
            searchText = searchField.getText();
            searchField.setFocused(false);
        }

        super.close();
        draggingScrollbar = false;
        scrollbarDragOffset = 0;
        hoveredSlot = -1;
    }

    public void setItems(List<T> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        applyFilter();
    }

    public void setSearchText(String searchText) {
        this.searchText = searchText == null ? "" : searchText;
        if (searchField != null) searchField.setText(this.searchText);
        applyFilter();
    }

    public String getSearchText() {
        return searchField != null ? searchField.getText() : searchText;
    }

    public void updateScreen() {
        if (!isOpen() || searchField == null) return;

        searchField.updateCursorCounter();
    }

    public void draw(int mouseX, int mouseY, float partialTicks) {
        if (!isOpen()) return;

        // Reset GL state before drawing the selector. The surrounding GUI can leave item
        // lighting/depth on, which would tint the modal texture and let prior icons bleed through.
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        // Solid textured background shared by the feature-local selectors.
        context.getWidgetMinecraft().getTextureManager().bindTexture(WidgetTextures.SELECTOR_BACKGROUND);
        drawTexturedModalRect(left, top, 0, 0, WIDTH, HEIGHT);

        context.getWidgetFontRenderer().drawString(
            I18n.format(titleKey),
            left + 8,
            top + 6,
            0x000000);

        if (searchField != null) searchField.drawTextBox();

        // Reset state again because the text field renderer may have left things in an odd state.
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        hoveredSlot = -1;

        for (int row = 0; row < GRID_ROWS; row++) {
            for (int col = 0; col < GRID_COLS; col++) {
                int displaySlot = row * GRID_COLS + col;
                int itemIndex = (scrollOffset + row) * GRID_COLS + col;
                int slotX = left + GRID_X + col * SLOT_SIZE;
                int slotY = top + GRID_Y + row * SLOT_SIZE;

                boolean hovered = mouseX >= slotX && mouseX < slotX + SLOT_SIZE
                    && mouseY >= slotY && mouseY < slotY + SLOT_SIZE;
                if (hovered) {
                    hoveredSlot = displaySlot;
                    drawRect(slotX + 1, slotY + 1, slotX + SLOT_SIZE - 1, slotY + SLOT_SIZE - 1, 0x80FFFFFF);
                }

                if (itemIndex < 0 || itemIndex >= filteredItems.size()) continue;

                entryRenderer.render(context, filteredItems.get(itemIndex), slotX + 1, slotY + 1);

                // Restore the neutral color after each entry render so the remaining slots and
                // scrollbar start from known state.
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            }
        }

        drawScrollbar();

        // Final state restore so anything drawn after the modal (for example tooltips) starts clean.
        GlStateManager.enableDepth();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public void drawTooltip(int mouseX, int mouseY) {
        if (!isOpen()) return;

        T hoveredItem = getHoveredItem();
        if (hoveredItem == null) return;

        tooltipRenderer.renderTooltip(hoveredItem, mouseX, mouseY);
    }

    public boolean keyTyped(char typedChar, int keyCode) {
        if (!isOpen()) return false;

        if (super.keyTyped(keyCode)) return true;

        if (searchField != null && searchField.textboxKeyTyped(typedChar, keyCode)) {
            searchText = searchField.getText();
            applyFilter();
            return true;
        }

        return true;
    }

    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (!isOpen()) return false;

        if (super.mouseClicked(mouseX, mouseY)) return true;

        if (searchField != null) {
            boolean overSearchField = isMouseOverSearchField(mouseX, mouseY);
            if (overSearchField && mouseButton == 1) {
                searchField.setText("");
                searchField.setFocused(true);
                searchText = "";
                applyFilter();
                return true;
            }

            searchField.mouseClicked(mouseX, mouseY, mouseButton);
            if (overSearchField) return true;
        }

        if (mouseButton == 0 && isMouseOverScrollbar(mouseX, mouseY)) {
            draggingScrollbar = true;
            scrollbarDragOffset = isMouseOverScrollbarThumb(mouseX, mouseY)
                ? mouseY - getScrollbarThumbY()
                : SCROLL_THUMB_H / 2;
            updateScrollFromMouse(mouseY);
            return true;
        }

        if (mouseButton != 0) return true;

        T hoveredItem = getHoveredItem();
        if (hoveredItem == null) return true;

        selectionHandler.accept(hoveredItem);
        close();
        return true;
    }

    public void mouseReleased(int mouseX, int mouseY, int state) {
        draggingScrollbar = false;
        scrollbarDragOffset = 0;
    }

    public boolean mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (!isOpen() || !draggingScrollbar || clickedMouseButton != 0) return false;

        updateScrollFromMouse(mouseY);
        return true;
    }

    public boolean handleMouseWheel(int wheelDelta) {
        if (!isOpen() || wheelDelta == 0) return false;

        scrollOffset -= Integer.signum(wheelDelta);
        clampScroll();
        return true;
    }

    private void initSearchField() {
        searchField = new GuiTextField(
            100,
            context.getWidgetFontRenderer(),
            left + SEARCH_X,
            top + SEARCH_Y,
            SEARCH_W,
            SEARCH_H);
        searchField.setMaxStringLength(50);
        searchField.setEnableBackgroundDrawing(true);
        searchField.setTextColor(0xFFFFFF);
        searchField.setText(searchText);
        searchField.setFocused(true);
    }

    private void applyFilter() {
        filteredItems.clear();
        String search = getSearchText().toLowerCase().trim();

        if (search.isEmpty()) {
            filteredItems.addAll(items);
        } else {
            for (T item : items) {
                if (searchTextProvider.getSearchText(item).toLowerCase().contains(search)) {
                    filteredItems.add(item);
                }
            }
        }

        clampScroll();
    }

    private void clampScroll() {
        int maxScroll = getMaxScroll();
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
    }

    private int getMaxScroll() {
        int totalRows = (filteredItems.size() + GRID_COLS - 1) / GRID_COLS;
        return Math.max(0, totalRows - GRID_ROWS);
    }

    private boolean isMouseOverSearchField(int mouseX, int mouseY) {
        int searchLeft = left + SEARCH_X;
        int searchTop = top + SEARCH_Y;

        return mouseX >= searchLeft && mouseX < searchLeft + SEARCH_W
            && mouseY >= searchTop && mouseY < searchTop + SEARCH_H;
    }

    private boolean isMouseOverScrollbar(int mouseX, int mouseY) {
        int scrollX = left + SCROLL_X;
        int scrollY = top + SCROLL_Y;

        return mouseX >= scrollX && mouseX < scrollX + SCROLL_THUMB_W
            && mouseY >= scrollY && mouseY < scrollY + SCROLL_TRACK_H;
    }

    private void updateScrollFromMouse(int mouseY) {
        int maxScroll = getMaxScroll();
        if (maxScroll <= 0) {
            scrollOffset = 0;
            return;
        }

        int scrollY = top + SCROLL_Y;
        int thumbRange = SCROLL_TRACK_H - SCROLL_THUMB_H;
        if (thumbRange <= 0) {
            scrollOffset = 0;
            return;
        }

        // Keep thumb dragging anchored to the point the user grabbed, while track clicks still jump.
        float ratio = (float) (mouseY - scrollY - scrollbarDragOffset) / thumbRange;
        scrollOffset = Math.round(ratio * maxScroll);
        clampScroll();
    }

    private void drawScrollbar() {
        WidgetDrawHelper.drawCreativeScrollbar(
            context.getWidgetMinecraft(),
            left + SCROLL_X,
            top + SCROLL_Y,
            SCROLL_TRACK_H,
            SCROLL_THUMB_W,
            SCROLL_THUMB_H,
            scrollOffset,
            getMaxScroll());
    }

    private boolean isMouseOverScrollbarThumb(int mouseX, int mouseY) {
        if (!isMouseOverScrollbar(mouseX, mouseY)) return false;

        int thumbTop = getScrollbarThumbY();
        return mouseY >= thumbTop && mouseY < thumbTop + SCROLL_THUMB_H;
    }

    private int getScrollbarThumbY() {
        int maxScroll = getMaxScroll();
        if (maxScroll <= 0) return top + SCROLL_Y;

        int thumbRange = SCROLL_TRACK_H - SCROLL_THUMB_H;
        return top + SCROLL_Y + thumbRange * scrollOffset / maxScroll;
    }

    private T getHoveredItem() {
        if (hoveredSlot < 0) return null;

        int row = hoveredSlot / GRID_COLS;
        int col = hoveredSlot % GRID_COLS;
        int itemIndex = (scrollOffset + row) * GRID_COLS + col;
        if (itemIndex < 0 || itemIndex >= filteredItems.size()) return null;

        return filteredItems.get(itemIndex);
    }
}