#!/usr/bin/env python3
"""Synthesizes res/raw/key_thock_{0,1,2}.wav: soft, deep "thock" key sounds like a premium,
lubed mechanical keyboard (no sharp click).

Each sample: a low-mid body thump (pitch-swept damped sines ~150-420 Hz, as the keycap and case
resonate when the switch bottoms out), low-passed noise for the felt "puff", and a quiet, rounded
upper-mid tap (~1.6 kHz, heavily damped) for definition. Soft ~1.5 ms attack, short ~35 ms tail.
Three variants with slightly different pitch/decay/balance are rotated at runtime so fast typing
does not sound machine-gun identical. 22.05 kHz mono 16-bit (the content is mostly low-mid), ~55 ms.
Deterministic (fixed seeds): re-running reproduces the committed files.
"""
import math, os, random, struct, sys, wave

RATE, DUR = 22050, 0.055
outdir = sys.argv[1] if len(sys.argv) > 1 else "app/src/main/res/raw"
os.makedirs(outdir, exist_ok=True)
VARIANTS = [  # body Hz, second body Hz, body decay s, tap Hz, tap gain, noise gain
    (205, 410, 0.012, 1600, 0.10, 0.22),
    (188, 372, 0.013, 1450, 0.08, 0.20),
    (222, 445, 0.011, 1750, 0.11, 0.24),
]


def synth(k, f1, f2, decay, ftap, gtap, gnoise):
    rnd = random.Random(100 + k)
    n = int(RATE * DUR)
    s, lp1, lp2 = [], 0.0, 0.0
    ph1 = ph2 = 0.0
    for i in range(n):
        t = i / RATE
        sweep = 1.0 + 0.35 * math.exp(-t / 0.004)      # pitch drops quickly: "thock", not "tonk"
        ph1 += 2 * math.pi * f1 * sweep / RATE
        ph2 += 2 * math.pi * f2 * sweep / RATE
        v = 0.85 * math.sin(ph1) * math.exp(-t / decay)
        v += 0.35 * math.sin(ph2) * math.exp(-t / (decay * 0.6))
        # two-pole low-passed noise: soft felt "puff" of the bottom-out
        x = rnd.uniform(-1, 1)
        lp1 += 0.18 * (x - lp1)
        lp2 += 0.18 * (lp1 - lp2)
        v += gnoise * 3.0 * lp2 * math.exp(-t / 0.006)
        v += gtap * math.sin(2 * math.pi * ftap * t) * math.exp(-t / 0.0022)
        v *= 1 - math.exp(-t / 0.0015)                  # ~1.5 ms soft attack, rounds the edge
        tail = DUR - 0.012
        if t > tail:
            v *= 0.5 * (1 + math.cos(math.pi * (t - tail) / 0.012))   # smooth fade, no pop
        s.append(v)
    peak = max(abs(x) for x in s)
    path = os.path.join(outdir, "key_thock_%d.wav" % k)
    with wave.open(path, "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(RATE)
        w.writeframes(b"".join(struct.pack("<h", int(x / peak * 0.92 * 32767)) for x in s))
    print(path, n, "samples")


for k, v in enumerate(VARIANTS):
    synth(k, *v)
