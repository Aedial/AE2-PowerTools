package com.ae2powertools.features.maintainer;

import appeng.api.networking.crafting.ICraftingLink;


/**
 * Holds a crafting link restored from NBT after server restart.
 * Unlike MaintainerTask, this class only tracks the link itself and the entry it belongs to.
 * The link is used for AE2 reconciliation and item delivery after restart.
 */
public class PersistedCraftingLink {

    private final int entryIndex;
    private final ICraftingLink link;

    public PersistedCraftingLink(int entryIndex, ICraftingLink link) {
        this.entryIndex = entryIndex;
        this.link = link;
    }

    public int getEntryIndex() {
        return entryIndex;
    }

    public ICraftingLink getLink() {
        return link;
    }

    public boolean isDone() {
        return link.isDone();
    }

    public boolean isCanceled() {
        return link.isCanceled();
    }
}
