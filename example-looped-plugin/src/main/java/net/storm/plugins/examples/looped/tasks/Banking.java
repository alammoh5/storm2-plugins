package net.storm.plugins.examples.looped.tasks;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Varbits;
import net.storm.api.domain.items.IBankItem;
import net.storm.api.items.WithdrawMode;
import net.storm.api.plugins.Task;
import net.storm.plugins.examples.looped.ExampleLoopedConfig;
import net.storm.plugins.examples.looped.ExampleLoopedPlugin;
import net.storm.plugins.examples.looped.misc.Constants;
import net.storm.sdk.entities.TileObjects;
import net.storm.sdk.game.Vars;
import net.storm.sdk.items.Bank;
import net.storm.sdk.items.Equipment;
import net.storm.sdk.items.Inventory;
import net.storm.sdk.movement.Movement;

@Slf4j
public class Banking implements Task {

    private final ExampleLoopedPlugin plugin;
    private final ExampleLoopedConfig config;

    public Banking(ExampleLoopedPlugin plugin, ExampleLoopedConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    @Override
    public boolean validate() {
        return !plugin.bankingComplete;
    }

    @Override
    public int execute() {
        log.info("Colossal Pouch Quantity: " + Vars.getBit(Varbits.ESSENCE_POUCH_COLOSSAL_AMOUNT));
        plugin.colossalPouchQuantity = Vars.getBit(Varbits.ESSENCE_POUCH_COLOSSAL_AMOUNT);
        plugin.status = "Colossal Pouch Quantity: " + plugin.colossalPouchQuantity;

        if (!Bank.isOpen()) {
            var bankBooth = TileObjects.getNearest("Bank booth");
            
            if (bankBooth == null || !bankBooth.isInteractable()) {
                plugin.status = "Teleporting to Edgeville...";
                var eternalGlory = Equipment.getFirst(Constants.ETERNAL_GLORY);
                if (eternalGlory != null) {
                    log.info("Using Eternal Glory to teleport to Edgeville");
                    eternalGlory.interact("Edgeville");
                    return 3000;
                }
                log.error("Eternal Glory not found in equipment!");
                return -1;
            }
            
            plugin.status = "Opening bank...";
            Bank.open();
            plugin.needsPOHRestore = Movement.getRunEnergy() <= config.minRunEnergy();
            return -1;
        }

        plugin.status = "Banking...";

        if (Inventory.contains(Constants.WRATH_RUNE)) {
            Bank.depositAll(Constants.WRATH_RUNE);
            // Update bank count after depositing
            updateBankWrathRuneCount();
            return -1;
        }

        // Update bank wrath rune count
        updateBankWrathRuneCount();

        if (plugin.needsPOHRestore) {
            if (!Inventory.contains(Constants.LAW_RUNE)) {
                plugin.status = "Withdrawing Law Rune...";
                withdrawItem(Constants.LAW_RUNE, 1);
                return -1;
            }
            if (!Inventory.contains(Constants.DUST_RUNE)) {
                plugin.status = "Withdrawing Dust Rune...";
                withdrawItem(Constants.DUST_RUNE, 1);
                return -1;
            }
        }
        
        if (!Inventory.isFull()) {
            IBankItem essence = Bank.getFirst(i -> i != null && i.getId() == Constants.PURE_ESSENCE && !i.isPlaceholder());
            if (essence != null) {
                log.info("Withdrawing Pure Essence... (Free slots: " + Inventory.getFreeSlots() + ")");
                plugin.status = "Withdrawing Pure Essence...";
                Bank.withdrawAll(Constants.PURE_ESSENCE);
                return 200;
            }
        }
        if (Inventory.contains(Constants.PURE_ESSENCE) && plugin.colossalPouchQuantity < 40) {
            log.info("Filling Colossal Pouch... (Current: " + plugin.colossalPouchQuantity + "/40)");
            plugin.status = "Filling Colossal Pouch...";
            Bank.Inventory.getFirst(Constants.COLOSSAL_POUCH).interact("Fill");
            return -1;
        }

            


        Bank.close();
        plugin.status = "Banking complete...";
        plugin.bankingComplete = true;
        return -1;
    }

    private void withdrawItem(int itemId, int amount) {
        IBankItem item = Bank.getFirst(i -> i != null && i.getId() == itemId && !i.isPlaceholder());
        if (item != null) {
            Bank.withdraw(itemId, amount, WithdrawMode.ITEM);
        }
    }

    private void updateBankWrathRuneCount() {
        IBankItem wrathRuneItem = Bank.getFirst(i -> i != null && i.getId() == Constants.WRATH_RUNE && !i.isPlaceholder());
        if (wrathRuneItem != null) {
            plugin.totalWrathRunesInBank = wrathRuneItem.getQuantity();
            plugin.totalWrathRunesGpValue = (long) plugin.totalWrathRunesInBank * config.wrathRunePrice();
        } else {
            plugin.totalWrathRunesInBank = 0;
            plugin.totalWrathRunesGpValue = 0;
        }
    }
}

