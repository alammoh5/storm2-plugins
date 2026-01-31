package net.storm.plugins.examples.gildedaltar.tasks;

import net.storm.api.domain.tiles.ITileObject;
import net.storm.api.magic.SpellBook;
import net.storm.api.plugins.Task;
import net.storm.plugins.examples.gildedaltar.GildedAltarConfig;
import net.storm.plugins.examples.gildedaltar.GildedAltarPlugin;
import net.storm.plugins.examples.gildedaltar.misc.Constants;
import net.storm.sdk.entities.TileObjects;
import net.storm.sdk.items.Inventory;
import net.storm.sdk.magic.Magic;
import net.storm.sdk.widgets.Dialog;
import net.storm.sdk.widgets.Widgets;

import lombok.extern.slf4j.Slf4j;
import java.util.Random;

@Slf4j
public class TravelToPOH implements Task {

    private final GildedAltarPlugin plugin;
    private final GildedAltarConfig config;
    private final Random random = new Random();

    private boolean enteredFriendHouse = false;
    private boolean waitingForDialog = false;
    private String pendingFriendName = null;
    private long lastTeleportTime = 0;
    private int dialogAttempts = 0;

    public TravelToPOH(GildedAltarPlugin plugin, GildedAltarConfig config) {
        this.plugin = plugin;
        this.config = config;
    }
    @Override
    public boolean validate() {
        return !plugin.travelToPOHComplete && Inventory.contains(Constants.DRAGON_BONES);
    }

    @Override
    public int execute() {
        String friendsName = config.friendsPOHName();
        ITileObject insidePortal = TileObjects.getNearest(Constants.POH_PORTAL_INSIDE);
        ITileObject outsidePortal = TileObjects.getNearest(Constants.POH_PORTAL_OUTSIDE);

        return executeFriendsPOH(friendsName, insidePortal, outsidePortal);
    }

    private int executeFriendsPOH(String friendsName, ITileObject insidePortal, ITileObject outsidePortal) {
        if (insidePortal != null && insidePortal.isInteractable()) {
            plugin.status = "Entered House..";
            enteredFriendHouse = false;
            plugin.travelToPOHComplete = true;
            dialogAttempts = 0;
            return random.nextBoolean() ? -1 : random.nextInt(401) + 700;
        }

        if (!enteredFriendHouse) {
            return handleEnterFriendHouse(friendsName, insidePortal, outsidePortal);
        }

        plugin.status = "Waiting to enter friend's house...";
        return 2000;
    }

    private int handleEnterFriendHouse(String friendsName, ITileObject insidePortal, ITileObject outsidePortal) {
        if (waitingForDialog && pendingFriendName != null) {
            plugin.status = "Entering friend's house... " + dialogAttempts + " attempts.";
            log.info("Entering friend's house... " + dialogAttempts + " attempts.");
            dialogAttempts++;
            if (dialogAttempts > 500) {
                plugin.status = "Failed to enter friend's house after " + dialogAttempts + " attempts. Stopping plugin.";
                log.error("Failed to enter friend's house after {} attempts. Stopping plugin.", dialogAttempts - 1);
                plugin.setStopped(true);
                return -1;
            }
            Dialog.enterName(pendingFriendName);
            waitingForDialog = false;
            return 2000;
        }

        if (insidePortal != null) {
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

