package com.avatardk.game;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.avatardk.game.ui.AvatarDkGame;

public class DesktopLauncher {
    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration cfg = new Lwjgl3ApplicationConfiguration();
        cfg.setTitle("Thị Trấn Mặt Trời (Sunny Town)");
        cfg.setWindowedMode(1280, 720);
        cfg.setForegroundFPS(60);
        new Lwjgl3Application(new AvatarDkGame(), cfg);
    }
}
