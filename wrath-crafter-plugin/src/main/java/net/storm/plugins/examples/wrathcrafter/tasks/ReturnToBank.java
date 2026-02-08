package net.storm.plugins.examples.wrathcrafter.tasks;

import lombok.extern.slf4j.Slf4j;
import net.storm.api.plugins.Task;
import net.storm.plugins.examples.wrathcrafter.WrathCrafterPlugin;
import net.storm.plugins.examples.wrathcrafter.misc.Constants;
import net.storm.sdk.items.Equipment;
import net.storm.sdk.items.Inventory;

@Slf4j
public class ReturnToBank implements Task {

    private final WrathCrafterPlugin plugin;

    public ReturnToBank(WrathCrafterPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean validate() {
        return plugin.equipmentSetupComplete && 
               !Inventory.contains(Constants.PURE_ESSENCE) && 
               Inventory.contains(Constants.WRATH_RUNE);
    }

    @Override
    public int execute() {
        plugin.status = "Returning to bank...";
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

