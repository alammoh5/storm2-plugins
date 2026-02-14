package net.storm.plugins.inferno;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import net.runelite.api.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.storm.api.prayer.PrayerInfo;
import net.storm.plugins.inferno.displaymodes.InfernoPrayerDisplayMode;
import net.storm.plugins.inferno.displaymodes.InfernoSafespotDisplayMode;
import net.storm.sdk.widgets.Prayers;
import net.storm.sdk.widgets.Widgets;
import net.runelite.api.Prayer;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;

import static net.runelite.client.ui.overlay.OverlayUtil.renderPolygon;
import static net.storm.plugins.inferno.overlay.OverlayUtil.*;

@Singleton
public class InfernoOverlay extends Overlay {
    private static final int TICK_PIXEL_SIZE = 60;
    private static final int BOX_WIDTH = 10;
    private static final int BOX_HEIGHT = 5;

    private final Client client;
    private final InfernoPlugin plugin;
    private final InfernoConfig config;

    @Inject
    InfernoOverlay(Client client, InfernoPlugin plugin, InfernoConfig config) {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(OverlayPriority.HIGHEST);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        InfernoSafespotDisplayMode safespotDisplayMode = InfernoConfigParsers.safespotDisplayMode(config);
        InfernoPrayerDisplayMode prayerDisplayMode = InfernoConfigParsers.prayerDisplayMode(config);
        if (config.indicateObstacles()) renderObstacles(graphics);
        if (safespotDisplayMode == InfernoSafespotDisplayMode.AREA) renderAreaSafepots(graphics);
        else if (safespotDisplayMode == InfernoSafespotDisplayMode.INDIVIDUAL_TILES) renderIndividualTilesSafespots(graphics);
        if (config.indicateBlobDeathLocation()) renderBlobDeathPoly(graphics);

        for (InfernoNPC infernoNPC : plugin.getInfernoNpcs()) {
            if (infernoNPC.getNpc().getConvexHull() != null) {
                if (config.indicateNonSafespotted() && plugin.isNormalSafespots(infernoNPC)
                    && infernoNPC.canAttack(client, client.getLocalPlayer().getWorldLocation())) {
                    renderPolygon(graphics, infernoNPC.getNpc().getConvexHull(), Color.RED);
                }
                if (config.indicateTemporarySafespotted() && plugin.isNormalSafespots(infernoNPC)
                    && infernoNPC.canMoveToAttack(client, client.getLocalPlayer().getWorldLocation(), plugin.getObstacles())) {
                    renderPolygon(graphics, infernoNPC.getNpc().getConvexHull(), Color.YELLOW);
                }
                if (config.indicateSafespotted() && plugin.isNormalSafespots(infernoNPC)) {
                    renderPolygon(graphics, infernoNPC.getNpc().getConvexHull(), Color.GREEN);
                }
                if (config.indicateNibblers() && infernoNPC.getType() == InfernoNPC.Type.NIBBLER
                    && (!config.indicateCentralNibbler() || plugin.getCentralNibbler() != infernoNPC)) {
                    renderPolygon(graphics, infernoNPC.getNpc().getConvexHull(), Color.CYAN);
                }
                if (config.indicateCentralNibbler() && infernoNPC.getType() == InfernoNPC.Type.NIBBLER
                    && plugin.getCentralNibbler() == infernoNPC) {
                    renderPolygon(graphics, infernoNPC.getNpc().getConvexHull(), Color.BLUE);
                }
                if (config.indicateActiveHealerJad() && infernoNPC.getType() == InfernoNPC.Type.HEALER_JAD
                    && infernoNPC.getNpc().getInteracting() != client.getLocalPlayer()) {
                    renderPolygon(graphics, infernoNPC.getNpc().getConvexHull(), Color.CYAN);
                }
                if (config.indicateActiveHealerZuk() && infernoNPC.getType() == InfernoNPC.Type.HEALER_ZUK
                    && infernoNPC.getNpc().getInteracting() != client.getLocalPlayer()) {
                    renderPolygon(graphics, infernoNPC.getNpc().getConvexHull(), Color.CYAN);
                }
            }
            if (plugin.isIndicateNpcPosition(infernoNPC)) renderNpcLocation(graphics, infernoNPC);
            if (plugin.isTicksOnNpc(infernoNPC) && infernoNPC.getTicksTillNextAttack() > 0) {
                renderTicksOnNpc(graphics, infernoNPC, infernoNPC.getNpc());
            }
            if (config.ticksOnNpcZukShield() && infernoNPC.getType() == InfernoNPC.Type.ZUK && plugin.getZukShield() != null
                && infernoNPC.getTicksTillNextAttack() > 0) {
                renderTicksOnNpc(graphics, infernoNPC, plugin.getZukShield());
            }
            if (config.ticksOnNpcMeleerDig() && infernoNPC.getType() == InfernoNPC.Type.MELEE
                && infernoNPC.getIdleTicks() >= config.digTimerThreshold() && infernoNPC.getTicksTillNextAttack() == 0) {
                renderDigTimer(graphics, infernoNPC);
            }
        }

        boolean prayerWidgetHidden = getPrayerWidget(plugin.getClosestAttack()) == null;
        if ((prayerDisplayMode == InfernoPrayerDisplayMode.PRAYER_TAB || prayerDisplayMode == InfernoPrayerDisplayMode.BOTH)
            && (!prayerWidgetHidden || config.alwaysShowPrayerHelper())) {
            renderPrayerIconOverlay(graphics);
            if (config.descendingBoxes()) renderDescendingBoxes(graphics);
        }

        InfernoSpawnTimerInfobox timer = plugin.getSpawnTimerInfoBox();
        if (timer != null && config.spawnTimerInfobox()) {
            graphics.setColor(timer.getTextColor());
            graphics.drawString(timer.getText(), 10, 20);
        }
        return null;
    }

    private static net.runelite.api.widgets.Widget getPrayerWidget(InfernoNPC.Attack attack) {
        if (attack == null || attack.getPrayer() == null) return null;
        PrayerInfo info = PrayerInfo.MAP.get(attack.getPrayer());
        return info != null ? (net.runelite.api.widgets.Widget) Widgets.get(info.getComponent()) : null;
    }

    private void renderObstacles(Graphics2D g) {
        for (WorldPoint wp : plugin.getObstacles()) {
            LocalPoint lp = LocalPoint.fromWorld(client, wp);
            if (lp == null) continue;
            Polygon poly = Perspective.getCanvasTilePoly(client, lp);
            if (poly != null) renderPolygon(g, poly, Color.BLUE);
        }
    }

    private void renderIndividualTilesSafespots(Graphics2D g) {
        for (WorldPoint wp : plugin.getSafeSpotMap().keySet()) {
            int id = plugin.getSafeSpotMap().get(wp);
            if (id > 6) continue;
            LocalPoint lp = LocalPoint.fromWorld(client, wp);
            if (lp == null) continue;
            Polygon poly = Perspective.getCanvasTilePoly(client, lp);
            if (poly == null) continue;
            Color c;
            switch (id) {
                case 0: c = Color.WHITE; break;
                case 1: c = Color.RED; break;
                case 2: c = Color.GREEN; break;
                case 3: c = Color.BLUE; break;
                case 4: c = new Color(255, 255, 0); break;
                case 5: c = new Color(255, 0, 255); break;
                case 6: c = new Color(0, 255, 255); break;
                default: continue;
            }
            renderPolygon(g, poly, c);
        }
    }

    private void renderAreaSafepots(Graphics2D g) {
        for (int safeSpotId : plugin.getSafeSpotAreas().keySet()) {
            if (safeSpotId > 6) continue;
            Color c1;
            Color c2 = null;
            Color cf;
            switch (safeSpotId) {
                case 0: c1 = Color.WHITE; cf = Color.WHITE; break;
                case 1: c1 = Color.RED; cf = Color.RED; break;
                case 2: c1 = Color.GREEN; cf = Color.GREEN; break;
                case 3: c1 = Color.BLUE; cf = Color.BLUE; break;
                case 4: c1 = Color.RED; c2 = Color.GREEN; cf = Color.YELLOW; break;
                case 5: c1 = Color.RED; c2 = Color.BLUE; cf = new Color(255, 0, 255); break;
                case 6: c1 = Color.GREEN; c2 = Color.BLUE; cf = new Color(0, 255, 255); break;
                default: continue;
            }
            List<int[][]> allEdges = new ArrayList<>();
            int edgeSizeSq = 0;
            for (WorldPoint wp : plugin.getSafeSpotAreas().get(safeSpotId)) {
                LocalPoint lp = LocalPoint.fromWorld(client, wp);
                if (lp == null) continue;
                Polygon tp = Perspective.getCanvasTilePoly(client, lp);
                if (tp == null) continue;
                renderAreaTilePolygon(g, tp, cf);
                for (int i = 0; i < 4; i++) {
                    int[] p1 = new int[]{tp.xpoints[i], tp.ypoints[i]};
                    int[] p2 = new int[]{tp.xpoints[(i + 1) % 4], tp.ypoints[(i + 1) % 4]};
                    allEdges.add(new int[][]{p1, p2});
                    edgeSizeSq += Math.pow(tp.xpoints[i] - tp.xpoints[(i + 1) % 4], 2) + Math.pow(tp.ypoints[i] - tp.ypoints[(i + 1) % 4], 2);
                }
            }
            if (allEdges.isEmpty()) continue;
            edgeSizeSq /= allEdges.size();
            int tolSq = (int) Math.ceil(edgeSizeSq / 6);
            for (int i = 0; i < allEdges.size(); i++) {
                int[][] be = allEdges.get(i);
                boolean dup = false;
                for (int j = 0; j < allEdges.size(); j++) {
                    if (i == j) continue;
                    if (edgeEquals(be, allEdges.get(j), tolSq)) { dup = true; break; }
                }
                if (!dup) {
                    renderFullLine(g, be, c1);
                    if (c2 != null) renderDashedLine(g, be, c2);
                }
            }
        }
    }

    private boolean edgeEquals(int[][] a, int[][] b, int tol) {
        return (ptEq(a[0], b[0], tol) && ptEq(a[1], b[1], tol)) || (ptEq(a[0], b[1], tol) && ptEq(a[1], b[0], tol));
    }

    private boolean ptEq(int[] p1, int[] p2, int tolSq) {
        return Math.pow(p1[0] - p2[0], 2) + Math.pow(p1[1] - p2[1], 2) <= tolSq;
    }

    private void renderBlobDeathPoly(Graphics2D g) {
        g.setColor(config.getBlobDeathLocationColor());
        for (InfernoBlobDeathSpot spot : plugin.getBlobDeathSpots()) {
            Polygon area = Perspective.getCanvasTileAreaPoly(client, spot.getLocation(), 3);
            Color c = config.blobDeathLocationFade()
                ? new Color(config.getBlobDeathLocationColor().getRed(), config.getBlobDeathLocationColor().getGreen(),
                    config.getBlobDeathLocationColor().getBlue(), spot.fillAlpha())
                : config.getBlobDeathLocationColor();
            renderOutlinePolygon(g, area, c);
            String ticks = String.valueOf(spot.getTicksUntilDone());
            Point loc = Perspective.getCanvasTextLocation(client, g, spot.getLocation(), ticks, 0);
            renderTextLocation(g, ticks, plugin.getTextSize(), plugin.getFontStyle().getFont(), config.getBlobDeathLocationColor(), loc, false, 0);
        }
    }

    private void renderTicksOnNpc(Graphics2D g, InfernoNPC npc, NPC renderOn) {
        Color c = (npc.getTicksTillNextAttack() == 1 || (npc.getType() == InfernoNPC.Type.BLOB && npc.getTicksTillNextAttack() == 4))
            ? npc.getNextAttack().getCriticalColor() : npc.getNextAttack().getNormalColor();
        g.setFont(new Font("Arial", plugin.getFontStyle().getFont(), plugin.getTextSize()));
        String txt = String.valueOf(npc.getTicksTillNextAttack());
        Point pt = renderOn.getCanvasTextLocation(g, txt, 0);
        renderTextLocation(g, txt, plugin.getTextSize(), plugin.getFontStyle().getFont(), c, pt, false, 0);
    }

    private void renderNpcLocation(Graphics2D g, InfernoNPC npc) {
        LocalPoint lp = LocalPoint.fromWorld(client, npc.getNpc().getWorldLocation());
        if (lp != null) {
            Polygon poly = Perspective.getCanvasTilePoly(client, lp);
            if (poly != null) renderPolygon(g, poly, Color.BLUE);
        }
    }

    private void renderDigTimer(Graphics2D g, InfernoNPC npc) {
        String txt = String.valueOf(npc.getIdleTicks());
        g.setFont(new Font("Arial", plugin.getFontStyle().getFont(), config.getMeleeDigFontSize()));
        Point loc = npc.getNpc().getCanvasTextLocation(g, txt, 0);
        if (loc == null) return;
        Color digColor = npc.getIdleTicks() < config.digTimerDangerThreshold() ? config.getMeleeDigSafeColor() : config.getMeleeDigDangerColor();
        renderTextLocation(g, txt, config.getMeleeDigFontSize(), plugin.getFontStyle().getFont(), digColor, loc, false, 0);
    }

    private void renderPrayerIconOverlay(Graphics2D g) {
        InfernoNPC.Attack closest = plugin.getClosestAttack();
        if (closest == null) return;
        InfernoNPC.Attack prayerFor = Prayers.isEnabled(Prayer.PROTECT_FROM_MAGIC) ? InfernoNPC.Attack.MAGIC
            : Prayers.isEnabled(Prayer.PROTECT_FROM_MISSILES) ? InfernoNPC.Attack.RANGED
            : Prayers.isEnabled(Prayer.PROTECT_FROM_MELEE) ? InfernoNPC.Attack.MELEE : null;
        if (closest != prayerFor || config.indicateWhenPrayingCorrectly()) {
            var widget = getPrayerWidget(closest);
            if (widget != null && !widget.isHidden()) {
                Rectangle r = widget.getBounds();
                if (r != null) {
                    Rectangle rect = new Rectangle((int) r.getWidth(), (int) r.getHeight());
                    rect.translate((int) r.getX(), (int) r.getY());
                    Color prayerColor = closest == prayerFor ? Color.GREEN : Color.RED;
                    renderOutlinePolygon(g, rect, prayerColor);
                }
            }
        }
    }

    private void renderDescendingBoxes(Graphics2D g) {
        for (Integer tick : plugin.getUpcomingAttacks().keySet()) {
            Map<InfernoNPC.Attack, Integer> ap = plugin.getUpcomingAttacks().get(tick);
            int bestP = 999;
            InfernoNPC.Attack best = null;
            for (Map.Entry<InfernoNPC.Attack, Integer> e : ap.entrySet()) {
                if (e.getValue() < bestP) { best = e.getKey(); bestP = e.getValue(); }
            }
            for (InfernoNPC.Attack atk : ap.keySet()) {
                if (atk.getPrayer() == null) continue;
                var widget = getPrayerWidget(atk);
                if (widget == null || widget.isHidden()) continue;
                Rectangle b = widget.getBounds();
                if (b == null) continue;
                int baseX = (int) (b.getX() + b.getWidth() / 2) - BOX_WIDTH / 2;
                int baseY = (int) (b.getY() - tick * TICK_PIXEL_SIZE - BOX_HEIGHT)
                    + (int) (TICK_PIXEL_SIZE - (plugin.getLastTick() + 600 - System.currentTimeMillis()) / 600.0 * TICK_PIXEL_SIZE);
                Rectangle box = new Rectangle(BOX_WIDTH, BOX_HEIGHT);
                box.translate(baseX, baseY);
                Color color = (tick == 1 && atk == best) ? Color.RED : Color.ORANGE;
                if (atk == best) renderFilledPolygon(g, box, color);
                else if (config.indicateNonPriorityDescendingBoxes()) renderOutlinePolygon(g, box, color);
            }
        }
    }
}
