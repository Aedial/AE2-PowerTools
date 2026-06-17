package com.ae2powertools.features.monitor.display;

import java.util.Arrays;
import java.util.List;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.client.render.TesrRenderHelper;
import appeng.util.IWideReadableNumberConverter;
import appeng.util.ReadableNumberConverter;

import com.ae2powertools.client.DisplayBlockColor;
import com.ae2powertools.features.monitor.MonitoredResource;
import com.mekeng.github.common.me.data.IAEGasStack;

import mekanism.api.gas.Gas;
import mekanism.api.gas.GasStack;
import mekanism.client.render.MekanismRenderer;
import thaumcraft.api.aspects.Aspect;
import thaumicenergistics.api.EssentiaStack;
import thaumicenergistics.api.storage.IAEEssentiaStack;


/**
 * Shared display-rendering helpers for the block TESR and cable-part dynamic renderer.
 * The full-block variant only uses the face-content helpers: its corner overlay is still
 * baked into the block model and tinted via {@code DisplayBlockColor}.
 * <p>
 * Centralizes the bits that have to look identical between the part dynamic renderer and
 * the baked block model.
 */
@SideOnly(Side.CLIENT)
public final class DisplayRenderHelper {

    private static final String MEKENG_MODID = "mekeng";
    private static final String THAUMIC_ENERGISTICS_MODID = "thaumicenergistics";
    private static final float DISPLAY_ICON_SCALE = 1.0F;
    private static final float DISPLAY_AMOUNT_SPACING = 0.27F;
    private static final float DISPLAY_ICON_SIZE = DISPLAY_ICON_SCALE * 0.5F;
    private static final float DISPLAY_ICON_X = -DISPLAY_ICON_SIZE * 0.5F;
    private static final float DISPLAY_ICON_Y = -0.25F;
    private static final float DISPLAY_ICON_Z = 0.0001F;
    private static final IWideReadableNumberConverter NUMBER_CONVERTER = ReadableNumberConverter.INSTANCE;

    /**
     * Texture sprite name for the corner indicator overlay. Same texture used by the block
     * model's corner element, so the part's dynamic-rendered corners are pixel-for-pixel
     * identical to the block's baked corners.
     */
    private static final List<String> CENTER_SPRITE_NAMES = Arrays.asList(
        "ae2powertools:blocks/display_color_center",
        "ae2powertools:blocks/display_color_center_smaller",
        "ae2powertools:blocks/display_color_center_smallerer"
    );
    private static final List<String> CORNER_SPRITE_NAMES = Arrays.asList(
        "ae2powertools:blocks/display_color_corner",
        "ae2powertools:blocks/display_color_corner_smaller",
        "ae2powertools:blocks/display_color_corner_smallerer"
    );

    /**
     * Returns true if the player's eye is on the front side of a face anchored at
     * {@code pos + (0.5, 0.5, 0.5) + facing * 0.5}. Used to skip rendering the icon / text
     * when the viewer is behind the host, sparing the cost of the TESR when the block
     * cannot be seen (depth culling).
     */
    public static boolean isViewerInFront(BlockPos pos, EnumFacing facing, float partialTicks) {
        EntityPlayer player = Minecraft.getMinecraft().player;
        if (player == null) return true;

        Vec3d eye = player.getPositionEyes(partialTicks);
        double dx = eye.x - (pos.getX() + 0.5);
        double dy = eye.y - (pos.getY() + 0.5);
        double dz = eye.z - (pos.getZ() + 0.5);

        // dot > 0 means the eye is on the side the face is pointing toward (in front of it).
        // Small epsilon so being exactly in the face plane still renders (avoids flicker).
        double dot = dx * facing.getXOffset() + dy * facing.getYOffset() + dz * facing.getZOffset();
        return dot > -1.0e-4;
    }

    public static void drawScreenCenter(int packedLight, EnumFacing facing, int modelIndex) {
        if (modelIndex < 0 || modelIndex >= CENTER_SPRITE_NAMES.size()) modelIndex = 0;

        drawFaceOverlay(CENTER_SPRITE_NAMES.get(modelIndex), DisplayBlockColor.getCenterTint(), packedLight, facing, 0.00001F);
    }

    /**
     * Draws the corner indicator overlay as a textured quad on the current face, in AE2's
     * post-{@code rotateToFace} coordinate system. The face plane is XY, the face normal
     * points in -Z (toward the viewer), and the face spans roughly [-0.5, 0.5] in both axes.
     * <p>
     * The block display renders this same sprite as a cutout-tinted model layer, lit by the
     * world and multiplied by vanilla's face-diffuse factor. The part path has to reproduce
     * that manually so the same ARGB values do not read noticeably brighter on a cable part
     * than they do on the block variant.
     *
     * @param argb packed 0xAARRGGBB color from {@link DisplayLogic#getCornerColor()}
     * @param packedLight host block light from {@code World#getCombinedLight}
     * @param modelIndex index of the model variant to use
     */
    public static void drawCornerIndicators(int argb, int packedLight, EnumFacing facing, int modelIndex) {
        if (modelIndex < 0 || modelIndex >= CORNER_SPRITE_NAMES.size()) modelIndex = 0;

        drawFaceOverlay(CORNER_SPRITE_NAMES.get(modelIndex), argb, packedLight, facing, 0.00002F);
    }

    /**
     * Register the sprites used by the dynamic TESR rendering, for all model variants.
     */
    public static void registerSprites(TextureMap textureMap) {
        registerSprites(textureMap, CENTER_SPRITE_NAMES);
        registerSprites(textureMap, CORNER_SPRITE_NAMES);
    }

    private static void registerSprites(TextureMap textureMap, List<String> spriteNames) {
        for (String spriteName : spriteNames) {
            textureMap.registerSprite(new ResourceLocation(spriteName));
        }
    }

    private static void drawFaceOverlay(String spriteName, int argb, int packedLight, EnumFacing facing, float z) {
        TextureAtlasSprite sprite = Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(spriteName);
        if (sprite == null) return;

        boolean lightingEnabled = GL11.glIsEnabled(GL11.GL_LIGHTING);
        boolean alphaEnabled = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        boolean blendEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean cullEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);

        float shade = getFaceDiffuseShade(facing);
        float a = ((argb >> 24) & 0xFF) / 255.0F;
        float r = ((argb >> 16) & 0xFF) / 255.0F * shade;
        float g = ((argb >>  8) & 0xFF) / 255.0F * shade;
        float b = ( argb        & 0xFF) / 255.0F * shade;

        // AE2's rotateToFace flips local axes via scale(-1,-1,-1) (for the NORTH-default
        // case): after that, local +Z points TOWARD the viewer. We pull the quad slightly
        // forward so it sits just in front of the host's front face and doesn't z-fight.
        float half = 0.5F;
        float uMin = sprite.getMinU();
        float uMax = sprite.getMaxU();
        float vMin = sprite.getMinV();
        float vMax = sprite.getMaxV();

        int lightmapX = packedLight & 0xFFFF;
        int lightmapY = (packedLight >>> 16) & 0xFFFF;

        GlStateManager.disableLighting();
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
        GlStateManager.disableBlend();
        GlStateManager.disableCull();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_LMAP_COLOR);

        buf.pos(-half, -half, z).tex(uMin, vMax).lightmap(lightmapX, lightmapY).color(r, g, b, a).endVertex();
        buf.pos( half, -half, z).tex(uMax, vMax).lightmap(lightmapX, lightmapY).color(r, g, b, a).endVertex();
        buf.pos( half,  half, z).tex(uMax, vMin).lightmap(lightmapX, lightmapY).color(r, g, b, a).endVertex();
        buf.pos(-half,  half, z).tex(uMin, vMin).lightmap(lightmapX, lightmapY).color(r, g, b, a).endVertex();

        tess.draw();

        if (lightingEnabled) GlStateManager.enableLighting();
        else GlStateManager.disableLighting();

        if (alphaEnabled) GlStateManager.enableAlpha();
        else GlStateManager.disableAlpha();

        if (blendEnabled) GlStateManager.enableBlend();
        else GlStateManager.disableBlend();

        if (cullEnabled) GlStateManager.enableCull();
        else GlStateManager.disableCull();

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static float getFaceDiffuseShade(EnumFacing facing) {
        switch (facing) {
            case DOWN:
                return 0.5F;
            case UP:
                return 1.0F;
            case NORTH:
            case SOUTH:
                return 0.8F;
            case WEST:
            case EAST:
                return 0.6F;
            default:
                return 1.0F;
        }
    }

    /**
     * Renders the display snapshot on the already-rotated face. Items and fluids delegate to
     * AE2's item helper so the icon and amount match the stock monitor look exactly. Fluids,
     * gas, and essentia use the same positioning and amount formatting, but draw their own
     * full sprites because the AE2 system doesn't handle them well enough.
     */
    public static void renderResourceWithAmount(MonitoredResource content, long quantity) {
        if (content == null) return;

        IAEStack<?> stack = content.getStack();
        if (stack == null) return;

        if (stack instanceof IAEItemStack) {
            renderItemWithAmount((IAEItemStack) stack, quantity);
            return;
        }

        if (stack instanceof IAEFluidStack) {
            renderFluidWithAmount((IAEFluidStack) stack, quantity);
            return;
        }

        if (Loader.isModLoaded(MEKENG_MODID) && renderGasWithAmount(stack, quantity)) return;

        if (Loader.isModLoaded(THAUMIC_ENERGISTICS_MODID)) renderEssentiaWithAmount(stack, quantity);
    }

    private static void renderItemWithAmount(IAEItemStack stack, long quantity) {
        ItemStack renderStack = stack.asItemStackRepresentation();
        if (renderStack.isEmpty()) return;

        // GUI item rendering expects the same standard item lights used by JEI and AE2's GUIs.
        // Without them block models render flat and noticeably darker on the monitor face.
        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.translate(0.0F, 0.1F, 0.0F);
        TesrRenderHelper.renderItem2d(renderStack, DISPLAY_ICON_SCALE);
        GlStateManager.translate(0.0F, -0.1F, 0.0F);
        RenderHelper.disableStandardItemLighting();

        GlStateManager.enableAlpha();
        GlStateManager.disableLighting();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        renderAmountText(quantity, "");
    }

    private static void renderFluidWithAmount(IAEFluidStack stack, long quantity) {
        FluidStack fluidStack = stack.getFluidStack();
        if (fluidStack == null) return;

        Fluid fluid = fluidStack.getFluid();
        if (fluid == null || fluid.getStill(fluidStack) == null) return;

        TextureAtlasSprite sprite = Minecraft.getMinecraft().getTextureMapBlocks()
            .getAtlasSprite(fluid.getStill(fluidStack).toString());
        if (sprite == null) return;

        int color = fluid.getColor(fluidStack);
        float red = (color >> 16 & 0xFF) / 255.0F;
        float green = (color >> 8 & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;

        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);

        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.disableLighting();
        GlStateManager.color(red, green, blue, 1.0F);

        Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        drawSpriteQuad(sprite, DISPLAY_ICON_X, DISPLAY_ICON_Y, DISPLAY_ICON_SIZE, DISPLAY_ICON_SIZE, DISPLAY_ICON_Z);

        GlStateManager.enableAlpha();
        GlStateManager.disableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        renderAmountText(quantity / 1000L, "B");
    }

    @Optional.Method(modid = MEKENG_MODID)
    private static boolean renderGasWithAmount(IAEStack<?> stack, long quantity) {
        if (!(stack instanceof IAEGasStack)) return false;

        GasStack gasStack = ((IAEGasStack) stack).getGasStack();
        if (gasStack == null) return false;

        Gas gas = gasStack.getGas();
        if (gas == null) return false;

        TextureAtlasSprite sprite = gas.getSprite();
        if (sprite == null) return false;

        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);

        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.disableLighting();

        Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        MekanismRenderer.color(gas);
        drawSpriteQuad(sprite, DISPLAY_ICON_X, DISPLAY_ICON_Y, DISPLAY_ICON_SIZE, DISPLAY_ICON_SIZE, DISPLAY_ICON_Z);
        MekanismRenderer.resetColor();

        GlStateManager.enableAlpha();
        GlStateManager.disableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        renderAmountText(quantity / 1000L, "B");
        return true;
    }

    @Optional.Method(modid = THAUMIC_ENERGISTICS_MODID)
    private static void renderEssentiaWithAmount(IAEStack<?> stack, long quantity) {
        if (!(stack instanceof IAEEssentiaStack)) return;

        EssentiaStack essentiaStack = ((IAEEssentiaStack) stack).getStack();
        if (essentiaStack == null) return;

        Aspect aspect = essentiaStack.getAspect();
        if (aspect == null) return;

        int color = aspect.getColor();
        float red = (color >> 16 & 0xFF) / 255.0F;
        float green = (color >> 8 & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;

        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);

        GlStateManager.enableBlend();
        GlStateManager.disableLighting();
        GlStateManager.color(red, green, blue, 1.0F);

        Minecraft.getMinecraft().getTextureManager().bindTexture(aspect.getImage());
        drawTexturedQuad(DISPLAY_ICON_X, DISPLAY_ICON_Y, DISPLAY_ICON_SIZE, DISPLAY_ICON_SIZE, DISPLAY_ICON_Z);

        GlStateManager.disableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        renderAmountText(quantity, "");
    }

    private static void renderAmountText(long quantity, String suffix) {
        String renderedAmount = NUMBER_CONVERTER.toWideReadableForm(quantity) + suffix;

        float scale = 1.0F / 75.0F;

        FontRenderer fontRenderer = Minecraft.getMinecraft().fontRenderer;
        int width = fontRenderer.getStringWidth(renderedAmount);
        GlStateManager.translate(0.0F, DISPLAY_AMOUNT_SPACING, 0.0F);
        GlStateManager.scale(scale, scale, scale);
        GlStateManager.translate(-0.5F * width, 0.0F, 0.5F);
        fontRenderer.drawString(renderedAmount, 0, 0, 0);
    }

    private static void drawSpriteQuad(TextureAtlasSprite sprite, float x, float y, float width, float height, float z) {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        buffer.pos(x, y + height, z).tex(sprite.getMinU(), sprite.getMaxV()).endVertex();
        buffer.pos(x + width, y + height, z).tex(sprite.getMaxU(), sprite.getMaxV()).endVertex();
        buffer.pos(x + width, y, z).tex(sprite.getMaxU(), sprite.getMinV()).endVertex();
        buffer.pos(x, y, z).tex(sprite.getMinU(), sprite.getMinV()).endVertex();
        tessellator.draw();
    }

    private static void drawTexturedQuad(float x, float y, float width, float height, float z) {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        buffer.pos(x, y + height, z).tex(0.0D, 1.0D).endVertex();
        buffer.pos(x + width, y + height, z).tex(1.0D, 1.0D).endVertex();
        buffer.pos(x + width, y, z).tex(1.0D, 0.0D).endVertex();
        buffer.pos(x, y, z).tex(0.0D, 0.0D).endVertex();
        tessellator.draw();
    }

    private DisplayRenderHelper() {}
}
