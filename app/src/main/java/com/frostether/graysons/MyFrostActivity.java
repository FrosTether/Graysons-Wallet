package com.frostether.graysons;

import android.content.Intent;
import android.net.Uri;

public final class MyFrostActivity extends WebActivity {
    @Override // com.frostether.graysons.WebActivity
    String mode() {
        return "myfrost";
    }

    @Override // com.frostether.graysons.WebActivity
    String hashFor(Intent intent) {
        Uri data = intent == null ? null : intent.getData();
        if (data != null && "frostchain".equalsIgnoreCase(data.getScheme())) {
            String uri = data.toString();
            if (uri.length() <= 512) {
                return mode() + "?pay=" + Uri.encode(uri);
            }
        }
        return mode();
    }
}
