package net.storm.plugins.inferno.overlay;

import com.google.common.base.Strings;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Shape;
import net.runelite.api.Point;
import java.awt.Stroke;
import net.runelite.api.Prayer;
import net.storm.api.prayer.PrayerInfo;
import net.storm.sdk.widgets.Widgets;
import net.storm.api.widgets.Tab;
import net.storm.sdk.widgets.Tabs;

import static net.runelite.client.ui.overlay.OverlayUtil.renderPolygon;

public final class OverlayUtil {
    private OverlayUtil() {}

    public static java.awt.Rectangle renderPrayerOverlay(Graphics2D graphics, Prayer prayer, Color color) {
        PrayerInfo prayerInfo = PrayerInfo.MAP.get(prayer);
        if (prayerInfo == null) return null;
        var widget = Widgets.get(prayerInfo.getComponent());
        if (widget == null || !Tabs.isOpen(Tab.PRAYER)) return null;
        java.awt.Rectangle bounds = widget.getBounds();
        renderPolygon(graphics, rectToPolygon(bounds), color);
        return bounds;
    }

    private static java.awt.Polygon rectToPolygon(java.awt.Rectangle rect) {
        return new java.awt.Polygon(
            new int[]{rect.x, rect.x + rect.width, rect.x + rect.width, rect.x},
            new int[]{rect.y, rect.y, rect.y + rect.height, rect.y + rect.height},
            4);
    }

    public static void renderTextLocation(Graphics2D g, String txt, int fontSize, int fontStyle, Color color, Point canvasPoint, boolean shadows, int yOffset) {
        g.setFont(new Font("Arial", fontStyle, fontSize));
        if (canvasPoint == null) return;
        Point center = new Point(canvasPoint.getX(), canvasPoint.getY() + yOffset);
        Point shadow = new Point(canvasPoint.getX() + 1, canvasPoint.getY() + 1 + yOffset);
        if (shadows) renderTextLocation(g, shadow, txt, Color.BLACK);
        renderTextLocation(g, center, txt, color);
    }

    public static void renderTextLocation(Graphics2D g, Point loc, String text, Color color) {
        if (Strings.isNullOrEmpty(text)) return;
        g.setColor(Color.BLACK);
        g.drawString(text, loc.getX() + 1, loc.getY() + 1);
        g.setColor(color);
        g.drawString(text, loc.getX(), loc.getY());
    }

    public static void renderOutlinePolygon(Graphics2D g, Shape poly, Color color) {
        g.setColor(color);
        Stroke orig = g.getStroke();
        g.setStroke(new BasicStroke(2));
        g.draw(poly);
        g.setStroke(orig);
    }

    public static void renderFilledPolygon(Graphics2D g, Shape poly, Color color) {
        g.setColor(color);
        Stroke orig = g.getStroke();
        g.setStroke(new BasicStroke(2));
        g.draw(poly);
        g.fill(poly);
        g.setStroke(orig);
    }

    public static void renderAreaTilePolygon(Graphics2D g, Shape poly, Color color) {
        g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 10));
        g.fill(poly);
    }

    public static void renderFullLine(Graphics2D g, int[][] line, Color color) {
        g.setColor(color);
        Stroke orig = g.getStroke();
        g.setStroke(new BasicStroke(2));
        g.drawLine(line[0][0], line[0][1], line[1][0], line[1][1]);
        g.setStroke(orig);
    }

    public static void renderDashedLine(Graphics2D g, int[][] line, Color color) {
        g.setColor(color);
        Stroke orig = g.getStroke();
        g.setStroke(new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{9}, 0));
        g.drawLine(line[0][0], line[0][1], line[1][0], line[1][1]);
        g.setStroke(orig);
    }
}
