# Experimental HUD/GUI animation for 1.0.34

**Not yet tested in Minecraft.** This recovery project also builds the separately labelled 1.0.34 experimental JAR. Client 1.0.35 is prepared from the same patch for publication at the user's explicit request despite the lack of an in-game test. Keep backups and test in a separate instance.

The patch keeps the existing 1.0.34 classes and resources except for surgical bytecode changes to `RShiftHud` and `RShiftPanel`, plus four helper classes. It interpolates the three actually rendered HUD zones while dragging, keeps hitboxes and outlines aligned with displayed positions, corrects vertical bounds and saves on release or when chat closes. The RShift panel slides into place on opening. Both use the existing animation-speed/instant setting. This does not add animations to every classic GUI screen or individual feature card.

The experimental JAR's mod metadata still says `1.0.34`, deliberately. The release package uses `1.0.35` to enable updates. Never install either alongside another mod with the `nexus` ID. Nexus Launcher may replace the experimental build when a newer official version is available.

## How to test safely

1. In Nexus Launcher, right-click your 1.21.11 Fabric instance and duplicate it. The copy contains its own game files, configuration and worlds. Close the game before editing files.
2. In the copied instance's mods page, open the mods folder. Move its existing Nexus Client `.jar` **out of the mods folder** to a backup folder. Do not remove Fabric API or other mods. If several Nexus jars exist, move all of them out: Fabric cannot load two copies of the same mod.
3. Copy `artifacts/nexus-client-1.0.34-animation-EXPERIMENTAL.jar` into the copied instance's mods folder. Start that copied instance only.
4. In a test world, open the RShift panel; check that it slides in and that buttons, search, tabs, settings, scrolling and profiles work. Open chat and drag the top, right and notification HUD zones; check that their borders and drag hitboxes follow the visible elements. Move them to each edge, close chat during a drag, reopen and restart to verify saved positions. Check different GUI scales/resolutions and the animation speed setting, including instant.
5. If it crashes or looks wrong, remove the experimental JAR and restore the original from your backup; send `minecraft/logs/latest.log` and, if present, the newest file in `minecraft/crash-reports`. Do not post logs containing information you do not want to share. The original instance remains untouched.

## Rebuilding and verification

Requires Python 3 and JDK 17 or newer with its bundled internal ASM (`JAVA_HOME` may be set). No downloads are needed:

```sh
python3 client/recovery-1.0.34/build.py
# Release metadata and filename:
python3 client/recovery-1.0.34/build.py --version 1.0.35 --output updates/nexus-client-1.0.35-mc1.21.11.zip
python3 client/recovery-1.0.34/verify_release.py
```

The script rejects any baseline whose SHA-256 is not `f6259ff4ac726111b648e9e1c0be84a85111fc5748fdce09d44071c227bb7ab5`; asserts expected bytecode structure; runs motion and adapter tests against stubs; verifies the rewritten methods' bytecode data flow; compares all other archive entries byte-for-byte; and builds the JAR in `artifacts/`. Stub classes and tests are never packaged. The ASM checks use JDK-internal APIs and temporarily adjust the input class version for the JDK 17 reader, then restore Java 21 in the output. These checks cannot replace testing with the real Minecraft/Fabric runtime.
