---
title: Three separations
section: Ore
order: 40
icon: astronima:ore_crusher
---

Rock does not become metal in one step, and it does not become metal by being heated. It becomes
metal by being **broken up** and then **sorted**, twice, and each step has a control with a cost.

## 1. Crushing — liberation against work

The jaws have a gap. Narrow it and you free more mineral from the rock around it; narrow it and
each batch costs more cranking.

![Work per kilogram along the bottom, mineral freed up the side. One curve: what another turn of the jaws buys against what it costs. The marked point is where the product first reaches the size of the grains it is made of.](astronima:textures/codex/grind.png 160x104)

The curve climbs steeply and then **bends over**, and the bend is the whole decision.

Liberation flattens because it has to: once a particle is smaller than the mineral grain inside it
there is nothing left to free. Work does not flatten — it goes as the reciprocal square root of
particle size, which is **Bond's law**, so every further step down costs more than the last one.

> Finer is not better. It is *more freed and more work*. Past the marked point you are spending
> real effort to liberate mineral that is already liberated — and the marked point is nowhere near
> the end of the dial.

Two runs at two settings is a real strategy: coarse for bulk, fine for the fraction worth the
effort. The right answer also depends on the ore in front of you, which is why this page plots a
curve instead of naming a number.

Drag the gap below and watch liberation and work move together — same curve, your own numbers.

{calc:ore/comminution}

## 2. Separating — grade against recovery

A magnet takes the iron-nickel and leaves the tailings. Turn it up and you catch more of the
metal *and* more of the rock with it; turn it down and what you catch is cleaner and there is
less of it.

Drag the field below and watch recovery and grade move in opposite directions — the trade in
real numbers, off whatever your crusher last handed the drum.

{calc:ore/separation}

The **winnowing table** does the same job with a gas stream instead of a magnet, which means it
needs a pressurised room and it separates by density rather than by magnetism.

### Which one, and why it is not a preference

Two machines, and the choice between them is not taste — it is a question about the rock, and the
rock has already answered it. Only two numbers decide: how magnetic a mineral is, and how dense.

![Density along the bottom, magnetic susceptibility up the side, on a log scale. Green: any field takes it. Amber: only a field wound right up. Blue, right of the line: no magnet will, but a gas stream sorts it by weight. Grey, bottom left: tailings. The ringed mark is pentlandite.](astronima:textures/codex/separations.png 160x112)

- **Kamacite** — native iron-nickel, susceptibility 1.0. Any field takes it. This is the metal you
  live on for the first hours, and it is metal *already*: no smelting at any stage.
- **Magnetite** — 0.55. Caught, but only with the field wound up, which drags rock in with it. The
  iron in it is bonded to oxygen anyway, so catching it buys you a job for later.
- **Pentlandite** — **0.005**, and it is where the nickel is. A magnet does essentially nothing to
  it however finely you grind. But it is 4.8 g/cm³ against serpentine's 2.6, so a gas stream can
  concentrate it — and that is the entire argument for the winnowing table.
- **Troilite** — 4.7 g/cm³, barely magnetic. Comes across with the pentlandite; the sulfur has to
  go somewhere later.
- **Serpentine, olivine, carbonate, organics** — light and non-magnetic. Neither machine wants
  them, which is what makes them tailings — and the tailings are where the water lives.

> The magnet's cut point **moves**. Capture goes as χ·B², so winding the field up lowers the
> susceptibility a grain needs to be taken. That is the same recovery-against-grade trade in a
> different coat: a stronger field reaches further down the list, and everything it reaches
> further down is dirtier.

The rule the whole picture states in one line: **a magnet sorts by what a mineral is, a gas stream
sorts by what it weighs.** When the metal you want is locked in a sulfide, no amount of field
strength is the answer and no amount of grinding is either — you need the chemistry in
[[ore/carbonyl]].

Both machines go out of true as you use them; see [[ore/calibration]].

## 3. Forging — cold, and it work-hardens

The **cold forge** presses grains into a tool head. No furnace, because there is no fuel to spare
and no air to burn it in. Working metal cold makes it harder and more brittle, which is real and
is why a meteoric tool blunts rather than shatters.

## What comes out

Tailings are not waste. They are hydrated silicate, and the retort bakes water out of them —
see [[ore/isru]].
