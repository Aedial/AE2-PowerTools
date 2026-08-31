package com.ae2powertools.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.ae2powertools.features.monitor.MonitoredResource;
import com.ae2powertools.features.monitor.client.MonitoredResourceRenderer;


/**
 * Shared manager for client HUD overlays.
 * Providers register once and return lightweight overlay specs each frame.
 */
@SideOnly(Side.CLIENT)
public final class HudOverlayManager {

    public static final HudOverlayManager INSTANCE = new HudOverlayManager();

    private final List<HudOverlayProvider> providers = new ArrayList<>();

    private HudOverlayManager() {}

    public static void register(HudOverlayProvider provider) {
        if (provider == null || INSTANCE.providers.contains(provider)) return;

        INSTANCE.providers.add(provider);
        INSTANCE.providers.sort(Comparator.comparingInt(HudOverlayProvider::getPriority));
    }


    /**
     * Render HUD overlays when no screen is open.
     * <p>
     * Minecraft renders the in-game overlay before the active GuiScreen, so
     * overlays that should appear on top of screens need a separate GUI-post pass.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onRenderHudOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen != null) return;
        if (mc.fontRenderer == null) return;

        renderOverlays(mc);
    }

    /**
     * Render screen-safe overlays after the current GUI has finished drawing.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDrawScreenPost(GuiScreenEvent.DrawScreenEvent.Post event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen == null) return;
        if (event.getGui() != mc.currentScreen) return;
        if (mc.fontRenderer == null) return;

        renderOverlays(mc);
    }

    private void renderOverlays(Minecraft mc) {
        for (HudOverlayProvider provider : this.providers) provider.onOverlayRendered(0);

        List<ActiveOverlay> topLeftBoxed = new ArrayList<>();
        List<ActiveOverlay> topLeftUnboxed = new ArrayList<>();
        List<ActiveOverlay> aboveXpBoxed = new ArrayList<>();
        List<ActiveOverlay> aboveXpUnboxed = new ArrayList<>();

        for (HudOverlayProvider provider : this.providers) {
            if (provider.hideWithDebugInfo() && mc.gameSettings.showDebugInfo) continue;
            if (!provider.getScreenPolicy().allows(mc)) continue;
            if (!provider.isActive(mc)) continue;

            List<HudOverlayLine> lines = provider.getLines(mc);
            if (lines.isEmpty()) continue;

            ActiveOverlay overlay = new ActiveOverlay(provider, provider.getAnchor(), provider.getStyle(), lines);
            bucketOverlay(overlay, topLeftBoxed, topLeftUnboxed, aboveXpBoxed, aboveXpUnboxed);
        }

        // Item and GUI renderers often leave depth, lighting, or tint state dirty.
        // Reset to a flat 2D overlay state before we draw screen-space panels.
        GlStateManager.pushMatrix();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        ScaledResolution resolution = new ScaledResolution(mc);
        int topLeftHeight = 0;
        for (ActiveOverlay overlay : topLeftBoxed) {
            int renderedHeight = renderTopLeftOverlay(mc, topLeftHeight, overlay.lines, true);
            topLeftHeight += renderedHeight;
            overlay.provider.onOverlayRendered(renderedHeight);
        }

        for (ActiveOverlay overlay : topLeftUnboxed) {
            int renderedHeight = renderTopLeftOverlay(mc, topLeftHeight, overlay.lines, false);
            topLeftHeight += renderedHeight;
            overlay.provider.onOverlayRendered(renderedHeight);
        }

        int aboveXpHeight = 0;
        for (ActiveOverlay overlay : aboveXpBoxed) {
            int renderedHeight = renderAboveXpOverlay(mc, resolution, aboveXpHeight, overlay.lines, true);
            aboveXpHeight += renderedHeight;
            overlay.provider.onOverlayRendered(renderedHeight);
        }

        for (ActiveOverlay overlay : aboveXpUnboxed) {
            int renderedHeight = renderAboveXpOverlay(mc, resolution, aboveXpHeight, overlay.lines, false);
            aboveXpHeight += renderedHeight;
            overlay.provider.onOverlayRendered(renderedHeight);
        }

        GlStateManager.disableBlend();
        GlStateManager.enableDepth();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
    }

    private void bucketOverlay(ActiveOverlay overlay, List<ActiveOverlay> topLeftBoxed,
            List<ActiveOverlay> topLeftUnboxed, List<ActiveOverlay> aboveXpBoxed,
            List<ActiveOverlay> aboveXpUnboxed) {
        if (overlay.anchor == OverlayAnchor.TOP_LEFT_STACK) {
            if (overlay.style == OverlayStyle.BOXED) {
                topLeftBoxed.add(overlay);
                return;
            }

            topLeftUnboxed.add(overlay);
            return;
        }

        if (overlay.style == OverlayStyle.BOXED) {
            aboveXpBoxed.add(overlay);
            return;
        }

        aboveXpUnboxed.add(overlay);
    }

    private int renderTopLeftOverlay(Minecraft mc, int stackedHeight, List<HudOverlayLine> overlayLines, boolean boxed) {
        OverlayLayout layout = measureOverlay(mc, overlayLines);
        PowerToolsClientConfig.RemoteMonitor config = PowerToolsClientConfig.remoteMonitor;

        int overlayX = config.getX();
        int overlayY = config.getY() + stackedHeight;

        if (boxed) drawOverlayBox(overlayX, overlayY, layout.getBoxWidth(), layout.getBoxHeight());

        int contentX = overlayX + (boxed ? layout.paddingInternal : 0);
        int contentY = overlayY + (boxed ? layout.paddingInternal : 0);
        renderOverlayLines(mc, overlayLines, layout, contentX, contentY, layout.contentWidth, false);

        return layout.getBoxHeight(boxed) + TrackedLocationRenderer.getExternalPadding();
    }

    private int renderAboveXpOverlay(Minecraft mc, ScaledResolution resolution, int stackedHeight,
            List<HudOverlayLine> overlayLines, boolean boxed) {
        OverlayLayout layout = measureOverlay(mc, overlayLines);

        int overlayX = (resolution.getScaledWidth() - layout.getBoxWidth(boxed)) / 2;
        int overlayY = resolution.getScaledHeight() - 45 - stackedHeight - layout.getBoxHeight(boxed);

        if (boxed) drawOverlayBox(overlayX, overlayY, layout.getBoxWidth(), layout.getBoxHeight());

        int contentX = overlayX + (boxed ? layout.paddingInternal : 0);
        int contentY = overlayY + (boxed ? layout.paddingInternal : 0);
        renderOverlayLines(mc, overlayLines, layout, contentX, contentY, layout.contentWidth, true);

        return layout.getBoxHeight(boxed) + TrackedLocationRenderer.getExternalPadding();
    }

    private OverlayLayout measureOverlay(Minecraft mc, List<HudOverlayLine> overlayLines) {
        // TODO: Move the Remote Monitor overlay config to a shared overlay config
        //       It does not belong to the Remote Monitor anymore, since we unified overlays
        PowerToolsClientConfig.RemoteMonitor config = PowerToolsClientConfig.remoteMonitor;

        List<LineMetrics> lineMetrics = new ArrayList<>();
        float scaledTextHeight = mc.fontRenderer.FONT_HEIGHT * config.getTextScale();
        int textHeight = (int) Math.ceil(scaledTextHeight);
        int contentWidth = 0;
        int contentHeight = 0;

        for (int i = 0; i < overlayLines.size(); i++) {
            HudOverlayLine line = overlayLines.get(i);
            int iconWidth = line.icon != null ? config.getIconSize() + config.getIconTextGap() : 0;
            int textWidth = (int) Math.ceil(mc.fontRenderer.getStringWidth(line.text) * config.getTextScale());
            int lineWidth = iconWidth + textWidth;
            int lineHeight = line.icon != null ? Math.max(textHeight, config.getIconSize()) : textHeight;

            lineMetrics.add(new LineMetrics(lineWidth, lineHeight));
            contentWidth = Math.max(contentWidth, lineWidth);
            contentHeight += lineHeight;
            if (i + 1 < overlayLines.size()) contentHeight += config.getLineSpacing();
        }

        return new OverlayLayout(
            config.getPaddingInternal(),
            config.getIconSize(),
            config.getIconTextGap(),
            config.getLineSpacing(),
            config.getTextScale(),
            scaledTextHeight,
            contentWidth,
            contentHeight,
            lineMetrics);
    }

    private void renderOverlayLines(Minecraft mc, List<HudOverlayLine> overlayLines, OverlayLayout layout,
            int contentX, int contentY, int availableWidth, boolean centerLines) {
        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        int lineY = contentY;
        for (int i = 0; i < overlayLines.size(); i++) {
            HudOverlayLine line = overlayLines.get(i);
            LineMetrics metrics = layout.lineMetrics.get(i);
            int lineX = centerLines ? contentX + (availableWidth - metrics.width) / 2 : contentX;
            int textX = lineX;

            if (line.icon != null) {
                int iconY = lineY + (metrics.height - layout.iconSize) / 2;
                MonitoredResourceRenderer.renderIcon(line.icon, lineX, iconY, layout.iconSize);
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
                textX += layout.iconSize + layout.iconTextGap;
            }

            float textY = lineY + (metrics.height - layout.scaledTextHeight) / 2.0F;
            drawScaledText(mc, line.text, textX, textY, line.color, layout.textScale);
            lineY += metrics.height + layout.lineSpacing;
        }

        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private void drawOverlayBox(int x, int y, int width, int height) {
        Gui.drawRect(x - 1, y - 1, x + width + 1, y + height + 1, 0xFF404040);
        Gui.drawRect(x, y, x + width, y + height, 0xC0101010);
    }

    private void drawScaledText(Minecraft mc, String text, int x, float y, int color, float scale) {
        if (scale == 1.0F) {
            mc.fontRenderer.drawStringWithShadow(text, x, y, color);
            return;
        }

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0.0F);
        GlStateManager.scale(scale, scale, 1.0F);
        mc.fontRenderer.drawStringWithShadow(text, 0.0F, 0.0F, color);
        GlStateManager.popMatrix();
    }

    public interface HudOverlayProvider {

        /**
         * Return true if the overlay should be rendered in the current context.
         * This usually involves checking the player's held item, the current screen,
         * a config, and/or any other relevant state.
         */
        boolean isActive(Minecraft mc);

        /**
         * Return the position where the overlay should be rendered.
         * Overlays with the same anchor will be stacked vertically in the order they were registered.
         */
        OverlayAnchor getAnchor();

        /**
         * Controls the visual style of the overlay, like whether it is boxed.
         */
        OverlayStyle getStyle();

        /**
         * Returns the lines to be rendered in the overlay.
         * Each line can have an optional icon and a color.
         * The lines will be rendered in the order they are returned.
         */
        List<HudOverlayLine> getLines(Minecraft mc);

        /**
         * Returns which screens the overlay is allowed to render on.
         * By default, overlays are only rendered on the HUD (no GUI open).
         * This ensures invasive overlays do not interfere with other screens,
         * but small unobtrusive overlays can be rendered on top of other screens if desired.
         */
        default ScreenPolicy getScreenPolicy() {
            return ScreenPolicy.HUD_ONLY;
        }

        /**
         * Whether the overlay should be hidden when the debug info (F3) is shown.
         * This follows the same rationale as getScreenPolicy, since the debug info
         * is a fairly dense "GUI", which our overlays will interfere with.
         */
        default boolean hideWithDebugInfo() {
            return true;
        }

        default int getPriority() {
            return 0;
        }

        default void onOverlayRendered(int renderedHeight) {}
    }

    public enum OverlayAnchor {
        TOP_LEFT_STACK,
        ABOVE_XP_BAR_STACK
    }

    public enum OverlayStyle {
        BOXED,
        UNBOXED
    }

    public enum ScreenPolicy {
        ALWAYS {
            @Override
            public boolean allows(Minecraft mc) {
                return true;
            }
        },
        HUD_ONLY {
            @Override
            public boolean allows(Minecraft mc) {
                // TODO: Allow rendering on top of more screens.
                //       It is hard to know which screens are safe to render on.
                return mc.currentScreen == null || mc.currentScreen instanceof GuiChat;
            }
        };

        public abstract boolean allows(Minecraft mc);
    }

    public static final class HudOverlayLine {

        private final String text;
        private final int color;
        @Nullable
        private final MonitoredResource icon;

        private HudOverlayLine(String text, int color, @Nullable MonitoredResource icon) {
            this.text = text;
            this.color = color;
            this.icon = icon;
        }

        public static HudOverlayLine text(String text, int color) {
            return new HudOverlayLine(text, color, null);
        }

        public static HudOverlayLine iconText(MonitoredResource icon, String text, int color) {
            return new HudOverlayLine(text, color, icon);
        }

        public static List<HudOverlayLine> textLines(List<String> lines, List<Integer> colors) {
            List<HudOverlayLine> overlayLines = new ArrayList<>();
            int lineCount = Math.min(lines.size(), colors.size());

            for (int i = 0; i < lineCount; i++) {
                overlayLines.add(text(lines.get(i), colors.get(i)));
            }

            return overlayLines;
        }
    }

    private static final class OverlayLayout {

        private final int paddingInternal;
        private final int iconSize;
        private final int iconTextGap;
        private final int lineSpacing;
        private final float textScale;
        private final float scaledTextHeight;
        private final int contentWidth;
        private final int contentHeight;
        private final List<LineMetrics> lineMetrics;

        private OverlayLayout(int paddingInternal, int iconSize, int iconTextGap, int lineSpacing, float textScale,
                float scaledTextHeight, int contentWidth, int contentHeight, List<LineMetrics> lineMetrics) {
            this.paddingInternal = paddingInternal;
            this.iconSize = iconSize;
            this.iconTextGap = iconTextGap;
            this.lineSpacing = lineSpacing;
            this.textScale = textScale;
            this.scaledTextHeight = scaledTextHeight;
            this.contentWidth = contentWidth;
            this.contentHeight = contentHeight;
            this.lineMetrics = lineMetrics;
        }

        private int getBoxWidth() {
            return getBoxWidth(true);
        }

        private int getBoxWidth(boolean boxed) {
            return this.contentWidth + (boxed ? this.paddingInternal * 2 : 0);
        }

        private int getBoxHeight() {
            return getBoxHeight(true);
        }

        private int getBoxHeight(boolean boxed) {
            return this.contentHeight + (boxed ? this.paddingInternal * 2 : 0);
        }
    }

    private static final class LineMetrics {

        private final int width;
        private final int height;

        private LineMetrics(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }

    private static final class ActiveOverlay {

        private final HudOverlayProvider provider;
        private final OverlayAnchor anchor;
        private final OverlayStyle style;
        private final List<HudOverlayLine> lines;

        private ActiveOverlay(HudOverlayProvider provider, OverlayAnchor anchor, OverlayStyle style,
                List<HudOverlayLine> lines) {
            this.provider = provider;
            this.anchor = anchor;
            this.style = style;
            this.lines = new ArrayList<>(lines);
        }
    }
}