package com.ae2powertools.features.monitor.display;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.client.render.TesrRenderHelper;

import com.ae2powertools.client.PowerToolsClientConfig;
import com.ae2powertools.features.monitor.MonitoredResource;


/**
 * TESR (Tile Entity Special Renderer) for the ME Storage Display block.
 * Renders the configured content icon and quantity text on the facing face,
 * sourcing its data from the {@link com.ae2powertools.features.monitor.dependent.DisplayLogic}'s
 * client-side render cache so it works the moment the chunk loads (no need to open the GUI first).
 * <p>
 * Skips rendering when the player is beyond the configured maximum render distance,
 * or when the player is on the back side of the block (so the back of a screen doesn't
 * show inverted text bleeding through).
 * <p>
 * The corner color overlay is rendered by the baked block model itself (see
 * {@code BlockStorageDisplay} + {@code DisplayBlockColor}), NOT by this TESR. That way
 * the corners stay visible at any distance, unaffected by the TESR's distance gate.
 */
@SideOnly(Side.CLIENT)
public class TESRStorageDisplay extends TileEntitySpecialRenderer<TileStorageDisplay> {

    @Override
    public void render(TileStorageDisplay te, double x, double y, double z,
                        float partialTicks, int destroyStage, float alpha) {

        // Distance gate - dynamic content fades out beyond user-configured range.
        double distSq = Minecraft.getMinecraft().player.getDistanceSq(
            te.getPos().getX() + 0.5, te.getPos().getY() + 0.5, te.getPos().getZ() + 0.5);
        int maxDist = PowerToolsClientConfig.monitor.displayRenderDistance;
        if (distSq > (double) maxDist * maxDist) return;

        // Pull the snapshot the server pushed via writeToStream. We deliberately do NOT call
        // getFirstResource() here: the dependent's full entries list is only synced to the
        // client while the GUI is open, so relying on it would leave the block blank after
        // a chunk reload. The cache below is kept fresh by DisplayLogic.pollSyncDirty().
        MonitoredResource content = te.getDisplayLogic().getClientResource();
        if (content == null) return;

        EnumFacing facing = te.getFacing();

        // Don't draw the screen contents from behind the block (the BlockRenderLayer is
        // CUTOUT_MIPPED, so the front face's transparent regions would otherwise let the
        // mirrored item geometry show through from the back).
        if (!DisplayRenderHelper.isViewerInFront(te.getPos(), facing, partialTicks)) return;

        GlStateManager.pushMatrix();
        // Translate to the BLOCK CENTER first - AE2's TesrRenderHelper.moveToFace expects the
        // origin to be at the block's centre and translates by ±0.5 from there to reach the
        // face. Without the +0.5, the rendering anchors at the block's south-west-bottom
        // corner instead, producing a half-block offset along all three axes.
        GlStateManager.translate(x + 0.5, y + 0.5, z + 0.5);

        // Use AE2's own face-anchoring helpers - they apply the same scale(-1,-1,-1)
        // convention AE2's storage monitors use, which is what makes the rendered text/item
        // appear right-side-up. Rolling our own rotation matrix here was the source of the
        // "everything is mirrored when looking at the front" bug.
        TesrRenderHelper.moveToFace(facing);
        TesrRenderHelper.rotateToFace(facing, (byte) 0);

        DisplayRenderHelper.renderResourceWithAmount(content, te.getDisplayLogic().getClientQuantity());

        GlStateManager.popMatrix();
    }
}
