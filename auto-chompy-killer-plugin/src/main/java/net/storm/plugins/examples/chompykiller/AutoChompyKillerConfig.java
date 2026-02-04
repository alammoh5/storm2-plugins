package net.storm.plugins.examples.chompykiller;

import net.storm.api.plugins.SoxExclude;
import net.storm.api.plugins.config.Config;
import net.storm.api.plugins.config.ConfigGroup;
import net.storm.api.plugins.config.ConfigItem;

@ConfigGroup(AutoChompyKillerConfig.GROUP)
@SoxExclude
public interface AutoChompyKillerConfig extends Config {
    String GROUP = "auto-chompy-killer-plugin";

    @ConfigItem(
            keyName = "pause",
            name = "Pause",
            description = "Pause the plugin"
    )
    default boolean pause() {
        return false;
    }
}


