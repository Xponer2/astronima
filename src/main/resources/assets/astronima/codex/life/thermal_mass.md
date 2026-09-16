---
title: A block that remembers how hot it got
section: Life support
order: 36
icon: astronima:paraffin_thermal_mass
---

Everything on [[life/heat]] and [[life/heat_pipe]] is about heat *moving* — into a room,
out of it, or from one room to another. This block does neither. It **stores** heat, the
way a battery stores charge: not by getting hotter, but by changing state at a fixed
temperature and changing back later.

## Wax that melts is a battery, not a decoration

Real paraffin wax melts at a fixed point — about 58 °C for the fully-refined grade — and
melting takes real energy that does not show up as a higher temperature while it happens.
A room running hot enough to start melting a block of it holds right at that temperature,
however much heat keeps arriving, until every last bit of wax has turned liquid. Only then
does the room's temperature start climbing again.

Cooling back down works the same way in reverse: the block holds the room at the melt
point while it re-freezes, **giving back** every joule it took in earlier. Nothing is lost —
the block only changes *when* the heat arrives at the room you are standing in.

## Where the wax comes from

Not the cracking tower — that machine converts its whole charge to gas on purpose, and this
does not reopen that. Heat {item:astronima:sludge} gently, at ordinary furnace heat, well
short of what the tower needs to crack anything, and the lighter, waxy fraction of it renders
out as {item:astronima:paraffin_wax} — the same way real oil-shale retorting yields a heavy
wax cut long before the temperature that would crack the rock apart.

{recipe:astronima:paraffin_wax}

Box the wax in hull plate and it is a real, placeable thermal mass:

{recipe:astronima:paraffin_thermal_mass}

## What it is actually good for

A busy workshop with a few machines running warms up — real, slow, and honest, the same
heat balance every sealed room already answers to. A block of this buys real time before
that shows up on the gas analyzer: enough to absorb a work session's own burst of heat, not
enough to hold a room cold forever. **The cheap way to survive a heat burst, before you have
built anything better.**

Need more headroom? Place more blocks. Each one works on its own, and together they act as a
shared reserve — whichever still has capacity left absorbs whatever the last one could not.

## No gauge, no dial

Nothing to switch on and nothing to read on the block itself. The room's own instrument
already shows the result: a room holding dead level at 58 °C while machines keep running
*is* a block of this working. If it is not holding — if the temperature keeps climbing
anyway — every block placed in that room has already fully melted, and it is time to place
another one, or build something that actively cools the room instead.
