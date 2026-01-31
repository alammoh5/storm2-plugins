package net.storm.plugins.examples.gildedaltar;

import net.storm.api.plugins.SoxExclude;
import net.storm.api.plugins.config.Config;
import net.storm.api.plugins.config.ConfigGroup;
import net.storm.api.plugins.config.ConfigItem;

@ConfigGroup(GildedAltarConfig.GROUP)
@SoxExclude
public interface GildedAltarConfig extends Config {
    String GROUP = "gilded-altar-plugin";

    @ConfigItem(
        keyName = "pause",
        name = "Pause",
        description = "Pause the plugin"
    )
    default boolean pause() {
        return false;
    }
    @ConfigItem(
        keyName = "friendsPOHName",
        name = "Friend's POH Name",
        description = "Name of friend to use their POH. Leave blank to use your own POH."
    )
    default String friendsPOHName() {
        return "";
    }

    @ConfigItem(
        keyName = "use1TickOfferBones",
        name = "Use 1 Tick Offer Bones",
        description = "Use 1 tick method for offering bones at gilded altar"
    )
    default boolean use1TickOfferBones() {
        return false;
    }
}

