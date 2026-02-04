package net.storm.plugins.examples.chompykiller;

import com.google.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;

import java.awt.*;

public class AutoChompyKillerOverlay extends OverlayPanel {
    private final AutoChompyKillerPlugin plugin;

    @Inject
    public AutoChompyKillerOverlay(AutoChompyKillerPlugin plugin, AutoChompyKillerConfig config) {
        this.plugin = plugin;
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        return super.render(graphics);
    }
}


