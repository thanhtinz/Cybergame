package com.avatardk.game.sim;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** Bảng FRAME phải khớp nguyên văn Avatar.cs của client gốc. */
public class AvatarAnimatorTest {
    @Test
    void frameTableMatchesOriginal() {
        assertArrayEquals(new int[]{0, 0, 0, 0, 0, 1, 1, 1, 1, 1}, AvatarAnimator.FRAME[AvatarAnimator.STAND]);
        assertArrayEquals(new int[]{0, 0, 0, 0, 0, 2, 2, 2, 2, 2}, AvatarAnimator.FRAME[AvatarAnimator.RUN]);
        for (int action = 2; action <= 13; action++) {
            for (int v : AvatarAnimator.FRAME[action]) assertEquals(action + 1, v);
        }
        assertArrayEquals(AvatarAnimator.FRAME[0], AvatarAnimator.FRAME[14]);
    }

    @Test
    void frameIndexAlwaysWithinPartFrames() {
        AvatarAnimator a = new AvatarAnimator();
        for (int action = 0; action < AvatarAnimator.FRAME.length; action++) {
            a.action = action;
            for (int i = 0; i < 40; i++) {
                a.update(0.05f);
                int f = a.frameIndex();
                assertTrue(f >= 0 && f <= 14, "frame " + f + " của action " + action + " ngoài 0..14");
            }
        }
    }

    @Test
    void standCycleAlternatesBetweenFrame0And1() {
        AvatarAnimator a = new AvatarAnimator();
        a.action = AvatarAnimator.STAND;
        boolean saw0 = false, saw1 = false;
        for (int i = 0; i < 20; i++) {
            a.update(0.05f);
            if (a.frameIndex() == 0) saw0 = true;
            if (a.frameIndex() == 1) saw1 = true;
        }
        assertTrue(saw0 && saw1);
    }
}
