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
import net.storm.plugins.examples.autoconstruction.misc.Constants;
import net.storm.sdk.entities.NPCs;
import net.storm.sdk.entities.Players;
import net.storm.sdk.entities.TileObjects;
import net.storm.sdk.game.Skills;
import net.storm.sdk.items.Inventory;
import org.pf4j.Extension;
import net.storm.sdk.widgets.Widgets;
import net.storm.sdk.widgets.Dialog;



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
    private int previousBenchCount = 0;
    
    private boolean butlerCalled = false;

    @Inject
    private AutoConstructionConfig config;

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
        this.previousBenchCount = 0;
        this.status = "";
        this.startTime = 0;
        this.isPaused = false;
        this.butlerCalled = false;
    }

    @Override
    public int loop() {
        if (isPaused) {
            status = "Paused...";
            return 1000;
        }

        IPlayer local = Players.getLocal();
        if (local == null) {
            return 1000;
        }

        if (startingConstructionXp == -1) {
            startingConstructionXp = Skills.getExperience(Skill.CONSTRUCTION);
        }

        int currentXp = Skills.getExperience(Skill.CONSTRUCTION);
        totalXpGained = currentXp - startingConstructionXp;

        int currentPlankCount = Inventory.getCount(true, "Mahogany plank");
        log.info("Current plank count: " + currentPlankCount);
        
        if (Players.getLocal().isAnimating() || Players.getLocal().isMoving()) {
            return -1;
        }

        var callServantWidget = Widgets.get(370, 22);
        var houseOptionsWidget = Widgets.get(116, 31);
        var optionsTabWidget = Widgets.get(161, 54);
        

        if(Dialog.isOpen()) {
            Dialog.chooseOption(1);
            return -1;
        }
        if (currentPlankCount <= 18 && !butlerCalled) {
            if(callServantWidget == null) {
                if(houseOptionsWidget == null) {
                    optionsTabWidget.click();
                    status = "Opening options tab...";
                    return 50;
                }
                status = "Opening house options...";
                houseOptionsWidget.click();
                return -1;
            }
            status = "Calling servant to fetch planks...";
            callServantWidget.interact("Call Servant");
            butlerCalled = true;
            return -2;
        }
        if(currentPlankCount > 18) {
            status = "Butler has returned with planks...";
            butlerCalled = false;
            return -1;
        }

        var buildWidget = Widgets.get(458, 5);
        if (buildWidget != null && buildWidget.hasAction("Build")) {
            status = "Building from widget...";
            log.info("Building mahogany bench from widget");
            buildWidget.interact("Build");
            totalBenchesBuilt++;
            return -1;
        }

        ITileObject bench1Unbuilt = TileObjects.getNearest(Constants.BENCH_SPOT_1_UNBUILT);
        ITileObject bench2Unbuilt = TileObjects.getNearest(Constants.BENCH_SPOT_2_UNBUILT);
        ITileObject bench1Built = TileObjects.getNearest(Constants.BENCH_SPOT_1_BUILT);
        ITileObject bench2Built = TileObjects.getNearest(Constants.BENCH_SPOT_2_BUILT);
        ITileObject unbuiltBench = bench1Unbuilt != null ? bench1Unbuilt : bench2Unbuilt;
        
        if (unbuiltBench != null && unbuiltBench.isInteractable() && currentPlankCount >= 6) {
            status = "Building bench...";
            log.info("Building mahogany bench");
            unbuiltBench.interact("Build");
            return -1;
        }

        if(bench1Built != null && bench1Built.isInteractable()) {
            status = "Removing bench...";
            log.info("Removing mahogany bench");
            bench1Built.interact("Remove");
            return -1;
        }

        return -1;
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
