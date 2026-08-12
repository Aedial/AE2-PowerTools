package com.ae2powertools.features.maintainer.widgets;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.lwjgl.input.Keyboard;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.client.config.GuiUtils;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.storage.data.IAEItemStack;
import appeng.client.render.StackSizeRenderer;

import com.ae2powertools.Tags;
import com.ae2powertools.features.maintainer.MaintainerEntry;
import com.ae2powertools.util.Ae2FluidCraftingCompat;
import com.ae2powertools.util.FormatUtil;
import com.ae2powertools.widgets.AbstractModalGui;
import com.ae2powertools.widgets.FormattedNumberFieldHelper;
import com.ae2powertools.widgets.SearchableGridSelectorWidget;
import com.ae2powertools.widgets.WidgetContext;


/**
 * Feature-local entry editor overlay for the Better Level Maintainer.
 */
@SideOnly(Side.CLIENT)
public class MaintainerEntryEditorOverlay extends AbstractModalGui {

    public interface RecipeSelectionHandler {

        void select(int entryIndex, IAEItemStack selectedItem);
    }

    public interface SaveHandler {

        void save(int entryIndex, IAEItemStack targetItem, long targetQty, long batchSize, int frequency, boolean enabled);
    }

    private static final ResourceLocation MODAL_BACKGROUND = new ResourceLocation(
        Tags.MODID, "textures/guis/maintainer_modal.png");

    private static final int MODAL_WIDTH = 176;
    private static final int MODAL_HEIGHT = 107;

    private final WidgetContext context;
    private final Supplier<List<IAEItemStack>> craftableItemsSupplier;
    private final RecipeSelectionHandler recipeSelectionHandler;
    private final SaveHandler saveHandler;
    private final StackSizeRenderer stackSizeRenderer = new StackSizeRenderer();
    private final SearchableGridSelectorWidget<IAEItemStack> selector;
    private final List<GuiButton> frequencyButtons = new ArrayList<>();

    private int entryIndex = -1;
    private MaintainerEntry workingEntry;
    private int modalLeft;
    private int modalTop;
    private int lastFrequency;
    private GuiTextField targetField;
    private GuiTextField batchField;
    private GuiTextField frequencyField;

    public MaintainerEntryEditorOverlay(WidgetContext context, Supplier<List<IAEItemStack>> craftableItemsSupplier,
            RecipeSelectionHandler recipeSelectionHandler, SaveHandler saveHandler) {
        super(MODAL_WIDTH, MODAL_HEIGHT);

        this.context = context;
        this.craftableItemsSupplier = craftableItemsSupplier;
        this.recipeSelectionHandler = recipeSelectionHandler;
        this.saveHandler = saveHandler;
        this.selector = new SearchableGridSelectorWidget<>(
            context,
            "gui.ae2powertools.maintainer.selector.title",
            item -> Ae2FluidCraftingCompat.getDisplayStack(item).getDisplayName(),
            this::renderSelectorEntry,
            this::renderSelectorTooltip,
            this::selectRecipe);
    }

    public void initGui() {
        modalLeft = (context.getWidgetWidth() - MODAL_WIDTH) / 2;
        modalTop = (context.getWidgetHeight() - MODAL_HEIGHT) / 2;
        setPosition(modalLeft, modalTop);
        selector.initGui();

        if (!isOpen() || workingEntry == null) return;

        initFields();
    }

    public void updateScreen() {
        if (!isOpen()) return;

        if (targetField != null) targetField.updateCursorCounter();
        if (batchField != null) batchField.updateCursorCounter();
        if (frequencyField != null) frequencyField.updateCursorCounter();
        selector.updateScreen();
    }

    public void open(int entryIndex, MaintainerEntry entry) {
        if (entry == null) return;

        this.entryIndex = entryIndex;
        this.workingEntry = entry.copy();
        this.lastFrequency = workingEntry.getFrequencySeconds();
        super.open();

        Keyboard.enableRepeatEvents(true);
        initFields();
    }

    public void close(boolean save) {
        if (!isOpen()) return;

        if (save && workingEntry != null) {
            long targetQty = MaintainerEntry.parseQuantity(targetField.getText());
            if (targetQty < 0) targetQty = workingEntry.getTargetQuantity();

            long batchSize = MaintainerEntry.parseQuantity(batchField.getText());
            if (batchSize < 1) batchSize = workingEntry.getBatchSize();

            // Parse frequency from field text, fall back to lastFrequency if invalid.
            int frequency = MaintainerEntry.parseTime(frequencyField.getText());
            if (frequency < 1) frequency = lastFrequency;

            saveHandler.save(
                entryIndex,
                workingEntry.getTargetItem(),
                targetQty,
                batchSize,
                frequency,
                workingEntry.isEnabled());
        }

        selector.close();
        super.close();
        entryIndex = -1;
        workingEntry = null;
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public void close() {
        close(true);
    }

    public void draw(int mouseX, int mouseY, float partialTicks) {
        if (!isOpen()) return;

        // The selector replaces the editor instead of drawing on top of it. Keeping only one
        // item-rendering modal visible at a time prevents the underlying slot icon from masking it.
        if (selector.isOpen()) {
            selector.draw(mouseX, mouseY, partialTicks);
            return;
        }

        // Reset GL state completely before drawing modal.
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        context.getWidgetMinecraft().getTextureManager().bindTexture(MODAL_BACKGROUND);
        drawTexturedModalRect(modalLeft, modalTop, 0, 0, MODAL_WIDTH, MODAL_HEIGHT);

        int localX = mouseX - modalLeft;
        int localY = mouseY - modalTop;
        if (localX >= 5 && localX < 23 && localY >= 5 && localY < 23) {
            drawRect(modalLeft + 5, modalTop + 5, modalLeft + 23, modalTop + 23, 0x40FFFFFF);
        }

        targetField.drawTextBox();
        batchField.drawTextBox();
        frequencyField.drawTextBox();

        for (GuiButton button : frequencyButtons) {
            button.drawButton(context.getWidgetMinecraft(), mouseX, mouseY, partialTicks);
        }

        context.getWidgetFontRenderer().drawString(
            I18n.format("gui.ae2powertools.maintainer.modal.target"),
            modalLeft + 60,
            modalTop + 28,
            0x404040);
        context.getWidgetFontRenderer().drawString(
            I18n.format("gui.ae2powertools.maintainer.modal.batch"),
            modalLeft + 60,
            modalTop + 54,
            0x404040);
        context.getWidgetFontRenderer().drawString(
            I18n.format("gui.ae2powertools.maintainer.modal.frequency"),
            modalLeft + 60,
            modalTop + 80,
            0x404040);

        // Draw item in slot with proper GL state.
        if (workingEntry != null && workingEntry.getTargetItem() != null
                && workingEntry.getTargetItemStack() != null
                && !workingEntry.getTargetItemStack().isEmpty()) {
            ItemStack stack = workingEntry.getTargetItemStack();

            GlStateManager.enableDepth();
            RenderHelper.enableGUIStandardItemLighting();
            context.getWidgetItemRenderer().renderItemAndEffectIntoGUI(stack, modalLeft + 6, modalTop + 6);
            RenderHelper.disableStandardItemLighting();
            GlStateManager.disableDepth();

            String name = stack.getDisplayName();
            int maxWidth = 145;
            if (context.getWidgetFontRenderer().getStringWidth(name) > maxWidth) {
                name = context.getWidgetFontRenderer().trimStringToWidth(name, maxWidth - 6) + "...";
            }

            context.getWidgetFontRenderer().drawString(
                name,
                modalLeft + 25,
                modalTop + 10,
                workingEntry.isEnabled() ? 0x404040 : 0x808080);
        } else {
            context.getWidgetFontRenderer().drawString(
                I18n.format("gui.ae2powertools.maintainer.modal.select_item"),
                modalLeft + 25,
                modalTop + 10,
                0x808080);
        }

        GlStateManager.enableDepth();
    }

    public void drawTooltip(int mouseX, int mouseY) {
        if (!isOpen()) return;

        if (selector.isOpen()) {
            selector.drawTooltip(mouseX, mouseY);
            return;
        }

        int localX = mouseX - modalLeft;
        int localY = mouseY - modalTop;
        List<String> tooltip = new ArrayList<>();

        if (localX >= 5 && localX < 23 && localY >= 5 && localY < 23) {
            if (workingEntry != null && workingEntry.getTargetItem() != null
                    && workingEntry.getTargetItemStack() != null
                    && !workingEntry.getTargetItemStack().isEmpty()) {
                ITooltipFlag flag = context.getWidgetMinecraft().gameSettings.advancedItemTooltips
                    ? ITooltipFlag.TooltipFlags.ADVANCED
                    : ITooltipFlag.TooltipFlags.NORMAL;
                tooltip.addAll(workingEntry.getTargetItemStack().getTooltip(context.getWidgetMinecraft().player, flag));
                tooltip.add("");
            }

            tooltip.add("§7" + I18n.format("gui.ae2powertools.maintainer.modal.slot_left_click"));
            tooltip.add("§7" + I18n.format("gui.ae2powertools.maintainer.modal.slot_right_click"));
        }

        if (localX >= 25 && localX < 170 && localY >= 5 && localY < 23) {
            tooltip.add("§7" + I18n.format("gui.ae2powertools.maintainer.modal.name_click_toggle"));
        }

        if (!tooltip.isEmpty()) {
            GuiUtils.drawHoveringText(
                tooltip,
                mouseX,
                mouseY,
                context.getWidgetWidth(),
                context.getWidgetHeight(),
                -1,
                context.getWidgetFontRenderer());
        }
    }

    public boolean keyTyped(char typedChar, int keyCode) {
        if (!isOpen()) return false;

        if (selector.isOpen()) return selector.keyTyped(typedChar, keyCode);

        if (super.keyTyped(keyCode)) return true;

        if (targetField.textboxKeyTyped(typedChar, keyCode)) {
            FormattedNumberFieldHelper.reformatPreservingDigits(
                targetField,
                MaintainerEntry::parseQuantity,
                MaintainerEntry::formatQuantity);
            return true;
        }

        if (batchField.textboxKeyTyped(typedChar, keyCode)) {
            FormattedNumberFieldHelper.reformatPreservingDigits(
                batchField,
                MaintainerEntry::parseQuantity,
                MaintainerEntry::formatQuantity);
            return true;
        }

        return true;
    }

    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (!isOpen()) return false;

        if (selector.isOpen()) return selector.mouseClicked(mouseX, mouseY, mouseButton);

        if (super.mouseClicked(mouseX, mouseY)) return true;

        targetField.mouseClicked(mouseX, mouseY, mouseButton);
        batchField.mouseClicked(mouseX, mouseY, mouseButton);

        for (GuiButton button : frequencyButtons) {
            if (button.mousePressed(context.getWidgetMinecraft(), mouseX, mouseY)) {
                handleFrequencyButton(button);
                return true;
            }
        }

        int localX = mouseX - modalLeft;
        int localY = mouseY - modalTop;
        if (localX >= 5 && localX < 23 && localY >= 5 && localY < 23) {
            if (mouseButton == 0) {
                selector.open(craftableItemsSupplier.get());
            } else if (mouseButton == 1 && workingEntry != null) {
                workingEntry.setTargetItem(null);
            }

            return true;
        }

        if (localX >= 25 && localX < 170 && localY >= 5 && localY < 23
                && workingEntry != null && workingEntry.getTargetItem() != null) {
            workingEntry.setEnabled(!workingEntry.isEnabled());
        }

        return true;
    }

    public void mouseReleased(int mouseX, int mouseY, int state) {
        if (!isOpen()) return;

        selector.mouseReleased(mouseX, mouseY, state);
    }

    public boolean mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (!isOpen()) return false;

        return selector.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }

    public boolean handleMouseWheel(int scrollDelta) {
        if (!isOpen()) return false;

        return selector.handleMouseWheel(scrollDelta);
    }

    private void initFields() {
        frequencyButtons.clear();

        targetField = new GuiTextField(1, context.getWidgetFontRenderer(), modalLeft + 60, modalTop + 38, 110, 12);
        targetField.setMaxStringLength(20);
        targetField.setText(MaintainerEntry.formatQuantity(workingEntry.getTargetQuantity()));

        batchField = new GuiTextField(2, context.getWidgetFontRenderer(), modalLeft + 60, modalTop + 64, 110, 12);
        batchField.setMaxStringLength(20);
        batchField.setText(MaintainerEntry.formatQuantity(workingEntry.getBatchSize()));

        frequencyField = new GuiTextField(3, context.getWidgetFontRenderer(), modalLeft + 60, modalTop + 90, 110, 12);
        frequencyField.setMaxStringLength(20);
        frequencyField.setText(FormatUtil.formatTimeSeconds(lastFrequency));

        int buttonX = modalLeft + 3;
        int buttonY = modalTop + 48;
        int nextId = 100;
        frequencyButtons.add(new GuiButton(nextId++, buttonX, buttonY, 26, 12, "-1s"));
        frequencyButtons.add(new GuiButton(nextId++, buttonX + 28, buttonY, 26, 12, "+1s"));
        buttonY += 14;
        frequencyButtons.add(new GuiButton(nextId++, buttonX, buttonY, 26, 12, "-1m"));
        frequencyButtons.add(new GuiButton(nextId++, buttonX + 28, buttonY, 26, 12, "+1m"));
        buttonY += 14;
        frequencyButtons.add(new GuiButton(nextId++, buttonX, buttonY, 26, 12, "-1h"));
        frequencyButtons.add(new GuiButton(nextId++, buttonX + 28, buttonY, 26, 12, "+1h"));
        buttonY += 14;
        frequencyButtons.add(new GuiButton(nextId++, buttonX, buttonY, 26, 12, "-1d"));
        frequencyButtons.add(new GuiButton(nextId++, buttonX + 28, buttonY, 26, 12, "+1d"));
    }

    private void handleFrequencyButton(GuiButton button) {
        int delta = 0;
        String label = button.displayString;
        switch (label) {
            case "-1s":
                delta = -1;
                break;
            case "+1s":
                delta = 1;
                break;
            case "-1m":
                delta = -60;
                break;
            case "+1m":
                delta = 60;
                break;
            case "-1h":
                delta = -3600;
                break;
            case "+1h":
                delta = 3600;
                break;
            case "-1d":
                delta = -86400;
                break;
            case "+1d":
                delta = 86400;
                break;
        }

        if (delta == 0) return;

        lastFrequency = Math.max(1, lastFrequency + delta);
        frequencyField.setText(FormatUtil.formatTimeSeconds(lastFrequency));
    }

    private void renderSelectorEntry(WidgetContext context, IAEItemStack item, int x, int y) {
        ItemStack stack = Ae2FluidCraftingCompat.getDisplayStack(item);

        GlStateManager.enableDepth();
        RenderHelper.enableGUIStandardItemLighting();
        context.getWidgetItemRenderer().renderItemAndEffectIntoGUI(stack, x, y);

        long quantity = item.getStackSize();
        if (quantity > 1) {
            // Match the terminal's scaled overlay so larger outputs stay readable.
            stackSizeRenderer.renderStackSize(context.getWidgetFontRenderer(), item, x, y);
        }

        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableDepth();
    }

    private void renderSelectorTooltip(IAEItemStack item, int mouseX, int mouseY) {
        ItemStack stack = Ae2FluidCraftingCompat.getDisplayStack(item);
        ITooltipFlag flag = context.getWidgetMinecraft().gameSettings.advancedItemTooltips
            ? ITooltipFlag.TooltipFlags.ADVANCED
            : ITooltipFlag.TooltipFlags.NORMAL;

        GuiUtils.drawHoveringText(
            stack.getTooltip(context.getWidgetMinecraft().player, flag),
            mouseX,
            mouseY,
            context.getWidgetWidth(),
            context.getWidgetHeight(),
            -1,
            context.getWidgetFontRenderer());
    }

    private void selectRecipe(IAEItemStack item) {
        if (workingEntry == null) return;

        // Recipe selection still fires its dedicated packet immediately. The editor keeps the local
        // copy in sync so the new target is visible before the player commits the rest of the fields.
        workingEntry.setTargetItem(item);
        recipeSelectionHandler.select(entryIndex, item);
    }
}