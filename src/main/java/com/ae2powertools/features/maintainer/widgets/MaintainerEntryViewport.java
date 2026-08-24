package com.ae2powertools.features.maintainer.widgets;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.client.config.GuiUtils;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.util.ReadableNumberConverter;

import com.ae2powertools.features.maintainer.ContainerBetterLevelMaintainer;
import com.ae2powertools.features.maintainer.MaintainerEntry;
import com.ae2powertools.features.maintainer.MaintainerState;
import com.ae2powertools.features.maintainer.TileBetterLevelMaintainer;
import com.ae2powertools.widgets.QueuedItemRenderer;
import com.ae2powertools.widgets.WidgetContext;
import com.ae2powertools.widgets.WidgetDrawHelper;


/**
 * Feature-local viewport for the maintainer's scrollable entry list and status bar.
 * <p>
 * Small mode is the baseline layout. Tall mode tiles the texture to match the screen height,
 * without cutting entries in the middle. As the tall mode has more space, it also shows more
 * information about each entry.
 */
@SideOnly(Side.CLIENT)
public class MaintainerEntryViewport extends Gui {

    private static final class EntryLayout {

        private final boolean tall;
        private final int visibleRows;
        private final int entryStartY;
        private final int rowHeight;
        private final int scrollbarY;
        private final int scrollbarHeight;
        private final int statusY;

        private EntryLayout(boolean tall, int visibleRows, int entryStartY, int rowHeight,
                int scrollbarY, int scrollbarHeight, int statusY) {
            this.tall = tall;
            this.visibleRows = visibleRows;
            this.entryStartY = entryStartY;
            this.rowHeight = rowHeight;
            this.scrollbarY = scrollbarY;
            this.scrollbarHeight = scrollbarHeight;
            this.statusY = statusY;
        }
    }

    /**
     * One filtered visible entry with its screen-space geometry.
     * Drawing, tooltips, and hit testing all consume this same structure so the GUI stays consistent.
     */
    private static final class VisibleEntry {

        private final int entryIndex;
        private final MaintainerEntry entry;
        private final int x;
        private final int y;
        private final int width;
        private final int height;

        private VisibleEntry(int entryIndex, MaintainerEntry entry, int x, int y, int width, int height) {
            this.entryIndex = entryIndex;
            this.entry = entry;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    private static final int ENTRY_START_X = 9;
    private static final int ENTRY_START_Y = 18;
    private static final int ENTRY_WIDTH = 68;
    private static final int ENTRY_HEIGHT = 23;
    private static final int VISIBLE_ROWS = 6;
    private static final int COLUMNS = 3;

    private static final int SCROLLBAR_X = 218;
    private static final int SCROLLBAR_Y = 19;
    private static final int SCROLLBAR_WIDTH = 12;
    private static final int SCROLLBAR_HEIGHT = 136;
    private static final int SCROLLBAR_THUMB_HEIGHT = 15;

    private static final int STATUS_Y = 163;

    private static final int TALL_SLICE_START_Y = 19;
    private static final int TALL_SLICE_HEIGHT = 23;
    private static final int TALL_STATUS_OFFSET = 18;

    private final WidgetContext context;
    private final ContainerBetterLevelMaintainer container;

    private int scrollOffset;
    private int maxScroll;
    private boolean draggingScrollbar;
    private int hoveredEntryIndex = -1;
    private int cpuX;
    private int cpuTextWidth;
    private int recipeX;
    private int recipeTextWidth;
    private int tallVisibleRows = 6;
    private int tallScrollbarHeight;

    public MaintainerEntryViewport(WidgetContext context, ContainerBetterLevelMaintainer container) {
        this.context = context;
        this.container = container;
    }

    public void setTallLayout(int tallVisibleRows, int tallScrollbarHeight) {
        this.tallVisibleRows = tallVisibleRows;
        this.tallScrollbarHeight = tallScrollbarHeight;
    }

    public void resetScroll() {
        scrollOffset = 0;
    }

    public void updateScrollLimits(boolean useTallView) {
        TileBetterLevelMaintainer maintainer = container.getMaintainer();
        if (useTallView) {
            maxScroll = Math.max(0, maintainer.getOpenSlots() - tallVisibleRows);
        } else {
            int totalRows = (maintainer.getOpenSlots() + COLUMNS - 1) / COLUMNS;
            maxScroll = Math.max(0, totalRows - VISIBLE_ROWS);
        }

        scrollOffset = Math.min(scrollOffset, maxScroll);
    }

    public void draw(boolean useTallView, int guiLeft, int guiTop, int xSize, int ySize,
            String searchTerm, boolean modalOpen, boolean suppressItemRendering, int mouseX, int mouseY) {
        EntryLayout layout = createLayout(useTallView, guiTop, ySize);
        List<VisibleEntry> visibleEntries = collectVisibleEntries(layout, guiLeft, guiTop, xSize, searchTerm);

        drawEntries(layout, visibleEntries, modalOpen, suppressItemRendering, mouseX, mouseY);
        drawScrollbar(guiLeft, layout);
        drawStatusBar(guiLeft, xSize, layout.statusY);
    }

    public void drawTooltips(boolean useTallView, int guiLeft, int guiTop, int ySize,
            boolean modalOpen, int mouseX, int mouseY) {
        if (modalOpen) return;

        drawEntryTooltips(mouseX, mouseY);
        drawStatusBarTooltips(useTallView, guiTop, ySize, mouseX, mouseY);
    }

    public boolean beginScrollbarDrag(boolean useTallView, int guiLeft, int guiTop, int mouseX, int mouseY) {
        if (maxScroll <= 0) return false;

        EntryLayout layout = createLayout(useTallView, guiTop, 0);
        int scrollbarX = guiLeft + SCROLLBAR_X;
        if (mouseX < scrollbarX || mouseX >= scrollbarX + SCROLLBAR_WIDTH
                || mouseY < layout.scrollbarY || mouseY >= layout.scrollbarY + layout.scrollbarHeight) {
            return false;
        }

        draggingScrollbar = true;
        return true;
    }

    public void mouseReleased() {
        draggingScrollbar = false;
    }

    public boolean mouseClickMove(boolean useTallView, int guiTop, int mouseY) {
        if (!draggingScrollbar || maxScroll <= 0) return false;

        EntryLayout layout = createLayout(useTallView, guiTop, 0);
        float ratio = (float) (mouseY - layout.scrollbarY) / layout.scrollbarHeight;
        scrollOffset = Math.round(ratio * maxScroll);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
        return true;
    }

    public void handleScroll(int wheelDelta) {
        scrollOffset += (wheelDelta < 0) ? 1 : -1;
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
    }

    public int getEntryAtPosition(boolean useTallView, int guiLeft, int guiTop, int xSize,
            String searchTerm, int mouseX, int mouseY) {
        EntryLayout layout = createLayout(useTallView, guiTop, 0);
        List<VisibleEntry> visibleEntries = collectVisibleEntries(layout, guiLeft, guiTop, xSize, searchTerm);
        for (VisibleEntry visibleEntry : visibleEntries) {
            if (mouseX >= visibleEntry.x && mouseX < visibleEntry.x + visibleEntry.width
                    && mouseY >= visibleEntry.y && mouseY < visibleEntry.y + visibleEntry.height) {
                return visibleEntry.entryIndex;
            }
        }

        return -1;
    }

    private EntryLayout createLayout(boolean useTallView, int guiTop, int ySize) {
        if (useTallView) {
            return new EntryLayout(
                true,
                tallVisibleRows,
                TALL_SLICE_START_Y,
                TALL_SLICE_HEIGHT,
                guiTop + TALL_SLICE_START_Y,
                tallScrollbarHeight,
                guiTop + ySize - TALL_STATUS_OFFSET);
        }

        return new EntryLayout(
            false,
            VISIBLE_ROWS,
            ENTRY_START_Y,
            ENTRY_HEIGHT,
            guiTop + SCROLLBAR_Y,
            SCROLLBAR_HEIGHT,
            guiTop + STATUS_Y);
    }

    /**
     * Builds the filtered visible-entry list once per frame. Small and tall modes share the same
     * scroll and search walk; only the derived geometry differs.
     */
    private List<VisibleEntry> collectVisibleEntries(EntryLayout layout, int guiLeft, int guiTop,
            int xSize, String searchTerm) {
        TileBetterLevelMaintainer maintainer = container.getMaintainer();
        String loweredSearchTerm = searchTerm.toLowerCase();
        List<VisibleEntry> visibleEntries = new ArrayList<>();
        int displayIndex = 0;
        int startIndex = layout.tall ? scrollOffset : scrollOffset * COLUMNS;

        for (int entryIndex = startIndex; entryIndex < maintainer.getOpenSlots(); entryIndex++) {
            MaintainerEntry entry = maintainer.getEntry(entryIndex);
            if (entry == null) continue;

            if (!loweredSearchTerm.isEmpty() && entry.hasRecipe()) {
                ItemStack targetStack = entry.getTargetItemStack();
                if (targetStack == null || targetStack.isEmpty()) continue;

                String name = targetStack.getDisplayName().toLowerCase();
                if (!name.contains(loweredSearchTerm)) continue;
            }

            int row = layout.tall ? displayIndex : displayIndex / COLUMNS;
            if (row >= layout.visibleRows) break;

            int col = layout.tall ? 0 : displayIndex % COLUMNS;
            int x = guiLeft + ENTRY_START_X + col * ENTRY_WIDTH;
            int y = guiTop + layout.entryStartY + row * layout.rowHeight;
            int width = layout.tall ? xSize - ENTRY_START_X * 2 - SCROLLBAR_WIDTH - 4 : ENTRY_WIDTH;
            visibleEntries.add(new VisibleEntry(entryIndex, entry, x, y, width, layout.rowHeight));
            displayIndex++;
        }

        return visibleEntries;
    }

    private void drawEntries(EntryLayout layout, List<VisibleEntry> visibleEntries,
            boolean modalOpen, boolean suppressItemRendering, int mouseX, int mouseY) {
        QueuedItemRenderer itemQueue = new QueuedItemRenderer();
        hoveredEntryIndex = -1;
        for (VisibleEntry visibleEntry : visibleEntries) {
            boolean hovered = !modalOpen
                && mouseX >= visibleEntry.x && mouseX < visibleEntry.x + visibleEntry.width
                && mouseY >= visibleEntry.y && mouseY < visibleEntry.y + visibleEntry.height;
            if (hovered) hoveredEntryIndex = visibleEntry.entryIndex;

            drawEntryBackground(layout, visibleEntry, hovered);
            if (layout.tall) {
                drawTallEntryContent(visibleEntry, itemQueue, suppressItemRendering);
            } else {
                drawCompactEntryContent(visibleEntry, itemQueue, suppressItemRendering);
            }
        }

        // Flush all queued entry icons together so the viewport owns one predictable item-render pass
        // instead of bouncing lighting and depth state for every visible row.
        itemQueue.flush(context);
    }

    private void drawEntryBackground(EntryLayout layout, VisibleEntry visibleEntry, boolean hovered) {
        int topY = layout.tall ? visibleEntry.y : visibleEntry.y + 1;
        int bottomY = layout.tall ? visibleEntry.y + visibleEntry.height - 1 : visibleEntry.y + visibleEntry.height;

        int backgroundColor = visibleEntry.entry.getState().getBackgroundColor();
        if (backgroundColor != 0) {
            drawRect(visibleEntry.x, topY, visibleEntry.x + visibleEntry.width - 1, bottomY, backgroundColor);
        }

        if (hovered) {
            drawRect(visibleEntry.x, topY, visibleEntry.x + visibleEntry.width - 1, bottomY, 0x40FFFFFF);
        }
    }

    /**
     * Compact mode is the baseline terminal-like view: small icon on the left, quantity and
     * frequency aligned to the right edge of each cell.
     */
    private void drawCompactEntryContent(VisibleEntry visibleEntry, QueuedItemRenderer itemQueue,
            boolean suppressItemRendering) {
        if (!visibleEntry.entry.hasRecipe()) return;

        int slotX = visibleEntry.x + 1;
        int slotY = visibleEntry.y + 2;
        int slotSize = 12;
        drawRect(slotX, slotY, slotX + slotSize, slotY + slotSize, 0xFF373737);
        drawRect(slotX + 1, slotY + 1, slotX + slotSize, slotY + slotSize, 0xFFFFFFFF);
        drawRect(slotX + 1, slotY + 1, slotX + slotSize - 1, slotY + slotSize - 1, 0xFF8B8B8B);

        if (!suppressItemRendering) {
            ItemStack stack = visibleEntry.entry.getTargetItemStack();
            if (stack == null || stack.isEmpty()) return;

            itemQueue.queue(widgetContext -> {
                GlStateManager.pushMatrix();
                float scale = 0.75F;
                GlStateManager.scale(scale, scale, 1.0F);
                widgetContext.getWidgetItemRenderer().renderItemIntoGUI(
                    stack,
                    (int) ((visibleEntry.x + 1) / scale),
                    (int) ((visibleEntry.y + 2) / scale));
                GlStateManager.popMatrix();
            });
        }

        int maxWidth = ENTRY_WIDTH - 2;
        String quantityText = String.format(
            "%s/%s",
            ReadableNumberConverter.INSTANCE.toSlimReadableForm(visibleEntry.entry.getCurrentQuantity()),
            ReadableNumberConverter.INSTANCE.toSlimReadableForm(visibleEntry.entry.getTargetQuantity()));
        int quantityX = visibleEntry.x + maxWidth - context.getWidgetFontRenderer().getStringWidth(quantityText);
        context.getWidgetFontRenderer().drawString(
            quantityText,
            quantityX,
            visibleEntry.y + 4,
            visibleEntry.entry.isEnabled() ? 0x000000 : 0x808080);

        String frequencyText = visibleEntry.entry.formatFrequency();
        if (context.getWidgetFontRenderer().getStringWidth(frequencyText) > maxWidth) {
            frequencyText = context.getWidgetFontRenderer().trimStringToWidth(
                frequencyText,
                maxWidth - context.getWidgetFontRenderer().getStringWidth("...")) + "...";
        }
        int frequencyX = visibleEntry.x + maxWidth - context.getWidgetFontRenderer().getStringWidth(frequencyText);
        context.getWidgetFontRenderer().drawString(frequencyText, frequencyX, visibleEntry.y + 15, 0x000000);
    }

    /**
     * Tall mode extends the compact view by dedicating the full row width to one entry and adding
     * the translated short state label next to the icon.
     */
    private void drawTallEntryContent(VisibleEntry visibleEntry, QueuedItemRenderer itemQueue,
            boolean suppressItemRendering) {
        if (!visibleEntry.entry.hasRecipe()) {
            context.getWidgetFontRenderer().drawString(
                I18n.format("gui.ae2powertools.maintainer.tooltip.empty"),
                visibleEntry.x + 4,
                visibleEntry.y + 7,
                0x808080);
            return;
        }

        ItemStack stack = visibleEntry.entry.getTargetItemStack();
        if (stack == null || stack.isEmpty()) return;

        if (!suppressItemRendering) {
            itemQueue.queue(widgetContext -> widgetContext.getWidgetItemRenderer().renderItemAndEffectIntoGUI(
                stack,
                visibleEntry.x + 2,
                visibleEntry.y + 2));
        }

        String name = stack.getDisplayName();
        int nameMaxWidth = visibleEntry.width - 24 - 100;
        if (context.getWidgetFontRenderer().getStringWidth(name) > nameMaxWidth) {
            name = context.getWidgetFontRenderer().trimStringToWidth(name, nameMaxWidth - 6) + "...";
        }
        context.getWidgetFontRenderer().drawString(
            name,
            visibleEntry.x + 22,
            visibleEntry.y + 3,
            visibleEntry.entry.isEnabled() ? 0x000000 : 0x808080);

        MaintainerState state = visibleEntry.entry.getState();
        String stateText = I18n.format("gui.ae2powertools.maintainer.state.short." + state.name().toLowerCase());
        context.getWidgetFontRenderer().drawString(stateText, visibleEntry.x + 22, visibleEntry.y + 12, state.getTextColor());

        String quantityText = String.format(
            "%s / %s",
            ReadableNumberConverter.INSTANCE.toWideReadableForm(visibleEntry.entry.getCurrentQuantity()),
            ReadableNumberConverter.INSTANCE.toWideReadableForm(visibleEntry.entry.getTargetQuantity()));
        int quantityX = visibleEntry.x + visibleEntry.width - context.getWidgetFontRenderer().getStringWidth(quantityText) - 2;
        context.getWidgetFontRenderer().drawString(
            quantityText,
            quantityX,
            visibleEntry.y + 3,
            visibleEntry.entry.isEnabled() ? 0x000000 : 0x808080);

        String frequencyText = visibleEntry.entry.formatFrequency();
        int frequencyX = visibleEntry.x + visibleEntry.width - context.getWidgetFontRenderer().getStringWidth(frequencyText) - 2;
        context.getWidgetFontRenderer().drawString(frequencyText, frequencyX, visibleEntry.y + 12, 0x606060);
    }

    private void drawScrollbar(int guiLeft, EntryLayout layout) {
        WidgetDrawHelper.drawCreativeScrollbar(
            context.getWidgetMinecraft(),
            guiLeft + SCROLLBAR_X,
            layout.scrollbarY,
            layout.scrollbarHeight,
            SCROLLBAR_WIDTH,
            SCROLLBAR_THUMB_HEIGHT,
            scrollOffset,
            maxScroll);
    }

    private void drawStatusBar(int guiLeft, int xSize, int y) {
        int halfWidth = (xSize - 16) / 2;
        int leftX = guiLeft + 8;
        int rightX = leftX + halfWidth;

        int activeCpus = container.getActiveCpuCount();
        int totalCpus = container.getTotalCpuCount();
        String cpuText = String.format("§a%d§r / §8%d§r", activeCpus, totalCpus);
        cpuTextWidth = context.getWidgetFontRenderer().getStringWidth(String.format("%d / %d", activeCpus, totalCpus));
        cpuX = leftX + (halfWidth - cpuTextWidth) / 2;
        context.getWidgetFontRenderer().drawStringWithShadow(cpuText, cpuX, y, 0xFFFFFF);

        int running = container.getRunningRecipeCount();
        int total = container.getTotalRecipeCount();
        int failed = container.getFailedRecipeCount();
        int postError = container.getPostErrorRecipeCount();
        String recipeText = String.format("§a%d§r / §8%d§r / §c%d§r / §5%d§r", running, total, failed, postError);
        recipeTextWidth = context.getWidgetFontRenderer().getStringWidth(String.format("%d / %d / %d / %d", running, total, failed, postError));
        recipeX = rightX + (halfWidth - recipeTextWidth) / 2;
        context.getWidgetFontRenderer().drawStringWithShadow(recipeText, recipeX, y, 0xFFFFFF);
    }

    private void drawEntryTooltips(int mouseX, int mouseY) {
        if (hoveredEntryIndex < 0) return;

        MaintainerEntry entry = container.getMaintainer().getEntry(hoveredEntryIndex);
        if (entry == null) return;

        List<String> tooltip = new ArrayList<>();
        if (entry.hasRecipe() && entry.getTargetItemStack() != null && !entry.getTargetItemStack().isEmpty()) {
            tooltip.add(entry.getTargetItemStack().getDisplayName());

            tooltip.add("§a" + String.format("%s / %s", entry.getCurrentQuantity(), entry.getTargetQuantity()));
            tooltip.add("§7" + I18n.format("gui.ae2powertools.maintainer.tooltip.batch_size", entry.getBatchSize()));
            tooltip.add("§7" + I18n.format("gui.ae2powertools.maintainer.tooltip.frequency", entry.formatFrequency()));
            tooltip.add("");

            MaintainerState state = entry.getState();
            String stateKey = "gui.ae2powertools.maintainer.state." + state.name().toLowerCase();
            tooltip.add(state.getColorCode() + I18n.format(stateKey));

            // Show error message for error states. The component is built server-side as a
            // TextComponentTranslation, so we just resolve it here using the client's locale.
            if (state.isError() && entry.getErrorComponent() != null) {
                tooltip.add("§c" + entry.getErrorComponent().getFormattedText());
            }

            tooltip.add("");
            tooltip.add("§7" + I18n.format("gui.ae2powertools.maintainer.tooltip.click_edit"));
            tooltip.add("§7" + I18n.format("gui.ae2powertools.maintainer.tooltip.right_click_toggle"));
            tooltip.add("§7" + I18n.format("gui.ae2powertools.maintainer.tooltip.shift_scroll_quantity"));
            tooltip.add("§7" + I18n.format("gui.ae2powertools.maintainer.tooltip.ctrl_scroll_frequency"));
        } else {
            tooltip.add(I18n.format("gui.ae2powertools.maintainer.tooltip.empty"));
            tooltip.add("§7" + I18n.format("gui.ae2powertools.maintainer.tooltip.click_add"));
        }

        GuiUtils.drawHoveringText(
            tooltip,
            mouseX,
            mouseY,
            context.getWidgetWidth(),
            context.getWidgetHeight(),
            -1,
            context.getWidgetFontRenderer());
    }

    private void drawStatusBarTooltips(boolean useTallView, int guiTop, int ySize, int mouseX, int mouseY) {
        int statusY = useTallView ? guiTop + ySize - TALL_STATUS_OFFSET : guiTop + STATUS_Y;
        if (mouseY < statusY || mouseY > statusY + 12) return;

        List<String> tooltip = new ArrayList<>();
        if (mouseX >= cpuX && mouseX < cpuX + cpuTextWidth) {
            tooltip.add("§e" + I18n.format("gui.ae2powertools.maintainer.status.cpu_title"));
            tooltip.add("§a" + I18n.format("gui.ae2powertools.maintainer.status.cpu_active", container.getActiveCpuCount()));
            tooltip.add("§7" + I18n.format("gui.ae2powertools.maintainer.status.cpu_total", container.getTotalCpuCount()));
        }

        if (mouseX >= recipeX && mouseX < recipeX + recipeTextWidth) {
            tooltip.add("§e" + I18n.format("gui.ae2powertools.maintainer.status.recipe_title"));
            tooltip.add("§a" + I18n.format("gui.ae2powertools.maintainer.status.recipe_running", container.getRunningRecipeCount()));
            tooltip.add("§7" + I18n.format("gui.ae2powertools.maintainer.status.recipe_total", container.getTotalRecipeCount()));
            tooltip.add("§c" + I18n.format("gui.ae2powertools.maintainer.status.recipe_failed", container.getFailedRecipeCount()));
            tooltip.add("§5" + I18n.format("gui.ae2powertools.maintainer.status.recipe_post_error", container.getPostErrorRecipeCount()));
        }

        if (!tooltip.isEmpty()) {
            GuiUtils.drawHoveringText(
                tooltip,
                mouseX,
                mouseY,
                context.getWidgetWidth(),
                context.getWidgetHeight(),
                -1,
                context.getWidgetFontRenderer());
        }
    }
}