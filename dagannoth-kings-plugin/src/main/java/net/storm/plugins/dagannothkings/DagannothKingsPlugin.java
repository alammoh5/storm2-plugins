package net.storm.plugins.dagannothkings;

import com.google.inject.Provides;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.Prayer;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.ui.overlay.OverlayManager;
import net.storm.api.domain.actors.IPlayer;
import net.storm.api.domain.actors.INPC;
import net.storm.api.events.NpcDespawned;
import net.storm.api.events.NpcSpawned;
import net.storm.api.plugins.Plugin;
import net.storm.api.plugins.PluginDescriptor;
import net.storm.api.plugins.config.ConfigManager;
import net.storm.plugins.dagannothkings.entity.DagannothKing;
import net.storm.plugins.dagannothkings.overlay.PrayerOverlay;
import net.storm.sdk.widgets.Prayers;
import net.storm.sdk.entities.Players;
import org.pf4j.Extension;

@Singleton
@Extension
@PluginDescriptor(
        name = "Dagannoth Kings",
        enabledByDefault = false,
        description = "Prayer widget overlay assistant for Dagannoth Kings.",
        tags = {"dagannoth", "kings", "dks", "daggs"}
)
public class DagannothKingsPlugin extends Plugin {
    public static final int ANIMATION_ID_DAG_REX = 2853;
    public static final int ANIMATION_ID_DAG_PRIME = 2854;
    public static final int ANIMATION_ID_DAG_SUPREME = 2855;

    private static final int WATERBIRTH_REGION = 11589;

    @Inject
    private Client client;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private PrayerOverlay prayerOverlay;

    @Inject
    private DagannothKingsConfig config;

    @Getter
    private final Set<DagannothKing> dagannothKings = new HashSet<>();

    private boolean atDks;

    @Getter
    private long lastTickTime;

    private Prayer lastRecommendedPrayer;

    @Provides
    DagannothKingsConfig getConfig(final ConfigManager configManager) {
        return configManager.getConfig(DagannothKingsConfig.class);
    }

    @Override
    public void startUp() {
        if (client.getGameState() != GameState.LOGGED_IN || !atDks()) {
            return;
        }

        init();
    }

    private void init() {
        atDks = true;
        addOverlays();

        for (final var npc : client.getTopLevelWorldView().npcs()) {
            if (npc instanceof INPC) {
                addNpc((INPC) npc);
            }
        }
    }

    @Override
    public void shutDown() {
        atDks = false;
        lastRecommendedPrayer = null;
        removeOverlays();
        dagannothKings.clear();
    }

    @Subscribe
    private void onGameStateChanged(final GameStateChanged event) {
        final GameState gameState = event.getGameState();
        switch (gameState) {
            case LOGGED_IN:
                if (atDks()) {
                    if (!atDks) {
                        init();
                    }
                } else if (atDks) {
                    shutDown();
                }
                break;
            case LOGIN_SCREEN:
            case HOPPING:
                shutDown();
                break;
            default:
                break;
        }
    }

    @Subscribe
    private void onGameTick(final GameTick event) {
        lastTickTime = System.currentTimeMillis();
        if (dagannothKings.isEmpty()) {
            return;
        }

        for (final DagannothKing dagannothKing : dagannothKings) {
            dagannothKing.updateTicksUntilNextAnimation();
        }

        if (config.autoTogglePrayer()) {
            maybeToggleRecommendedPrayer();
        }
    }

    @Subscribe
    private void onNpcSpawned(final NpcSpawned event) {
        addNpc(event.getNpc());
    }

    @Subscribe
    private void onNpcDespawned(final NpcDespawned event) {
        removeNpc(event.getNpc());
    }

    private void addNpc(final INPC npc) {
        switch (npc.getId()) {
            case NpcID.DAGCAVE_MELEE_BOSS:
            case NpcID.DAGCAVE_RANGED_BOSS:
            case NpcID.DAGCAVE_MAGIC_BOSS:
                dagannothKings.add(new DagannothKing(npc));
                break;
            default:
                break;
        }
    }

    private void removeNpc(final INPC npc) {
        switch (npc.getId()) {
            case NpcID.DAGCAVE_MELEE_BOSS:
            case NpcID.DAGCAVE_RANGED_BOSS:
            case NpcID.DAGCAVE_MAGIC_BOSS:
                dagannothKings.removeIf(dk -> dk.getNpc() == npc);
                break;
            default:
                break;
        }
    }

    private void addOverlays() {
        overlayManager.add(prayerOverlay);
    }

    private void removeOverlays() {
        overlayManager.remove(prayerOverlay);
    }

    private boolean atDks() {
        return Arrays.stream(client.getTopLevelWorldView().getMapRegions()).anyMatch(x -> x == WATERBIRTH_REGION);
    }

    private void maybeToggleRecommendedPrayer() {
        final Optional<DagannothKing> priorityKing = getPriorityKingForPrayerWidget();
        if (priorityKing.isEmpty()) {
            return;
        }

        final Prayer recommendedPrayer = priorityKing.get().getAttackStyle().getPrayer();
        if (recommendedPrayer == lastRecommendedPrayer || Prayers.isEnabled(recommendedPrayer) || !Prayers.canUse(recommendedPrayer)) {
            lastRecommendedPrayer = recommendedPrayer;
            return;
        }

        Prayers.toggle(recommendedPrayer);
        lastRecommendedPrayer = recommendedPrayer;
    }

    private Optional<DagannothKing> getPriorityKingForPrayerWidget() {
        final IPlayer localPlayer = Players.getLocal();
        if (localPlayer == null) {
            return Optional.empty();
        }

        // Primary: existing behavior (countdown-driven, optionally only attackers)
        Optional<DagannothKing> primary = dagannothKings.stream()
                .filter(dk -> dk.getNpc() != null)
                .filter(dk -> !dk.getNpc().isDead())
                .filter(dk -> dk.getTicksUntilNextAnimation() > 0)
                .filter(dk -> !config.ignoringNonAttacking() || dk.getInteractingActor() == localPlayer)
                .min(Comparator.comparingInt(DagannothKing::getTicksUntilNextAnimation));

        if (primary.isPresent()) {
            return primary;
        }

        // Fallback: if nobody qualifies above, pray for nearest DK in range (using each boss's attackRange)
        final var playerLoc = localPlayer.getWorldLocation();
        if (playerLoc == null) {
            return Optional.empty();
        }

        return dagannothKings.stream()
                .filter(dk -> dk.getNpc() != null)
                .filter(dk -> !dk.getNpc().isDead())
                .filter(dk -> dk.getNpc().getWorldLocation() != null)
                .filter(dk -> dk.getNpc().getWorldLocation().distanceTo(playerLoc) <= dk.getAttackRange())
                .min(Comparator.comparingInt(dk ->
                        dk.getNpc().getWorldLocation().distanceTo(playerLoc)));
    }
}

