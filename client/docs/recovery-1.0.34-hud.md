# HUD/GUI recovery notes for client 1.0.34

These notes describe the **1.0.34 binary**, not the older Java sources under `client/src`. A patch is available in `client/recovery-1.0.34` and was prepared as client 1.0.35 for release at the user's request; it has not been tested in Minecraft.

## Baseline

- The shipped file is `updates/nexus-client-1.0.34-mc1.21.11.zip`, SHA-256 `f6259ff4ac726111b648e9e1c0be84a85111fc5748fdce09d44071c227bb7ab5`. Its `fabric.mod.json` declares version 1.0.34 and Minecraft 1.21.11.
- `client/gradle.properties` declares 1.0.15. Rebuilding that source tree would not preserve the full 1.0.34 client.
- The 1.0.34 archive contains `dev/nexus/client/hud/RShiftHud`, `dev/nexus/client/ui/panel/RShiftPanel` and `RShiftScreen`; these are not present in the source tree. The GUI opens other screens, including the older-style HUD editor and module settings, so replacing it with the older GUI is not equivalent.
- Inspection used CFR on the released class files. Its output is an analysis aid, not compilable recovered source: Minecraft identifiers remain intermediary names and CFR did not successfully decompile `RShiftPanel.featuresForTab`.

## Observed HUD behavior and hazards

- `RShiftHud` stores offsets in `OFF`, keyed by `top`, `left`, `bottom`, `center`, `right` in `config/nexus_rshift_hud.json`. Preserve this format and existing offsets when implementing animation; do not persist transient interpolated coordinates.
- In the inspected render method, only zones 0, 4 and 3 are drawn. Zones 1 and 2 still have draggable outlines, despite no corresponding drawing in that method. Zone 3 draws the notification near y=421, while its bounding box begins at y=357. The right zone groups several separated cards. Any change to selection or boxes must be checked against the actual rendered contents and existing saved layouts.
- `handleDrag` runs after rendering. Its outlines call `pxF`/`pyF` with an offset already added; those helpers also add the current zone's `curDx`/`curDy`. Because zone 3 was the last rendered zone, moving it can shift every outline away from its hit area. Correct rendering, outlines and hit testing must use the same current visual position.
- The vertical drag clamp uses `b[2]` (width) rather than `b[3]` (height). Saving currently occurs on mouse release while a drag is active; closing chat with the mouse held cancels the drag without that save. Any recovery needs explicit tests for these cases and for loading existing layouts.
- Animations must honor the client's animation speed/instant setting, use elapsed time rather than frame counts, and avoid jumps on load, resolution changes and low frame rates. If drag targets and displayed positions differ, hit testing and outlines must follow the displayed positions while the saved target remains stable.

## Before shipping

1. Recover or surgically patch the exact 1.0.34 classes, preserving untouched classes and resources. Verify any bytecode transformation against the exact input hash and method signatures, and fail closed if they differ. Do not compile the 1.0.15 source tree as a substitute.
2. Test animation and interaction together: HUD selection/drag/release, chat close, screen bounds, resolution and GUI scale changes, persistence across restart, speed/instant settings, overlap, and GUI controls/navigation. Verify the RShift panel, classic screens and profiles all still work.
3. Run the patched client in Minecraft 1.21.11 with the required Fabric dependencies and inspect visuals and logs. Unit or bytecode checks alone do not prove in-game behavior. The user explicitly requested publication without this verification; the release must disclose that limitation.
