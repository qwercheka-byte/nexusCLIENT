#!/usr/bin/env python3
"""Build an isolated experimental JAR from the exact checked-in 1.0.34 binary.

Requires Python 3 and a JDK (17+). No network, Minecraft downloads, or launcher changes.
"""
from __future__ import annotations
import argparse
import copy
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import zipfile

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
BASE = ROOT / "updates/nexus-client-1.0.34-mc1.21.11.zip"
EXPECTED = "f6259ff4ac726111b648e9e1c0be84a85111fc5748fdce09d44071c227bb7ab5"
HUD = "dev/nexus/client/hud/RShiftHud.class"
PANEL = "dev/nexus/client/ui/panel/RShiftPanel.class"
EXPORTS = ["--add-exports", "java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED",
           "--add-exports", "java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED",
           "--add-exports", "java.base/jdk.internal.org.objectweb.asm.tree.analysis=ALL-UNNAMED"]


def binary(name: str) -> str:
    home = os.environ.get("JAVA_HOME")
    candidate = str(Path(home) / "bin" / name) if home else shutil.which(name)
    if not candidate or not Path(candidate).is_file():
        raise SystemExit(f"Missing {name}; install a JDK 17+ or set JAVA_HOME")
    return candidate


def run(*args: str) -> None:
    subprocess.run(args, check=True)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, default=HERE / "artifacts/nexus-client-1.0.34-animation-EXPERIMENTAL.jar")
    parser.add_argument("--version", choices=("1.0.34", "1.0.35"), default="1.0.34")
    args = parser.parse_args()
    output = args.output.resolve()
    if output == BASE.resolve():
        raise SystemExit("Refusing to overwrite the original release")
    if hashlib.sha256(BASE.read_bytes()).hexdigest() != EXPECTED:
        raise SystemExit("Input archive is not the verified 1.0.34 release; refusing to patch")
    java, javac = binary("java"), binary("javac")
    with tempfile.TemporaryDirectory(prefix="nexus-recovery-") as tmp:
        work = Path(tmp); tools = work / "tools"; classes = work / "classes"; tools.mkdir(); classes.mkdir()
        run(javac, *EXPORTS, "-d", str(tools), str(HERE / "tools/Patch.java"), str(HERE / "tools/Verify.java"))
        sources = sorted(str(p) for d in ("src", "stubs") for p in (HERE / d).rglob("*.java"))
        tests = sorted(str(p) for p in (HERE / "tests").glob("*.java"))
        run(javac, "--release", "17", "-encoding", "UTF-8", "-d", str(classes), *sources, *tests)
        run(java, "-cp", str(classes), "HudMotionTest")
        run(java, "-cp", str(classes), "AdapterTest")
        with zipfile.ZipFile(BASE) as original:
            names = original.namelist()
            if len(names) != len(set(names)) or HUD not in names or PANEL not in names:
                raise SystemExit("Unexpected archive contents")
            if any(n.startswith("dev/nexus/recovery/") for n in names):
                raise SystemExit("Recovery classes already present")
            for name, kind in ((HUD, "hud"), (PANEL, "panel")):
                inp, out = work / (kind + ".input"), work / (kind + ".class")
                inp.write_bytes(original.read(name))
                run(java, *EXPORTS, "-cp", str(tools), "Patch", str(inp), str(out), kind)
            run(java, *EXPORTS, "-cp", str(tools), "Verify", str(work / "hud.class"), str(work / "panel.class"))
            output.parent.mkdir(parents=True, exist_ok=True)
            # Write outside the output path first; leave any existing output intact on failure.
            draft = work / "result.jar"
            with zipfile.ZipFile(draft, "w") as result:
                for info in original.infolist():
                    data = (work / "hud.class").read_bytes() if info.filename == HUD else \
                           (work / "panel.class").read_bytes() if info.filename == PANEL else original.read(info)
                    if info.filename == "fabric.mod.json" and args.version != "1.0.34":
                        metadata = json.loads(data)
                        assert metadata["version"] == "1.0.34"
                        metadata["version"] = args.version
                        data = (json.dumps(metadata, ensure_ascii=False, indent=2) + "\n").encode()
                    result.writestr(copy.copy(info), data)
                for file in sorted((classes / "dev/nexus/recovery").glob("*.class")):
                    info = zipfile.ZipInfo(file.relative_to(classes).as_posix(), (1980, 1, 1, 0, 0, 0))
                    info.compress_type = zipfile.ZIP_DEFLATED
                    result.writestr(info, file.read_bytes())
            with zipfile.ZipFile(draft) as result:
                assert result.testzip() is None
                for info in original.infolist():
                    if info.filename not in (HUD, PANEL, "fabric.mod.json"):
                        assert original.read(info) == result.read(info.filename), info.filename
                old_metadata = json.loads(original.read("fabric.mod.json"))
                new_metadata = json.loads(result.read("fabric.mod.json"))
                assert old_metadata.pop("version") == "1.0.34"
                assert new_metadata.pop("version") == args.version
                assert old_metadata == new_metadata
                added = set(result.namelist()) - set(names)
                assert added == {"dev/nexus/recovery/HudMotion.class", "dev/nexus/recovery/HudAdapter.class",
                                 "dev/nexus/recovery/PanelMotion.class", "dev/nexus/recovery/PanelMotion$State.class"}, added
            shutil.copyfile(draft, output)
    print("Client package:", output, "version", args.version)
    print("SHA-256:", hashlib.sha256(output.read_bytes()).hexdigest())
    print("Warning: this patch has not been tested in Minecraft.")


if __name__ == "__main__":
    main()
