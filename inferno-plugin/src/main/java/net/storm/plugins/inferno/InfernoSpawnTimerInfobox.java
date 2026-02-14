package net.storm.plugins.inferno;

import java.awt.Color;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;

class InfernoSpawnTimerInfobox {
    static final long SPAWN_DURATION = 210;
    static final long SPAWN_DURATION_INCREMENT = 105;
    static final long SPAWN_DURATION_WARNING = 120;
    static final long SPAWN_DURATION_DANGER = 30;

    private long timeRemaining;
    private long startTime;

    @Getter(AccessLevel.PACKAGE)
    private boolean running;

    InfernoSpawnTimerInfobox() {
        running = false;
        timeRemaining = SPAWN_DURATION;
    }

    void run() {
        startTime = Instant.now().getEpochSecond();
        running = true;
    }

    void reset() {
        running = false;
        timeRemaining = SPAWN_DURATION;
    }

    void pause() {
        if (!running) return;
        running = false;
        long timeElapsed = Instant.now().getEpochSecond() - startTime;
        timeRemaining = Math.max(0, timeRemaining - timeElapsed);
        timeRemaining += SPAWN_DURATION_INCREMENT;
    }

    String getText() {
        long seconds = running
            ? Math.max(0, timeRemaining - (Instant.now().getEpochSecond() - startTime))
            : timeRemaining;
        long minutes = seconds % 3600 / 60;
        long secs = seconds % 60;
        return String.format("%02d:%02d", minutes, secs);
    }

    Color getTextColor() {
        long seconds = running
            ? Math.max(0, timeRemaining - (Instant.now().getEpochSecond() - startTime))
            : timeRemaining;
        return seconds <= SPAWN_DURATION_DANGER ? Color.RED
            : seconds <= SPAWN_DURATION_WARNING ? Color.ORANGE : Color.GREEN;
    }
}
