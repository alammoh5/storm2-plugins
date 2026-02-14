package net.storm.plugins.dagannothkings;

import net.storm.api.plugins.SoxExclude;
import net.storm.api.plugins.config.Config;
import net.storm.api.plugins.config.ConfigGroup;
import net.storm.api.plugins.config.ConfigItem;
import net.storm.api.plugins.config.ConfigSection;

@ConfigGroup(DagannothKingsConfig.GROUP)
@SoxExclude
public interface DagannothKingsConfig extends Config {
    String GROUP = "dagannoth-kings-plugin";

    @ConfigSection(
            keyName = "settings",
            position = 0,
            name = "Settings",
            description = ""
    )
    String settings = "Settings";

    @ConfigItem(
            position = 0,
            keyName = "showPrayerWidgetOverlay",
            name = "Prayer widget overlay",
            description = "Overlay prayer widget with tick timer.",
            section = settings
    )
    default boolean showPrayerWidgetOverlay() {
        return true;
    }

    @ConfigItem(
            position = 1,
            keyName = "showGuitarHeroOverlay",
            name = "Guitar hero overlay",
            description = "Render \"Guitar Hero\" style prayer overlay.",
            section = settings
    )
    default boolean showGuitarHeroOverlay() {
        return false;
    }

    @ConfigItem(
            position = 2,
            keyName = "ignoringNonAttacking",
            name = "Ignore non-attacking NPCs",
            description = "Ignore NPCs that are not attacking you.",
            section = settings
    )
    default boolean ignoringNonAttacking() {
        return false;
    }

    @ConfigItem(
            position = 3,
            keyName = "autoTogglePrayer",
            name = "Auto toggle prayer",
            description = "Automatically toggles the recommended protection prayer.",
            section = settings
    )
    default boolean autoTogglePrayer() {
        return false;
    }
}

