import glob
import os
import time
import json
import zipfile
from io import BytesIO
import requests
import pandas as pd
from sqlalchemy import create_engine
import openpyxl  # required to read excel files
from tqdm import tqdm
from cobblemon_drops_csv_to_json import get_drops_df, parse_drops
from scriptutils import printCobblemonHeader, print_cobblemon_script_footer, print_cobblemon_script_description, \
    print_warning, print_list_filtered, sanitize_pokemon

pokemon_sheet_url = 'https://docs.google.com/spreadsheets/d/e/2PACX-1vSbr09QbGxmaQL9ZqQOMhSw2JE-QKnUdSGBIPxZOYYjyO1jslxhgCjAMQ6vq4yxFNbiNSbVM5tDdPsH/pub?gid=689189899&single=true&output=csv'
dictionary_sheet_url = 'https://docs.google.com/spreadsheets/d/e/2PACX-1vSbr09QbGxmaQL9ZqQOMhSw2JE-QKnUdSGBIPxZOYYjyO1jslxhgCjAMQ6vq4yxFNbiNSbVM5tDdPsH/pub?gid=1283339708&single=true&output=csv'
herds_sheet_url = 'https://docs.google.com/spreadsheets/d/e/2PACX-1vSbr09QbGxmaQL9ZqQOMhSw2JE-QKnUdSGBIPxZOYYjyO1jslxhgCjAMQ6vq4yxFNbiNSbVM5tDdPsH/pub?gid=1492622158&single=true&output=csv'
herd_pokemon_sheet_url = 'https://docs.google.com/spreadsheets/d/e/2PACX-1vSbr09QbGxmaQL9ZqQOMhSw2JE-QKnUdSGBIPxZOYYjyO1jslxhgCjAMQ6vq4yxFNbiNSbVM5tDdPsH/pub?gid=1665277602&single=true&output=csv'
habitats_sheet_url = 'https://docs.google.com/spreadsheets/d/e/2PACX-1vSbr09QbGxmaQL9ZqQOMhSw2JE-QKnUdSGBIPxZOYYjyO1jslxhgCjAMQ6vq4yxFNbiNSbVM5tDdPsH/pub?gid=261458803&single=true&output=csv'


# This script is used to convert the data from the cobblemon spawning spreadsheet into a json format
def init_filters():
    global pokemon_numbers, included_groups, known_contexts, bucket_mapping, included_generations
    # Define what kind of pokémon should be included, if nothing is specified (empty array), all will be included.
    # filter by number ranges (dex range)
    pokemon_numbers = range(1, 1200)
    # filter by group ['basic', 'boss', 'fossil']
    included_groups = ['basic', 'boss', 'fossil']
    # filter by context ['grounded', 'submerged', 'seafloor', 'surface', 'fishing']
    known_contexts = ['grounded', 'submerged', 'seafloor', 'surface', 'fishing']
    # filter by bucket ['common', 'uncommon', 'rare', 'ultra-rare']
    bucket_mapping = ['common', 'uncommon', 'rare', 'ultra-rare']
    # filter by generation
    included_generations = [1, 2, 3, 4, 5, 6, 7, 8, 9]


def ui_init_filters(pokemon_nrs_min, pokemon_nrs_max, included_grps, known_cntxts, bucket_map, cstm_files, cstm_dirs):
    global pokemon_numbers, included_groups, known_contexts, bucket_mapping, included_generations, custom_files, custom_dirs
    pokemon_numbers = range(pokemon_nrs_min, pokemon_nrs_max + 1)
    included_groups = included_grps
    known_contexts = known_cntxts
    bucket_mapping = bucket_map
    custom_files = cstm_files
    custom_dirs = cstm_dirs

    included_generations = []


# exclude the following forms - make them spawn, but don't put these tags in the name.
excluded_forms = []
# List of ignored biome identifiers
ignored_biomes = ['freshwater', 'preset', 'river']
# List of all currently known presets
preset_list = [
    'ancient_city',
    'derelict',
    'desert_pyramid',
    'end_city',
    'foliage',
    'illager_structures',
    'jungle_pyramid',
    'lava',
    'mansion',
    'natural',
    'nether_fossil',
    'nether_structures',
    'ocean_monument',
    'ocean_ruins',
    'pillager_outpost',
    'redstone',
    'ruined_portal',
    'salt',
    'stronghold',
    'trail_ruins',
    'treetop',
    'urban',
    'water',
    'webs',
    'wild'
]

# Initialize lists for the report
unknown_conditions = []
unknown_weight_multiplier_identifiers = []


def main(pokemon_data_dir, spawn_spreadsheet_path="", only_update_existing_files=False, ignore_filters=False):
    printCobblemonHeader()

    scriptName = "♥ Cobblemon Spawn CSV to JSON Script ♥"
    scriptDescription = "This script is used to convert the data from the cobblemon spawning spreadsheet into a json format. It also provides a report about any possible issues."

    print_cobblemon_script_description(scriptName, scriptDescription)

    if spawn_spreadsheet_path:
        if spawn_spreadsheet_path.endswith('.xlsx'):
            pokemon_df = pd.read_excel(spawn_spreadsheet_path, engine='openpyxl',
                                   dtype={'Pokémon': str, 'Entry': str, 'No.': int})
        elif spawn_spreadsheet_path.endswith('.csv'):
            pokemon_df = pd.read_csv(spawn_spreadsheet_path, dtype={'Pokémon': str, 'Entry': str, 'No.': int})
        else:
            print_warning("Invalid file format. Please provide a valid excel or csv file.")
            return
    else:
        # Download Excel file and convert it into csv format
        pokemon_df = download_excel_data(pokemon_sheet_url, {'Pokémon': str, 'Entry': str, 'No.': int})

    # Verify all biome tags are valid
    invalid_biome_tags = [] # verify_biome_tags() I don't know that this provides much value except to restrict the machines the script can run on
    # Print "Script is loading data..." message
    print("\nScript is loading data...")

    # Apply filters
    pokemon_df = validate_and_filter_data(pokemon_df, only_update_existing_files, ignore_filters)

    # alter the data in the b column to be a 4-digit number by prepending 0s if necessary
    pokemon_df['No.'] = pokemon_df['No.'].apply(lambda x: str(x).zfill(4))

    # add a column for the pokémon's id using generateID
    pokemon_df['id'] = pokemon_df.apply(lambda x: generateID(x['Pokémon'], x['Entry']), axis=1)

    # alter the data in the Pokémon column by using generateName
    pokemon_df['Pokémon'] = pokemon_df['Pokémon'].apply(generateName)

    # add the drops column to the pokemon_df
    drops_df = load_special_drops_data()

    # make the canSeeSky column lowercase
    pokemon_df['Sees Sky'] = pokemon_df['Sees Sky'].apply(lambda x: str(x).lower())

    # Creating the output directory if it doesn't exist
    os.makedirs(pokemon_data_dir, exist_ok=True)

    # Handle dataframes depending on the usage of the public template spreadsheet
    csvs_grouped_by_folder_and_or_file = []
    # Create two separate DataFrames: one for rows with 'folder' and 'file_name' and another for rows without them
    if {'Folder', 'File Name'}.issubset(pokemon_df.columns):
        pokemon_df_with_folder_or_file = pokemon_df.dropna(subset=['Folder', 'File Name'], how='all')
        # Group the DataFrame with 'folder' and 'file_name' by these columns
        if pokemon_df_with_folder_or_file['Folder'].isna().any():
            csv_folder_file = pokemon_df_with_folder_or_file[pokemon_df_with_folder_or_file['Folder'].isna()]
            csv_grouped_by_folder_file = csv_folder_file.groupby('File Name')
            csvs_grouped_by_folder_and_or_file.append(csv_grouped_by_folder_file)
        # If 'File Name' is NaN, group by 'Folder' only
        if pokemon_df_with_folder_or_file['File Name'].isna().any():
            csv_with_file_name = pokemon_df_with_folder_or_file[pokemon_df_with_folder_or_file['File Name'].isna()]
            csv_grouped_by_folder_and_no = csv_with_file_name.groupby(['Folder', 'No.'])
            csvs_grouped_by_folder_and_or_file.append(csv_grouped_by_folder_and_no)
        # If there are any entries where neither is NaN, group those by both
        if pokemon_df_with_folder_or_file[['Folder', 'File Name']].notna().all(axis=1).any():
            csv_folder_and_file = pokemon_df_with_folder_or_file.dropna(subset=['Folder', 'File Name'], how='any')
            csv_grouped_by_folder_and_file = csv_folder_and_file.groupby(['Folder', 'File Name'])
            csvs_grouped_by_folder_and_or_file.append(csv_grouped_by_folder_and_file)
        # Only fill the with_folder_file DataFrame with rows that have 'folder' and 'file_name' columns empty if both filters include ''
        if ('' in custom_files) and ('' in custom_dirs):
            pokemon_df_without_folder_file = pokemon_df[pokemon_df['Folder'].isna() & pokemon_df['File Name'].isna()]
            # Group the DataFrame without 'folder' and 'file_name' by dex number
            csv_grouped_without_folder_file = pokemon_df_without_folder_file.groupby('No.')
        else:
            csv_grouped_without_folder_file = pd.DataFrame()
    else:
        csv_grouped_without_folder_file = pokemon_df.groupby('No.')

    print_warning("Modifying files...")
    # Processing each Pokémon group and converting it to JSON
    try:
        dataframes_to_process = [(df, MERGED_TOGETHER) for df in csvs_grouped_by_folder_and_or_file]
        dataframes_to_process.append((csv_grouped_without_folder_file, 'Default Groupings'))

        for df, id_type in dataframes_to_process:
            print(df.head(10))
            for id_value, group in tqdm(df, desc=f"Processing {id_type} ...",
                                        bar_format='\033[92m' + '{l_bar}\033[0m{bar:58}\033[92m{r_bar}\033[0m',
                                        colour='blue'):

                # Determine the file_id based on the id_type
                if id_type == MERGED_TOGETHER:
                    file_id = group['No.'].iloc[0]
                    file_name = group['File Name'].iloc[0]
                    if pd.isna(file_name):
                        file_name = None
                else:
                    file_id = id_value
                    file_name = None
                pokemon_json = transform_pokemon_to_json(group, invalid_biome_tags, drops_df)
                # put files into folders if 'Folder' is not null
                if 'Folder' in group.columns and pd.notna(group['Folder'].iloc[0]):
                    save_json_to_file(pokemon_json, file_id, group['Pokémon'].iloc[0],
                                      os.path.join(pokemon_data_dir, group['Folder'].iloc[0]), file_name)
                else:
                    # Handle the case when 'Folder' is null
                    save_json_to_file(pokemon_json, file_id, group['Pokémon'].iloc[0], pokemon_data_dir, file_name)
    finally:
        print_report()

    process_habitats()

    # Save data to SQLite
    # print("Saving data to SQLite")
    # write_to_sqlite(pokemon_df, sqlite_db_name, sqlite_table_name)
    print_cobblemon_script_footer("Thanks for using the spawn csv to json script, provided to you by Waldleufer")

def process_habitats():
    print("Processing habitats...")

    # Habitat columns:
    # Habitat, No.,	Pokémon,	Entry,	Lv. Min,	Lv. Max,	Bucket,	Weight,	Phases,	Time,	Weather,	Context,	Light Min,	Light Max,	Modifiers
    # Habitat column is the key that should be used to group records into a single JSON file.
    habitats_df = download_excel_data(habitats_sheet_url, {'Habitat': str, 'Pokémon': str, 'Entry': str})#, 'No.': int})
    habitat_groups = habitats_df.groupby('Habitat')
    for habitat_name, group in habitat_groups:
        # create the json data
        json_data = {
            "name": f"cobblemon.habitat.{habitat_name.replace(' ', '_').lower()}.name",
            "type": "cobblemon:natural",
            "spawns": []
        }
        for _, row in group.iterrows():
            if pd.isna(row['Pokémon']) or pd.isna(row['Entry']):
                continue
            spawn_data = {
                "species": row['Pokémon'],
                "bucket": row['Bucket'],
                "spawnablePositionType": row['Context'],
                "weight": row['Weight'],
                "levelRange": f"{int(row['Lv. Min'])}-{int(row['Lv. Max'])}",
                "modifiers": row['Modifiers'],
                "phases": row['Phases'],
                'timeRange': row['Time'],
                'minLight': row['Light Min'],
                'maxLight': row['Light Max']
            }
            if pd.isna(spawn_data['modifiers']):
                del spawn_data['modifiers']
            if row['Light Min'] == 'any':
                del spawn_data['minLight']
            if row['Light Max'] == 'any':
                del spawn_data['maxLight']
            if row['Time'] == 'any':
                del spawn_data['timeRange']
            if pd.isna(row['Phases']) or row['Phases'] == 'any':
                del spawn_data['phases']
            json_data['spawns'].append(spawn_data)
        if len(json_data['spawns']) == 0:
            continue
        # save the json data to a file
        file_name = f"{habitat_name.replace(' ', '_').lower()}.json"
        output_directory = '../common/src/main/resources/data/cobblemon/habitat_pools'
        file_path = os.path.join(output_directory, file_name)
        os.makedirs(output_directory, exist_ok=True)
        with open(file_path, 'w', encoding='utf-8') as file:
            json.dump(json_data, file, indent=2)


def print_report():
    if unknown_weight_multiplier_identifiers:
        print_warning("The following weight multiplier identifiers are unknown:")
        print_list_filtered(unknown_weight_multiplier_identifiers)
    if unknown_conditions:
        print_warning("The following conditions are unknown:")
        print_list_filtered(unknown_conditions)


def validate_and_filter_data(pokemon_df, only_update_existing_files=False, ignore_filters=False):
    # Data Validation
    # remove any rows that have a blank "No." column
    pokemon_df = pokemon_df[pokemon_df['No.'].notna()]
    # remove any rows that have a blank "Entry" column
    pokemon_df = pokemon_df[pokemon_df['Entry'].notna()]
    # remove any rows that have a blank "Pokémon" column
    pokemon_df = pokemon_df[pokemon_df['Pokémon'].notna()]
    # Filter the data
    if not ignore_filters:
        # Define a dictionary mapping filter variables to DataFrame column names
        filter_dict = {
            'pokemon_numbers': 'No.',
            'known_contexts': 'Context',
            'bucket_mapping': 'Bucket',
            'included_generations': 'Gen'
        }

        # Iterate over the dictionary and apply the filters
        for filter_var, column_name in filter_dict.items():
            filter_values = globals()[filter_var]
            if filter_values:
                # Replace '' with None in filter_values
                filter_values = [value if value != '' else None for value in filter_values]
                if None in filter_values:
                    pokemon_df = pokemon_df[pokemon_df[column_name].isin(filter_values) | pokemon_df[column_name].isnull()]
                else:
                    pokemon_df = pokemon_df[pokemon_df[column_name].isin(filter_values)]

                def is_continuous_range(lst):
                    try:
                        return lst == list(range(min(lst), max(lst)+1))
                    except Exception as e:
                        return False

                if is_continuous_range(filter_values):
                    print(f"Filtering by {filter_var}: ({min(filter_values)} - {max(filter_values)})")
                else:
                    print(f"Filtering by {filter_var}: {filter_values}")
    if only_update_existing_files:
        # extract the dex numbers from the filenames of the existing files
        existing_dex_numbers = [int(file.split('/')[-1].split('_')[0]) for file in os.listdir(default_pokemon_data_dir)]
        pokemon_df = pokemon_df[pokemon_df['No.'].isin(existing_dex_numbers)]
    return pokemon_df


def transform_pokemon_to_json(pokemon_rows, invalid_biome_tags, drops_df):
    json_data = {
        "enabled": True,
        "neededInstalledMods": [],
        "neededUninstalledMods": [],
        "spawns": []
    }

    for _, row in pokemon_rows.iterrows():
        try:
            currentPokemon = row['Pokémon']
            currentID = row['id']
            spawn_data = {
                "id": currentID,
                "pokemon": currentPokemon,
            }
            # Conditional fields
            if pd.notna(row['Presets']):
                spawn_data['presets'] = [preset.strip().lower() for preset in row['Presets'].split(',')]

            more_spawn_data = {
                "type": "pokemon",
                "spawnablePositionType": row['Context'],
                "bucket": row['Bucket'],
                "level": f"{int(row['Lv. Min'])}-{int(row['Lv. Max'])}",
                "weight": row['Weight'],
            }
            spawn_data.update(more_spawn_data)

            # Weight Multipliers field
            weight_multipliers = []
            if pd.notna(row['Multipliers']):
                # split weight multipliers row by comma, then split each string by "x" and add it to the weight_multipliers dictionary
                for string in str(row['Multipliers']).split(','):
                    string = string.strip()
                    if not string:  # skip empty strings
                        continue
                    items = string.split('x')
                    identifier = items[0].strip()
                    multiplier = float(items[1].strip())
                    # check if the identifier is a weather condition or night, and add it to the weight_multipliers dictionary
                    multiplier_condition = {}
                    match identifier:
                        case "Rain":
                            multiplier_condition["isRaining"] = True
                        case "Storm":
                            multiplier_condition["isThundering"] = True
                        case "Clear":
                            multiplier_condition["isRaining"] = False
                        case "Night":
                            multiplier_condition["timeRange"] = "night"
                        case "Day":
                            multiplier_condition["timeRange"] = "day"
                        case "Twilight":
                            multiplier_condition["timeRange"] = "twilight"
                        case "Dusk":
                            multiplier_condition["timeRange"] = "dusk"
                        case "Dawn":
                            multiplier_condition["timeRange"] = "dawn"
                        case "Beehive":
                            multiplier_condition["neededNearbyBlocks"] = ["#minecraft:beehives"]
                        case "Lava":
                            multiplier_condition["neededNearbyBlocks"] = ["#minecraft:lava"]
                        case "Water":
                            multiplier_condition["neededNearbyBlocks"] = ["#minecraft:water"]
                        case "Full Moon":
                            multiplier_condition["moonPhase"] = 0
                        case "New Moon":
                            multiplier_condition["moonPhase"] = 4
                        case "Wreck":
                            multiplier_condition["structures"] = ["#minecraft:shipwreck"]
                        case "Lure0":
                            multiplier_condition["minLureLevel"] = 0
                            multiplier_condition["maxLureLevel"] = 0
                        case "Lure0/1":
                            multiplier_condition["minLureLevel"] = 0
                            multiplier_condition["maxLureLevel"] = 1
                        case "Lure1/2":
                            multiplier_condition["minLureLevel"] = 1
                            multiplier_condition["maxLureLevel"] = 2
                        case "Lure2":
                            multiplier_condition["minLureLevel"] = 2
                            multiplier_condition["maxLureLevel"] = 2
                        case "Lure3":
                            multiplier_condition["minLureLevel"] = 3
                        case "MJ-Skel":
                            multiplier_condition["biomes"] = ["#cobblemon:is_spooky"]
                        case "MJ-Cal1":
                            multiplier_condition["biomes"] = ["#cobblemon:is_floral", "#cobblemon:is_mountain", "#cobblemon:is_freshwater"]
                        case "MJ-Cal2":
                            multiplier_condition["biomes"] = ["#cobblemon:is_temperate"]
                        case "MJ-Gld1":
                            multiplier_condition["biomes"] = ["#cobblemon:is_badlands"]
                        case "MJ-Gld2":
                            multiplier_condition["biomes"] = ["#cobblemon:is_island"]
                        case "MJ-Orn1":
                            multiplier_condition["biomes"] = ["#cobblemon:is_badlands", "#cobblemon:is_forest", "#cobblemon:is_island", "#cobblemon:is_savanna"]
                        case "MJ-Orn2":
                            multiplier_condition["biomes"] = ["#cobblemon:is_ocean", "#cobblemon:is_river"]
                        case "MJ-Pnk1":
                            multiplier_condition["biomes"] = ["#cobblemon:is_desert", "#cobblemon:is_magical", "#cobblemon:is_plains"]
                        case "MJ-Pnk2":
                            multiplier_condition["biomes"] = ["#cobblemon:is_floral", "#cobblemon:is_island"]
                        case "MJ-Gry1":
                            multiplier_condition["biomes"] = ["#cobblemon:is_river", "#cobblemon:is_snowy", "#cobblemon:is_tundra"]
                        case "MJ-Gry2":
                            multiplier_condition["biomes"] = ["#cobblemon:is_mountain", "#cobblemon:is_spooky"]
                        case "MJ-Pur1":
                            multiplier_condition["biomes"] = ["#cobblemon:is_floral", "#cobblemon:is_forest", "#cobblemon:is_magical", "#cobblemon:is_taiga"]
                        case "MJ-Pur2":
                            multiplier_condition["biomes"] = ["#cobblemon:is_swamp"]
                        case "MJ-Apc1":
                            multiplier_condition["biomes"] = ["#cobblemon:is_jungle", "#cobblemon:is_ocean", "#cobblemon:is_plains"]
                        case "MJ-Apc2":
                            multiplier_condition["biomes"] = ["#cobblemon:is_desert", "#cobblemon:is_savanna"]
                        case "MJ-Brw1":
                            multiplier_condition["biomes"] = ["#cobblemon:is_mountain", "#cobblemon:is_swamp", "#cobblemon:is_taiga"]
                        case "MJ-Brw2":
                            multiplier_condition["biomes"] = ["#cobblemon:is_badlands", "#cobblemon:is_jungle"]
                        case "MJ-White":
                            multiplier_condition["biomes"] = ["#cobblemon:is_desert", "#cobblemon:is_savanna", "#cobblemon:is_snowy", "#cobblemon:is_tundra"]
                        case "MJ-Bla1":
                            multiplier_condition["biomes"] = ["#cobblemon:is_badlands", "#cobblemon:is_spooky"]
                        case "MJ-Bla2":
                            multiplier_condition["biomes"] = ["#cobblemon:is_taiga"]
                        case "MJ-Blu1":
                            multiplier_condition["biomes"] = ["#cobblemon:is_island", "#cobblemon:is_ocean"]
                        case "MJ-Blu2":
                            multiplier_condition["biomes"] = ["#cobblemon:is_snowy", "#cobblemon:is_tundra"]
                        case "MJ-Vio1":
                            multiplier_condition["biomes"] = ["#cobblemon:is_jungle", "#cobblemon:is_spooky"]
                        case "MJ-Vio2":
                            multiplier_condition["biomes"] = ["#cobblemon:is_magical"]
                        case _:
                            unknown_weight_multiplier_identifiers.append(f"'{identifier}' ({currentID})")
                    weight_multipliers.append({"multiplier": multiplier, "condition": multiplier_condition})
                if len(weight_multipliers) == 1:
                    spawn_data['weightMultiplier'] = weight_multipliers[0]
                elif len(weight_multipliers) > 1:
                    spawn_data['weightMultipliers'] = weight_multipliers

            # Handle Conditions that have their own columns
            condition = handle_common_conditions('Conditions', currentID, invalid_biome_tags, row, spawn_data)
            # Handle Conditions and Anticonditions columns
            spawn_data = special_conditions_anticonditions(condition, "Conditions", currentID, invalid_biome_tags, row,
                                                          spawn_data)
            spawn_data = special_conditions_anticonditions({}, 'Anticonditions', currentID, invalid_biome_tags, row,
                                                          spawn_data)

            # Spawn specific drops field
            spawn_specific_drops = drops_df[drops_df['Pokémon'] == currentPokemon]["Spawn Specific Drops"]
            if not spawn_specific_drops.empty:
                # Split spawn specific drops by ;
                spawn_specific_drops = spawn_specific_drops.iloc[0].split(';')
                # Iterate through the spawn_specific_drops
                for drop in spawn_specific_drops:
                    # Split the string by = and strip each string
                    dropconditions, unprocessedDrops = drop.split('=')[0].strip(), drop.split('=')[1].strip()
                    for dropcondition in dropconditions.split(','):
                        if dropcondition.startswith("preset:"):
                            # split dropcondition by "," remove ":preset" and strip each string
                            presets = [drop.strip().replace("preset:", "").lower() for drop in dropcondition.split(',')]
                            # check if the dropcondition is in the preset_list, if not, then add it to the unknown_conditions list
                            for preset in presets:
                                if preset not in preset_list:
                                    print_warning(f"Unknown preset: {preset}")
                                # if the current entry's preset matches with the condition, then add the drops to the json_data
                                # check if the presets field is in the spawn_data
                                if "presets" in spawn_data and preset in spawn_data['presets']:
                                    spawn_data["drops"] = parse_drops(unprocessedDrops)
                        elif dropcondition.startswith("biome:"):
                            biomes = [drop.strip().replace("biome:", "").lower() for drop in dropcondition.split(',')]
                            for biome in biomes:
                                biome = biome.lower()
                                if biome not in biome_dictionary:
                                    print_warning(f"Unknown biome: {biome}")
                                # Apply the biome mapping to the condition
                                if biome in biome_dictionary:
                                    biome = biome_dictionary[biome]
                                # if the current entry's biome matches with the condition, then add the drops to the json_data
                                if biome in condition['biomes']:
                                    spawn_data["drops"] = parse_drops(unprocessedDrops)

            # Handle Pattern Key field and Append the spawn_data to the json_data
            if pd.notna(row['Pattern Key']):
                # if it does not contain = then raise an error
                if "=" not in str(row['Pattern Key']):
                    print(f"Pattern Key: {row['Pattern Key']}")
                    raise ValueError
                else:
                    # Initialize an empty array
                    specialSpawns = []
                    keyValuePairs = str(row['Pattern Key']).split(';')

                    for string in keyValuePairs:
                        key = string.split('=')[0].strip()
                        value = string.split('=')[1].strip()
                        # split the value by "," and strip each string, remove any empty strings
                        values = [v.strip() for v in value.split(',') if v.strip()]

                        if not specialSpawns:
                            for val in values:
                                copy = spawn_data.copy()
                                # Use val instead of value
                                copy['id'] = '-'.join(
                                    [spawn_data["pokemon"], val, copy['id'].split(spawn_data["pokemon"] + '-')[1]])
                                copy['pokemon'] += f" {key}={val}"
                                specialSpawns.append(copy)
                        else:
                            newSpecialSpawns = []
                            for spawn in specialSpawns:
                                for val in values:
                                    copy = spawn.copy()
                                    # Only add new key=value if it's not already in the pokemon field
                                    if f"{key}={val}" not in copy['pokemon']:
                                        copy['pokemon'] += f" {key}={val}"
                                    newSpecialSpawns.append(copy)
                            specialSpawns = newSpecialSpawns
                    # add the specialSpawns to the json_data
                    if specialSpawns:
                        json_data["spawns"] += specialSpawns
            else:
                json_data["spawns"].append(spawn_data)
        except Exception as e:
            print_warning(f"Error while processing {currentID}: {e}")
            raise e

    return json_data


def handle_common_conditions(column_name, currentID, invalid_biome_tags, row, spawn_data):
    condition = {}
    # Common Conditions that have their own columns

    # canSeeSky is different from SkyLight, in that it treats leaves and water of any thickness as fully transparent.
    if pd.notna(row['Sees Sky']) and row['Sees Sky'] == 'true':
        condition['canSeeSky'] = True
    elif row['Sees Sky'] == 'false':
        condition['canSeeSky'] = False

    # SkyLight
    if pd.notna(row['Sky Light Min']) or pd.notna(row['Sky Light Max']):
        # If one of them is empty, set them to 0 or 15 respectively
        if pd.isna(row['Sky Light Min']):
            row['Sky Light Min'] = 0
        if pd.isna(row['Sky Light Max']):
            row['Sky Light Max'] = 15
        # raise error if skyLightMin is greater than skyLightMax
        if int(row['Sky Light Min']) > int(row['Sky Light Max']):
            raise ValueError(f"Sky Light Min is greater than Sky Light Max in {currentID}")
        condition['minSkyLight'] = int(row['Sky Light Min'])
        condition['maxSkyLight'] = int(row['Sky Light Max'])

    # Biomes
    if pd.notna(row['Biomes']):
        parsed_biomes = parse_biomes(row['Biomes'], invalid_biome_tags)
        if parsed_biomes:
            condition['biomes'] = parsed_biomes

    # check if Time column is day, night, dawn, dusk, or any
    if pd.notna(row['Time']):
        if row['Time'].lower() == 'day':
            condition['timeRange'] = row['Time'].lower()
        elif row['Time'].lower() == 'night':
            condition['timeRange'] = row['Time'].lower()
        elif row['Time'].lower() == 'dawn':
            condition['timeRange'] = row['Time'].lower()
        elif row['Time'].lower() == 'dusk':
            condition['timeRange'] = row['Time'].lower()

    # check if Weather column is clear or rain or any
    if pd.notna(row['Weather']):
        if row['Weather'].lower() == 'clear':
            condition['isRaining'] = False
        elif row['Weather'].lower() == 'rain':
            condition['isRaining'] = True

    return condition


def special_conditions_anticonditions(condition, column_name, currentID, invalid_biome_tags, row, spawn_data):
    # More Rare Conditions (all contained in the Conditions or Anticonditions column)
    if pd.notna(row[column_name]):
        # split by "," and then strip each string
        strings = str(row[column_name]).split(';')
        for string in strings:
            key = string.split('=')[0].strip()
            value = string.split('=')[1].strip()
            # Json parse the value property
            value = json.loads(value)
            condition[key] = value
            continue
            s = string.strip()
            # if the string contains "structure:" then split it and add it to the condition dictionary under "structures"
            if "structure:" in s:
                if 'structures' not in condition:
                    condition['structures'] = []
                structure = s.split("structure:")[1].lower().strip()
                if ":" not in structure:
                    raise ValueError(
                        f"Invalid structure tag: {structure} in {currentID}. Please specify the full tag, including its prefix. (e.g. 'minecraft:desert_well')")
                condition['structures'].append(structure)
            # if the string contains ":" then use it as is and add it to the condition dictionary under "neededBaseBlocks"
            elif ":" in s:
                # split the string by " " and check if the first string is "on"
                if s.split(' ')[0] == "on":
                    if 'neededBaseBlocks' not in condition:
                        condition['neededBaseBlocks'] = []
                    condition['neededBaseBlocks'].append(s.split(' ')[1].strip())
                else:
                    if 'neededNearbyBlocks' not in condition:
                        condition['neededNearbyBlocks'] = []
                    condition['neededNearbyBlocks'].append(s)
            # if the string contains "minY = " then split it and add it to the condition dictionary
            elif "minY" in string:
                condition['minY'] = int(string.split('=')[1].strip())
            # if the string contains "maxY = " then split it and add it to the condition dictionary
            elif "maxY" in string:
                condition['maxY'] = int(string.split('=')[1].strip())
            # if the string contains "minX = " then split it and add it to the condition dictionary
            elif "minX" in string:
                condition['minX'] = int(string.split('=')[1].strip())
            # if the string contains "maxX = " then split it and add it to the condition dictionary
            elif "maxX" in string:
                condition['maxX'] = int(string.split('=')[1].strip())
            # if the string is "Full Moon" then add moonPhase = 0 to the condition dictionary
            elif string == "Full Moon":
                condition['moonPhase'] = "0"
            # if the string contains "moonPhase =" then split it and look at the next entries in strings that are a number
            elif "moonPhase = " in string:
                # get the first integer in the string
                digitsArray = [int(string.split('=')[1].strip())]
                # get the index of the current string
                index = strings.index(string)
                # get the next strings that are a number and add them to the digitsArray
                while index < len(strings) - 1:
                    index += 1
                    if strings[index].strip().isdigit():
                        digitsArray.append(int(strings[index].strip()))
                    else:
                        break
                # add the digitsArray to the condition dictionary in the format moonPhase = "1,2,4"
                condition['moonPhase'] = ','.join(str(x) for x in digitsArray)
            # if the string contains "maxLight = " then split it and add it to the condition dictionary
            elif "maxLight" in string:
                condition['maxLight'] = int(string.split('=')[1].strip())
            # if the string contains "minLight = " then split it and add it to the condition dictionary
            elif "minLight" in string:
                condition['minLight'] = int(string.split('=')[1].strip())
            elif "maxSkyLight" in string:
                condition['maxSkyLight'] = int(string.split('=')[1].strip())
            elif "minSkyLight" in string:
                condition['minSkyLight'] = int(string.split('=')[1].strip())
            # if the string is "timeRange  = dusk" then add it to the condition dictionary
            elif "timeRange  = dusk" in string:
                condition['timeRange'] = "dusk"
            # if the string is "Slime Chunk" then add isSlimeChunk = true to the condition dictionary
            elif "Slime Chunk" in string:
                condition['isSlimeChunk'] = True
            # if the string contains "minLureLevel = " then split it and add it to the condition dictionary
            elif "minLureLevel" in string:
                condition['minLureLevel'] = int(string.split('=')[1].strip())
            # if the string contains "maxLureLevel = " then split it and add it to the condition dictionary
            elif "maxLureLevel" in string:
                condition['maxLureLevel'] = int(string.split('=')[1].strip())
            # if the string contains "bobber = master_ball" then add it to the condition dictionary
            elif "bobber = master_ball" in string:
                condition['rodType'] = "cobblemon:master_rod"
            # if the string contains "bobber = love_ball" then add it to the condition dictionary
            elif "bobber = love_ball" in string:
                condition['rodType'] = "cobblemon:love_rod"
            # if the string contains "bait = love_sweet" then add it to the condition dictionary
            elif "bait = love_sweet" in string:
                condition['bait'] = "cobblemon:love_sweet"
            # if the string is a digit, pass (was already hanlded by other conditions)
            elif string.strip().isdigit():
                pass
            else:
                unknown_conditions.append(f"'{string}' ({currentID})")

    # Handle Excluded Biomes if currently handling the Anticonditions column
    if column_name == "Anticonditions":
        if pd.notna(row['Excluded Biomes']):
            parsed_biomes = parse_biomes(row['Excluded Biomes'], invalid_biome_tags)
            if parsed_biomes:
                condition['biomes'] = parsed_biomes

    # Do sanity checks for minLight, minSkyLight, minY and their respective max values
    if 'minLight' in condition and 'maxLight' in condition:
        if condition['minLight'] > condition['maxLight']:
            raise ValueError(f"minLight is greater than maxLight in {currentID}")
    if 'minSkyLight' in condition and 'maxSkyLight' in condition:
        if condition['minSkyLight'] > condition['maxSkyLight']:
            raise ValueError(f"minSkyLight is greater than maxSkyLight in {currentID}")
    if 'minY' in condition and 'maxY' in condition:
        if condition['minY'] > condition['maxY']:
            raise ValueError(f"minY is greater than maxY in {currentID}")

    # Add the condition to the spawn_data
    if condition:
        if column_name == "Anticonditions":
            spawn_data['anticondition'] = condition
        else:
            spawn_data['condition'] = condition
    return spawn_data


def reducePokemonJson(pokemon_json):
    """
    check pokemon_json for spawn entries, where everything ecxept biomes is the same
    if there are multiple entries, then combine them into one entry

    :param pokemon_json: the pokemon json to reduce
    :return: the reduced pokemon_json
    """
    # get the first spawn entry
    first_spawn_entry = pokemon_json["spawns"][0]
    # check if there are more than one spawn entry
    if len(pokemon_json["spawns"]) > 1:
        # loop through the rest of the spawn entries
        for spawn_entry in pokemon_json["spawns"][1:]:
            if len(spawn_entry.keys()) != len(first_spawn_entry.keys()):
                continue
            # check if the spawn entry is the same as the first spawn entry
            all_equal = all(
                spawn_entry[key] == first_spawn_entry[key] for key in spawn_entry.keys() if
                key not in ("condition", "id"))
            if all_equal:
                conditions = spawn_entry["condition"]
                firstCoditions = first_spawn_entry["condition"]
                if len(conditions.keys()) != len(firstCoditions.keys()):
                    continue
                all_same = all(conditions[key] == firstCoditions[key] for key in conditions.keys() if key != "biomes")
                if all_same:
                    # if it is the same, then add the biomes from the spawn entry to the first spawn entry
                    first_spawn_entry["condition"]["biomes"] += spawn_entry["condition"]["biomes"]
                    # remove the spawn entry from the pokemon_json
                    pokemon_json["spawns"].remove(spawn_entry)
    return pokemon_json


def generateID(pokemon_name, entry):
    # remove any ' from the pokemon name
    pokemon_name = pokemon_name.replace("'", "")
    # If the pokemon name contains any of the excluded forms, then remove them
    for form in excluded_forms:
        if form in pokemon_name:
            pokemon_name = pokemon_name.replace(form, "").strip().lower()

    # If the pokemon name contains [], then cut the string at the first space, add a dash,
    # the content between the brackets and add the entry
    if "[" in pokemon_name:
        return f"{sanitize_pokemon(pokemon_name.lower().split(' ')[0])}-{pokemon_name.lower().split('[')[1].split(']')[0]}-{entry}"
    return f"{sanitize_pokemon(pokemon_name)}-{entry}"


def generateName(pokemon_name):
    # If the pokemon name contains any of the excluded forms, then remove them
    for form in excluded_forms:
        if form in pokemon_name:
            pokemon_name = pokemon_name.replace(form, "").strip()
    # If the pokemon name contains [], then remove the []
    if "[" in pokemon_name:
        return f"{sanitize_pokemon(pokemon_name.lower().split(' ')[0])} {pokemon_name.lower().split(' [')[1].split(']')[0]}"
    return sanitize_pokemon(pokemon_name)


def parse_biomes(biomes_str, invalid_biome_tags):
    # Use the biome_dictionary to convert the Biome column to the format used in the spawn json
    biomes = []
    for biome in biomes_str.split(','):
        biome = biome.lower().strip()

        # Verify that the biome is not in the invalid_biome_tags list
        if invalid_biome_tags and biome_dictionary[biome] in invalid_biome_tags:
            print_warning(
                f"Used possibly invalid biome tag: {biome}\nThe wrong tag specified in biome_dictionary is: {biome_dictionary[biome]}")

        if biome in biome_dictionary:
            biomes.append(biome_dictionary[biome])
        elif biome in ignored_biomes:
            pass
        else:
            raise ValueError(
                f"Unknown biome: {biome}. To fix this error, add the biome to the biome_dictionary dictionary at the top of the script.")
    return biomes


def download_excel_data(spawn_spreadsheet_csv_url, dtypes, max_retries=5):
    '''Download a CSV file from a url and return the data as a DataFrame'''
    delay = 1
    for attempt in range(max_retries):
        try:
            response = requests.get(spawn_spreadsheet_csv_url, timeout=10)
            response.raise_for_status()
            return pd.read_csv(BytesIO(response.content), dtype=dtypes)
        except requests.RequestException as e:
            if attempt < max_retries - 1:
                time.sleep(delay)
                delay *= 2
            else:
                raise e

def save_json_to_file(json_data, file_id, pokemon_name, output_directory, file_name=None):
    print(f"Saving {pokemon_name} to {output_directory}")
    # Remove any ' from the pokemon name
    pokemon_name = pokemon_name.replace("'", "")
    # If the pokemon name contains a space, remove everything after the space (because of basculin)
    if " " in pokemon_name:
        pokemon_name = pokemon_name.split(" ")[0]
    if file_name:
        filename = f"{file_name}.json"
    else:
        filename = f"{str(file_id).zfill(4)}_{pokemon_name.lower()}.json"
    file_path = os.path.join(output_directory, filename)
    os.makedirs(output_directory, exist_ok=True)
    with open(file_path, 'w', encoding='utf-8') as file:
        json.dump(json_data, file, indent=4)
    return file_path


def load_special_drops_data():
    drops_df, _a, _b, _c = get_drops_df(pokemon_numbers)
    # delete all rows that have a blank "Spawn Specific Drops" column
    drops_df = drops_df[drops_df['Spawn Specific Drops'].notna()]
    # remove all columns except "Pokémon" and "Spawn Specific Drops"
    drops_df = drops_df[['Pokémon', 'Spawn Specific Drops']]
    # apply sanitize_pokemon to the "Pokémon" column
    drops_df['Pokémon'] = drops_df['Pokémon'].apply(sanitize_pokemon)
    return drops_df


def verify_biome_tags():
    # Define the directory path
    directory_path = '../common/src/main/resources/data/cobblemon/tags/worldgen/biome/'
    minecraft_biome_directory_path = '../.gradle/loom-cache/minecraftMaven/net/minecraft/'

    # Check if the biome directories exist and are not empty
    if not os.path.exists(minecraft_biome_directory_path) or not os.listdir(minecraft_biome_directory_path):
        print_warning(
            "Minecraft BiomeTags are currently not validated outside of a development environment with a completed gradle build.")

    # Check if the biome directories exist and are not empty
    if not os.path.exists(directory_path) or not os.listdir(directory_path):
        print_warning(
            "Cobblemon BiomeTags are currently not validated outside of a development environment")

    # get the list of all biomes by reading the filenames in the directory
    biome_files = []
    for root, dirs, files in os.walk(directory_path):
        for file in files:
            if root == directory_path:
                biome_files.append(file)
            else:
                # Get the directory name
                directory_name = os.path.basename(root)
                biome_files.append(f"{directory_name}/{file}")
    # remove the .json from the filenames and prepend "#cobblemon:" to the biome name
    biome_files = [f"#cobblemon:{file[:-5]}" for file in biome_files]

    # get the list of all minecraft biomes by reading the filenames in the directory
    minecraft_biomes = []
    # Construct the path pattern for JAR files
    jar_pattern = os.path.join("..", ".gradle", "loom-cache", "minecraftMaven", "net", "minecraft", "*",
                               "*", "*.jar")
    for jar_path in glob.glob(jar_pattern):
        with zipfile.ZipFile(jar_path, 'r') as jar:
            for file in jar.namelist():
                minecraft_biomes.append(file)

    unknown_biomes = []

    print_warning("The following biome tags can currently not be verified, please make sure they are correct:")
    for biome in biome_dictionary.values():
        # if biome starts with #cobblemon: then verify that it is in biome_files
        if biome.startswith("#cobblemon:"):
            if biome not in biome_files:
                unknown_biomes.append(biome)
        elif biome.startswith("minecraft:") or biome.startswith("#minecraft"):
            biome_name = biome.split(":")[1]
            if biome.startswith("minecraft:"):
                biome_path = f"data/minecraft/worldgen/biome/{biome_name}.json"
            else:  # starts with #minecraft
                biome_path = f"data/minecraft/tags/worldgen/biome/{biome_name}.json"
            # Check if the biome path is in minecraft_biomes
            if biome_path not in minecraft_biomes:
                unknown_biomes.append(biome)
        else:
            print(f"  Unverified biome tag: {biome}")
    if unknown_biomes:
        if unknown_biomes:
            print_warning("These biome tags are not implemented:")
            print_list_filtered(unknown_biomes)
        return unknown_biomes


def is_filepattern_in_jars(biome_file_pattern):
    # Construct the path pattern for JAR files
    jar_pattern = os.path.join("..", ".gradle", "loom-cache", "minecraftMaven", "net", "minecraft", "*",
                               "*", "*.jar")
    for jar_path in glob.glob(jar_pattern):
        with zipfile.ZipFile(jar_path, 'r') as jar:
            for file in jar.namelist():
                if biome_file_pattern in file:
                    return True
    return False


MERGED_TOGETHER = 'Custom Folders and Files'

# Load dictionary of terms
dictionary_df = download_excel_data(dictionary_sheet_url, {'Type': str, 'Term': str, 'String': str})
# Load mapping of biome, habitat, or multiplier based on the 'Type' column of the data.
biome_dictionary = dictionary_df[dictionary_df['Type'] == 'biome'][['Term', 'String']].set_index('Term')['String'].to_dict()
habitat_dictionary = dictionary_df[dictionary_df['Type'] == 'habitat'][['Term', 'String']].set_index('Term')['String'].to_dict()
multiplier_dictionary = dictionary_df[dictionary_df['Type'] == 'multiplier'][['Term', 'String']].set_index('Term')['String'].to_dict()
# Map keys of each dictionary to lowercase
biome_dictionary = {k.lower(): v for k, v in biome_dictionary.items()}
habitat_dictionary = {k.lower(): v for k, v in habitat_dictionary.items()}
multiplier_dictionary = {k.lower(): v for k, v in multiplier_dictionary.items()}

if __name__ == "__main__":
    init_filters()
    # Configuration data
    # Read excel url from .env in same directory as this script
    default_pokemon_data_dir = '../common/src/main/resources/data/cobblemon/spawn_pool_world'
    main(default_pokemon_data_dir, only_update_existing_files=False, ignore_filters=False)
