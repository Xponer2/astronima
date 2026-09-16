---
title: Petrochemicals
section: Ore
order: 58
icon: astronima:cracking_tower
---

Tholins and sludge have been waste since the first tier — the tar the atmosphere leaves behind
and the soot a starved burner coughs up. Both are long hydrocarbon chains, and a long chain has
exactly one thing worth doing to it: breaking it into shorter, more useful ones.

## The tower cracks, it does not burn

Real thermal cracking splits a long hydrocarbon into lighter fragments at high heat, no oxygen
required — refineries have run it for a century to turn crude into the light gases plastics are
actually made from. The tower does the same: a charge of tholins or sludge converts entirely to
gas, mostly **ethylene**, vented straight into the sealed room it stands in. No ash, no residue —
real cracking does leave solid coke behind, and this is a named simplification rather than a
claim that nothing is lost.

## Ethylene is the monomer, not the product

*n* C₂H₄ → (C₂H₄)ₙ — polyethylene is what you get when ethylene molecules link end to end, and
that reaction needs a catalyst it does not consume in bulk. Real polyethylene plants run on
Ziegler–Natta catalysts, titanium-based, which is not a coincidence dressed up after the fact:
{item:astronima:titania} already exists in this mod as the electrolysis cell's own leftover, and
using it here is the real reason those plants exist at all. A small amount is spent per batch —
a named simplification, the same one every catalyst in this mod's chemistry carries, because a
part loaded once and never again would leave titania with one fewer real use than it has today.

> Build a cracking tower and a polymerizer in the same sealed room and the ethylene never has to
> touch an inventory at all — the tower vents it, the polymerizer reads it straight out of the
> air.

## One stock, four real uses

Polyethylene, mylar and kapton tape are chemically distinct polymer families in reality — this
mod gives all three the same precursor rather than a machine step each, an ordering choice named
here rather than hidden. Shaped at a bench, not brewed a second time:

{recipe:astronima:mylar}

{recipe:astronima:kapton_tape}

- **Mylar** — a second thermal-layer repair, alongside insulation weave.
- **Kapton tape** — a second helmet-seal repair, alongside the sealant patch.
- **Acoustic foam** — a real interior wall panel. The dampening it is named for has no physics
  yet; the block itself is genuine content today.
- **Sleeping bag** — the mod's first placed respawn point.

## Cold makes plastic brittle, and this one remembers

Real polymers embrittle below their glass transition — the mod's own heat rule already tracked
temperature, and polyethylene's products are the first thing it bites. A kapton or mylar repair
applied below **−20 °C** starts with less life in it than the same patch applied warm. An
ordinary metal part is untouched; only a polymer one reads the room it was fixed in. See
[[life/heat]].
