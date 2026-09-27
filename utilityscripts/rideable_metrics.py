#!/usr/bin/env python3
"""
Extracts hitbox/scale metrics from ALL Cobblemon species JSONs.
Outputs a sorted .xlsx with effective volume, alpha scaling, and ride status.

Ride Status:
  RIDEABLE          (green)  — has riding with seats and non-null behaviour
  LARGE ENOUGH      (yellow) — not rideable, but base eff volume >= threshold
  ALPHA LARGE ENOUGH (red)   — not rideable, base too small, alpha volume >= threshold
  TOO SMALL         (black)  — neither meets threshold

Threshold is configurable in cell B1 of the sheet (default: 1.0, Skiddo's eff volume).

Usage:
    python rideable_metrics.py

Expects to live in cobblemon/scripts/ (sibling to common/).
Output: rideable_pokemon_metrics.xlsx in the script's directory.
"""

import json
import sys
from pathlib import Path
from openpyxl import Workbook
from openpyxl.styles import Font, PatternFill, Alignment, Border, Side
from openpyxl.utils import get_column_letter

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent
SPECIES_DIR = REPO_ROOT / "common/src/main/resources/data/cobblemon/species"

DEFAULT_THRESHOLD = 1.0  # Skiddo's approximate eff volume


def get_alpha_scale_multiplier(hitbox_width, hitbox_height, base_scale):
    base_hitbox_size = max(hitbox_width, hitbox_height) * base_scale
    coerced = max(0.25, min(base_hitbox_size, 5.0))
    return 1.1 + (0.8 * (0.5 ** coerced))


def has_valid_riding(data):
    """True if species has riding with at least one seat and non-null behaviour."""
    riding = data.get("riding")
    if not riding:
        return False
    seats = riding.get("seats")
    if not isinstance(seats, list) or len(seats) == 0:
        return False
    # Must have non-null behaviour somewhere
    behaviours = riding.get("behaviours")
    behaviour = riding.get("behaviour")
    if behaviours and isinstance(behaviours, dict) and len(behaviours) > 0:
        return True
    if behaviour is not None and isinstance(behaviour, dict) and len(behaviour) > 0:
        return True
    return False


def process_species_file(filepath):
    try:
        with open(filepath, "r", encoding="utf-8") as f:
            data = json.load(f)
    except (json.JSONDecodeError, OSError):
        return None

    name = data.get("name", filepath.stem)
    hitbox = data.get("hitbox", {})
    if isinstance(hitbox, str):
        if hitbox == "player":
            width, height = 0.6, 1.8
        else:
            return None
    elif isinstance(hitbox, dict):
        width = hitbox.get("width", 0.6)
        height = hitbox.get("height", 1.8)
    else:
        return None

    base_scale = data.get("baseScale", 1.0)
    eff_width = width * base_scale
    eff_height = height * base_scale
    eff_volume = eff_width * eff_width * eff_height

    alpha_mult = get_alpha_scale_multiplier(width, height, base_scale)
    alpha_eff_width = eff_width * alpha_mult
    alpha_eff_height = eff_height * alpha_mult
    alpha_volume = alpha_eff_width * alpha_eff_width * alpha_eff_height

    rideable = has_valid_riding(data)

    return {
        "name": name,
        "eff_volume": round(eff_volume, 4),
        "alpha_multiplier": round(alpha_mult, 4),
        "alpha_volume": round(alpha_volume, 4),
        "rideable": rideable,
    }


def determine_status(entry, threshold):
    if entry["rideable"]:
        return "RIDEABLE"
    if entry["eff_volume"] >= threshold:
        return "LARGE ENOUGH"
    if entry["alpha_volume"] >= threshold:
        return "ALPHA LARGE ENOUGH"
    return "TOO SMALL"


STATUS_FILLS = {
    "RIDEABLE": PatternFill("solid", fgColor="C6EFCE"),
    "LARGE ENOUGH": PatternFill("solid", fgColor="FFEB9C"),
    "ALPHA LARGE ENOUGH": PatternFill("solid", fgColor="FFC7CE"),
    "TOO SMALL": PatternFill("solid", fgColor="D9D9D9"),
}

STATUS_FONTS = {
    "RIDEABLE": Font(name="Arial", size=10, color="006100"),
    "LARGE ENOUGH": Font(name="Arial", size=10, color="9C6500"),
    "ALPHA LARGE ENOUGH": Font(name="Arial", size=10, color="9C0006"),
    "TOO SMALL": Font(name="Arial", size=10, color="404040"),
}


def build_xlsx(entries, output_path):
    wb = Workbook()
    ws = wb.active
    ws.title = "Pokemon Metrics"

    # --- Threshold config row ---
    ws.cell(row=1, column=1, value="Eff Volume Threshold:").font = Font(name="Arial", bold=True, size=10)
    threshold_cell = ws.cell(row=1, column=2, value=DEFAULT_THRESHOLD)
    threshold_cell.font = Font(name="Arial", bold=True, size=10, color="0000FF")
    threshold_cell.number_format = "0.0000"

    # Legend
    ws.cell(row=1, column=4, value="Legend:").font = Font(name="Arial", bold=True, size=10)
    legend = [
        ("RIDEABLE", "C6EFCE", "006100"),
        ("LARGE ENOUGH", "FFEB9C", "9C6500"),
        ("ALPHA LARGE ENOUGH", "FFC7CE", "9C0006"),
        ("TOO SMALL", "D9D9D9", "404040"),
    ]
    for i, (label, bg, fg) in enumerate(legend):
        c = ws.cell(row=1, column=5 + i, value=label)
        c.font = Font(name="Arial", size=9, color=fg)
        c.fill = PatternFill("solid", fgColor=bg)

    # --- Data headers at row 3 ---
    HEADER_ROW = 3
    headers = [
        ("Species", 20),
        ("Eff Volume", 12),
        ("Alpha Mult", 12),
        ("Alpha Volume", 13),
        ("Ride Status", 20),
    ]

    header_font = Font(name="Arial", bold=True, color="FFFFFF", size=10)
    header_fill = PatternFill("solid", fgColor="2F5496")
    header_align = Alignment(horizontal="center", vertical="center", wrap_text=True)
    thin_border = Border(
        left=Side(style="thin", color="D0D0D0"),
        right=Side(style="thin", color="D0D0D0"),
        top=Side(style="thin", color="D0D0D0"),
        bottom=Side(style="thin", color="D0D0D0"),
    )

    for col_idx, (h, w) in enumerate(headers, 1):
        cell = ws.cell(row=HEADER_ROW, column=col_idx, value=h)
        cell.font = header_font
        cell.fill = header_fill
        cell.alignment = header_align
        cell.border = thin_border
        ws.column_dimensions[get_column_letter(col_idx)].width = w

    ws.row_dimensions[HEADER_ROW].height = 25
    ws.auto_filter.ref = f"A{HEADER_ROW}:E{HEADER_ROW}"
    ws.freeze_panes = f"A{HEADER_ROW + 1}"

    # Sort by eff volume descending
    entries.sort(key=lambda e: e["eff_volume"], reverse=True)

    # Determine status for each entry
    for entry in entries:
        entry["status"] = determine_status(entry, DEFAULT_THRESHOLD)

    data_font = Font(name="Arial", size=10)
    alt_fill = PatternFill("solid", fgColor="EDF2F9")

    for row_idx, entry in enumerate(entries, HEADER_ROW + 1):
        base_fill = alt_fill if (row_idx - HEADER_ROW) % 2 == 0 else PatternFill()

        # Species
        c = ws.cell(row=row_idx, column=1, value=entry["name"])
        c.font = data_font
        c.border = thin_border
        c.fill = base_fill

        # Eff Volume
        c = ws.cell(row=row_idx, column=2, value=entry["eff_volume"])
        c.font = data_font
        c.border = thin_border
        c.fill = base_fill
        c.number_format = "0.0000"
        c.alignment = Alignment(horizontal="right", vertical="center")

        # Alpha Mult
        c = ws.cell(row=row_idx, column=3, value=entry["alpha_multiplier"])
        c.font = data_font
        c.border = thin_border
        c.fill = base_fill
        c.number_format = "0.0000"
        c.alignment = Alignment(horizontal="right", vertical="center")

        # Alpha Volume
        c = ws.cell(row=row_idx, column=4, value=entry["alpha_volume"])
        c.font = data_font
        c.border = thin_border
        c.fill = base_fill
        c.number_format = "0.0000"
        c.alignment = Alignment(horizontal="right", vertical="center")

        # Ride Status (colored)
        status = entry["status"]
        c = ws.cell(row=row_idx, column=5, value=status)
        c.font = STATUS_FONTS[status]
        c.fill = STATUS_FILLS[status]
        c.border = thin_border
        c.alignment = Alignment(horizontal="center", vertical="center")

    wb.save(output_path)
    print(f"Saved {len(entries)} Pokemon to {output_path}")

    # Print summary
    from collections import Counter
    counts = Counter(e["status"] for e in entries)
    print(f"  RIDEABLE:           {counts.get('RIDEABLE', 0)}")
    print(f"  LARGE ENOUGH:       {counts.get('LARGE ENOUGH', 0)}")
    print(f"  ALPHA LARGE ENOUGH: {counts.get('ALPHA LARGE ENOUGH', 0)}")
    print(f"  TOO SMALL:          {counts.get('TOO SMALL', 0)}")


def main():
    if not SPECIES_DIR.exists():
        print(f"ERROR: Species directory not found: {SPECIES_DIR}")
        sys.exit(1)

    print(f"Species dir: {SPECIES_DIR}")
    entries = []

    for json_file in sorted(SPECIES_DIR.rglob("*.json")):
        result = process_species_file(json_file)
        if result:
            entries.append(result)

    print(f"Found {len(entries)} total Pokemon")
    output = SCRIPT_DIR / "rideable_pokemon_metrics.xlsx"
    build_xlsx(entries, output)


if __name__ == "__main__":
    main()
