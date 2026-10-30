#!/usr/bin/env python3
"""Synthesizes res/raw/key_snap.wav: a crisp "kasha" (カシャッ) key sound — a short, bright
mechanical snap with a light rattly, shutter-like texture, like a crisp switch actuation.

Each sample: a sharp band-passed noise transient (the snap), 3-4 quickly decaying micro-clicks a few
milliseconds apart (the rattle of the stem/leaf, "sha"), a short metallic ping (~4-5 kHz) and only a
faint low-mid body, so it reads bright rather than thocky. 0.15 ms attack, ~40 ms total.
A single sample is used for every key, so the sound is identical on each press.
44.1 kHz mono 16-bit (needs the high band). Fixed seeds: re-running reproduces the committed files.
"""
import math, os, random, struct, sys, wave

RATE, DUR = 44100, 0.040
outdir = sys.argv[1] if len(sys.argv) > 1 else "app/src/main/res/raw"
os.makedirs(outdir, exist_ok=True)
SNAP = (4600, (2.6, 5.1, 8.9, 13.5), 0.80, 720)  # ping Hz, rattle offsets (ms), brightness (0..1), body Hz


class BandPass:
    """RBJ biquad band-pass (constant 0 dB peak)."""
    def __init__(self, f, q):
        w = 2 * math.pi * f / RATE
        al = math.sin(w) / (2 * q)
        a0 = 1 + al
        self.b = (al / a0, 0.0, -al / a0)
        self.a = (-2 * math.cos(w) / a0, (1 - al) / a0)
        self.x1 = self.x2 = self.y1 = self.y2 = 0.0

    def __call__(self, x):
        y = self.b[0] * x + self.b[2] * self.x2 - self.a[0] * self.y1 - self.a[1] * self.y2
        self.x2, self.x1, self.y2, self.y1 = self.x1, x, self.y1, y
        return y


def synth(k, ping, rattle, bright, body):
    rnd = random.Random(300 + k)
    n = int(RATE * DUR)
    hi = BandPass(5200 + 1800 * bright, 0.9)   # snap band
    mid = BandPass(2600, 1.2)                   # rattle band
    s = []
    for i in range(n):
        t = i / RATE
        x = rnd.uniform(-1, 1)
        h, m = hi(x), mid(x)                                            # each filter stepped once
        v = 1.6 * h * math.exp(-t / 0.0009)                        # "ka": the snap
        r = 0.0
        for j, ms in enumerate(rattle):                                 # "sha": micro-clicks
            tj = t - ms / 1000
            if tj >= 0:
                r += (0.55 * 0.62 ** j) * math.exp(-tj / 0.0007)
        v += 1.4 * m * r + 0.5 * h * r * bright
        v += 0.22 * bright * h * math.exp(-max(0.0, t - 0.003) / 0.006) * min(1.0, t / 0.003)  # airy "sha" tail
        v += 0.30 * math.sin(2 * math.pi * ping * t) * math.exp(-t / 0.0035)
        v += 0.16 * math.sin(2 * math.pi * body * t) * math.exp(-t / 0.004)
        v *= min(1.0, t / 0.00015)
        tail = DUR - 0.008
        if t > tail:
            v *= 0.5 * (1 + math.cos(math.pi * (t - tail) / 0.008))
        s.append(v)
    peak = max(abs(x) for x in s)
    path = os.path.join(outdir, "key_snap.wav")
    with wave.open(path, "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(RATE)
        w.writeframes(b"".join(struct.pack("<h", int(x / peak * 0.9 * 32767)) for x in s))
    print(path, n, "samples")


synth(0, *SNAP)
