package com.frostether.desktop;

import com.frostether.frostchain.Api;
import com.frostether.frostchain.Json;
import com.frostether.frostchain.Resonance;
import java.util.Map;

/** The desktop app has no tone sensors and never mines: Frostoise stays on phones. */
final class DesktopPlatform implements Api.Platform {

    @Override
    public Resonance.Reading latestReading() {
        return null;
    }

    @Override
    public void miningChanged(boolean mining) {
    }

    @Override
    public String name() {
        return "desktop";
    }

    @Override
    public Map<String, Object> sensors() {
        return Json.o("mag", false, "mic", false, "running", null, "error", "", "magName", "");
    }

    @Override
    public void startSensor(String sensor) {
        throw new IllegalArgumentException("the desktop app doesn't mine: mine with Frostoise on a phone");
    }

    @Override
    public void stopSensor() {
    }

    @Override
    public boolean canMine() {
        return false;
    }
}
