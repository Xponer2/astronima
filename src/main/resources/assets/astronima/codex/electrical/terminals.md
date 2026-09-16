---
title: Terminals, and what "connected" means
section: Electrical
order: 23
icon: astronima:wire_coil
---

A machine is not wired by having a wire near it. Every block that belongs on a circuit publishes
its **terminals**: what each one is for and exactly where it is, to the pixel. Hold the coil and
they light up on every face, so wiring a machine is aiming at a stud you can see rather than waving
a coil at a wall.

## The strip

Machines carry the same strip on **all four sides**, so a machine in a corner is still wirable, and
always in the same order — so it reads the same on every machine in the game.

| Stud | Kind | What it does |
|---|---|---|
| `enable` | listens | a live line lets it run; see the asymmetry below |
| `power` | both ways | watts in, and a DC bus really is bidirectional |
| `working` | drives | closed while the machine is turning |
| `stuck` | drives | closed while it is stopped for a reason a person has to fix |
| `servo` | listens | holds the machine's calibration steady — [[ore/calibration]] |

Two outputs rather than one, because *busy* and *stuck* want opposite responses: one is patience,
the other is a person.

> **A bare `enable` stud means the machine runs.** Leave it unwired and nothing has changed. Land a
> wire on it and you have taken charge: from then on it runs only while that line is live. The
> asymmetry is deliberate — a feature that stopped every machine in the world the day it shipped
> would be a feature nobody opted into.

## Landed is *ending*, not touching

![A machine face, sixteen pixels square. The grey run comes in along the strip's own row and stops on the power stud — crossing the enable stud on the way, struck through in red, because crossing is not connecting. The green run comes up from below and stops on the servo stud, ringed: that one is landed.](astronima:textures/codex/strip.png 160x132)

A terminal is one pixel. A run is wired to it when the run **ends** there — at most one neighbour —
and not when it merely passes over on its way somewhere else. That is how a screw terminal works: a
cable running past one is not connected to it.

This matters because the strip is a **row**. A power cable coming in along that row crosses the
enable stud on its way to the power stud. Under "touching counts", every powered machine in the
game read its own power cable as somebody having wired up its enable — and sat waiting for a signal
nobody was sending.

**Tapping a bus still works, and works the way real wiring does:** the drop ends at the stud while
the bus carries on past it. Only a run driven straight *through* a terminal fails to connect.

> Put the goggles on {item:astronima:diagnostic_goggles} and each stud says which it is: `no wire`,
> `crossed, not landed`, or `wired`. If a machine is ignoring a line you are sure you ran, that is
> the first thing to look at.

## Pads are not studs

A surface-mount part's **pad** is a contact patch on the face the part is lying on, so a run
crossing a pad is genuinely lying on it and is connected. A stud is a screw. The two rules are
different because the two things are different — see [[electrical/logic]] for the parts.

## Colour is a circuit

Two runs may share a pixel; a stud can have a power cable crossing it and a control line landed on
it at the same time. What keeps them apart is **colour**: each colour is its own circuit, and a
signal only ever travels along its own.

Everything about the conductor itself — gauge, material, what a long run costs you — is in
[[electrical/sizing]].
