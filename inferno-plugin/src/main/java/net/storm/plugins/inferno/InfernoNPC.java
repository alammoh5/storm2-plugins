package net.storm.plugins.inferno;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Prayer;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.storm.sdk.widgets.Prayers;

class InfernoNPC {
    static final int JAL_NIB = 7574;
    static final int JAL_MEJRAH = 7578;
    static final int JAL_MEJRAH_STAND = 7577;
    static final int JAL_AK_RANGE_ATTACK = 7581;
    static final int JAL_AK_MELEE_ATTACK = 7582;
    static final int JAL_AK_MAGIC_ATTACK = 7583;
    static final int JAL_IMKOT = 7597;
    static final int JAL_XIL_MELEE_ATTACK = 7604;
    static final int JAL_XIL_RANGE_ATTACK = 7605;
    static final int JAL_ZEK_MAGE_ATTACK = 7610;
    static final int JAL_ZEK_MELEE_ATTACK = 7612;
    static final int JALTOK_JAD_MAGE_ATTACK = 7592;
    static final int JALTOK_JAD_RANGE_ATTACK = 7593;
    static final int TZKAL_ZUK = 7566;

    @Getter(AccessLevel.PACKAGE)
    private final NPC npc;
    @Getter(AccessLevel.PACKAGE)
    private final Type type;
    @Getter(AccessLevel.PACKAGE)
    private Attack nextAttack;
    @Getter(AccessLevel.PACKAGE)
    @Setter(AccessLevel.PACKAGE)
    private int ticksTillNextAttack;
    @Getter(AccessLevel.PACKAGE)
    private int idleTicks;
    private int lastAnimation;
    private boolean lastCanAttack;
    private final Map<WorldPoint, Integer> safeSpotCache;

    InfernoNPC(NPC npc) {
        this.npc = npc;
        this.type = Type.typeFromId(npc.getId());
        this.nextAttack = type != null ? type.getDefaultAttack() : Attack.UNKNOWN;
        this.ticksTillNextAttack = 0;
        this.lastAnimation = -1;
        this.lastCanAttack = false;
        this.idleTicks = 0;
        this.safeSpotCache = new HashMap<>();
    }

    private int getHealth() {
        return npc.getHealthRatio() * 100;
    }
    boolean isDead() {
        return getHealth() == 0;
    }

    void updateNextAttack(Attack nextAttack, int ticksTillNextAttack) {
        this.idleTicks = 0;
        this.nextAttack = nextAttack;
        this.ticksTillNextAttack = ticksTillNextAttack;
    }

    private void updateNextAttack(Attack nextAttack) {
        this.nextAttack = nextAttack;
    }

    boolean canAttack(Client client, WorldPoint target) {
        if (safeSpotCache.containsKey(target)) {
            return safeSpotCache.get(target) == 2;
        }
        var wv = client.getTopLevelWorldView();
        WorldArea targetArea = new WorldArea(target, 1, 1);
        boolean hasLos = targetArea.hasLineOfSightTo(wv, npc.getWorldArea());
        boolean hasRange = type.getDefaultAttack() == Attack.MELEE
            ? npc.getWorldArea().isInMeleeDistance(target)
            : npc.getWorldArea().distanceTo(target) <= type.getRange();
        if (hasLos && hasRange) {
            safeSpotCache.put(target, 2);
        }
        return hasLos && hasRange;
    }

    boolean canMoveToAttack(Client client, WorldPoint target, List<WorldPoint> obstacles) {
        if (safeSpotCache.containsKey(target)) {
            int v = safeSpotCache.get(target);
            return v == 1 || v == 2;
        }
        if (canAttack(client, target)) {
            safeSpotCache.put(target, 1);
            return true;
        }
        List<WorldPoint> realObstacles = new ArrayList<>();
        for (WorldPoint obstacle : obstacles) {
            if (npc.getWorldArea().toWorldPointList().contains(obstacle)) continue;
            realObstacles.add(obstacle);
        }
        var wv = client.getTopLevelWorldView();
        WorldArea current = npc.getWorldArea();
        for (int steps = 0; steps < 30; steps++) {
            int dx = Integer.compare(target.getX(), current.getX());
            int dy = Integer.compare(target.getY(), current.getY());
            if (dx == 0 && dy == 0) break;
            java.util.function.Predicate<WorldPoint> pred = p -> {
                for (WorldPoint o : realObstacles) {
                    if (new WorldArea(p, 1, 1).intersectsWith(new WorldArea(o, 1, 1))) return false;
                }
                return true;
            };
            if (dx != 0 && dy != 0) {
                if (current.canTravelInDirection(wv, dx, 0, pred)) {
                    current = new WorldArea(current.getX() + dx, current.getY(), current.getWidth(), current.getHeight(), current.getPlane());
                } else if (current.canTravelInDirection(wv, 0, dy, pred)) {
                    current = new WorldArea(current.getX(), current.getY() + dy, current.getWidth(), current.getHeight(), current.getPlane());
                } else {
                    safeSpotCache.put(target, 0);
                    return false;
                }
            } else {
                if (!current.canTravelInDirection(wv, dx, dy, pred)) {
                    safeSpotCache.put(target, 0);
                    return false;
                }
                current = new WorldArea(current.getX() + dx, current.getY() + dy, current.getWidth(), current.getHeight(), current.getPlane());
            }
            if (new WorldArea(target, 1, 1).hasLineOfSightTo(wv, current)
                && (type.getDefaultAttack() == Attack.MELEE ? current.isInMeleeDistance(target) : current.distanceTo(target) <= type.getRange())) {
                safeSpotCache.put(target, 1);
                return true;
            }
        }
        safeSpotCache.put(target, 0);
        return false;
    }

    private boolean couldAttackPrevTick(Client client, WorldPoint lastPlayerLocation) {
        return new WorldArea(lastPlayerLocation, 1, 1).hasLineOfSightTo(client.getTopLevelWorldView(), npc.getWorldArea());
    }

    void gameTick(Client client, WorldPoint lastPlayerLocation, boolean finalPhase, int ticksSinceFinalPhase) {
        safeSpotCache.clear();
        this.idleTicks += 1;
        if (type == Type.BLOB && ticksTillNextAttack == 3
            && client.getLocalPlayer().getWorldLocation().distanceTo(npc.getWorldArea()) <= Type.BLOB.getRange()) {
            Attack nextBlobAttack = Attack.MAGIC;
            if (Prayers.isEnabled(Prayer.PROTECT_FROM_MISSILES)) {
                nextBlobAttack = Attack.MAGIC;
            } else if (Prayers.isEnabled(Prayer.PROTECT_FROM_MAGIC)) {
                nextBlobAttack = Attack.RANGED;
            }
            updateNextAttack(nextBlobAttack);
        }
        if (ticksTillNextAttack > 0) {
            this.ticksTillNextAttack--;
        }
        if (type == Type.JAD && npc.getAnimation() != -1 && npc.getAnimation() != lastAnimation) {
            Attack currentAttack = Attack.attackFromId(npc.getAnimation());
            if (currentAttack != null && currentAttack != Attack.UNKNOWN) {
                updateNextAttack(currentAttack, type.getTicksAfterAnimation());
            }
        }
        if (ticksTillNextAttack <= 0) {
            switch (type) {
                case ZUK:
                    if (npc.getAnimation() == TZKAL_ZUK) {
                        if (finalPhase) {
                            if (ticksSinceFinalPhase > 3) {
                                updateNextAttack(type.getDefaultAttack(), 7);
                            }
                        } else {
                            updateNextAttack(type.getDefaultAttack(), 10);
                        }
                    }
                    break;
                case JAD:
                    if (nextAttack != Attack.UNKNOWN) {
                        updateNextAttack(type.getDefaultAttack(), 8);
                    }
                    break;
                case BLOB:
                    if (!lastCanAttack && couldAttackPrevTick(client, lastPlayerLocation)) {
                        updateNextAttack(Attack.UNKNOWN, 3);
                    } else if (!lastCanAttack && canAttack(client, client.getLocalPlayer().getWorldLocation())) {
                        updateNextAttack(Attack.UNKNOWN, 4);
                    } else if (npc.getAnimation() != -1) {
                        updateNextAttack(type.getDefaultAttack(), type.getTicksAfterAnimation());
                    }
                    break;
                case BAT:
                    if (canAttack(client, client.getLocalPlayer().getWorldLocation())
                        && npc.getAnimation() != JAL_MEJRAH_STAND && npc.getAnimation() != -1) {
                        updateNextAttack(type.getDefaultAttack(), type.getTicksAfterAnimation());
                    }
                    break;
                case MELEE:
                case RANGER:
                case MAGE:
                    if (npc.getAnimation() == JAL_IMKOT
                        || npc.getAnimation() == JAL_XIL_RANGE_ATTACK || npc.getAnimation() == JAL_XIL_MELEE_ATTACK
                        || npc.getAnimation() == JAL_ZEK_MAGE_ATTACK || npc.getAnimation() == JAL_ZEK_MELEE_ATTACK) {
                        updateNextAttack(type.getDefaultAttack(), type.getTicksAfterAnimation());
                    } else if (npc.getAnimation() == 7600) {
                        updateNextAttack(type.getDefaultAttack(), 12);
                    } else if (npc.getAnimation() == 7611) {
                        updateNextAttack(type.getDefaultAttack(), 8);
                    }
                    break;
                case AKREK_XIL:
                case AKREK_MEJ:
                case AKREK_KET:
                    if (npc.getAnimation() == JAL_AK_RANGE_ATTACK || npc.getAnimation() == JAL_AK_MELEE_ATTACK
                        || npc.getAnimation() == JAL_AK_MAGIC_ATTACK) {
                        updateNextAttack(type.getDefaultAttack(), type.getTicksAfterAnimation());
                    }
                    break;
                default:
                    if (npc.getAnimation() != -1) {
                        updateNextAttack(type.getDefaultAttack(), type.getTicksAfterAnimation());
                    }
                    break;
            }
        }
        lastAnimation = npc.getAnimation();
        lastCanAttack = canAttack(client, client.getLocalPlayer().getWorldLocation());
    }

    private static boolean contains(int[] arr, int val) {
        for (int i : arr) {
            if (i == val) return true;
        }
        return false;
    }

    @Getter(AccessLevel.PACKAGE)
    enum Attack {
        MELEE(Prayer.PROTECT_FROM_MELEE, Color.ORANGE, Color.RED, new int[]{JAL_NIB, JAL_AK_MELEE_ATTACK, JAL_IMKOT, JAL_XIL_MELEE_ATTACK, JAL_ZEK_MELEE_ATTACK}),
        RANGED(Prayer.PROTECT_FROM_MISSILES, Color.GREEN, new Color(0, 128, 0), new int[]{JAL_MEJRAH, JAL_AK_RANGE_ATTACK, JAL_XIL_RANGE_ATTACK, JALTOK_JAD_RANGE_ATTACK}),
        MAGIC(Prayer.PROTECT_FROM_MAGIC, Color.CYAN, Color.BLUE, new int[]{JAL_AK_MAGIC_ATTACK, JAL_ZEK_MAGE_ATTACK, JALTOK_JAD_MAGE_ATTACK}),
        UNKNOWN(null, Color.WHITE, Color.GRAY, new int[]{});

        private final Prayer prayer;
        private final Color normalColor;
        private final Color criticalColor;
        private final int[] animationIds;

        Attack(Prayer prayer, Color normalColor, Color criticalColor, int[] animationIds) {
            this.prayer = prayer;
            this.normalColor = normalColor;
            this.criticalColor = criticalColor;
            this.animationIds = animationIds;
        }

        static Attack attackFromId(int animationId) {
            for (Attack attack : Attack.values()) {
                if (contains(attack.animationIds, animationId)) {
                    return attack;
                }
            }
            return null;
        }
    }

    @Getter(AccessLevel.PACKAGE)
    enum Type {
        NIBBLER(new int[]{net.runelite.api.NpcID.JALNIB}, Attack.MELEE, 4, 99, 100),
        BAT(new int[]{net.runelite.api.NpcID.JALMEJRAH}, Attack.RANGED, 3, 4, 7),
        BLOB(new int[]{net.runelite.api.NpcID.JALAK}, Attack.UNKNOWN, 6, 15, 4),
        MELEE(new int[]{net.runelite.api.NpcID.JALIMKOT}, Attack.MELEE, 4, 1, 3),
        RANGER(new int[]{net.runelite.api.NpcID.JALXIL, net.runelite.api.NpcID.JALXIL_7702}, Attack.RANGED, 4, 98, 2),
        MAGE(new int[]{net.runelite.api.NpcID.JALZEK, net.runelite.api.NpcID.JALZEK_7703}, Attack.MAGIC, 4, 98, 1),
        AKREK_XIL(new int[]{net.runelite.api.NpcID.JALAKREKXIL}, Attack.RANGED, 4, 15, 8),
        AKREK_MEJ(new int[]{net.runelite.api.NpcID.JALAKREKMEJ}, Attack.MAGIC, 4, 15, 9),
        AKREK_KET(new int[]{net.runelite.api.NpcID.JALAKREKKET}, Attack.MELEE, 4, 1, 10),
        JAD(new int[]{net.runelite.api.NpcID.JALTOKJAD, net.runelite.api.NpcID.JALTOKJAD_7704, 10623}, Attack.UNKNOWN, 3, 99, 0),
        HEALER_JAD(new int[]{net.runelite.api.NpcID.YTHURKOT, net.runelite.api.NpcID.YTHURKOT_7701, net.runelite.api.NpcID.YTHURKOT_7705}, Attack.MELEE, 4, 1, 6),
        ZUK(new int[]{net.runelite.api.NpcID.TZKALZUK}, Attack.UNKNOWN, 10, 99, 99),
        HEALER_ZUK(new int[]{net.runelite.api.NpcID.JALMEJJAK, 10624}, Attack.UNKNOWN, -1, 99, 100);

        private final int[] npcIds;
        private final Attack defaultAttack;
        private final int ticksAfterAnimation;
        private final int range;
        private final int priority;

        Type(int[] npcIds, Attack defaultAttack, int ticksAfterAnimation, int range, int priority) {
            this.npcIds = npcIds;
            this.defaultAttack = defaultAttack;
            this.ticksAfterAnimation = ticksAfterAnimation;
            this.range = range;
            this.priority = priority;
        }

        static Type typeFromId(int npcId) {
            for (Type type : Type.values()) {
                if (contains(type.npcIds, npcId)) {
                    return type;
                }
            }
            return null;
        }
    }
}
