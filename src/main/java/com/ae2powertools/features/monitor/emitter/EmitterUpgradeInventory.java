package com.ae2powertools.features.monitor.emitter;

import appeng.api.config.Upgrades;
import appeng.parts.automation.UpgradeInventory;
import appeng.util.inv.IAEAppEngInventory;


/**
 * Dedicated two-slot upgrade inventory for storage level emitters.
 * Only fuzzy and crafting cards are accepted, one of each.
 */
public class EmitterUpgradeInventory extends UpgradeInventory {

    public static final int SLOT_COUNT = 2;

    public EmitterUpgradeInventory(IAEAppEngInventory parent) {
        super(parent, SLOT_COUNT);
    }

    @Override
    public int getMaxInstalled(Upgrades upgrades) {
        switch (upgrades) {
            case FUZZY:
            case CRAFTING:
                return 1;

            default:
                return 0;
        }
    }
}