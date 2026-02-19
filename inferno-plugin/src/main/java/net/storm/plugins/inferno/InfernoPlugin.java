package net.storm.plugins.inferno;

import com.google.inject.Provides;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.InventoryID;
import net.runelite.api.MenuAction;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.NpcID;
import net.runelite.api.VarPlayer;
import net.runelite.api.Prayer;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import net.runelite.client.ui.overlay.OverlayManager;
import net.storm.api.Static;
import net.storm.api.events.ConfigChanged;
import net.storm.api.plugins.Plugin;
import net.storm.api.plugins.PluginDescriptor;
import net.storm.api.plugins.config.ConfigManager;
import net.storm.plugins.inferno.displaymodes.InfernoPrayerDisplayMode;
import net.storm.plugins.inferno.displaymodes.InfernoSafespotDisplayMode;
import net.storm.plugins.inferno.displaymodes.InfernoWaveDisplayMode;
import net.storm.plugins.inferno.displaymodes.InfernoZukShieldDisplayMode;
import net.storm.api.magic.SpellBook;
import net.storm.sdk.entities.NPCs;
import net.storm.sdk.entities.Players;
import net.storm.sdk.items.Equipment;
import net.storm.sdk.items.Inventory;
import net.storm.sdk.game.Combat;
import net.storm.api.movement.pathfinder.LocalCollisionMap;
import net.storm.sdk.movement.Movement;
import net.storm.sdk.widgets.Prayers;
import net.storm.api.util.WeaponStyle;
import org.pf4j.Extension;

import javax.inject.Singleton;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

@Singleton
@Getter(AccessLevel.PACKAGE)
@Extension
@PluginDescriptor(
        name = "Inferno",
        enabledByDefault = false,
        description = "Inferno wave tracking, prayer recommendations, safespots, and Zuk timer assistance.",
        tags = {"inferno", "zuk", "jad", "tzhaar"}
)
@Slf4j
public class InfernoPlugin extends Plugin {

    private static final int INFERNO_REGION = 9043;
    private static final int ZUK_MAX_HP = 1400;

    private static final Set<Integer> NON_ATTACK_ANIMATIONS = Set.of(
        397, 378, 388, 383, 403, 410, 415, 420, 424, 1666, 435, 1156, 1709, 2063, 1659,
        829, 712, 713, 3170, 3872, 1816, 6293, 6294, 7118, 7672, 4411, 4413, 8316, 7198, 8975, 8979, 8970, 881, 8973
    );

    @Inject
    private Client client;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private InfernoOverlay infernoOverlay;

    @Inject
    private InfernoWaveOverlay waveOverlay;

    @Inject
    private InfernoInfoBoxOverlay jadOverlay;

    @Inject
    private InfernoDebugOverlay debugOverlay;

    @Inject
    private InfernoConfig config;

    @Inject
    private ConfigManager configManager;

    @Inject
    private ItemManager itemManager;

    private static final String MENU_MARK_MAGE = "Inferno: Mark as Mage gear";
    private static final String MENU_MARK_RANGE = "Inferno: Mark as Range gear";

    private int[] lastEquipmentItemIds = new int[0];
    private final Set<Integer> pendingGearEquipIds = new LinkedHashSet<>();

    @Getter(AccessLevel.PACKAGE)
    private InfernoConfig.FontStyle fontStyle = InfernoConfig.FontStyle.BOLD;
    @Getter(AccessLevel.PACKAGE)
    private int textSize = 32;

    private WorldPoint lastLocation = new WorldPoint(0, 0, 0);

    private boolean freezingNibblers = false;

    @Getter(AccessLevel.PACKAGE)
    private int currentWaveNumber = -1;

    @Getter(AccessLevel.PACKAGE)
    private final List<InfernoNPC> infernoNpcs = new ArrayList<>();

    @Getter(AccessLevel.PACKAGE)
    private final Map<Integer, Map<InfernoNPC.Attack, Integer>> upcomingAttacks = new HashMap<>();
    @Getter(AccessLevel.PACKAGE)
    private InfernoNPC.Attack closestAttack = null;
    @Getter(AccessLevel.PACKAGE)
    private int closestAttackTick = 999;

    private Prayer armedProtectionPrayer = null;
    private boolean disablePrayerNextTick = false;

    // #region agent log
    private static final int HITSPLAT_DAMAGE_ME_CYAN = 18;
    private static final int HITSPLAT_DAMAGE_ME_ORANGE = 20;
    private static final int HITSPLAT_DAMAGE_ME_YELLOW = 22;
    private static final Path WAVE_LOG_PATH = Paths.get("c:/Users/Alam/Documents/GitHub/storm2-plugins/.cursor/debug.log");

    private static void waveLog(String event, String dataJson) {
        try {
            String json = "{\"event\":\"" + event + "\",\"data\":" + (dataJson != null ? dataJson : "{}") + ",\"timestamp\":" + System.currentTimeMillis() + "}";
            Files.write(WAVE_LOG_PATH, (json + "\n").getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {}
    }

    private static InfernoNPC.Attack hitsplatTypeToAttack(int hitsplatType) {
        if (hitsplatType == HITSPLAT_DAMAGE_ME_ORANGE) return InfernoNPC.Attack.MELEE;
        if (hitsplatType == HITSPLAT_DAMAGE_ME_YELLOW) return InfernoNPC.Attack.RANGED;
        if (hitsplatType == HITSPLAT_DAMAGE_ME_CYAN) return InfernoNPC.Attack.MAGIC;
        return null;
    }

    private Map<Integer, Map<InfernoNPC.Attack, Integer>> lastUpcomingAttacks = new HashMap<>();
    private InfernoNPC.Attack lastClosestAttack = null;
    private int lastClosestAttackTick = 999;
    // #endregion
    private Prayer armedOffensivePrayer = null;
    private int offensiveTicksSinceLastAttack = -1;
    private boolean offensiveFirstAttackSeen = false;
    private int lastOffensiveAnim = -1;

    @Getter(AccessLevel.PACKAGE)
    private final List<WorldPoint> obstacles = new ArrayList<>();

    @Getter(AccessLevel.PACKAGE)
    private boolean finalPhase = false;
    private boolean finalPhaseTick = false;
    private int ticksSinceFinalPhase = 0;
    @Getter(AccessLevel.PACKAGE)
    private NPC zukShield = null;
    private NPC zuk = null;
    private WorldPoint zukShieldLastPosition = null;
    private WorldPoint zukShieldBase = null;
    private int zukShieldCornerTicks = -2;

    private int zukShieldNegativeXCoord = -1;
    private int zukShieldPositiveXCoord = -1;
    private int zukShieldLastNonZeroDelta = 0;
    private int zukShieldLastDelta = 0;
    private int zukShieldTicksLeftInCorner = -1;

    @Getter(AccessLevel.PACKAGE)
    private InfernoNPC centralNibbler = null;

    @Getter(AccessLevel.PACKAGE)
    private final Map<WorldPoint, Integer> safeSpotMap = new HashMap<>();
    @Getter(AccessLevel.PACKAGE)
    private final Map<Integer, List<WorldPoint>> safeSpotAreas = new HashMap<>();

    @Getter(AccessLevel.PACKAGE)
    private final List<InfernoBlobDeathSpot> blobDeathSpots = new ArrayList<>();

    @Getter(AccessLevel.PACKAGE)
    private long lastTick;

    @Getter(AccessLevel.PACKAGE)
    private InfernoSpawnTimerInfobox spawnTimerInfoBox;

    @Provides
    InfernoConfig getConfig(ConfigManager configManager) {
        return configManager.getConfig(InfernoConfig.class);
    }

    @Override
    public void startUp() {
        InfernoWaveDisplayMode waveDisplayMode = InfernoConfigParsers.waveDisplayMode(config);
        waveOverlay.setDisplayMode(waveDisplayMode);
        waveOverlay.setWaveHeaderColor(config.getWaveOverlayHeaderColor());
        waveOverlay.setWaveTextColor(config.getWaveTextColor());
        if (isInInferno()) {
            overlayManager.add(infernoOverlay);
            if (waveDisplayMode != InfernoWaveDisplayMode.NONE) {
                overlayManager.add(waveOverlay);
            }
            overlayManager.add(jadOverlay);
            if (config.debug()) overlayManager.add(debugOverlay);
        }
    }

    @Override
    public void shutDown() {
        overlayManager.remove(infernoOverlay);
        overlayManager.remove(waveOverlay);
        overlayManager.remove(jadOverlay);
        overlayManager.remove(debugOverlay);
        spawnTimerInfoBox = null;
        currentWaveNumber = -1;
        pendingGearEquipIds.clear();
        resetLazyFlickState();
    }

    @Subscribe
    private void onConfigChanged(ConfigChanged event) {
        if (!InfernoConfig.GROUP.equals(event.getGroup())) {
            return;
        }
        if (event.getKey() != null && event.getKey().endsWith("color")) {
            waveOverlay.setWaveHeaderColor(config.getWaveOverlayHeaderColor());
            waveOverlay.setWaveTextColor(config.getWaveTextColor());
        } else if ("waveDisplay".equals(event.getKey())) {
            InfernoWaveDisplayMode waveDisplayMode = InfernoConfigParsers.waveDisplayMode(config);
            overlayManager.remove(waveOverlay);
            waveOverlay.setDisplayMode(waveDisplayMode);
            if (isInInferno() && waveDisplayMode != InfernoWaveDisplayMode.NONE) {
                overlayManager.add(waveOverlay);
            }
        } else if ("debug".equals(event.getKey())) {
            if (config.debug() && isInInferno()) overlayManager.add(debugOverlay);
            else overlayManager.remove(debugOverlay);
        } else if ("lazyFlick".equals(event.getKey()) || "autoTogglePrayer".equals(event.getKey()) || "offensiveLazyFlick".equals(event.getKey())) {
            resetLazyFlickState();
        }
    }

    @Subscribe
    private void onGameTick(GameTick event) {
        if (!isInInferno()) return;
        lastTick = System.currentTimeMillis();
        if (freezingNibblers) {
            boolean nibblerFrozen = client.getLocalPlayer().getAnimation() == 10092;
            if (nibblerFrozen) {
                Prayers.toggle(getOffensivePrayerForCurrentStyle());
                freezingNibblers = false;
                // WorldPoint SAFE_SPOT_CORNER = WorldPoint.toLocalInstance(Static.getClient().getTopLevelWorldView(), new WorldPoint(2276, 5354, 0)).stream().findFirst().orElse(null);
                // Movement.walkTo(SAFE_SPOT_CORNER, new LocalCollisionMap(true));

                // WorldPoint START_TILE = WorldPoint.toLocalInstance(Static.getClient().getTopLevelWorldView(), new WorldPoint(2274, 5355, 0)).stream().findFirst().orElse(null);
                // Movement.walkTo(START_TILE, new LocalCollisionMap(true));
                // queueGearSet(parseGearIds(config.rangeGearIds()));
            }
        }
        infernoNpcs.removeIf(InfernoNPC::isDead);
        obstacles.clear();
        calculateObstacles();
        upcomingAttacks.clear();
        calculateUpcomingAttacks();
        closestAttack = null;
        closestAttackTick = 999;
        calculateClosestAttack();
        if (disablePrayerNextTick && armedProtectionPrayer != null) {
            Prayer needTick0 = getPrayerForTick(0);
            Prayer needThisTick = getPrayerForTick(1);
            Prayer needNextTick = getPrayerForTick(2);
            if (armedProtectionPrayer != needTick0 && armedProtectionPrayer != needThisTick && armedProtectionPrayer != needNextTick) {
                if (Prayers.isEnabled(armedProtectionPrayer)) Prayers.toggle(armedProtectionPrayer);
            }
            resetProtectionFlickState();
        }
        if (config.autoTogglePrayer()) {
            Prayer needed = closestAttack != null ? closestAttack.getPrayer() : null;
            if (config.lazyFlick()) {
                if ((closestAttackTick == 0 || closestAttackTick == 1) && needed != null) {
                    if (!Prayers.isEnabled(needed)) {
                        Prayers.toggle(needed);
                        armedProtectionPrayer = needed;
                        disablePrayerNextTick = true;
                    }
                } else {
                    disableProtectionPrayers();
                }
            } else {
                if (needed != null) {
                    toggleProtectionPrayer(needed);
                } else {
                    disableAllActivePrayers();
                }
            }
        }
        if (config.offensiveLazyFlick()) {
            processOffensiveLazyFlick();
        }
        safeSpotMap.clear();
        calculateSafespots();
        safeSpotAreas.clear();
        calculateSafespotAreas();
        centralNibbler = null;
        calculateCentralNibbler();
        calculateSpawnTimerInfobox();
        manageBlobDeathLocations();
        processPendingGearEquips();
        if (finalPhaseTick) {
            finalPhaseTick = false;
        } else if (finalPhase) {
            ticksSinceFinalPhase++;
        }
        lastUpcomingAttacks = new HashMap<>();
        for (Map.Entry<Integer, Map<InfernoNPC.Attack, Integer>> e : upcomingAttacks.entrySet()) {
            lastUpcomingAttacks.put(e.getKey(), new HashMap<>(e.getValue()));
        }
        lastClosestAttack = closestAttack;
        lastClosestAttackTick = closestAttackTick;
    }

    @Subscribe
    private void onHitsplatApplied(HitsplatApplied event) {
        if (!isInInferno()) return;
        if (event.getActor() != client.getLocalPlayer()) return;
        var hitsplat = event.getHitsplat();
        if (hitsplat.getAmount() <= 0) return;
        int type = hitsplat.getHitsplatType();
        InfernoNPC.Attack attackType = hitsplatTypeToAttack(type);
        Prayer activePrayer = null;
        if (Prayers.isEnabled(Prayer.PROTECT_FROM_MELEE)) activePrayer = Prayer.PROTECT_FROM_MELEE;
        else if (Prayers.isEnabled(Prayer.PROTECT_FROM_MISSILES)) activePrayer = Prayer.PROTECT_FROM_MISSILES;
        else if (Prayers.isEnabled(Prayer.PROTECT_FROM_MAGIC)) activePrayer = Prayer.PROTECT_FROM_MAGIC;
        Prayer correctPrayer = attackType != null ? attackType.getPrayer() : null;
        boolean wasCorrect = correctPrayer != null && correctPrayer == activePrayer;
        Set<InfernoNPC.Attack> attacksAtTick1 = lastUpcomingAttacks.containsKey(1)
            ? lastUpcomingAttacks.get(1).keySet() : Set.of();
        boolean possiblyUnavoidable = attacksAtTick1.size() > 1;
        waveLog("damage", "{\"wave\":" + currentWaveNumber + ",\"amount\":" + hitsplat.getAmount()
            + ",\"hitsplatType\":" + type + ",\"attackType\":\"" + (attackType != null ? attackType : "?") + "\""
            + ",\"activePrayer\":\"" + (activePrayer != null ? activePrayer : "none") + "\""
            + ",\"correctPrayer\":\"" + (correctPrayer != null ? correctPrayer : "?") + "\""
            + ",\"wasCorrect\":" + wasCorrect + ",\"possiblyUnavoidable\":" + possiblyUnavoidable
            + ",\"attacksAtTick1\":" + attacksAtTick1 + ",\"lastClosestAttack\":\"" + lastClosestAttack + "\"}");
    }

    @Subscribe
    private void onNpcSpawned(NpcSpawned event) {
        if (!isInInferno()) return;
        int npcId = event.getNpc().getId();
        if (npcId == NpcID.ANCESTRAL_GLYPH) {
            zukShield = event.getNpc();
            return;
        }
        InfernoNPC.Type type = InfernoNPC.Type.typeFromId(npcId);
        if (type == null) return;
        switch (type) {
            case NIBBLER:
                if (!freezingNibblers && SpellBook.Ancient.ICE_BARRAGE.canCast() && Players.getLocal() != null && !Players.getLocal().isInteracting()) {
                    var nibbler = NPCs.query().ids(net.runelite.api.NpcID.JALNIB).results().stream()
                        .filter(n -> n.getWorldLocation().distanceTo(event.getNpc().getWorldLocation()) == 0)
                        .findFirst().orElse(null);
                    if (nibbler != null && nibbler.isInteractable()) {
                        try {
                            Prayers.toggle(getOffensivePrayerForCurrentStyle());
                            SpellBook.Ancient.ICE_BARRAGE.castOn(nibbler);
                            freezingNibblers = true;
                        } catch (Throwable ignored) { }
                    }
                }
                break;
            case BLOB:
                infernoNpcs.add(new InfernoNPC(event.getNpc()));
                waveLog("npc_spawn", "{\"wave\":" + currentWaveNumber + ",\"type\":\"" + type + "\",\"attack\":\"" + (type.getDefaultAttack() != null ? type.getDefaultAttack() : "UNKNOWN") + "\"}");
                return;
            case MAGE:
                if (zuk != null && spawnTimerInfoBox != null) {
                    spawnTimerInfoBox.reset();
                    spawnTimerInfoBox.run();
                }
                break;
            case ZUK:
                finalPhase = false;
                zukShieldCornerTicks = -2;
                zukShieldLastPosition = null;
                zukShieldBase = null;
                if (config.spawnTimerInfobox()) {
                    zuk = event.getNpc();
                    spawnTimerInfoBox = new InfernoSpawnTimerInfobox();
                }
                break;
            case HEALER_ZUK:
                finalPhase = true;
                ticksSinceFinalPhase = 1;
                finalPhaseTick = true;
                for (InfernoNPC infernoNPC : infernoNpcs) {
                    if (infernoNPC.getType() == InfernoNPC.Type.ZUK) {
                        infernoNPC.setTicksTillNextAttack(-1);
                    }
                }
                break;
            default:
                break;
        }
        infernoNpcs.add(0, new InfernoNPC(event.getNpc()));
        waveLog("npc_spawn", "{\"wave\":" + currentWaveNumber + ",\"type\":\"" + type + "\",\"attack\":\"" + (type.getDefaultAttack() != null ? type.getDefaultAttack() : "UNKNOWN") + "\"}");
    }

    @Subscribe
    private void onNpcDespawned(NpcDespawned event) {
        if (!isInInferno()) return;
        int npcId = event.getNpc().getId();
        if (npcId == NpcID.ANCESTRAL_GLYPH) {
            zukShield = null;
            return;
        }
        if (npcId == NpcID.TZKALZUK) {
            zuk = null;
            spawnTimerInfoBox = null;
        }
        infernoNpcs.removeIf(infernoNPC -> infernoNPC.getNpc() == event.getNpc());
    }

    @Subscribe
    private void onInteractingChanged(InteractingChanged event) {
        if (!isInInferno() || !config.offensiveLazyFlick()) return;
        if (event.getSource() != client.getLocalPlayer()) return;
        if (event.getTarget() != null) return;
        disableArmedOffensivePrayer();
        resetOffensiveFlickState();
    }

    @Subscribe
    private void onAnimationChanged(AnimationChanged event) {
        if (!isInInferno()) return;
        if (event.getActor() instanceof NPC) {
            NPC npc = (NPC) event.getActor();
            if (contains(NPC_TYPE_NIBBLER_IDS, npc.getId()) && npc.getAnimation() == 7576) {
                infernoNpcs.removeIf(infernoNPC -> infernoNPC.getNpc() == npc);
            }
            if (config.indicateBlobDeathLocation() && InfernoNPC.Type.typeFromId(npc.getId()) == InfernoNPC.Type.BLOB
                && npc.getAnimation() == InfernoBlobDeathSpot.BLOB_DEATH_ANIMATION) {
                infernoNpcs.removeIf(infernoNPC -> infernoNPC.getNpc() == npc);
                blobDeathSpots.add(new InfernoBlobDeathSpot(npc.getLocalLocation()));
            }
        }
    }

    private static final int[] NPC_TYPE_NIBBLER_IDS = InfernoNPC.Type.NIBBLER.getNpcIds();

    private static boolean contains(int[] arr, int val) {
        for (int i : arr) if (i == val) return true;
        return false;
    }

    @Subscribe
    private void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() != GameState.LOGGED_IN) return;
        if (!isInInferno()) {
            lastEquipmentItemIds = new int[0];
            pendingGearEquipIds.clear();
            infernoNpcs.clear();
            currentWaveNumber = -1;
            overlayManager.remove(infernoOverlay);
            overlayManager.remove(waveOverlay);
            overlayManager.remove(jadOverlay);
            zukShield = null;
            zuk = null;
            spawnTimerInfoBox = null;
            resetLazyFlickState();
        } else if (currentWaveNumber == -1) {
            InfernoWaveDisplayMode waveDisplayMode = InfernoConfigParsers.waveDisplayMode(config);
            infernoNpcs.clear();
            currentWaveNumber = 1;
            overlayManager.add(infernoOverlay);
            overlayManager.add(jadOverlay);
            if (waveDisplayMode != InfernoWaveDisplayMode.NONE) {
                overlayManager.add(waveOverlay);
            }
            if (config.debug()) overlayManager.add(debugOverlay);
        }
    }

    @Subscribe
    private void onMenuEntryAdded(MenuEntryAdded event) {
        if (!isInInferno()) return;
        int itemId = event.getItemId();
        if (itemId <= 0) return;
        String option = event.getOption();
        if (!"Use".equals(option) && !"Wear".equals(option) && !"Wield".equals(option) && !"Drop".equals(option)) return;
        if (menuContainsGearMarkOptions(client.getMenuEntries())) return;
        int param0 = event.getActionParam0();
        int param1 = event.getActionParam1();
        client.createMenuEntry(-1)
            .setOption(MENU_MARK_MAGE)
            .setTarget("<col=ff9040>" + event.getTarget() + "</col>")
            .setType(MenuAction.RUNELITE)
            .setIdentifier(itemId)
            .setParam0(param0)
            .setParam1(param1);
        client.createMenuEntry(-1)
            .setOption(MENU_MARK_RANGE)
            .setTarget("<col=ff9040>" + event.getTarget() + "</col>")
            .setType(MenuAction.RUNELITE)
            .setIdentifier(itemId)
            .setParam0(param0)
            .setParam1(param1);
    }

    @Subscribe
    private void onMenuOptionClicked(MenuOptionClicked event) {
        if (MENU_MARK_MAGE.equals(event.getMenuOption())) {
            event.consume();
            int id = event.getMenuEntry() != null ? event.getMenuEntry().getIdentifier() : event.getItemId();
            markItemAsGear(id, true);
        } else if (MENU_MARK_RANGE.equals(event.getMenuOption())) {
            event.consume();
            int id = event.getMenuEntry() != null ? event.getMenuEntry().getIdentifier() : event.getItemId();
            markItemAsGear(id, false);
        }
    }

    private static boolean menuContainsGearMarkOptions(net.runelite.api.MenuEntry[] entries) {
        if (entries == null) return false;
        for (var e : entries) {
            if (e != null && (MENU_MARK_MAGE.equals(e.getOption()) || MENU_MARK_RANGE.equals(e.getOption())))
                return true;
        }
        return false;
    }

    private void markItemAsGear(int itemId, boolean asMage) {
        if (itemId <= 0) return;
        Set<Integer> mage = parseGearIds(config.mageGearIds());
        Set<Integer> range = parseGearIds(config.rangeGearIds());
        if (asMage) {
            range.remove(itemId);
            mage.add(itemId);
        } else {
            mage.remove(itemId);
            range.add(itemId);
        }
        configManager.setConfiguration(InfernoConfig.GROUP, "mageGearIds", serializeGearIds(mage));
        configManager.setConfiguration(InfernoConfig.GROUP, "rangeGearIds", serializeGearIds(range));
    }

    private static Set<Integer> parseGearIds(String csv) {
        if (csv == null || csv.isBlank()) return new HashSet<>();
        return Arrays.stream(csv.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(s -> {
                try { return Integer.parseInt(s); } catch (NumberFormatException e) { return -1; }
            })
            .filter(id -> id > 0)
            .collect(Collectors.toSet());
    }

    private static String serializeGearIds(Set<Integer> ids) {
        if (ids == null || ids.isEmpty()) return "";
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    @Subscribe
    private void onItemContainerChanged(ItemContainerChanged event) {
        if (!isInInferno()) return;
        if (event.getContainerId() != InventoryID.EQUIPMENT.getId()) return;
        if (event.getItemContainer() == null) return;
        int[] current = getEquipmentItemIds(event.getItemContainer().getItems());
        if (lastEquipmentItemIds.length == 0) {
            lastEquipmentItemIds = current;
            return;
        }
        int[] newlyEquipped = findNewlyEquipped(lastEquipmentItemIds, current);
        lastEquipmentItemIds = current;
        if (newlyEquipped.length == 0) return;
        Set<Integer> mage = parseGearIds(config.mageGearIds());
        Set<Integer> range = parseGearIds(config.rangeGearIds());
        for (int id : newlyEquipped) {
            if (mage.contains(id)) {
                queueGearSet(mage);
                return;
            }
            if (range.contains(id)) {
                queueGearSet(range);
                return;
            }
        }
    }

    private int[] getEquipmentItemIds(net.runelite.api.Item[] items) {
        if (items == null) return new int[0];
        int[] out = new int[items.length];
        for (int i = 0; i < items.length; i++) {
            var it = items[i];
            out[i] = it != null ? it.getId() : -1;
        }
        return out;
    }

    private int[] findNewlyEquipped(int[] prev, int[] curr) {
        Set<Integer> prevSet = new HashSet<>();
        for (int id : prev) if (id > 0) prevSet.add(id);
        List<Integer> added = new ArrayList<>();
        for (int id : curr) {
            if (id > 0 && !prevSet.contains(id)) added.add(id);
        }
        return added.stream().mapToInt(Integer::intValue).toArray();
    }


    private void queueGearSet(Set<Integer> gearIds) {
        pendingGearEquipIds.clear();
        for (int itemId : gearIds) {
            if (!Equipment.contains(itemId)) {
                pendingGearEquipIds.add(itemId);
            }
        }
    }

    private void processPendingGearEquips() {
        if (pendingGearEquipIds.isEmpty()) {
            return;
        }
        int equipAttempts = 0;
        for (int itemId : new ArrayList<>(pendingGearEquipIds)) {
            if (equipAttempts >= config.maxEquipPerTick()) {
                break;
            }
            if (Equipment.contains(itemId)) {
                pendingGearEquipIds.remove(itemId);
                continue;
            }
            var invItem = Inventory.getFirst(itemId);
            if (invItem == null) {
                pendingGearEquipIds.remove(itemId);
                continue;
            }
            String action = getEquipAction(itemId);
            if (action != null) {
                invItem.interact(action);
                equipAttempts++;
            }
        }
    }

    private void equipMageGear() {
        queueGearSet(parseGearIds(config.mageGearIds()));
    }

    private String getEquipAction(int itemId) {
        var def = client.getItemDefinition(itemId);
        if (def == null) return "Wear";
        String[] actions = def.getInventoryActions();
        if (actions != null) {
            for (String a : actions) {
                if (a != null && (a.equalsIgnoreCase("Wear") || a.equalsIgnoreCase("Wield") || a.equalsIgnoreCase("Equip")))
                    return a;
            }
        }
        return "Wear";
    }

    @Subscribe
    private void onChatMessage(ChatMessage event) {
        if (!isInInferno() || event.getType() != ChatMessageType.GAMEMESSAGE) return;
        String message = event.getMessage();
        if (message.contains("Wave completed!")) {
            resetLazyFlickState();
            disableAllActivePrayers();
            equipMageGear();
            WorldPoint START_TILE = WorldPoint.toLocalInstance(Static.getClient().getTopLevelWorldView(), new WorldPoint(2274, 5355, 0)).stream().findFirst().orElse(null);
            Movement.walkTo(START_TILE, new LocalCollisionMap(true));
        }
        if (message.contains("Wave:")) {
            message = message.substring(message.indexOf(": ") + 2);
            currentWaveNumber = Integer.parseInt(message.substring(0, message.indexOf('<')));
            waveLog("wave_start", "{\"wave\":" + currentWaveNumber + "}");
        }
    }

    private boolean isInInferno() {
        int[] regions = client.getTopLevelWorldView().getMapRegions();
        return regions != null && Arrays.stream(regions).anyMatch(r -> r == INFERNO_REGION);
    }

    int getNextWaveNumber() {
        return currentWaveNumber == -1 || currentWaveNumber == 69 ? -1 : currentWaveNumber + 1;
    }

    private void calculateUpcomingAttacks() {
        var playerLoc = client.getLocalPlayer().getWorldLocation();
        for (InfernoNPC infernoNPC : infernoNpcs) {
            if (infernoNPC.getType() == InfernoNPC.Type.BLOB
                && infernoNPC.getTicksTillNextAttack() == 4
                && config.autoTogglePrayer()
                && !Prayers.isEnabled(Prayer.PROTECT_FROM_MAGIC)
                && !Prayers.isEnabled(Prayer.PROTECT_FROM_MISSILES)
                && isPrayerHelper(infernoNPC)) {
                InfernoNPC.Attack bestSync = getHighestPriorityAttackAtTick(3);
                if (bestSync == InfernoNPC.Attack.MAGIC) {
                    Prayers.toggle(Prayer.PROTECT_FROM_MISSILES);
                } else if (bestSync == InfernoNPC.Attack.RANGED) {
                    Prayers.toggle(Prayer.PROTECT_FROM_MAGIC);
                } else {
                    Prayers.toggle(Prayer.PROTECT_FROM_MAGIC);
                }
            }
            infernoNPC.gameTick(client, lastLocation, finalPhase, ticksSinceFinalPhase);
            if (infernoNPC.getType() == InfernoNPC.Type.ZUK && zukShieldCornerTicks == -1) {
                infernoNPC.updateNextAttack(InfernoNPC.Attack.UNKNOWN, 12);
                zukShieldCornerTicks = 0;
            }
            if (infernoNPC.getTicksTillNextAttack() > 0 && isPrayerHelper(infernoNPC)
                && (infernoNPC.getNextAttack() != InfernoNPC.Attack.UNKNOWN
                || (config.indicateBlobDetectionTick() && infernoNPC.getType() == InfernoNPC.Type.BLOB && infernoNPC.getTicksTillNextAttack() >= 3))) {
                upcomingAttacks.computeIfAbsent(infernoNPC.getTicksTillNextAttack(), k -> new HashMap<>());
                if (config.indicateBlobDetectionTick() && infernoNPC.getType() == InfernoNPC.Type.BLOB && infernoNPC.getTicksTillNextAttack() >= 4) {
                    upcomingAttacks.computeIfAbsent(infernoNPC.getTicksTillNextAttack() - 3, k -> new HashMap<>());
                    upcomingAttacks.computeIfAbsent(infernoNPC.getTicksTillNextAttack() - 4, k -> new HashMap<>());
                    if (upcomingAttacks.get(infernoNPC.getTicksTillNextAttack() - 3).containsKey(InfernoNPC.Attack.MAGIC)) {
                        if (upcomingAttacks.get(infernoNPC.getTicksTillNextAttack() - 3).get(InfernoNPC.Attack.MAGIC) > InfernoNPC.Type.BLOB.getPriority()) {
                            upcomingAttacks.get(infernoNPC.getTicksTillNextAttack() - 3).put(InfernoNPC.Attack.MAGIC, InfernoNPC.Type.BLOB.getPriority());
                        }
                    } else if (upcomingAttacks.get(infernoNPC.getTicksTillNextAttack() - 3).containsKey(InfernoNPC.Attack.RANGED)) {
                        if (upcomingAttacks.get(infernoNPC.getTicksTillNextAttack() - 3).get(InfernoNPC.Attack.RANGED) > InfernoNPC.Type.BLOB.getPriority()) {
                            upcomingAttacks.get(infernoNPC.getTicksTillNextAttack() - 3).put(InfernoNPC.Attack.RANGED, InfernoNPC.Type.BLOB.getPriority());
                        }
                    } else if (upcomingAttacks.get(infernoNPC.getTicksTillNextAttack()) != null && upcomingAttacks.get(infernoNPC.getTicksTillNextAttack()).containsKey(InfernoNPC.Attack.MAGIC)
                        || (upcomingAttacks.get(infernoNPC.getTicksTillNextAttack() - 4) != null && upcomingAttacks.get(infernoNPC.getTicksTillNextAttack() - 4).containsKey(InfernoNPC.Attack.MAGIC))) {
                        if (!upcomingAttacks.get(infernoNPC.getTicksTillNextAttack() - 3).containsKey(InfernoNPC.Attack.RANGED)
                            || upcomingAttacks.get(infernoNPC.getTicksTillNextAttack() - 3).get(InfernoNPC.Attack.RANGED) > InfernoNPC.Type.BLOB.getPriority()) {
                            upcomingAttacks.get(infernoNPC.getTicksTillNextAttack() - 3).put(InfernoNPC.Attack.RANGED, InfernoNPC.Type.BLOB.getPriority());
                        }
                    } else if (upcomingAttacks.get(infernoNPC.getTicksTillNextAttack()) != null && upcomingAttacks.get(infernoNPC.getTicksTillNextAttack()).containsKey(InfernoNPC.Attack.RANGED)
                        || (upcomingAttacks.get(infernoNPC.getTicksTillNextAttack() - 4) != null && upcomingAttacks.get(infernoNPC.getTicksTillNextAttack() - 4).containsKey(InfernoNPC.Attack.RANGED))) {
                        if (!upcomingAttacks.get(infernoNPC.getTicksTillNextAttack() - 3).containsKey(InfernoNPC.Attack.MAGIC)
                            || upcomingAttacks.get(infernoNPC.getTicksTillNextAttack() - 3).get(InfernoNPC.Attack.MAGIC) > InfernoNPC.Type.BLOB.getPriority()) {
                            upcomingAttacks.get(infernoNPC.getTicksTillNextAttack() - 3).put(InfernoNPC.Attack.MAGIC, InfernoNPC.Type.BLOB.getPriority());
                        }
                    } else {
                        upcomingAttacks.get(infernoNPC.getTicksTillNextAttack() - 3).put(InfernoNPC.Attack.MAGIC, InfernoNPC.Type.BLOB.getPriority());
                    }
                } else {
                    InfernoNPC.Attack attack = infernoNPC.getNextAttack();
                    int priority = infernoNPC.getType().getPriority();
                    if (!upcomingAttacks.get(infernoNPC.getTicksTillNextAttack()).containsKey(attack)
                        || upcomingAttacks.get(infernoNPC.getTicksTillNextAttack()).get(attack) > priority) {
                        upcomingAttacks.get(infernoNPC.getTicksTillNextAttack()).put(attack, priority);
                    }
                }
            }
            addPreemptiveAttackForFirstHit(infernoNPC, playerLoc);
        }
        lastLocation = playerLoc;
    }

    private void addPreemptiveAttackForFirstHit(InfernoNPC infernoNPC, WorldPoint playerLoc) {
        if (infernoNPC.getTicksTillNextAttack() > 0) {
            return;
        }
        if (!canThreatenPlayerTile(infernoNPC, playerLoc)) {
            return;
        }
        if (infernoNPC.getType() == InfernoNPC.Type.BLOB) {
            if (!isPrayerHelper(infernoNPC)) return;
            addUpcomingAttack(1, InfernoNPC.Attack.MAGIC, infernoNPC.getType().getPriority());
            addUpcomingAttack(1, InfernoNPC.Attack.RANGED, infernoNPC.getType().getPriority());
            return;
        }
        if (!isPreemptivePrayerCandidate(infernoNPC)) {
            return;
        }
        addUpcomingAttack(0, infernoNPC.getType().getDefaultAttack(), infernoNPC.getType().getPriority());
        addUpcomingAttack(1, infernoNPC.getType().getDefaultAttack(), infernoNPC.getType().getPriority());
    }

    private boolean isPreemptivePrayerCandidate(InfernoNPC infernoNPC) {
        if (!isPrayerHelper(infernoNPC)) {
            return false;
        }
        if (infernoNPC.getType() == InfernoNPC.Type.JAD) {
            return false;
        }
        InfernoNPC.Attack defaultAttack = infernoNPC.getType().getDefaultAttack();
        return defaultAttack != null && defaultAttack.getPrayer() != null;
    }

    private InfernoNPC.Attack getHighestPriorityAttackAtTick(int tick) {
        Map<InfernoNPC.Attack, Integer> atTick = upcomingAttacks.get(tick);
        if (atTick == null || atTick.isEmpty()) return null;
        InfernoNPC.Attack best = null;
        int bestPrio = 999;
        for (Map.Entry<InfernoNPC.Attack, Integer> e : atTick.entrySet()) {
            if (e.getKey() == InfernoNPC.Attack.MAGIC || e.getKey() == InfernoNPC.Attack.RANGED) {
                if (e.getValue() < bestPrio) {
                    best = e.getKey();
                    bestPrio = e.getValue();
                }
            }
        }
        return best;
    }

    private boolean canThreatenPlayerTile(InfernoNPC infernoNPC, WorldPoint playerLoc) {
        InfernoNPC.Attack def = infernoNPC.getType().getDefaultAttack();
        if (def == InfernoNPC.Attack.MELEE) {
            return infernoNPC.canAttack(client, playerLoc);
        }
        return infernoNPC.canAttack(client, playerLoc)
            || infernoNPC.canMoveToAttack(client, playerLoc, obstacles);
    }

    private void addUpcomingAttack(int tick, InfernoNPC.Attack attack, int priority) {
        upcomingAttacks.computeIfAbsent(tick, k -> new HashMap<>());
        Integer existingPriority = upcomingAttacks.get(tick).get(attack);
        if (existingPriority == null || existingPriority > priority) {
            upcomingAttacks.get(tick).put(attack, priority);
        }
    }

    private void calculateClosestAttack() {
        closestAttackTick = 999;
        InfernoPrayerDisplayMode prayerDisplayMode = InfernoConfigParsers.prayerDisplayMode(config);
        if (prayerDisplayMode == InfernoPrayerDisplayMode.PRAYER_TAB || prayerDisplayMode == InfernoPrayerDisplayMode.BOTH) {
            int closestTick = 999;
            int closestPriority = 999;
            for (Integer tick : upcomingAttacks.keySet()) {
                Map<InfernoNPC.Attack, Integer> attackPriority = upcomingAttacks.get(tick);
                for (InfernoNPC.Attack currentAttack : attackPriority.keySet()) {
                    int currentPriority = attackPriority.get(currentAttack);
                    if (tick < closestTick || (tick == closestTick && currentPriority < closestPriority)) {
                        closestAttack = currentAttack;
                        closestPriority = currentPriority;
                        closestTick = tick;
                    }
                }
            }
            closestAttackTick = closestTick;
        }
    }

    private Prayer getPrayerForTick(int tick) {
        Map<InfernoNPC.Attack, Integer> attackPriority = upcomingAttacks.get(tick);
        if (attackPriority == null || attackPriority.isEmpty()) return null;
        InfernoNPC.Attack best = null;
        int bestPriority = 999;
        for (Map.Entry<InfernoNPC.Attack, Integer> e : attackPriority.entrySet()) {
            if (e.getValue() < bestPriority) {
                best = e.getKey();
                bestPriority = e.getValue();
            }
        }
        return best != null ? best.getPrayer() : null;
    }

    private Prayer getOffensivePrayerForCurrentStyle() {
        WeaponStyle style = Combat.getCurrentWeaponStyle();
        if (style == null) return null;
        switch (style) {
            case RANGE:
                return Prayers.getBestRangeOffensive();
            case MAGIC:
                return Prayers.getBestMageOffensive();
            default:
                return null;
        }
    }

    private int getOffensiveAttackSpeed() {
        int weaponId = getEquippedWeaponId();
        ItemStats stats = weaponId != -1 ? itemManager.getItemStats(weaponId) : null;
        int base = (stats != null && stats.getEquipment() != null) ? stats.getEquipment().getAspeed() : 5;
        WeaponStyle style = Combat.getCurrentWeaponStyle();
        if (style == WeaponStyle.RANGE && client.getVarpValue(VarPlayer.ATTACK_STYLE) == 1) {
            base = Math.max(2, base - 1);
        }
        return Math.max(2, Math.min(6, base));
    }

    private int getEquippedWeaponId() {
        ItemContainer inv = client.getItemContainer(InventoryID.EQUIPMENT);
        if (inv == null) return -1;
        Item weapon = inv.getItem(EquipmentInventorySlot.WEAPON.getSlotIdx());
        return weapon != null ? weapon.getId() : -1;
    }

    private boolean isPlayerAttackingNow() {
        var player = client.getLocalPlayer();
        if (player == null || !(player.getInteracting() instanceof NPC)) return false;
        int anim = player.getAnimation();
        if (anim == -1) return false;
        if (NON_ATTACK_ANIMATIONS.contains(anim)) return false;
        return true;
    }

    private boolean isInteractingWithNpc() {
        var player = client.getLocalPlayer();
        return player != null && player.getInteracting() instanceof NPC;
    }

    private void disableArmedOffensivePrayer() {
        if (armedOffensivePrayer == null) return;
        if (Prayers.isEnabled(armedOffensivePrayer)) Prayers.toggle(armedOffensivePrayer);
    }

    private void processOffensiveLazyFlick() {
        boolean interacting = isInteractingWithNpc();
        if (!interacting) {
            disableArmedOffensivePrayer();
            resetOffensiveFlickState();
            return;
        }
        Prayer offensivePrayer = getOffensivePrayerForCurrentStyle();
        if (offensivePrayer == null) return;

        int attackSpeed = getOffensiveAttackSpeed();
        boolean attackingNow = isPlayerAttackingNow();
        int anim = client.getLocalPlayer() != null ? client.getLocalPlayer().getAnimation() : -1;
        boolean newAttackTick = attackingNow && anim != lastOffensiveAnim;
        if (attackingNow) lastOffensiveAnim = anim;
        else lastOffensiveAnim = -1;
        if (newAttackTick) {
            disableArmedOffensivePrayer();
            armedOffensivePrayer = null;
            offensiveFirstAttackSeen = true;
            offensiveTicksSinceLastAttack = 0;
        } else {
            if (offensiveTicksSinceLastAttack < 0) {
                offensiveTicksSinceLastAttack = 0;
            } else {
                offensiveTicksSinceLastAttack++;
            }
            if (offensiveFirstAttackSeen && offensiveTicksSinceLastAttack == attackSpeed - 1) {
                if (!Prayers.isEnabled(offensivePrayer)) {
                    Prayers.toggle(offensivePrayer);
                }
                armedOffensivePrayer = offensivePrayer;
            } else if (offensiveTicksSinceLastAttack == attackSpeed && armedOffensivePrayer != null) {
                disableArmedOffensivePrayer();
                armedOffensivePrayer = null;
                offensiveTicksSinceLastAttack = 0;
            }
        }
    }

    private void calculateSafespots() {
        InfernoSafespotDisplayMode safespotDisplayMode = InfernoConfigParsers.safespotDisplayMode(config);
        InfernoZukShieldDisplayMode zukShieldBeforeHealersMode = InfernoConfigParsers.zukShieldBeforeHealersMode(config);
        InfernoZukShieldDisplayMode zukShieldAfterHealersMode = InfernoConfigParsers.zukShieldAfterHealersMode(config);
        if (currentWaveNumber < 69) {
            if (safespotDisplayMode != InfernoSafespotDisplayMode.OFF) {
                int checkSize = (int) Math.floor(config.safespotsCheckSize() / 2.0);
                WorldPoint center = client.getLocalPlayer().getWorldLocation();
                for (int x = -checkSize; x <= checkSize; x++) {
                    for (int y = -checkSize; y <= checkSize; y++) {
                        WorldPoint checkLoc = center.dx(x).dy(y);
                        if (obstacles.contains(checkLoc)) continue;
                        for (InfernoNPC infernoNPC : infernoNpcs) {
                            if (!isNormalSafespots(infernoNPC)) continue;
                            safeSpotMap.putIfAbsent(checkLoc, 0);
                            if (infernoNPC.canAttack(client, checkLoc) || infernoNPC.canMoveToAttack(client, checkLoc, obstacles)) {
                                applySafespotDanger(infernoNPC, checkLoc);
                            }
                        }
                    }
                }
            }
        } else if (currentWaveNumber == 69 && zukShield != null) {
            WorldPoint zukShieldCurrentPosition = zukShield.getWorldLocation();
            if (zukShieldLastPosition != null && zukShieldLastPosition.getX() != zukShieldCurrentPosition.getX() && zukShieldCornerTicks == -2) {
                zukShieldBase = zukShieldLastPosition;
                zukShieldCornerTicks = -1;
            }
            if (zukShieldLastPosition != null) {
                int zukShieldDelta = zukShieldCurrentPosition.getX() - zukShieldLastPosition.getX();
                if (zukShieldDelta != 0) zukShieldLastNonZeroDelta = zukShieldDelta;
                if (zukShieldLastDelta == 0 && zukShieldDelta != 0) zukShieldTicksLeftInCorner = 4;
                if (zukShieldDelta == 0) {
                    if (zukShieldLastNonZeroDelta > 0) zukShieldPositiveXCoord = zukShieldCurrentPosition.getX();
                    else if (zukShieldLastNonZeroDelta < 0) zukShieldNegativeXCoord = zukShieldCurrentPosition.getX();
                    if (zukShieldTicksLeftInCorner > 0) zukShieldTicksLeftInCorner--;
                }
                zukShieldLastDelta = zukShieldDelta;
            }
            zukShieldLastPosition = zukShieldCurrentPosition;
            if (safespotDisplayMode != InfernoSafespotDisplayMode.OFF) {
                if ((finalPhase && zukShieldAfterHealersMode == InfernoZukShieldDisplayMode.LIVE)
                    || (!finalPhase && zukShieldBeforeHealersMode == InfernoZukShieldDisplayMode.LIVE)) {
                    drawZukSafespot(zukShield.getWorldLocation().getX(), zukShield.getWorldLocation().getY(), 0);
                }
                if ((finalPhase && zukShieldAfterHealersMode == InfernoZukShieldDisplayMode.LIVEPLUSPREDICT)
                    || (!finalPhase && zukShieldBeforeHealersMode == InfernoZukShieldDisplayMode.LIVEPLUSPREDICT)) {
                    drawZukSafespot(zukShield.getWorldLocation().getX(), zukShield.getWorldLocation().getY(), 0);
                    drawZukPredictedSafespot();
                } else if ((finalPhase && zukShieldAfterHealersMode == InfernoZukShieldDisplayMode.PREDICT)
                    || (!finalPhase && zukShieldBeforeHealersMode == InfernoZukShieldDisplayMode.PREDICT)) {
                    drawZukPredictedSafespot();
                }
            }
        }
    }

    private void applySafespotDanger(InfernoNPC infernoNPC, WorldPoint checkLoc) {
        int current = safeSpotMap.get(checkLoc);
        if (infernoNPC.getType().getDefaultAttack() == InfernoNPC.Attack.MELEE) {
            if (current == 0) safeSpotMap.put(checkLoc, 1);
            else if (current == 2) safeSpotMap.put(checkLoc, 4);
            else if (current == 3) safeSpotMap.put(checkLoc, 5);
            else if (current == 6) safeSpotMap.put(checkLoc, 7);
        }
        if (infernoNPC.getType().getDefaultAttack() == InfernoNPC.Attack.MAGIC || (infernoNPC.getType() == InfernoNPC.Type.BLOB && current != 2 && current != 4)) {
            if (current == 0) safeSpotMap.put(checkLoc, 3);
            else if (current == 1) safeSpotMap.put(checkLoc, 5);
            else if (current == 2) safeSpotMap.put(checkLoc, 6);
            else if (current == 5) safeSpotMap.put(checkLoc, 7);
        }
        if (infernoNPC.getType().getDefaultAttack() == InfernoNPC.Attack.RANGED || (infernoNPC.getType() == InfernoNPC.Type.BLOB && current != 3 && current != 5)) {
            if (current == 0) safeSpotMap.put(checkLoc, 2);
            else if (current == 1) safeSpotMap.put(checkLoc, 4);
            else if (current == 3) safeSpotMap.put(checkLoc, 6);
            else if (current == 4) safeSpotMap.put(checkLoc, 7);
        }
        if (infernoNPC.getType() == InfernoNPC.Type.JAD && infernoNPC.getNpc().getWorldArea().isInMeleeDistance(checkLoc)) {
            if (current == 0) safeSpotMap.put(checkLoc, 1);
            else if (current == 2) safeSpotMap.put(checkLoc, 4);
            else if (current == 3) safeSpotMap.put(checkLoc, 5);
            else if (current == 6) safeSpotMap.put(checkLoc, 7);
        }
    }

    private void drawZukPredictedSafespot() {
        if (zukShield == null || zukShieldPositiveXCoord == -1 || zukShieldNegativeXCoord == -1) return;
        WorldPoint zukShieldCurrentPosition = zukShield.getWorldLocation();
        int nextShieldXCoord = zukShieldCurrentPosition.getX();
        for (InfernoNPC infernoNPC : infernoNpcs) {
            if (infernoNPC.getType() == InfernoNPC.Type.ZUK) {
                int ticksTilZukAttack = finalPhase ? infernoNPC.getTicksTillNextAttack() : infernoNPC.getTicksTillNextAttack() - 1;
                if (ticksTilZukAttack < 1) {
                    if (finalPhase) return;
                    ticksTilZukAttack = 10;
                }
                if (zukShieldLastNonZeroDelta > 0) {
                    nextShieldXCoord += ticksTilZukAttack;
                    if (nextShieldXCoord > zukShieldPositiveXCoord) {
                        nextShieldXCoord -= zukShieldTicksLeftInCorner;
                        if (nextShieldXCoord <= zukShieldPositiveXCoord) nextShieldXCoord = zukShieldPositiveXCoord;
                        else nextShieldXCoord = zukShieldPositiveXCoord - nextShieldXCoord + zukShieldPositiveXCoord;
                    }
                } else {
                    nextShieldXCoord -= ticksTilZukAttack;
                    if (nextShieldXCoord < zukShieldNegativeXCoord) {
                        nextShieldXCoord += zukShieldTicksLeftInCorner;
                        if (nextShieldXCoord >= zukShieldNegativeXCoord) nextShieldXCoord = zukShieldNegativeXCoord;
                        else nextShieldXCoord = zukShieldNegativeXCoord - nextShieldXCoord + zukShieldNegativeXCoord;
                    }
                }
            }
        }
        drawZukSafespot(nextShieldXCoord, zukShield.getWorldLocation().getY(), 2);
    }

    private void drawZukSafespot(int xCoord, int yCoord, int colorSafeSpotId) {
        for (int x = xCoord - 1; x <= xCoord + 3; x++) {
            for (int y = yCoord - 4; y <= yCoord - 2; y++) {
                safeSpotMap.put(new WorldPoint(x, y, client.getPlane()), colorSafeSpotId);
            }
        }
    }

    private void calculateSafespotAreas() {
        if (InfernoConfigParsers.safespotDisplayMode(config) == InfernoSafespotDisplayMode.AREA) {
            for (WorldPoint worldPoint : safeSpotMap.keySet()) {
                safeSpotAreas.computeIfAbsent(safeSpotMap.get(worldPoint), k -> new ArrayList<>()).add(worldPoint);
            }
        }
        lastLocation = client.getLocalPlayer().getWorldLocation();
    }

    private void calculateObstacles() {
        for (net.runelite.api.NPC npc : client.getTopLevelWorldView().npcs()) {
            obstacles.addAll(npc.getWorldArea().toWorldPointList());
        }
    }

    private void manageBlobDeathLocations() {
        if (config.indicateBlobDeathLocation()) {
            blobDeathSpots.forEach(InfernoBlobDeathSpot::decrementTick);
            blobDeathSpots.removeIf(InfernoBlobDeathSpot::isDone);
        }
    }

    private void calculateCentralNibbler() {
        InfernoNPC bestNibbler = null;
        int bestAmountInArea = 0;
        int bestDistanceToPlayer = 999;
        WorldPoint playerLoc = client.getLocalPlayer().getWorldLocation();
        for (InfernoNPC infernoNPC : infernoNpcs) {
            if (infernoNPC.getType() != InfernoNPC.Type.NIBBLER) continue;
            int amountInArea = 0;
            int distanceToPlayer = infernoNPC.getNpc().getWorldLocation().distanceTo(playerLoc);
            for (InfernoNPC checkNpc : infernoNpcs) {
                if (checkNpc.getType() != InfernoNPC.Type.NIBBLER
                    || checkNpc.getNpc().getWorldArea().distanceTo(infernoNPC.getNpc().getWorldArea()) > 1) continue;
                amountInArea++;
            }
            if (amountInArea > bestAmountInArea || (amountInArea == bestAmountInArea && distanceToPlayer < bestDistanceToPlayer)) {
                bestNibbler = infernoNPC;
                bestAmountInArea = amountInArea;
                bestDistanceToPlayer = distanceToPlayer;
            }
        }
        if (bestNibbler != null) centralNibbler = bestNibbler;
    }

    private void calculateSpawnTimerInfobox() {
        if (zuk == null || finalPhase || spawnTimerInfoBox == null) return;
        int pauseHp = 600;
        int resumeHp = 480;
        int hp = calculateNpcHp(zuk.getHealthRatio(), zuk.getHealthScale(), ZUK_MAX_HP);
        if (hp <= 0) return;
        if (spawnTimerInfoBox.isRunning()) {
            if (hp >= resumeHp && hp < pauseHp) spawnTimerInfoBox.pause();
        } else {
            if (hp < resumeHp) spawnTimerInfoBox.run();
        }
    }

    private static int calculateNpcHp(int ratio, int health, int maxHp) {
        if (ratio < 0 || health <= 0 || maxHp == -1) return -1;
        if (ratio > 0) {
            int minHealth = 1;
            int maxHealth;
            if (health > 1) {
                if (ratio > 1) minHealth = (maxHp * (ratio - 1) + health - 2) / (health - 1);
                maxHealth = (maxHp * ratio - 1) / (health - 1);
                if (maxHealth > maxHp) maxHealth = maxHp;
            } else {
                maxHealth = maxHp;
            }
            return (minHealth + maxHealth + 1) / 2;
        }
        return 0;
    }

    private static void toggleProtectionPrayer(Prayer prayer) {
        if (!Prayers.isEnabled(prayer)) {
            Prayers.toggle(prayer);
        }
    }

    private void resetProtectionFlickState() {
        armedProtectionPrayer = null;
        disablePrayerNextTick = false;
    }

    private void resetOffensiveFlickState() {
        armedOffensivePrayer = null;
        offensiveTicksSinceLastAttack = -1;
        offensiveFirstAttackSeen = false;
        lastOffensiveAnim = -1;
    }

    private void resetLazyFlickState() {
        resetProtectionFlickState();
        resetOffensiveFlickState();
    }

    private static void disableProtectionPrayers() {
        for (Prayer p : new Prayer[]{Prayer.PROTECT_FROM_MELEE, Prayer.PROTECT_FROM_MISSILES, Prayer.PROTECT_FROM_MAGIC}) {
            if (Prayers.isEnabled(p)) Prayers.toggle(p);
        }
    }

    private static void disableAllActivePrayers() {
        for (Prayer prayer : Prayer.values()) {
            if (Prayers.isEnabled(prayer)) {
                Prayers.toggle(prayer);
            }
        }
    }

    private boolean isPrayerHelper(InfernoNPC infernoNPC) {
        switch (infernoNPC.getType()) {
            case BAT: return config.prayerBat();
            case BLOB: return config.prayerBlob();
            case MELEE: return config.prayerMeleer();
            case RANGER: return config.prayerRanger();
            case MAGE: return config.prayerMage();
            case AKREK_XIL: return config.prayerRanger();
            case AKREK_MEJ: return config.prayerMage();
            case AKREK_KET: return config.prayerMeleer();
            case HEALER_JAD: return config.prayerHealerJad();
            case JAD: return config.prayerJad();
            default: return false;
        }
    }

    boolean isTicksOnNpc(InfernoNPC infernoNPC) {
        switch (infernoNPC.getType()) {
            case BAT: return config.ticksOnNpcBat();
            case BLOB: return config.ticksOnNpcBlob();
            case MELEE: return config.ticksOnNpcMeleer();
            case RANGER: return config.ticksOnNpcRanger();
            case MAGE: return config.ticksOnNpcMage();
            case AKREK_XIL: return config.ticksOnNpcRanger();
            case AKREK_MEJ: return config.ticksOnNpcMage();
            case AKREK_KET: return config.ticksOnNpcMeleer();
            case HEALER_JAD: return config.ticksOnNpcHealerJad();
            case JAD: return config.ticksOnNpcJad();
            case ZUK: return config.ticksOnNpcZuk();
            default: return false;
        }
    }

    boolean isNormalSafespots(InfernoNPC infernoNPC) {
        switch (infernoNPC.getType()) {
            case BAT: return config.safespotsBat();
            case BLOB: return config.safespotsBlob();
            case MELEE: return config.safespotsMeleer();
            case RANGER: return config.safespotsRanger();
            case MAGE: return config.safespotsMage();
            case AKREK_XIL: return config.safespotsRanger();
            case AKREK_MEJ: return config.safespotsMage();
            case AKREK_KET: return config.safespotsMeleer();
            case HEALER_JAD: return config.safespotsHealerJad();
            case JAD: return config.safespotsJad();
            default: return false;
        }
    }

    boolean isIndicateNpcPosition(InfernoNPC infernoNPC) {
        switch (infernoNPC.getType()) {
            case BAT: return config.indicateNpcPositionBat();
            case BLOB: return config.indicateNpcPositionBlob();
            case MELEE: return config.indicateNpcPositionMeleer();
            case RANGER: return config.indicateNpcPositionRanger();
            case MAGE: return config.indicateNpcPositionMage();
            default: return false;
        }
    }
}
