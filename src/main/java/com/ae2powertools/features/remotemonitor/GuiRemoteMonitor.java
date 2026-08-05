package com.ae2powertools.features.remotemonitor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.lwjgl.input.Mouse;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.client.config.GuiUtils;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.client.gui.widgets.GuiTabButton;

import com.ae2powertools.Tags;
import com.ae2powertools.features.monitor.MonitoredResource;
import com.ae2powertools.features.monitor.client.MonitoredResourceRenderer;
import com.ae2powertools.integration.jei.JeiTooltipBridge;
import com.ae2powertools.network.PacketRemoteMonitorRequestContents;
import com.ae2powertools.network.PacketRemoteMonitorSelectSlot;
import com.ae2powertools.network.PowerToolsNetwork;
import com.ae2powertools.util.FormatUtil;
import com.ae2powertools.widgets.SearchableGridSelectorWidget;
import com.ae2powertools.widgets.WidgetContext;


/**
 * Standalone client GUI for the Remote Storage Monitor.
 * Uses a fixed 9x9 slot grid and a modal selector overlay for choosing resources.
 */
@SideOnly(Side.CLIENT)
public class GuiRemoteMonitor extends GuiScreen implements WidgetContext {

    private static final ResourceLocation BACKGROUND = new ResourceLocation(
        Tags.MODID, "textures/guis/remote_monitor_gui.png");

    private static final int GUI_WIDTH = 178;
    private static final int GUI_HEIGHT = 207;
    private static final int GRID_COLS = 9;
    private static final int GRID_ROWS = 9;
    private static final int SLOT_SIZE = 18;
    private static final int GRID_X = 8;
    private static final int GRID_Y = 18;
    private static final int REFRESH_INTERVAL_ICON = 5 * 16 + 2;
    private static final int SLIDING_WINDOW_ICON = 4 * 16 + 2;
    private static final int TIMING_TAB_X = GUI_WIDTH - 22 - 1;
    private static final int TIMING_TAB_SPACING = 22 + 1;

    private static GuiRemoteMonitor activeInstance;

    private final long deviceId;
    // The shared selector widget keeps its own search term, so switching slots preserves
    // the current filter.
    private final SearchableGridSelectorWidget<MonitoredResource> selectorWidget;

    private int guiLeft;
    private int guiTop;

    private GuiTabButton refreshIntervalBtn;
    private GuiTabButton slidingWindowBtn;

    private int selectorTargetIndex = -1;

    public GuiRemoteMonitor(long deviceId) {
        this.deviceId = deviceId;
        this.selectorWidget = new SearchableGridSelectorWidget<>(
            this,
            "gui.ae2powertools.remote_monitor.selector.title",
            MonitoredResource::getDisplayName,
            (context, resource, x, y) -> {
                MonitoredResourceRenderer.renderIcon(resource, x, y, 16);
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            },
            this::renderSelectorResourceTooltip,
            this::selectMonitorResource);
    }

    public static void receiveSelectorResources(long deviceId, List<MonitoredResource> resources) {
        if (activeInstance == null || activeInstance.deviceId != deviceId) return;
        activeInstance.selectorWidget.setItems(resources);
    }

    public static GuiRemoteMonitor getActiveInstance() {
        return activeInstance;
    }

    @Override
    public void initGui() {
        super.initGui();

        RemoteMonitorClientState.setActiveDeviceId(this.deviceId);
        activeInstance = this;

        this.guiLeft = (this.width - GUI_WIDTH) / 2;
        this.guiTop = (this.height - GUI_HEIGHT) / 2;

        this.buttonList.clear();
        this.buttonList.add(this.refreshIntervalBtn = new GuiTabButton(
            this.guiLeft + TIMING_TAB_X,
            this.guiTop,
            REFRESH_INTERVAL_ICON,
            I18n.format("gui.ae2powertools.remote_monitor.refresh_interval.title"),
            this.itemRender));
        this.buttonList.add(this.slidingWindowBtn = new GuiTabButton(
            this.guiLeft + TIMING_TAB_X - TIMING_TAB_SPACING,
            this.guiTop,
            SLIDING_WINDOW_ICON,
            I18n.format("gui.ae2powertools.remote_monitor.sliding_window.title"),
            this.itemRender));

        selectorWidget.initGui();
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        if (activeInstance == this) activeInstance = null;
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        RemoteMonitorClientState.requestSyncIfNeeded(this.deviceId, !RemoteMonitorClientState.hasState(this.deviceId));
        selectorWidget.updateScreen();
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        super.actionPerformed(button);

        if (button == this.refreshIntervalBtn) {
            this.mc.displayGuiScreen(new GuiRemoteMonitorPollingRate(this.deviceId));
            return;
        }

        if (button == this.slidingWindowBtn) {
            this.mc.displayGuiScreen(new GuiRemoteMonitorSlidingWindow(this.deviceId));
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();

        resetGuiRenderState();
        this.mc.getTextureManager().bindTexture(BACKGROUND);
        this.drawTexturedModalRect(this.guiLeft, this.guiTop, 0, 0, GUI_WIDTH, GUI_HEIGHT);

        // Only draw slots when they are not covered. This avoid GL leaks from overlapping items.
        if (!selectorWidget.isOpen()) drawSlots(mouseX, mouseY);

        resetGuiRenderState();
        super.drawScreen(mouseX, mouseY, partialTicks);

        if (selectorWidget.isOpen()) {
            selectorWidget.draw(mouseX, mouseY, partialTicks);
            selectorWidget.drawTooltip(mouseX, mouseY);
            return;
        }

        drawSlotTooltip(mouseX, mouseY);
        drawRefreshIntervalTooltip(mouseX, mouseY);
        drawSlidingWindowTooltip(mouseX, mouseY);
    }

    private void drawSlots(int mouseX, int mouseY) {
        MonitoredResource[] resources = RemoteMonitorClientState.getOrCreateState(this.deviceId).getResources();
        for (int index = 0; index < RemoteMonitorSessionManager.SLOT_COUNT; index++) {
            int col = index % GRID_COLS;
            int row = index / GRID_COLS;
            int slotX = this.guiLeft + GRID_X + col * SLOT_SIZE;
            int slotY = this.guiTop + GRID_Y + row * SLOT_SIZE;

            MonitoredResourceRenderer.renderIcon(resources[index], slotX + 1, slotY + 1, 16);

            if (mouseX >= slotX && mouseX < slotX + SLOT_SIZE && mouseY >= slotY && mouseY < slotY + SLOT_SIZE) {
                drawRect(slotX + 1, slotY + 1, slotX + SLOT_SIZE - 1, slotY + SLOT_SIZE - 1, 0x40FFFFFF);
            }
        }

        resetGuiRenderState();
    }

    private void resetGuiRenderState() {
        GlStateManager.enableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
            GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
            GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void drawSlotTooltip(int mouseX, int mouseY) {
        int index = getHoveredGridIndex(mouseX, mouseY);
        if (index < 0) return;

        List<String> tooltip = new ArrayList<>();

        MonitoredResource resource = RemoteMonitorClientState.getOrCreateState(this.deviceId).getResources()[index];
        if (resource == null) {
            tooltip.add(TextFormatting.GRAY + I18n.format("gui.ae2powertools.remote_monitor.empty_slot"));
        } else {
            tooltip.addAll(getResourceTooltip(resource, mouseX, mouseY));
        }

        tooltip.add("");
        tooltip.add(TextFormatting.AQUA + I18n.format("gui.ae2powertools.remote_monitor.slot_left_click"));
        if (resource != null) {
            tooltip.add(TextFormatting.AQUA + I18n.format("gui.ae2powertools.remote_monitor.slot_right_click"));
        }

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GuiUtils.drawHoveringText(tooltip, mouseX, mouseY, this.width, this.height, -1, this.fontRenderer);
    }

    private void drawRefreshIntervalTooltip(int mouseX, int mouseY) {
        String interval = FormatUtil.formatTimeTicks(RemoteMonitorClientState.getOrCreateState(this.deviceId).getRefreshRate());
        drawTimingTooltip(
            this.refreshIntervalBtn,
            I18n.format("gui.ae2powertools.remote_monitor.refresh_interval.tooltip", interval),
            I18n.format("gui.ae2powertools.remote_monitor.refresh_interval.description"),
            mouseX,
            mouseY);
    }

    private void drawSlidingWindowTooltip(int mouseX, int mouseY) {
        String interval = FormatUtil.formatTimeTicks(RemoteMonitorClientState.getOrCreateState(this.deviceId).getSlidingWindow());
        drawTimingTooltip(
            this.slidingWindowBtn,
            I18n.format("gui.ae2powertools.remote_monitor.sliding_window.tooltip", interval),
            I18n.format("gui.ae2powertools.remote_monitor.sliding_window.description"),
            mouseX,
            mouseY);
    }

    private void drawTimingTooltip(GuiTabButton button, String title, String description, int mouseX, int mouseY) {
        if (button == null || !button.visible) return;
        if (mouseX < button.x || mouseX >= button.x + button.width) return;
        if (mouseY < button.y || mouseY >= button.y + button.height) return;

        List<String> tooltip = new ArrayList<>();
        tooltip.add(TextFormatting.AQUA + title);
        tooltip.add(TextFormatting.GRAY + description);

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GuiUtils.drawHoveringText(tooltip, mouseX, mouseY, this.width, this.height, -1, this.fontRenderer);
    }

    private int getHoveredGridIndex(int mouseX, int mouseY) {
        int relX = mouseX - this.guiLeft - GRID_X;
        int relY = mouseY - this.guiTop - GRID_Y;
        if (relX < 0 || relY < 0) return -1;

        int col = relX / SLOT_SIZE;
        int row = relY / SLOT_SIZE;
        if (col < 0 || col >= GRID_COLS || row < 0 || row >= GRID_ROWS) return -1;

        return row * GRID_COLS + col;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (selectorWidget.mouseClicked(mouseX, mouseY, mouseButton)) return;

        super.mouseClicked(mouseX, mouseY, mouseButton);

        int index = getHoveredGridIndex(mouseX, mouseY);
        if (index < 0) return;

        // Left-click opens the selector, right-click clears the slot
        if (mouseButton == 0) {
            openSelector(index);
            return;
        }

        if (mouseButton == 1) {
            PowerToolsNetwork.INSTANCE.sendToServer(new PacketRemoteMonitorSelectSlot(this.deviceId, index, null));
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (selectorWidget.keyTyped(typedChar, keyCode)) return;

        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();

        int scroll = Mouse.getEventDWheel();
        if (selectorWidget.handleMouseWheel(scroll)) return;
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        if (selectorWidget.isOpen()) {
            selectorWidget.mouseReleased(mouseX, mouseY, state);
            return;
        }

        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (selectorWidget.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick)) {
            return;
        }

        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }

    private void openSelector(int targetIndex) {
        this.selectorTargetIndex = targetIndex;
        selectorWidget.open(RemoteMonitorClientState.getOrCreateState(this.deviceId).getSelectorResources());
        PowerToolsNetwork.INSTANCE.sendToServer(new PacketRemoteMonitorRequestContents(this.deviceId));
    }

    private List<String> getResourceTooltip(MonitoredResource resource, int mouseX, int mouseY) {
        IAEStack<?> stack = resource.getStack();
        if (stack instanceof IAEItemStack) {
            ItemStack itemStack = ((IAEItemStack) stack).getDefinition();

            return new ArrayList<>(this.getItemToolTip(itemStack));
        }

        return JeiTooltipBridge.buildTooltip(resource);
    }

    private void renderSelectorResourceTooltip(MonitoredResource resource, int mouseX, int mouseY) {
        List<String> tooltip = getResourceTooltip(resource, mouseX, mouseY);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GuiUtils.drawHoveringText(tooltip, mouseX, mouseY, this.width, this.height, -1, this.fontRenderer);
    }

    private void selectMonitorResource(MonitoredResource resource) {
        PowerToolsNetwork.INSTANCE.sendToServer(new PacketRemoteMonitorSelectSlot(
            this.deviceId,
            this.selectorTargetIndex,
            resource));
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