package com.ae2powertools.features.scanner.data;

import java.util.Objects;

import net.minecraft.util.math.BlockPos;


/**
 * Pattern-related issue tied to a crafting provider position.
 */
public class PatternIssue extends AbstractLocation {

    public enum Category {
        INVALID_CRAFTING_RECIPE("invalid"),
        CONFLICTING_OUTPUTS("conflicting_outputs"),
        NESTED_INPUT_OUTPUT("nested_input_output");

        private final String translationSuffix;

        Category(String translationSuffix) {
            this.translationSuffix = translationSuffix;
        }

        public String getTitleKey() {
            return "gui.ae2powertools.scanner.pattern." + translationSuffix + ".title";
        }

        public String getTooltipKey() {
            return "gui.ae2powertools.scanner.pattern." + translationSuffix + ".tooltip";
        }
    }

    private final Category category;
    private final String dimensionName;
    private final String description;
    private final String summary;

    public PatternIssue(Category category, BlockPos pos, int dimension, String dimensionName,
            String description, String summary) {
        super(pos, dimension);

        this.category = category;
        this.dimensionName = dimensionName;
        this.description = description;
        this.summary = summary;
    }

    public Category getCategory() {
        return category;
    }

    public BlockPos getPos() {
        return pos;
    }

    @Override
    public ScannerIssueKey getIssueKey() {
        return new ScannerIssueKey(ScannerTabId.PATTERNS,
            category.name() + ':' + dimension + ':' + pos.getX() + ':' + pos.getY() + ':' + pos.getZ()
                + ':' + description + ':' + summary);
    }

    @Override
    public int getDimension() {
        return dimension;
    }

    public String getDimensionName() {
        return dimensionName;
    }

    public String getDescription() {
        return description;
    }

    public String getSummary() {
        return summary;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof PatternIssue)) return false;

        PatternIssue other = (PatternIssue) obj;
        return dimension == other.dimension
            && category == other.category
            && Objects.equals(pos, other.pos)
            && Objects.equals(description, other.description)
            && Objects.equals(summary, other.summary);
    }

    @Override
    public int hashCode() {
        return Objects.hash(category, pos, dimension, description, summary);
    }
}