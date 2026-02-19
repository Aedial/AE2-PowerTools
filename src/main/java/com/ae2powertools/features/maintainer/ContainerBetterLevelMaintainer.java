package com.ae2powertools.features.maintainer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.google.common.collect.ImmutableCollection;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.AEApi;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.me.GridAccessException;

import com.ae2powertools.network.PacketCraftableItemsSync;
import com.ae2powertools.network.PowerToolsNetwork;


/**
 * Container for the main Better Level Maintainer GUI.
 */
public class ContainerBetterLevelMaintainer extends Container {

    /** How often to refresh craftable items (in ticks). */
    private static final int CRAFTABLE_REFRESH_INTERVAL = 40;  // 2 seconds

    private final TileBetterLevelMaintainer maintainer;
    private final EntityPlayer player;

    // Cached values for change detection
    private int lastOpenSlots = -1;
    private int lastActiveCpus = -1;
    private int lastTotalCpus = -1;
    private int lastRunningRecipes = -1;
    private int lastTotalRecipes = -1;
    private int lastFailedRecipes = -1;
    private int lastPostErrorRecipes = -1;

    // Craftable items sync
    private int craftableRefreshTicker = 0;
    private List<IAEItemStack> lastCraftableItems = new ArrayList<>();

    // Client-side craftable items (synced from server)
    @SideOnly(Side.CLIENT)
    private List<IAEItemStack> clientCraftableItems;

    public ContainerBetterLevelMaintainer(InventoryPlayer playerInv, TileBetterLevelMaintainer maintainer) {
        this.maintainer = maintainer;
        this.player = playerInv.player;

        // Initialize client list if on client side
        if (playerInv.player.world.isRemote) {
            this.clientCraftableItems = new ArrayList<>();
        } else {
            // Server-side: refresh all current quantities when the GUI opens
            maintainer.refreshAllCurrentQuantities();
        }
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return maintainer.getWorld().getTileEntity(maintainer.getPos()) == maintainer
                && player.getDistanceSq(maintainer.getPos()) <= 64.0;
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();

        // Server-side only
        if (player.world.isRemote) return;

        // Send updates when values change
        int openSlots = maintainer.getOpenSlots();
        int activeCpus = maintainer.getActiveCpuCount();
        int totalCpus = maintainer.getTotalCpuCount();
        int runningRecipes = maintainer.getRunningRecipeCount();
        int totalRecipes = maintainer.getTotalRecipeCount();
        int failedRecipes = maintainer.getFailedRecipeCount();
        int postErrorRecipes = maintainer.getPostErrorRecipeCount();

        boolean changed = openSlots != lastOpenSlots ||
                activeCpus != lastActiveCpus ||
                totalCpus != lastTotalCpus ||
                runningRecipes != lastRunningRecipes ||
                totalRecipes != lastTotalRecipes ||
                failedRecipes != lastFailedRecipes ||
                postErrorRecipes != lastPostErrorRecipes;

        if (changed) {
            lastOpenSlots = openSlots;
            lastActiveCpus = activeCpus;
            lastTotalCpus = totalCpus;
            lastRunningRecipes = runningRecipes;
            lastTotalRecipes = totalRecipes;
            lastFailedRecipes = failedRecipes;
            lastPostErrorRecipes = postErrorRecipes;

            // Send sync packet
            for (IContainerListener listener : this.listeners) {
                listener.sendWindowProperty(this, 0, openSlots);
                listener.sendWindowProperty(this, 1, activeCpus);
                listener.sendWindowProperty(this, 2, totalCpus);
                listener.sendWindowProperty(this, 3, runningRecipes);
                listener.sendWindowProperty(this, 4, totalRecipes);
                listener.sendWindowProperty(this, 5, failedRecipes);
                listener.sendWindowProperty(this, 6, postErrorRecipes);
            }
        }

        // Periodically sync craftable items
        craftableRefreshTicker++;
        if (craftableRefreshTicker >= CRAFTABLE_REFRESH_INTERVAL) {
            craftableRefreshTicker = 0;
            syncCraftableItems();
        }
    }

    /**
     * Gathers craftable items from the network and sends them to the client.
     * For each craftable item, we set the stack size to the pattern's output quantity
     * (how many items the recipe produces per craft).
     */
    private void syncCraftableItems() {
        if (!(player instanceof EntityPlayerMP)) return;

        List<IAEItemStack> craftableItems = new ArrayList<>();

        try {
            IStorageGrid storageGrid = maintainer.getProxy().getStorage();
            ICraftingGrid craftingGrid = maintainer.getProxy().getCrafting();
            IItemStorageChannel channel = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
            IMEMonitor<IAEItemStack> storage = storageGrid.getInventory(channel);

            for (IAEItemStack stack : storage.getStorageList()) {
                if (!stack.isCraftable()) continue;

                // Create a copy with the output quantity from the pattern
                IAEItemStack craftableStack = stack.copy();

                // Get patterns for this item to find the output quantity
                ImmutableCollection<ICraftingPatternDetails> patterns = craftingGrid.getCraftingFor(
                        stack, null, 0, player.world);

                if (!patterns.isEmpty()) {
                    // Use the first pattern's output quantity
                    ICraftingPatternDetails pattern = patterns.iterator().next();
                    IAEItemStack primaryOutput = pattern.getPrimaryOutput();
                    if (primaryOutput != null) {
                        craftableStack.setStackSize(primaryOutput.getStackSize());
                    } else {
                        craftableStack.setStackSize(1);
                    }
                } else {
                    // No patterns found - default to 1
                    craftableStack.setStackSize(1);
                }

                craftableItems.add(craftableStack);
            }
        } catch (GridAccessException e) {
            // Grid not available - send empty list
        }

        // Sort by display name
        craftableItems.sort(Comparator.comparing(
                stack -> stack.createItemStack().getDisplayName().toLowerCase()));

        // Send to client
        PacketCraftableItemsSync packet = new PacketCraftableItemsSync(craftableItems);
        PowerToolsNetwork.INSTANCE.sendTo(packet, (EntityPlayerMP) player);

        lastCraftableItems = craftableItems;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void updateProgressBar(int id, int value) {
        switch (id) {
            case 0:
                // openSlots - handled via packet
                break;
            case 1:
                lastActiveCpus = value;
                break;
            case 2:
                lastTotalCpus = value;
                break;
            case 3:
                lastRunningRecipes = value;
                break;
            case 4:
                lastTotalRecipes = value;
                break;
            case 5:
                lastFailedRecipes = value;
                break;
            case 6:
                lastPostErrorRecipes = value;
                break;
        }
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        return ItemStack.EMPTY;
    }

    public TileBetterLevelMaintainer getMaintainer() {
        return maintainer;
    }

    public EntityPlayer getPlayer() {
        return player;
    }

    // Client-side cached values
    public int getActiveCpuCount() {
        return lastActiveCpus >= 0 ? lastActiveCpus : maintainer.getActiveCpuCount();
    }

    public int getTotalCpuCount() {
        return lastTotalCpus >= 0 ? lastTotalCpus : maintainer.getTotalCpuCount();
    }

    public int getRunningRecipeCount() {
        return lastRunningRecipes >= 0 ? lastRunningRecipes : maintainer.getRunningRecipeCount();
    }

    public int getTotalRecipeCount() {
        return lastTotalRecipes >= 0 ? lastTotalRecipes : maintainer.getTotalRecipeCount();
    }

    public int getFailedRecipeCount() {
        return lastFailedRecipes >= 0 ? lastFailedRecipes : maintainer.getFailedRecipeCount();
    }

    public int getPostErrorRecipeCount() {
        return lastPostErrorRecipes >= 0 ? lastPostErrorRecipes : maintainer.getPostErrorRecipeCount();
    }

    /**
     * Called from the client packet handler to update the craftable items list.
     */
    @SideOnly(Side.CLIENT)
    public void setCraftableItems(List<IAEItemStack> items) {
        this.clientCraftableItems = new ArrayList<>(items);
    }

    /**
     * Gets the list of craftable items (client-side only).
     */
    @SideOnly(Side.CLIENT)
    public List<IAEItemStack> getCraftableItems() {
        return Collections.unmodifiableList(clientCraftableItems);
    }

    /**
     * Forces an immediate refresh of craftable items (server-side).
     * Called when entering the GUI to ensure items are available immediately.
     */
    public void forceRefreshCraftableItems() {
        // Will trigger on next detectAndSendChanges
        if (!player.world.isRemote) craftableRefreshTicker = CRAFTABLE_REFRESH_INTERVAL;
    }
}
