package com.frostether.graysons;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.ValueCallback;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import com.frostether.frostchain.Api;
import com.frostether.frostchain.Json;
import com.frostether.frostchain.Node;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class WebActivity extends Activity {
    static final String BASE = "file:///android_asset/ui/index.html";
    private static final ExecutorService EXEC = Executors.newFixedThreadPool(3);
    private static final int REQ_PERMS = 7;
    private String pendingPerms = "";
    WebView web;

    abstract String mode();

    String hashFor(Intent intent) {
        return mode();
    }

    @Override // android.app.Activity
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        Core.init(this);
        buildWebView();
    }

    public void buildWebView() {
        this.web = new WebView(this);
        this.web.setBackgroundColor(0);
        WebSettings settings = this.web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setGeolocationEnabled(false);
        settings.setSupportZoom(false);
        settings.setTextZoom(100);
        settings.setMediaPlaybackRequiresUserGesture(true);
        this.web.setWebViewClient(new WebViewClient() {
            @Override // android.webkit.WebViewClient
            public boolean shouldOverrideUrlLoading(WebView webView, WebResourceRequest webResourceRequest) {
                Uri url = webResourceRequest.getUrl();
                if (url.toString().startsWith(WebActivity.BASE)) {
                    return false;
                }
                if ("https".equals(url.getScheme()) || "http".equals(url.getScheme())) {
                    try {
                        WebActivity.this.startActivity(new Intent("android.intent.action.VIEW", url));
                    } catch (RuntimeException e) {
                    }
                }
                return true;
            }

            @Override // android.webkit.WebViewClient
            public boolean onRenderProcessGone(WebView webView, RenderProcessGoneDetail renderProcessGoneDetail) {
                if (webView == WebActivity.this.web) {
                    WebActivity.this.web = null;
                    webView.destroy();
                    WebActivity.this.buildWebView();
                    return true;
                }
                webView.destroy();
                return true;
            }
        });
        this.web.addJavascriptInterface(new Bridge(), "FrostBridge");
        setContentView(this.web);
        this.web.loadUrl("file:///android_asset/ui/index.html#" + hashFor(getIntent()));
    }

    @Override // android.app.Activity
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (this.web != null) {
            this.web.evaluateJavascript("location.hash=" + Json.write("#" + hashFor(intent)), null);
        }
    }

    @Override // android.app.Activity
    protected void onResume() {
        super.onResume();
        if (this.web != null) {
            this.web.onResume();
        }
    }

    @Override // android.app.Activity
    protected void onPause() {
        if (this.web != null) {
            this.web.onPause();
        }
        super.onPause();
    }

    @Override // android.app.Activity
    protected void onStart() {
        super.onStart();
        Core.visible(this, true);
    }

    @Override // android.app.Activity
    protected void onStop() {
        super.onStop();
        Core.visible(this, false);
        SensorHub sensorsNow = Core.sensorsNow();
        Node nodeNow = Core.nodeNow();
        if (sensorsNow == null || nodeNow == null || nodeNow.miner.running() || isChangingConfigurations()) {
            return;
        }
        sensorsNow.stopSensor();
    }

    @Override // android.app.Activity
    protected void onDestroy() {
        if (this.web != null) {
            this.web.removeJavascriptInterface("FrostBridge");
            this.web.destroy();
            this.web = null;
        }
        super.onDestroy();
    }

    @Override // android.app.Activity
    public void onBackPressed() {
        if (this.web != null) {
            this.web.evaluateJavascript("window.FrostUI && window.FrostUI.back ? window.FrostUI.back() : false", new ValueCallback<String>() {
                @Override // android.webkit.ValueCallback
                public void onReceiveValue(String str) {
                    if (!"true".equals(str)) {
                        WebActivity.this.finishAfterBack();
                    }
                }
            });
        } else {
            super.onBackPressed();
        }
    }

    public void finishAfterBack() {
        moveTaskToBack(true);
    }

    @Override // android.app.Activity
    public void onRequestPermissionsResult(int i, String[] strArr, int[] iArr) {
        super.onRequestPermissionsResult(i, strArr, iArr);
        if (i == REQ_PERMS) {
            reportPermissions(this.pendingPerms);
        }
    }

    public void reportPermissions(String str) {
        LinkedHashMap linkedHashMap = new LinkedHashMap();
        for (String str2 : str.split(",")) {
            String permissionFor = permissionFor(str2);
            if (permissionFor != null) {
                linkedHashMap.put(str2, Boolean.valueOf(checkSelfPermission(permissionFor) == 0));
            } else if (!str2.isEmpty()) {
                linkedHashMap.put(str2, Boolean.TRUE);
            }
        }
        js("window.__frostPermission && window.__frostPermission(" + Json.write(Json.write(linkedHashMap)) + ")");
    }

    public static String permissionFor(String str) {
        if ("mic".equals(str)) {
            return "android.permission.RECORD_AUDIO";
        }
        if (!"notify".equals(str) || Build.VERSION.SDK_INT < 33) {
            return null;
        }
        return "android.permission.POST_NOTIFICATIONS";
    }

    public void js(final String str) {
        runOnUiThread(new Runnable() {
            @Override // java.lang.Runnable
            public void run() {
                if (WebActivity.this.web != null) {
                    WebActivity.this.web.evaluateJavascript(str, null);
                }
            }
        });
    }

    final class Bridge {
        Bridge() {
        }

        @JavascriptInterface
        public void call(final String str, final String str2, final String str3) {
            WebActivity.EXEC.execute(new Runnable() {
                @Override // java.lang.Runnable
                public void run() {
                    Api api = Core.api();
                    WebActivity.this.js("window.__frostReply(" + Json.write(str) + "," + Json.write(api == null ? Json.write(Json.o("error", "the Frostchain node didn't start: " + Core.error())) : api.call(str2, str3)) + ")");
                }
            });
        }

        @JavascriptInterface
        public void copy(final String str) {
            WebActivity.this.runOnUiThread(new Runnable() {
                @Override // java.lang.Runnable
                public void run() {
                    ClipboardManager clipboardManager = (ClipboardManager) WebActivity.this.getSystemService("clipboard");
                    if (clipboardManager != null) {
                        clipboardManager.setPrimaryClip(ClipData.newPlainText("Frostchain address", str));
                    }
                }
            });
        }

        @JavascriptInterface
        public void share(final String str) {
            WebActivity.this.runOnUiThread(new Runnable() {
                @Override // java.lang.Runnable
                public void run() {
                    try {
                        WebActivity.this.startActivity(Intent.createChooser(new Intent("android.intent.action.SEND").setType("text/plain").putExtra("android.intent.extra.TEXT", str), "Share your address"));
                    } catch (RuntimeException e) {
                    }
                }
            });
        }

        @JavascriptInterface
        public int cores() {
            return Runtime.getRuntime().availableProcessors();
        }

        @JavascriptInterface
        public void keepScreenOn(final boolean z) {
            WebActivity.this.runOnUiThread(new Runnable() {
                @Override // java.lang.Runnable
                public void run() {
                    if (!z) {
                        WebActivity.this.getWindow().clearFlags(128);
                    } else {
                        WebActivity.this.getWindow().addFlags(128);
                    }
                }
            });
        }

        @JavascriptInterface
        public void mode(final String str) {
            WebActivity.this.runOnUiThread(new Runnable() {
                @Override // java.lang.Runnable
                public void run() {
                    WebActivity.this.setTitle("frostoise".equals(str) ? "Frostoise" : "myfrost".equals(str) ? "MyFrost" : "remix".equals(str) ? "Remix" : "Graysons Vault");
                }
            });
        }

        @JavascriptInterface
        public void openApp(final String str) {
            WebActivity.this.runOnUiThread(new Runnable() {
                @Override // java.lang.Runnable
                public void run() {
                    Class cls;
                    if ("frostoise".equals(str)) {
                        cls = FrostoiseActivity.class;
                    } else {
                        cls = "myfrost".equals(str) ? MyFrostActivity.class : WalletActivity.class;
                    }
                    if (cls != WebActivity.this.getClass()) {
                        try {
                            WebActivity.this.startActivity(new Intent(WebActivity.this, cls).addFlags(268435456));
                        } catch (RuntimeException e) {
                        }
                    }
                }
            });
        }

        @JavascriptInterface
        public void openUrl(final String str) {
            WebActivity.this.runOnUiThread(new Runnable() {
                @Override // java.lang.Runnable
                public void run() {
                    Uri parse = Uri.parse(str == null ? "" : str);
                    if ("https".equals(parse.getScheme())) {
                        try {
                            WebActivity.this.startActivity(new Intent("android.intent.action.VIEW", parse).addFlags(268435456));
                        } catch (RuntimeException e) {
                        }
                    }
                }
            });
        }

        @JavascriptInterface
        public void requestPermissions(final String str) {
            WebActivity.this.runOnUiThread(new Runnable() {
                @Override // java.lang.Runnable
                public void run() {
                    ArrayList arrayList = new ArrayList();
                    for (String str2 : str.split(",")) {
                        String permissionFor = WebActivity.permissionFor(str2);
                        if (permissionFor != null && WebActivity.this.checkSelfPermission(permissionFor) != 0) {
                            arrayList.add(permissionFor);
                        }
                    }
                    WebActivity.this.pendingPerms = str;
                    if (arrayList.isEmpty()) {
                        WebActivity.this.reportPermissions(str);
                    } else {
                        WebActivity.this.requestPermissions((String[]) arrayList.toArray(new String[0]), WebActivity.REQ_PERMS);
                    }
                }
            });
        }
    }
}
