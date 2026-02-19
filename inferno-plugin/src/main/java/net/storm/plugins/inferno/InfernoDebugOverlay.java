package net.storm.plugins.inferno;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.components.ComponentConstants;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

@Singleton
public class InfernoDebugOverlay extends Overlay {
    private final Client client;
    private final InfernoConfig config;
    private final PanelComponent panel = new PanelComponent();

    @Inject
    InfernoDebugOverlay(Client client, InfernoConfig config) {
        this.client = client;
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
        setPriority(OverlayPriority.LOW);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!config.debug()) return null;
        panel.getChildren().clear();
        panel.setPreferredSize(new Dimension(320, 0));
        var player = client.getLocalPlayer();
        if (player == null) return null;
        int anim = player.getAnimation();
        WorldPoint playerLocation = player.getWorldLocation();
        WorldPoint localInstanceLocation = WorldPoint.fromLocalInstance(client, player.getLocalLocation());
        panel.getChildren().add(TitleComponent.builder().text("Debug").color(Color.WHITE).build());
        panel.getChildren().add(LineComponent.builder().left("Animation: ").right(String.valueOf(anim)).build());
        panel.getChildren().add(LineComponent.builder().left("Player Location: ").right(String.valueOf(playerLocation)).build());
        panel.getChildren().add(LineComponent.builder().left("Local Instance Location: ").right(String.valueOf(localInstanceLocation)).build());
        panel.setBackgroundColor(ComponentConstants.STANDARD_BACKGROUND_COLOR);
        return panel.render(graphics);
    }
}