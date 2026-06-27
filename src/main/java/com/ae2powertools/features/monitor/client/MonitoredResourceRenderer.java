package com.ae2powertools.features.monitor.client;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;

import com.ae2powertools.features.monitor.MonitoredResource;
import com.ae2powertools.features.monitor.ResourceType;


/**
 * Centralised rendering helper for {@link MonitoredResource} icons.
 * <p>
 * Mirrors CELLS' {@code com.cells.gui.ResourceRenderer} so that fluids, gases, and
 * essentia all render natively in the Storage Emitter GUI's grid and selector.
 * <p>
 * Each renderer is wrapped in {@link Optional.Method} guards so the class is safe
 * to load even when the optional mods (Mekanism Energistics, Thaumic Energistics)
 * aren't present.
 */
@SideOnly(Side.CLIENT)
public final class MonitoredResourceRenderer {

    private static final String MEKENG_MODID = "mekeng";
    private static final String TE_MODID = "thaumicenergistics";

    private MonitoredResourceRenderer() {}

    /**
     * Render the icon for a resource at (x, y) in the given size.
     * Handles GL state setup/teardown internally so callers don't have to worry
     * about lighting, depth, or color leaks.
     */
    public static void renderIcon(MonitoredResource resource, int x, int y, int size) {
        if (resource == null) return;

        ResourceType type = resource.getType();
        IAEStack<?> stack = resource.getStack();
        if (stack == null) return;

        switch (type) {
            case ITEM:
                if (stack instanceof IAEItemStack) renderItem((IAEItemStack) stack, x, y, size);
                break;

            case FLUID:
                if (stack instanceof IAEFluidStack) renderFluid((IAEFluidStack) stack, x, y, size, size);
                break;

            case GAS:
                if (Loader.isModLoaded(MEKENG_MODID)) renderGas(stack, x, y, size, size);
                break;

            case ESSENTIA:
                if (Loader.isModLoaded(TE_MODID)) renderEssentia(stack, x, y, size, size);
                break;

            default: break;
        }
    }

    // ==================== Item rendering ====================

    private static void renderItem(IAEItemStack aeStack, int x, int y, int size) {
        ItemStack is = aeStack.getDefinition();
        if (is.isEmpty()) return;

        Minecraft mc = Minecraft.getMinecraft();
        float scale = size / 16.0F;

        GlStateManager.enableDepth();
        RenderHelper.enableGUIStandardItemLighting();

        if (scale == 1.0F) {
            mc.getRenderItem().renderItemAndEffectIntoGUI(is, x, y);
        } else {
            GlStateManager.pushMatrix();
            GlStateManager.translate(x, y, 0.0F);
            GlStateManager.scale(scale, scale, 1.0F);
            mc.getRenderItem().renderItemAndEffectIntoGUI(is, 0, 0);
            GlStateManager.popMatrix();
        }

        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableDepth();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    // ==================== Fluid rendering ====================

    private static void renderFluid(IAEFluidStack aeFluid, int x, int y, int width, int height) {
        FluidStack fluidStack = aeFluid.getFluidStack();
        if (fluidStack == null) return;

        Fluid fluid = fluidStack.getFluid();
        if (fluid == null || fluid.getStill() == null) return;

        Minecraft mc = Minecraft.getMinecraft();

        GlStateManager.disableLighting();
        GlStateManager.disableBlend();
        mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        TextureAtlasSprite sprite = mc.getTextureMapBlocks().getAtlasSprite(fluid.getStill().toString());

        // NBT-aware coloring (potions etc.)
        int color = fluid.getColor(fluidStack);
        float r = (color >> 16 & 0xFF) / 255.0F;
        float g = (color >> 8 & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;
        GlStateManager.color(r, g, b, 1.0F);

        drawTexturedRect(x, y, sprite, width, height);

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableBlend();
    }

    // ==================== Gas rendering ====================

    @Optional.Method(modid = MEKENG_MODID)
    private static void renderGas(IAEStack<?> stack, int x, int y, int width, int height) {
        if (!(stack instanceof com.mekeng.github.common.me.data.IAEGasStack)) return;

        mekanism.api.gas.GasStack gasStack = ((com.mekeng.github.common.me.data.IAEGasStack) stack).getGasStack();
        if (gasStack == null) return;

        mekanism.api.gas.Gas gas = gasStack.getGas();
        if (gas == null) return;

        TextureAtlasSprite sprite = gas.getSprite();
        if (sprite == null) return;

        Minecraft mc = Minecraft.getMinecraft();
        GlStateManager.disableLighting();
        GlStateManager.disableBlend();
        mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);

        mekanism.client.render.MekanismRenderer.color(gas);
        drawTexturedRect(x, y, sprite, width, height);
        mekanism.client.render.MekanismRenderer.resetColor();

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableBlend();
    }

    // ==================== Essentia rendering ====================

    @Optional.Method(modid = TE_MODID)
    private static void renderEssentia(IAEStack<?> stack, int x, int y, int width, int height) {
        if (!(stack instanceof thaumicenergistics.api.storage.IAEEssentiaStack)) return;

        thaumicenergistics.api.EssentiaStack essentiaStack =
            ((thaumicenergistics.api.storage.IAEEssentiaStack) stack).getStack();
        if (essentiaStack == null) return;

        thaumcraft.api.aspects.Aspect aspect = essentiaStack.getAspect();
        if (aspect == null) return;

        Minecraft mc = Minecraft.getMinecraft();

        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.pushMatrix();

        // Aspect images aren't on the block atlas; bind the aspect's full texture directly.
        mc.getTextureManager().bindTexture(aspect.getImage());

        java.awt.Color c = new java.awt.Color(aspect.getColor());
        float r = c.getRed() / 255.0F;
        float g = c.getGreen() / 255.0F;
        float b = c.getBlue() / 255.0F;
        GlStateManager.color(r, g, b, 1.0F);

        // The aspect sprite occupies the entire bound texture, so we use plain 0..1 UVs.
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buffer = tess.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
        buffer.pos(x, y + height, 0).tex(0.0D, 1.0D).color(r, g, b, 1.0f).endVertex();
        buffer.pos(x + width, y + height, 0).tex(1.0D, 1.0D).color(r, g, b, 1.0f).endVertex();
        buffer.pos(x + width, y, 0).tex(1.0D, 0.0D).color(r, g, b, 1.0f).endVertex();
        buffer.pos(x, y, 0).tex(0.0D, 0.0D).color(r, g, b, 1.0f).endVertex();
        tess.draw();

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
        GlStateManager.disableBlend();
    }

    // ==================== Shared helpers ====================

    /**
     * Draw a textured rect with proper sprite UV mapping (for animated atlas textures).
     */
    private static void drawTexturedRect(int x, int y, TextureAtlasSprite sprite, int width, int height) {
        Tessellator t = Tessellator.getInstance();
        BufferBuilder buf = t.getBuffer();

        buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        buf.pos(x, y + height, 0.0).tex(sprite.getMinU(), sprite.getMaxV()).endVertex();
        buf.pos(x + width, y + height, 0.0).tex(sprite.getMaxU(), sprite.getMaxV()).endVertex();
        buf.pos(x + width, y, 0.0).tex(sprite.getMaxU(), sprite.getMinV()).endVertex();
        buf.pos(x, y, 0.0).tex(sprite.getMinU(), sprite.getMinV()).endVertex();
        t.draw();
    }
}
