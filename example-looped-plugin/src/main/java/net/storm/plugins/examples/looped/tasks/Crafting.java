package net.storm.plugins.examples.looped.tasks;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Varbits;
import net.storm.api.domain.tiles.ITileObject;
import net.storm.api.plugins.Task;
import net.storm.plugins.examples.looped.ExampleLoopedConfig;
import net.storm.plugins.examples.looped.ExampleLoopedPlugin;
import net.storm.plugins.examples.looped.misc.Constants;
import net.storm.sdk.entities.Players;
import net.storm.sdk.entities.TileObjects;
import net.storm.sdk.game.Vars;
import net.storm.sdk.items.Inventory;

@Slf4j
public class Crafting implements Task {

    private final ExampleLoopedPlugin plugin;
    private final ExampleLoopedConfig config;
    public Crafting(ExampleLoopedPlugin plugin, ExampleLoopedConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    @Override
    public boolean validate() {
        ITileObject altar = TileObjects.getNearest(Constants.ALTAR);
        int pouchQuantity = Vars.getBit(Varbits.ESSENCE_POUCH_COLOSSAL_AMOUNT);
        return altar != null && altar.isInteractable() 
               && (Inventory.contains(Constants.PURE_ESSENCE) || pouchQuantity > 0);
    }

    @Override
    public int execute() {
        plugin.clickedCaveExit = false;
        plugin.colossalPouchQuantity = Vars.getBit(Varbits.ESSENCE_POUCH_COLOSSAL_AMOUNT);

        ITileObject wrathAltar = TileObjects.getNearest(Constants.ALTAR);

        if (wrathAltar == null) {
            log.info("Altar not found");
            return -1;
        }
        if (Inventory.contains(Constants.PURE_ESSENCE)) {
            if(( Players.getLocal().isAnimating() || !Players.getLocal().isIdle()) && config.use1TickCraft()) {
                return -1;
            }
            if(Players.getLocal().isMoving() && wrathAltar.getWorldLocation().distanceTo(Players.getLocal().getWorldLocation()) > 2) {
                return -1;
            }
            plugin.status = "Crafting runes... (Pouch: " + plugin.colossalPouchQuantity + "/40)";
            log.info("Crafting essence in inventory");
            wrathAltar.interact("Craft-rune");
            return -1;
        }

        if (!Inventory.contains(Constants.PURE_ESSENCE) && plugin.colossalPouchQuantity > 0) {
            plugin.status = "Emptying pouch... (" + plugin.colossalPouchQuantity + "/40)";
            log.info("Emptying colossal pouch, quantity: " + plugin.colossalPouchQuantity);
            var colossalPouch = Inventory.getFirst(Constants.COLOSSAL_POUCH);
            if (colossalPouch != null) {
                colossalPouch.interact("Empty");
                if(config.use1TickCraft()){
                    wrathAltar.interact("Craft-rune");
                }
                return -1;
            }
        }

        return -1;
    }
}

