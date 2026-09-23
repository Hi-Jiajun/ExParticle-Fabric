[中文](README.md) | **English**

# ExParticle (Fabric)

**Unofficial** Fabric port of [hackermdch/ExParticle](https://github.com/hackermdch/ExParticle) v1.5.2
(NeoForge 1.21.1) to **Minecraft 1.21.10 / Fabric**.
Licensed **LGPL-3.0-only**, same as upstream (this is a modified version — see "Differences from upstream").

Drive particle motion with mathematical expressions: shapes, motion and colors are written as expressions
and evaluated per particle per tick.

## Requirements

- Minecraft **1.21.10**, Fabric Loader **≥ 0.19.5**, **Fabric API**, **Java ≥ 21**
- Optional: [JavaCV](https://github.com/bytedeco/javacv) for the `video` command — drop the jars into
  `<gamedir>/javacv/` (tested with `javacv`, `javacpp`, `javacpp-windows-x86_64`, `ffmpeg`, `ffmpeg-windows-x86_64` 1.5.11)

## Build

```bash
./gradlew build          # output: build/libs/exparticle-fabric-<version>.jar
```

Behind an HTTP proxy add `-Dhttp.proxyHost=<host> -Dhttp.proxyPort=<port>` on the command line
(don't put personal proxy settings into `gradle.properties` — this repo intentionally ships without them).

## Commands

All upstream command families are available:

`normal` / `conditional` / `parameter` / `polar-parameter` / `tick-*` / `rgba-*` /
`custom-normal|parameter|image|conditional|video` / `image(-matrix)` / `video(-matrix)` / `text` /
`group remove|change` / `clear-particle|clear-cache` / `global-variable` / `user-function`

```
/particlex normal minecraft:end_rod ~ ~1 ~ 1 1 1 1 0 0 0 1 1 1 500 600 "null" 1
/particlex text   minecraft:end_rod ~ ~1 ~ "AB" 3 "null" 10 0 0 0 600
/particlex image  minecraft:end_rod ~ ~1 ~ "test.png" 1 0 0 0 not 10 0 0 0 600
```

- `image` / `video` read files from `<gamedir>/particleImages` and `<gamedir>/particleVideos`
- Matrix arguments are **literal matrices** (`"E4"` for the identity, or comma-separated with `",,"` as row break), not expressions
- `color4` / `speed3` style arguments are greedy multi-token parsers — always write the full number of components

## Differences from upstream

1. **`text` uses a self-managed GPU offscreen rasterizer.** MC 1.21.6 removed the `TextureTarget`/legacy projection
   API that upstream relied on, so this port renders glyph quads into its own FBO (`#version 330 core`, pixel-space
   vertices, `glReadPixels` readback) with a CPU software-rasterizer fallback.
2. **`video` loads JavaCV through a child-first class loader.** Five core jars are enough (upstream's module-layer
   approach needs the whole JavaCV module chain plus JavaFX), and the port works around upstream's
   `Java2DFrameConverter.convert()` NPE by using `copy()`.
3. **Bold text looks different from 1.21.1 upstream.** MC 1.21.10 changed bold from "draw twice with an offset" to
   "single pass with `extraThickness`"; this port follows 1.21.10.
4. Differential testing against upstream: **16 of 17 cases produce bit-identical particle counts**; the one
   exception is exactly the bold difference above (MC version semantics).

## Verification / diagnostics

- `-Dexparticle.text=cpu|auto|gpu` — force CPU rasterizer / default (GPU with CPU fallback) / strict GPU
- `-Dexparticle.selftest=true` — on world join, runs a CPU/GPU cross-check of the text pipeline and prints ASCII pixel maps
- `-Dexparticle.text.compare=true` — per-call CPU/GPU pixel comparison
- `-Dexparticle.text.forceRed=true` — force the R8-atlas code path (diagnostic only)
- `-Dexparticle.selftest.api=true` — exercise `ExParticleApi` and report the returned sizes
- `-Dexparticle.selftest.video=true` — print the color written per decoded video frame
- `-Dexparticle.testChannel=true` — enable the file-driven test channel
  (`<gamedir>/config/exparticle-live.txt`, **off by default in releases**)

## Credits & license

- Upstream: [hackermdch/ExParticle](https://github.com/hackermdch/ExParticle) v1.5.2 (NeoForge 1.21.1),
  **LGPL-3.0-only**
- This repository is an unofficial Fabric port and therefore a modified version: it is distributed under the
  **same license (LGPL-3.0-only)**; `LICENSE.md` is the upstream text. Source and modification notes are
  available in this repository (LGPL requirement).
- The project icon was drawn for this port; no upstream art assets are used.
- Detailed porting notes, evidence tables and the full upstream-differential test report are in the
  [Chinese README](README.md).
