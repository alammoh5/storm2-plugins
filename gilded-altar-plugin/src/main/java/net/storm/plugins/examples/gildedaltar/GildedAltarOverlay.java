package net.storm.plugins.examples.gildedaltar;

import com.google.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import java.awt.*;

public class GildedAltarOverlay extends OverlayPanel {
    private final GildedAltarPlugin plugin;
    private final GildedAltarConfig config;

    @Inject
    public GildedAltarOverlay(GildedAltarPlugin plugin, GildedAltarConfig config) {
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
        this.panelComponent.getChildren().add(createBonesOfferedComponent());
        this.panelComponent.getChildren().add(createBonesPerHourComponent());
        this.panelComponent.getChildren().add(LineComponent.builder().left("").build());
        this.panelComponent.getChildren().add(createXpGainedComponent());
        this.panelComponent.getChildren().add(createXpPerHourComponent());

        return super.render(graphics);
    }

    private TitleComponent createTitleComponent() {
        return TitleComponent.builder()
                .text("GildedAltar")
                .color(Color.ORANGE)
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
                .rightColor(Color.GREEN)
                .build();
    }

    private LineComponent createBonesOfferedComponent() {
        return LineComponent.builder()
                .left("Bones Offered")
                .right(formatNumber(plugin.totalBonesOffered))
                .rightColor(Color.RED)
                .build();
    }

    private LineComponent createBonesPerHourComponent() {
        long runtime = System.currentTimeMillis() - plugin.startTime;
        int bonesPerHour = calculatePerHour(plugin.totalBonesOffered, runtime);

        return LineComponent.builder()
                .left("Bones/hr")
                .right(formatNumber(bonesPerHour))
                .rightColor(Color.RED)
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
