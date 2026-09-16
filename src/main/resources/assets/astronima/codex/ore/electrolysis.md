---
title: Molten-oxide electrolysis
section: Ore
order: 46
icon: astronima:electrolysis_cell
---

Tailings are the gangue an earlier tier already gave up on — the ore-processing set that
recovers metal by melting and rock, decomposition and gas has one machine left, and it works on
what all the others discarded. Real ISS-era research (Sirona Technologies, MSFC) melts regolith
and runs current through it: the oxide itself splits, oxygen bubbles off the anode, and molten
metal collects at the cathode. No furnace, no reducing gas — the current does the whole job.

## The electrode chooses the metal, not a dial

```
FeO  -> Fe  + 1/2 O2     no electrode — the cell's default target
SiO2 -> Si  + O2          needs {item:astronima:silicon_electrode}
Al2O3 -> 2 Al + 3/2 O2    needs {item:astronima:aluminum_electrode}
```

Real decomposition voltage ranks in exactly this order — iron lowest, silicon roughly double,
aluminium harder again — which is why every published study uses iron oxide as its reference
case and why this cell reaches it with nothing installed at all. Earlier drafts of this machine
let more power reach a *harder* target; that broke the mod's own rule that power buys rate, never
a different reaction, so an electrode is what actually retunes the cell now — a crafted, swappable
component, never a function of how much power happens to be arriving.

> {item:astronima:silicon_electrode} and {item:astronima:aluminum_electrode} are equipment, not a
> reagent: pull one back out and the cell reaches iron again, at will.

## One target at a time, not a mixture

Feed {item:astronima:tailings} and the cell reads how much of *the installed target's own oxide*
that batch actually carries — not the whole rock. Iron dominates a typical chondrite's
electrolytic mass; silicon and aluminium are minor shares of the same pool, matching real
chondritic abundances loosely rather than exactly. Everything the target's own electrode cannot
reach is fixed as residue the moment the charge loads, and no amount of running the cell longer
converts it — swap the electrode and load a fresh batch instead.

## What comes back

The metal collects as a real item, pure straight from the melt; the oxygen it releases vents
straight into the sealed room the cell stands in, real and breathable, the same shape the solar
retort's own water vapor already established. {item:astronima:silicon} and
{item:astronima:aluminum} have no recipe that consumes them yet — {item:astronima:aluminum} is
the rarest and hardest target this cell reaches at all, kept honestly rather than inventing a use
no phase has named yet.
