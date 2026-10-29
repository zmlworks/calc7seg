package com.zmlworks.calc7seg;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;

/** Low-latency key click (res/raw/key_click.wav, synthesized by tools/sound/make_click.py). */
final class KeyClick {
    private static final float VOLUME = 0.32f;
    private final SoundPool pool;
    private final int id;
    private volatile boolean loaded;

    KeyClick(Context c) {
        pool = new SoundPool.Builder().setMaxStreams(4).setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
        pool.setOnLoadCompleteListener((p, sid, status) -> loaded = status == 0);
        id = pool.load(c, R.raw.key_click, 1);
    }

    void play() {
        if (loaded) pool.play(id, VOLUME, VOLUME, 1, 0, 1f);
    }

    void release() { pool.release(); }
}
