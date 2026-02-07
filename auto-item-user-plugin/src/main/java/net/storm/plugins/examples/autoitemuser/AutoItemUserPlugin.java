package net.storm.plugins.examples.autoitemuser;

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

import java.security.SecureRandom;

import org.pf4j.Extension;
import net.storm.sdk.items.Inventory;
import net.storm.api.domain.items.IInventoryItem;

@Slf4j
@PluginDescriptor(name = "Auto Item User")
@Extension
public class AutoItemUserPlugin extends LoopedPlugin {

    private static final SecureRandom RNG = new SecureRandom();

    public String status = "Initializing...";
    public long startTime;
    public boolean isPaused;

    public AutoItemUserPlugin(AutoItemUserConfig config) {
        this.config = config;
    }

    private final AutoItemUserConfig config;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private AutoItemUserOverlay overlay;

    @Override
    public void startUp() {
        
    }

    @Override
    public void shutDown() {
        
    }

    @Override
    public int loop() {
        IInventoryItem item1 = Inventory.getFirst(config.itemName());
        IInventoryItem item2 = Inventory.getFirst(config.itemName2());

        if(item1 != null && item2 != null){
            item1.useOn(item2);
            return 50 + RNG.nextInt(151);
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
    public AutoItemUserConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(AutoItemUserConfig.class);
    }
}

