package net.storm.plugins.examples.looped.tasks;

import lombok.extern.slf4j.Slf4j;
import net.storm.api.domain.items.IBankItem;
import net.storm.api.plugins.Task;
import net.storm.plugins.examples.looped.ExampleLoopedPlugin;
import net.storm.plugins.examples.looped.misc.Constants;
import net.storm.sdk.items.Bank;
import net.storm.sdk.items.Equipment;
import net.storm.sdk.items.Inventory;

@Slf4j
public class EquipmentSetup implements Task {

    private final ExampleLoopedPlugin plugin;

    public EquipmentSetup(ExampleLoopedPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean validate() {
        return !plugin.equipmentSetupComplete;
    }

    @Override
    public int execute() {
        plugin.status = "Setting up equipment...";

        if (!Bank.isOpen()) {
            Bank.open();
            return -1;
        }

        if (ensureEquippedAny(Constants.HAT_OF_THE_EYE, "Hat of the eye")) return -1;
        if (ensureEquippedAny(Constants.ROBE_TOP_OF_THE_EYE, "Robe top of the eye")) return -1;
        if (ensureEquippedAny(Constants.ROBE_BOTTOM_OF_THE_EYE, "Robe bottoms of the eye")) return -1;
        if (ensureEquipped(Constants.BOOTS_OF_THE_EYE, "Boots of the eye")) return -1;

        if (!Equipment.contains(Constants.RUNECRAFT_CAPE) && !Equipment.contains(Constants.RUNECRAFT_CAPE_T)) {
            if (ensureEquipped(Constants.RUNECRAFT_CAPE, "Runecraft cape")) return -1;
            if (ensureEquipped(Constants.RUNECRAFT_CAPE_T, "Runecraft cape(t)")) return -1;
        }

        if (ensureEquipped(Constants.GRACEFUL_GLOVES, "Graceful gloves")) return -1;
        if (ensureEquipped(Constants.ANTI_DRAGON_SHIELD, "Anti-dragon shield")) return -1;

        if (ensureInInventory(Constants.COLOSSAL_POUCH, "Colossal pouch")) return -1;
        if (ensureInInventory(Constants.MYTHICAL_CAPE, "Mythical cape")) return -1;
        if (ensureInInventory(Constants.ETERNAL_GLORY, "Eternal glory")) return -1;

        plugin.equipmentSetupComplete = true;
        log.info("Equipment setup complete!");
        return -1;
    }

    private boolean ensureEquipped(int itemId, String itemName) {
        if (Equipment.contains(itemId)) {
            return false;
        }

        IBankItem bankItem = Bank.getFirst(i -> i != null && i.getId() == itemId && !i.isPlaceholder());
        if (bankItem == null) {
            log.warn("{} not found in bank", itemName);
            return false;
        }

        bankItem.withdraw(1);
        return true;
    }

    private boolean ensureEquippedAny(int[] itemIds, String itemName) {
        for (int itemId : itemIds) {
            if (Equipment.contains(itemId)) {
                return false;
            }
        }

        for (int itemId : itemIds) {
            IBankItem bankItem = Bank.getFirst(i -> i != null && i.getId() == itemId && !i.isPlaceholder());
            if (bankItem != null) {
                bankItem.withdraw(1);
                return true;
            }
        }

        log.warn("{} (any variant) not found in bank", itemName);
        return false;
    }

    private boolean ensureInInventory(int itemId, String itemName) {
        if (Inventory.contains(itemId)) {
            return false;
        }

        IBankItem bankItem = Bank.getFirst(i -> i != null && i.getId() == itemId && !i.isPlaceholder());
        if (bankItem == null) {
            log.warn("{} not found in bank", itemName);
            return false;
        }

        bankItem.withdraw(1);
        return true;
    }
}

