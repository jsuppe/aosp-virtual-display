# AOSP Virtual Display

Host-controlled logical displays and custom presentation to physical displays.
Extracted from [aosp-virtual-camera](https://github.com/jsuppe/aosp-virtual-camera)
on 2026-10-24, preserving the display directory's Git history.

## Status: design and incomplete scaffold

This is **not yet a buildable or validated display implementation**. The existing
Soong file references missing implementation `.cpp`, init `.rc` and VINTF `.xml`
files. No host compositor, working physical-output ownership, boot integration,
SELinux policy, app-routing enforcement or device qualification is delivered.
Composer interface/library versions must be checked against the selected AOSP
base before implementation.

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

Two integration strategies have been discussed; neither is implemented or
selected as the final design:

1. Replace the primary display through a Composer/display backend available
   during native graphics startup.
2. Boot normally, create an activity-capable virtual display later, and enforce
   selected task placement in ActivityTaskManager/WindowManager. DisplayManager
   alone does not enforce activity placement, and this alone does not transfer
   physical HDMI ownership to the host.

The host/Android process boundary, physical-output ownership and startup/recovery
contract must be specified before implementation. This repository does not yet
choose a GPU API or establish zero-copy/performance guarantees.

## Existing files

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
