package net.storm.plugins.examples.looped;


import net.storm.api.plugins.SoxExclude;
import net.storm.api.plugins.config.Config;
import net.storm.api.plugins.config.ConfigGroup;
import net.storm.api.plugins.config.ConfigItem;

@ConfigGroup(ExampleLoopedConfig.GROUP)
@SoxExclude
public interface ExampleLoopedConfig extends Config {
    String GROUP = "example-looped-plugin";

    @ConfigItem(
            keyName = "minRunEnergy",
            name = "Min Run Energy",
            description = "Minimum run energy before restoring at POH"
    )
    default int minRunEnergy() {
        return 30;
    }

    @ConfigItem(
            keyName = "useColossalPouch",
            name = "Use Colossal Pouch",
            description = "Use colossal pouch for extra essence"
    )
    default boolean useColossalPouch() {
        return true;
    }

    @ConfigItem(
            keyName = "pause",
            name = "Pause",
            description = "Pause the plugin"
    )
    default boolean pause() {
        return false;
    }

    @ConfigItem(
            keyName = "wrathRunePrice",
            name = "Wrath Rune Price",
            description = "Price per wrath rune for GP calculations"
    )
    default int wrathRunePrice() {
        return 240;
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
            keyName = "use1TickBank",
            name = "Use 1 Tick Bank",
            description = "Use 1 tick bank method for filling pouch"
    )
    default boolean use1TickBank() {
        return false;
    }

    @ConfigItem(
        keyName = "use1TickCraft",
        name = "Use 1 Tick Craft",
        description = "Use 1 tick craft method for crafting runes"
    )
    default boolean use1TickCraft() {
        return false;
    }
}
