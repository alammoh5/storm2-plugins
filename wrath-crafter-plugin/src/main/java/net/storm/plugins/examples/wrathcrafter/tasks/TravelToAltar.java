package net.storm.plugins.examples.wrathcrafter.tasks;

import lombok.extern.slf4j.Slf4j;
import net.storm.api.domain.tiles.ITileObject;
import net.storm.api.plugins.Task;
import net.storm.plugins.examples.wrathcrafter.WrathCrafterPlugin;
import net.storm.plugins.examples.wrathcrafter.misc.Constants;
import net.storm.sdk.entities.Players;
import net.storm.sdk.entities.TileObjects;
import net.storm.sdk.items.Inventory;
import net.storm.sdk.movement.Movement;

@Slf4j
public class TravelToAltar implements Task {

    private final WrathCrafterPlugin plugin;

    public TravelToAltar(WrathCrafterPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean validate() {
        
        ITileObject altar = TileObjects.getNearest(Constants.ALTAR);
        return plugin.equipmentSetupComplete &&
               Inventory.contains(Constants.PURE_ESSENCE) &&
               plugin.colossalPouchQuantity == 40 &&
               altar == null;
    }

    @Override
    public int execute() {
        ITileObject statue = TileObjects.getNearest(Constants.STATUE_ID);
        ITileObject wrathAltarRuins = TileObjects.getNearest(Constants.MYSTERIOUS_RUINS);
        ITileObject caveExit = TileObjects.getNearest(Constants.CAVE_EXIT);
        ITileObject fountain = TileObjects.getNearest(Constants.CAVE_FOUNTAIN);

        plugin.locationInfo = "Statue: " + (statue != null ? "Found" : "None") + 
                             " | Ruins: " + (wrathAltarRuins != null ? "Found" : "None") + 
                             " | Cave: " + (caveExit != null ? "Found" : "None") +
                             " | Fountain: " + (fountain != null ? "Found" : "None");

        if (statue == null && caveExit == null) {
            plugin.status = "Teleporting to Myths' Guild";
            var mythCape = Inventory.getFirst(Constants.MYTHICAL_CAPE);
            if (mythCape != null) {
                log.info("Using Mythical cape teleport");
                mythCape.interact("Teleport");
                return 3000;
            }
            plugin.status = "ERROR: No teleport found!";
            log.error("Cannot find mythical cape for teleport!");
            return -1;
        }

        if (statue != null && statue.isInteractable()) {
            plugin.status = "Entering statue cave";
            log.info("Entering Mythic Statue");
            if(!Players.getLocal().isMoving() && !Players.getLocal().isAnimating() && Players.getLocal().isIdle()) {
                statue.interact("Enter"); 
            }           
            return -1;
        }
        if (fountain != null && !caveExit.isInteractable()) {
            plugin.status = "Walking to cave";
            log.info("Walking to cave");
            Movement.walkTo(Constants.CAVE_WALK_POINT);
            return -1;
        }

        if (caveExit != null && caveExit.isInteractable()) {
            if (!plugin.clickedCaveExit) {
                plugin.clickedCaveExit = true;
                plugin.status = "Exiting cave";
                log.info("Exiting cave");
                caveExit.interact("Enter");
            } else {
                plugin.status = "Walking to cave exit";
            }
            return -1;
        }

        if (wrathAltarRuins != null && wrathAltarRuins.isInteractable()) {
            if (!Players.getLocal().isMoving() && !Players.getLocal().isAnimating() && Players.getLocal().isIdle()) {
                plugin.status = "Entering altar ruins";
                log.info("Entering Wrath altar ruins");
                wrathAltarRuins.interact("Enter");
            } else {
                plugin.status = "Walking to ruins";
            }
            return -1;
        }

        plugin.status = "Walking to altar...";
        return -1;
    }
}

