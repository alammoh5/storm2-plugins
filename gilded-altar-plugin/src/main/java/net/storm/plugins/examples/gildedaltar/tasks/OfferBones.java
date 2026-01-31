package net.storm.plugins.examples.gildedaltar.tasks;

import net.storm.api.plugins.Task;
import net.storm.plugins.examples.gildedaltar.GildedAltarConfig;
import net.storm.plugins.examples.gildedaltar.GildedAltarPlugin;

import net.storm.sdk.items.Inventory;
import net.storm.sdk.entities.TileObjects;
import net.storm.plugins.examples.gildedaltar.misc.Constants;
import net.storm.api.domain.tiles.ITileObject;

import java.util.Random;


public class OfferBones implements Task {

    private final GildedAltarPlugin plugin;
    private final GildedAltarConfig config;
    private final Random random = new Random();

    public OfferBones(GildedAltarPlugin plugin, GildedAltarConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    @Override
    public boolean validate() {
        plugin.status = "Checking for bones and gilded altar...";
        ITileObject gildedAltar = TileObjects.getNearest(Constants.GILDED_ALTAR);
        return Inventory.contains(Constants.DRAGON_BONES) && gildedAltar != null && gildedAltar.isInteractable();
    }

    @Override
    public int execute() {
        ITileObject gildedAltar = TileObjects.getNearest(Constants.GILDED_ALTAR);
        if (gildedAltar != null && gildedAltar.isInteractable()) {
            plugin.status = "Offering Bones...";
            Inventory.getFirst(Constants.DRAGON_BONES).useOn(gildedAltar);
            return config.use1TickOfferBones() ? -1 : random.nextBoolean() ? -1 : random.nextInt(401) + 700;
        }
        return 2000;
    }
}

