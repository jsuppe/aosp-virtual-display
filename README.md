# AOSP Virtual Display

Host-controlled logical displays and custom presentation to physical displays.
Extracted from [aosp-virtual-camera](https://github.com/jsuppe/aosp-virtual-camera)
on 2026-10-24, preserving the display directory's Git history.

## Status: first app-hosted POC

The [dual-output POC](poc/README.md) now creates a real Android virtual display,
launches a test activity onto it, and renders cropped halves to the primary
window and a secondary-display Presentation. It supports left/right and
top/bottom layouts plus independent host information areas. See the
[Pi validation record](docs/POC-VALIDATION-20261024.md) for the tested scope.

This app requires matched platform signing for Android's activity-embedding
permission. It does **not** replace the physical displays or complete the older
Composer HAL scaffold. The `hal/Android.bp` still references missing implementation,
init and VINTF files; do not attempt to build that scaffold as the POC.

The [original design notes](docs/INITIAL-DESIGN.md) are preserved verbatim for
history. Their proposed directory tree, sample code, API names, permission model,
HDR/rate claims and automatic configuration behavior are **proposals, not
implemented or verified guarantees**. Where they conflict with the direction
below, treat them as historical exploration.

## Current direction

A host service should control logical-display configuration and rendering to
physical outputs. Android supplies application/system content; the host decides
how that content maps onto the connected displays.

Intended capabilities:

- Host-controlled logical display lifecycle, dimensions and timing.
- Presentation across two physical displays: left/right, top/bottom, crops,
  transforms or other layouts selected by the host.
- Independently rendered host information in areas not occupied by Android's
  logical scene.
- Explicit buffer ownership/fences, input-coordinate mapping and defined behavior
  for host failure or display disconnects.

Two integration strategies have been discussed. The POC implements the app-hosted
subset of option 2, without app-placement enforcement; the final OS architecture
remains open:

1. Replace the primary display through a Composer/display backend available
   during native graphics startup.
2. Boot normally, create an activity-capable virtual display later, and enforce
   selected task placement in ActivityTaskManager/WindowManager. DisplayManager
   alone does not enforce activity placement, and this alone does not transfer
   physical HDMI ownership to the host.

The host/Android process boundary, physical-output ownership and startup/recovery
contract must be specified before implementation. The POC uses GLES for its two-output renderer; this is not a commitment to the
final backend and does not establish zero-copy/performance guarantees.

## Existing files

- `poc/` — SDK-buildable, platform-signed dual-output app and instructions.
- `scripts/build-poc.sh` — reproducible local compile/package/sign steps.

- `aidl/IVirtualDisplayService.aidl` — proposed registration/control API.
- `aidl/IVirtualDisplaySession.aidl` — proposed renderer session API.
- `aidl/VirtualDisplayConfig.aidl` — proposed configuration parcelable.
- `hal/VirtualDisplayComposer.h` — Composer interface scaffold.
- `hal/service.cpp` — native service-entry scaffold.
- `hal/Android.bp` — incomplete Soong build definition.
- [Original design](docs/INITIAL-DESIGN.md) — historical proposal, not a build guide.
- [Migration record](docs/MIGRATION.md) — provenance and verification scope.

For the separately released camera feature, see
[aosp-virtual-camera v0.1.0](https://github.com/jsuppe/aosp-virtual-camera/releases/tag/v0.1.0).
