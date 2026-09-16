---
title: Moving heat on purpose
section: Life support
order: 35
icon: astronima:ammonia_heat_pipe
---

Everything on [[life/heat]] is about a room losing heat it never wanted to lose. An ammonia
heat pipe is the other half: a way to move heat somewhere you *do* want it, deliberately,
between two rooms of your own choosing.

## A sealed conductor, not a machine

There is no dial, no fuel slot, and nothing to switch on. A heat pipe is charged once, at
crafting, with a canister of ammonia — sealed inside forever after, the same working fluid a
real spacecraft heat pipe carries. It does not care which end is hotter. Build it between any
two rooms and it moves real heat from whichever side is warmer to whichever is colder, and
stops on its own as the two converge — never a source of heat, only a much faster path for
heat that was already going to move eventually.

> A hull plate lets heat cross slowly. An ammonia heat pipe lets it cross **one to two orders
> of magnitude faster** for the same area — a real, published figure for an ammonia loop heat
> pipe, not a made-up multiplier.

## Filling the canister

Ammonia turns up as a rare, otherwise-useless gas pocket while mining. Vent one into a sealed
room and an empty canister right-clicked against that room's own air fills from it, exactly
the way an oxygen tank fills from breathable air. The filled canister is the crafting
ingredient — one canister, one pipe, charged for good. Nothing about the pipe's later
operation draws on any room's gas at all.

## Where it earns its place

Bury a habitat and insulate it (see [[life/heat]]) and passive loss drops to a trickle almost
everywhere — which is exactly what makes a *deliberate* heat path between two particular rooms
worth building: a workshop running machines all day and a food store that needs to stay cold
next door to it, a greenhouse that wants a machine bay's waste heat instead of losing it to
rock. The pipe only moves what a real conductance times a real temperature difference says it
should — a thicker gap moves more, a smaller one moves less, and two rooms already at the same
temperature move nothing, because there is nothing to move.

## What it will not do

It will not warm a room faster than the room on the other end can afford to cool, and it will
not do anything at all between one room and open vacuum — a heat pipe needs a real sealed room
at both ends, the same requirement every other piece of this mod's atmosphere places on itself.
