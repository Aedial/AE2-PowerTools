package com.ae2powertools.features.monitor.emitter;

import appeng.api.config.Upgrades;
import appeng.parts.automation.UpgradeInventory;
import appeng.util.inv.IAEAppEngInventory;

import com.ae2powertools.util.upgrade.ISelectableUpgradeInventory;


/**
 * Emitter host that exposes the two AE2-style monitor cards.
 */
public interface IEmitterCardHost extends IEmitterRedstoneHost, IAEAppEngInventory {

    UpgradeInventory getUpgradeInventory();

    default ISelectableUpgradeInventory getSelectableUpgradeInventory() {
        UpgradeInventory inventory = getUpgradeInventory();
        return inventory instanceof ISelectableUpgradeInventory ? (ISelectableUpgradeInventory) inventory : null;
    }

    int getInstalledUpgrades(Upgrades upgrade);

    default boolean hasFuzzyCard() {
        return getInstalledUpgrades(Upgrades.FUZZY) > 0;
    }

    default boolean hasCraftingCard() {
        return getInstalledUpgrades(Upgrades.CRAFTING) > 0;
    }
}