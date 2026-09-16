---
title: Calibration, and why there's no slider
section: Ore
order: 44
icon: astronima:wrench
---

A jaw crusher has one number that matters: the **closed side setting** — the gap the jaws leave at
the bottom of their stroke. It decides how fine the product is, how much mineral is freed, and how
much work every kilogram costs.

It is not a number you keep changing. You pick it for the ore you are running and then you leave
it alone. That is exactly why there is no slider in a window for it: a control that is correct to
ignore takes up panel space, takes your attention once, and teaches you that panels are decoration.

## The gap does not stay where you put it

Rock grinds the liners away. As they wear, the gap **opens**, the product coarsens, and less
mineral is liberated — quietly, over hours, while the machine goes on looking exactly the same.

Real plants deal with this on a schedule. Somebody drops a lump of lead between the jaws, lets the
machine close on it, fishes out the flattened lump and measures it: that thickness *is* the closed
side setting. Then they re-shim it. It is a real job, and it is a better one than dragging a handle.

> **Wear is charged against work done, not against the clock.** A machine standing idle keeps its
> setting; one that has been grinding all afternoon does not. Log off for the night and nothing
> has changed when you come back.

## What the panel shows

Instead of a slider, one scale carries **two marks**: where you set it, and where it has got to. The distance between them is the whole mechanic, so it is drawn as a distance
rather than printed as a number.

Below the scale the machine says what it thinks:

| It says | It means |
|---|---|
| `set true` | the marks are together; leave it alone |
| `jaws worn open` | past the point where the walk is worth it |
| `calibrating — 40 %` | somebody has a wrench in it right now |

Each machine names its own fault, because *"ram spring gone soft"* tells you what to picture and
`0.14` does not.

## The wrench does both jobs, because they are one job

{item:astronima:wrench} A click moves the setting **one notch and re-shims it true**. Sneak-click
to go the other way. Ten notches cross the range.

Adjusting and calibrating are not separate verbs on a real machine — re-shimming a crusher *is*
setting it. There is no separate "just set it" action, because the real machine does not have one
either.

It costs **time, never material**: the machine stops for three seconds while the shims settle, and
it starts again on its own. So a machine can always be put right, however far it has gone.

Set the gap below, then drag the work up and watch the two marks come apart — the same distance the
panel draws.

{calc:ore/comminution}

## Wrench, or dial

| Machine | Set with | Why |
|---|---|---|
| Ore crusher | wrench | you pick a gap for the ore and leave it |
| Cold forge | wrench | you pick a blow for the billet and leave it |
| Magnetic separator | wrench | you pick a field for the feed and leave it |
| Carbonyl refiner | **dial** | the run *is* the journey from 50 °C to 180 °C |
| Fluidized bed | **dial** | the run *is* trimming spin against the grind |
| Solar retort | dial | the sun keeps moving the right answer for you |

The two machines with a dial still drift — an ageing element runs cold, dragging bearings
run slow — so a second mark appears under the handle showing where the machine really is. There
the wrench **only re-zeroes the instrument** and leaves your setpoint exactly where you put it. A
tool that moved your setpoint every time you corrected it would be a tool that fights you.

## Why the product is stamped with a band and not a number

Crushed ore carries the gap it was ground at. If it carried the *exact* gap, then once the jaws
start drifting every batch would come out a hair different and refuse to stack with the batch
before it — a machine that jams its own output slot after one load.

So the product carries a **size band**, the way a screen deck grades material into fractions.
Nobody stamps a sack with an exact micron. A band is wider than the drift a machine is allowed to
accumulate before it asks for a wrench, so an ordinary run fills a sack.

> Re-setting the jaws **does** change the band. A machine you have just wrenched will stop with
> "no room for product" until you take the old grade out. That is a machine waiting, not a machine
> broken — and it is the same reason you do not mix two fractions in one pile.

The bed downstream cares about this more than you do: see [[ore/fluidization]] for why the spin
window slides with the grind you brought it.

## The servo, and what electricity actually buys

Wire a live line to the **servo** stud on a machine's terminal strip and, while the machine is
drawing power, a positioner holds the setting steady.

- It holds a **band, not a point.** A servo that cancelled drift outright would switch this whole
  page off for anybody past the electrical tier. The error settles a little way out and stops
  growing — comfortably short of the point where the panel asks for a wrench.
- It **does not calibrate.** Power a servo onto a machine that has already gone out of true and
  the error it already had is still there. A servo holds where it finds things; putting a machine
  right is a person with a tool.
- It needs **power**, not a wire. Turn the handle by hand and the machine draws nothing, so its
  servo is dead. This is the reward at the end of [[electrical/sizing]], and it is a reward
  because you will have spent hours re-setting machines by hand first.

Mind where you run the cable: a wire has to **end** on a stud to be landed on it. A run driven
straight through one is passing by, and the goggles will say `crossed, not landed`.

## How often, roughly

| Machine | Service after about | In batches |
|---|---|---|
| Cold forge | 800 work | ~30 hammer blows |
| Ore crusher | 900 work | ~6 loads of ore |
| Magnetic separator | 1300 work | ~10 drums |
| Fluidized bed | 2800 work | ~7 charges |
| Carbonyl refiner | 3200 work | ~8 charges |

Nothing here is a timer asking for a click. The jaws open, the grind coarsens, liberation falls,
and the panel says all three — see [[ore/processing]] for what liberation costs you. Ignore it and
you are not punished by a hidden number; you watch the yield fall and decide whether the walk is
worth it.

Back to [[start/tiers|the ladder]].
