package net.storm.plugins.examples.autoconstruction;

import net.storm.api.plugins.SoxExclude;
import net.storm.api.plugins.config.Config;
import net.storm.api.plugins.config.ConfigGroup;
import net.storm.api.plugins.config.ConfigItem;

@ConfigGroup(AutoConstructionConfig.GROUP)
@SoxExclude
public interface AutoConstructionConfig extends Config {
    String GROUP = "auto-construction-plugin";

    @ConfigItem(
            keyName = "pause",
            name = "Pause",
            description = "Pause the plugin"
    )
    default boolean pause() {
        return false;
    }
}
