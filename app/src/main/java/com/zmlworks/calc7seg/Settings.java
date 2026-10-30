package com.zmlworks.calc7seg;

import android.content.SharedPreferences;

/** User settings (vibration, key sound, main display look). Defaults: both on. Storage is abstracted so it is JVM-testable. */
final class Settings {
    interface Store {
        boolean get(String key, boolean def);
        void put(String key, boolean value);
    }

    static final String VIBRATION = "vibration", SOUND = "sound", RAISED_LCD = "raised_lcd";
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
    /** Main display look: false = flat (default, 0.8.0), true = raised out of its recess. */
    boolean raisedLcd() { return store.get(RAISED_LCD, false); }
    void setRaisedLcd(boolean on) { store.put(RAISED_LCD, on); }
}
