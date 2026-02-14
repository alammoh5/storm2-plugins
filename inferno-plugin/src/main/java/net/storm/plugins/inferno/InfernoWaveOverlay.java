package net.storm.plugins.inferno;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.AccessLevel;
import lombok.Setter;
import net.storm.plugins.inferno.displaymodes.InfernoWaveDisplayMode;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.components.PanelComponent;

import static net.storm.plugins.inferno.InfernoWaveMappings.addWaveComponent;

@Singleton
public class InfernoWaveOverlay extends Overlay {
    private final InfernoPlugin plugin;
    private final InfernoConfig config;
    private final PanelComponent panelComponent = new PanelComponent();

    @Setter(AccessLevel.PACKAGE)
    private Color waveHeaderColor;

    @Setter(AccessLevel.PACKAGE)
    private Color waveTextColor;

    @Setter(AccessLevel.PACKAGE)
    private InfernoWaveDisplayMode displayMode;

    @Inject
    InfernoWaveOverlay(InfernoPlugin plugin, InfernoConfig config) {
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.TOP_RIGHT);
        setPriority(OverlayPriority.HIGH);
        panelComponent.setPreferredSize(new Dimension(160, 0));
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        panelComponent.getChildren().clear();
        if (displayMode == InfernoWaveDisplayMode.CURRENT || displayMode == InfernoWaveDisplayMode.BOTH) {
            addWaveComponent(config, panelComponent, "Current Wave (Wave " + plugin.getCurrentWaveNumber() + ")",
                plugin.getCurrentWaveNumber(), waveHeaderColor, waveTextColor);
        }
        if (displayMode == InfernoWaveDisplayMode.NEXT || displayMode == InfernoWaveDisplayMode.BOTH) {
            addWaveComponent(config, panelComponent, "Next Wave (Wave " + plugin.getNextWaveNumber() + ")",
                plugin.getNextWaveNumber(), waveHeaderColor, waveTextColor);
        }
        return panelComponent.render(graphics);
    }
}
