package com.ae2powertools.features.monitor.display;

import java.io.IOException;

import javax.annotation.Nonnull;

import io.netty.buffer.ByteBuf;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.util.AECableType;
import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.parts.IPartCollisionHelper;
import appeng.client.render.TesrRenderHelper;

import com.ae2powertools.client.PowerToolsClientConfig;
import com.ae2powertools.features.monitor.MonitoredResource;
import com.ae2powertools.features.monitor.dependent.DisplayLogic;
import com.ae2powertools.features.monitor.dependent.MonitorHostType;
import com.ae2powertools.features.monitor.dependent.PartStorageMonitorBase;


/**
 * Cable part variant of the ME Storage Display.
 * Renders content icon + quantity + corner color on its face.
 */
abstract public class PartStorageDisplayBase extends PartStorageMonitorBase {

    private final int modelIndex;
    private final DisplayLogic displayLogic;

    public PartStorageDisplayBase(ItemStack is, int modelIndex) {
        super(is);
        this.modelIndex = modelIndex;
        this.displayLogic = new DisplayLogic(monitorLogic);
    }

    // --- Grid ticking ---

    @Override
    @Nonnull
    public TickRateModulation tickingRequest(@Nonnull IGridNode node, int ticksSinceLastCall) {
        World world = getHostWorld();
        if (world == null) return TickRateModulation.IDLE;

        monitorLogic.refresh();
        displayLogic.evaluate();

        // Push a fresh client sync ONLY when something the client cares about
        if (displayLogic.pollSyncDirty()) getHost().markForUpdate();

        return TickRateModulation.SAME;
    }

    // --- Dynamic rendering ---

    @Override
    @SideOnly(Side.CLIENT)
    public void renderDynamic(double x, double y, double z, float partialTicks, int destroyStage) {
        World world = getHostWorld();
        if (world == null) return;

        // Distance check
        double distSq = Minecraft.getMinecraft().player.getDistanceSq(
            getHostPos().getX() + 0.5, getHostPos().getY() + 0.5, getHostPos().getZ() + 0.5);
        int maxDist = PowerToolsClientConfig.monitor.displayRenderDistance;
        boolean renderContent = distSq <= (double) maxDist * maxDist;

        EnumFacing facing = getSide().getFacing();

        // Skip rendering when the player is on the back side of the part
        if (!DisplayRenderHelper.isViewerInFront(getHostPos(), facing, partialTicks)) return;

        // The cached resource/quantity are populated by readFromStream from the server's
        // pollSyncDirty()-driven pushes
        MonitoredResource content = displayLogic.getClientResource();
        long quantity = displayLogic.getClientQuantity();

        GlStateManager.pushMatrix();
        // Translate to the BLOCK CENTER first - AE2's TesrRenderHelper.moveToFace expects the
        // origin to be at the block's centre and translates by ±0.5 from there to reach the
        // face.
        GlStateManager.translate(x + 0.5, y + 0.5, z + 0.5);

        // Use AE2's own face-anchoring + rotation helpers. They produce the same coordinate
        // frame AE2 uses for cable-mounted monitors (storage monitors, conversion monitors,
        // etc.), which gives us text-upright orientation on every face for free.
        TesrRenderHelper.moveToFace(facing);
        TesrRenderHelper.rotateToFace(facing, (byte) 0);

        // Match the block display's baked front overlays instead of letting the part's static
        // model pick up cable-bus AO and skip vanilla face-diffuse shading.
        int packedLight = world.getCombinedLight(getHostPos(), 0);
        DisplayRenderHelper.drawCornerIndicators(displayLogic.getCornerColor(), packedLight, facing, modelIndex);

        // The center is drawn in the baked model, as it is a fixed white color
        // It would be a different matter if we wanted to tint it
        // DisplayRenderHelper.drawScreenCenter(packedLight, facing, modelIndex);


        if (renderContent) DisplayRenderHelper.renderResourceWithAmount(content, quantity);

        GlStateManager.popMatrix();
    }

    // --- Part model ---

    @Override
    public boolean requireDynamicRender() {
        return true;
    }

    @Override
    public float getCableConnectionLength(AECableType cable) {
        // The current display model is a 2-pixel-deep full-face panel, so the cable arm
        // must stop 2 units from the face to meet it cleanly.
        return 2;
    }

    // --- Collision box ---

    @Override
    abstract public void getBoxes(IPartCollisionHelper bch);

    // --- IMonitorLogicHost ---

    @Override
    public void onConditionChanged(boolean oldMet, boolean newMet) {
        displayLogic.evaluate();
        getHost().markForUpdate();
    }

    @Override
    public MonitorHostType getHostType() {
        return MonitorHostType.DISPLAY;
    }

    // --- NBT persistence ---

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        displayLogic.readFromNBT(tag);
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        displayLogic.writeToNBT(tag);
    }

    // --- Network sync ---

    @Override
    public void writeToStream(ByteBuf data) throws IOException {
        super.writeToStream(data);
        // Sync the rendering snapshot (resource + quantity + cornerColor) so the client TESR
        // can draw the display without needing the (much heavier) full entries list, which
        // is only synced while the GUI is open.
        displayLogic.writeToStream(data);
    }

    @Override
    public boolean readFromStream(ByteBuf data) throws IOException {
        super.readFromStream(data);
        displayLogic.readFromStream(data);
        // Returning true asks AE2 to re-render the part. Required because the corner color
        // tint and the displayed icon both come from this stream.
        return true;
    }
}
