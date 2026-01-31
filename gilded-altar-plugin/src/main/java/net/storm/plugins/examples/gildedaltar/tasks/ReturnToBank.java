package net.storm.plugins.examples.gildedaltar.tasks;

import lombok.extern.slf4j.Slf4j;
import net.storm.api.plugins.Task;
import net.storm.plugins.examples.gildedaltar.GildedAltarPlugin;
import net.storm.plugins.examples.gildedaltar.misc.Constants;
import net.storm.sdk.items.Equipment;
import net.storm.sdk.items.Inventory;

@Slf4j
public class ReturnToBank implements Task {

    private final GildedAltarPlugin plugin;

    public ReturnToBank(GildedAltarPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean validate() {
        return !Inventory.contains(Constants.DRAGON_BONES);
    }

    @Override
    public int execute() {
        plugin.status = "Returning to bank...";
        plugin.travelToPOHComplete = false;
        plugin.bankingComplete = false;

        var eternalGlory = Equipment.getFirst(Constants.ETERNAL_GLORY);
        if (eternalGlory == null) {
            log.error("Eternal glory not found!");
            return -1;
        }

        log.info("Teleporting to Edgeville");
        eternalGlory.interact("Edgeville");
        return 3000;
    }
}

