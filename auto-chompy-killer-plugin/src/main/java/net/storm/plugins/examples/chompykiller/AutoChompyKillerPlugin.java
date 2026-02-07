package net.storm.plugins.examples.chompykiller;

import com.google.inject.Inject;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.StatChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.ui.overlay.OverlayManager;
import net.storm.api.Static;
import net.storm.api.domain.actors.INPC;
import net.storm.api.domain.actors.IPlayer;
import net.storm.api.domain.items.IInventoryItem;
import net.storm.api.domain.tiles.ITile;
import net.storm.api.domain.tiles.ITileObject;
import net.storm.api.entities.INPCs;
import net.storm.api.events.ConfigChanged;
import net.storm.api.movement.pathfinder.LocalCollisionMap;
import net.storm.api.plugins.PluginDescriptor;
import net.storm.api.plugins.LoopedPlugin;
import net.storm.api.plugins.config.ConfigManager;
import net.storm.sdk.entities.Players;
import net.storm.sdk.entities.TileObjects;
import net.storm.sdk.entities.Tiles;
import net.storm.sdk.items.Inventory;
import net.storm.sdk.movement.Movement;

import org.pf4j.Extension;

import java.util.Comparator;
import java.util.function.Predicate;

@Slf4j
@PluginDescriptor(name = "Auto Chompy Killer")
@Extension
public class AutoChompyKillerPlugin extends LoopedPlugin {

    public String status = "Initializing...";
    public long startTime;
    public boolean isPaused;
    public IPlayer localPlayer;
    public int totalChompiesAttacked = 0;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private AutoChompyKillerOverlay overlay;

    @Override
    public void startUp() {
        startTime = System.currentTimeMillis();
        overlayManager.add(overlay);
        status = "Ready";
    }

    @Override
    public void shutDown() {
        overlayManager.remove(overlay);
        this.status = "";
        this.startTime = 0;
        this.isPaused = false;
        this.localPlayer = null;
        this.totalChompiesAttacked = 0;
    }

    @Override
    public int loop() {
        INPCs npcs = Static.getNpcs();
        INPC chompy = npcs.getNearest(1475);
        INPC swampToad = npcs.getNearest(1473);
        INPC bloatedToad = npcs.getNearest(1474);
        ITileObject SwampBubbles = TileObjects.getNearest(684);
        IPlayer local = Players.getLocal();
        var needMoreSwampToads = Inventory.getCount(2875) < 3;
        var hasAtLeastOneSwampToad = Inventory.getCount(2875) > 0;
        IInventoryItem ogreBellow3 = Inventory.getFirst(2872);
        IInventoryItem ogreBellow2 = Inventory.getFirst(2873);
        IInventoryItem ogreBellow1 = Inventory.getFirst(2874);
        IInventoryItem ogreBellow0 = Inventory.getFirst(2871);
        // Predicate<WorldPoint> notWalkableTile = (wp) -> !wp.equals(local.getWorldLocation());
        boolean fillingBellows = false;
        var needToFillBellows = ogreBellow3 == null && ogreBellow2 == null && ogreBellow1 == null;
        var allBellowsFilled = ogreBellow0 == null;
        localPlayer = local;

        if(local.isInteracting()){
            status = "Interacting with " + local.getInteracting().getName();
            return 50;
        }


        if (chompy != null && !chompy.isDead() && !local.isAnimating()) {
            status = "Attacking Chompy";
            chompy.interact("Attack");
            return -1;
        }

// Movement.getNearestWalkableTile(local.getWorldLocation(), new LocalCollisionMap(false), notWalkableTile);
        if(hasAtLeastOneSwampToad){
            status = "Checking if we are standing on a bloated toad";
            if(bloatedToad != null && Movement.calculateDistance(local.getWorldLocation(), bloatedToad.getWorldLocation()) <=1){
                WorldPoint currentLocation = local.getWorldLocation();
                ITile nearbyTile = Tiles.getSurrounding(currentLocation, 10).stream()
                        .filter(t -> !t.getWorldLocation().equals(currentLocation))
                        .filter(t -> !t.isObstructed())
                        .min(Comparator.comparingInt(t -> t.distanceTo(currentLocation)))
                        .orElse(null);
                
                if (nearbyTile != null) {
                    log.info("curr location: " + currentLocation.toString());
                    log.info("nearest walkable tile: " + nearbyTile.getWorldLocation().toString());
                    status = "Walking away from Bloated Toad";
                    Movement.walkTo(nearbyTile.getWorldLocation());
                    return -1;
                }
            }
            status = "Dropping Bloated Swamp Toad";
            Inventory.getFirst(2875).interact("Drop");
            return -1;
        }
        if(needToFillBellows && local.isIdle()){
            status = "Filling Ogre Bellows";
            log.info("Filling Ogre Bellows");
            SwampBubbles.interact("Suck");
            fillingBellows = true;
            return -5;
        }
        if(swampToad != null && needMoreSwampToads && !local.isAnimating()){
            status = "Inflating Swamp Toad";
            swampToad.interact("Inflate");
            return -1;
        }

        return -1;
    }

    @Subscribe
    private void onConfigChanged(ConfigChanged e) {
    }

    @Subscribe
    private void onStatChanged(StatChanged e) {
    }

    @Provides
    public AutoChompyKillerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(AutoChompyKillerConfig.class);
    }
}

