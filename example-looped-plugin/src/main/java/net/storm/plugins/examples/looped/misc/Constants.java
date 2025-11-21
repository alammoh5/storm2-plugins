package net.storm.plugins.examples.looped.misc;

import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;

public final class Constants {

    public static final int[] HAT_OF_THE_EYE = {
            ItemID.HAT_OF_THE_EYE_RED,
            ItemID.HAT_OF_THE_EYE_GREEN,
            ItemID.HAT_OF_THE_EYE_BLUE
    };

    public static final int[] ROBE_TOP_OF_THE_EYE = {
            ItemID.ROBE_TOP_OF_THE_EYE_RED,
            ItemID.ROBE_TOP_OF_THE_EYE_GREEN,
            ItemID.ROBE_TOP_OF_THE_EYE_BLUE
    };

    public static final int[] ROBE_BOTTOM_OF_THE_EYE = {
            ItemID.ROBE_BOTTOM_OF_THE_EYE_RED,
            ItemID.ROBE_BOTTOM_OF_THE_EYE_GREEN,
            ItemID.ROBE_BOTTOM_OF_THE_EYE_BLUE
    };

    public static final int BOOTS_OF_THE_EYE = ItemID.BOOTS_OF_THE_EYE;
    public static final int GRACEFUL_GLOVES = ItemID.GRACEFUL_GLOVES;
    public static final int ANTI_DRAGON_SHIELD = ItemID.ANTIDRAGONBREATHSHIELD;
    public static final int COLOSSAL_POUCH = ItemID.RCU_POUCH_COLOSSAL;
    public static final int MYTHICAL_CAPE = ItemID.MYTHICAL_CAPE;
    public static final int ETERNAL_GLORY = ItemID.AMULET_OF_GLORY_INF;
    public static final int RUNECRAFT_CAPE = ItemID.SKILLCAPE_RUNECRAFTING;
    public static final int RUNECRAFT_CAPE_T = ItemID.SKILLCAPE_RUNECRAFTING_TRIMMED;
    
    public static final int PURE_ESSENCE = ItemID.BLANKRUNE_HIGH;
    public static final int WRATH_RUNE = ItemID.WRATHRUNE;
    public static final int LAW_RUNE = ItemID.LAWRUNE;
    public static final int DUST_RUNE = ItemID.DUSTRUNE;

    public static final String ORNATE_POOL = "Ornate pool of Rejuvenation";
    public static final String MYSTERIOUS_RUINS = "Mysterious ruins";
    public static final String ALTAR = "Altar";
    public static final String BARRIER = "Barrier";
    public static final String CAVE_FOUNTAIN = "Fountain of Uhld";
    public static final String CAVE_EXIT = "Cave";
    public static final String STATUE = "Mythic Statue";
    public static final int STATUE_ID = 31626;

    public static final WorldPoint CAVE_WALK_POINT = new WorldPoint(1933, 8982, 1);


    private Constants() {
    }
}
