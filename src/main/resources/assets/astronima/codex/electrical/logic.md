---
title: Logic in the wire layer
section: Electrical
order: 22
icon: astronima:gate_and
---

Logic here is not a block. A gate is a **surface-mount part** that lies flat in the same layer
as the wire, five pixels square, and a trace reaches its pad by ordinary adjacency. That means
you can bolt two gates to one wall a few pixels apart, wire them to each other, and then plaster
the whole circuit over — the wall does not break it.

> A run has to land **on the pad itself**. A gate can be wired wrong, and you can see that you
> did.

## The pieces

- **Six gates** — AND, OR, XOR, NAND, NOR, NOT. Blue pads down one edge listen, an amber pad on
  the other drives. The inverting three carry the bubble at their output, which is the real
  schematic notation and the only thing that tells a NAND from an AND at five pixels across.
- **A switch** {item:astronima:signal_switch} is a state you set. **A button**
  {item:astronima:signal_button} is an event — and an event is what you test a circuit with:
  press it, watch the charge leave, follow it through your gates, see where it stops.
- **A plate** {item:astronima:circuit_plate} is a whole schematic in one component, eight pixels
  square, with four inputs and four outputs. Right-click it in the air to open the board editor.
- **Sensors** stay blocks, because they measure a *room* and a room is block-sized.

## Two things worth knowing before you build

**Parallel contacts are already an OR.** Two sources on one line energise it, and that needs no
part at all. Everything else a circuit wants is a component, exactly as it is in a real one.

**A plate cannot contain a loop**, so its answer is always defined — and so it cannot remember
anything. Latches need a loop, a loop needs timing, and timing is its own machine.

## Building your first interlock

Build it out of **separate gates and wire**, not a plate. That is how you learn what a gate
does. The plate is what you reach for afterwards: having worked a piece of logic out once, stop
rebuilding it.

Wire runs to parts are ordinary wire, so everything in [[electrical/sizing]] applies — including
that the run has to reach the pad and not merely pass near it.
