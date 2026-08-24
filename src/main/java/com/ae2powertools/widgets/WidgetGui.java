package com.ae2powertools.widgets;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.lwjgl.input.Mouse;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.util.ResourceLocation;


/**
 * Shared host for the mod's widget-driven GUIs.
 * <p>
 * Subclasses register GUI-relative buttons and top-layer modals, while keeping their
 * feature-specific background, foreground, and packet behavior local.
 */
public abstract class WidgetGui extends GuiContainer implements WidgetContext {

    /**
     * Screen coordinates that cannot be supplied by an actual mouse event. They keep the
     * inactive GUI from entering any hover state while a modal owns the real pointer.
     */
    private static final int MODAL_INERT_MOUSE_COORDINATE = -10_000;

    private static final class RegisteredWidget {

        private final PressableWidget widget;
        private final int offsetX;
        private final int offsetY;

        private RegisteredWidget(PressableWidget widget, int offsetX, int offsetY) {
            this.widget = widget;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
        }
    }

    private static final class RegisteredModal {

        private final AbstractModalGui modal;
        private final WidgetAnchor anchor;
        private final int offsetX;
        private final int offsetY;

        private RegisteredModal(AbstractModalGui modal, WidgetAnchor anchor, int offsetX, int offsetY) {
            this.modal = modal;
            this.anchor = anchor;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
        }
    }

    private final List<RegisteredWidget> registeredWidgets = new ArrayList<>();
    private final List<RegisteredModal> registeredModals = new ArrayList<>();
    @Nullable
    private final ResourceLocation backgroundTexture;

    protected WidgetGui(Container container, int guiWidth, int guiHeight, @Nullable ResourceLocation backgroundTexture) {
        super(container);
        this.xSize = guiWidth;
        this.ySize = guiHeight;
        this.backgroundTexture = backgroundTexture;
    }

    protected WidgetGui(int guiWidth, int guiHeight, @Nullable ResourceLocation backgroundTexture) {
        this(createEmptyContainer(), guiWidth, guiHeight, backgroundTexture);
    }

    private static Container createEmptyContainer() {
        return new Container() {
            @Override
            public boolean canInteractWith(@Nonnull EntityPlayer playerIn) {
                return false;
            }
        };
    }

    protected final <T extends PressableWidget> T registerWidget(T widget, int offsetX, int offsetY) {
        registeredWidgets.add(new RegisteredWidget(widget, offsetX, offsetY));
        return widget;
    }

    protected final <T extends AbstractModalGui> T registerModal(T modal, WidgetAnchor anchor, int offsetX, int offsetY) {
        registeredModals.add(new RegisteredModal(modal, anchor, offsetX, offsetY));
        return modal;
    }

    @Nullable
    protected final AbstractModalGui getOpenModal() {
        for (RegisteredModal registeredModal : registeredModals) {
            if (registeredModal.modal.isOpen()) return registeredModal.modal;
        }

        return null;
    }

    protected final boolean isManagedModalOpen() {
        return getOpenModal() != null;
    }

    @Override
    public void initGui() {
        super.initGui();
        buttonList.clear();

        for (RegisteredWidget registeredWidget : registeredWidgets) {
            registeredWidget.widget.setPosition(guiLeft + registeredWidget.offsetX, guiTop + registeredWidget.offsetY);
        }

        for (RegisteredModal registeredModal : registeredModals) {
            int modalX = guiLeft + registeredModal.offsetX;
            int modalY = guiTop + registeredModal.offsetY;
            if (registeredModal.anchor == WidgetAnchor.SCREEN_CENTER) {
                modalX = (width - registeredModal.modal.getModalWidth()) / 2 + registeredModal.offsetX;
                modalY = (height - registeredModal.modal.getModalHeight()) / 2 + registeredModal.offsetY;
            }

            registeredModal.modal.setPosition(modalX, modalY);
            registeredModal.modal.initGui();
        }

        afterWidgetGuiInit();
    }

    protected void afterWidgetGuiInit() {
    }

    @Override
    public void updateScreen() {
        super.updateScreen();

        for (RegisteredModal registeredModal : registeredModals) {
            if (registeredModal.modal.isOpen()) registeredModal.modal.updateScreen();
        }

        updateWidgetGuiScreen();
    }

    protected void updateWidgetGuiScreen() {
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        int underlyingMouseX = getUnderlyingMouseCoordinate(mouseX);
        int underlyingMouseY = getUnderlyingMouseCoordinate(mouseY);

        if (shouldDrawWidgetGuiBackground()) drawDefaultBackground();

        beforeWidgetGuiDrawScreen(underlyingMouseX, underlyingMouseY, partialTicks);
        super.drawScreen(underlyingMouseX, underlyingMouseY, partialTicks);
        afterWidgetGuiDrawScreen(underlyingMouseX, underlyingMouseY, partialTicks);

        AbstractModalGui openModal = getOpenModal();
        if (openModal != null) {
            openModal.draw(mouseX, mouseY, partialTicks);
            openModal.drawTooltip(mouseX, mouseY);
            return;
        }

        for (RegisteredWidget registeredWidget : registeredWidgets) {
            registeredWidget.widget.drawTooltip(this, mouseX, mouseY);
        }

        drawWidgetGuiTooltips(mouseX, mouseY);
    }

    protected boolean shouldDrawWidgetGuiBackground() {
        return true;
    }

    protected void beforeWidgetGuiDrawScreen(int mouseX, int mouseY, float partialTicks) {
    }

    protected void afterWidgetGuiDrawScreen(int mouseX, int mouseY, float partialTicks) {
    }

    protected void drawWidgetGuiTooltips(int mouseX, int mouseY) {
    }

    @Override
    protected final void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        int underlyingMouseX = getUnderlyingMouseCoordinate(mouseX);
        int underlyingMouseY = getUnderlyingMouseCoordinate(mouseY);

        prepareWidgetGuiBackground();
        drawWidgetGuiBackgroundTexture();
        drawWidgetGuiBackgroundContents(partialTicks, underlyingMouseX, underlyingMouseY);

        for (RegisteredWidget registeredWidget : registeredWidgets) {
            registeredWidget.widget.draw(this, underlyingMouseX, underlyingMouseY);
        }

        afterWidgetGuiWidgetsDraw();
    }

    protected void prepareWidgetGuiBackground() {
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    protected final void drawWidgetGuiBackgroundTexture() {
        if (backgroundTexture == null) return;

        mc.getTextureManager().bindTexture(backgroundTexture);
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize);
    }

    protected abstract void drawWidgetGuiBackgroundContents(float partialTicks, int mouseX, int mouseY);

    protected void afterWidgetGuiWidgetsDraw() {
    }

    @Override
    protected boolean isPointInRegion(int rectX, int rectY, int rectWidth, int rectHeight, int pointX, int pointY) {
        if (isManagedModalOpen()) return false;

        return super.isPointInRegion(rectX, rectY, rectWidth, rectHeight, pointX, pointY);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        AbstractModalGui openModal = getOpenModal();
        if (openModal != null) {
            openModal.keyTyped(typedChar, keyCode);
            return;
        }

        if (handleWidgetGuiKeyTyped(typedChar, keyCode)) return;

        super.keyTyped(typedChar, keyCode);
    }

    protected boolean handleWidgetGuiKeyTyped(char typedChar, int keyCode) {
        return false;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        AbstractModalGui openModal = getOpenModal();
        if (openModal != null) {
            openModal.mouseClicked(mouseX, mouseY, mouseButton);
            return;
        }

        for (RegisteredWidget registeredWidget : registeredWidgets) {
            if (registeredWidget.widget.mouseClicked(mouseX, mouseY, mouseButton)) return;
        }

        if (handleWidgetGuiMouseClicked(mouseX, mouseY, mouseButton)) return;

        super.mouseClicked(mouseX, mouseY, mouseButton);
        afterWidgetGuiMouseClicked(mouseX, mouseY, mouseButton);
    }

    protected boolean handleWidgetGuiMouseClicked(int mouseX, int mouseY, int mouseButton) {
        return false;
    }

    protected void afterWidgetGuiMouseClicked(int mouseX, int mouseY, int mouseButton) {
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        AbstractModalGui openModal = getOpenModal();
        if (openModal != null) {
            openModal.mouseReleased(mouseX, mouseY, state);
            return;
        }

        super.mouseReleased(mouseX, mouseY, state);
        afterWidgetGuiMouseReleased(mouseX, mouseY, state);
    }

    protected void afterWidgetGuiMouseReleased(int mouseX, int mouseY, int state) {
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        AbstractModalGui openModal = getOpenModal();
        if (openModal != null) {
            openModal.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
            return;
        }

        if (handleWidgetGuiMouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick)) {
            return;
        }

        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }

    protected boolean handleWidgetGuiMouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        return false;
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();

        int scroll = Mouse.getEventDWheel();
        if (scroll == 0) return;

        AbstractModalGui openModal = getOpenModal();
        if (openModal != null) {
            openModal.handleMouseWheel(scroll);
            return;
        }

        int mouseX = Mouse.getEventX() * width / mc.displayWidth;
        int mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        handleWidgetGuiMouseWheel(mouseX, mouseY, scroll);
    }

    protected void handleWidgetGuiMouseWheel(int mouseX, int mouseY, int scrollDelta) {
    }

    private int getUnderlyingMouseCoordinate(int coordinate) {
        return isManagedModalOpen() ? MODAL_INERT_MOUSE_COORDINATE : coordinate;
    }

    @Override
    public Minecraft getWidgetMinecraft() {
        return mc;
    }

    @Override
    public FontRenderer getWidgetFontRenderer() {
        return fontRenderer;
    }

    @Override
    public RenderItem getWidgetItemRenderer() {
        return itemRender;
    }

    @Override
    public int getWidgetWidth() {
        return width;
    }

    @Override
    public int getWidgetHeight() {
        return height;
    }
}
