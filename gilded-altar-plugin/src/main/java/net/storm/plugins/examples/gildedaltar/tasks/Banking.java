package net.storm.plugins.examples.gildedaltar.tasks;

import net.storm.api.plugins.Task;
import net.storm.plugins.examples.gildedaltar.GildedAltarConfig;
import net.storm.plugins.examples.gildedaltar.GildedAltarPlugin;
import net.storm.plugins.examples.gildedaltar.misc.Constants;
import net.storm.sdk.items.Bank;
import net.storm.sdk.items.Inventory;
import lombok.extern.slf4j.Slf4j;
import net.storm.api.domain.items.IBankItem;
import net.storm.sdk.items.Equipment;
import net.storm.sdk.entities.TileObjects;
import net.storm.api.items.WithdrawMode;

import java.util.Random;


@Slf4j
public class Banking implements Task {

    private final GildedAltarPlugin plugin;
    private final GildedAltarConfig config;
    private final Random random = new Random();

    public Banking(GildedAltarPlugin plugin, GildedAltarConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    @Override
    public boolean validate() {
        plugin.status = "Checking for banking...";
        return !plugin.bankingComplete;
    }

    public int execute() {

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
            return random.nextBoolean() ? -1 : random.nextInt(401) + 700;
        }

        plugin.status = "Banking...";
        
        if (!Inventory.isFull()) {
            if (!Inventory.contains(Constants.LAW_RUNE)) {
                plugin.status = "Withdrawing Law Rune...";
                withdrawItem(Constants.LAW_RUNE, 1);
                return random.nextBoolean() ? -1 : random.nextInt(401) + 700;
            }
            if (!Inventory.contains(Constants.DUST_RUNE)) {
                plugin.status = "Withdrawing Dust Rune...";
                withdrawItem(Constants.DUST_RUNE, 1);
                return random.nextBoolean() ? -1 : random.nextInt(401) + 700;
            }
            IBankItem dragonBones = Bank.getFirst(i -> i != null && i.getId() == Constants.DRAGON_BONES && !i.isPlaceholder());
            if (dragonBones != null) {
                log.info("Withdrawing Dragon Bones... (Free slots: " + Inventory.getFreeSlots() + ")");
                plugin.status = "Withdrawing Dragon Bones...";
                Bank.withdrawAll(Constants.DRAGON_BONES);
                return random.nextBoolean() ? -1 : random.nextInt(401) + 700;
            }
        }

        Bank.close();
        plugin.status = "Banking complete...";
        plugin.bankingComplete = true;
        return random.nextBoolean() ? -1 : random.nextInt(401) + 700;
    }

    private void withdrawItem(int itemId, int amount) {
        IBankItem item = Bank.getFirst(i -> i != null && i.getId() == itemId && !i.isPlaceholder());
        if (item != null) {
            Bank.withdraw(itemId, amount, WithdrawMode.ITEM);
        }
    }
}

