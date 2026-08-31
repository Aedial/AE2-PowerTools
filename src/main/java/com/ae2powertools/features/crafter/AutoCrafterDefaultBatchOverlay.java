package com.ae2powertools.features.crafter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.ae2powertools.client.HudOverlayManager;


/**
 * Warns when an AutoCrafter is still using the default batch size.
 */
@SideOnly(Side.CLIENT)
public class AutoCrafterDefaultBatchOverlay implements HudOverlayManager.HudOverlayProvider {

    private static final int WARNING_COLOR = 0xFFAA33;

    @Override
    public boolean isActive(Minecraft mc) {
        if (mc.player == null || mc.world == null) return false;

        return resolveVisibleBatch(mc) == TileAutoCrafter.DEFAULT_BATCH_SIZE;
    }

    @Override
    public HudOverlayManager.OverlayAnchor getAnchor() {
        return HudOverlayManager.OverlayAnchor.ABOVE_XP_BAR_STACK;
    }

    @Override
    public HudOverlayManager.OverlayStyle getStyle() {
        return HudOverlayManager.OverlayStyle.BOXED;
    }

    @Override
    public List<HudOverlayManager.HudOverlayLine> getLines(Minecraft mc) {
        long batch = resolveVisibleBatch(mc);
        if (batch != TileAutoCrafter.DEFAULT_BATCH_SIZE) return Collections.emptyList();

        String title = I18n.format("gui.ae2powertools.crafter.overlay.default_batch_warning", batch);
        String description = I18n.format("gui.ae2powertools.crafter.overlay.default_batch_warning_desc");

        ScaledResolution resolution = new ScaledResolution(mc);
        int maxWidth = Math.max(120, resolution.getScaledWidth() - 20);
        List<String> wrappedLines = new ArrayList<>();
        wrappedLines.addAll(mc.fontRenderer.listFormattedStringToWidth(title, maxWidth));
        wrappedLines.addAll(mc.fontRenderer.listFormattedStringToWidth(description, maxWidth));

        List<Integer> colors = new ArrayList<>();
        for (int i = 0; i < wrappedLines.size(); i++) colors.add(WARNING_COLOR);

        return HudOverlayManager.HudOverlayLine.textLines(wrappedLines, colors);
    }

    @Override
    public HudOverlayManager.ScreenPolicy getScreenPolicy() {
        return HudOverlayManager.ScreenPolicy.ALWAYS;
    }

    private long resolveVisibleBatch(Minecraft mc) {
        if (mc.currentScreen instanceof GuiAutoCrafter) {
            return ((GuiAutoCrafter) mc.currentScreen).getContainer().syncBatchSize;
        }

        return -1L;
    }
}