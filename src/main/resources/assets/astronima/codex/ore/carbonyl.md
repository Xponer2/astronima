---
title: The Mond process
section: Ore
order: 42
icon: astronima:carbonyl_refiner
---

Ludwig Mond found this in 1890 by accident: his gas plant's nickel valves were corroding, and the
culprit turned out to be the carbon monoxide flowing through them. He had discovered a metal that
could be persuaded to **walk out of an ore as a vapour and walk back in as pure metal**, at
temperatures a kitchen oven reaches. Refineries still run it today.

That is why the refiner exists here. Smelting needs a furnace; a furnace needs fuel; fuel needs
oxygen you cannot spare. This needs a warm pipe.

## The two reactions are the same reaction, run backwards

```
Ni + 4 CO  ⇌  Ni(CO)₄
```

Left to right at **50 °C**. Right to left at **180 °C**. Nothing else changes — no reagent is
added, no catalyst is swapped. You are moving one equilibrium up and down a temperature scale, and
the metal falls out of the gas on the way back.

![Left: the band where the gas forms. Right: the band where the metal comes back. Between them the vessel is busy and gives nothing back.](astronima:textures/codex/mond.png 160x96)

## The carbon monoxide is a carrier, not an ingredient

This is the point worth carrying to every other machine in the mod. Four CO go in per nickel atom
and **four CO come back out** when the carbonyl breaks. The gas is a lift, not a fuel. Keep it in
the room and it works forever; vent it and you buy it again.

> Compare the fluidized bed one bench over, where the hydrogen is **consumed** — it takes the
> oxygen and leaves as water. Two machines, two gases, and only one of them is a supply problem.
> See [[ore/fluidization]].

## Why it is a separation and not a melt

Iron does the same trick — `Fe + 5 CO ⇌ Fe(CO)₅` — but at its own temperatures. The two bands do
not overlap. So a vessel held in the nickel band lifts nickel out of an iron-nickel grain and
**leaves the iron sitting there**, untouched, as a solid.

No melt separates two metals that alloy with each other. This does, because it never melts
anything. That is the whole reason meteoric iron-nickel is worth feeding to this machine instead
of a furnace you cannot build.

## The mistake the machine allows

Set the vessel between the two bands — above 80 °C, below 180 °C — and the picture stays busy.
The charge shrinks, the column fills with gas, everything looks like a running machine. **Nothing
comes out.** The carbonyl has been made and there is nowhere for it to give the nickel back.

That is the hatched corridor in the diagram, and the panel says `carbonyl building up - too cool
to give it back` rather than showing you a progress bar that would only report a low number.

## Nickel carbonyl is genuinely dangerous

Real Ni(CO)₄ is one of the more toxic industrial gases known — colourless, faintly sweet, and it
kills over the following days rather than immediately. Mond-process plants are built around never
letting it out.

In here, the same rule applies through the same door as every other gas: what escapes the vessel
is in the room you are standing in. Keep the seal and keep the room read. See [[life/air]].

## What it takes and what it gives

{item:astronima:iron_nickel_grains} in, {item:astronima:pure_nickel} out, and the iron stays
behind for the forge. The grains come from the separator — [[ore/processing]].
