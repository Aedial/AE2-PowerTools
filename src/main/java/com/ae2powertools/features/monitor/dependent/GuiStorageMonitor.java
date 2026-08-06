package com.ae2powertools.features.monitor.dependent;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.lwjgl.input.Keyboard;

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
import com.ae2powertools.features.monitor.MonitoredEntry;
import com.ae2powertools.features.monitor.MonitoredResource;
import com.ae2powertools.features.monitor.client.MonitoredResourceRenderer;
import com.ae2powertools.features.monitor.dependent.widgets.StorageMonitorEntryGridWidget;
import com.ae2powertools.features.monitor.emitter.EmitterRedstonePower;
import com.ae2powertools.features.monitor.emitter.IEmitterCardHost;
import com.ae2powertools.features.monitor.emitter.IEmitterRedstoneHost;
import com.ae2powertools.integration.jei.JeiTooltipBridge;
import com.ae2powertools.network.PacketModifyStorageMonitorUpgradeSlot;
import com.ae2powertools.network.PacketOpenStorageMonitorPollingRate;
import com.ae2powertools.network.PacketRequestMonitorContents;
import com.ae2powertools.network.PacketSelectMonitorContent;
import com.ae2powertools.network.PacketSetEmitterRedstonePower;
import com.ae2powertools.network.PacketSetEmitterRedstoneStrength;
import com.ae2powertools.network.PacketSetHysteresisMode;
import com.ae2powertools.network.PacketSetMatchMode;
import com.ae2powertools.network.PacketToggleAlarmRegistration;
import com.ae2powertools.network.PacketUpdateMonitorEntry;
import com.ae2powertools.network.PowerToolsNetwork;
import com.ae2powertools.util.FormatUtil;
import com.ae2powertools.util.upgrade.ISelectableUpgradeInventory;
import com.ae2powertools.util.upgrade.UpgradePickerGuiHelper;
import com.ae2powertools.widgets.Ae2Button;
import com.ae2powertools.widgets.BeveledButton;
import com.ae2powertools.widgets.TabButton;
import com.ae2powertools.widgets.SearchableGridSelectorWidget;
import com.ae2powertools.widgets.WidgetAnchor;
import com.ae2powertools.widgets.WidgetGui;


/**
 * Shared GUI for both ME Storage Level Emitter and ME Storage Display.
 * <p>
 * Layout (modeled after {@code GuiBetterLevelMaintainer}):
 * - Fixed 4 cols x 6 rows = 24 entry cells. The grid never grows or shrinks.
 *   Empty cells are still interactive: clicking the left half of an empty cell
 *   opens the content selector to assign a new resource at that index.
 * - Each cell is rendered with an INNER area of 50x22 plus a 1 px right/bottom
 *   border that is left uncolored (so cells don't visually merge). Cell pitch
 *   is therefore 51x23.
 * - Per-cell zones:
 *     - Icon zone:   16x16 at local (3, 3), with the current quantity rendered over it
 *     - Comparison:  10x10 at local (20, 6), straddles left/right halves
 *     - Thresholds:  16x16+ at local (31, 3), one threshold in normal mode and
 *       top/bottom increasing/decreasing thresholds in hysteresis mode
 *   Background tint covers the inner 50x22 area:
 *     - Grey       if entry is disabled
 *     - Translucent green if condition met
 *     - Translucent red   if condition not met
 *   Empty cells get no tint.
 * <p>
 * Click hit detection (priority order, left-click only):
 *   1. If a count field is visible and the click is OUTSIDE it: dismiss-and-save first,
 *      then continue with normal hit detection on the underlying click.
 *   2. Inside the comparison 10x10 region: cycle the comparison.
 *   3. Otherwise inside the left half (localX < 25): open content selector for that cell.
 *   4. Otherwise inside the threshold side (localX >= 25): show the count field for that
 *      threshold. Hysteresis mode splits that side into upper / lower halves.
 * <p>
 * Hover feedback uses the same three-zone partition: whichever zone the mouse
 * is in gets a 0x40FFFFFF white overlay. This makes interactive areas obvious.
 * <p>
 * Right-click on a filled cell toggles the entry's enabled flag.
 * <p>
 * The Storage Display variant ({@link MonitorHostType#DISPLAY}) shares the SAME interactive
 * surface as the Emitter.
 * <p>
 * Hover tooltips summarize the resource, exact values, and per-cell controls.
 */
@SideOnly(Side.CLIENT)
public class GuiStorageMonitor extends WidgetGui {

    private static final ResourceLocation BACKGROUND = new ResourceLocation(
        Tags.MODID, "textures/guis/emitter_gui.png");
    private static final ResourceLocation HYSTERESIS_ICON = new ResourceLocation(
        Tags.MODID, "textures/guis/hysteresis_icon.png");
    private static final ResourceLocation REDSTONE_STRENGTH_PANEL = new ResourceLocation(
        Tags.MODID, "textures/guis/redstone_strength_panel.png");

    // --- Main GUI dimensions ---
    private static final int GUI_WIDTH = 221;
    private static final int GUI_HEIGHT = 184;

    // --- Side buttons (sits OUTSIDE the GUI on the left) ---
    private static final int SIDE_BTN_SIZE = 16;
    private static final int SIDE_BTN_X_OFFSET = -SIDE_BTN_SIZE - 2;
    private static final int SIDE_BTN_SPACING = 4;
    private static final int MATCH_MODE_BTN_Y = 4;
    private static final int TAB_BTN_SIZE = 22;
    private static final int TOP_BUTTON_GAP = 2;

    private static final int POLLING_ICON_INDEX = 5 * 16 + 2;

    // --- Emitter strength panel (drawn outside the GUI on the right) ---
    private static final int STRENGTH_PANEL_OFFSET_X = 2;
    private static final int STRENGTH_PANEL_PADDING = 3;
    private static final int STRENGTH_PANEL_WIDTH = 72;
    private static final int STRENGTH_PANEL_HEIGHT = 101;  // Panel + upgrade slots
    private static final int STRENGTH_BTN_Y_START = 12;
    private static final int STRENGTH_BTN_X_OFFSET = 40;
    private static final int STRENGTH_BTN_Y_OFFSET = 16;
    private static final int STRENGTH_BTN_WIDTH = 25;
    private static final int STRENGTH_BTN_HEIGHT = 14;

    private final ContainerStorageMonitor container;

    /** Emitter-only redstone-strength button. */
    private final Ae2Button redstoneSignalBtn = new Ae2Button(0, 0, SIDE_BTN_SIZE);

    /** Wrench tab button. */
    private final TabButton pollingRateBtn = new TabButton(0, 0, POLLING_ICON_INDEX,
                                                           I18n.format("gui.ae2powertools.storage_emitter.polling_rate.title"));

    /** Alarm-only registration toggle button shown next to the polling-rate tab. */
    private final Ae2Button alarmRegistrationBtn = new Ae2Button(0, 0, SIDE_BTN_SIZE);

    /** Match-mode button shown on the left side of the GUI. */
    private final Ae2Button matchModeButton = new Ae2Button(0, 0, SIDE_BTN_SIZE);

    /** Hysteresis-mode button shown below the match-mode button. */
    private final Ae2Button hysteresisButton = new Ae2Button(0, 0, SIDE_BTN_SIZE);

    /** Compact strength controls drawn in the right-side panel. */
    private int emitterStrengthPanelX;
    private int emitterStrengthPanelY;
    private boolean emitterStrengthTextHovered = false;

    private final SearchableGridSelectorWidget<MonitoredResource> selectorWidget;
    private final StorageMonitorEntryGridWidget entryGrid;

    /** Index to replace the resource at; -1 means "append a new entry". */
    private int selectorTargetIndex = -1;

    private final UpgradePickerGuiHelper upgradePicker = new UpgradePickerGuiHelper();

    /** Static reference for receiving async packet data from the server. */
    private static GuiStorageMonitor activeInstance;

    public GuiStorageMonitor(ContainerStorageMonitor container) {
        super(container, GUI_WIDTH, GUI_HEIGHT, BACKGROUND);
        this.container = container;

        this.selectorWidget = new SearchableGridSelectorWidget<>(
            this,
            "gui.ae2powertools.storage_emitter.select_content",
            MonitoredResource::getDisplayName,
            (context, resource, x, y) -> {
                MonitoredResourceRenderer.renderIcon(resource, x, y, 16);
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            },
            this::renderSelectorResourceTooltip,
            this::selectMonitorResource);
        upgradePicker.configure(this, this::getSelectableUpgradeInventory, () -> mc.player.inventory, this::installUpgradeFromPlayerSlot);
        registerModal(upgradePicker, WidgetAnchor.SCREEN_CENTER, 0, 0);

        this.entryGrid = new StorageMonitorEntryGridWidget(this, container, this::openSelector, this::sendEntryUpdate);

        redstoneSignalBtn.setVisible(container.supportsEmitterRedstone());
        redstoneSignalBtn.setOnClick(this::toggleRedstoneSignal);
        redstoneSignalBtn.setTooltipProvider(this::buildRedstonePowerTooltip);

        matchModeButton.setVisible(container.supportsMatchMode());
        matchModeButton.setOnClick(this::cycleMatchMode);
        matchModeButton.setTooltipProvider(this::buildMatchModeTooltip);

        hysteresisButton.setVisible(container.getHost().supportsHysteresis());
        hysteresisButton.setOnClick(this::toggleHysteresisMode);
        hysteresisButton.setTooltipProvider(this::buildHysteresisTooltip);

        alarmRegistrationBtn.setVisible(container.supportsPlayerRegistration());
        alarmRegistrationBtn.setOnClick(this::toggleAlarmRegistration);
        alarmRegistrationBtn.setTooltipProvider(this::buildAlarmRegistrationTooltip);

        pollingRateBtn.setOnClick(this::openPollingRateGui);
        pollingRateBtn.setTooltipProvider(this::buildPollingRateTooltip);

        int nextSideButtonY = MATCH_MODE_BTN_Y;

        if (matchModeButton.isVisible()) {
            registerWidget(matchModeButton, SIDE_BTN_X_OFFSET, nextSideButtonY);
            nextSideButtonY += SIDE_BTN_SIZE + SIDE_BTN_SPACING;
        }

        if (redstoneSignalBtn.isVisible()) {
            registerEmitterStrengthButtons();

            registerWidget(redstoneSignalBtn, SIDE_BTN_X_OFFSET, nextSideButtonY);
            nextSideButtonY += SIDE_BTN_SIZE + SIDE_BTN_SPACING;
        }

        if (hysteresisButton.isVisible()) {
            registerWidget(hysteresisButton, SIDE_BTN_X_OFFSET, nextSideButtonY);
            nextSideButtonY += SIDE_BTN_SIZE + SIDE_BTN_SPACING;
        }

        if (alarmRegistrationBtn.isVisible()) {
            registerWidget(alarmRegistrationBtn, GUI_WIDTH - TAB_BTN_SIZE + 1 - SIDE_BTN_SIZE - TOP_BUTTON_GAP, 1);
            nextSideButtonY += SIDE_BTN_SIZE + SIDE_BTN_SPACING;
        }

        // ... and any further side button can be added here, using the same pattern

        registerWidget(pollingRateBtn, GUI_WIDTH - TAB_BTN_SIZE + 1, 0);
    }

    @Override
    protected void afterWidgetGuiInit() {
        activeInstance = this;

        emitterStrengthPanelX = guiLeft + GUI_WIDTH + STRENGTH_PANEL_OFFSET_X;
        emitterStrengthPanelY = guiTop;
        selectorWidget.setSearchText("");
        entryGrid.initGui(guiLeft, guiTop);
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();

        if (activeInstance == this) activeInstance = null;
    }

    @Override
    protected void updateWidgetGuiScreen() {
        entryGrid.updateScreen();
    }

    // ====================== DRAWING ======================

    @Override
    protected void drawWidgetGuiBackgroundContents(float partialTicks, int mouseX, int mouseY) {
        emitterStrengthPanelX = guiLeft + GUI_WIDTH + STRENGTH_PANEL_OFFSET_X;
        emitterStrengthPanelY = guiTop;

        syncEmitterRedstoneButton();
        syncAlarmRegistrationButton();
        syncSideButtons(mouseX, mouseY);

        if (container.supportsEmitterRedstone()) drawEmitterStrengthPanel(mouseX, mouseY);

        entryGrid.draw(guiLeft, guiTop, isManagedModalOpen(), mouseX, mouseY);

        upgradePicker.drawUpgradeSlotIcons(mc, guiLeft, guiTop, container.getEmitterCardSlots());

        // Reset GL state so the registered buttons do not inherit lighting/depth from prior draws.
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    protected void afterWidgetGuiWidgetsDraw() {
        GlStateManager.enableDepth();
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fontRenderer.drawString(
            I18n.format(container.getHostType().getTitleLangKey()),
            8, 6, 0x404040);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void drawWidgetGuiTooltips(int mouseX, int mouseY) {
        upgradePicker.drawUpgradeSlotHighlight(mouseX, mouseY, guiLeft, guiTop, container.getEmitterCardSlots());

        if (emitterStrengthTextHovered) drawRedstoneStrengthTooltip(mouseX, mouseY);

        upgradePicker.drawUpgradeSlotTooltip(
            getSelectableUpgradeInventory(),
            container.getEmitterCardSlots(),
            guiLeft,
            guiTop,
            mouseX,
            mouseY,
            width,
            height,
            fontRenderer);

        entryGrid.drawTooltip(guiLeft, guiTop, mouseX, mouseY);
    }

    @Override
    protected void renderHoveredToolTip(int mouseX, int mouseY) {
        if (isManagedModalOpen()) return;
        if (upgradePicker.getUpgradeSlotAt(mouseX, mouseY, guiLeft, guiTop, container.getEmitterCardSlots()) >= 0) return;

        super.renderHoveredToolTip(mouseX, mouseY);
    }

    @Override
    protected boolean isPointInRegion(int rectX, int rectY, int rectWidth, int rectHeight, int pointX, int pointY) {
        if (upgradePicker.isUpgradeSlotRegion(container.getEmitterCardSlots(), rectX, rectY, rectWidth, rectHeight)) return false;

        return super.isPointInRegion(rectX, rectY, rectWidth, rectHeight, pointX, pointY);
    }

    private void syncSideButtons(int mouseX, int mouseY) {
        if (matchModeButton.isVisible()) {
            matchModeButton.setCenteredLabel(container.getSyncMatchMode().getSymbol(), 0xFFFFFFFF);
        }

        if (hysteresisButton.isVisible()) {
            int iconX = (container.isSyncHysteresisEnabled() ? 0 : 1) * SIDE_BTN_SIZE;
            int iconY = (hysteresisButton.contains(mouseX, mouseY) ? 1 : 0) * SIDE_BTN_SIZE;
            hysteresisButton.setScaledTextureIcon(HYSTERESIS_ICON, iconX, iconY, SIDE_BTN_SIZE * 2, SIDE_BTN_SIZE * 2);
        }
    }

    private void drawRedstoneStrengthTooltip(int mouseX, int mouseY) {
        List<String> tt = new ArrayList<>();
        tt.add(I18n.format("gui.ae2powertools.storage_emitter.strength.tooltip"));
        tt.add("");
        tt.add("§7" + I18n.format("gui.ae2powertools.storage_emitter.strength.description"));
        GuiUtils.drawHoveringText(tt, mouseX, mouseY, width, height, -1, fontRenderer);
    }

    private void openSelector(int targetIndex) {
        selectorTargetIndex = targetIndex;
        selectorWidget.setSearchText("");
        selectorWidget.open(Collections.emptyList());
        PowerToolsNetwork.INSTANCE.sendToServer(new PacketRequestMonitorContents(container.getHost()));
    }

    /**
     * Called from the network thread (via PacketMonitorContentsSync) to provide
     * the list of available resources for the selector modal.
     */
    public static void handleContentsSync(List<MonitoredResource> resources) {
        if (activeInstance == null || !activeInstance.selectorWidget.isOpen()) return;

        activeInstance.selectorWidget.setItems(resources);
    }

    /**
     * Called from the network thread (via PacketStorageEntryStateSync) to push
     * live per-entry quantities and condition flags into the open GUI.
     * The arrays correspond 1:1 with the host's entries list.
     */
    public static void handleEntryStateSync(long[] quantities, boolean[] conditions) {
        if (activeInstance == null) return;

        List<MonitoredEntry> entries = activeInstance.container.getHost().getEntries();
        int n = Math.min(entries.size(), Math.min(quantities.length, conditions.length));
        for (int i = 0; i < n; i++) {
            entries.get(i).setLastQuantity(quantities[i]);
            entries.get(i).setLastConditionMet(conditions[i]);
        }
    }

    /**
     * Called from the network thread (via PacketSyncMonitorEntries) to mirror the server's
     * entry list onto the client host. Required because the host TileEntity / Part doesn't
     * push the entries list through the standard NBT update packet path - without this,
     * the GUI would never see entries added or modified server-side (e.g. after picking
     * a resource in the selector or cycling a comparator).
     * <p>
     * The transient lastQuantity / lastConditionMet fields are NOT serialized by
     * MonitoredEntry, so a fresh sync will not make all entries flicker.
     */
    public static void handleEntriesSync(List<MonitoredEntry> entries) {
        if (activeInstance == null) return;

        // Preserve last quantity / condition for entries whose resource at the same
        // index is unchanged. Resource-less placeholders are skipped: their lastQuantity
        // is meaningless without a resource to look up.
        List<MonitoredEntry> oldEntries = activeInstance.container.getHost().getEntries();
        for (int i = 0; i < entries.size() && i < oldEntries.size(); i++) {
            MonitoredEntry oldE = oldEntries.get(i);
            MonitoredEntry newE = entries.get(i);
            if (!oldE.hasResource() || !newE.hasResource()) continue;
            if (oldE.getResource().toKey().equals(newE.getResource().toKey())) {
                newE.setLastQuantity(oldE.getLastQuantity());
                newE.setLastConditionMet(oldE.isLastConditionMet());
            }
        }

        activeInstance.container.getHost().setEntries(entries);
    }

    // ====================== INPUT HANDLING ======================

    @Override
    protected boolean handleWidgetGuiMouseClicked(int mouseX, int mouseY, int mouseButton) {
        int emitterCardSlot = upgradePicker.getUpgradeSlotAt(mouseX, mouseY, guiLeft, guiTop, container.getEmitterCardSlots());
        if (emitterCardSlot >= 0) {
            handleEmitterCardSlotClick(emitterCardSlot, mouseButton);
            return true;
        }

        return false;
    }

    @Override
    protected void afterWidgetGuiMouseClicked(int mouseX, int mouseY, int mouseButton) {
        entryGrid.mouseClicked(guiLeft, guiTop, mouseX, mouseY, mouseButton);
    }

    private void handleEmitterCardSlotClick(int slotIndex, int mouseButton) {
        ISelectableUpgradeInventory inventory = getSelectableUpgradeInventory();
        if (inventory == null) return;

        ItemStack stack = inventory.getStackInSlot(slotIndex);
        if (mouseButton == 0) {
            upgradePicker.open(slotIndex);
            return;
        }

        if (mouseButton != 1 || stack.isEmpty()) return;

        PowerToolsNetwork.INSTANCE.sendToServer(new PacketModifyStorageMonitorUpgradeSlot(
            container.getHost(),
            PacketModifyStorageMonitorUpgradeSlot.Action.REMOVE,
            slotIndex,
            -1));
    }

    private void installUpgradeFromPlayerSlot(int selectedPlayerSlot) {
        PowerToolsNetwork.INSTANCE.sendToServer(new PacketModifyStorageMonitorUpgradeSlot(
            container.getHost(),
            PacketModifyStorageMonitorUpgradeSlot.Action.INSTALL_FROM_PLAYER_SLOT,
            upgradePicker.getTargetUpgradeSlot(),
            selectedPlayerSlot));
    }

    @Override
    protected boolean handleWidgetGuiKeyTyped(char typedChar, int keyCode) {
        return entryGrid.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void handleWidgetGuiMouseWheel(int mouseX, int mouseY, int scroll) {
        entryGrid.handleMouseWheel(guiLeft, guiTop, mouseX, mouseY, scroll, isShiftDown());
    }

    // ====================== STATE TRANSITIONS ======================

    private boolean isShiftDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
    }

    private ISelectableUpgradeInventory getSelectableUpgradeInventory() {
        if (!(container.getHost() instanceof IEmitterCardHost)) return null;

        return ((IEmitterCardHost) container.getHost()).getSelectableUpgradeInventory();
    }

    private void cycleMatchMode() {
        if (!container.supportsMatchMode()) return;

        MatchMode next = container.getSyncMatchMode().next();
        PowerToolsNetwork.INSTANCE.sendToServer(
            new PacketSetMatchMode(container.getHost(), next));
    }

    private void toggleHysteresisMode() {
        PowerToolsNetwork.INSTANCE.sendToServer(
            new PacketSetHysteresisMode(container.getHost(), !container.isSyncHysteresisEnabled()));
    }

    private void adjustEmitterStrength(int delta) {
        if (!container.supportsEmitterRedstone()) return;

        int currentStrength = container.getSyncEmitterStrength();
        int nextStrength = Math.max(
            IEmitterRedstoneHost.MIN_REDSTONE_STRENGTH,
            Math.min(IEmitterRedstoneHost.MAX_REDSTONE_STRENGTH, currentStrength + delta));
        if (nextStrength == currentStrength) return;

        PowerToolsNetwork.INSTANCE.sendToServer(
            new PacketSetEmitterRedstoneStrength(container.getHost(), nextStrength));
    }

    private void toggleRedstoneSignal() {
        PowerToolsNetwork.INSTANCE.sendToServer(
            new PacketSetEmitterRedstonePower(
                container.getHost(),
                container.getSyncEmitterRedstonePower().next()));
    }

    private void openPollingRateGui() {
        // Open the polling-rate sub-GUI via server round-trip so synced fields arrive in the new
        // container before the first client render frame.
        PowerToolsNetwork.INSTANCE.sendToServer(
            new PacketOpenStorageMonitorPollingRate(container.getHost()));
    }

    private void toggleAlarmRegistration() {
        PowerToolsNetwork.INSTANCE.sendToServer(
            new PacketToggleAlarmRegistration(container.getHost()));
    }

    private void syncEmitterRedstoneButton() {
        if (!redstoneSignalBtn.isVisible()) return;

        // Reuse AE2's redstone-emitter button art
        redstoneSignalBtn.setAe2TextureIcon(container.getSyncEmitterRedstonePower().getIconIndex());
    }

    private void syncAlarmRegistrationButton() {
        alarmRegistrationBtn.setCenteredLabel(container.isSyncPlayerRegistered() ? "-" : "+");
    }

    private void registerEmitterStrengthButtons() {
        int btnY = STRENGTH_PANEL_PADDING + STRENGTH_BTN_Y_START;

        for (int y = 1; y < 4; y++) {  // 1, 2, 3
            int btnX = GUI_WIDTH + STRENGTH_PANEL_OFFSET_X + STRENGTH_PANEL_PADDING;

            for (int x = -1; x < 2; x += 2) {  // -1, 1
                int delta = x * y * 5;
                BeveledButton button = new BeveledButton(0, 0, STRENGTH_BTN_WIDTH, formatDelta(delta));
                button.setOnClick(() -> adjustEmitterStrength(delta));
                registerWidget(button, btnX, btnY);
                btnX += STRENGTH_BTN_X_OFFSET;
            }

            btnY += STRENGTH_BTN_Y_OFFSET;
        }
    }

    private void drawEmitterStrengthPanel(int mouseX, int mouseY) {
        mc.getTextureManager().bindTexture(REDSTONE_STRENGTH_PANEL);
        drawTexturedModalRect(emitterStrengthPanelX, emitterStrengthPanelY, 0, 0, STRENGTH_PANEL_WIDTH, STRENGTH_PANEL_HEIGHT);

        String title = I18n.format("gui.ae2powertools.storage_emitter.strength");
        int titleX = emitterStrengthPanelX + (STRENGTH_PANEL_WIDTH - fontRenderer.getStringWidth(title)) / 2;
        int titleY = emitterStrengthPanelY + STRENGTH_PANEL_PADDING + 2;
        fontRenderer.drawString(title, titleX, titleY, 0xFF000000);

        // We want the value to be at centered on the middle button row
        String value = Integer.toString(container.getSyncEmitterStrength());
        int width = fontRenderer.getStringWidth(value);
        int valueX = emitterStrengthPanelX + (STRENGTH_PANEL_WIDTH - width) / 2;
        int valueY = emitterStrengthPanelY + STRENGTH_PANEL_PADDING + STRENGTH_BTN_Y_START
                   + STRENGTH_BTN_Y_OFFSET + (STRENGTH_BTN_HEIGHT - fontRenderer.FONT_HEIGHT) / 2 + 1;
        fontRenderer.drawString(value, valueX, valueY, 0xFF000000);

        boolean emitterStrengthTitleHovered = mouseX >= titleX && mouseX < titleX + fontRenderer.getStringWidth(title)
            && mouseY >= titleY && mouseY < titleY + fontRenderer.FONT_HEIGHT;
        boolean emitterStrengthValueHovered = mouseX >= valueX && mouseX < valueX + width
            && mouseY >= valueY && mouseY < valueY + fontRenderer.FONT_HEIGHT;
        emitterStrengthTextHovered = emitterStrengthTitleHovered || emitterStrengthValueHovered;
    }

    private void sendEntryUpdate(int idx, ComparisonMode comparison, long threshold, long lowerThreshold, boolean enabled) {
        PowerToolsNetwork.INSTANCE.sendToServer(
            new PacketUpdateMonitorEntry(container.getHost(), idx, comparison, threshold, lowerThreshold, enabled));
    }

    // ====================== JEI INTEGRATION ======================

    /**
     * Exclusion zones for JEI's overlay so its sidebar doesn't paint over our
     * out-of-frame side button. Mirrors GuiBetterLevelMaintainer's helper of the same name.
     * <p>
     * The side buttons sit OUTSIDE guiLeft to the left, so JEI thinks that area is
     * free space and happily covers it with its filter UI; we have to declare them
     * explicitly. The polling-rate wrench tab button is inside the GUI bounds and
     * doesn't need to be listed.
     */
    public List<Rectangle> getJEIExclusionArea() {
        List<Rectangle> areas = new ArrayList<>();

        if (matchModeButton.isVisible()) areas.add(matchModeButton.getBounds());
        if (hysteresisButton.isVisible()) areas.add(hysteresisButton.getBounds());
        if (redstoneSignalBtn.isVisible()) areas.add(redstoneSignalBtn.getBounds());

        if (container.supportsEmitterRedstone()) {
            areas.add(new Rectangle(emitterStrengthPanelX, emitterStrengthPanelY,
                                    STRENGTH_PANEL_WIDTH, STRENGTH_PANEL_HEIGHT));
        }

        return areas;
    }

    private List<String> buildMatchModeTooltip() {
        List<String> tooltip = new ArrayList<>();
        tooltip.add(I18n.format("gui.ae2powertools.storage_emitter.match_mode", container.getSyncMatchMode().name()));
        tooltip.add("§7" + I18n.format("gui.ae2powertools.storage_emitter.match_mode.click_toggle"));
        return tooltip;
    }

    private List<String> buildHysteresisTooltip() {
        String prefix = "gui.ae2powertools.storage_emitter.hysteresis";
        List<String> tooltip = new ArrayList<>();
        tooltip.add(I18n.format(
            prefix,
            I18n.format(prefix + (container.isSyncHysteresisEnabled() ? ".on" : ".off"))));
        tooltip.add("§7" + I18n.format(prefix + ".click_toggle"));
        return tooltip;
    }

    private List<String> buildRedstonePowerTooltip() {
        EmitterRedstonePower redstonePower = container.getSyncEmitterRedstonePower();
        List<String> tooltip = new ArrayList<>();
        tooltip.add(I18n.format(
            "gui.ae2powertools.storage_emitter.redstone_signal",
            I18n.format(redstonePower.getLangKey())));
        tooltip.add("§7" + I18n.format("gui.ae2powertools.storage_emitter.redstone_signal.click_toggle"));
        return tooltip;
    }

    private List<String> buildAlarmRegistrationTooltip() {
        String prefix = "gui.ae2powertools.level_monitor_alarm.registration";
        List<String> tooltip = new ArrayList<>();
        tooltip.add(I18n.format(container.isSyncPlayerRegistered() ? prefix + ".registered" : prefix + ".unregistered"));
        tooltip.add("§7" + I18n.format(container.isSyncPlayerRegistered()
            ? prefix + ".click_unregister"
            : prefix + ".click_register"));
        return tooltip;
    }

    private List<String> buildPollingRateTooltip() {
        String interval = FormatUtil.formatTimeTicks(container.refreshRate);
        List<String> tooltip = new ArrayList<>();
        tooltip.add("§e" + I18n.format("gui.ae2powertools.storage_emitter.polling_rate.tooltip", interval));
        tooltip.add("");
        tooltip.add("§7" + I18n.format("gui.ae2powertools.storage_emitter.polling_rate.description"));
        return tooltip;
    }

    private void renderSelectorResourceTooltip(MonitoredResource resource, int mouseX, int mouseY) {
        IAEStack<?> stack = resource.getStack();
        if (stack instanceof IAEItemStack) {
            ItemStack itemStack = ((IAEItemStack) stack).getDefinition();
            this.renderToolTip(itemStack, mouseX, mouseY);
            return;
        }

        GuiUtils.drawHoveringText(
            JeiTooltipBridge.buildTooltip(resource),
            mouseX,
            mouseY,
            width,
            height,
            -1,
            fontRenderer);
    }

    private void selectMonitorResource(MonitoredResource selected) {
        PowerToolsNetwork.INSTANCE.sendToServer(
            new PacketSelectMonitorContent(container.getHost(), selected, selectorTargetIndex));
    }

    private static String formatDelta(int delta) {
        return delta > 0 ? "+" + delta : Integer.toString(delta);
    }
}
