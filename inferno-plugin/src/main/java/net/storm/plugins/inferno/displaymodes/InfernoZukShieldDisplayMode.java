package net.storm.plugins.inferno.displaymodes;

import lombok.AccessLevel;
import lombok.Getter;

@Getter(AccessLevel.PACKAGE)
public enum InfernoZukShieldDisplayMode {
    OFF("Off"),
    LIVE("Live (follow shield)"),
    PREDICT("Predict"),
    LIVEPLUSPREDICT("Live and Predict");

    private final String name;

    InfernoZukShieldDisplayMode(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return this.name;
    }
}
