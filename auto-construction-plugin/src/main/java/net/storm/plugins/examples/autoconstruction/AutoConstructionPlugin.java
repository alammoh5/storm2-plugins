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
                return 50;
            }
            status = "Opening house options...";
            houseOptionsWidget.click();
        }
        status = "Calling servant to fetch planks...";
        callServantWidget.interact("Call Servant");
        return -1;
    }

// Enum for FSM states
private enum State {
    CALL_BUTLER,
    REMOVE_BENCH_1,
    WAIT_REMOVE_1_DIALOG,
    BUILD_BENCH_2,
    WAIT_BUILD_2_WIDGET,
    REMOVE_BENCH_2,
    WAIT_REMOVE_2_DIALOG,
    BUILD_BENCH_1,
    WAIT_BUILD_1_WIDGET,
    BUILD_SINGLE,
    WAIT_BUILD_SINGLE_WIDGET,
    WAIT
}

// State variables
private State currentState = State.CALL_BUTLER;
private boolean bench1Built = true;
private boolean bench2Built = false;

@Override
public int loop() {
    ITileObject bench1Unbuilt = TileObjects.getNearest(Constants.BENCH_SPOT_1_POINT, Constants.BENCH_SPOT_1_UNBUILT);
    ITileObject bench2Unbuilt = TileObjects.getNearest(Constants.BENCH_SPOT_2_POINT, Constants.BENCH_SPOT_2_UNBUILT);
    ITileObject bench1BuiltObj = TileObjects.getNearest(Constants.BENCH_SPOT_1_POINT, Constants.BENCH_SPOT_1_BUILT);
    ITileObject bench2BuiltObj = TileObjects.getNearest(Constants.BENCH_SPOT_2_POINT, Constants.BENCH_SPOT_2_BUILT);
    IPlayer local = Players.getLocal();
    
    if (local.isMoving()) {
        return -1;
    }
    
    // Handle all dialogs - both butler and remove confirmations
    if (Dialog.isOpen()) {
        if (Dialog.canContinue()) {
            // This is the butler returning with planks
            status = "Receiving planks from butler...";
            Dialog.continueSpace();
            butlerCalled = false;
            currentState = State.WAIT;
            return -1;  // Next tick
        }
        // This is the remove confirmation dialog
        status = "Confirming dialog...";
        Dialog.chooseOption(1);
        
        // Transition based on which remove we're waiting for
        if (currentState == State.WAIT_REMOVE_1_DIALOG) {
            bench1Built = false;
            currentState = State.BUILD_BENCH_2;
        } else if (currentState == State.WAIT_REMOVE_2_DIALOG) {
            bench2Built = false;
            currentState = State.BUILD_BENCH_1;
        }
        return 50;
    }
    
    // Count current planks
    int planks = Inventory.getCount("Mahogany plank");
    
    switch (currentState) {
        
        case CALL_BUTLER:
            status = "Calling butler for planks...";
            int result = callServant();
            if (result == -1) {  // Successfully called
                butlerCalled = true;
                currentState = State.REMOVE_BENCH_1;
                return -1;  // Next tick
            }
            return result;  // Still opening menus
        
        case REMOVE_BENCH_1:
            status = "Removing bench 1...";
            if (removeBench(bench1BuiltObj)) {
                currentState = State.WAIT_REMOVE_1_DIALOG;
                return -1;  // Next tick for dialog to appear
            }
            return 50;  // Retry
        
        case WAIT_REMOVE_1_DIALOG:
            // Waiting for dialog to open (handled above)
            return 50;
        
        case BUILD_BENCH_2:
            status = "Building bench 2...";
            if (buildBench(bench2Unbuilt)) {
                currentState = State.WAIT_BUILD_2_WIDGET;
                return -1;  // Next tick for widget to appear
            }
            return 50;  // Retry
        
        case WAIT_BUILD_2_WIDGET:
            var buildWidget2 = Widgets.get(458, 5);
            if (buildWidget2 != null && buildWidget2.hasAction("Build")) {
                status = "Building from widget...";
                log.info("Building mahogany bench from widget");
                buildWidget2.interact("Build");
                totalBenchesBuilt++;
                bench2Built = true;
                
                // After building, check if we have planks for next cycle
                if (planks - 6 >= 6) {  // Will have 6+ after this build
                    currentState = State.REMOVE_BENCH_2;
                } else {
                    // Out of planks after this - butler should be returning
                    currentState = State.REMOVE_BENCH_1;
                }
                return -1;  // Next tick
            }
            return 50;  // Wait for widget
        
        case REMOVE_BENCH_2:
            status = "Removing bench 2...";
            if (removeBench(bench2BuiltObj)) {
                currentState = State.WAIT_REMOVE_2_DIALOG;
                return -1;  // Next tick for dialog to appear
            }
            return 50;  // Retry
        
        case WAIT_REMOVE_2_DIALOG:
            // Waiting for dialog to open (handled above)
            return 50;
        
        case BUILD_BENCH_1:
            status = "Building bench 1...";
            if (buildBench(bench1Unbuilt)) {
                currentState = State.WAIT_BUILD_1_WIDGET;
                return -1;  // Next tick for widget to appear
            }
            return 50;  // Retry
        
        case WAIT_BUILD_1_WIDGET:
            var buildWidget1 = Widgets.get(458, 5);
            if (buildWidget1 != null && buildWidget1.hasAction("Build")) {
                status = "Building from widget...";
                log.info("Building mahogany bench from widget");
                buildWidget1.interact("Build");
                totalBenchesBuilt++;
                bench1Built = true;
                
                // After building, check if we have planks for next cycle
                if (planks - 6 >= 6) {  // Will have 6+ after this build
                    currentState = State.REMOVE_BENCH_1;
                } else {
                    // Out of planks - butler should arrive soon
                    currentState = State.REMOVE_BENCH_1;
                }
                return -1;  // Next tick
            }
            return 50;  // Wait for widget
        
        case BUILD_SINGLE:
            status = "Building single bench after butler...";
            // Build on whichever hotspot is empty
            if (!bench1Built) {
                if (buildBench(bench1Unbuilt)) {
                    currentState = State.WAIT_BUILD_SINGLE_WIDGET;
                    return -1;  // Next tick for widget
                }
            } else if (!bench2Built) {
                if (buildBench(bench2Unbuilt)) {
                    currentState = State.WAIT_BUILD_SINGLE_WIDGET;
                    return -1;  // Next tick for widget
                }
            } else {
                // Both built (shouldn't happen) - default to remove bench 1
                currentState = State.REMOVE_BENCH_1;
                return -1;  // Next tick
            }
            return 50;  // Retry
        
        case WAIT_BUILD_SINGLE_WIDGET:
            var buildWidgetSingle = Widgets.get(458, 5);
            if (buildWidgetSingle != null && buildWidgetSingle.hasAction("Build")) {
                status = "Building from widget...";
                log.info("Building mahogany bench from widget");
                buildWidgetSingle.interact("Build");
                totalBenchesBuilt++;
                
                // Update which bench was built
                if (!bench1Built) {
                    bench1Built = true;
                } else {
                    bench2Built = true;
                }
                
                currentState = State.REMOVE_BENCH_1;
                return -1;  // Next tick
            }
            return 50;  // Wait for widget
        
        default:
            status = "Unknown state!";
            return -1;  // Next tick
    }
}
// Helper method to remove a bench
private boolean removeBench(ITileObject bench) {
    if (bench != null && bench.isInteractable()) {
        bench.interact("Remove");
        return true;
    }
    return false;    
}

// Helper method to build a bench
private boolean buildBench(ITileObject bench) {
    if (bench != null && bench.isInteractable()) {
        bench.interact("Build");
        return true;
    }
    return false;    
}
    

    // @Override
    // public int loop() {
    //     if (isPaused) {
    //         status = "Paused...";
    //         return 1000;
    //     }

    //     IPlayer local = Players.getLocal();
    //     ITileObject bench1Unbuilt = TileObjects.getNearest(Constants.BENCH_SPOT_1_POINT, Constants.BENCH_SPOT_1_UNBUILT);
    //     ITileObject bench2Unbuilt = TileObjects.getNearest(Constants.BENCH_SPOT_2_POINT, Constants.BENCH_SPOT_2_UNBUILT);
    //     ITileObject bench1Built = TileObjects.getNearest(Constants.BENCH_SPOT_1_POINT, Constants.BENCH_SPOT_1_BUILT);
    //     ITileObject bench2Built = TileObjects.getNearest(Constants.BENCH_SPOT_2_POINT, Constants.BENCH_SPOT_2_BUILT);
        // var buildWidget = Widgets.get(458, 5);

    //     if (local == null) {
    //         return 1000;
    //     }

    //     if (startingConstructionXp == -1) {
    //         startingConstructionXp = Skills.getExperience(Skill.CONSTRUCTION);
    //     }

    //     int currentXp = Skills.getExperience(Skill.CONSTRUCTION);
    //     totalXpGained = currentXp - startingConstructionXp;

    //     int currentPlankCount = Inventory.getCount(true, "Mahogany plank");
    //     log.info("Current plank count: " + currentPlankCount);

    //     if(Dialog.isOpen()) {
    //         if(Dialog.canContinue()) {
    //             // this is when the demon butler hands you the 24 planks
    //             Dialog.continueSpace();
    //             butlerCalled = false;
    //             return -1;
    //         }
    //         // this is the option selected on the chat dialog to remove the bench from bench1Built.interact("Remove");
    //         Dialog.chooseOption(1);
    //         return 50;
    //     }
    //     if (currentPlankCount <= 12 && !butlerCalled) {
    //         butlerCalled = true;
    //         return callServant();
    //     }


        // if (buildWidget != null && buildWidget.hasAction("Build")) {
        //     // this is the widget that opens when you do bench1Unbuilt.interact("Build");
        //     status = "Building from widget...";
        //     log.info("Building mahogany bench from widget");
        //     buildWidget.interact("Build");
        //     totalBenchesBuilt++;
        //     return -5;
        // }

    //     if(bench1Built != null && bench1Built.isInteractable()) {
    //         // this will open the chat dialog to remove the bench
    //         status = "Removing bench...";
    //         log.info("Removing mahogany bench");
    //         bench1Built.interact("Remove");
    //         return -1;
    //     }

    //     if (bench1Unbuilt != null && bench1Unbuilt.isInteractable() && currentPlankCount >= 6) {
    //         // this will open the build widget
    //         status = "Building bench...";
    //         log.info("Building mahogany bench");
    //         bench1Unbuilt.interact("Build");
    //         return -1;
    //     }

    //     return -1;
    // }

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
