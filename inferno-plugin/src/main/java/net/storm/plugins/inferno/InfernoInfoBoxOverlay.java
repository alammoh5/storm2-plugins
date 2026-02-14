package net.storm.plugins.inferno;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.components.ComponentConstants;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import net.storm.plugins.inferno.displaymodes.InfernoPrayerDisplayMode;
import net.storm.sdk.widgets.Prayers;

@Singleton
public class InfernoInfoBoxOverlay extends Overlay {
    private static final Color NOT_ACTIVATED_BACKGROUND = new Color(150, 0, 0, 150);

    private final InfernoPlugin plugin;
    private final InfernoConfig config;
    private final PanelComponent imagePanelComponent = new PanelComponent();

    @Inject
    InfernoInfoBoxOverlay(InfernoPlugin plugin, InfernoConfig config) {
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.BOTTOM_RIGHT);
        setPriority(OverlayPriority.HIGH);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        InfernoPrayerDisplayMode prayerDisplayMode = InfernoConfigParsers.prayerDisplayMode(config);
        if (prayerDisplayMode != InfernoPrayerDisplayMode.BOTTOM_RIGHT
            && prayerDisplayMode != InfernoPrayerDisplayMode.BOTH) {
            return null;
        }
        imagePanelComponent.getChildren().clear();
        InfernoNPC.Attack closest = plugin.getClosestAttack();
        if (closest != null && closest.getPrayer() != null) {
            String label = closest == InfernoNPC.Attack.MELEE ? "Melee" : closest == InfernoNPC.Attack.RANGED ? "Range" : "Magic";
            imagePanelComponent.getChildren().add(TitleComponent.builder().text(label).color(closest.getNormalColor()).build());
            boolean correct = Prayers.isEnabled(closest.getPrayer());
            imagePanelComponent.setBackgroundColor(correct ? ComponentConstants.STANDARD_BACKGROUND_COLOR : NOT_ACTIVATED_BACKGROUND);
        } else {
            imagePanelComponent.setBackgroundColor(ComponentConstants.STANDARD_BACKGROUND_COLOR);
        }
        return imagePanelComponent.render(graphics);
    }
}
