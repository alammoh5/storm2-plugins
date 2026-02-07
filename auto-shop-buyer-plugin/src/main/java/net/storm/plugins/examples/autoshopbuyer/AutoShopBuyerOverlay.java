package net.storm.plugins.examples.autoshopbuyer;

import com.google.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import java.awt.*;

public class AutoShopBuyerOverlay extends OverlayPanel {
    private final AutoShopBuyerPlugin plugin;

    @Inject
    public AutoShopBuyerOverlay(AutoShopBuyerPlugin plugin, AutoShopBuyerConfig config) {
        this.plugin = plugin;
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        this.panelComponent.setPreferredSize(new Dimension(220, 0));
        this.panelComponent.setGap(new Point(5, 5));

        this.panelComponent.getChildren().add(createTitleComponent());
        this.panelComponent.getChildren().add(createRunningTimeComponent());
        this.panelComponent.getChildren().add(createStatusComponent());

        return super.render(graphics);
    }

    private TitleComponent createTitleComponent() {
        return TitleComponent.builder()
                .text("Auto Shop Buyer")
                .color(Color.CYAN)
                .build();
    }

    private LineComponent createRunningTimeComponent() {
        return LineComponent.builder()
                .left("Runtime")
                .right(formatRuntime(plugin.startTime))
                .rightColor(Color.ORANGE)
                .build();
    }

    private LineComponent createStatusComponent() {
        return LineComponent.builder()
                .left("Status")
                .right(plugin.status != null ? plugin.status : "Initializing...")
                .rightColor(plugin.isPaused ? Color.RED : Color.GREEN)
                .build();
    }

    public static String formatRuntime(long startTime) {
        long runTime = System.currentTimeMillis() - startTime;

        long seconds = (runTime / 1000) % 60;
        long minutes = (runTime / (1000 * 60)) % 60;
        long hours = (runTime / (1000 * 60 * 60));

        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
}

