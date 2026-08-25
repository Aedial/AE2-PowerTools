package com.ae2powertools.features.remotemonitor;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.client.config.GuiUtils;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;

import com.ae2powertools.Tags;
import com.ae2powertools.features.monitor.MonitoredResource;
import com.ae2powertools.features.monitor.client.MonitoredResourceRenderer;
import com.ae2powertools.integration.jei.JeiTooltipBridge;
import com.ae2powertools.network.PacketRemoteMonitorPollNow;
import com.ae2powertools.network.PacketRemoteMonitorRequestContents;
import com.ae2powertools.network.PacketRemoteMonitorSelectSlot;
import com.ae2powertools.network.PowerToolsNetwork;
import com.ae2powertools.util.FormatUtil;
import com.ae2powertools.widgets.Ae2Button;
import com.ae2powertools.widgets.SearchableGridSelectorWidget;
import com.ae2powertools.widgets.TabButton;
import com.ae2powertools.widgets.WidgetAnchor;
import com.ae2powertools.widgets.WidgetGui;


/**
 * Standalone client GUI for the Remote Storage Monitor.
 * Uses a fixed 9x9 slot grid and a modal selector overlay for choosing resources.
 */
@SideOnly(Side.CLIENT)
public class GuiRemoteMonitor extends WidgetGui {

    private static final ResourceLocation BACKGROUND = new ResourceLocation(
        Tags.MODID, "textures/guis/remote_monitor_gui.png");

    private static final int GUI_WIDTH = 178;
    private static final int GUI_HEIGHT = 207;
    private static final int GRID_COLS = 9;
    private static final int GRID_ROWS = 9;
    private static final int SLOT_SIZE = 18;
    private static final int GRID_X = 8;
    private static final int GRID_Y = 18;
    private static final int SIDE_BTN_SIZE = 16;
    private static final int SIDE_BTN_X = -SIDE_BTN_SIZE - 2;
    private static final int SIDE_BTN_Y = 4;
    private static final int REFRESH_INTERVAL_ICON = 5 * 16 + 2;
    private static final int SLIDING_WINDOW_ICON = 4 * 16 + 2;
    private static final int TIMING_TAB_X = GUI_WIDTH - 22 - 1;
    private static final int TIMING_TAB_SPACING = 22 + 1;

    private static GuiRemoteMonitor activeInstance;

    private final long deviceId;
    // The shared selector widget keeps its own search term, so switching slots preserves
    // the current filter.
    private final SearchableGridSelectorWidget<MonitoredResource> selectorWidget;
    private final Ae2Button manualPollBtn = new Ae2Button(0, 0, SIDE_BTN_SIZE);

    private final TabButton refreshIntervalBtn = new TabButton(0, 0, REFRESH_INTERVAL_ICON,
                                                               I18n.format("gui.ae2powertools.remote_monitor.refresh_interval.title"));
    private final TabButton slidingWindowBtn = new TabButton(0, 0, SLIDING_WINDOW_ICON,
                                                             I18n.format("gui.ae2powertools.remote_monitor.sliding_window.title"));

    private int selectorTargetIndex = -1;

    public GuiRemoteMonitor(long deviceId) {
        super(GUI_WIDTH, GUI_HEIGHT, BACKGROUND);

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
        registerModal( this.selectorWidget, WidgetAnchor.SCREEN_CENTER, 0, 0);

        this.manualPollBtn.setTextureIcon(Ae2Button.ICON.REFRESH);
        this.manualPollBtn.setOnClick(
            () -> PowerToolsNetwork.INSTANCE.sendToServer(new PacketRemoteMonitorPollNow(this.deviceId)));
        this.manualPollBtn.setTooltipProvider(this::buildManualPollTooltip);
        registerWidget(manualPollBtn, SIDE_BTN_X, SIDE_BTN_Y);

        this.refreshIntervalBtn.setTooltipProvider(this::buildRefreshIntervalTooltip);
        this.refreshIntervalBtn.setOnClick(
            () -> this.mc.displayGuiScreen(new GuiRemoteMonitorPollingRate(this.deviceId)));
        registerWidget(refreshIntervalBtn, TIMING_TAB_X, 0);

        this.slidingWindowBtn.setTooltipProvider(this::buildSlidingWindowTooltip);
        this.slidingWindowBtn.setOnClick(
            () -> this.mc.displayGuiScreen(new GuiRemoteMonitorSlidingWindow(this.deviceId)));
        registerWidget(slidingWindowBtn, TIMING_TAB_X - TIMING_TAB_SPACING, 0);
    }

    private List<String> buildTimingTooltip(String prefix, int value) {
        String formattedValue = FormatUtil.formatTimeTicks(value);

        List<String> tooltip = new ArrayList<>();
        tooltip.add(I18n.format(prefix + ".tooltip", formattedValue));
        tooltip.add(I18n.format(prefix + ".description"));
        return tooltip;
    }

    private List<String> buildRefreshIntervalTooltip() {
        int interval = RemoteMonitorClientState.getOrCreateState(this.deviceId).getRefreshRate();
        return buildTimingTooltip("gui.ae2powertools.remote_monitor.refresh_interval", interval);
    }

    private List<String> buildSlidingWindowTooltip() {
        int interval = RemoteMonitorClientState.getOrCreateState(this.deviceId).getSlidingWindow();
        return buildTimingTooltip("gui.ae2powertools.remote_monitor.sliding_window", interval);
    }

    private List<String> buildManualPollTooltip() {
        List<String> tooltip = new ArrayList<>();
        tooltip.add(I18n.format("gui.ae2powertools.poll_now.title"));
        tooltip.add(I18n.format("gui.ae2powertools.poll_now.description"));
        return tooltip;
    }

    public static void receiveSelectorResources(long deviceId, List<MonitoredResource> resources) {
        if (activeInstance == null || activeInstance.deviceId != deviceId) return;
        activeInstance.selectorWidget.setItems(resources);
    }

    public static GuiRemoteMonitor getActiveInstance() {
        return activeInstance;
    }

    @Override
    protected void afterWidgetGuiInit() {
        RemoteMonitorClientState.setActiveDeviceId(this.deviceId);
        activeInstance = this;
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        if (activeInstance == this) activeInstance = null;
    }

    @Override
    protected void updateWidgetGuiScreen() {
        RemoteMonitorClientState.requestSyncIfNeeded(this.deviceId, !RemoteMonitorClientState.hasState(this.deviceId));
    }

    @Override
    protected void prepareWidgetGuiBackground() {
        resetGuiRenderState();
    }

    @Override
    protected void drawWidgetGuiBackgroundContents(float partialTicks, int mouseX, int mouseY) {
        if (!isManagedModalOpen()) drawSlots(mouseX, mouseY);

        resetGuiRenderState();
    }

    @Override
    protected void drawWidgetGuiTooltips(int mouseX, int mouseY) {
        drawSlotTooltip(mouseX, mouseY);
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
            tooltip.add(I18n.format("gui.ae2powertools.remote_monitor.empty_slot"));
        } else {
            tooltip.addAll(getResourceTooltip(resource, mouseX, mouseY));
        }

        tooltip.add("");
        tooltip.add(I18n.format("gui.ae2powertools.remote_monitor.slot_left_click"));
        if (resource != null) {
            tooltip.add(I18n.format("gui.ae2powertools.remote_monitor.slot_right_click"));
        }

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GuiUtils.drawHoveringText(tooltip, mouseX, mouseY, this.width, this.height, -1, this.fontRenderer);
    }

    private int getHoveredGridIndex(int mouseX, int mouseY) {
        int relX = mouseX - this.guiLeft - GRID_X;
        int relY = mouseY - this.guiTop - GRID_Y;
        if (relX < 0 || relY < 0) return -1;

        int col = relX / SLOT_SIZE;
        int row = relY / SLOT_SIZE;
        if (col >= GRID_COLS || row >= GRID_ROWS) return -1;

        return row * GRID_COLS + col;
    }

    @Override
    protected void afterWidgetGuiMouseClicked(int mouseX, int mouseY, int mouseButton) {
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

    public List<Rectangle> getJEIExclusionArea() {
        return Collections.singletonList(manualPollBtn.getBounds());
    }
}