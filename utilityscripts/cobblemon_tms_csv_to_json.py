import requests
import csv
import json
import os
from io import StringIO

URL = "https://docs.google.com/spreadsheets/d/e/2PACX-1vRAViUitLFuY6Qo-3kFyY1sws1BQGqkSmSV6yyj66ilUmxomN_Ac6FfHSxxYE63t38aIPcTGRfg8eIC/pub?output=csv"

OUTPUT_DIR = "../common/src/main/resources/data/cobblemon/tms/"
os.makedirs(OUTPUT_DIR, exist_ok=True)

def parse_ingredient(cell):
    if not cell or cell.strip() == "":
        return None

    parts = cell.split(" x")
    raw_id = parts[0].strip()
    count = int(parts[1]) if len(parts) > 1 else 1

    # Tag support
    if raw_id.startswith("#"):
        return {
            "tag": raw_id[1:],  # remove '#'
            "count": count
        }
    else:
        return {
            "item": raw_id,
            "count": count
        }

response = requests.get(URL, timeout=10)
response.raise_for_status()

reader = csv.DictReader(StringIO(response.text))

for row in reader:
    recipe = []

    for col in ["Type Gems", "Additional Resource 1", "Additional Resource 2"]:
        parsed = parse_ingredient(row.get(col))
        if parsed:
            recipe.append(parsed)

    # Determine obtain method based on "Locked" column value
    column_b_value = row.get("Locked", "").strip().lower()
    if column_b_value == "locked":
        obtain_variant = "cobblemon:unlockable"
    else:
        obtain_variant = "cobblemon:default"

    tm = {
        "moveName": row["Showdown Move"].lower(),
        "obtainMethods": [
            {
                "variant": obtain_variant
            }
        ],
        "type": row["Type"].lower(),
        "recipe": recipe
    }

    filename = f"{tm['moveName']}.json"
    filepath = os.path.join(OUTPUT_DIR, filename)

    with open(filepath, "w", encoding="utf-8") as f:
        json.dump(tm, f, indent=2)

    print(f"Saved {filepath}")
