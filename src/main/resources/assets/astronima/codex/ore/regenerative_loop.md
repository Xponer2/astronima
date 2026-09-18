---
title: The regenerative loop
section: Ore
order: 60
icon: astronima:sabatier_reactor
---

The ISS Water Recovery System runs on one trick: the CO₂ the crew exhales and the H₂ a water
electrolyzer makes as its own byproduct react into water and methane. The water goes back into
the loop; the methane is the only mass genuinely lost, vented to space because the real station
has no use for fuel gas in vacuum. This mod has somewhere for it to go that is not lost — a
combustion generator already burns methane for power.

```
water bottle --[Electrolyzer, power]--> O2 (breathe) + H2 (room)
CO2 (exhaled, room) + H2 (room) --[Sabatier]--> CH4 (room) + H2O vapor (room)
CH4 --[combustion generator]--> power
H2O vapor --[dehumidifier]--> water bottle, and the loop closes
```

## The electrolyzer needs no catalyst, and that is honest

`2 H2O → 2 H2 + O2`, real electrolysis, power-driven, feeding a water bottle in and venting both
gases into its own sealed room in a real 2:1 ratio. No consumed catalyst item — real electrolyzer
electrodes are durable equipment, not a reagent, and every other machine on this page having one
does not mean this one should invent a use for a part it genuinely does not need.

## Two real routes to close the carbon side

`CO2 + 4 H2 → CH4 + 2 H2O` over nickel, or `CO2 + 2 H2 → C(s) + 2 H2O` over iron — the Sabatier
reaction and the Bosch reaction, and real mission literature treats them as the two genuine
candidates rather than one simply beating the other.

**Sabatier** burns its own methane for power and needs the full 8 mol of hydrogen per 2 mol CO2.
**Bosch** needs only half that hydrogen for the same CO2, because the carbon leaves as a solid
instead of being built into fuel — cheaper on hydrogen, but now the carbon has to be dealt with
rather than simply burned.

> Sabatier's nickel and Bosch's iron are both consumed in a token amount per batch, the same
> named simplification the polymerizer's own titania carries — a real catalyst is not
> stoichiometrically spent, but "installed once, forever" would leave the metal with one fewer
> real use than it has today.

## A real, named tension

A scrubber already removes CO2 from a room and discards it. Run one alongside a Sabatier or Bosch
reactor and they compete for the same molecule — which is exactly what real ISS mission planners
balance between the CDRA and the Sabatier system. Keep the scrubber's demand low, or build the
reactor where the CO2 is thickest.

## What each machine leaves behind

{item:astronima:pure_nickel} feeds Sabatier — the Mond process's own product, see
[[ore/carbonyl]]. {item:astronima:iron_powder} feeds Bosch — the fluidized bed's, see
[[ore/fluidization]]. Bosch's own solid carbon, {item:astronima:carbon_powder}, anneals to real
graphite in a graphitizer with the room's oxygen purged out — the same vessel in an ordinary
breathable room just burns it away instead.
