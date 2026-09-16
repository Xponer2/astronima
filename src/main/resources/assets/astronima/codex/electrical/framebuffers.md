---
title: A screen, and how several become one
section: Electrical
order: 24
icon: astronima:video_framebuffer
---

{item:astronima:video_framebuffer} is memory-mapped video: a real chip, not a device you send
drawing commands to. Its sixty-four bits **are** the picture, drawn live on its own face — the
same store [[electrical/logic]]'s own memory chip holds, wired the identical way.

## The same interface as memory

Address on the left, data in across the top, data out on the right, `we` and `clk` along the
bottom — [[electrical/terminals]] names the pads, and there is nothing extra to learn: whatever
drives a memory chip drives a screen. Sixteen cells, four bits each, sixty-four pixels.

**An address picks a half-row.** Cell `2y` is row `y`'s left four pixels; cell `2y + 1` is the same
row's right four. Eight rows, two halves apiece, is the whole map — not a coincidence, the reason
eight-by-eight and sixteen-by-four are the pair of numbers that fit each other.

> A full redraw is sixteen writes, one refresh apart — about four seconds. A partial one (a
> counter's value, one changed row) is a single refresh, a quarter of a second. This is a
> real-hardware trade, not a bug: moving more than four bits in one pulse would need pads a
> twelve-by-twelve housing has no room for.

## Several tiles make one bigger screen

Mount two boards side by side and you have two eight-by-eight tiles making one sixteen-by-eight
picture — a real video wall, tile by tile, the way an actual stadium screen is built. Nothing about
one board's own circuit changes: it is still sixty-four bits, still the same pads, still unaware
that a second board exists next to it.

What ties two tiles into *one* screen is the address bus, not the boards themselves — land the same
`a0`–`a3`, `d0`–`d3` and `clk` on both boards and they see the identical address and the identical
data at every instant. The only line each board needs on its own is `we`: whichever board's `we`
line is actually live is the one that writes.

### A one-line decoder picks the tile

Add one more address line, `sel`, and let it choose which board's `we` is live:

| `sel` | Board 0 writes | Board 1 writes |
|---|---|---|
| low | when the shared `we` is live | never |
| high | never | when the shared `we` is live |

Two gates build exactly that, out of parts you already have:

- {item:astronima:gate_not} inverts `sel`.
- {item:astronima:gate_and} feeds the shared `we` and the inverted `sel` into board 0's own `we`.
- A second {item:astronima:gate_and} feeds the shared `we` and `sel` itself into board 1's own `we`.

Now one address bus reaches sixteen cells on *either* board — five real address bits total (`a0`–
`a3` plus `sel`) addressing thirty-two cells across two tiles, the same doubling a real chip gets
from one more address pin. A third tile is the same trick again: two `sel` lines and four decoding
gates instead of one and two.

> Wire it once, mount the boards, and the picture behind them reads as one screen — because from
> the address bus's own point of view, it is.
