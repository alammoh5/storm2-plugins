package net.storm.plugins.examples.autoconstruction;

import com.google.inject.Inject;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.api.events.StatChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.ui.overlay.OverlayManager;
import net.storm.api.domain.actors.IPlayer;
import net.storm.api.domain.tiles.ITileObject;
import net.storm.api.events.ConfigChanged;
import net.storm.api.plugins.PluginDescriptor;
import net.storm.api.plugins.LoopedPlugin;
import net.storm.api.plugins.config.ConfigManager;
import net.storm.api.widgets.Tab;
import net.storm.plugins.examples.autoconstruction.misc.Constants;
import net.storm.sdk.entities.Players;
import net.storm.sdk.entities.TileObjects;
import net.storm.sdk.game.Skills;
import net.storm.sdk.items.Inventory;
import org.pf4j.Extension;
import net.storm.sdk.widgets.Widgets;
import net.storm.sdk.widgets.Dialog;
import net.storm.sdk.widgets.Tabs;


/*

FLOW

From Wiki:
Start with 12 planks and 1 built bench.
Call servant and send the Demon Butler to bring 24 Mahogany Planks from your bank.
On the next tick, click to remove the built bench.
On the next tick, click to build on the other empty hotspot.
Every time you build a bench, repeat steps 3 and 4 until you run out of planks.
The Demon Butler will return and restock your planks.
Remove and build one bench using the new supplies before returning to step 2.

Code Flow:
CALL_BUTLER -> CALL_BUTLER_WAIT ->  REMOVE_BENCH_1 -> REMOVE_BENCH_1_WAIT ->  BUILD_BENCH_2 -> BUILD_BENCH_2_WAIT (Planks: 12 -> 6) -> REMOVE_BENCH_2 -> REMOVE_BENCH_2_WAIT ->  BUILD_BENCH_1 -> BUILD_BENCH_1_WAIT (Planks: 6 -> 0) -> REMOVE_BENCH_1 -> REMOVE_BENCH_1_WAIT -> WAIT_FOR_BUTLER_RESTOCK (Planks: 0 -> 24) -> BUILD_BENCH_2 -> BUILD_BENCH_2_WAIT (Planks: 24 -> 18) ->REMOVE_BENCH_2 -> REMOVE_BENCH_2_WAIT ->  BUILD_BENCH_1 -> BUILD_BENCH_1_WAIT (Planks: 18 -> 12) REPEAT

*/


@Slf4j
@PluginDescriptor(name = "Auto Construction")
@Extension
public class AutoConstructionPlugin extends LoopedPlugin {

    public String status = "Initializing...";
    public long startTime;
    public boolean isPaused;
    
    public int totalXpGained = 0;
    public int totalBenchesBuilt = 0;
    private int startingConstructionXp = -1;
    
    private boolean butlerCalled = false;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private AutoConstructionOverlay overlay;

    @Override
    public void startUp() {
        startTime = System.currentTimeMillis();
        startingConstructionXp = Skills.getExperience(Skill.CONSTRUCTION);
        overlayManager.add(overlay);
        status = "Ready";
    }

    @Override
    public void shutDown() {
        overlayManager.remove(overlay);
        this.totalXpGained = 0;
        this.totalBenchesBuilt = 0;
        this.startingConstructionXp = -1;
        this.status = "";
        this.startTime = 0;
        this.isPaused = false;
        this.butlerCalled = false;
    }

    public int callServant() {
        // this function calls the demon butler
        var callServantWidget = Widgets.get(370, 22);
        var houseOptionsWidget = Widgets.get(116, 31);
        if (!Tabs.isOpen(Tab.OPTIONS)) {
            Tabs.open(Tab.OPTIONS);
        }
        if(callServantWidget == null) {
            if(houseOptionsWidget == null) {
                status = "Opening options tab...";
                log.info("Opening options tab");
                return 50;
            }
            status = "Opening house options...";
            houseOptionsWidget.click();
            log.info("Opening house options");
        }
        status = "Calling servant to fetch planks...";
        log.info("Calling servant to fetch planks");
        callServantWidget.interact("Call Servant");
        return -1;
    }

// Enum for FSM states
private enum State {
    CALL_BUTLER,
    CALL_BUTLER_WAIT,
    REMOVE_BENCH_1,
    REMOVE_BENCH_1_WAIT,
    BUILD_BENCH_2,
    BUILD_BENCH_2_WAIT,
    REMOVE_BENCH_2,
    REMOVE_BENCH_2_WAIT,
    BUILD_BENCH_1,
    BUILD_BENCH_1_WAIT,
    WAIT_FOR_BUTLER_RESTOCK,
    ERROR
}

// State variables
private State currentState = State.CALL_BUTLER;

@Override
public int loop() {
    ITileObject bench1Unbuilt = TileObjects.getNearest(Constants.BENCH_SPOT_1_POINT, Constants.BENCH_SPOT_1_UNBUILT);
    ITileObject bench2Unbuilt = TileObjects.getNearest(Constants.BENCH_SPOT_2_POINT, Constants.BENCH_SPOT_2_UNBUILT);
    ITileObject bench1BuiltObj = TileObjects.getNearest(Constants.BENCH_SPOT_1_POINT, Constants.BENCH_SPOT_1_BUILT);
    ITileObject bench2BuiltObj = TileObjects.getNearest(Constants.BENCH_SPOT_2_POINT, Constants.BENCH_SPOT_2_BUILT);
    boolean bench1Built = bench1BuiltObj != null && bench1BuiltObj.isInteractable();
    boolean bench2Built = bench2BuiltObj != null && bench2BuiltObj.isInteractable();
    IPlayer local = Players.getLocal();

    log.info("Current state: " + currentState);
    
    if (local.isMoving()) {
        return -1;
    }
    
    // Count current planks
    int planks = Inventory.getCount("Mahogany plank");
    
    switch (currentState) {
        
        case CALL_BUTLER:
            status = "Calling butler for planks...";
            log.info("Calling butler for planks");
            int result = callServant();
            if (result == -1) {  // Successfully called
                butlerCalled = true;
                currentState = State.CALL_BUTLER_WAIT;
                return -1;  // Next tick
            }
            return 10;  // Still opening menus
        
        case REMOVE_BENCH_1:
            status = "Removing bench 1...";
            log.info("Removing bench 1");
            if (removeBench(bench1BuiltObj)) {
                currentState = State.REMOVE_BENCH_1_WAIT;
                return -1;  // Next tick for dialog to appear
            }
            return 10;  // Retry
        
        case BUILD_BENCH_2:
            status = "Building bench 2...";
            log.info("Building bench 2");
            if (buildBench(bench2Unbuilt)) {
                currentState = State.BUILD_BENCH_2_WAIT;
                return -1;  // Next tick for widget to appear
            }
            return 10;  // Retry
        
        case BUILD_BENCH_2_WAIT:
            log.info("Building bench 2 wait");
            var buildWidget2 = Widgets.get(458, 5);
            if (buildWidget2 != null && buildWidget2.hasAction("Build")) {
                status = "Building from widget...";
                log.info("Building mahogany bench from widget");
                buildWidget2.interact("Build");
                totalBenchesBuilt++;
                
                // After building bench 2, always go to REMOVE_BENCH_2
                // Flow: BUILD_BENCH_2_WAIT (12->6 or 24->18) -> REMOVE_BENCH_2
                currentState = State.REMOVE_BENCH_2;
                return -1;  // Next tick
            }
            return 10;  // Wait for widget
        
        case REMOVE_BENCH_2:
            status = "Removing bench 2...";
            log.info("Removing bench 2");
            if (removeBench(bench2BuiltObj)) {
                currentState = State.REMOVE_BENCH_2_WAIT;
                return 300;  // Wait 300ms for dialog to appear
            }
            return 10;  // Retry
        
        case BUILD_BENCH_1:
            status = "Building bench 1...";
            log.info("Building bench 1");
            if (buildBench(bench1Unbuilt)) {
                currentState = State.BUILD_BENCH_1_WAIT;
                return -1;  // Next tick for widget to appear
            }
            return 10;  // Retry
        
        case BUILD_BENCH_1_WAIT:
            log.info("Building bench 1 wait");
            var buildWidget1 = Widgets.get(458, 5);
            if (buildWidget1 != null && buildWidget1.hasAction("Build")) {
                status = "Building from widget...";
                log.info("Building mahogany bench from widget");
                buildWidget1.interact("Build");
                totalBenchesBuilt++;
                
                // After building, check if we have planks for next cycle
                if (planks <= 6) {
                    currentState = State.REMOVE_BENCH_1;
                } else {
                    // Call butler to go grab more planks
                    currentState = State.CALL_BUTLER;
                }
                return -1;  // Building takes 5 ticks
            }
            return 10;  // Wait for widget
        
        case WAIT_FOR_BUTLER_RESTOCK:
            status = "Waiting for butler to return with planks...";
            log.info("Waiting for butler to return with planks");
            if (Dialog.isOpen()) {
                if (Dialog.canContinue()) {
                    // This is the butler returning with planks (0 -> 24)
                    status = "Receiving planks from butler...";
                    log.info("Receiving planks from butler");
                    Dialog.continueSpace();
                    butlerCalled = false;
                    // After receiving planks, go to BUILD_BENCH_2 (24 -> 18)
                    currentState = State.BUILD_BENCH_2;
                    return 10;  // continue immediately
                }
                else{
                    status = "WAIT_FOR_BUTLER_RESTOCK DIALOG ERROR";
                    log.info("WAIT_FOR_BUTLER_RESTOCK DIALOG ERROR");
                    currentState = State.ERROR;
                    return 10;
                }
            }
            return 10;

 
        case CALL_BUTLER_WAIT:
            status = "Waiting for butler to come...";
            log.info("Waiting for butler to come");
            if (Dialog.isOpen()) {
                if (!Dialog.canContinue()) {
                    status = "Butler came";
                    log.info("Butler came");
                    status = "Sending butler to fetch 24 planks";
                    log.info("Sending butler to fetch 24 planks");
                    Dialog.chooseOption(1);
                    currentState = State.REMOVE_BENCH_1;
                    return 10;  // continue immediately
                }
                else{
                    status = "CALL_BUTLER_WAIT DIALOG ERROR";
                    log.info("CALL_BUTLER_WAIT DIALOG ERROR");
                    currentState = State.ERROR;
                    return 10;
                }
            }
            return 10;

        case REMOVE_BENCH_1_WAIT:
            status = "Waiting for dialog to open...";
            log.info("Waiting for dialog to open");
            if (Dialog.isOpen()) {
                if (!Dialog.canContinue()) {
                    status = "Bench 1 Dialog opened";
                    log.info("Bench 1 Dialog opened");
                    Dialog.chooseOption(1);
                    
                    // Check plank count to determine next state
                    // If planks are 0, we need to wait for butler to return
                    // Otherwise, continue to BUILD_BENCH_2
                    if (planks == 0) {
                        currentState = State.WAIT_FOR_BUTLER_RESTOCK;
                    } else {
                        currentState = State.BUILD_BENCH_2;
                    }
                    return -1;  // next tick
                }
                else{
                    status = "REMOVE_BENCH_1_WAIT DIALOG ERROR";
                    log.info("REMOVE_BENCH_1_WAIT DIALOG ERROR");
                    currentState = State.ERROR;
                    return 10;
                }
            }
            return 10;

        case REMOVE_BENCH_2_WAIT:
            status = "Waiting for dialog to open...";
            log.info("Waiting for dialog to open");
            if (Dialog.isOpen()) {
                if (!Dialog.canContinue()) {
                    status = "Bench 2 Dialog opened";
                    log.info("Bench 2 Dialog opened");
                    Dialog.chooseOption(1);
                    currentState = State.BUILD_BENCH_1;
                    return -1;  // next tick
                }
                else{
                    status = "REMOVE_BENCH_2_WAIT DIALOG ERROR";
                    log.info("REMOVE_BENCH_2_WAIT DIALOG ERROR");
                    currentState = State.ERROR;
                    return 10;
                }
            }
            return 50;

        
        default:
            status = "Unknown state!";
            log.info("Unknown state!");
            return -1;  // Next tick
    }
}
// Helper method to remove a bench
private boolean removeBench(ITileObject bench) {
    if (bench != null && bench.isInteractable()) {
        bench.interact("Remove");
        log.info("Removing bench");
        return true;
    }
    return false;    
}

// Helper method to build a bench
private boolean buildBench(ITileObject bench) {
    if (bench != null && bench.isInteractable()) {
        bench.interact("Build");
        log.info("Building bench");
        return true;
    }
    return false;    
}
    

    

    @Subscribe
    private void onConfigChanged(ConfigChanged e) {
        if (!e.getGroup().equals(AutoConstructionConfig.GROUP)) {
            return;
        }

        if (e.getKey().equals("pause")) {
            isPaused = !isPaused;
        }
    }

    @Subscribe
    private void onStatChanged(StatChanged e) {
        if (e.getSkill() != Skill.CONSTRUCTION) {
            return;
        }

        if (startingConstructionXp == -1) {
            startingConstructionXp = Skills.getExperience(Skill.CONSTRUCTION);
        }

        int currentXp = Skills.getExperience(Skill.CONSTRUCTION);
        totalXpGained = currentXp - startingConstructionXp;
        log.info("Construction XP updated (Total gained: " + totalXpGained + ")");
    }

    @Provides
    public AutoConstructionConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(AutoConstructionConfig.class);
    }
}
