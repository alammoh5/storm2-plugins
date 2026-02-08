package net.storm.plugins.examples.wrathcrafter.tasks;

import net.storm.api.domain.tiles.ITileObject;
import net.storm.api.magic.SpellBook;
import net.storm.api.plugins.Task;
import net.storm.plugins.examples.wrathcrafter.WrathCrafterConfig;
import net.storm.plugins.examples.wrathcrafter.WrathCrafterPlugin;
import net.storm.plugins.examples.wrathcrafter.misc.Constants;
import net.storm.sdk.entities.TileObjects;
import net.storm.sdk.items.Inventory;
import net.storm.sdk.magic.Magic;
import net.storm.sdk.widgets.Dialog;
import net.storm.sdk.widgets.Widgets;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class POHRestore implements Task {

    private final WrathCrafterPlugin plugin;
    private final WrathCrafterConfig config;
    
    private boolean enteredFriendHouse = false;
    private boolean waitingForDialog = false;
    private String pendingFriendName = null;
    private long lastTeleportTime = 0;
    private int dialogAttempts = 0;

    public POHRestore(WrathCrafterPlugin plugin, WrathCrafterConfig config) {
        this.plugin = plugin;
        this.config = config;
    }
    @Override
    public boolean validate() {
        return plugin.equipmentSetupComplete && plugin.needsPOHRestore && !Inventory.contains(Constants.WRATH_RUNE);
    }

    @Override
    public int execute() {
        String friendsName = config.friendsPOHName();
        ITileObject ornatePool = TileObjects.getNearest(Constants.ORNATE_POOL);
        ITileObject insidePortal = TileObjects.getNearest(Constants.POH_PORTAL_INSIDE);
        ITileObject outsidePortal = TileObjects.getNearest(Constants.POH_PORTAL_OUTSIDE);
        boolean useFriendsPOH = friendsName != null && !friendsName.trim().isEmpty();

        if (useFriendsPOH) {
            return executeFriendsPOH(friendsName.trim(), ornatePool, insidePortal, outsidePortal);
        } else {
            return executeOwnPOH(ornatePool, insidePortal, outsidePortal);
        }
    }

    private int executeOwnPOH(ITileObject ornatePool, ITileObject insidePortal, ITileObject outsidePortal) {
        plugin.status = "Restoring at POH...";

        if (ornatePool == null) {
            plugin.status = "No ornate pool found, teleporting to house...";
            if (!Inventory.contains(Constants.LAW_RUNE) || !Inventory.contains(Constants.DUST_RUNE)) {
                plugin.needsPOHRestore = false;
                return -1;
            }
            var outsideWidget = Widgets.get(218, 31);
            if (outsideWidget != null) {
                outsideWidget.interact("Cast");
                return 4000;
            } else {
                log.info("No cast widget found");
                plugin.setStopped(true);
                return -1;
            }
        }
        if(ornatePool.isInteractable()) {
            plugin.status = "Drinking from ornate pool...";
            ornatePool.interact("Drink");
            plugin.needsPOHRestore = false;
            return 4000;
        }
        return -1;
    }

    private int executeFriendsPOH(String friendsName, ITileObject ornatePool, ITileObject insidePortal, ITileObject outsidePortal) {
        if (ornatePool != null && ornatePool.isInteractable()) {
            plugin.status = "Drinking from ornate pool...";
            ornatePool.interact("Drink");
            plugin.needsPOHRestore = false;
            enteredFriendHouse = false;
            return 4000;
        }

        if (!enteredFriendHouse) {
            return handleEnterFriendHouse(friendsName, ornatePool, insidePortal, outsidePortal);
        }

        plugin.status = "Waiting to enter friend's house...";
        return 2000;
    }

    private int handleEnterFriendHouse(String friendsName, ITileObject ornatePool, ITileObject insidePortal, ITileObject outsidePortal) {
        if (waitingForDialog && pendingFriendName != null) {
            plugin.status = "Entering friend's house... " + dialogAttempts + " attempts.";
            log.info("Entering friend's house... " + dialogAttempts + " attempts.");
            dialogAttempts++;
            if (dialogAttempts > 50) {
                plugin.status = "Failed to enter friend's house after " + dialogAttempts + " attempts. Stopping plugin.";
                log.error("Failed to enter friend's house after {} attempts. Stopping plugin.", dialogAttempts - 1);
                plugin.setStopped(true);
                return -1;
            }
            Dialog.enterName(pendingFriendName);
            waitingForDialog = false;
            return 2000;
        }

        if (ornatePool != null || insidePortal != null) {
            enteredFriendHouse = true;
            dialogAttempts = 0;
            return 2000;
        }

        if (outsidePortal != null && insidePortal == null) {
            plugin.status = "Entering friend's house...";
            outsidePortal.interact("Friend's house");
            pendingFriendName = friendsName;
            waitingForDialog = true;
            return 2000;
        }

        if (insidePortal == null && outsidePortal == null) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastTeleportTime < 5000) {
                plugin.status = "Waiting for house to load...";
                return 2000;
            }
            plugin.status = "Teleporting outside house...";
            var outsideWidget = Widgets.get(218, 31);
            if (outsideWidget != null) {
                outsideWidget.interact("Outside");
                lastTeleportTime = currentTime;
                return 4000;
            } else {
                log.info("No outside widget found");
                plugin.setStopped(true);
                return -1;
            }
        }

        return 1000;
    }
}

