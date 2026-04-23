#!/usr/bin/env python3
"""
Copies alpha eye overlay textures (*_alpha.png) from the cobblemon-assets repo
into the correct locations in the cobblemon textures directory.

Can use either:
  - A local clone of cobblemon-assets (fast, file copies)
  - GitLab API downloads (slower, no clone needed)

Destination: common/src/main/resources/assets/cobblemon/textures/pokemon/NNNN_species/

Safe to re-run. Will prompt for overwrite behavior on startup.
"""

import os
import sys
import shutil
import unicodedata
import urllib.request
import json
from pathlib import Path

SCRIPT_DIR   = Path(__file__).resolve().parent
REPO_ROOT    = SCRIPT_DIR.parent.parent
TEXTURES_DIR = REPO_ROOT / "common/src/main/resources/assets/cobblemon/textures/pokemon"

GITLAB_PROJECT  = "cable-mc%2Fcobblemon-assets"
GITLAB_BRANCH   = "master"
GITLAB_RAW_BASE = f"https://gitlab.com/api/v4/projects/{GITLAB_PROJECT}/repository/files"
GITLAB_TREE_BASE = f"https://gitlab.com/api/v4/projects/{GITLAB_PROJECT}/repository/tree"

ASSETS_POKEMON_SUBPATH = "blockbench/pokemon"

# Manual aliases for filenames in cobblemon-assets that differ from folder names in the mod.
# Key: species name as it appears in the alpha filename (without _alpha).
# Value: species name as it appears in the NNNN_species folder.
ALIASES: dict[str, str] = {
    "basculeigon":        "basculegion",
    "basculeigon_female": "basculegion",
    "basculeigon_male":   "basculegion",
    "klevin":             "kleavor",
    # Add further entries here as needed.
}


# ---------------------------------------------------------------------------
# Prompts
# ---------------------------------------------------------------------------

def prompt_assets_root() -> Path | None:
    """Ask user for local assets clone path. Returns None if blank/invalid."""
    print("=" * 60)
    print("cobblemon-assets source")
    print("=" * 60)
    response = input(
        "Enter path to your cobblemon-assets clone (ensure its up to date)\n"
        "(leave blank to download via GitLab API): "
    ).strip()

    if not response:
        return None

    path = Path(response)
    if not path.exists():
        print(f"  WARN: Path does not exist: {path}")
        print("  Falling back to GitLab API downloads.")
        return None

    pokemon_path = path / ASSETS_POKEMON_SUBPATH
    if not pokemon_path.exists():
        print(f"  WARN: Expected '{ASSETS_POKEMON_SUBPATH}' not found under {path}")
        print("  Is this the correct cobblemon-assets root?")
        confirm = input("  Continue anyway? (y/n): ").strip().lower()
        if confirm != "y":
            print("  Falling back to GitLab API downloads.")
            return None

    return path


def prompt_overwrite() -> bool:
    """Ask user whether to overwrite existing files."""
    print()
    print("=" * 60)
    print("Overwrite existing files?")
    print("=" * 60)
    while True:
        response = input("Overwrite existing alpha textures? (y/n): ").strip().lower()
        if response in ("y", "yes"):
            return True
        if response in ("n", "no"):
            return False
        print("  Please enter y or n.")


# ---------------------------------------------------------------------------
# Local file discovery
# ---------------------------------------------------------------------------

def find_alpha_textures_local(assets_root: Path) -> list[Path]:
    """Find all *_alpha.png files under blockbench/pokemon/ in the local clone."""
    pokemon_dir = assets_root / ASSETS_POKEMON_SUBPATH
    if not pokemon_dir.exists():
        print(f"ERROR: {pokemon_dir} does not exist.")
        sys.exit(1)

    results = list(pokemon_dir.rglob("*_alpha.png"))
    print(f"  Found {len(results)} alpha textures in local clone.")
    return results


# ---------------------------------------------------------------------------
# GitLab API discovery + download
# ---------------------------------------------------------------------------

def gitlab_list_alpha_files() -> list[str]:
    """
    List all *_alpha.png paths under blockbench/pokemon/ via GitLab API.
    Handles pagination automatically.
    """
    print("  Querying GitLab API for alpha textures (this may take a moment)...")
    all_files = []
    page = 1

    while True:
        url = (
            f"{GITLAB_TREE_BASE}"
            f"?path={ASSETS_POKEMON_SUBPATH}"
            f"&recursive=true"
            f"&ref={GITLAB_BRANCH}"
            f"&per_page=100"
            f"&page={page}"
        )
        try:
            with urllib.request.urlopen(url) as r:
                data = json.load(r)
        except Exception as e:
            print(f"  ERROR querying GitLab API: {e}")
            sys.exit(1)

        if not data:
            break

        for item in data:
            if item.get("type") == "blob" and item["path"].endswith("_alpha.png"):
                all_files.append(item["path"])

        page += 1

    print(f"  Found {len(all_files)} alpha textures via API.")
    return all_files


def gitlab_download_file(repo_path: str, dest: Path):
    """Download a single file from GitLab raw API."""
    encoded_path = repo_path.replace("/", "%2F")
    url = f"{GITLAB_RAW_BASE}/{encoded_path}/raw?ref={GITLAB_BRANCH}"
    try:
        with urllib.request.urlopen(url) as r:
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_bytes(r.read())
    except Exception as e:
        raise RuntimeError(f"Failed to download {repo_path}: {e}")


# ---------------------------------------------------------------------------
# Destination matching
# ---------------------------------------------------------------------------

def normalize(name: str) -> str:
    """
    Normalize a species name for loose matching:
      1. NFD decomposition strips diacritics (é -> e, etc.)
      2. Drop any remaining non-ASCII bytes
      3. Strip underscores and hyphens
    """
    nfd = unicodedata.normalize("NFD", name)
    ascii_only = nfd.encode("ascii", "ignore").decode("ascii")
    return ascii_only.replace("_", "").replace("-", "")


def build_folder_map() -> dict[str, Path]:
    """
    Build a lookup of species_name -> folder path from TEXTURES_DIR.
    Stores both the raw name and the normalized form (no hyphens/underscores/diacritics).
    """
    folder_map = {}
    for folder in TEXTURES_DIR.iterdir():
        if not folder.is_dir():
            continue
        parts = folder.name.split("_", 1)
        if len(parts) == 2:
            raw_name = parts[1]
            folder_map[raw_name] = folder
            norm = normalize(raw_name)
            if norm != raw_name:
                folder_map[norm] = folder
    return folder_map


def _try_candidate(candidate: str, folder_map: dict[str, Path]) -> Path | None:
    """Try a candidate string against the folder map, raw then normalized."""
    if candidate in folder_map:
        return folder_map[candidate]
    norm = normalize(candidate)
    if norm in folder_map:
        return folder_map[norm]
    return None


def find_dest_folder(filename_stem: str, folder_map: dict[str, Path]) -> Path | None:
    """
    Given a stem like 'charizard_alpha', find the matching NNNN_species folder.

    Resolution order:
      1. Apply ALIASES to the full species name if present.
      2. Strip trailing segments one at a time (handles form suffixes like _hisuian).
      3. Strip leading segments one at a time (handles prefix forms like ash_greninja).
      4. Each candidate is tried raw, then with Unicode/hyphen/underscore normalization.

    Examples:
      charizard_alpha         -> charizard          -> 0006_charizard
      zorua_hisuian_alpha     -> zorua_hisuian, zorua -> 0570_zorua
      ash_greninja_alpha      -> ash_greninja, ash (fail), greninja -> 0658_greninja
      ho_oh_alpha             -> ho_oh / hooh        -> 0250_hooh
      porygon-z_alpha         -> porygon-z / porygonz -> NNNN_porygonz
      flabébé_alpha           -> flabebe (NFD strip)  -> NNNN_flabebe
      basculeigon_alpha       -> alias -> basculegion -> NNNN_basculegion
    """
    if not filename_stem.endswith("_alpha"):
        return None

    full_species_name = filename_stem[:-len("_alpha")]

    # 1. Alias resolution
    if full_species_name in ALIASES:
        aliased = ALIASES[full_species_name]
        result = _try_candidate(aliased, folder_map)
        if result:
            return result

    segments = full_species_name.split("_")

    # 2. Strip trailing segments (longest match first)
    for i in range(len(segments), 0, -1):
        candidate = "_".join(segments[:i])
        result = _try_candidate(candidate, folder_map)
        if result:
            return result

    # 3. Strip leading segments (handles prefix-form names like ash_greninja)
    for i in range(1, len(segments)):
        candidate = "_".join(segments[i:])
        result = _try_candidate(candidate, folder_map)
        if result:
            return result

    return None


# ---------------------------------------------------------------------------
# Main processing
# ---------------------------------------------------------------------------

def process_local(assets_root: Path, overwrite: bool):
    alpha_files = find_alpha_textures_local(assets_root)
    folder_map = build_folder_map()

    stats = {"copied": 0, "skipped_exists": 0, "skipped_no_dest": 0, "errors": 0}
    no_dest = []

    for src_path in sorted(alpha_files):
        stem = src_path.stem
        filename = src_path.name

        dest_folder = find_dest_folder(stem, folder_map)
        if dest_folder is None:
            no_dest.append(stem)
            stats["skipped_no_dest"] += 1
            continue

        dest_file = dest_folder / filename

        if dest_file.exists() and not overwrite:
            stats["skipped_exists"] += 1
            continue

        try:
            shutil.copy2(src_path, dest_file)
            stats["copied"] += 1
            print(f"  COPIED: {filename} -> {dest_folder.name}/")
        except OSError as e:
            print(f"  ERROR copying {filename}: {e}")
            stats["errors"] += 1

    print_summary(stats, no_dest)


def process_api(overwrite: bool):
    alpha_paths = gitlab_list_alpha_files()
    folder_map = build_folder_map()

    stats = {"copied": 0, "skipped_exists": 0, "skipped_no_dest": 0, "errors": 0}
    no_dest = []

    for repo_path in sorted(alpha_paths):
        filename = Path(repo_path).name
        stem = Path(repo_path).stem

        dest_folder = find_dest_folder(stem, folder_map)
        if dest_folder is None:
            no_dest.append(stem)
            stats["skipped_no_dest"] += 1
            continue

        dest_file = dest_folder / filename

        if dest_file.exists() and not overwrite:
            stats["skipped_exists"] += 1
            continue

        try:
            gitlab_download_file(repo_path, dest_file)
            stats["copied"] += 1
            print(f"  DOWNLOADED: {filename} -> {dest_folder.name}/")
        except RuntimeError as e:
            print(f"  ERROR: {e}")
            stats["errors"] += 1

    print_summary(stats, no_dest)


def print_summary(stats: dict, no_dest: list):
    print()
    print("=" * 60)
    print("SUMMARY")
    print("=" * 60)
    print(f"  Copied/downloaded    : {stats['copied']}")
    print(f"  Skipped (exists)     : {stats['skipped_exists']}")
    print(f"  No matching dest     : {stats['skipped_no_dest']}")
    print(f"  Errors               : {stats['errors']}")

    if no_dest:
        print()
        print(f"No matching texture folder for ({len(no_dest)}):")
        for name in sorted(no_dest):
            print(f"  - {name}")


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

def main():
    print()
    print("Alpha Eye Texture Sync")
    print()

    if not TEXTURES_DIR.exists():
        print(f"ERROR: Cobblemon textures directory not found: {TEXTURES_DIR}")
        print("Is this script in the cobblemon/utilityscripts/alphas directory?")
        sys.exit(1)

    assets_root = prompt_assets_root()
    overwrite = prompt_overwrite()

    print()
    print("=" * 60)
    print("Processing")
    print("=" * 60)

    if assets_root is not None:
        print(f"  Mode: local clone ({assets_root})")
        process_local(assets_root, overwrite)
    else:
        print("  Mode: GitLab API")
        process_api(overwrite)


if __name__ == "__main__":
    main()