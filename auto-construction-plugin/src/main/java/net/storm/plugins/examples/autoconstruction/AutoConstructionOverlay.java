package net.storm.plugins.examples.autoconstruction;

import com.google.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import java.awt.*;

public class AutoConstructionOverlay extends OverlayPanel {
    private final AutoConstructionPlugin plugin;
    private final AutoConstructionConfig config;

    @Inject
    public AutoConstructionOverlay(AutoConstructionPlugin plugin, AutoConstructionConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        this.panelComponent.setPreferredSize(new Dimension(220, 0));
        this.panelComponent.setGap(new Point(5, 5));

        this.panelComponent.getChildren().add(createTitleComponent());
        this.panelComponent.getChildren().add(createRunningTimeComponent());
        this.panelComponent.getChildren().add(createStatusComponent());
        
        this.panelComponent.getChildren().add(LineComponent.builder().left("").build());
        this.panelComponent.getChildren().add(createXpGainedComponent());
        this.panelComponent.getChildren().add(createXpPerHourComponent());
        this.panelComponent.getChildren().add(createBenchesBuiltComponent());
        this.panelComponent.getChildren().add(createBenchesPerHourComponent());

        return super.render(graphics);
    }

    private TitleComponent createTitleComponent() {
        return TitleComponent.builder()
                .text("Mahogany Benches")
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

    private LineComponent createXpGainedComponent() {
        return LineComponent.builder()
                .left("Total XP")
                .right(formatNumber(plugin.totalXpGained))
                .rightColor(Color.CYAN)
                .build();
    }

    private LineComponent createXpPerHourComponent() {
        long runtime = System.currentTimeMillis() - plugin.startTime;
        int xpPerHour = calculatePerHour(plugin.totalXpGained, runtime);
        
        return LineComponent.builder()
                .left("XP/hr")
                .right(formatNumber(xpPerHour))
                .rightColor(Color.CYAN)
                .build();
    }

    private LineComponent createBenchesBuiltComponent() {
        return LineComponent.builder()
                .left("Benches Built")
                .right(formatNumber(plugin.totalBenchesBuilt))
                .rightColor(Color.MAGENTA)
                .build();
    }

    private LineComponent createBenchesPerHourComponent() {
        long runtime = System.currentTimeMillis() - plugin.startTime;
        int benchesPerHour = calculatePerHour(plugin.totalBenchesBuilt, runtime);
        
        return LineComponent.builder()
                .left("Benches/hr")
                .right(formatNumber(benchesPerHour))
                .rightColor(Color.MAGENTA)
                .build();
    }

    private int calculatePerHour(int total, long runtime) {
        if (runtime <= 0) {
            return 0;
        }
        return (int) ((double) total / runtime * 3600000);
    }

    private String formatNumber(int number) {
        if (number >= 1000000) {
            return String.format("%.2fM", number / 1000000.0);
        } else if (number >= 1000) {
            return String.format("%.1fK", number / 1000.0);
        }
        return String.valueOf(number);
    }

    public static String formatRuntime(long startTime) {
        long runTime = System.currentTimeMillis() - startTime;

        long seconds = (runTime / 1000) % 60;
        long minutes = (runTime / (1000 * 60)) % 60;
        long hours = (runTime / (1000 * 60 * 60));

        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
}
