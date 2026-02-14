package net.storm.plugins.examples.autoitemuser;

import com.google.inject.Inject;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.storm.api.domain.actors.IPlayer;
import net.storm.sdk.entities.Players;
import net.storm.api.domain.items.IInventoryItem;
import net.storm.api.plugins.LoopedPlugin;
import net.storm.api.plugins.PluginDescriptor;
import net.storm.api.plugins.config.ConfigManager;
import net.storm.sdk.items.Inventory;
import net.storm.sdk.items.Bank;
import net.storm.sdk.widgets.Dialog;
import org.pf4j.Extension;

import java.security.SecureRandom;

@Slf4j
@PluginDescriptor(name = "Auto Item User")
@Extension
public class AutoItemUserPlugin extends LoopedPlugin {

    private static final SecureRandom RNG = new SecureRandom();

    @Inject
    private AutoItemUserConfig config;

    public String status = "Initializing...";
    public long startTime;
    public boolean isPaused;

    @Override
    public void startUp() {
        
    }

    @Override
    public void shutDown() {
        
    }

    @Override
    public int loop() {

        if(config.pause() || Bank.isOpen() || Dialog.isOpen()){
            return -1;
        }

        IInventoryItem item1 = Inventory.getFirst(config.itemName());
        IInventoryItem item2 = Inventory.getFirst(config.itemName2());

        if(item1 != null && item2 != null){
            item1.useOn(item2);
            int waitTime = config.minDelay() + RNG.nextInt(config.maxDelay());
            log.info("Waiting for " + waitTime + "ms");
            return waitTime;
        }
        return -1;
    }

    @Provides
    AutoItemUserConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(AutoItemUserConfig.class);
    }

}

