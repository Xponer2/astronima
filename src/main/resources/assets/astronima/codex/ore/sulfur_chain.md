---
title: The sulfur chain
section: Ore
order: 61
icon: astronima:sulfuric_acid_plant
---

Troilite (FeS) rides along in every chondrite you crush — the sulfur has always had to go
somewhere. These two machines are where it finally does: roasted out of the ore, then turned
into a real industrial acid.

## Roasting spends real oxygen

```
4 FeS + 7 O2 -> 2 Fe2O3 + 4 SO2
```

Feed crushed ore and the roaster reads the batch's own grade to work out how much troilite —
and therefore how much oxygen — it actually needs. Ordinary chondrite carries troilite at 6% by
mass; a metal-rich seam carries more. The oxygen it spends is the same air a room's crew
breathes, so a roaster sharing a sealed room with people, a generator or a fuel cell is a real
draw on one shared supply, not three separate ones.

{item:astronima:hematite_ore} comes out — the same oxide {item:astronima:ilmenite_ore}'s own
reduction already produces, smeltable to iron like any other ore. {item:astronima:sulfuric_acid_plant}
one bench over reads the SO2 this vents.

## The Contact Process, folded into one line

```
2 SO2 + O2 + 2 H2O -> 2 H2SO4
```

The real industrial route to sulfuric acid runs in two stages — catalytic oxidation to SO3, then
absorption into water — folded here into one net, balanced equation the same way the roaster's
own oxidation and decomposition are. Reads **three** room reagents at once: SO2, oxygen, and
water vapor — the exact gas the Sabatier or Bosch reactor already vents as its own byproduct. See
[[ore/regenerative_loop]].

> Build a roaster, a Sabatier or Bosch reactor, and this plant in one sealed room and every
> reagent it needs is already being produced there by something else.

**The catalyst is honest about its limits.** Real Contact Process plants run on platinum — the
original 1831 patent — or, in every modern plant, vanadium(V) oxide. Neither exists as a mineral
in this mod, and inventing an ore vein for one recipe would be a lie dressed up as content. The
roaster's own hematite stands in instead: a real, documented catalytic pathway for SO2 oxidation
in atmospheric and mineral-dust chemistry, but honestly not the industrial-standard choice.

## Sulfuric acid, and where it does not go yet

{item:astronima:sulfuric_acid} — a real, corrosive, bottled product, and nothing in this mod
consumes it yet. Its real uses are ore leaching and lead-acid battery electrolyte; the power cell
does not model battery chemistry yet either, so this stays honest content without an invented
sink, the same standing {item:astronima:carbon_powder} already has.
