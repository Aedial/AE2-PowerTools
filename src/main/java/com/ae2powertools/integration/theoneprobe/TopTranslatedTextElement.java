package com.ae2powertools.integration.theoneprobe;

import io.netty.buffer.ByteBuf;

import mcjty.theoneprobe.api.IElement;
import mcjty.theoneprobe.api.ITheOneProbe;
import mcjty.theoneprobe.network.NetworkTools;


public class TopTranslatedTextElement implements IElement {

    private static int elementId = -1;

    private final String translationKey;
    private final String[] arguments;

    public TopTranslatedTextElement(String translationKey, String... arguments) {
        this.translationKey = translationKey != null ? translationKey : "";
        this.arguments = arguments != null ? arguments.clone() : new String[0];
    }

    public TopTranslatedTextElement(ByteBuf buf) {
        this.translationKey = NetworkTools.readStringUTF8(buf);

        int argumentCount = buf.readInt();
        this.arguments = new String[argumentCount];
        for (int index = 0; index < argumentCount; index++) {
            this.arguments[index] = NetworkTools.readStringUTF8(buf);
        }
    }

    public static void register(ITheOneProbe probe) {
        elementId = probe.registerElementFactory(TopTranslatedTextElement::new);
    }

    @Override
    public void render(int x, int y) {
        TopTranslatedTextRenderer.render(this.translationKey, this.arguments, x, y);
    }

    @Override
    public int getWidth() {
        return TopTranslatedTextRenderer.getWidth(this.translationKey, this.arguments);
    }

    @Override
    public int getHeight() {
        return 10;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        NetworkTools.writeStringUTF8(buf, this.translationKey);
        buf.writeInt(this.arguments.length);
        for (String argument : this.arguments) {
            NetworkTools.writeStringUTF8(buf, argument != null ? argument : "");
        }
    }

    @Override
    public int getID() {
        return elementId;
    }
}