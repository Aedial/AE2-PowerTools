package com.ae2powertools.features.scanner.gui.tab;

import java.util.Comparator;

import net.minecraft.client.resources.I18n;

import com.ae2powertools.features.scanner.data.ScannerTabId;
import com.ae2powertools.features.scanner.data.client.ChunkLocationClient;
import com.ae2powertools.features.scanner.gui.IssueOverlayPosition;
import com.ae2powertools.features.scanner.gui.ScannerDisplayText;
import com.ae2powertools.features.scanner.gui.ScannerGroupKey;
import com.ae2powertools.features.scanner.gui.ScannerRowGroup;
import com.ae2powertools.features.scanner.gui.ScannerSortContext;
import com.ae2powertools.features.scanner.gui.ScannerSortMode;
import com.ae2powertools.features.scanner.gui.ScannerTabDescriptor;
import com.ae2powertools.features.scanner.gui.ScannerViewContext;


/**
 * Scanner tab used to list and draw unloaded chunks.
 */
public final class UnloadedChunkScannerTab extends AbstractScannerTab<ChunkLocationClient> {

    public UnloadedChunkScannerTab() {
        super(new ScannerTabDescriptor(ScannerTabId.UNLOADED_CHUNKS, ScannerTabDescriptor.IconType.TEXT, "▦",
            "gui.ae2powertools.scanner.tab_chunks", 0xFFFFAA00, 0xFFAA00, 1.0f, 0.67f, 0.0f));
    }

    @Override
    public Comparator<ChunkLocationClient> createComparator(ScannerSortContext context) {
        return (left, right) -> {
            int dimensionCompare = compareDimension(left.dimension, right.dimension, context);
            if (dimensionCompare != 0) return dimensionCompare;

            if (context.getSortMode() == ScannerSortMode.NAME) {
                int xCompare = Integer.compare(left.chunkX, right.chunkX);
                if (xCompare != 0) return xCompare;

                int zCompare = Integer.compare(left.chunkZ, right.chunkZ);
                if (zCompare != 0) return zCompare;
            }

            return Double.compare(distanceFromAnchor(left, context), distanceFromAnchor(right, context));
        };
    }

    @Override
    protected ScannerRowGroup getGroup(ChunkLocationClient entry) {
        String title = I18n.format("gui.ae2powertools.scanner.dimension_format", entry.dimensionName, entry.dimension);
        return new ScannerRowGroup(new ScannerGroupKey(getDescriptor().getId(), "dimension:" + entry.dimension), title, null);
    }

    @Override
    protected String getRowText(ChunkLocationClient entry, ScannerViewContext viewContext) {
        return I18n.format("gui.ae2powertools.scanner.chunk_entry", entry.chunkX, entry.chunkZ)
            + ScannerDisplayText.currentDistanceSuffix(entry, viewContext);
    }

    @Override
    protected String getHudText(ChunkLocationClient entry, ScannerViewContext viewContext) {
        return I18n.format("gui.ae2powertools.scanner.chunk_entry", entry.chunkX, entry.chunkZ) + ": "
            + ScannerDisplayText.overlayDistance(entry.getDistanceFrom(viewContext.getPlayerPosition()));
    }

    @Override
    protected IssueOverlayPosition getIssueOverlay(ChunkLocationClient entry) {
        return IssueOverlayPosition.chunk(entry, getDescriptor());
    }
}
