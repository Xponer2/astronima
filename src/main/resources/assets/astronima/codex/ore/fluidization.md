---
title: Fluidization, and why it spins
section: Ore
order: 43
icon: astronima:fluidized_bed
---

Blow gas up through a bed of powder slowly and nothing happens. Blow it faster and at one exact
speed the bed **stops being a solid**: it swells, bubbles, and starts behaving like a boiling
liquid — you could float a duck on it. Faster still and the powder is simply carried away.

Between those two speeds is where nearly all industrial gas–solid chemistry is done, because a
fluidized bed gives every grain contact with the gas at once instead of only the ones on top.

## The problem: there is no weight here to balance

A fluidized bed is a balance. Gas drag lifts the powder; **weight** pulls it back. The bed hangs
between the two.

On an asteroid at roughly 1/70 g there is no weight worth balancing. The same machine that works
on Earth becomes a dust cloud the first draught carries off. So this one makes its own gravity:

```
a = ω²r
```

Spin the drum and the powder is flung against its perforated wall exactly as weight would hold it
to a floor. Then blow the hydrogen **inward** through that wall. The dial is not gas flow — the
flow is fixed by the chemistry — the dial is *how much gravity you want*.

## Two failures bracket one band

| Spin | Condition | What happens |
|---|---|---|
| Too slow | `U_gas ≥ U_terminal` | the flow beats the bed and carries the charge out of the exhaust |
| Right | `U_mf ≤ U_gas < U_terminal` | boiling: vigorous contact, water swept out, iron reduced |
| Too fast | `U_gas < U_mf` | pinned to the wall; the gas channels through and reduction stalls |

`U_mf` is minimum fluidization (Wen & Yu) and `U_terminal` is the entrainment velocity (Haider &
Levenspiel). Both are real correlations and both are computed here rather than faked.

## The window moves, and that is the whole machine

Both of those velocities scale with grain size. So the usable band of drum speeds **slides with
how finely you ground the ore**:

![Grain size along the bottom, drum speed up the side. Green is the band that boils. The line marks the 120 µm grain the crusher liberates at.](astronima:textures/codex/window.png 160x108)

Read the picture and the lesson is unavoidable: a fine dust must be spun hard to be pinned, and a
coarse grind packs solid at the speed the dust needed.

> There is no better grind here. Unlike the crusher — where finer frees more mineral and costs
> more work — this machine only asks for a **match** between the grind you brought and the speed
> you set. Change the feed and the target moves out from under your dial.

## The chemistry it is all for

```
FeTiO₃ + H₂  →  Fe + TiO₂ + H₂O
```

Ilmenite plus hydrogen gives iron, titania and water, at about 1000 °C. It is the standard
proposal for making metal out of lunar and asteroid regolith, and it has two consequences you
will feel:

- **The hydrogen is consumed.** Unlike the refiner's carbon monoxide, this gas leaves as water and
  does not come back. You must keep supplying it. See [[ore/carbonyl]] for the contrast.
- **The water must be swept away.** The reaction stalls at equilibrium if its own product sits in
  the bed, which is half of why a boiling bed reduces and a packed one does not — the fixed gas
  velocity is set as much by removal as by chemistry.

## What it takes and what it gives

{item:astronima:crushed_ilmenite} in, and both {item:astronima:iron_powder} and
{item:astronima:titania} out. The titania is not a by-product to throw away; it is the white
pigment and the feedstock for everything above this rung.

Grind it first: [[ore/processing]].
