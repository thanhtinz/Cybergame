package com.neolang.cyber;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/** All audio is synthesized to small WAV files under local "synth/" then loaded via Gdx.audio. */
public class SoundSynth {
    private static final int RATE = 22050;
    private final Map<String, Sound> sounds = new HashMap<String, Sound>();
    private Music music;
    private boolean soundOn = true;
    private boolean musicOn = true;

    public SoundSynth() {
        try {
            Gdx.files.local("synth").mkdirs();
            put("click", tone(880, 0.06, 0.5));
            put("coin", seq(new double[][]{{1318, 0.07}, {1760, 0.13}}));
            put("hit", noise(0.12, 0.6));
            put("levelup", seq(new double[][]{{523, 0.09}, {659, 0.09}, {784, 0.09}, {1046, 0.18}}));
            put("plant", seq(new double[][]{{196, 0.12}, {262, 0.16}}));
            put("harvest", seq(new double[][]{{784, 0.08}, {988, 0.08}, {1318, 0.15}}));
            put("error", tone(150, 0.16, 0.5));
            put("storm", seq(new double[][]{{660, 0.12}, {440, 0.12}, {660, 0.12}, {440, 0.2}}));
            put("eat", seq(new double[][]{{392, 0.07}, {523, 0.1}}));
            put("buy", seq(new double[][]{{988, 0.06}, {1318, 0.1}}));
            FileHandle mf = writeWav("music", musicPcm());
            music = Gdx.audio.newMusic(mf);
            music.setLooping(true);
            music.setVolume(0.32f);
        } catch (Exception e) {
            // Audio is optional; the game must run silently if synthesis fails.
        }
    }

    private void put(String name, byte[] pcm) {
        try {
            FileHandle fh = writeWav(name, pcm);
            Sound s = Gdx.audio.newSound(fh);
            if (s != null) sounds.put(name, s);
        } catch (Exception ignored) {
        }
    }

    private FileHandle writeWav(String name, byte[] pcm) {
        byte[] wav = new byte[44 + pcm.length];
        writeStr(wav, 0, "RIFF");
        putInt(wav, 4, 36 + pcm.length);
        writeStr(wav, 8, "WAVE");
        writeStr(wav, 12, "fmt ");
        putInt(wav, 16, 16);
        putShort(wav, 20, (short) 1);      // PCM
        putShort(wav, 22, (short) 1);      // mono
        putInt(wav, 24, RATE);
        putInt(wav, 28, RATE * 2);
        putShort(wav, 32, (short) 2);
        putShort(wav, 34, (short) 16);
        writeStr(wav, 36, "data");
        putInt(wav, 40, pcm.length);
        System.arraycopy(pcm, 0, wav, 44, pcm.length);
        FileHandle fh = Gdx.files.local("synth/" + name + ".wav");
        fh.writeBytes(wav, false);
        return fh;
    }

    private static void writeStr(byte[] b, int off, String s) {
        for (int i = 0; i < s.length(); i++) b[off + i] = (byte) s.charAt(i);
    }

    private static void putInt(byte[] b, int off, int v) {
        b[off] = (byte) (v & 0xff);
        b[off + 1] = (byte) ((v >> 8) & 0xff);
        b[off + 2] = (byte) ((v >> 16) & 0xff);
        b[off + 3] = (byte) ((v >> 24) & 0xff);
    }

    private static void putShort(byte[] b, int off, short v) {
        b[off] = (byte) (v & 0xff);
        b[off + 1] = (byte) ((v >> 8) & 0xff);
    }

    private static byte[] tone(double freq, double durSec, double vol) {
        int n = (int) (RATE * durSec);
        byte[] pcm = new byte[n * 2];
        for (int i = 0; i < n; i++) {
            double t = i / (double) RATE;
            double env = Math.min(1, i / 300.0) * Math.max(0, 1 - i / (double) n * 0.7);
            double v = Math.sin(2 * Math.PI * freq * t) * vol * env;
            putShort(pcm, i * 2, (short) (v * 32767));
        }
        return pcm;
    }

    private static byte[] seq(double[][] notes) {
        int total = 0;
        for (double[] nt : notes) total += (int) (RATE * nt[1]);
        byte[] pcm = new byte[total * 2];
        int pos = 0;
        for (double[] nt : notes) {
            byte[] one = tone(nt[0], nt[1], 0.5);
            System.arraycopy(one, 0, pcm, pos, one.length);
            pos += one.length;
        }
        return pcm;
    }

    private static byte[] noise(double durSec, double vol) {
        int n = (int) (RATE * durSec);
        byte[] pcm = new byte[n * 2];
        Random r = new Random(1234);
        double last = 0;
        for (int i = 0; i < n; i++) {
            double white = r.nextDouble() * 2 - 1;
            last = last * 0.55 + white * 0.45; // soften
            double env = Math.max(0, 1 - i / (double) n);
            putShort(pcm, i * 2, (short) (last * vol * env * 32767));
        }
        return pcm;
    }

    /** 8 second gentle pad loop: Cmaj7 then Am7. */
    private static byte[] musicPcm() {
        int n = RATE * 8;
        byte[] pcm = new byte[n * 2];
        double[][] chords = {
            {261.63, 329.63, 392.00, 493.88},
            {220.00, 261.63, 329.63, 392.00}
        };
        for (int i = 0; i < n; i++) {
            double t = i / (double) RATE;
            int ci = (t < 4) ? 0 : 1;
            double lt = t - ci * 4;
            double env = Math.min(1, lt / 1.2) * Math.min(1, (4 - lt) / 1.2);
            double v = 0;
            for (double f : chords[ci]) {
                v += Math.sin(2 * Math.PI * f * t) + 0.4 * Math.sin(2 * Math.PI * f * 2 * t);
            }
            v = v / chords[ci].length * 0.16 * Math.max(0, env);
            putShort(pcm, i * 2, (short) (v * 32767));
        }
        return pcm;
    }

    public void play(String name) {
        if (!soundOn) return;
        Sound s = sounds.get(name);
        if (s != null) {
            try { s.play(0.7f); } catch (Exception ignored) { }
        }
    }

    public void startMusic() {
        if (music != null && musicOn) {
            try { if (!music.isPlaying()) music.play(); } catch (Exception ignored) { }
        }
    }

    public void stopMusic() {
        if (music != null) {
            try { music.stop(); } catch (Exception ignored) { }
        }
    }

    public void setEnabled(boolean sound, boolean musicFlag) {
        soundOn = sound;
        musicOn = musicFlag;
        if (!musicOn) stopMusic();
        else startMusic();
    }
}
