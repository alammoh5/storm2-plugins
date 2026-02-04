package net.storm.plugins.examples.chompykiller;

import com.google.inject.Inject;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.api.events.StatChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.ui.overlay.OverlayManager;
import net.storm.api.events.ConfigChanged;
import net.storm.api.plugins.PluginDescriptor;
import net.storm.api.plugins.LoopedPlugin;
import net.storm.api.plugins.config.ConfigManager;
import org.pf4j.Extension;

@Slf4j
@PluginDescriptor(name = "Auto Chompy Killer")
@Extension
public class AutoChompyKillerPlugin extends LoopedPlugin {

    public String status = "Initializing...";
    public long startTime;
    public boolean isPaused;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private AutoChompyKillerOverlay overlay;

    @Override
    public void startUp() {
    }

    @Override
    public void shutDown() {
    }

    @Override
    public int loop() {
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

