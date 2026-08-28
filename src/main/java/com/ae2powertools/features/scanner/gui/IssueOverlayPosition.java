package com.ae2powertools.features.scanner.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.util.math.BlockPos;

import com.ae2powertools.features.scanner.data.ScannerIssue;
import com.ae2powertools.features.scanner.data.client.ChunkLocationClient;


/**
 * Data used to draw selected scanner results in the world.
 */
public final class IssueOverlayPosition {

    private final BlockPos position;
    private final int dimension;
    private final boolean blockOutline;
    private final boolean alwaysShowArrow;
    private final boolean yAgnosticArrow;
    private final int arrowColor;
    private final float red;
    private final float green;
    private final float blue;
    private final List<String> floatingLines;

    private IssueOverlayPosition(BlockPos position, int dimension, boolean blockOutline,
            boolean alwaysShowArrow, boolean yAgnosticArrow, int arrowColor,
            float red, float green, float blue, List<String> floatingLines) {
        this.position = position;
        this.dimension = dimension;
        this.blockOutline = blockOutline;
        this.alwaysShowArrow = alwaysShowArrow;
        this.yAgnosticArrow = yAgnosticArrow;
        this.arrowColor = arrowColor;
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.floatingLines = Collections.unmodifiableList(new ArrayList<>(floatingLines));
    }

    public static IssueOverlayPosition block(ScannerIssue issue, ScannerTabDescriptor descriptor) {
        return new IssueOverlayPosition(issue.getAnchorPos(), issue.getDimension(), true, false, false,
            descriptor.getOverlayColor(), descriptor.getRed(), descriptor.getGreen(), descriptor.getBlue(),
            Collections.emptyList());
    }

    public static IssueOverlayPosition chunk(ChunkLocationClient issue, ScannerTabDescriptor descriptor) {
        return new IssueOverlayPosition(issue.getCenterPos(), issue.getDimension(), false, true, true,
            descriptor.getOverlayColor(), descriptor.getRed(), descriptor.getGreen(), descriptor.getBlue(),
            Collections.emptyList());
    }

    public IssueOverlayPosition withFloatingLines(List<String> lines) {
        return new IssueOverlayPosition(position, dimension, blockOutline, alwaysShowArrow, yAgnosticArrow,
            arrowColor, red, green, blue, lines);
    }

    public BlockPos getPosition() {
        return position;
    }

    public int getDimension() {
        return dimension;
    }

    public boolean hasBlockOutline() {
        return blockOutline;
    }

    public boolean isAlwaysShowArrow() {
        return alwaysShowArrow;
    }

    public boolean isYAgnosticArrow() {
        return yAgnosticArrow;
    }

    public int getArrowColor() {
        return arrowColor;
    }

    public float getRed() {
        return red;
    }

    public float getGreen() {
        return green;
    }

    public float getBlue() {
        return blue;
    }

    public List<String> getFloatingLines() {
        return floatingLines;
    }

    public double getDistanceFrom(BlockPos from) {
        double dx = position.getX() - from.getX();
        double dy = position.getY() - from.getY();
        double dz = position.getZ() - from.getZ();

        if (yAgnosticArrow) return Math.sqrt(dx * dx + dz * dz);

        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
