package net.storm.plugins.inferno;

import net.storm.plugins.inferno.displaymodes.InfernoNamingDisplayMode;
import net.storm.plugins.inferno.displaymodes.InfernoPrayerDisplayMode;
import net.storm.plugins.inferno.displaymodes.InfernoSafespotDisplayMode;
import net.storm.plugins.inferno.displaymodes.InfernoWaveDisplayMode;
import net.storm.plugins.inferno.displaymodes.InfernoZukShieldDisplayMode;

final class InfernoConfigParsers {
    private InfernoConfigParsers() {}

    static InfernoPrayerDisplayMode prayerDisplayMode(InfernoConfig config) {
        try {
            return InfernoPrayerDisplayMode.valueOf(config.prayerDisplayMode());
        } catch (Exception e) {
            return InfernoPrayerDisplayMode.BOTH;
        }
    }

    static InfernoSafespotDisplayMode safespotDisplayMode(InfernoConfig config) {
        try {
            return InfernoSafespotDisplayMode.valueOf(config.safespotDisplayMode());
        } catch (Exception e) {
            return InfernoSafespotDisplayMode.AREA;
        }
    }

    static InfernoWaveDisplayMode waveDisplayMode(InfernoConfig config) {
        try {
            return InfernoWaveDisplayMode.valueOf(config.waveDisplay());
        } catch (Exception e) {
            return InfernoWaveDisplayMode.BOTH;
        }
    }

    static InfernoNamingDisplayMode namingDisplayMode(InfernoConfig config) {
        try {
            return InfernoNamingDisplayMode.valueOf(config.npcNaming());
        } catch (Exception e) {
            return InfernoNamingDisplayMode.SIMPLE;
        }
    }

    static InfernoZukShieldDisplayMode zukShieldBeforeHealersMode(InfernoConfig config) {
        try {
            return InfernoZukShieldDisplayMode.valueOf(config.safespotsZukShieldBeforeHealers());
        } catch (Exception e) {
            return InfernoZukShieldDisplayMode.PREDICT;
        }
    }

    static InfernoZukShieldDisplayMode zukShieldAfterHealersMode(InfernoConfig config) {
        try {
            return InfernoZukShieldDisplayMode.valueOf(config.safespotsZukShieldAfterHealers());
        } catch (Exception e) {
            return InfernoZukShieldDisplayMode.LIVE;
        }
    }
}
