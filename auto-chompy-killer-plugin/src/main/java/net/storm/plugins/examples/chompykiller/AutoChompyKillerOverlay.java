package net.storm.plugins.examples.chompykiller;

import com.google.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import java.awt.*;

public class AutoChompyKillerOverlay extends OverlayPanel {
    private final AutoChompyKillerPlugin plugin;

    @Inject
    public AutoChompyKillerOverlay(AutoChompyKillerPlugin plugin, AutoChompyKillerConfig config) {
        this.plugin = plugin;
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        this.panelComponent.setPreferredSize(new Dimension(300, 0));
        this.panelComponent.setGap(new Point(5, 5));

        this.panelComponent.getChildren().add(createTitleComponent());
        
        this.panelComponent.getChildren().add(LineComponent.builder()
                .left("Status")
                .right(plugin.status != null ? plugin.status : "Initializing...")
                .rightColor(Color.GREEN)
                .build());

        this.panelComponent.getChildren().add(LineComponent.builder().left("").build());
        
        this.panelComponent.getChildren().add(LineComponent.builder()
                .left("Chompies Attacked")
                .right(String.valueOf(plugin.totalChompiesAttacked))
                .rightColor(Color.ORANGE)
                .build());
        
        long runtime = System.currentTimeMillis() - plugin.startTime;
        int chompiesPerHour = calculatePerHour(plugin.totalChompiesAttacked, runtime);
        this.panelComponent.getChildren().add(LineComponent.builder()
                .left("Chompies/hr")
                .right(String.valueOf(chompiesPerHour))
                .rightColor(Color.ORANGE)
                .build());

        return super.render(graphics);
    }
    
    private int calculatePerHour(int total, long runtime) {
        if (runtime <= 0) {
            return 0;
        }
        return (int) ((double) total / runtime * 3600000);
    }

    private TitleComponent createTitleComponent() {
        return TitleComponent.builder()
                .text("Auto Chompy Killer")
                .color(Color.CYAN)
                .build();
    }
}


