package com.ae2powertools.features.monitor.emitter;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import appeng.api.AEApi;
import appeng.api.config.Upgrades;
import appeng.parts.automation.UpgradeInventory;
import appeng.util.inv.IAEAppEngInventory;

import com.ae2powertools.util.upgrade.ISelectableUpgradeInventory;
import com.ae2powertools.util.upgrade.UpgradeCardDefinition;


/**
 * Dedicated two-slot upgrade inventory for storage level emitters.
 * Only fuzzy and crafting cards are accepted, one of each.
 */
public class EmitterUpgradeInventory extends UpgradeInventory implements ISelectableUpgradeInventory {

    public static final int SLOT_COUNT = 2;

    private final List<UpgradeCardDefinition> supportedCards = Collections.unmodifiableList(Arrays.asList(
        UpgradeCardDefinition.ae2Card(AEApi.instance().definitions().materials().cardFuzzy(), Upgrades.FUZZY, "fuzzy"),
        UpgradeCardDefinition.ae2Card(AEApi.instance().definitions().materials().cardCrafting(), Upgrades.CRAFTING, "crafting")
    ));

    public EmitterUpgradeInventory(IAEAppEngInventory parent) {
        super(parent, SLOT_COUNT);
    }

    @Override
    public List<UpgradeCardDefinition> getSupportedUpgradeCards() {
        return supportedCards;
    }

    @Override
    public String getUpgradeTooltipPrefix() {
        return "gui.ae2powertools.storage_emitter";
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