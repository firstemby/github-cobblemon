import os
import time
import requests
import pandas as pd
import json
from io import StringIO
from sqlalchemy import create_engine
from tqdm import tqdm
from scriptutils import printCobblemonHeader, print_cobblemon_script_description, print_cobblemon_script_footer, \
    print_list_filtered, print_warning, download_spreadsheet_data

trainer_preset = "cobblemon:trainer"

def main(dex_range=None):
    """
    The cobblemon_trainer_data_csvs_to_json script does the following:
    1. Retrieves the DataFrame containing the data for moveset builders, party pools, party arrangements, and trainer classes.
    2. Converts each into JSONs
    3. Writes the data into the appropriate files in the mod's data directory.
    """
    # Print header
    printCobblemonHeader()
    script_name = "Cobblemon Trainer Data Generator"
    script_description = "This script generates the trainer data for the mod based on the data in a Google Spreadsheet."
    print_cobblemon_script_description(script_name, script_description)

    update_moveset_builders()
    update_party_compositions()
    update_party_pools()
    update_npc_classes()

def load_data_from_csv(csv_data, skip_rows):
    return pd.read_csv(StringIO(csv_data), encoding='utf8', engine='python', skiprows=skip_rows, na_filter=False)

def update_moveset_builders():
    moveset_builders_spreadsheet_csv_url = 'https://docs.google.com/spreadsheets/d/e/2PACX-1vRrzepexgCvAMsvxhN_u_WV0VzIqcjJQb65Rtk-UhUtndRoXS-80lFlWgqLTHHThGMz3CqPrV8memOb/pub?gid=883883971&single=true&output=csv'
    moveset_builders_data_dir = '../common/src/main/resources/data/cobblemon/moveset_builders'
    # Download the CSV from the Google Spreadsheet
    csv_data = download_spreadsheet_data(moveset_builders_spreadsheet_csv_url)
    # Load the data into a DataFrame
    moveset_builders_df = load_data_from_csv(csv_data, 0)

    for index, row in moveset_builders_df.iterrows():
        id = row['Moveset ID']
        if (pd.isna(id) or id == ""):
            continue
        slot1 = row['Slot 1']
        slot2 = row['Slot 2']
        slot3 = row['Slot 3']
        slot4 = row['Slot 4']

        moveset_builder_json = {
            "type": "cobblemon:default",
            "slot1": slot1.split(', ') if pd.notna(slot1) else [],
            "slot2": slot2.split(', ') if pd.notna(slot2) else [],
            "slot3": slot3.split(', ') if pd.notna(slot3) else [],
            "slot4": slot4.split(', ') if pd.notna(slot4) else []
        }

        # Make file with name ${id}.json inside moveset_builders_data_dir

        with open(f'{moveset_builders_data_dir}/{id}.json', 'w') as f:
            import re
            json_str = json.dumps(moveset_builder_json, indent=2)
            json_str = re.sub(r'\[\n\s+', r'[', json_str)
            json_str = re.sub(r'\n\s+\]', r']', json_str)
            json_str = re.sub(r',\n\s+(?="[^"]*"\s*[,\]])', r', ', json_str)
            f.write(json_str)

    print("Moveset builders updated successfully!")

def update_party_compositions():
    party_compositions_spreadsheet_csv_url = 'https://docs.google.com/spreadsheets/d/e/2PACX-1vRrzepexgCvAMsvxhN_u_WV0VzIqcjJQb65Rtk-UhUtndRoXS-80lFlWgqLTHHThGMz3CqPrV8memOb/pub?gid=1855392430&single=true&output=csv'
    party_compositions_data_dir = '../common/src/main/resources/data/cobblemon/party_compositions'
    # Download the CSV from the Google Spreadsheet
    csv_data = download_spreadsheet_data(party_compositions_spreadsheet_csv_url)
    # Load the data into a DataFrame
    party_compositions_df = load_data_from_csv(csv_data, 1)

    for index, row in party_compositions_df.iterrows():
        # print all keys in the row
        id = row['Composition ID']
        if (pd.isna(id) or id == ""):
            continue
        scrambled_order = row['Scrambled Order']
        slot1 = row['Slot 1']
        slot2 = row['Slot 2']
        slot3 = row['Slot 3']
        slot4 = row['Slot 4']
        slot5 = row['Slot 5']
        slot6 = row['Slot 6']

        composition_json = {
            "scrambleOrder": scrambled_order,
            "slot1": slot1.split(', ') if pd.notna(slot1) else [],
            "slot2": slot2.split(', ') if pd.notna(slot2) else [],
            "slot3": slot3.split(', ') if pd.notna(slot3) else [],
            "slot4": slot4.split(', ') if pd.notna(slot4) else [],
            "slot5": slot5.split(', ') if pd.notna(slot5) else [],
            "slot6": slot6.split(', ') if pd.notna(slot6) else []
        }


        # Make file with name ${id}.json inside party_compositions_data_dir

        with open(f'{party_compositions_data_dir}/{id}.json', 'w') as f:
            import re
            json_str = json.dumps(composition_json, indent=2)
            json_str = re.sub(r'\[\n\s+', r'[', json_str)
            json_str = re.sub(r'\n\s+\]', r']', json_str)
            json_str = re.sub(r',\n\s+(?="[^"]*"\s*[,\]])', r', ', json_str)
            f.write(json_str)

    print("Party compositions updated successfully!")

def update_party_pools():
    party_pools_spreadsheet_csv_url = 'https://docs.google.com/spreadsheets/d/e/2PACX-1vRrzepexgCvAMsvxhN_u_WV0VzIqcjJQb65Rtk-UhUtndRoXS-80lFlWgqLTHHThGMz3CqPrV8memOb/pub?gid=295609571&single=true&output=csv'
    party_pools_data_dir = '../common/src/main/resources/data/cobblemon/party_pools'
    # Download the CSV from the Google Spreadsheet
    csv_data = download_spreadsheet_data(party_pools_spreadsheet_csv_url)
    # Load the data into a DataFrame
    party_pools_df = load_data_from_csv(csv_data, 1)

    pools = {}

    for index, row in party_pools_df.iterrows():
        # print all keys in the row
        id = row['Pool ID']
        if (pd.isna(id) or id == ""):
            continue
        pokemon = row['Pokémon']
        movesets = row['Movesets']
        weight = row['Weight']
        max_times = row['Max Times']
        level_min = row['L.Min']
        level_max = row['L.Max']
        variation_min = row['V.Min']
        variation_max = row['V.Max']
        labels = row['Labels']
        required_labels = row['Required']
        incompatible_labels = row['Incompatible']

        entry = {
            "pokemon": pokemon,
            "labels": labels.split(', ') if labels != '' else [],
            "required": required_labels.split(', ') if required_labels != '' else [],
            "incompatible": incompatible_labels.split(', ') if incompatible_labels != '' else [],
            "movesetBuilders": movesets.split(', ') if movesets != '' else [],
            "weight": weight,
            "maxTimesSelectable": max_times,
            "npcLevels": f'{level_min}-{level_max}',
            "levelVariation": f'{variation_min}-{variation_max}'
        }

        if id not in pools:
            pools[id] = {
                "entries": []
            }
        pools[id]["entries"].append(entry)


    # Make file with name ${id}.json inside party_pools_data_dir
    # iterate over each key in pools, use its value to make the json file named by the key
    for id, pool_json in pools.items():
        pool_json["entries"] = sorted(pool_json["entries"], key=lambda x: x["weight"], reverse=True)

        with open(f'{party_pools_data_dir}/{id}.json', 'w') as f:
            import re
            json_str = json.dumps(pool_json, indent=2)
            json_str = re.sub(r'\[\n\s+', r'[', json_str)
            json_str = re.sub(r'\n\s+\]', r']', json_str)
            json_str = re.sub(r',\n\s+(?="[^"]*"\s*[,\]])', r', ', json_str)
            json_str = json_str.replace('[{', '[\n    {')
            json_str = json_str.replace('}]', '}\n  ]')
            f.write(json_str)

    print("Party pools updated successfully!")

def convert_to_resource_location(str):
    # if it contains : then return as is, otherwise prefix cobblemon:
    return str if ':' in str else f'cobblemon:{str}'

def update_npc_classes():
    trainer_classes_spreadsheet_csv_url = 'https://docs.google.com/spreadsheets/d/e/2PACX-1vRrzepexgCvAMsvxhN_u_WV0VzIqcjJQb65Rtk-UhUtndRoXS-80lFlWgqLTHHThGMz3CqPrV8memOb/pub?gid=0&single=true&output=csv'
    trainer_classes_data_dir = '../common/src/main/resources/data/cobblemon/npcs'
    # Download the CSV from the Google Spreadsheet
    csv_data = download_spreadsheet_data(trainer_classes_spreadsheet_csv_url)
    # Load the data into a DataFrame
    trainer_classes_df = load_data_from_csv(csv_data, 1)

    for index, row in trainer_classes_df.iterrows():
        # print all keys in the row
        id = row['Trainer Class Name'].lower().replace(' ', '_')
        if (pd.isna(id) or id == ""):
            continue
        hitbox = row['Hitbox']
        ai = row['AI'] # unusedish rn
        drops = row['Drops'] # unused rn
        composition = row['Composition']
        pool = row['Pool']
        min_party_size = row['Min Party Size']
        max_party_size = row['Max Party Size']

        # if there is an existing JSON with this name, read its contents in so we can modify it
        trainer_class_json = {
            "presets": [ trainer_preset ]
        }
        if os.path.exists(f'{trainer_classes_data_dir}/{id}.json'):
            with open(f'{trainer_classes_data_dir}/{id}.json', 'r') as f:
                trainer_class_json = json.load(f)
        trainer_class_json.update({
            "hitbox": hitbox,
#             "ai": ai,
#             "drops": drops,
            "party": {
                "type": "cobblemon:composed_pool",
                "composition": convert_to_resource_location(composition),
                "pool": convert_to_resource_location(pool),
                "isStatic": True,
                "useFixedRandom": False,
                "minPokemon": min_party_size,
                "maxPokemon": max_party_size
            }
        })

        with open(f'{trainer_classes_data_dir}/{id}.json', 'w') as f:
            f.write(json.dumps(trainer_class_json, indent=2))

    print("NPC classes updated successfully!")


if __name__ == "__main__":
    main()
