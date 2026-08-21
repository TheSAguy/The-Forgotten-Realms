#!/usr/bin/env python3
"""Assemble "The Forgotten Realms" standalone game folder.

One command builds the shippable package (MOD_SCOPE.md #89 part 2):

    python standalone-packaging/build_standalone.py [--zip]

Inputs:
  - This repo (must be built first:
      mvn -pl forge-gui-mobile-dev -am package -DskipTests
    so forge-gui-mobile-dev/target/ holds the jar-with-dependencies).
  - A stock Forge install of the SAME engine version as the repo (BASE_INSTALL
    below) - used for the launcher exe/cmd shells and the installer-shaped res/
    tree (cardsfolder.zip etc.), which the repo does not contain in that shape.

Output: OUT_DIR/<GAME_NAME>/ - unzip-anywhere game folder. --zip also writes
OUT_DIR/<GAME_NAME>-<version>.zip.

What it does, in order:
  1. Verify the built jar's version matches BASE_INSTALL's jar version.
  2. Copy the include-listed root files + launcher shells from BASE_INSTALL,
     renaming the adventure launcher to the game's name.
  3. Copy BASE_INSTALL/res EXCEPT res/adventure.
  4. res/adventure gets exactly two entries: common/ (from BASE_INSTALL) and
     the repo's "The Forgotten Realms" plane folder.
  5. Overwrite the jar with the repo-built one (carries the mod engine code).
  6. Overlay the repo's non-adventure res edits (en-US.properties, skins art) -
     the list is DERIVED from git (diff vs the upstream merge base), so future
     rounds' res edits are picked up automatically.
  7. Drop in README.md, CREDITS.md, GAME_GUIDE.md; mirror LICENSE.txt +
     CREDITS.md into the plane folder ("licensing in the mod folder").
  8. Verify: our GameLauncher title marker is inside the shipped jar, the
     update-check kill is present, res/adventure has exactly 2 entries.
"""
import argparse
import os
import re
import shutil
import subprocess
import sys
import time
import zipfile

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BASE_INSTALL = r"E:\GAMES\Forge_2"
OUT_DIR = r"F:\FORGE\TFR-Standalone"
GAME_NAME = "The Forgotten Realms"
PLANE = "The Forgotten Realms"

# Root files copied verbatim from BASE_INSTALL (everything else is deliberately
# dropped: stock-Forge client, editors, uninstaller, upstream txt files that
# would mislead in a standalone context).
ROOT_INCLUDE = ["LICENSE.txt", "CONTRIBUTORS.txt", "build.txt"]


def fail(msg):
    print(f"ERROR: {msg}")
    sys.exit(1)


def find_jar(folder):
    jars = [f for f in os.listdir(folder)
            if re.fullmatch(r"forge-gui-mobile-dev-.*-jar-with-dependencies\.jar", f)]
    if len(jars) != 1:
        fail(f"expected exactly one mobile-dev jar in {folder}, found {jars}")
    return jars[0]


def git_overlay_list():
    """Non-adventure files under forge-gui/res that the mod changed vs upstream."""
    mb = subprocess.check_output(
        ["git", "merge-base", "HEAD", "upstream/master"], cwd=REPO, text=True).strip()
    out = subprocess.check_output(
        ["git", "-c", "core.quotepath=off", "diff", "--name-only", mb, "HEAD", "--", "forge-gui/res"],
        cwd=REPO, text=True, encoding="utf-8")
    files = [f for f in out.splitlines()
             if f and not f.startswith("forge-gui/res/adventure/")]
    return files


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--zip", action="store_true", help="also write a release zip")
    args = ap.parse_args()

    jar_name = find_jar(os.path.join(BASE_INSTALL))
    built_jar_dir = os.path.join(REPO, "forge-gui-mobile-dev", "target")
    if not os.path.isdir(built_jar_dir):
        fail("repo not built - run: mvn -pl forge-gui-mobile-dev -am package -DskipTests")
    built_jar = os.path.join(built_jar_dir, find_jar(built_jar_dir))
    if os.path.basename(built_jar) != jar_name:
        fail(f"version mismatch: built {os.path.basename(built_jar)} vs base install {jar_name} - "
             "the launcher shells target the base install's jar name exactly")

    game_dir = os.path.join(OUT_DIR, GAME_NAME)
    if os.path.exists(game_dir):
        print(f"removing previous package at {game_dir}")
        shutil.rmtree(game_dir)
    # Windows: rmtree returns before the directory handle is fully released, so an
    # immediate makedirs can get WinError 5 - retry briefly.
    for attempt in range(30):
        try:
            os.makedirs(game_dir)
            break
        except (PermissionError, FileExistsError):
            if attempt == 29:
                raise
            time.sleep(1)

    # 2. root files + launchers
    for f in ROOT_INCLUDE:
        shutil.copy2(os.path.join(BASE_INSTALL, f), game_dir)
    # the exe comes from OUR build (launch4j in forge-gui-mobile-dev's package phase) - it
    # carries the TFR icon from src/main/config/forge-adventure.ico, unlike the stock exe
    repo_exe = os.path.join(built_jar_dir, "forge-adventure.exe")
    if not os.path.exists(repo_exe):
        fail("forge-adventure.exe missing from target/ - run the package (not just compile) goal")
    shutil.copy2(repo_exe, os.path.join(game_dir, f"{GAME_NAME}.exe"))
    cmd = open(os.path.join(BASE_INSTALL, "forge-adventure.cmd"), encoding="utf-8",
               errors="ignore").read()
    open(os.path.join(game_dir, f"{GAME_NAME}.cmd"), "w", encoding="utf-8",
         newline="\r\n").write(cmd)

    # 3. res minus adventure
    print("copying res/ from base install (this is the big one)...")
    shutil.copytree(os.path.join(BASE_INSTALL, "res"), os.path.join(game_dir, "res"),
                    ignore=lambda d, names: ["adventure"] if os.path.samefile(d, os.path.join(BASE_INSTALL, "res")) else [])

    # 4. adventure = common + the plane
    adv = os.path.join(game_dir, "res", "adventure")
    os.makedirs(adv)
    shutil.copytree(os.path.join(BASE_INSTALL, "res", "adventure", "common"),
                    os.path.join(adv, "common"))
    print("copying the plane folder from the repo...")
    shutil.copytree(os.path.join(REPO, "forge-gui", "res", "adventure", PLANE),
                    os.path.join(adv, PLANE))

    # 5. our jar
    print("copying the built jar...")
    shutil.copy2(built_jar, os.path.join(game_dir, jar_name))

    # 6. non-adventure res overlay, derived from git
    overlay = git_overlay_list()
    for rel in overlay:
        src = os.path.join(REPO, rel)
        dst = os.path.join(game_dir, "res", os.path.relpath(rel, "forge-gui/res"))
        if not os.path.exists(src):
            print(f"  overlay skip (deleted in repo): {rel}")
            continue
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        shutil.copy2(src, dst)
    print(f"overlaid {len(overlay)} repo res file(s): {overlay}")

    # 7. docs
    here = os.path.dirname(os.path.abspath(__file__))
    shutil.copy2(os.path.join(here, "README.md"), game_dir)
    shutil.copy2(os.path.join(here, "CREDITS.md"), game_dir)
    guide = os.path.join(adv, PLANE, "GUIDE.md")
    if os.path.exists(guide):
        shutil.copy2(guide, os.path.join(game_dir, "GAME_GUIDE.md"))
    shutil.copy2(os.path.join(BASE_INSTALL, "LICENSE.txt"), os.path.join(adv, PLANE))
    shutil.copy2(os.path.join(here, "CREDITS.md"), os.path.join(adv, PLANE))

    # 8. verify
    errors = []
    with zipfile.ZipFile(os.path.join(game_dir, jar_name)) as z:
        gl = z.read("forge/app/GameLauncher.class")
        if b"The Forgotten Realms (Forge " not in gl:
            errors.append("shipped jar's GameLauncher lacks the standalone title - wrong/stale jar?")
        pp = z.read("forge/localinstance/properties/ForgeProfileProperties.class")
        if b"ForgottenRealms" not in pp:
            errors.append("shipped jar lacks the ForgottenRealms data-dir rebrand")
    entries = sorted(os.listdir(adv))
    if entries != sorted(["common", PLANE]):
        errors.append(f"res/adventure should hold exactly common + the plane, has: {entries}")
    if errors:
        for e in errors:
            print("VERIFY FAIL:", e)
        sys.exit(1)

    total = sum(os.path.getsize(os.path.join(r, f))
                for r, _, fs in os.walk(game_dir) for f in fs)
    print(f"\nPackage OK: {game_dir}  ({total / 1024 / 1024:.0f} MB)")

    if args.zip:
        # Name the release zip by the GAME's version (config.json modVersion), not the Forge
        # engine version - players downloading "v1.00" were confused by a "2.0.15" filename.
        version = "dev"
        try:
            cfg = open(os.path.join(REPO, "forge-gui", "res", "adventure", PLANE, "config.json"),
                       encoding="utf-8").read()
            mv = re.search(r'"modVersion"\s*:\s*"([^"]+)"', cfg)
            if mv:
                version = "v" + mv.group(1)
        except OSError:
            pass
        zpath = os.path.join(OUT_DIR, f"{GAME_NAME.replace(' ', '-')}-{version}.zip")
        print(f"zipping to {zpath} ...")
        with zipfile.ZipFile(zpath, "w", zipfile.ZIP_DEFLATED) as z:
            for r, _, fs in os.walk(game_dir):
                for f in fs:
                    p = os.path.join(r, f)
                    z.write(p, os.path.relpath(p, OUT_DIR))
        print("zip done")


if __name__ == "__main__":
    main()
