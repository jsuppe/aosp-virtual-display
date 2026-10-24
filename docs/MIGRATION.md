# Repository extraction — 2026-10-24

Source: `jsuppe/aosp-virtual-camera`, directory `virtual-display/`, at
`d7b8ca70277e7aecffb36934569337a78ccc54b6` (camera release `v0.1.0`).

The directory history was extracted with `git filter-repo --path virtual-display/
--path-rename virtual-display/:` in a fresh clone. Unrelated camera files and
release tags were excluded. The original display commit subject/author/date are
retained; its hash changes because its tree and ancestry are filtered.

All seven original file contents were preserved. The original `README.md` is now
`docs/INITIAL-DESIGN.md`; six AIDL/HAL files retain their relative paths. The new
root README identifies actual scaffold status and the host-service direction.

The camera repository's current `main` replaces the original directory contents
with a forwarding README and updates its top-level references. Existing camera
release tags and history remain unchanged and still contain their original copy.

Verification: compare each migrated file's SHA-256 with the source checkout,
check new documentation links/whitespace, and verify the new GitHub default
branch and commit. The archived design retains its original trailing whitespace
so its content remains byte-for-byte identical; a whole-commit whitespace check
flags those historical lines, not new implementation changes. No compile/device test is claimed: the
scaffold references missing source/init/manifest files and is not build-ready.
