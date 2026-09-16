---
title: Printing a part
section: Ore
order: 45
icon: astronima:sls_printer
---

Every machine on this rung until now controls **one number** and the good result sits somewhere
on a line: too cold, done, spoiled. The printer is different, and the difference is real.

## Why a plane, not a line

A laser scans a bed of iron powder and fuses it, track by track. What fuses it is the
**volumetric energy density** the laser delivers,

```
VED = power / (speed x hatch x layer)
```

and the same energy reached two ways does **not** print the same part. High power run fast and
low power run slow can land on the identical number — and one keyholes while the other balls.
So the control is genuinely two axes, laser power and scan speed together, and the good result
is a **pocket in the plane** rather than a point on a line.

> Too little energy — low power or high speed — leaves the powder half-melted: **lack of
> fusion**, a part shot through with voids. Too much — high power or low speed — vaporises the
> melt pool into **keyholing**. Scanned too fast at *any* energy, the molten track beads up
> before it can fuse to its neighbours: **balling**, on the speed axis alone, independent of the
> energy entirely.

## Why it still prints outside the pocket

There is no failure state that refuses to work. A part printed outside the sound pocket still
comes out — porous, or beaded, whichever corner you landed in — and its soundness travels with
it onto the item itself. It fails when you *use* it, not when you print it, the same honesty
[[ore/isru]]'s other machines already keep about a batch that ran wrong.

## Where the feed comes from

Iron powder off the fluidized bed, the same stock a furnace already turns into an ingot — this
is its other route, into a shaped part instead of a lump. See [[ore/fluidization]].
