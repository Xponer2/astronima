# Astronima

A hardcore, real-science space survival mod for NeoForge. You wake up in the wreck of a
crashed crew module on a hollow asteroid — no wood, no vanilla farming, no shortcuts.
Every hazard runs on real physics and chemistry (gas partial pressures, real
stoichiometry, real decompression and radiation models), and every gate you have to
pass through a real machine to get past is a law of nature, not an arbitrary tier.

This repository holds the buildable mod source only. Design and progression documents
are kept out of it on purpose, so the build stays spoiler-free.

## Requirements

- Minecraft `1.26.1.2`
- NeoForge `26.1.2.82` or newer (`[26,)`)
- [Curios](https://www.curseforge.com/minecraft/mc-mods/curios) `15.0.0+26.1.2` or newer
- Java 25 to build from source

## Installing

Grab the jar from the [Releases](../../releases) page and drop it in your `mods`
folder alongside a matching Curios build.

## Building from source

```bash
./gradlew build
```

The mod jar is written to `build/libs/`.

## Releases

Pushing a tag (`vX.Y.Z`) builds the mod and publishes it as a GitHub Release
automatically, with release notes generated from the commits since the last tag.
