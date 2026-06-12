package com.ae2powertools.features.scanner;

import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;

import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;


/**
 * Shared scanner text helpers for deferring translatable descriptions until the client.
 */
public final class ScannerTextHelper {

    private ScannerTextHelper() {
    }

    /**
     * Serialize a text component for transport over scanner sync packets.
     */
    public static String serializeComponent(ITextComponent component) {
        return component == null ? "" : ITextComponent.Serializer.componentToJson(component);
    }

    /**
     * Deserialize a scanner text payload. Plain strings are accepted as a fallback.
     */
    public static ITextComponent deserializeComponent(String payload) {
        if (payload == null || payload.isEmpty()) {
            return new TextComponentString("");
        }

        try {
            return ITextComponent.Serializer.jsonToComponent(payload);
        } catch (Exception e) {
            return new TextComponentString(payload);
        }
    }

    /**
     * Resolve a scanner text payload into the formatted string used by the GUI and overlay.
     */
    public static String resolveForDisplay(String payload) {
        return deserializeComponent(payload).getFormattedText();
    }

    /**
     * Build a transport-safe node description payload.
     */
    public static String getNodeDescription(IGridNode node) {
        return serializeComponent(getNodeDescriptionComponent(node));
    }

    /**
     * Append a translated suffix without flattening it on the server.
     */
    public static String appendTranslatedSuffix(String payload, String translationKey) {
        TextComponentString result = new TextComponentString("");
        result.appendSibling(deserializeComponent(payload).createCopy());
        result.appendText(" ");
        result.appendSibling(new TextComponentTranslation(translationKey));
        return serializeComponent(result);
    }

    private static ITextComponent getNodeDescriptionComponent(IGridNode node) {
        if (node == null) {
            return new TextComponentTranslation("ae2powertools.common.unknown");
        }

        try {
            ItemStack representation = node.getGridBlock().getMachineRepresentation();

            if (!representation.isEmpty()) {
                return new TextComponentString(representation.getDisplayName());
            }
        } catch (Exception e) {
            // Fall through to class name fallback.
        }

        IGridHost host = node.getMachine();
        if (host == null) {
            return new TextComponentTranslation("ae2powertools.common.unknown");
        }

        String className = host.getClass().getSimpleName();

        if (className.startsWith("Tile")) {
            className = className.substring(4);
        } else if (className.startsWith("Part")) {
            className = className.substring(4);
        }

        return new TextComponentString(className);
    }
}