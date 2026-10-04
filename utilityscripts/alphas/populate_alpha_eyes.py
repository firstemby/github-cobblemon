#!/usr/bin/env python3
"""
Populates alpha_eyes layer entries into Cobblemon Pokemon resolver JSONs.

For each resolver JSON that:
  - Has a single file (not aggregated multi-file resolvers)
  - Does not already have an alpha_eyes layer in any variation

Adds a new variation entry:
{
  "aspects": ["alpha_eyes"],
  "layers": [{
    "name": "alpha_eyes",
    "texture": "cobblemon:textures/pokemon/NNNN_species/species_alpha.png",
    "emissive": true
  }]
}

Safe to re-run — skips any resolver that already has alpha_eyes.
"""

import json
import os
import sys
from pathlib import Path
from collections import defaultdict

SCRIPT_DIR    = Path(__file__).resolve().parent
REPO_ROOT     = SCRIPT_DIR.parent
RESOLVERS_DIR = REPO_ROOT / "common/src/main/resources/assets/cobblemon/bedrock/pokemon/resolvers"
TEXTURES_DIR  = REPO_ROOT / "common/src/main/resources/assets/cobblemon/textures/pokemon"


def get_species_name_from_resolver(data: dict) -> str | None:
    """Extract species short name from 'cobblemon:charizard' -> 'charizard'"""
    species = data.get("species", "")
    if ":" in species:
        return species.split(":", 1)[1]
    return species or None


def already_has_alpha_eyes(data: dict) -> bool:
    for variation in data.get("variations", []):
        layers = variation.get("layers") or []
        for layer in layers:
            if layer.get("name") == "alpha_eyes":
                return True
        # Also check aspects list for the variation itself
        if "alpha_eyes" in variation.get("aspects", []):
            return True
    return False


def find_texture_folder(species_name: str) -> Path | None:
    """Find the NNNN_speciesname folder for a given species name."""
    if not TEXTURES_DIR.exists():
        return None
    for folder in TEXTURES_DIR.iterdir():
        if not folder.is_dir():
            continue
        # Folder name format: NNNN_speciesname
        parts = folder.name.split("_", 1)
        if len(parts) == 2 and parts[1] == species_name:
            return folder
    return None


def build_alpha_variation(folder_name: str, species_name: str) -> dict:
    texture_path = f"cobblemon:textures/pokemon/{folder_name}/{species_name}_alpha.png"
    return {
        "aspects": ["alpha_eyes"],
        "layers": [
            {
                "name": "alpha_eyes",
                "texture": texture_path,
                "emissive": True
            }
        ]
    }


def find_resolver_files() -> dict[str, list[Path]]:
    """
    Returns a dict mapping species identifier -> list of resolver files for that species.
    Species with multiple files are aggregated resolvers — we skip those.
    """
    if not RESOLVERS_DIR.exists():
        print(f"ERROR: Resolvers directory not found: {RESOLVERS_DIR}")
        sys.exit(1)

    species_to_files = defaultdict(list)

    for json_file in RESOLVERS_DIR.rglob("*.json"):
        try:
            with open(json_file, "r", encoding="utf-8") as f:
                data = json.load(f)
        except (json.JSONDecodeError, OSError) as e:
            print(f"  WARN: Could not read {json_file}: {e}")
            continue

        species = data.get("species") or data.get("name") or data.get("pokeball")
        if not species:
            print(f"  WARN: No species field in {json_file}, skipping")
            continue

        species_to_files[species].append(json_file)

    return dict(species_to_files)


def main():
    print(f"Resolvers dir : {RESOLVERS_DIR}")
    print(f"Textures dir  : {TEXTURES_DIR}")
    print()

    species_map = find_resolver_files()
    print(f"Found {len(species_map)} unique species across {sum(len(v) for v in species_map.values())} resolver files\n")

    stats = {
        "skipped_already_has": 0,
        "skipped_no_texture_folder": 0,
        "updated": 0,
        "errors": 0,
    }

    updated_files = []
    skipped_no_texture = []

    for species_id, files in sorted(species_map.items()):

        # Load all files for this species
        loaded = []
        load_error = False
        for json_file in files:
            try:
                with open(json_file, "r", encoding="utf-8") as f:
                    data = json.load(f)
                loaded.append((json_file, data))
            except (json.JSONDecodeError, OSError) as e:
                print(f"  ERROR reading {json_file}: {e}")
                stats["errors"] += 1
                load_error = True
        if load_error:
            continue

        # Skip if any file in the group already has alpha_eyes
        if any(already_has_alpha_eyes(data) for _, data in loaded):
            stats["skipped_already_has"] += 1
            continue

        # Pick the lowest order file as the target
        # Sort by the 'order' field, defaulting to 0 if missing
        loaded_sorted = sorted(loaded, key=lambda x: x[1].get("order", 0))
        json_file, data = loaded_sorted[0]

        if len(files) > 1:
            print(f"  MULTI-FILE ({len(files)} files): {species_id} — targeting order={data.get('order', 0)} ({json_file.name})")

        species_name = get_species_name_from_resolver(data)
        if not species_name:
            print(f"  ERROR: Could not determine species name from {json_file}")
            stats["errors"] += 1
            continue

        # Find texture folder
        texture_folder = find_texture_folder(species_name)
        if texture_folder is None:
            skipped_no_texture.append(species_name)
            stats["skipped_no_texture_folder"] += 1
            continue

        # Build and append the alpha_eyes variation
        alpha_variation = build_alpha_variation(texture_folder.name, species_name)
        data["variations"].append(alpha_variation)

        # Write back with consistent formatting
        try:
            with open(json_file, "w", encoding="utf-8", newline="\n") as f:
                json.dump(data, f, indent=2, ensure_ascii=False)
                f.write("\n")
            stats["updated"] += 1
            updated_files.append((species_name, json_file))
            print(f"  UPDATED: {species_name} -> {texture_folder.name}/{species_name}_alpha.png")
        except OSError as e:
            print(f"  ERROR writing {json_file}: {e}")
            stats["errors"] += 1

    # Summary
    print()
    print("=" * 60)
    print("SUMMARY")
    print("=" * 60)
    print(f"  Updated              : {stats['updated']}")
    print(f"  Already had alpha    : {stats['skipped_already_has']}")
    print(f"  No texture folder    : {stats['skipped_no_texture_folder']}")
    print(f"  Errors               : {stats['errors']}")

    if skipped_no_texture:
        print()
        print(f"Species with no matching texture folder ({len(skipped_no_texture)}):")
        for name in sorted(skipped_no_texture):
            print(f"  - {name}")


if __name__ == "__main__":
    main()
