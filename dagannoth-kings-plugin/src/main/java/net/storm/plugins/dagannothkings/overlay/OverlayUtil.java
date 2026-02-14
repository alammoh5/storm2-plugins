package net.storm.plugins.dagannothkings.overlay;

import com.google.common.base.Strings;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.Stroke;
import net.runelite.api.Point;
import net.runelite.api.Prayer;
import net.storm.api.widgets.Tab;
import net.storm.api.prayer.PrayerInfo;
import net.storm.sdk.widgets.Tabs;
import net.storm.sdk.widgets.Widgets;
import static net.runelite.client.ui.overlay.OverlayUtil.renderPolygon;

public class OverlayUtil {
    public static Rectangle renderPrayerOverlay(final Graphics2D graphics, final Prayer prayer, final Color color) {
        final PrayerInfo prayerInfo = PrayerInfo.MAP.get(prayer);
        if (prayerInfo == null) {
            return null;
        }

        final var widget = Widgets.get(prayerInfo.getComponent());

        if (widget == null || !Tabs.isOpen(Tab.PRAYER)) {
            return null;
        }

        final Rectangle bounds = widget.getBounds();
        renderPolygon(graphics, rectangleToPolygon(bounds), color);
        return bounds;
    }

    private static Polygon rectangleToPolygon(final Rectangle rect) {
        final int[] xpoints = {rect.x, rect.x + rect.width, rect.x + rect.width, rect.x};
        final int[] ypoints = {rect.y, rect.y, rect.y + rect.height, rect.y + rect.height};
        return new Polygon(xpoints, ypoints, 4);
    }

    public static void renderTextLocation(final Graphics2D graphics, final String txtString, final int fontSize,
                                          final int fontStyle, final Color fontColor, final Point canvasPoint,
                                          final boolean shadows, final int yOffset) {
        graphics.setFont(new Font("Arial", fontStyle, fontSize));
        if (canvasPoint == null) {
            return;
        }

        final Point canvasCenterPoint = new Point(canvasPoint.getX(), canvasPoint.getY() + yOffset);
        final Point canvasCenterPointShadow = new Point(canvasPoint.getX() + 1, canvasPoint.getY() + 1 + yOffset);
        if (shadows) {
            renderTextLocation(graphics, canvasCenterPointShadow, txtString, Color.BLACK);
        }
        renderTextLocation(graphics, canvasCenterPoint, txtString, fontColor);
    }

    public static void renderTextLocation(final Graphics2D graphics, final Point txtLoc, final String text,
                                          final Color color) {
        if (Strings.isNullOrEmpty(text)) {
            return;
        }

        final int x = txtLoc.getX();
        final int y = txtLoc.getY();

        graphics.setColor(Color.BLACK);
        graphics.drawString(text, x + 1, y + 1);

        graphics.setColor(color);
        graphics.drawString(text, x, y);
    }

    public static void renderFilledPolygon(final Graphics2D graphics, final Shape poly, final Color color) {
        graphics.setColor(color);
        final Stroke originalStroke = graphics.getStroke();
        graphics.setStroke(new BasicStroke(2));
        graphics.draw(poly);
        graphics.fill(poly);
        graphics.setStroke(originalStroke);
    }
}

