package com.zmlworks.calc7seg;

import android.content.SharedPreferences;

/** User settings (vibration, key sound). Defaults: both on. Storage is abstracted so it is JVM-testable. */
final class Settings {
    interface Store {
        boolean get(String key, boolean def);
        void put(String key, boolean value);
    }

    static final String VIBRATION = "vibration", SOUND = "sound";
    private final Store store;

    Settings(Store store) { this.store = store; }

    static Settings of(SharedPreferences sp) {
        return new Settings(new Store() {
            @Override public boolean get(String k, boolean d) { return sp.getBoolean(k, d); }
            @Override public void put(String k, boolean v) { sp.edit().putBoolean(k, v).apply(); }
        });
    }

    boolean vibration() { return store.get(VIBRATION, true); }
    boolean sound() { return store.get(SOUND, true); }
    void setVibration(boolean on) { store.put(VIBRATION, on); }
    void setSound(boolean on) { store.put(SOUND, on); }
}
