package net.storm.plugins.examples.autoitemuser;

import net.storm.api.plugins.SoxExclude;
import net.storm.api.plugins.config.Config;
import net.storm.api.plugins.config.ConfigGroup;
import net.storm.api.plugins.config.ConfigItem;

@ConfigGroup(AutoItemUserConfig.GROUP)
@SoxExclude
public interface AutoItemUserConfig extends Config {
    String GROUP = "auto-item-user-plugin";

    @ConfigItem(
            keyName = "npcName",
            name = "NPC Name",
            description = "Name of the NPC to buy from"
    )
    default String npcName() {
        return "";
    }

    @ConfigItem(
            keyName = "itemName",
            name = "Item Name",
            description = "Item 1"
    )
    default String itemName() {
        return "";
    }
    @ConfigItem(
            keyName = "itemName2",
            name = "Item Name 2",
            description = "Item 2"
    )
    default String itemName2() {
        return "";
    }

    @ConfigItem(
            keyName = "pause",
            name = "Pause",
            description = "Pause the plugin"
    )
    default boolean pause() {
        return false;
    }
}

