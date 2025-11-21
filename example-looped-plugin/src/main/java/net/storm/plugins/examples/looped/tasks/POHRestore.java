package net.storm.plugins.examples.looped.tasks;

import lombok.extern.slf4j.Slf4j;
import net.storm.api.domain.tiles.ITileObject;
import net.storm.api.magic.SpellBook;
import net.storm.api.plugins.Task;
import net.storm.plugins.examples.looped.ExampleLoopedPlugin;
import net.storm.plugins.examples.looped.misc.Constants;
import net.storm.sdk.entities.TileObjects;
import net.storm.sdk.items.Inventory;
import net.storm.sdk.magic.Magic;
    
@Slf4j
public class POHRestore implements Task {

    private final ExampleLoopedPlugin plugin;

    public POHRestore(ExampleLoopedPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean validate() {
        return plugin.equipmentSetupComplete && plugin.needsPOHRestore && !Inventory.contains(Constants.WRATH_RUNE);
    }

    @Override
    public int execute() {
        plugin.status = "Restoring at POH...";

        ITileObject ornatePool = TileObjects.getNearest(Constants.ORNATE_POOL);

        if (ornatePool == null) {
            plugin.status = "No ornate pool found, teleporting to house...";
            if (!Inventory.contains(Constants.LAW_RUNE) || !Inventory.contains(Constants.DUST_RUNE)) {
                log.error("Missing runes for teleport to house!");
                plugin.needsPOHRestore = false;
                return -1;
            }
            Magic.cast(SpellBook.Standard.TELEPORT_TO_HOUSE);
            return 4000;
        }
        if(ornatePool.isInteractable()) {
            plugin.status = "Drinking from ornate pool...";

            ornatePool.interact("Drink");
            plugin.needsPOHRestore = false;
            return 4000;

    }
        return -1;
    }
}

