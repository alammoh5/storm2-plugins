package net.storm.plugins.examples.autoshopbuyer;

import net.storm.api.plugins.SoxExclude;
import net.storm.api.plugins.config.Config;
import net.storm.api.plugins.config.ConfigGroup;
import net.storm.api.plugins.config.ConfigItem;

@ConfigGroup(AutoShopBuyerConfig.GROUP)
@SoxExclude
public interface AutoShopBuyerConfig extends Config {
    String GROUP = "auto-shop-buyer-plugin";

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
            description = "Name of the item to buy"
    )
    default String itemName() {
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

