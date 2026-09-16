---
title: Getting clean
section: Medical
order: 61
icon: astronima:decon_station
---

There are three ways to pick something up and, until you build this, one way to put it down:
stand outside and wait. Vacuum weathers a load off in minutes; a warm habitat takes most of a day.

## The one thing worth understanding

Sterilisation is not subtraction. **Every equal dose kills the same fraction of what is left.**
Microbiologists call the dose that takes a population down tenfold its *D-value*, and the next
tenfold costs exactly the same again:

```
survivors = load × 10 ^ (−dose / D)
```

Three consequences, and all three will catch you out at least once:

- **The first half of the job is cheap and the last half is expensive.** Going from filthy to a
  tenth of filthy costs what going from a tenth to a hundredth costs.
- **There is no sterile button.** You pick a target and pay for it. That is why the station's
  gauge reads in *decades* rather than percent — a percentage flatters the first few seconds and
  then looks broken for the rest of the run.
- **A better lamp is not twice as fast, it is one more decade in the same time.**

> This is the same logarithmic thinking the disc-diffusion plate already asked of you. Zone
> diameter goes as the *logarithm* of concentration; kill goes as the logarithm of dose. Biology
> is full of straight lines that are only straight on a log axis — see [[medical/bench]].

## Why the booth has two controls

| | Ultraviolet | Rinse |
|---|---|---|
| Speed | fast — a decade in about five seconds | slow |
| Reach | **only what it can see** | everywhere the water runs |
| Costs | power | water, and it is consumed |
| Fails when | anything is shadowed | the tank runs dry |

**Shadowing is the whole reason water exists here.** A suit has folds, a glove has a palm, a boot
has a sole. Germicidal light is line-of-sight and nothing else, so a fixed share of the load —
about one part in sixteen — is simply out of its reach no matter how long you stand there.

The station shows you this rather than telling you: run the lamp and the bars fall fast, then
**stop**. That is not a bug and it is not the machine finishing. It is the lamp having cleaned
everything it can see, and it switches itself to the rinse. With no water in the tank it stops
there and you walk out one sixteenth dirty.

## Using it

1. Fill the tank — right-click with a water bottle (a third of a litre) or a bucket (the lot).
2. Stand on it and use it. The lamp comes on.
3. **Watch the biomonitor**, not the block. Press <kbd>H</kbd> and the contamination bars fall
   while you stand there. That readout is the instrument; the station deliberately does not have a
   second screen showing the same two numbers.
4. Walking out halfway leaves you halfway clean. Nothing is cancelled and nothing is wasted.

## Finding out what you have

The station gets a load off you. It cannot tell you *what* it was.

For that, take a blank Petri dish and **right-click the surface itself**. That is a swab, and the
plate now carries whatever your habitat has been growing on that bench — not an isolate somebody
handed you, but your own contamination, from a room you built.

Then it is the ordinary bench workflow: streak it out, incubate it at 37 °C, stain it, look at it,
and run the discs. See [[medical/bench]].

> **A blank plate is a real result.** Swab something nearly clean and nothing grows: you have spent
> a plate and a night to learn that. That is the cost of guessing where to sample, and it is what
> makes the goggles worth wearing — they tell you *where*, and the plate tells you *what*.

A swab arrives as a smear, never a streak. Every environmental plate has to be worked out into
quadrants at the bench before it will give a single colony you can pick.

## What it does not do

It cleans people and what they are carrying, not rooms. A contaminated surface in your habitat
stays contaminated until it weathers, and the only fast answer to that is still the airlock and
hard vacuum — see [[life/airlock]].

Nothing here ever reaches zero, because that is what the mathematics says. The station calls it
done at a thousandth, which is below anything that can start an infection. See
[[medical/bench]] for what a dose actually is.
