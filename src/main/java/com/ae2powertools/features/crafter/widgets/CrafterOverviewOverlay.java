package com.ae2powertools.features.crafter.widgets;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.function.IntToDoubleFunction;
import java.util.function.IntToLongFunction;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.client.config.GuiUtils;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.storage.data.IAEItemStack;

import com.ae2powertools.Tags;
import com.ae2powertools.features.crafter.CrafterState;
import com.ae2powertools.features.crafter.TileAutoCrafter;
import com.ae2powertools.widgets.AbstractModalGui;
import com.ae2powertools.widgets.BeveledButton;
import com.ae2powertools.widgets.QueuedItemRenderer;
import com.ae2powertools.widgets.WidgetContext;


/**
 * Feature-local overview overlay for the AutoCrafter GUI.
 */
@SideOnly(Side.CLIENT)
public class CrafterOverviewOverlay extends AbstractModalGui {

    private static final ResourceLocation OVERVIEW_TEXTURE = new ResourceLocation(
        Tags.MODID, "textures/guis/crafter_overview.png");

    private static final int OVERVIEW_MODAL_WIDTH = 176;
    private static final int OVERVIEW_MODAL_HEIGHT = 248;
    private static final int OVERVIEW_ROW_X = 7;
    private static final int OVERVIEW_ROW_Y = 25;
    private static final int OVERVIEW_ROW_WIDTH = 162;
    private static final int OVERVIEW_ROW_HEIGHT = 18;
    private static final int PAGE_BTN_SIZE = 12;
    private static final int OVERVIEW_BTN_X = 5;
    private static final int OVERVIEW_BTN_Y = 5;

    private final WidgetContext context;
    private final IntPredicate hasDisplayData;
    private final IntFunction<CrafterState> stateProvider;
    private final IntFunction<IAEItemStack> outputProvider;
    private final IntFunction<List<ITextComponent>> errorDetailsProvider;
    private final IntToDoubleFunction occupancyProvider;
    private final IntToDoubleFunction errorRateProvider;
    private final IntToLongFunction metricsTotalProvider;
    private final IntFunction<String> overviewInfoProvider;
    private final Function<CrafterState, String> stateTextProvider;
    private final IntConsumer pageSelectionHandler;
    private final IntConsumer toggleEntryHandler;
    private final BeveledButton closeButton = new BeveledButton(0, 0, PAGE_BTN_SIZE, PAGE_BTN_SIZE, ">");

    private int overviewLeft;
    private int overviewTop;
    private int hoveredRow = -1;

    public CrafterOverviewOverlay(WidgetContext context,
            IntPredicate hasDisplayData,
            IntFunction<CrafterState> stateProvider,
            IntFunction<IAEItemStack> outputProvider,
            IntFunction<List<ITextComponent>> errorDetailsProvider,
            IntToDoubleFunction occupancyProvider,
            IntToDoubleFunction errorRateProvider,
            IntToLongFunction metricsTotalProvider,
            IntFunction<String> overviewInfoProvider,
            Function<CrafterState, String> stateTextProvider,
            IntConsumer pageSelectionHandler,
            IntConsumer toggleEntryHandler) {
        super(OVERVIEW_MODAL_WIDTH, OVERVIEW_MODAL_HEIGHT);

        this.context = context;
        this.hasDisplayData = hasDisplayData;
        this.stateProvider = stateProvider;
        this.outputProvider = outputProvider;
        this.errorDetailsProvider = errorDetailsProvider;
        this.occupancyProvider = occupancyProvider;
        this.errorRateProvider = errorRateProvider;
        this.metricsTotalProvider = metricsTotalProvider;
        this.overviewInfoProvider = overviewInfoProvider;
        this.stateTextProvider = stateTextProvider;
        this.pageSelectionHandler = pageSelectionHandler;
        this.toggleEntryHandler = toggleEntryHandler;

        closeButton.setOnClick(this::close);
    }

    public void initGui(int guiLeft, int guiTop) {
        overviewLeft = guiLeft;
        overviewTop = guiTop;
        closeButton.setPosition(overviewLeft + OVERVIEW_BTN_X, overviewTop + OVERVIEW_BTN_Y);
        super.setPosition(overviewLeft, overviewTop);
    }

    @Override
    public void close() {
        super.close();
        hoveredRow = -1;
    }

    public void draw(int mouseX, int mouseY, float partialTicks) {
        if (!isOpen()) return;

        // Fully reset GL state before drawing modal.
        // Item rendering from recipe view leaves various GL states enabled that cause
        // texture bleeding (for example the pattern slot texture leaking into later rows).
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

        // Draw modal background after the reset so the overview texture starts from a clean state.
        context.getWidgetMinecraft().getTextureManager().bindTexture(OVERVIEW_TEXTURE);
        drawTexturedModalRect(overviewLeft, overviewTop, 0, 0, OVERVIEW_MODAL_WIDTH, OVERVIEW_MODAL_HEIGHT);
        GlStateManager.enableAlpha();

        closeButton.draw(context, mouseX, mouseY);
        context.getWidgetFontRenderer().drawString(
            I18n.format("gui.ae2powertools.crafter.overview"),
            overviewLeft + OVERVIEW_BTN_X + PAGE_BTN_SIZE + 4,
            overviewTop + 7,
            0x404040);

        drawEntries(mouseX, mouseY);
        GlStateManager.enableDepth();
    }

    public void drawTooltip(int mouseX, int mouseY) {
        if (!isOpen() || hoveredRow < 0 || hoveredRow >= TileAutoCrafter.ENTRY_COUNT) return;

        int entryIndex = hoveredRow;
        CrafterState state = stateProvider.apply(entryIndex);
        List<ITextComponent> errorDetails = errorDetailsProvider.apply(entryIndex);

        int rowX = overviewLeft + OVERVIEW_ROW_X;
        int metricsX = rowX + OVERVIEW_ROW_WIDTH - 60;
        boolean hoveringMetrics = mouseX >= metricsX;

        IAEItemStack output = outputProvider.apply(entryIndex);
        List<String> tooltip = new ArrayList<>();

        if (!hasDisplayData.test(entryIndex)) {
            if (state == CrafterState.NO_PATTERN) {
                tooltip.add(I18n.format("gui.ae2powertools.crafter.empty_slot", entryIndex + 1));
            } else {
                tooltip.add(stateTextProvider.apply(state));
                if (!errorDetails.isEmpty()) {
                    tooltip.add("");
                    tooltip.add(TextFormatting.RED + I18n.format("gui.ae2powertools.crafter.issues") + ":");
                    for (ITextComponent detail : errorDetails) {
                        tooltip.add(TextFormatting.GRAY + "  - " + detail.getFormattedText());
                    }
                }
            }

            tooltip.add("");
            tooltip.add(TextFormatting.DARK_GRAY + I18n.format("gui.ae2powertools.crafter.click_to_view"));
            GuiUtils.drawHoveringText(tooltip, mouseX, mouseY, context.getWidgetWidth(), context.getWidgetHeight(), -1, context.getWidgetFontRenderer());
            return;
        }

        if (output == null) return;

        if (hoveringMetrics && metricsTotalProvider.applyAsLong(entryIndex) > 0) {
            addMetricsTooltip(tooltip, entryIndex);
        } else {
            tooltip.add(output.createItemStack().getDisplayName());
            tooltip.add("");
            tooltip.add(TextFormatting.GRAY + I18n.format("gui.ae2powertools.crafter.state")
                + ": " + TextFormatting.RESET + stateTextProvider.apply(state));

            if (!errorDetails.isEmpty()) {
                tooltip.add("");
                tooltip.add(TextFormatting.RED + I18n.format("gui.ae2powertools.crafter.issues") + ":");
                // Resolve each component in the player's locale at render time.
                for (ITextComponent detail : errorDetails) {
                    tooltip.add(TextFormatting.GRAY + "  - " + detail.getFormattedText());
                }
            }

            if (metricsTotalProvider.applyAsLong(entryIndex) > 0) {
                tooltip.add("");
                addMetricsTooltip(tooltip, entryIndex);
            }

            tooltip.add("");
            tooltip.add(TextFormatting.AQUA + I18n.format("gui.ae2powertools.crafter.click_to_view"));
            tooltip.add(TextFormatting.AQUA + I18n.format("gui.ae2powertools.crafter.right_click_toggle"));
        }

        GuiUtils.drawHoveringText(tooltip, mouseX, mouseY, context.getWidgetWidth(), context.getWidgetHeight(), -1, context.getWidgetFontRenderer());
    }

    public boolean keyTyped(char typedChar, int keyCode) {
        if (!isOpen()) return false;
        if (super.keyTyped(keyCode)) return true;

        return true;
    }

    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (!isOpen()) return false;
        if (mouseButton == 0 && closeButton.mouseClicked(mouseX, mouseY, mouseButton)) return true;
        if (super.mouseClicked(mouseX, mouseY)) return true;

        if (hoveredRow < 0) return true;

        if (mouseButton == 0) {
            pageSelectionHandler.accept(hoveredRow);
            close();
        } else if (mouseButton == 1) {
            toggleEntryHandler.accept(hoveredRow);
        }

        return true;
    }

    public boolean handleMouseWheel(int wheelDelta) {
        // In overview mode, scrolling does nothing so the underlying recipe page does not move.
        return isOpen();
    }

    /**
     * Returns whether a GUI-relative rectangle is covered by the overview modal.
     * Covered elements are skipped instead of fighting the modal's item rendering.
     */
    public boolean coversRelativeRegion(int x, int y, int width, int height) {
        return x < OVERVIEW_MODAL_WIDTH && x + width > 0
            && y < OVERVIEW_MODAL_HEIGHT && y + height > 0;
    }

    private void drawEntries(int mouseX, int mouseY) {
        QueuedItemRenderer itemQueue = new QueuedItemRenderer();
        hoveredRow = -1;
        for (int index = 0; index < TileAutoCrafter.ENTRY_COUNT; index++) {
            int rowX = overviewLeft + OVERVIEW_ROW_X;
            int rowY = overviewTop + OVERVIEW_ROW_Y + index * OVERVIEW_ROW_HEIGHT;
            if (mouseX >= rowX && mouseX < rowX + OVERVIEW_ROW_WIDTH && mouseY >= rowY && mouseY < rowY + OVERVIEW_ROW_HEIGHT) {
                hoveredRow = index;
            }

            CrafterState state = stateProvider.apply(index);
            int backgroundColor = state.getBackgroundColor();
            if ((backgroundColor & 0xFF000000) != 0) {
                drawRect(rowX, rowY, rowX + OVERVIEW_ROW_WIDTH, rowY + OVERVIEW_ROW_HEIGHT, backgroundColor);
            }

            if (hoveredRow == index) {
                drawRect(rowX, rowY, rowX + OVERVIEW_ROW_WIDTH, rowY + OVERVIEW_ROW_HEIGHT, 0x40FFFFFF);
            }

            if (hasDisplayData.test(index)) {
                IAEItemStack outputItem = outputProvider.apply(index);
                if (outputItem != null) {
                    ItemStack stack = outputItem.createItemStack();
                    itemQueue.queue(widgetContext -> {
                        GlStateManager.enableRescaleNormal();
                        widgetContext.getWidgetItemRenderer().renderItemAndEffectIntoGUI(stack, rowX + 1, rowY + 1);
                        widgetContext.getWidgetItemRenderer().renderItemOverlayIntoGUI(
                            widgetContext.getWidgetFontRenderer(),
                            stack,
                            rowX + 1,
                            rowY + 1,
                            null);
                        GlStateManager.disableRescaleNormal();
                    });
                }
            }

            context.getWidgetFontRenderer().drawString(
                overviewInfoProvider.apply(index),
                rowX + 20,
                rowY + 5,
                state.getTextColor());

            if (hasDisplayData.test(index) && metricsTotalProvider.applyAsLong(index) > 0) {
                String metrics = I18n.format(
                    "gui.ae2powertools.crafter.metrics_format",
                    String.format("%.0f", occupancyProvider.applyAsDouble(index)),
                    String.format("%.0f", errorRateProvider.applyAsDouble(index)));
                int metricsWidth = context.getWidgetFontRenderer().getStringWidth(metrics);
                // Use contrasting colors: white with shadow for colored backgrounds, gray for idle.
                int metricsColor = state == CrafterState.IDLE ? 0x707070 : 0xFFFFFF;
                int metricsX = rowX + OVERVIEW_ROW_WIDTH - metricsWidth - 2;
                if (state == CrafterState.IDLE) {
                    context.getWidgetFontRenderer().drawString(metrics, metricsX, rowY + 5, metricsColor);
                } else {
                    // Use shadow for better contrast on colored backgrounds.
                    context.getWidgetFontRenderer().drawStringWithShadow(metrics, metricsX, rowY + 5, metricsColor);
                }
            }
        }

        // Flush all row icons together so the overview owns one clean item-render pass instead of
        // toggling lighting and depth once per row or letting recipe-view state leak between entries.
        itemQueue.flush(context);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void addMetricsTooltip(List<String> tooltip, int entryIndex) {
        String occupancy = String.format("%.1f%%", occupancyProvider.applyAsDouble(entryIndex));
        tooltip.add(TextFormatting.GREEN + I18n.format("gui.ae2powertools.crafter.occupancy", occupancy));
        tooltip.add(TextFormatting.GRAY + I18n.format("gui.ae2powertools.crafter.occupancy_desc"));
        tooltip.add("");

        TextFormatting errorColor = TextFormatting.GREEN;
        double errorRate = errorRateProvider.applyAsDouble(entryIndex);
        if (errorRate > 10) {
            errorColor = TextFormatting.RED;
        } else if (errorRate > 0) {
            errorColor = TextFormatting.YELLOW;
        }

        String errorRateText = String.format("%.1f%%", errorRate);
        tooltip.add(errorColor + I18n.format("gui.ae2powertools.crafter.error_rate", errorRateText));
        tooltip.add(TextFormatting.GRAY + I18n.format("gui.ae2powertools.crafter.error_rate_desc"));
    }
}