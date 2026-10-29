#!/usr/bin/env python3
"""Synthesizes res/raw/key_click.wav: a short, crisp mechanical-keyboard style click.

Layers: a high-passed noise transient (the "tick" of the switch), a damped ~3.4 kHz ping
(plastic resonance) and a low damped "thock" (key bottoming out). 44.1 kHz mono 16-bit, ~45 ms.
Deterministic (fixed seed), so re-running reproduces the committed file.
"""
import math, random, struct, sys, wave

RATE, DUR = 44100, 0.045
out = sys.argv[1] if len(sys.argv) > 1 else "app/src/main/res/raw/key_click.wav"
rnd = random.Random(7)
n = int(RATE * DUR)
s, prev = [], 0.0
for i in range(n):
    t = i / RATE
    noise = rnd.uniform(-1, 1)
    hp = noise - prev * 0.6          # crude high-pass -> brighter tick
    prev = noise
    v = 0.75 * hp * math.exp(-t / 0.0011)
    v += 0.45 * math.sin(2 * math.pi * 3400 * t) * math.exp(-t / 0.0035)
    v += 0.22 * math.sin(2 * math.pi * 1250 * t) * math.exp(-t / 0.007)
    v += 0.30 * math.sin(2 * math.pi * 210 * t) * math.exp(-t / 0.011)
    # second, softer tick ~9 ms later (key release of the switch leaf)
    t2 = t - 0.009
    if t2 > 0:
        v += 0.18 * rnd.uniform(-1, 1) * math.exp(-t2 / 0.0008)
    v *= min(1.0, t / 0.0002)        # 0.2 ms attack, avoids a DC pop
    v *= 1.0 - max(0.0, (t - (DUR - 0.005)) / 0.005)  # fade out last 5 ms
    s.append(v)
peak = max(abs(x) for x in s)
with wave.open(out, "wb") as w:
    w.setnchannels(1); w.setsampwidth(2); w.setframerate(RATE)
    w.writeframes(b"".join(struct.pack("<h", int(x / peak * 0.9 * 32767)) for x in s))
print(out, n, "samples")
