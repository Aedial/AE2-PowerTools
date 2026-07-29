package com.ae2powertools.features.remotemonitor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
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
import com.ae2powertools.util.PollingRateUtils;


/**
 * Standalone client GUI for the Remote Storage Monitor.
 * Uses a fixed 9x9 slot grid and a modal selector overlay for choosing resources.
 */
@SideOnly(Side.CLIENT)
public class GuiRemoteMonitor extends GuiScreen {

    private static final ResourceLocation BACKGROUND = new ResourceLocation(
        Tags.MODID, "textures/guis/remote_monitor_gui.png");
    private static final ResourceLocation SELECTOR_BACKGROUND = new ResourceLocation(
        Tags.MODID, "textures/guis/recipe_selector.png");
    private static final ResourceLocation SCROLLBAR_TEXTURE = new ResourceLocation(
        "minecraft", "textures/gui/container/creative_inventory/tabs.png");

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

    private static final int SELECTOR_WIDTH = 195;
    private static final int SELECTOR_HEIGHT = 186;
    private static final int SELECTOR_GRID_X = 8;
    private static final int SELECTOR_GRID_Y = 17;
    private static final int SELECTOR_COLS = 9;
    private static final int SELECTOR_ROWS = 9;
    private static final int SELECTOR_SLOT_SIZE = 18;
    private static final int SELECTOR_SEARCH_X = 80;
    private static final int SELECTOR_SEARCH_Y = 4;
    private static final int SELECTOR_SEARCH_W = 90;
    private static final int SELECTOR_SEARCH_H = 12;
    private static final int SELECTOR_SCROLL_X = 175;
    private static final int SELECTOR_SCROLL_Y = 18;
    private static final int SELECTOR_SCROLL_TRACK_H = 162;
    private static final int SELECTOR_SCROLL_THUMB_W = 12;
    private static final int SELECTOR_SCROLL_THUMB_H = 15;

    private static GuiRemoteMonitor activeInstance;

    private final long deviceId;

    private int guiLeft;
    private int guiTop;
    private int selectorLeft;
    private int selectorTop;

    private GuiTabButton refreshIntervalBtn;
    private GuiTabButton slidingWindowBtn;

    private boolean selectorOpen;
    private int selectorTargetIndex = -1;
    private int selectorScrollOffset;
    private int selectorHoveredSlot = -1;
    private boolean selectorDragging;
    // Preserve the last filter term so switching slots does not reset the selector search.
    private String selectorSearchText = "";
    private GuiTextField selectorSearchField;
    private List<MonitoredResource> selectorResources = new ArrayList<>();
    private List<MonitoredResource> filteredResources = new ArrayList<>();

    public GuiRemoteMonitor(long deviceId) {
        this.deviceId = deviceId;
    }

    public static void receiveSelectorResources(long deviceId, List<MonitoredResource> resources) {
        if (activeInstance == null || activeInstance.deviceId != deviceId) return;
        activeInstance.selectorResources = new ArrayList<>(resources);
        activeInstance.filterSelectorResources();
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
        this.selectorLeft = (this.width - SELECTOR_WIDTH) / 2;
        this.selectorTop = (this.height - SELECTOR_HEIGHT) / 2;

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
        if (this.selectorOpen && this.selectorSearchField != null) this.selectorSearchField.updateCursorCounter();
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
        if (!this.selectorOpen) drawSlots(mouseX, mouseY);

        resetGuiRenderState();
        super.drawScreen(mouseX, mouseY, partialTicks);

        if (this.selectorOpen) {
            drawSelectorModal(mouseX, mouseY);
            drawSelectorTooltip(mouseX, mouseY);
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
        String interval = PollingRateUtils.format(RemoteMonitorClientState.getOrCreateState(this.deviceId).getRefreshRate());
        drawTimingTooltip(
            this.refreshIntervalBtn,
            I18n.format("gui.ae2powertools.remote_monitor.refresh_interval.tooltip", interval),
            I18n.format("gui.ae2powertools.remote_monitor.refresh_interval.description"),
            mouseX,
            mouseY);
    }

    private void drawSlidingWindowTooltip(int mouseX, int mouseY) {
        String interval = PollingRateUtils.format(RemoteMonitorClientState.getOrCreateState(this.deviceId).getSlidingWindow());
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
        if (this.selectorOpen) {
            handleSelectorClick(mouseX, mouseY, mouseButton);
            return;
        }

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
        if (this.selectorOpen) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                closeSelector();
                return;
            }

            if (this.selectorSearchField != null && this.selectorSearchField.textboxKeyTyped(typedChar, keyCode)) {
                filterSelectorResources();
                return;
            }

            return;
        }

        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();

        if (!this.selectorOpen) return;

        int scroll = Mouse.getEventDWheel();
        if (scroll == 0) return;

        this.selectorScrollOffset -= Integer.signum(scroll);
        clampSelectorScroll();
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        if (this.selectorOpen) {
            this.selectorDragging = false;
            return;
        }

        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (this.selectorOpen) {
            if (this.selectorDragging && clickedMouseButton == 0) {
                updateSelectorScrollFromMouse(mouseY);
            }

            return;
        }

        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }

    private void openSelector(int targetIndex) {
        this.selectorOpen = true;
        this.selectorTargetIndex = targetIndex;
        this.selectorScrollOffset = 0;
        this.selectorHoveredSlot = -1;
        this.selectorDragging = false;

        this.selectorResources = new ArrayList<>(RemoteMonitorClientState.getOrCreateState(this.deviceId).getSelectorResources());
        this.filteredResources = new ArrayList<>();

        this.selectorSearchField = new GuiTextField(100, this.fontRenderer,
            this.selectorLeft + SELECTOR_SEARCH_X,
            this.selectorTop + SELECTOR_SEARCH_Y,
            SELECTOR_SEARCH_W,
            SELECTOR_SEARCH_H);
        this.selectorSearchField.setMaxStringLength(50);
        this.selectorSearchField.setEnableBackgroundDrawing(true);
        this.selectorSearchField.setTextColor(0xFFFFFF);
        this.selectorSearchField.setText(this.selectorSearchText);
        this.selectorSearchField.setFocused(true);

        filterSelectorResources();
        PowerToolsNetwork.INSTANCE.sendToServer(new PacketRemoteMonitorRequestContents(this.deviceId));
    }

    private void filterSelectorResources() {
        String search = this.selectorSearchField != null ? this.selectorSearchField.getText() : this.selectorSearchText;
        this.selectorSearchText = search;
        search = search.toLowerCase().trim();

        if (search.isEmpty()) {
            this.filteredResources = new ArrayList<>(this.selectorResources);
        } else {
            this.filteredResources = new ArrayList<>();
            for (MonitoredResource resource : this.selectorResources) {
                if (resource.getDisplayName().toLowerCase().contains(search)) this.filteredResources.add(resource);
            }
        }

        clampSelectorScroll();
    }

    private void clampSelectorScroll() {
        int maxScroll = getSelectorMaxScroll();
        this.selectorScrollOffset = Math.max(0, Math.min(this.selectorScrollOffset, maxScroll));
    }

    private int getSelectorMaxScroll() {
        int totalRows = (this.filteredResources.size() + SELECTOR_COLS - 1) / SELECTOR_COLS;
        return Math.max(0, totalRows - SELECTOR_ROWS);
    }

    private boolean isMouseOverSelectorScrollbar(int mouseX, int mouseY) {
        int scrollX = this.selectorLeft + SELECTOR_SCROLL_X;
        int scrollY = this.selectorTop + SELECTOR_SCROLL_Y;

        return mouseX >= scrollX && mouseX < scrollX + SELECTOR_SCROLL_THUMB_W
            && mouseY >= scrollY && mouseY < scrollY + SELECTOR_SCROLL_TRACK_H;
    }

    private void updateSelectorScrollFromMouse(int mouseY) {
        int maxScroll = getSelectorMaxScroll();
        if (maxScroll <= 0) return;

        int scrollY = this.selectorTop + SELECTOR_SCROLL_Y;
        int thumbRange = SELECTOR_SCROLL_TRACK_H - SELECTOR_SCROLL_THUMB_H;
        if (thumbRange <= 0) {
            this.selectorScrollOffset = 0;
            return;
        }

        // Map the cursor onto the visible track so clicks and drags both reposition the thumb.
        float ratio = (float) (mouseY - scrollY - SELECTOR_SCROLL_THUMB_H / 2.0F) / thumbRange;
        this.selectorScrollOffset = Math.round(ratio * maxScroll);
        clampSelectorScroll();
    }

    private void drawSelectorModal(int mouseX, int mouseY) {
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        this.mc.getTextureManager().bindTexture(SELECTOR_BACKGROUND);
        drawTexturedModalRect(this.selectorLeft, this.selectorTop, 0, 0, SELECTOR_WIDTH, SELECTOR_HEIGHT);

        this.fontRenderer.drawString(I18n.format("gui.ae2powertools.remote_monitor.selector.title"),
            this.selectorLeft + 8, this.selectorTop + 6, 0x000000);
        if (this.selectorSearchField != null) this.selectorSearchField.drawTextBox();

        this.selectorHoveredSlot = -1;
        for (int row = 0; row < SELECTOR_ROWS; row++) {
            for (int col = 0; col < SELECTOR_COLS; col++) {
                int displaySlot = row * SELECTOR_COLS + col;
                int index = (this.selectorScrollOffset + row) * SELECTOR_COLS + col;
                int slotX = this.selectorLeft + SELECTOR_GRID_X + col * SELECTOR_SLOT_SIZE;
                int slotY = this.selectorTop + SELECTOR_GRID_Y + row * SELECTOR_SLOT_SIZE;

                boolean hovered = mouseX >= slotX && mouseX < slotX + SELECTOR_SLOT_SIZE
                    && mouseY >= slotY && mouseY < slotY + SELECTOR_SLOT_SIZE;
                if (hovered) {
                    this.selectorHoveredSlot = displaySlot;
                    drawRect(slotX + 1, slotY + 1, slotX + SELECTOR_SLOT_SIZE - 1, slotY + SELECTOR_SLOT_SIZE - 1,
                        0x80FFFFFF);
                }

                if (index < 0 || index >= this.filteredResources.size()) continue;

                MonitoredResource resource = this.filteredResources.get(index);
                MonitoredResourceRenderer.renderIcon(resource, slotX + 1, slotY + 1, 16);
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            }
        }

        drawSelectorScrollbar();
        GlStateManager.enableDepth();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void drawSelectorScrollbar() {
        int scrollX = this.selectorLeft + SELECTOR_SCROLL_X;
        int scrollY = this.selectorTop + SELECTOR_SCROLL_Y;

        this.mc.getTextureManager().bindTexture(SCROLLBAR_TEXTURE);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        int maxScroll = getSelectorMaxScroll();
        if (maxScroll <= 0) {
            drawTexturedModalRect(scrollX, scrollY, 244, 0, SELECTOR_SCROLL_THUMB_W, SELECTOR_SCROLL_THUMB_H);
            return;
        }

        int thumbY = scrollY + (SELECTOR_SCROLL_TRACK_H - SELECTOR_SCROLL_THUMB_H) * this.selectorScrollOffset / maxScroll;
        drawTexturedModalRect(scrollX, thumbY, 232, 0, SELECTOR_SCROLL_THUMB_W, SELECTOR_SCROLL_THUMB_H);
    }

    private void drawSelectorTooltip(int mouseX, int mouseY) {
        if (this.selectorHoveredSlot < 0) return;

        int row = this.selectorHoveredSlot / SELECTOR_COLS;
        int col = this.selectorHoveredSlot % SELECTOR_COLS;
        int index = (this.selectorScrollOffset + row) * SELECTOR_COLS + col;
        if (index < 0 || index >= this.filteredResources.size()) return;

        List<String> tooltip = getResourceTooltip(this.filteredResources.get(index), mouseX, mouseY);

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GuiUtils.drawHoveringText(tooltip, mouseX, mouseY, this.width, this.height, -1, this.fontRenderer);
    }

    private List<String> getResourceTooltip(MonitoredResource resource, int mouseX, int mouseY) {
        IAEStack<?> stack = resource.getStack();
        if (stack instanceof IAEItemStack) {
            ItemStack itemStack = ((IAEItemStack) stack).getDefinition();

            return new ArrayList<>(this.getItemToolTip(itemStack));
        }

        return JeiTooltipBridge.buildTooltip(resource);
    }

    private void closeSelector() {
        if (this.selectorSearchField != null) {
            this.selectorSearchText = this.selectorSearchField.getText();
            this.selectorSearchField.setFocused(false);
            this.selectorSearchField = null;
        }

        this.selectorOpen = false;
        this.selectorHoveredSlot = -1;
        this.selectorDragging = false;
    }

    private boolean isMouseOverSelectorSearchField(int mouseX, int mouseY) {
        int searchLeft = this.selectorLeft + SELECTOR_SEARCH_X;
        int searchTop = this.selectorTop + SELECTOR_SEARCH_Y;

        return mouseX >= searchLeft && mouseX < searchLeft + SELECTOR_SEARCH_W
            && mouseY >= searchTop && mouseY < searchTop + SELECTOR_SEARCH_H;
    }

    private boolean handleSelectorSearchFieldClick(int mouseX, int mouseY, int mouseButton) {
        if (this.selectorSearchField == null) return false;

        if (!isMouseOverSelectorSearchField(mouseX, mouseY)) {
            this.selectorSearchField.mouseClicked(mouseX, mouseY, mouseButton);
            return false;
        }

        if (mouseButton == 1) {
            this.selectorSearchField.setText("");
            this.selectorSearchField.setFocused(true);
            filterSelectorResources();
            return true;
        }

        this.selectorSearchField.mouseClicked(mouseX, mouseY, mouseButton);
        return true;
    }

    private void handleSelectorClick(int mouseX, int mouseY, int mouseButton) {
        if (mouseX < this.selectorLeft || mouseX >= this.selectorLeft + SELECTOR_WIDTH
                || mouseY < this.selectorTop || mouseY >= this.selectorTop + SELECTOR_HEIGHT) {
            closeSelector();
            return;
        }

        if (handleSelectorSearchFieldClick(mouseX, mouseY, mouseButton)) return;
        if (mouseButton == 0 && isMouseOverSelectorScrollbar(mouseX, mouseY)) {
            this.selectorDragging = true;
            updateSelectorScrollFromMouse(mouseY);
            return;
        }

        if (mouseButton != 0 || this.selectorHoveredSlot < 0) return;

        int row = this.selectorHoveredSlot / SELECTOR_COLS;
        int col = this.selectorHoveredSlot % SELECTOR_COLS;
        int index = (this.selectorScrollOffset + row) * SELECTOR_COLS + col;
        if (index < 0 || index >= this.filteredResources.size()) return;

        PowerToolsNetwork.INSTANCE.sendToServer(new PacketRemoteMonitorSelectSlot(
            this.deviceId,
            this.selectorTargetIndex,
            this.filteredResources.get(index)));
        closeSelector();
    }
}