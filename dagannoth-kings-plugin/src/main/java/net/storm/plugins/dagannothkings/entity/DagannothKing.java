package net.storm.plugins.dagannothkings.entity;

import java.awt.Color;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Actor;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.Prayer;
import net.storm.api.domain.actors.INPC;
import static net.storm.plugins.dagannothkings.DagannothKingsPlugin.ANIMATION_ID_DAG_PRIME;
import static net.storm.plugins.dagannothkings.DagannothKingsPlugin.ANIMATION_ID_DAG_REX;
import static net.storm.plugins.dagannothkings.DagannothKingsPlugin.ANIMATION_ID_DAG_SUPREME;

@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class DagannothKing implements Comparable<DagannothKing> {
    @Getter
    @EqualsAndHashCode.Include
    private final INPC npc;

    @Getter
    private final int npcId;

    @Getter
    private int ticksUntilNextAnimation;

    private final int animationId;
    private final int animationTickSpeed;

    @Getter
    private final AttackStyle attackStyle;

    @Getter
    private final int attackRange;

    public DagannothKing(final INPC npc) {
        this.npc = npc;
        this.npcId = npc.getId();
        this.ticksUntilNextAnimation = 0;

        final Boss boss = Boss.of(npcId);
        this.animationId = boss.animationId;
        this.animationTickSpeed = boss.attackSpeed;
        this.attackStyle = boss.attackStyle;
        this.attackRange = boss.attackRange;
    }

    public void updateTicksUntilNextAnimation() {
        if (ticksUntilNextAnimation > 0) {
            ticksUntilNextAnimation--;
        }

        if (npc.getAnimation() == animationId && ticksUntilNextAnimation == 0) {
            ticksUntilNextAnimation = animationTickSpeed;
        }
    }

    public Actor getInteractingActor() {
        return npc.getInteracting();
    }

    public Actor getTarget() {
        return npc.getTarget();
    }

    @Override
    public int compareTo(final DagannothKing dagannothKing) {
        if (dagannothKing.ticksUntilNextAnimation == 0) {
            return -1;
        }

        return ticksUntilNextAnimation - dagannothKing.ticksUntilNextAnimation;
    }

    @RequiredArgsConstructor
    public enum Boss {
        DAGANNOTH_PRIME(NpcID.DAGCAVE_MAGIC_BOSS, ANIMATION_ID_DAG_PRIME, 4, AttackStyle.MAGE, 10),
        DAGANNOTH_REX(NpcID.DAGCAVE_MELEE_BOSS, ANIMATION_ID_DAG_REX, 4, AttackStyle.MELEE, 10),
        DAGANNOTH_SUPREME(NpcID.DAGCAVE_RANGED_BOSS, ANIMATION_ID_DAG_SUPREME, 4, AttackStyle.RANGE, 10);

        private final int npcId;
        private final int animationId;
        private final int attackSpeed;
        private final AttackStyle attackStyle;
        private final int attackRange;

        public static Boss of(final int npcId) {
            for (final Boss boss : Boss.values()) {
                if (boss.npcId == npcId) {
                    return boss;
                }
            }

            throw new IllegalArgumentException("Unsupported Boss npcId");
        }
    }

    @Getter
    @RequiredArgsConstructor
    public enum AttackStyle {
        MAGE(Prayer.PROTECT_FROM_MAGIC, Color.CYAN),
        RANGE(Prayer.PROTECT_FROM_MISSILES, Color.GREEN),
        MELEE(Prayer.PROTECT_FROM_MELEE, Color.RED);

        private final Prayer prayer;
        private final Color color;
    }
}

