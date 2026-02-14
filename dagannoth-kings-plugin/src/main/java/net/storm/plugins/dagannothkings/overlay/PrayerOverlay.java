package net.storm.plugins.dagannothkings.overlay;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Actor;
import net.runelite.api.Point;
import net.runelite.api.Prayer;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.storm.api.prayer.PrayerInfo;
import net.storm.plugins.dagannothkings.DagannothKingsConfig;
import net.storm.plugins.dagannothkings.DagannothKingsPlugin;
import net.storm.plugins.dagannothkings.entity.DagannothKing;
import net.storm.sdk.widgets.Widgets;

@Singleton
public class PrayerOverlay extends Overlay {
    private static final int TICK_PIXEL_SIZE = 60;
    private static final int BOX_WIDTH = 10;
    private static final int BOX_HEIGHT = 5;

    private final DagannothKingsPlugin plugin;
    private final DagannothKingsConfig config;

    @Inject
    public PrayerOverlay(final DagannothKingsPlugin plugin, final DagannothKingsConfig config) {
        this.plugin = plugin;
        this.config = config;

        setPosition(OverlayPosition.DYNAMIC);
        setPriority(OverlayPriority.HIGH);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(final Graphics2D graphics2D) {
        final Set<DagannothKing> dagannothKings = plugin.getDagannothKings();
        if (dagannothKings.isEmpty() || (!config.showPrayerWidgetOverlay() && !config.showGuitarHeroOverlay())) {
            return null;
        }

        renderPrayer(graphics2D, dagannothKings);
        return null;
    }

    private void renderPrayer(final Graphics2D graphics2D, final Set<DagannothKing> dagannothKings) {
        final Actor player = net.storm.sdk.entities.Players.getLocal();
        final Map<Prayer, Integer> tickMap = new HashMap<>();

        for (final DagannothKing dagannothKing : dagannothKings) {
            final int ticksUntilNextAnimation = dagannothKing.getTicksUntilNextAnimation();
            final DagannothKing.AttackStyle attackStyle = dagannothKing.getAttackStyle();

            if (dagannothKing.getNpc() == null
                    || dagannothKing.getNpc().isDead()
                    || ticksUntilNextAnimation <= 0
                    || (config.ignoringNonAttacking() && dagannothKing.getInteractingActor() != player)) {
                continue;
            }

            if (config.showPrayerWidgetOverlay()) {
                final Rectangle rectangle = OverlayUtil.renderPrayerOverlay(graphics2D, attackStyle.getPrayer(), attackStyle.getColor());
                if (rectangle == null) {
                    continue;
                }

                final String text = String.valueOf(ticksUntilNextAnimation);
                final int fontSize = 16;
                final int fontStyle = Font.BOLD;
                final Color fontColor = ticksUntilNextAnimation == 1 ? Color.WHITE : attackStyle.getColor();
                final int x = (int) (rectangle.getX() + rectangle.getWidth() / 2);
                final int y = (int) (rectangle.getY() + rectangle.getHeight() / 2);
                final Point prayerWidgetPoint = new Point(x, y);
                final Point canvasPoint = new Point(prayerWidgetPoint.getX() - 3, prayerWidgetPoint.getY() + 6);
                OverlayUtil.renderTextLocation(graphics2D, text, fontSize, fontStyle, fontColor, canvasPoint, true, 0);
            }

            if (config.showGuitarHeroOverlay()) {
                tickMap.put(attackStyle.getPrayer(), ticksUntilNextAnimation);
            }
        }

        for (final Map.Entry<Prayer, Integer> entry : tickMap.entrySet()) {
            renderDescendingBoxes(graphics2D, entry.getKey(), entry.getValue());
        }
    }

    private void renderDescendingBoxes(final Graphics2D graphics2D, final Prayer prayer, final int tick) {
        final Color color = tick == 1 ? Color.RED : Color.ORANGE;
        final PrayerInfo prayerInfo = PrayerInfo.MAP.get(prayer);
        if (prayerInfo == null) {
            return;
        }

        final var prayerWidget = Widgets.get(prayerInfo.getComponent());
        if (prayerWidget == null || prayerWidget.isHidden()) {
            return;
        }

        int baseX = (int) prayerWidget.getBounds().getX();
        baseX += prayerWidget.getBounds().getWidth() / 2;
        baseX -= BOX_WIDTH / 2;

        int baseY = (int) prayerWidget.getBounds().getY() - tick * TICK_PIXEL_SIZE - BOX_HEIGHT;
        baseY += TICK_PIXEL_SIZE - ((plugin.getLastTickTime() + 600 - System.currentTimeMillis()) / 600.0 * TICK_PIXEL_SIZE);

        final Rectangle boxRectangle = new Rectangle(BOX_WIDTH, BOX_HEIGHT);
        boxRectangle.translate(baseX, baseY);
        OverlayUtil.renderFilledPolygon(graphics2D, boxRectangle, color);
    }
}

