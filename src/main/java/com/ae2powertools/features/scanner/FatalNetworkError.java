package com.ae2powertools.features.scanner;

import java.util.Objects;

import net.minecraft.util.math.BlockPos;


/**
 * Fatal network issue tied to a specific block position that the player can inspect.
 */
public class FatalNetworkError {

    public enum Category {
        DUPLICATE_STORAGE_TARGET("duplicate_storage_target"),
        SAME_NETWORK_INTERFACE_LINK("same_network_interface_link");

        private final String translationSuffix;

        Category(String translationSuffix) {
            this.translationSuffix = translationSuffix;
        }

        public String getTitleKey() {
            return "gui.ae2powertools.scanner.fatal." + translationSuffix + ".title";
        }

        public String getTooltipKey() {
            return "gui.ae2powertools.scanner.fatal." + translationSuffix + ".tooltip";
        }

        public String getEntryKey() {
            return "gui.ae2powertools.scanner.fatal." + translationSuffix + ".entry";
        }
    }

    private final Category category;
    private final BlockPos pos;
    private final int dimension;
    private final String dimensionName;
    private final String description;
    private final BlockPos sourcePos;

    public FatalNetworkError(Category category, BlockPos pos, int dimension, String dimensionName,
            String description, BlockPos sourcePos) {
        this.category = category;
        this.pos = pos;
        this.dimension = dimension;
        this.dimensionName = dimensionName;
        this.description = description;
        this.sourcePos = sourcePos;
    }

    public Category getCategory() {
        return category;
    }

    public BlockPos getPos() {
        return pos;
    }

    public int getDimension() {
        return dimension;
    }

    public String getDimensionName() {
        return dimensionName;
    }

    public String getDescription() {
        return description;
    }

    public BlockPos getSourcePos() {
        return sourcePos;
    }

    public double getDistanceFrom(BlockPos from) {
        double dx = pos.getX() - from.getX();
        double dy = pos.getY() - from.getY();
        double dz = pos.getZ() - from.getZ();

        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof FatalNetworkError)) return false;

        FatalNetworkError other = (FatalNetworkError) obj;
        return dimension == other.dimension
            && category == other.category
            && Objects.equals(pos, other.pos)
            && Objects.equals(sourcePos, other.sourcePos)
            && Objects.equals(description, other.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(category, pos, dimension, description, sourcePos);
    }
}