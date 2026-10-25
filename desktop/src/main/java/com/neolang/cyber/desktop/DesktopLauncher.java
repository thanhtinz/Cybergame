package com.neolang.cyber.desktop;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.neolang.cyber.NeoLangGame;

/** Desktop entry point. Pass --screenshot <dir> to render title/town/adventure frames and exit. */
public class DesktopLauncher {
    public static void main(String[] args) {
        String shotDir = null;
        for (int i = 0; i < args.length - 1; i++) {
            if ("--screenshot".equals(args[i])) shotDir = args[i + 1];
        }
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Neo Làng: Cyber Grove");
        config.setWindowedMode(1280, 720);
        if (shotDir != null) {
            // Screenshot mode: no vsync / fps cap so headless (xvfb) captures run fast.
            config.setForegroundFPS(0);
            config.useVsync(false);
        } else {
            config.setForegroundFPS(60);
            config.useVsync(true);
        }
        new Lwjgl3Application(new NeoLangGame(shotDir), config);
    }
}
