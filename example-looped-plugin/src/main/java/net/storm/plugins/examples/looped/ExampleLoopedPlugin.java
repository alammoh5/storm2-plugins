package net.storm.plugins.examples.looped;

import com.google.inject.Inject;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.ui.overlay.OverlayManager;
import net.storm.api.domain.actors.IPlayer;
import net.storm.api.events.ConfigChanged;
import net.storm.api.plugins.PluginDescriptor;
import net.storm.api.plugins.Task;
import net.storm.api.plugins.TaskPlugin;
import net.storm.api.plugins.config.ConfigManager;
import net.storm.plugins.examples.looped.misc.Constants;
import net.storm.plugins.examples.looped.tasks.*;
import net.storm.sdk.entities.Players;
import net.storm.sdk.game.Skills;
import net.storm.sdk.items.Inventory;
import org.pf4j.Extension;

@Slf4j
@PluginDescriptor(name = "WrathCrafter")
@Extension
public class ExampleLoopedPlugin extends TaskPlugin {

    public String status;
    public String locationInfo = "";
    public long startTime;
    public boolean isPaused;
    public boolean equipmentSetupComplete = false;
    public boolean bankingComplete = false;
    public boolean needsPOHRestore = false;
    public int colossalPouchQuantity = 0;

    public int totalXpGained = 0;
    public int totalWrathRunesCrafted = 0;
    private int previousWrathRuneCount = 0;
    private int startingRcXp = -1;
    
    public int totalWrathRunesInBank = 0;
    public long totalWrathRunesGpValue = 0;

    public Task[] tasks;
    public Task currentTask;

    @Inject
    private ExampleLoopedConfig config;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private ExampleLoopedOverlay overlay;

    @Override
    public void startUp() {
        startTime = System.currentTimeMillis();
        startingRcXp = Skills.getExperience(Skill.RUNECRAFT);
        overlayManager.add(overlay);

        tasks = new Task[]{
                new EquipmentSetup(this),
                new Banking(this, config),
                new POHRestore(this),
                new TravelToAltar(this),
                new Crafting(this),
                new ReturnToBank(this)
        };
    }

    @Override
    public void shutDown() {
        overlayManager.remove(overlay);
    }

    @Override
    public Task[] getTasks() {
        if (isPaused) {
            status = "Paused...";
            currentTask = null;
            return new Task[0];
        }

        IPlayer local = Players.getLocal();
        if (local == null) {
            currentTask = null;
            return new Task[0];
        }

        for (Task task : tasks) {
            if (task.validate()) {
                currentTask = task;
                break;
            }
        }

        return tasks;
    }

    @Subscribe
    private void onConfigChanged(ConfigChanged e) {
        if (!e.getGroup().equals(ExampleLoopedConfig.GROUP)) {
            return;
        }

        if (e.getKey().equals("pause")) {
            isPaused = !isPaused;
        }
    }

    @Subscribe
    private void onStatChanged(StatChanged e) {
        if (e.getSkill() != Skill.RUNECRAFT) {
            return;
        }

        if (startingRcXp == -1) {
            startingRcXp = Skills.getExperience(Skill.RUNECRAFT);
        }

        int currentXp = Skills.getExperience(Skill.RUNECRAFT);
        totalXpGained = currentXp - startingRcXp;
        log.info("Runecrafting XP updated (Total gained: " + totalXpGained + ")");
    }

    @Subscribe
    private void onItemContainerChanged(ItemContainerChanged e) {
        int currentWrathCount = Inventory.getCount(true, Constants.WRATH_RUNE);
        
        if (currentWrathCount > previousWrathRuneCount) {
            int runesCrafted = currentWrathCount - previousWrathRuneCount;
            totalWrathRunesCrafted += runesCrafted;
            log.info("Wrath runes crafted: " + runesCrafted + " (Total: " + totalWrathRunesCrafted + ")");
        }
        
        previousWrathRuneCount = currentWrathCount;
    }

    @Provides
    public ExampleLoopedConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(ExampleLoopedConfig.class);
    }
}
