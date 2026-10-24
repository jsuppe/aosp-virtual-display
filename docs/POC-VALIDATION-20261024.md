# App-hosted POC validation — 2026-10-24

Target: the existing Raspberry Pi 5 / AOSP17 development image, two connected
1920×1080 60-Hz HDMI outputs. Base remains userdebug/test-signed and SELinux
permissive. No framework, Composer HAL, display-mode settings or camera packages
were changed. A separate `com.ruminate.vdisplay` APK was installed.

## Verified

- SDK compilation, dexing, APK alignment and signature verification passed.
- The matched platform-signed app receives `ACTIVITY_EMBEDDING`.
- WindowManager places HostActivity on physical display 0 and SceneActivity on
  a distinct app-owned virtual display. Secondary display 4 hosts a Presentation,
  not a second copy of SceneActivity. Display IDs are observations, not constants.
- Physical-display screenshots show **LEFT HALF / RIGHT HALF** from a single
  1920×540 activity canvas, then **TOP HALF / BOTTOM HALF** from a resized
  960×1080 canvas. The visible source-frame stamps advance; host status regions
  are independent of the activity image. Both crops preserve orientation.
- The primary host buttons switch top/bottom → left/right and relaunch the
  activity on the same virtual display.
- **Stop** removes the virtual display and test activity, leaving only the two
  original physical displays. A subsequent launch creates a new virtual display.
- **Force-stop** also removes the virtual display/activity. Relaunch restores the
  app-hosted scene. Final state is live left/right rendering.
- Both output swap counters advance. These are diagnostic submissions, not
  measured refresh, distinct-pixel throughput or synchronized physical scanout.

## Screenshots

- Left/right layout: [primary / left](poc-screenshots/vdisplay-left.png),
  [secondary / right](poc-screenshots/vdisplay-right.png).
- Top/bottom layout: [primary / top](poc-screenshots/vdisplay-top.png),
  [secondary / bottom](poc-screenshots/vdisplay-bottom.png).

These are sequential physical-display compositor captures, so frame stamps differ
between outputs. They demonstrate layout, not synchronized scanout or optical latency.

## Failures and corrections retained

1. Initial debug-signed build: virtual display and both output windows were
   created, but activity launch was denied. An embeddable activity plus a
   Presentation anchor was also denied: the framework's presence check counts
   ActivityRecords. Corrected by matched platform signing and the narrow
   signature `ACTIVITY_EMBEDDING` permission, without disabling framework checks.
2. Before setting destroy-on-removal, package replacement/display removal could
   relocate the old scene to display 0. The final build requests
   `VIRTUAL_DISPLAY_FLAG_DESTROY_CONTENT_ON_REMOVAL`; explicit Stop and owner
   force-stop then left no scene activity or virtual display behind.
3. Initial serial transfer failed its hash check because the shell's heredoc
   temporary directory was not writable. Retried with a task-local writable
   temporary directory; no bad APK was installed. An uninstall status marker
   was interleaved with kernel console text; recorded output/package state
   established success before continuing.

Final APK SHA-256:
`0e71cab8b1bbd0d3491468f5cf083f5a2b3d36e562ebf3c7b69ee8539f2057a1`.

## Not qualified

Arbitrary third-party activity launch, input forwarding, protected/DRM content,
IME/system dialogs, Enforcing operation, other Android/BSP versions, physical
hotplug, mixed resolution/refresh, boot-time service recovery, long soak,
throughput and optical latency. Physical screen captures verify compositor
content; they are not synchronized photographs of the monitors.

The legacy `hal/` and `aidl/` proposal remains incomplete and is not used here.
See [build, architecture and controls](../poc/README.md).
