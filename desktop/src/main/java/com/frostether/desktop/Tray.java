package com.frostether.desktop;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GraphicsEnvironment;
import java.awt.Graphics2D;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * A tray icon with Open and Quit, where the system has a tray (Windows, most Linux desktops). The node keeps
 * running after the window closes, so this is how to bring the window back or stop it.
 */
final class Tray {
    private Tray() {
    }

    static void install(String url, File dataDir, Runnable quit) {
        try {
            if (GraphicsEnvironment.isHeadless() || !SystemTray.isSupported()) {
                return;
            }
            PopupMenu menu = new PopupMenu();
            MenuItem open = new MenuItem("Open Graysons Vault");
            open.addActionListener(e -> Window.open(url, dataDir));
            MenuItem stop = new MenuItem("Quit (stops the node)");
            stop.addActionListener(e -> quit.run());
            menu.add(open);
            menu.addSeparator();
            menu.add(stop);
            TrayIcon icon = new TrayIcon(icon(), "Graysons Vault: Qoin node running", menu);
            icon.setImageAutoSize(true);
            icon.addActionListener(e -> Window.open(url, dataDir));
            SystemTray.getSystemTray().add(icon);
        } catch (Exception | Error e) {
            // No tray here: the window's More page has Quit.
        }
    }

    /** A nine-pointed star in a ring: one point per solfeggio tone. */
    static BufferedImage icon() {
        int s = 64;
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(10, 12, 24));
        g.fillOval(1, 1, s - 2, s - 2);
        g.setStroke(new BasicStroke(3f));
        g.setColor(new Color(0, 229, 255));
        g.drawOval(4, 4, s - 8, s - 8);
        g.setColor(new Color(255, 64, 214));
        g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        double c = s / 2.0, r = s * 0.36;
        for (int i = 0; i < 9; i++) {
            double a1 = -Math.PI / 2 + i * 2 * Math.PI / 9;
            double a2 = -Math.PI / 2 + ((i + 4) % 9) * 2 * Math.PI / 9;
            g.drawLine((int) (c + r * Math.cos(a1)), (int) (c + r * Math.sin(a1)), (int) (c + r * Math.cos(a2)), (int) (c + r * Math.sin(a2)));
        }
        g.dispose();
        return img;
    }
}
