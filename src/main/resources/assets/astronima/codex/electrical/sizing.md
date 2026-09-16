---
title: Sizing a conductor
section: Electrical
order: 20
icon: astronima:iron_rod
---

A wire has three properties and all three matter: what it is made of, how thick it is, and
how far it goes. Resistance is **ρL/A** — the metal, the length, the cross-section — and
everything on this rung follows from it.

![Signal, standard and busbar, to scale. The area goes up a hundredfold; the diameter only tenfold.](astronima:textures/codex/gauges.png 160x90)

## The gauges

- **signal**, 0.5 mm². Control lines. About **2.9 A** in vacuum, which is not enough for a
  machine.
- **standard**, 4 mm². One or two machines. **13.7 A** in vacuum.
- **busbar**, 50 mm². A trunk that feeds a whole workshop.

## Three things that surprise people

**A run is only as good as its worst stretch.** One block of signal wire spliced into a
busbar trunk makes the whole trunk a signal run — for resistance, and for what
[[electrical/protection|its fuse]] trips at.

**A second wire beside the first halves the resistance.** Current divides by conductance, so
each of a doubled-up pair carries half and runs far cooler. An unequal pair does not share
evenly: the short way takes most of it, and the short way is what gets hot.

**A dead-end spur costs nothing.** Current cannot enter it — it would have to come back out
the way it went in.

> Half the voltage at the machine is a **quarter** of the work. A run that is a bit too thin
> is a nuisance; one that is twice too thin is a machine that has stopped.

Put the goggles on {item:astronima:diagnostic_goggles} and the wire tells you all of it before
you build anything: the metal, the length, the resistance, the current, the volts that would
reach the far end and the speed a machine there would run at.

Where a run has to *land* to count as connected — and why a cable passing over a stud
is not wired to it — is [[electrical/terminals]].
