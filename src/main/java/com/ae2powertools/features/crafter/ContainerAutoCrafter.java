package com.ae2powertools.features.crafter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import appeng.container.AEBaseContainer;
import appeng.container.guisync.GuiSync;
import appeng.container.slot.AppEngSlot;
import appeng.util.Platform;

import com.ae2powertools.features.crafter.pmt.PMTManager;
import com.ae2powertools.features.crafter.pmt.PMTSlot;
import com.ae2powertools.features.crafter.terminal.AutoCrafterPatternActions;
import com.ae2powertools.items.ItemCrafterSpeedUpgrade;
import com.ae2powertools.network.PowerToolsNetwork;
import com.ae2powertools.util.ContainerListenerSync;


/**
 * Container for the AE2 AutoCrafter GUI.
 * Manages the pattern slot and catalyst inventory for a single recipe entry.
 * <p>
 * When NAE2 is installed and the player has a Pattern Multi-Tool in their inventory,
 * this container adds PMT slots for convenient pattern storage access.
 */
public class ContainerAutoCrafter extends AEBaseContainer {

    private final TileAutoCrafter tile;
    private final InventoryPlayer playerInv;

    /**
     * Currently viewed entry index (0-11).
     * Synced from tile via {@link GuiSync}.
     * <p>
     * NOTE: This field is the CLIENT MIRROR of {@link TileAutoCrafter#getCurrentPage()}.
     * Server-side click handling MUST always use {@code tile.getCurrentPage()} as the
     * authoritative source, because the field could lag behind by one tick if a page packet
     * was processed in the same tick as a slot click. See handlePatternSlotClick etc.
     */
    @GuiSync(3)
    public int syncCurrentPage = 0;

    /**
     * Whether we're in overview mode.
     */
    @GuiSync(0)
    public boolean overviewMode = false;

    @GuiSync(1)
    public long syncSpeedTicks = TileAutoCrafter.DEFAULT_SPEED_TICKS;

    @GuiSync(2)
    public long syncBatchSize = TileAutoCrafter.DEFAULT_BATCH_SIZE;

    /**
     * Effective batch size (base * user batch * upgrade multiplier).
     * This is the actual number of crafts performed per operation.
     * Synced from server since it depends on upgrades and config.
     */
    @GuiSync(4)
    public long syncEffectiveBatchSize = TileAutoCrafter.DEFAULT_BATCH_SIZE;

    // ============================= Server-side diff caches =============================
    // These mirror the LAST sent snapshot to each listener. Per-tick detectAndSendChanges
    // diffs the current tile state against these caches and only sends packets when
    // something actually changed. Only what is needed to render the current page is cached/synced.
    // The overview is also sync'd, as it is cheap and is a modal, which can be opened/closed without page changes.

    /** Last overview snapshot sent for each entry (server-side only). */
    private final CrafterOverviewSnapshot[] lastOverviewSnapshots = new CrafterOverviewSnapshot[TileAutoCrafter.ENTRY_COUNT];

    /** Last recipe snapshot sent for the entry index this cache was filled with (server-side only). */
    @Nullable
    private CrafterRecipeSnapshot lastRecipeSnapshot;

    /** Entry index that {@link #lastRecipeSnapshot} corresponds to (-1 = none yet). */
    private int lastRecipeEntryIndex = -1;

    // Slots for the current entry
    private final SlotPattern patternSlot;
    private final SlotCatalyst[] catalystSlots;

    // Pattern Multi-Tool support
    private PMTManager pmtManager;
    private final List<PMTSlot> pmtSlots = new ArrayList<>();

    // Upgrade slots
    private static final int UPGRADE_START_X = 187;
    private static final int UPGRADE_START_Y = 8;
    private static final int UPGRADE_SLOT_SIZE = 18;

    // PMT slot layout
    private static final int PMT_OFFSET_X = -86 - 4;  // Left of main GUI
    private static final int PMT_OFFSET_Y = 26;       // Aligned with recipe area
    private static final int PMT_SLOT_MARGIN = 8;     // Margin from panel edge

    public ContainerAutoCrafter(InventoryPlayer playerInv, TileAutoCrafter tile) {
        super(playerInv, tile, null);
        this.tile = tile;
        this.playerInv = playerInv;

        // Initialize synced values from tile immediately (server-side).
        // This ensures the first @GuiSync send carries the correct values to the client
        // (AE2 sends initial values on the first detectAndSendChanges call).
        if (Platform.isServer()) {
            this.syncSpeedTicks = tile.getSpeedTicks();
            this.syncBatchSize = tile.getBatchSize();
            this.syncEffectiveBatchSize = tile.getEffectiveMaxBatchSize();
            this.syncCurrentPage = tile.getCurrentPage();
        }

        // Get initial page from tile
        int initialPage = tile.getCurrentPage();

        // Pattern slot - uses tile + entryIndex directly
        this.patternSlot = new SlotPattern(this, tile, initialPage, 17, 43);
        addSlotToContainer(patternSlot);

        // Catalyst slots (9 slots) - 1:1 mapping with crafting grid
        this.catalystSlots = new SlotCatalyst[CrafterEntry.CATALYST_SLOTS];
        for (int i = 0; i < CrafterEntry.CATALYST_SLOTS; i++) {
            int x = 8 + (i % 9) * 18;
            int y = 90;
            catalystSlots[i] = new SlotCatalyst(this, tile, i, initialPage, x, y);
            addSlotToContainer(catalystSlots[i]);
        }

        // Upgrade slots (4 slots) - uses tile directly (not per-entry)
        int x = UPGRADE_START_X;
        SlotUpgrade[] upgradeSlots = new SlotUpgrade[TileAutoCrafter.UPGRADE_SLOTS];
        for (int i = 0; i < TileAutoCrafter.UPGRADE_SLOTS; i++) {
            int y = UPGRADE_START_Y + i * UPGRADE_SLOT_SIZE;
            upgradeSlots[i] = new SlotUpgrade(this, tile, i, x, y);
            addSlotToContainer(upgradeSlots[i]);
        }

        // Player inventory (starting at y=167)
        this.bindPlayerInventory(playerInv, 0, 167);

        // Initialize Pattern Multi-Tool slots if NAE2 is loaded and player has PMT
        initializePMTSlots();
    }

    /**
     * Initializes Pattern Multi-Tool slots if NAE2 is loaded and player has a PMT.
     * Creates 36 PMT slots (4 columns x 9 rows), with columns enabled based on capacity upgrades.
     */
    private void initializePMTSlots() {
        // Find PMT in player inventory
        this.pmtManager = PMTManager.findPMT(playerInv.player);
        if (pmtManager == null) return;

        IItemHandler pmtInventory = pmtManager.getPatternInventory();

        // Add PMT slots: 4 columns x 9 rows
        // Slots are positioned to the left of the main GUI
        // NAE2 uses column-major indexing: col * ROWS + row
        for (int row = 0; row < PMTManager.ROWS; row++) {
            for (int col = 0; col < PMTManager.COLUMNS; col++) {
                // NAE2 uses column-major ordering, so slot index = col * ROWS + row
                int slotIndex = col * PMTManager.ROWS + row;
                int x = PMT_OFFSET_X + PMT_SLOT_MARGIN + col * 18;
                int y = PMT_OFFSET_Y + PMT_SLOT_MARGIN + row * 18;

                PMTSlot slot = new PMTSlot(pmtInventory, pmtManager, slotIndex, x, y, col, playerInv);
                addSlotToContainer(slot);
                pmtSlots.add(slot);
            }
        }
    }

    /**
     * Gets the PMT manager, or null if PMT is not available.
     */
    @Nullable
    public PMTManager getPMTManager() {
        return pmtManager;
    }

    /**
     * Gets the list of PMT slots, empty if PMT is not available.
     */
    public List<PMTSlot> getPMTSlots() {
        return pmtSlots;
    }

    @Override
    public void detectAndSendChanges() {
        // Pull tile values into @GuiSync mirrors BEFORE super.
        // AE2's SyncData uses null clientVersion on the very first tick to push the
        // initial value, so the field must already match the host before super runs.
        if (Platform.isServer()) {
            this.syncSpeedTicks = tile.getSpeedTicks();
            this.syncBatchSize = tile.getBatchSize();
            this.syncEffectiveBatchSize = tile.getEffectiveMaxBatchSize();

            int newPage = tile.getCurrentPage();
            if (newPage != this.syncCurrentPage) {
                // Repoint the client-side pattern/catalyst slots before vanilla's slot diff for
                // the new page arrives, just like the initial open path does in addListener().
                ContainerListenerSync.sendToPlayerListeners(this.listeners, new PacketCrafterPageInit(newPage));

                // Update server-side slot entry indices BEFORE super so that the vanilla
                // slot diff picks up the new page's pattern + catalysts atomically with
                // the @GuiSync currentPage update.
                this.syncCurrentPage = newPage;
                updateSlotsForCurrentPage();
            }
        }

        super.detectAndSendChanges();

        if (!Platform.isServer()) return;

        // Per-listener diff sync of overview + recipe data
        sendOverviewDiff();
        sendRecipeDiff();
    }

    /**
     * Diff-sync overview snapshots for all 12 entries against the last sent ones.
     * Only changed entries are bundled into the packet; if nothing changed, no packet is sent.
     */
    private void sendOverviewDiff() {
        Map<Integer, CrafterOverviewSnapshot> changed = null;

        for (int i = 0; i < TileAutoCrafter.ENTRY_COUNT; i++) {
            CrafterOverviewSnapshot current = CrafterOverviewSnapshot.fromEntry(tile.getEntry(i));
            CrafterOverviewSnapshot cached = lastOverviewSnapshots[i];

            if (cached != null && cached.equals(current)) continue;

            lastOverviewSnapshots[i] = current;
            if (changed == null) changed = new HashMap<>();
            changed.put(i, current);
        }

        if (changed == null) return;

        PacketCrafterOverviewSync packet = new PacketCrafterOverviewSync(changed);
        ContainerListenerSync.sendToPlayerListeners(this.listeners, packet);
    }

    /**
     * Diff-sync the current entry's recipe data. The recipe view only ever shows the
     * current page, so we only sync that one entry. When the page changes, the cache
     * is invalidated (new entryIndex != cached entryIndex) and a fresh recipe sync
     * is sent for the new page.
     */
    private void sendRecipeDiff() {
        int currentEntry = tile.getCurrentPage();
        if (currentEntry < 0 || currentEntry >= TileAutoCrafter.ENTRY_COUNT) return;

        CrafterRecipeSnapshot current = CrafterRecipeSnapshot.fromEntry(tile.getEntry(currentEntry));

        // Send if entry changed or content differs
        boolean entryChanged = lastRecipeEntryIndex != currentEntry;
        if (!entryChanged && lastRecipeSnapshot != null && lastRecipeSnapshot.equals(current)) return;

        lastRecipeEntryIndex = currentEntry;
        lastRecipeSnapshot = current;

        PacketCrafterRecipeSync packet = new PacketCrafterRecipeSync(currentEntry, current);
        ContainerListenerSync.sendToPlayerListeners(this.listeners, packet);
    }

    @Override
    public void addListener(@Nonnull IContainerListener listener) {
        // Send the current page to the client BEFORE super.addListener fires
        // vanilla's initial slot sync (sendAllContents). The catalyst slot's item handler
        // writes incoming stack data into tile.getEntry(slot.entryIndex).catalystStack on
        // the client. The client's tile.currentPage is NOT synced (it's not in chunk data),
        // so the client constructs its slots pointing at entry[0] regardless of which page
        // the server is actually on. Without this packet, the initial catalyst stacks for the
        // server's actual page leak into the client's entry[0], causing the catalyst to
        // reappear on page 1 when the user navigates back to it later. See PacketCrafterPageInit.
        EntityPlayerMP playerListener = ContainerListenerSync.getPlayerListener(listener);
        if (Platform.isServer() && playerListener != null) {
            PowerToolsNetwork.INSTANCE.sendTo(
                new PacketCrafterPageInit(tile.getCurrentPage()),
                playerListener
            );
        }

        super.addListener(listener);

        // Initial full sync to the new listener. The diff caches are
        // also populated so the very next tick only sends real changes.
        EntityPlayerMP mp = ContainerListenerSync.getPlayerListener(listener);
        if (!Platform.isServer() || mp == null) return;

        // Full overview snapshot for all entries
        Map<Integer, CrafterOverviewSnapshot> all = new HashMap<>();
        for (int i = 0; i < TileAutoCrafter.ENTRY_COUNT; i++) {
            CrafterOverviewSnapshot snap = CrafterOverviewSnapshot.fromEntry(tile.getEntry(i));
            lastOverviewSnapshots[i] = snap;
            all.put(i, snap);
        }
        PowerToolsNetwork.INSTANCE.sendTo(new PacketCrafterOverviewSync(all), mp);

        // Recipe snapshot for the current entry
        int currentEntry = tile.getCurrentPage();
        if (currentEntry >= 0 && currentEntry < TileAutoCrafter.ENTRY_COUNT) {
            CrafterRecipeSnapshot recipe = CrafterRecipeSnapshot.fromEntry(tile.getEntry(currentEntry));
            lastRecipeEntryIndex = currentEntry;
            lastRecipeSnapshot = recipe;
            PowerToolsNetwork.INSTANCE.sendTo(new PacketCrafterRecipeSync(currentEntry, recipe), mp);
        }
    }

    public TileAutoCrafter getTile() {
        return tile;
    }

    public int getCurrentEntryIndex() {
        return syncCurrentPage;
    }

    public void setCurrentEntryIndex(int index) {
        if (index < 0 || index >= TileAutoCrafter.ENTRY_COUNT) return;

        this.syncCurrentPage = index;
        this.tile.setCurrentPage(index);
        updateSlotsForCurrentPage();
    }

    public CrafterEntry getCurrentEntry() {
        return tile.getEntry(syncCurrentPage);
    }

    /**
     * Returns the live ItemStack in the catalyst slot at the given index, for the
     * page currently displayed by the GUI. Used by {@link GuiAutoCrafter} to render
     * ghost overlays without a separate sync channel (vanilla already syncs slots).
     */
    public ItemStack getCatalystSlotStack(int catalystIndex) {
        if (catalystIndex < 0 || catalystIndex >= catalystSlots.length) return ItemStack.EMPTY;
        return catalystSlots[catalystIndex].getStack();
    }

    /**
     * Update slot entry references when page changes.
     */
    private void updateSlotsForCurrentPage() {
        patternSlot.setEntryIndex(syncCurrentPage);

        for (SlotCatalyst slot : catalystSlots) slot.setEntryIndex(syncCurrentPage);
    }

    @Override
    @Nonnull
    public ItemStack slotClick(int slotId, int dragType, ClickType clickType, @Nonnull EntityPlayer player) {
        if (slotId >= 0 && slotId < inventorySlots.size()) {
            Slot slot = inventorySlots.get(slotId);

            if (slot instanceof SlotPattern) {
                return handlePatternSlotClick((SlotPattern) slot, dragType, clickType, player);
            }

            if (slot instanceof SlotCatalyst) {
                return handleCatalystSlotClick((SlotCatalyst) slot, dragType, clickType, player);
            }

            if (slot instanceof SlotUpgrade) {
                return handleUpgradeSlotClick((SlotUpgrade) slot, dragType, clickType, player);
            }
        }

        return super.slotClick(slotId, dragType, clickType, player);
    }

    private ItemStack handlePatternSlotClick(SlotPattern slot, int dragType, ClickType clickType, EntityPlayer player) {
        CrafterEntry entry = getCurrentEntry();
        if (entry == null) return heldResult(player);

        ItemStack held = player.inventory.getItemStack();

        // Right-click to extract
        if (dragType == 1 && clickType == ClickType.PICKUP) {
            if (entry.hasPattern()) {
                ItemStack pattern = entry.getPatternStack().copy();
                entry.setPatternStack(null);
                tile.simulatePattern(syncCurrentPage, null);
                // Pattern removed: eject any leftover catalysts so the slots stay valid.
                ejectCatalystsToPlayer(syncCurrentPage, player);

                if (held.isEmpty()) {
                    player.inventory.setItemStack(pattern);
                } else {
                    // Try to add to inventory
                    if (!player.inventory.addItemStackToInventory(pattern)) player.dropItem(pattern, false);
                }
                return heldResult(player);
            }
            return heldResult(player);
        }

        // Left-click to insert/swap
        if (clickType == ClickType.PICKUP) {
            if (held.isEmpty()) {
                // Extract existing pattern
                if (entry.hasPattern()) {
                    ItemStack pattern = entry.getPatternStack().copy();
                    entry.setPatternStack(null);
                    tile.simulatePattern(syncCurrentPage, null);
                    // Pattern removed: eject leftover catalysts.
                    ejectCatalystsToPlayer(syncCurrentPage, player);
                    player.inventory.setItemStack(pattern);
                }
            } else {
                // Check if held item is a valid pattern
                if (isValidPattern(held)) {
                    ItemStack existing = entry.hasPattern() ? entry.getPatternStack().copy() : ItemStack.EMPTY;
                    // If swapping/replacing the pattern, eject catalysts first since the
                    // recipe (and therefore the valid catalyst types) may change.
                    if (!existing.isEmpty()) ejectCatalystsToPlayer(syncCurrentPage, player);
                    ItemStack toInsert = held.splitStack(1);
                    entry.setPatternStack(toInsert);
                    tile.simulatePattern(syncCurrentPage, toInsert);

                    if (!existing.isEmpty()) {
                        if (held.isEmpty()) {
                            player.inventory.setItemStack(existing);
                        } else {
                            if (!player.inventory.addItemStackToInventory(existing)) {
                                player.dropItem(existing, false);
                            }
                        }
                    }
                }
            }
            return heldResult(player);
        }

        // Shift-click
        if (clickType == ClickType.QUICK_MOVE) {
            if (entry.hasPattern()) {
                ItemStack pattern = entry.getPatternStack().copy();

                // Try PMT first if available
                if (pmtManager != null && tryInsertIntoPMT(pattern)) {
                    entry.setPatternStack(null);
                    tile.simulatePattern(syncCurrentPage, null);
                    // Pattern removed: eject leftover catalysts.
                    ejectCatalystsToPlayer(syncCurrentPage, player);
                    tile.markDirty();
                    return heldResult(player);
                }

                // Fall back to player inventory
                entry.setPatternStack(null);
                tile.simulatePattern(syncCurrentPage, null);
                // Pattern removed: eject leftover catalysts.
                ejectCatalystsToPlayer(syncCurrentPage, player);

                if (!player.inventory.addItemStackToInventory(pattern)) player.dropItem(pattern, false);
            }
            return heldResult(player);
        }

        return heldResult(player);
    }

    /**
     * Returns the player's current held item to be sent back from {@link #slotClick}.
     * <p>
     * Vanilla's network handler ({@code NetHandlerPlayServer.processClickWindow}) compares
     * this against the client's predicted result and triggers a full container resync if
     * they differ. Returning {@link ItemStack#EMPTY} unconditionally would let client-side
     * stale state escape detection - notably the catalyst duplication exploit where the
     * client predicts extracting an item from a slot the server thinks is empty.
     * Always copy: vanilla mutates the held item later in the pipeline.
     */
    private static ItemStack heldResult(EntityPlayer player) {
        ItemStack held = player.inventory.getItemStack();
        return held.isEmpty() ? ItemStack.EMPTY : held.copy();
    }

    /**
     * Ejects all non-empty catalyst stacks from the given entry into the player's inventory,
     * dropping any that don't fit. Called whenever the pattern transitions to absent or is
     * replaced, since the catalyst slots are tied to the recipe's expected ingredients and
     * leftover catalysts would otherwise persist as orphan items the recipe no longer accepts.
     */
    private void ejectCatalystsToPlayer(int entryIndex, EntityPlayer player) {
        AutoCrafterPatternActions.ejectCatalystsToPlayer(tile, entryIndex, player);
    }

    /**
     * Handles catalyst slot click interactions.
     * <p>
     * Catalyst slots hold REUSABLE and DURABILITY items:
     * - Each slot holds exactly 1 item (crafting recipes use 1 item per slot)
     * - Only items matching the expected catalyst type can be inserted
     * - DURABILITY items: any item of the same type is valid (durability may vary)
     * - REUSABLE items: exact match required (same Item, metadata, NBT)
     */
    private ItemStack handleCatalystSlotClick(SlotCatalyst slot, int dragType, ClickType clickType, EntityPlayer player) {
        CrafterEntry entry = getCurrentEntry();
        if (entry == null) return heldResult(player);

        int catalystIndex = slot.getCatalystIndex();
        ItemStack held = player.inventory.getItemStack();
        ItemStack existing = entry.getCatalystStack(catalystIndex);

        // Check what item is expected in this slot
        ItemStack expectedItem = getExpectedCatalystItem(catalystIndex);

        // Right-click to extract
        if (dragType == 1 && clickType == ClickType.PICKUP) {
            if (!existing.isEmpty()) {
                // Catalyst slots only hold 1 item, extract it
                if (held.isEmpty()) {
                    player.inventory.setItemStack(existing.copy());
                } else {
                    if (!player.inventory.addItemStackToInventory(existing.copy())) {
                        player.dropItem(existing.copy(), false);
                    }
                }
                entry.setCatalystStack(catalystIndex, ItemStack.EMPTY);
                tile.validateCatalysts(syncCurrentPage);
                tile.markDirty();
            }
            return heldResult(player);
        }

        // Left-click to insert/swap
        if (clickType == ClickType.PICKUP) {
            if (held.isEmpty()) {
                // Extract all
                if (!existing.isEmpty()) {
                    player.inventory.setItemStack(existing.copy());
                    entry.setCatalystStack(catalystIndex, ItemStack.EMPTY);
                    tile.validateCatalysts(syncCurrentPage);
                    tile.markDirty();
                }
            } else {
                // If no expected item, this slot is not a catalyst slot - reject all items
                if (expectedItem.isEmpty()) return heldResult(player);

                // Check if item matches expected catalyst
                boolean canInsert = isValidCatalystItem(held, expectedItem, catalystIndex);

                if (canInsert) {
                    // Swap: put held item in slot, return existing to player
                    ItemStack toInsert = held.copy();
                    toInsert.setCount(1); // Catalyst slots only accept 1 item
                    held.shrink(1);
                    
                    if (!existing.isEmpty()) {
                        // Return existing to player
                        if (held.isEmpty()) {
                            player.inventory.setItemStack(existing.copy());
                        } else if (!player.inventory.addItemStackToInventory(existing.copy())) {
                            player.dropItem(existing.copy(), false);
                        }
                    }
                    
                    entry.setCatalystStack(catalystIndex, toInsert);
                    tile.validateCatalysts(syncCurrentPage);
                    tile.markDirty();
                }
            }
            return heldResult(player);
        }

        // Shift-click to move to player inventory
        if (clickType == ClickType.QUICK_MOVE) {
            if (!existing.isEmpty()) {
                if (!player.inventory.addItemStackToInventory(existing.copy())) {
                    player.dropItem(existing.copy(), false);
                }
                entry.setCatalystStack(catalystIndex, ItemStack.EMPTY);
                tile.validateCatalysts(syncCurrentPage);
                tile.markDirty();
            }
            return heldResult(player);
        }

        return heldResult(player);
    }

    /**
     * Handles upgrade slot click interactions.
     * <p>
     * Simple vanilla-style slot behavior:
     * - Only speed upgrades are accepted
     * - Only ONE speed upgrade can be installed across all slots
     * - Click with valid upgrade: insert or swap
     * - Click with empty hand: extract
     * - Shift-click: move to player inventory
     */
    private ItemStack handleUpgradeSlotClick(SlotUpgrade slot, int dragType, ClickType clickType, EntityPlayer player) {
        int upgradeIndex = slot.getUpgradeIndex();
        ItemStack held = player.inventory.getItemStack();
        ItemStack existing = tile.getUpgradeStack(upgradeIndex);

        // Shift-click: move existing upgrade to player inventory
        if (clickType == ClickType.QUICK_MOVE) {
            if (existing.isEmpty()) return heldResult(player);

            ItemStack toMove = existing.copy();
            tile.setUpgradeStack(upgradeIndex, ItemStack.EMPTY);

            if (!player.inventory.addItemStackToInventory(toMove)) player.dropItem(toMove, false);

            return heldResult(player);
        }

        // Regular click (left or right) - vanilla behavior: swap/insert/extract
        if (clickType != ClickType.PICKUP) return heldResult(player);

        // Empty hand: extract existing upgrade
        if (held.isEmpty()) {
            if (existing.isEmpty()) return heldResult(player);

            player.inventory.setItemStack(existing.copy());
            tile.setUpgradeStack(upgradeIndex, ItemStack.EMPTY);

            return heldResult(player);
        }

        // Holding something - check if it's a valid speed upgrade
        if (!ItemCrafterSpeedUpgrade.isSpeedUpgrade(held)) return heldResult(player);

        // Check if another slot already has a speed upgrade (only 1 allowed total)
        for (int i = 0; i < TileAutoCrafter.UPGRADE_SLOTS; i++) {
            if (i == upgradeIndex) continue;

            ItemStack otherSlot = tile.getUpgradeStack(i);
            if (ItemCrafterSpeedUpgrade.isSpeedUpgrade(otherSlot)) {
                // Already have a speed upgrade in another slot - not allowed
                // Player must remove it first
                return heldResult(player);
            }
        }

        // Insert new upgrade, swap with existing if any
        ItemStack toInsert = held.splitStack(1);
        tile.setUpgradeStack(upgradeIndex, toInsert);

        if (!existing.isEmpty()) {
            // Put existing upgrade into player's hand (vanilla swap behavior)
            if (held.isEmpty()) {
                player.inventory.setItemStack(existing.copy());
            } else if (!player.inventory.addItemStackToInventory(existing.copy())) {
                    // Hand not empty after split, add to inventory or drop
                    player.dropItem(existing.copy(), false);
            }
        }

        return heldResult(player);
    }

    /**
     * Checks if an item is valid for a catalyst slot.
     * Uses 1:1 slot mapping: catalyst slot index = recipe slot index.
     * For REUSABLE/DUPLICATION items, uses inclusive NBT matching.
     */
    private boolean isValidCatalystItem(ItemStack candidate, ItemStack expected, int catalystIndex) {
        if (candidate.isEmpty() || expected.isEmpty()) return true;

        CrafterEntry entry = getCurrentEntry();
        if (entry == null || !entry.hasValidRecipeInfo()) return true;

        CrafterRecipeInfo info = entry.getRecipeInfo();
        if (info == null) return true;

        // Find the ingredient whose slot index matches the catalyst index (1:1 mapping)
        for (CrafterRecipeInfo.IngredientInfo catalyst : info.getCatalystSlots()) {
            if (catalyst.getSlotIndex() == catalystIndex) {
                // For REUSABLE/DUPLICATION items, use inclusive NBT matching
                // The candidate must have at least the expected NBT but may have extra tags
                return areItemStacksMatchingInclusive(expected, candidate);
            }
        }

        // Slot is not a catalyst slot according to recipe, don't allow any item (shouldn't happen normally)
        return false;
    }

    /**
     * Checks if an item matches a requirement using inclusive NBT matching.
     * The requirement's NBT must be a subset of the actual item's NBT.
     * This allows items with additional NBT tags (e.g., from enchantments or mod data)
     * to still match the recipe requirement.
     * 
     * @param required The requirement (what the recipe expects)
     * @param actual The actual item (what the player provides)
     * @return true if actual matches required, including NBT subset check
     */
    private boolean areItemStacksMatchingInclusive(ItemStack required, ItemStack actual) {
        if (required.isEmpty() && actual.isEmpty()) return true;
        if (required.isEmpty() || actual.isEmpty()) return false;
        if (required.getItem() != actual.getItem()) return false;
        if (required.getMetadata() != actual.getMetadata()) return false;

        // Inclusive NBT check: required NBT must be subset of actual NBT
        NBTTagCompound requiredTag = required.getTagCompound();
        NBTTagCompound actualTag = actual.getTagCompound();

        // If requirement has no NBT, any item NBT is acceptable
        if (requiredTag == null || requiredTag.isEmpty()) return true;

        // If requirement has NBT but item has none, fail
        if (actualTag == null) return false;

        // Check that all keys in requiredTag exist in actualTag with same values
        return isNbtSubset(requiredTag, actualTag);
    }

    /**
     * Checks if 'subset' NBT is contained within 'superset' NBT.
     * All keys in subset must exist in superset with equal values.
     */
    private boolean isNbtSubset(NBTTagCompound subset, NBTTagCompound superset) {
        for (String key : subset.getKeySet()) {
            if (!superset.hasKey(key)) return false;
            if (subset.getTagId(key) != superset.getTagId(key)) return false;
            if (!subset.getTag(key).equals(superset.getTag(key))) return false;
        }

        return true;
    }

    /**
     * Gets the expected catalyst item for a slot based on recipe info.
     * Uses 1:1 slot mapping: catalyst slot index = recipe slot index.
     */
    private ItemStack getExpectedCatalystItem(int catalystIndex) {
        CrafterEntry entry = getCurrentEntry();
        if (entry == null || !entry.hasValidRecipeInfo()) return ItemStack.EMPTY;

        CrafterRecipeInfo info = entry.getRecipeInfo();
        if (info == null) return ItemStack.EMPTY;

        // Find the catalyst whose slot index matches the catalyst inventory index (1:1 mapping)
        for (CrafterRecipeInfo.IngredientInfo catalyst : info.getCatalystSlots()) {
            if (catalyst.getSlotIndex() == catalystIndex) {
                return catalyst.getItem() != null ? catalyst.getItem().createItemStack() : ItemStack.EMPTY;
            }
        }

        return ItemStack.EMPTY;
    }

    /**
     * Tries to insert a pattern into the Pattern Multi-Tool inventory.
     * First attempts to merge with existing matching patterns, then finds an empty slot.
     * Only uses enabled columns (based on capacity upgrades).
     * 
     * @param pattern The pattern to insert
     * @return true if successfully inserted, false otherwise
     */
    private boolean tryInsertIntoPMT(ItemStack pattern) {
        if (pmtManager == null || pattern.isEmpty()) return false;

        IItemHandler pmtInventory = pmtManager.getPatternInventory();
        int firstEmptySlot = -1;

        // First pass: try to merge with existing matching patterns
        for (int col = 0; col < PMTManager.COLUMNS; col++) {
            if (!pmtManager.isColumnEnabled(col)) continue;

            for (int row = 0; row < PMTManager.ROWS; row++) {
                int slotIndex = col * PMTManager.ROWS + row;
                ItemStack existing = pmtInventory.getStackInSlot(slotIndex);

                if (existing.isEmpty()) {
                    // Track first empty slot for later
                    if (firstEmptySlot < 0) firstEmptySlot = slotIndex;
                    continue;
                }

                // Check if we can merge (same item, metadata, NBT, and has room)
                if (ItemStack.areItemsEqual(existing, pattern) &&
                    ItemStack.areItemStackTagsEqual(existing, pattern) &&
                    existing.getCount() < existing.getMaxStackSize()) {

                    ItemStack remaining = pmtInventory.insertItem(slotIndex, pattern, false);
                    if (remaining.isEmpty()) {
                        pmtManager.saveChanges();
                        return true;
                    }
                }
            }
        }

        // Second pass: use first empty slot if no merge was possible
        if (firstEmptySlot >= 0) {
            ItemStack remaining = pmtInventory.insertItem(firstEmptySlot, pattern, false);
            if (remaining.isEmpty()) {
                pmtManager.saveChanges();
                return true;
            }
        }

        return false;
    }

    private boolean isValidPattern(ItemStack stack) {
        return AutoCrafterPatternActions.isValidPattern(tile, stack);
    }

    /**
     * Gets slot indices:
     * - Slot 0: Pattern
     * - Slots 1-9: Catalysts
     * - Slots 10-13: Upgrades
     * - Slots 14+: Player inventory
     */
    private static final int SLOT_PATTERN = 0;
    private static final int SLOT_CATALYST_START = 1;
    private static final int SLOT_UPGRADE_START = 10;
    private static final int SLOT_PLAYER_START = 14;

    @Override
    @Nonnull
    public ItemStack transferStackInSlot(EntityPlayer player, int slotIndex) {
        Slot slot = inventorySlots.get(slotIndex);
        if (slot == null || !slot.getHasStack()) return ItemStack.EMPTY;

        ItemStack stack = slot.getStack();
        ItemStack original = stack.copy();

        // Handle PMT slot shift-click (transfer to crafter pattern slot)
        if (slot instanceof PMTSlot) {
            PMTSlot pmtSlot = (PMTSlot) slot;
            if (!pmtSlot.isSlotEnabled()) return ItemStack.EMPTY;
            
            // Try to transfer pattern to crafter's pattern slot
            if (isValidPattern(stack)) {
                CrafterEntry entry = getCurrentEntry();
                if (entry != null && !entry.hasPattern()) {
                    ItemStack toInsert = stack.splitStack(1);
                    entry.setPatternStack(toInsert);
                    tile.simulatePattern(syncCurrentPage, toInsert);
                    tile.markDirty();
                    // Update PMT inventory
                    if (pmtManager != null) pmtManager.saveChanges();
                    return finishTransfer(slot, player, original);
                }
            }
            return ItemStack.EMPTY;
        }

        // From player inventory
        if (slotIndex >= SLOT_PLAYER_START && slotIndex < SLOT_PLAYER_START + 36) {
            // Try pattern slot first
            if (isValidPattern(stack)) {
                CrafterEntry entry = getCurrentEntry();
                if (entry != null && !entry.hasPattern()) {
                    ItemStack toInsert = stack.splitStack(1);
                    entry.setPatternStack(toInsert);
                    tile.simulatePattern(syncCurrentPage, toInsert);
                    tile.markDirty();
                    return finishTransfer(slot, player, original);
                }
            }

            // Try upgrade slots (speed upgrades) - only if no speed upgrade already installed
            if (ItemCrafterSpeedUpgrade.isSpeedUpgrade(stack)) {
                // Check if any slot already has a speed upgrade
                boolean hasSpeedUpgrade = false;
                for (int i = 0; i < TileAutoCrafter.UPGRADE_SLOTS; i++) {
                    if (ItemCrafterSpeedUpgrade.isSpeedUpgrade(tile.getUpgradeStack(i))) {
                        hasSpeedUpgrade = true;
                        break;
                    }
                }

                // Only insert if no speed upgrade already installed
                if (!hasSpeedUpgrade) {
                    for (int i = 0; i < TileAutoCrafter.UPGRADE_SLOTS; i++) {
                        if (tile.getUpgradeStack(i).isEmpty()) {
                            ItemStack toInsert = stack.splitStack(1);
                            tile.setUpgradeStack(i, toInsert);
                            return finishTransfer(slot, player, original);
                        }
                    }
                }
            }

            // Try catalyst slots (only if item matches expected for that slot)
            CrafterEntry entry = getCurrentEntry();
            if (entry != null && entry.hasValidRecipeInfo()) {
                CrafterRecipeInfo info = entry.getRecipeInfo();
                if (info != null) {
                    for (CrafterRecipeInfo.IngredientInfo catalyst : info.getCatalystSlots()) {
                        int slotIdx = catalyst.getSlotIndex();
                        if (slotIdx >= 0 && slotIdx < CrafterEntry.CATALYST_SLOTS) {
                            if (entry.getCatalystStack(slotIdx).isEmpty()) {
                                ItemStack expected = catalyst.getItem() != null
                                        ? catalyst.getItem().createItemStack()
                                        : ItemStack.EMPTY;
                                if (!expected.isEmpty() && isValidCatalystItem(stack, expected, slotIdx)) {
                                    ItemStack toInsert = stack.splitStack(1);
                                    entry.setCatalystStack(slotIdx, toInsert);
                                    tile.validateCatalysts(syncCurrentPage);
                                    tile.markDirty();
                                    return finishTransfer(slot, player, original);
                                }
                            }
                        }
                    }
                }
            }

            return ItemStack.EMPTY;
        }

        // From container slots to player inventory
        if (slotIndex == SLOT_PATTERN) {
            CrafterEntry entry = getCurrentEntry();
            if (entry != null && entry.hasPattern()) {
                ItemStack pattern = entry.getPatternStack().copy();

                // Try PMT first if available
                if (pmtManager != null && tryInsertIntoPMT(pattern)) {
                    entry.setPatternStack(null);
                    tile.simulatePattern(syncCurrentPage, null);
                    tile.markDirty();
                    return finishTransfer(slot, player, original);
                }

                // Fall back to player inventory
                if (player.inventory.addItemStackToInventory(pattern)) {
                    entry.setPatternStack(null);
                    tile.simulatePattern(syncCurrentPage, null);
                    tile.markDirty();
                    return finishTransfer(slot, player, original);
                }
            }
        } else if (slotIndex >= SLOT_CATALYST_START && slotIndex < SLOT_UPGRADE_START) {
            int catalystIdx = slotIndex - SLOT_CATALYST_START;
            CrafterEntry entry = getCurrentEntry();
            if (entry != null) {
                ItemStack catalyst = entry.getCatalystStack(catalystIdx);
                if (!catalyst.isEmpty()) {
                    if (player.inventory.addItemStackToInventory(catalyst.copy())) {
                        entry.setCatalystStack(catalystIdx, ItemStack.EMPTY);
                        tile.validateCatalysts(syncCurrentPage);
                        tile.markDirty();
                        return finishTransfer(slot, player, original);
                    }
                }
            }
        } else if (slotIndex >= SLOT_UPGRADE_START && slotIndex < SLOT_PLAYER_START) {
            int upgradeIdx = slotIndex - SLOT_UPGRADE_START;
            ItemStack upgrade = tile.getUpgradeStack(upgradeIdx);
            if (!upgrade.isEmpty()) {
                if (player.inventory.addItemStackToInventory(upgrade.copy())) {
                    tile.setUpgradeStack(upgradeIdx, ItemStack.EMPTY);
                    return finishTransfer(slot, player, original);
                }
            }
        }

        return ItemStack.EMPTY;
    }

    /**
     * Update the player inventory and slot after a successful transfer.
     * This prevents stale client-side state and ensures the slot is cleared or updated correctly.
     * @param slot The slot that was transferred.
     * @param player The player performing the transfer.
     * @param original The original ItemStack before the transfer.
     * @return The ItemStack that remains after the transfer.
     */
    private ItemStack finishTransfer(Slot slot, EntityPlayer player, ItemStack original) {
        ItemStack remaining = slot.getStack();
        if (remaining.isEmpty()) {
            slot.putStack(ItemStack.EMPTY);
        } else {
            slot.onSlotChanged();
        }

        slot.onTake(player, remaining);
        return original;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return tile.getWorld().getTileEntity(tile.getPos()) == tile &&
                player.getDistanceSq(tile.getPos().getX() + 0.5,
                        tile.getPos().getY() + 0.5,
                        tile.getPos().getZ() + 0.5) <= 64.0;
    }

    // ==================== CUSTOM SLOTS ====================

    /**
     * IItemHandler for pattern slot that reads/writes from tile's entry.
     * Properly implements insertItem/extractItem for working slot mechanics.
     * <p>
     * Implements {@link IItemHandlerModifiable} so vanilla's slot sync
     * ({@code Container.putStackInSlot} -> {@code AppEngSlot.putStack} ->
     * {@code ItemHandlerUtil.setStackInSlot}) can write through directly without
     * being rejected by {@link #isItemValid}. The client never has recipe info
     * (it's not synced via NBT), so insertion-based fallback would silently drop
     * server-pushed slot updates and leave the client display stale.
     */
    private static class PatternItemHandler implements IItemHandlerModifiable {
        private final ContainerAutoCrafter container;
        private final TileAutoCrafter tile;
        private int entryIndex;

        PatternItemHandler(ContainerAutoCrafter container, TileAutoCrafter tile, int entryIndex) {
            this.container = container;
            this.tile = tile;
            this.entryIndex = entryIndex;
        }

        @Override
        public void setStackInSlot(int slot, @Nonnull ItemStack stack) {
            CrafterEntry entry = getEntry();
            if (entry == null) return;

            ItemStack toStore = stack.isEmpty() ? null : stack;
            entry.setPatternStack(toStore);
            // Server only: rebuild recipe info / cache snapshots. On client this
            // method is invoked from vanilla's slot sync to mirror server state and
            // there's no recipe pipeline to invoke.
            if (Platform.isServer()) {
                tile.simulatePattern(entryIndex, toStore);
                tile.markDirty();
            }
        }

        void setEntryIndex(int index) {
            this.entryIndex = index;
        }

        private CrafterEntry getEntry() {
            return tile.getEntry(entryIndex);
        }

        @Override
        public int getSlots() {
            return 1;
        }

        @Nonnull
        @Override
        public ItemStack getStackInSlot(int slot) {
            CrafterEntry entry = getEntry();
            return entry != null && entry.hasPattern() ? entry.getPatternStack() : ItemStack.EMPTY;
        }

        @Nonnull
        @Override
        public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
            if (!isItemValid(slot, stack)) return stack;

            CrafterEntry entry = getEntry();
            if (entry == null || entry.hasPattern()) return stack;

            if (!simulate) {
                ItemStack toInsert = stack.copy();
                toInsert.setCount(1);
                entry.setPatternStack(toInsert);
                tile.simulatePattern(entryIndex, toInsert);
                tile.markDirty();
            }

            ItemStack remaining = stack.copy();
            remaining.shrink(1);
            return remaining;
        }

        @Nonnull
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            CrafterEntry entry = getEntry();
            if (entry == null || !entry.hasPattern()) return ItemStack.EMPTY;

            ItemStack pattern = entry.getPatternStack().copy();
            if (!simulate) {
                entry.setPatternStack(null);
                tile.simulatePattern(entryIndex, null);
                tile.markDirty();
            }

            return pattern;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            return container.isValidPattern(stack);
        }
    }

    /**
     * IItemHandler for catalyst slot that reads/writes from tile's entry.
     * Strictly validates items against the recipe's expected catalyst for this slot.
     * <p>
     * Implements {@link IItemHandlerModifiable} so vanilla's slot sync can write
     * through directly. See {@link PatternItemHandler} for the rationale - in short,
     * the client never has recipe info, so {@link #isItemValid} would reject the
     * server-pushed catalyst stacks and the client's slot would visually never
     * update past whatever the click handler set locally. That stale state is what
     * caused the catalyst-duplication exploit (catalyst appearing on a page that
     * actually has no catalyst, server-side).
     */
    private static class CatalystItemHandler implements IItemHandlerModifiable {
        private final ContainerAutoCrafter container;
        private final TileAutoCrafter tile;
        private final int catalystIndex;
        private int entryIndex;

        CatalystItemHandler(ContainerAutoCrafter container, TileAutoCrafter tile, int catalystIndex, int entryIndex) {
            this.container = container;
            this.tile = tile;
            this.catalystIndex = catalystIndex;
            this.entryIndex = entryIndex;
        }

        @Override
        public void setStackInSlot(int slot, @Nonnull ItemStack stack) {
            CrafterEntry entry = getEntry();
            if (entry == null) return;

            entry.setCatalystStack(catalystIndex, stack);
            // Only re-validate / mark dirty server-side. On the client this is the
            // sync mirror path; we just need the underlying storage updated so the
            // slot's getStack reflects the server's view.
            if (Platform.isServer()) {
                tile.validateCatalysts(entryIndex);
                tile.markDirty();
            }
        }

        void setEntryIndex(int index) {
            this.entryIndex = index;
        }

        private CrafterEntry getEntry() {
            return tile.getEntry(entryIndex);
        }

        @Override
        public int getSlots() {
            return 1;
        }

        @Nonnull
        @Override
        public ItemStack getStackInSlot(int slot) {
            CrafterEntry entry = getEntry();
            return entry != null ? entry.getCatalystStack(catalystIndex) : ItemStack.EMPTY;
        }

        @Nonnull
        @Override
        public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
            if (!isItemValid(slot, stack)) return stack;

            CrafterEntry entry = getEntry();
            if (entry == null) return stack;

            ItemStack existing = entry.getCatalystStack(catalystIndex);
            if (!existing.isEmpty()) return stack; // Slot occupied

            if (!simulate) {
                ItemStack toInsert = stack.copy();
                toInsert.setCount(1);
                entry.setCatalystStack(catalystIndex, toInsert);
                tile.validateCatalysts(entryIndex);
                tile.markDirty();
            }

            ItemStack remaining = stack.copy();
            remaining.shrink(1);
            return remaining;
        }

        @Nonnull
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            CrafterEntry entry = getEntry();
            if (entry == null) return ItemStack.EMPTY;

            ItemStack existing = entry.getCatalystStack(catalystIndex);
            if (existing.isEmpty()) return ItemStack.EMPTY;

            ItemStack result = existing.copy();
            if (!simulate) {
                entry.setCatalystStack(catalystIndex, ItemStack.EMPTY);
                tile.validateCatalysts(entryIndex);
                tile.markDirty();
            }

            return result;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            if (stack.isEmpty()) return true; // Empty is always valid

            CrafterEntry entry = getEntry();
            if (entry == null || !entry.hasValidRecipeInfo()) return false;

            CrafterRecipeInfo info = entry.getRecipeInfo();
            if (info == null) return false;

            // Find expected item for this catalyst slot (1:1 mapping)
            ItemStack expected = ItemStack.EMPTY;
            for (CrafterRecipeInfo.IngredientInfo catalyst : info.getCatalystSlots()) {
                if (catalyst.getSlotIndex() == catalystIndex) {
                    expected = catalyst.getItem() != null ? catalyst.getItem().createItemStack() : ItemStack.EMPTY;
                    break;
                }
            }

            // No expected item = slot is not a catalyst slot, reject all items
            if (expected.isEmpty()) return false;

            // Validate item matches expected (inclusive NBT matching)
            return container.isValidCatalystItem(stack, expected, catalystIndex);
        }
    }

    /**
     * IItemHandler for upgrade slot.
     * Only accepts speed upgrades. Properly implements insertItem/extractItem.
     * <p>
     * Implements {@link IItemHandlerModifiable} for the same client-sync reason
     * as {@link PatternItemHandler}. Upgrade slots store data on the tile (not
     * per-entry), but the same vanilla slot sync mechanism applies.
     */
    private static class UpgradeItemHandler implements IItemHandlerModifiable {
        private final TileAutoCrafter tile;
        private final int upgradeIndex;

        UpgradeItemHandler(TileAutoCrafter tile, int upgradeIndex) {
            this.tile = tile;
            this.upgradeIndex = upgradeIndex;
        }

        @Override
        public void setStackInSlot(int slot, @Nonnull ItemStack stack) {
            tile.setUpgradeStack(upgradeIndex, stack);
        }

        @Override
        public int getSlots() {
            return 1;
        }

        @Nonnull
        @Override
        public ItemStack getStackInSlot(int slot) {
            return tile.getUpgradeStack(upgradeIndex);
        }

        @Nonnull
        @Override
        public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
            if (!isItemValid(slot, stack)) return stack;

            ItemStack existing = tile.getUpgradeStack(upgradeIndex);
            if (!existing.isEmpty()) return stack; // Slot occupied

            if (!simulate) {
                ItemStack toInsert = stack.copy();
                toInsert.setCount(1);
                tile.setUpgradeStack(upgradeIndex, toInsert);
            }

            ItemStack remaining = stack.copy();
            remaining.shrink(1);
            return remaining;
        }

        @Nonnull
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            ItemStack existing = tile.getUpgradeStack(upgradeIndex);
            if (existing.isEmpty()) return ItemStack.EMPTY;

            ItemStack result = existing.copy();
            if (!simulate) {
                tile.setUpgradeStack(upgradeIndex, ItemStack.EMPTY);
            }

            return result;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            return ItemCrafterSpeedUpgrade.isSpeedUpgrade(stack);
        }
    }

    /**
     * Slot for the pattern item.
     */
    public static class SlotPattern extends AppEngSlot {
        private final PatternItemHandler handler;

        public SlotPattern(ContainerAutoCrafter container, TileAutoCrafter tile, int entryIndex, int x, int y) {
            super(new PatternItemHandler(container, tile, entryIndex), 0, x, y);
            this.handler = (PatternItemHandler) this.getItemHandler();
        }

        public void setEntryIndex(int index) {
            handler.setEntryIndex(index);
        }

        @Override
        public int getSlotStackLimit() {
            return 1;
        }
    }

    /**
     * Slot for catalyst items.
     * Strictly filters to only accept items matching the recipe's expected catalyst.
     */
    public static class SlotCatalyst extends AppEngSlot {
        private final CatalystItemHandler handler;
        private final int catalystIndex;

        public SlotCatalyst(ContainerAutoCrafter container, TileAutoCrafter tile, int catalystIndex,
                int entryIndex, int x, int y) {
            super(new CatalystItemHandler(container, tile, catalystIndex, entryIndex), 0, x, y);
            this.handler = (CatalystItemHandler) this.getItemHandler();
            this.catalystIndex = catalystIndex;
        }

        public void setEntryIndex(int index) {
            handler.setEntryIndex(index);
        }

        public int getCatalystIndex() {
            return catalystIndex;
        }

        @Override
        public int getSlotStackLimit() {
            // Crafting recipes only use 1 item per slot, so catalyst slots should only hold 1 item.
            // If a recipe requires multiple of the same catalyst (e.g., 3 Blood Orbs), 
            // they would go in separate slots (one per input slot that uses them).
            return 1;
        }
    }

    /**
     * Slot for upgrade items.
     * Only accepts speed upgrades.
     */
    public static class SlotUpgrade extends AppEngSlot {
        // UPGRADES icon index from SlotRestrictedInput.PlacableItemType: 13 * 16 + 15 = 223
        private static final int UPGRADE_ICON = 13 * 16 + 15;

        private final int upgradeIndex;

        public SlotUpgrade(ContainerAutoCrafter container, TileAutoCrafter tile, int upgradeIndex, int x, int y) {
            super(new UpgradeItemHandler(tile, upgradeIndex), 0, x, y);
            this.upgradeIndex = upgradeIndex;

            // Set the icon to show when slot is empty (AE2's "insert upgrade" icon)
            this.setIIcon(UPGRADE_ICON);
        }

        public int getUpgradeIndex() {
            return upgradeIndex;
        }

        @Override
        public int getSlotStackLimit() {
            return 1;
        }
    }
}
