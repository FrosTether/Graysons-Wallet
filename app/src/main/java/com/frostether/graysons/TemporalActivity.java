package com.frostether.graysons;

/** Temporal, the time machine: seal capsules for the future and go back to any block. Its own launcher icon and task. */
public final class TemporalActivity extends WebActivity {
    @Override // com.frostether.graysons.WebActivity
    String mode() {
        return "temporal";
    }
}
