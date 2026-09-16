---
title: Every light has a fingerprint
section: Spectroscopy
order: 70
icon: astronima:spectrograph
---

Heat a thing enough and it glows. That much you already know — the forge tells you a billet's
temperature by its colour. A spectrograph does the same trick with far more precision: it spreads
light out by wavelength and shows you not just *how hot*, but **what it is made of**, without
touching it, breaking it open, or using it up.

## The glow tells you the temperature

Every hot source radiates a spread of wavelengths that peaks at one point, and that peak moves
with temperature alone — hotter sources peak shorter (bluer), cooler ones peak longer (redder).
The Sun's surface, at 5778 K, peaks at **501.5 nm** — almost exactly in the middle of the visible
band, which is the entire reason daylight looks white rather than any one colour.

A cold, dark target has no peak at all. There is nothing to disperse if nothing is glowing.

## The lines tell you the composition

Layered on top of that glow are sharp lines — bright or dark, depending on whether you are looking
at the source directly or through a cooler gas in front of it. Every element makes its own,
always at the same wavelengths, because they come from the same electron transitions every time.
This is not a game mechanic borrowed from real science; it is *how real science works* — this is
the actual method used to know what stars are made of, without ever visiting one.

| Line | Wavelength | What it is |
|---|---|---|
| H-α | 656.3 nm | Hydrogen's Balmer line — the red of every nebula photograph ever taken |
| Na D₁ / D₂ | 589.6 / 589.0 nm | Sodium's doublet — contamination from something as ordinary as salt shows up unmistakably |
| Ca II H / K | 396.8 / 393.4 nm | Calcium's strongest pair — how solar activity is tracked from the ground |
| He I | 587.6 nm | Helium — found in the Sun's spectrum in **1868**, decades before it was found on Earth |
| Fe I | 527.0 nm | One line of iron's own forest — a rock's iron content, read before it is ever crushed |

![The visible spectrum, true wavelength to true colour. Every tick is a real line at its real position; the tall warm one past the green is the line below.](astronima:textures/codex/spectrum.png 160x96)

## The one that took sixty years to explain

There is a sixth line this instrument can find, at **500.7 nm**, and it will not show up on
anything you point this at today. For sixty years astronomers who found it in nebulae thought it
belonged to an undiscovered element and named it "nebulium." It was eventually identified as
doubly-ionised oxygen — [O III] — doing something ordinary oxygen never does on Earth: sitting in
an excited state just long enough to emit this exact wavelength before anything bumps into it.

That "long enough" is the whole story. Any collision at all knocks the atom out of that state
first, which is why you have never seen this line in a lit room: air is far too dense. It needs a
vacuum better than any laboratory on Earth keeps. Whatever finds one is a story for later.

## Reading a plate

{item:astronima:spectral_plate} is blank until it has caught something. Once it has, its own
tooltip lists every line actually found, wavelength and all — the same numbers this page just
gave you, because both come from the same place. A plate that came back with nothing was pointed
at something with no light, or nothing in it worth finding — which the tooltip says plainly,
rather than looking broken.

**TO USE THE SPECTROGRAPH:** hold a blank plate in your **off hand**, stand under open sky in
daylight, and right-click with {item:astronima:spectrograph} in your main hand. It refuses
cleanly with no clear sky, or with the wrong thing (or nothing) in your other hand.

The Sun is the target that is always there. It is no longer the only one: the sky carries named
objects worth aiming at, and — rarely — a supernova, the one target whose reading genuinely changes
between one plate and the next. See [[sky/events|Things that happen up there]].

## What the two claims proved

:::locked research:same_elements
The same H-alpha line, read from two objects with nothing in common — a cloud of gas lit by its
own newborn stars, and a dying star's shed shell — sits at exactly 656.3 nm in both. That is the
whole discovery: **the same elements are everywhere**. Hydrogen out there behaves by the same
rules hydrogen follows in a bottle in your own habitat. Every nebula photograph ever taken is
red for the same reason yours is.

This is how we learned the universe has one chemistry — not from a single lucky measurement, but
from the same fingerprint showing up in unrelated things, again and again, until coincidence
could no longer explain it.
:::

:::locked research:metal_assay
Iron's 527.0 nm line showed up in two wholly different old star populations — a great spiral's
core and its tiny companion galaxy — without heating a single sample of either. That is what a
spectral line is *for*: **a line names a metal without breaking the rock.** Every mine, every
smelter, every assay office on Earth is downstream of this trick. You have now done it across
intergalactic space.
:::

