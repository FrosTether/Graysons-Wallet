package com.frostether.desktop;

import com.frostether.frostchain.Log;
import java.awt.Desktop;
import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Opens the app's window. A Chromium browser (Edge, Chrome, Chromium, Brave) in app mode gives a plain window
 * with no address bar, using its own profile in the data folder, so browser extensions never see the wallet.
 * Without one, the default browser opens the page in a tab.
 */
final class Window {
    private Window() {
    }

    static void open(String url, File dataDir) {
        String profile = new File(dataDir, "window").getAbsolutePath();
        for (String browser : chromiumBrowsers()) {
            List<String> cmd = Arrays.asList(browser, "--app=" + url, "--user-data-dir=" + profile,
                    "--window-size=1180,820", "--no-first-run", "--no-default-browser-check");
            try {
                new ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
                return;
            } catch (Exception e) {
                // try the next one
            }
        }
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
                return;
            }
        } catch (Exception | Error e) {
            // headless or no desktop integration
        }
        String os = System.getProperty("os.name", "").toLowerCase();
        try {
            if (os.contains("win")) {
                new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();
            } else if (os.contains("mac")) {
                new ProcessBuilder("open", url).start();
            } else {
                new ProcessBuilder("xdg-open", url).start();
            }
        } catch (Exception e) {
            Log.w("desktop", "couldn't open a window; open " + url + " in a browser");
        }
    }

    static List<String> chromiumBrowsers() {
        List<String> found = new ArrayList<>();
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            String[] roots = {System.getenv("ProgramFiles(x86)"), System.getenv("ProgramFiles"), System.getenv("LOCALAPPDATA")};
            String[] rel = {
                "Microsoft\\Edge\\Application\\msedge.exe",
                "Google\\Chrome\\Application\\chrome.exe",
                "BraveSoftware\\Brave-Browser\\Application\\brave.exe",
                "Chromium\\Application\\chrome.exe",
            };
            for (String r : rel) {
                for (String root : roots) {
                    if (root != null && new File(root, r).isFile()) {
                        found.add(new File(root, r).getPath());
                        break;
                    }
                }
            }
        } else if (os.contains("mac")) {
            String[] apps = {
                "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
                "/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge",
                "/Applications/Chromium.app/Contents/MacOS/Chromium",
                "/Applications/Brave Browser.app/Contents/MacOS/Brave Browser",
            };
            for (String a : apps) {
                if (new File(a).isFile()) {
                    found.add(a);
                }
            }
        } else {
            String[] names = {"google-chrome", "google-chrome-stable", "chromium", "chromium-browser", "microsoft-edge", "microsoft-edge-stable", "brave-browser"};
            String path = System.getenv("PATH");
            if (path != null) {
                for (String n : names) {
                    for (String d : path.split(File.pathSeparator)) {
                        File f = new File(d, n);
                        if (f.isFile() && f.canExecute()) {
                            found.add(f.getPath());
                            break;
                        }
                    }
                }
            }
        }
        return found;
    }
}
