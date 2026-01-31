package net.storm.plugins.examples.gildedaltar;

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
import net.storm.plugins.examples.gildedaltar.misc.Constants;
import net.storm.sdk.items.Inventory;
import net.storm.api.plugins.PluginDescriptor;
import net.storm.api.plugins.Task;
import net.storm.api.plugins.TaskPlugin;
import net.storm.api.plugins.config.ConfigManager;
import net.storm.plugins.examples.gildedaltar.tasks.Banking;
import net.storm.plugins.examples.gildedaltar.tasks.OfferBones;
import net.storm.plugins.examples.gildedaltar.tasks.TravelToPOH;
import net.storm.plugins.examples.gildedaltar.tasks.ReturnToBank;
import net.storm.sdk.entities.Players;
import net.storm.sdk.game.Skills;
import org.pf4j.Extension;

@Slf4j
@PluginDescriptor(name = "GildedAltar")
@Extension
public class GildedAltarPlugin extends TaskPlugin {

    public boolean bankingComplete = false;
    public boolean travelToPOHComplete = false;
    public String status = "";
    public long startTime;
    public int totalBonesOffered = 0;
    public int totalXpGained = 0;
    private int startingPrayerXp = -1;
    private int previousBoneCount = 0;

    private Task[] tasks = new Task[0];
    private Task currentTask;
    private boolean isPaused;

    @Inject
    private GildedAltarConfig config;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private GildedAltarOverlay overlay;

    @Override
    public void startUp() {
        startTime = System.currentTimeMillis();
        startingPrayerXp = Skills.getExperience(Skill.PRAYER);
        overlayManager.add(overlay);

        tasks = new Task[]{
            new Banking(this, config),
            new TravelToPOH(this, config),
            new OfferBones(this, config),
            new ReturnToBank(this)
        };
    }

    @Override
    public void shutDown() {
        overlayManager.remove(overlay);
        this.bankingComplete = false;
        this.totalBonesOffered = 0;
        this.totalXpGained = 0;
        this.startingPrayerXp = -1;
        this.previousBoneCount = 0;
        this.status = "";
        this.startTime = 0;
        this.isPaused = false;
    }

    @Override
    public Task[] getTasks() {
        if (isPaused) {
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
    private void onConfigChanged(ConfigChanged event) {
        if (!event.getGroup().equals(GildedAltarConfig.GROUP)) {
            return;
        }

        if (event.getKey().equals("pause")) {
            isPaused = !isPaused;
        }
    }

    @Subscribe
    private void onStatChanged(StatChanged e) {
        if (e.getSkill() != Skill.PRAYER) {
            return;
        }

        if (startingPrayerXp == -1) {
            startingPrayerXp = Skills.getExperience(Skill.PRAYER);
        }

        int currentXp = Skills.getExperience(Skill.PRAYER);
        totalXpGained = currentXp - startingPrayerXp;
        log.info("Prayer XP updated (Total gained: " + totalXpGained + ")");
    }

    @Subscribe
    private void onItemContainerChanged(ItemContainerChanged e) {
        int currentBoneCount = Inventory.getCount(true, Constants.DRAGON_BONES);

        if (currentBoneCount < previousBoneCount) {
            int bonesUsed = previousBoneCount - currentBoneCount;
            totalBonesOffered += bonesUsed;
            log.info("Bones offered: " + bonesUsed + " (Total: " + totalBonesOffered + ")");
        }

        previousBoneCount = currentBoneCount;
    }

    @Provides
    public GildedAltarConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(GildedAltarConfig.class);
    }
}

