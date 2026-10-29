package com.zmlworks.calc7seg;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;

/**
 * Low-latency key sound: three crisp "kasha" snap variants (res/raw/key_snap_*.wav, synthesized by
 * tools/sound/make_click.py) rotated in a shuffled order with tiny pitch variation, so repeated
 * presses sound like real typing rather than one identical sample.
 */
final class KeyClick {
    private static final float VOLUME = 0.45f;
    private static final int[] RES = {R.raw.key_snap_0, R.raw.key_snap_1, R.raw.key_snap_2};
    private final SoundPool pool;
    private final int[] ids = new int[RES.length];
    private final java.util.Random rnd = new java.util.Random();
    private volatile int loaded;
    private int last = -1;

    KeyClick(Context c) {
        pool = new SoundPool.Builder().setMaxStreams(4).setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
        pool.setOnLoadCompleteListener((p, sid, status) -> { if (status == 0) loaded++; });
        for (int i = 0; i < RES.length; i++) ids[i] = pool.load(c, RES[i], 1);
    }

    void play() {
        if (loaded < RES.length) return;
        int k = rnd.nextInt(RES.length - 1);
        if (last >= 0 && k >= last) k++;            // never the same variant twice in a row
        if (last < 0) k = rnd.nextInt(RES.length);
        last = k;
        float rate = 0.97f + rnd.nextFloat() * 0.06f;  // ±3 % pitch
        pool.play(ids[k], VOLUME, VOLUME, 1, 0, rate);
    }

    void release() { pool.release(); }
}
