# App-hosted dual-output virtual display POC

## What runs

`HostActivity` owns one private, own-content-only Android `VirtualDisplay`.
`SceneActivity` is a real WindowManager activity launched onto that display with
`ActivityOptions.setLaunchDisplayId`. It draws the test grid, frame stamp and
moving marker, without knowing the physical-output arrangement.

```text
SceneActivity on Android virtual display
                  ↓ Surface / BufferQueue
          SurfaceTexture (external GL texture)
                  ↓ one EGL owner, two cropped draws
       Host SurfaceView        Presentation SurfaceView
          HDMI output 1            HDMI output 2
```

The host uses a main Activity on the primary display and a Presentation on the
first other presentation-capable display. Both consume crops of the **same
latched texture**, not two independently drawn copies of the test animation.
Output swaps are sequential; this does not synchronize physical scanout.

Controls:

- **Left / right:** a 1920×540 logical canvas split into two 960×540 regions.
- **Top / bottom:** a 960×1080 logical canvas split into two 960×540 regions.
- **Launch activity:** replace/relaunch the test activity on the virtual display.
- **Stop:** release the virtual display, output Presentation and renderer.

Each output letterboxes its crop. The two tested 1920×1080 outputs reserve the
same 128-pixel top and 40-pixel bottom host regions, independent of display DPI.
Those margins are intentionally simple POC geometry, not a general layout engine.
Host labels/controls are outside the virtual scene and rendered independently.
The animation requests redraws roughly every 33 ms; neither source nor display
throughput is guaranteed by that request.

## Why this architecture first

Android keeps its current physical displays, SurfaceFlinger and Composer HAL.
The app provides the virtual-display buffer sink and custom GLES renderer.
No framework patch, primary-display replacement, display-mode policy change or
HAL installation is needed. This proves activity → logical display → host
renderer → two outputs before undertaking native boot-time display ownership.

This POC is foreground/app-owned. A persistent host service, arbitrary-app launch
policy and exclusive physical-output ownership are future work.

## Matched-base permission requirement

The Pi's AOSP17 `ActivityTaskSupervisor.isCallerAllowedToLaunchOnDisplay` requires
an activity on an untrusted display to opt into embedding. With no existing
activity from the caller on that display, it additionally requires
`android.permission.ACTIVITY_EMBEDDING` (signature/privileged).

This POC therefore:

- sets `android:allowEmbedded="true"` only on its non-exported `SceneActivity`;
- requests `ACTIVITY_EMBEDDING` and is signed with the **matched development
  image's platform certificate**;
- uses the matched AOSP `VIRTUAL_DISPLAY_FLAG_DESTROY_CONTENT_ON_REMOVAL` value
  (`1 << 8`, a SystemApi constant omitted from public SDK stubs) so stopped/dead
  hosts do not migrate their virtual-display tasks onto the primary output.

An ordinary debug-signed APK was tested and rejected. Adding a Presentation as a
launch anchor did not satisfy the check: AOSP's `isUidPresent` counts activities,
not Presentation windows. Those attempts are not claimed as working solutions.
Do not disable framework checks or grant broad system-window privileges to avoid
this boundary. The build script requires matching signing inputs and does not
embed or generate platform keys.

## Build and install

Dependencies: Android SDK platform 35/build-tools 35.0.0, JDK with `javac`, `zip`,
and the authorized target's platform signing certificate/key. No Gradle downloads
or AOSP full rebuild are required for this app.

```sh
export ANDROID_SDK_ROOT=/absolute/path/to/Android/Sdk
export VDISPLAY_SIGN_KEY=/absolute/path/to/matched/platform.pk8
export VDISPLAY_SIGN_CERT=/absolute/path/to/matched/platform.x509.pem
./scripts/build-poc.sh
adb -s SERIAL install -r out/poc/VirtualDisplayPoc.apk
adb -s SERIAL shell am start -n com.ruminate.vdisplay/.HostActivity
```

Keep signing keys outside this repository. The script uses a fresh build directory,
compiles Java/dex, aligns and signs the APK, verifies its signature and prints its
SHA-256. Output is ignored by Git. A prior differently signed package must be
removed before installation; do not uninstall unrelated apps.

Use two connected displays. The tested Pi secondary output was presentation-capable
although it was not configured to host desktop tasks; the host needs a Presentation,
not a separate Activity, on that physical output. The virtual display itself has
own-content/task-hosting semantics.

## Inspect and remove

```sh
adb -s SERIAL shell dumpsys activity activities
adb -s SERIAL shell dumpsys display
adb -s SERIAL logcat -s VDisplayPOC
adb -s SERIAL shell am force-stop com.ruminate.vdisplay
```

Expect HostActivity on the primary display, SceneActivity on a different virtual
display, and a Presentation on the other physical display. IDs are assigned by
Android; do not hardcode the observed IDs into scripts. Stopping the host should
remove the virtual display and its test activity. Starting again creates a new one.

## Scope and known limitations

- Verified only on the matched Pi5/AOSP17 userdebug, **SELinux-permissive** base.
  Signature permission success does not establish Enforcing compatibility.
- Only the supplied activity is launched. Arbitrary third-party activities,
  permission dialogs, IME, DRM/protected content and system UI are not qualified.
- Touch/mouse/key events on the rendered scene are **not forwarded** to its virtual
  display. Host buttons work on the primary output. Input mapping is next-stage work.
- Two outputs only; the first eligible secondary display is selected automatically.
  Display-listener handling exists, but physical unplug/replug and mixed-resolution/
  mixed-refresh arrangements require separate testing.
- No boot/startup service, no app-placement enforcement and no persistent layout
  configuration. Relaunch starts left/right.
- No frame-rate, optical-latency, long-soak or synchronized-scanout qualification.
  Source-frame and swap counters are diagnostics, not proof of displayed FPS.
- The older `hal/` and `aidl/` directories remain incomplete historical scaffolds;
  this SDK-built POC does not use them or claim to complete that HAL.

See [the test record](../docs/POC-VALIDATION-20261024.md).
