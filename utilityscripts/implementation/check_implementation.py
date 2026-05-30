import os
import json
import re
try:
    import msvcrt
    _has_msvcrt = True
except ImportError:
    _has_msvcrt = False
    print("Could not import msvcrt")
import pandas as pd
import soundfile as sf

# Quick class to make getch work on all systems
class Getch:
    def __init__(self):
        if _has_msvcrt:
            self.impl = self._getch_windows
        else:
            self.impl = self._getch_unix

    def _getch_windows(self):
        return msvcrt.getch()

    def _getch_unix(self):
        import sys, tty, termios
        fd = sys.stdin.fileno()
        old_settings = termios.tcgetattr(fd)
        try:
            tty.setraw(sys.stdin.fileno())
            ch = sys.stdin.read(1)
        finally:
            termios.tcsetattr(fd, termios.TCSADRAIN, old_settings)
        return ch.encode()

    def __call__(self): return self.impl()
getch = Getch()

# Colors weeeee
def print_color(color):
    if color == "reset": print("\033[0m", end="")
    if color == "red": print("\033[31m", end="")
    if color == "green": print("\033[32m", end="")
    if color == "yellow": print("\033[33m", end="")
    if color == "blue": print("\033[34m", end="")
    if color == "magenta": print("\033[35m", end="")
    if color == "cyan": print("\033[36m", end="")
    if color == "white": print("\033[37m", end="")

# Debug function
def debug(string):
    if (log_debug): print(f"\033[33m" + f"{string}" + "\033[0m")

# Directories
script_dir = os.path.dirname(os.path.abspath(__file__))
assets_dir_config = os.path.join(script_dir, "assets_directory.txt")

if os.path.exists(assets_dir_config):
    with open(assets_dir_config, "r") as f:
        assets_dir = os.path.normpath(f.read().strip().replace('"', ''))
else:
    assets_dir = os.path.join(script_dir, "../../../cobblemon-assets")
    if not os.path.exists(assets_dir):
        print("Could not find the assets directory!")
        print("The script assumes \"cobblemon-assets\" is cloned in the same folder as the main repository")
        print("\nFor example:")
        print(" Repository <-- (That's the folder with everything needed)")
        print("  \\ cobblemon <---- (This is the main mod with the script)")
        print("  \\ cobblemon-assets <---- (This is the assets repository)")
        print("\nIf your repository is elsewhere, create \"assets_directory.txt\" and paste in your path there")
        print(r"For example: \"D:\Dev\Cobblemon\Repository\cobblemon-assets\"")
        getch()
        quit()


## Script start
print(f"Press either of these keys to check Cobblemon assets implementation")
print(f"► [1] - Basic")
print(f"► [2] - Full")
print(f"► [4] - Debug")
print(f"► [Esc] Exit")
print(f"\n[1] Basic only shows critical issues that will be apparent to players in-game.")
print(f"Issues with Pokémon cries show only if a cry is made and should be implemented.")
print(f"Some sheet disparities will be shown if a Pokémon's implementation status is incorrect.")
print(f"\n[2] Full shows all issues, inconsistencies and disparities.")
print(f"Files are compared one to one with the assets repository.")
print(f"\nThis script is still in development, but can already be used.")
print(f"Report any false positives or issues and feel free to request any features.\n")
log_debug = False
# Set the operation
while True:
    input = getch()
    if input == b"1":
        mode = "basic"
        break
    elif input == b"2":
        mode = "full"
        break
    elif input == b"4":
        mode = "debug"
        log_debug = True
        break
    elif input == b'\x1b':
        quit()
debug("Debug enabled, missile inbound")
print(f"Found the Cobblemon assets repository at \"{assets_dir}\"")



## Spreadsheet
# Sheet IDs
sheet_id = "1_5TB7TYfItjYX_4ezQh9rEAWs8dv2oJOtXYYvYqg5Hg"
gid_implementation = 0
gid_cries = 1849522968
gid_models = 1841954062
# DataFrames
print(f"Fetching spreadsheets, this may take a few seconds...")
try: 
    df_models = pd.read_csv(f"https://docs.google.com/spreadsheets/d/{sheet_id}/export?format=csv&gid={gid_models}")
    df_implementation = pd.read_csv(f"https://docs.google.com/spreadsheets/d/{sheet_id}/export?format=csv&gid={gid_implementation}")
    df_cries = pd.read_csv(f"https://docs.google.com/spreadsheets/d/{sheet_id}/export?format=csv&gid={gid_cries}")
except:
    print("Failed to fetch spreadsheets, make sure the sheet identifier is correct.")
    getch()
    quit()
print("\n")

# Clear the name from special characters
def sanitize_name(name):
    # Lower case everything
    name = name.lower()
    # Edge cases
    name = name.replace("mr. mime", "mr_mime") # Mr. Mime
    name = name.replace("mr. rime", "mr_rime") # Mr. Rime
    name = name.replace("mr.rime", "mr_rime")  # Also Mr. Rime
    name = name.replace("mime jr.", "mime_jr")  # Also Mr. Rime
    name = re.sub(r"furfrou.*", "furfrou", name) # Furfrou
    # fefe
    name = name.replace(" [", "_")
    name = name.replace("]", "")
    # Turn signs into letters
    name = name.replace("♀", "f")
    name = name.replace("♂", "m")
    # Turn m and f into full words
    name = re.sub(r"_m$", "_male", name)
    name = re.sub(r"_f$", "_female", name)
    # éééééééééé
    name = name.replace("é", "e")
    name = name.replace("\'", "")
    name = name.replace("-", "_")
    name = name.replace(" ", "_")
    # Correct regional names
    name = name.replace("alola", "alolan")
    name = name.replace("galar", "galarian")
    name = name.replace("hisui", "hisuian")
    name = name.replace("paldea", "paldean")
    # We go
    return name

# Removes regions and special forms from the name
def default_name(name):
    # Remove special forms
    name = name.replace("_midday", "")
    name = name.replace("_midnight", "")
    name = name.replace("_dusk", "")
    # Remove regions from names
    name = name.replace("_alolan", "")
    name = name.replace("_galarian", "")
    name = name.replace("_hisuian", "")
    name = name.replace("_paldean", "")
    # Tauros stuff
    name = name.replace("paldeancombat", "")
    name = name.replace("paldeanblaze", "")
    name = name.replace("paldeanaqua", "")
    # Turn remove variants
    name = name.replace("_male", "")
    name = name.replace("_female", "")
    # Edge cases
    name = re.sub(r"unown.*", "unown", name) # Unown
    # We go
    return name

# This is used for unconventional names
def incorrect_region_name(name):
    name = name.replace("_alolan", "_alola")
    name = name.replace("_galarian", "_galar")
    name = name.replace("_hisuian", "_hisui")
    name = name.replace("_paldean", "_paldea")
    # We go
    return name

# Handle all edge cases with different uses for Pokémon names
def normalize_name(name, mode):
    pokemon = name

    ## DIRECTORY
    if mode == "directory":
        # Remove special forms
        name = name.replace("_midday", "")
        name = name.replace("_midnight", "")
        name = name.replace("_dusk", "")
        # Remove regions from names
        name = name.replace("_alolan", "")
        name = name.replace("_galarian", "")
        name = name.replace("_hisuian", "")
        name = name.replace("_paldean", "")
        # Remove variants
        if "unown" not in name: name = name.replace("_male", "")
        if "unown" not in name: name = name.replace("_female", "")
        # Unown
        name = re.sub(r"unown.*", "unown", name)
        # Toxtricity stuff
        name = name.replace("_amped", "")
        name = name.replace("_low_key", "")
        name = name.replace("_lowkey", "")
        # Tauros stuff
        if "tauros" in name: return "tauros"
        # Gimmighoul
        if "gimmighoul" in name: return "gimmighoul"
        # Shellos
        if "shellos" in pokemon: return "shellos"
        # Cherrim
        if "cherrim" in pokemon: return "cherrim"
        # Sinistcha and Sinistea
        if "sinistcha" in pokemon: return "sinistcha"
        if "sinistea" in pokemon: return "sinistea"
        # Remove underscores
        name = name.replace('_', '')
        # We go
        return name
    
    ## POSER
    if mode == "poser":
        # Bulbasaur line
        if "bulbasaur" in name: return "bulbasaur"
        if "ivysaur" in name: return "ivysaur"
        if "venusaur" in name: return "venusaur"
        # Unown
        if "unown" in pokemon:
            name = name.replace('?', 'question')
            name = name.replace('!', 'exclamation')
            return name
        # Mr. Mime and Mr. Rime
        if "mr_mime" in pokemon: return "mrmime"
        if "mr_rime" in pokemon: return "mrrime"
        # Toxtricity
        if "toxtricity_low_key" in pokemon: return "toxtricity_lowkey"
        # Gimmighoul Roaming without an underscore apparently
        if "gimmighoul_roaming" in pokemon: return "gimmighoulroaming"
        # Ho-oh
        if "ho_oh" in pokemon: return "hooh"
        # Milotic
        if "milotic_male" in pokemon: return "milotic"
        # Porygon-Z
        if "porygon_z" in pokemon: return "porygon-z"
        # Sinistcha and Sinistea
        if "sinistcha" in pokemon: return "sinistcha"
        if "sinistea" in pokemon: return "sinistea"
        # Rattata
        if "rattata" in pokemon: return "rattata"


    ## GEO
    if mode == "geo":
        # Unown
        if "unown" in pokemon: return re.sub(r"unown.*", "unown", name)
        # Toxtricity
        if "toxtricity_low_key" in pokemon: return "toxtricity_lowkey"
        # Shellos
        if "shellos" in pokemon: return "shellos"
        # Jangmo-o line and my depleting sanity
        if "jangmo" in pokemon: return "jangmo-o"
        if "hakamo" in pokemon: return "hakamo-o"
        if "kommo" in pokemon: return "kommo-o"
        # Unfezant, only male, because it's not named that on the sheet
        if pokemon == "unfezant": return "unfezant_male"
        # Milotic
        if "milotic" in pokemon: return "milotic"
        # Porygon-Z
        if "porygon_z" in pokemon: return "porygon-z"

    ## ANIMATION
    if mode == "animation":
        # Bulbasaur line
        if "bulbasaur" in name: return "bulbasaur"
        if "ivysaur" in name: return "ivysaur"
        if "venusaur" in name: return "venusaur"
        # Unown
        if "unown" in pokemon: return re.sub(r"unown.*", "unown", name)
        # Toxtricity
        if "toxtricity_low_key" in pokemon: return "toxtricity_lowkey"
        # Shellos
        if "shellos" in pokemon: return "shellos"
        # Jangmo-o line and my depleting sanity
        if "jangmo" in pokemon: return "jangmo-o"
        if "hakamo" in pokemon: return "hakamo-o"
        if "kommo" in pokemon: return "kommo-o"
        # Unfezant, only male, because it's not named that on the sheet
        if pokemon == "unfezant": return "unfezant_male"
        # Milotic
        if "milotic" in pokemon: return "milotic"
        # Porygon-Z
        if "porygon_z" in pokemon: return "porygon-z"
        # Sinistcha and Sinistea
        if "sinistcha" in pokemon: return "sinistcha"
        if "sinistea" in pokemon: return "sinistea"
        # Rattata
        if "rattata_male" in pokemon: return "rattata"
        if "rattata_female" in pokemon: return "rattata"
    
    ## CRY
    if mode == "cry":
        # Bulbasaur line
        if "bulbasaur" in name: return "bulbasaur"
        if "ivysaur" in name: return "ivysaur"
        if "venusaur" in name: return "venusaur"
        # Kadabra and Alakazam
        if "kadabra_" in pokemon: return "kadabra"
        if "alakazam_" in pokemon: return "alakazam"
        # Meowstic
        if "meowstic" in pokemon: return "meowstic"
        # Unown
        name = re.sub(r"unown.*", "unown", name)
        # Toxtricity
        if "toxtricity_low_key" in pokemon: return "toxtricity_lowkey"
        # Gimmighoul Roaming without an underscore apparently
        if "gimmighoul_roaming" in pokemon: return "gimmighoulroaming"
        # Shellos
        if "shellos" in pokemon: return "shellos"
        # Jangmo-o line
        if "jangmo" in pokemon: return "jangmoo"
        if "hakamo" in pokemon: return "hakamoo"
        if "kommo" in pokemon: return "kommoo"
        # Ho-oh
        if "ho_oh" in pokemon: return "hooh"
        # Unfezant, only male, because it's not named that on the sheet
        if "unfezant" in pokemon: return "unfezant"
        # Milotic
        if "milotic" in pokemon: return "milotic"
        # Pyroar
        if "pyroar" in pokemon: return "pyroar"
        # Porygon-Z
        if "porygon_z" in pokemon: return "porygonz"
        # Sinistcha and Sinistea
        if "sinistcha" in pokemon: return "sinistcha"
        if "sinistea" in pokemon: return "sinistea"
        # Rattata
        if "rattata_male" in pokemon: return "rattata"
        if "rattata_female" in pokemon: return "rattata"

    return name

def find_poser(name):
    path = os.path.join(script_dir, "../..", f"common/src/main/resources/assets/cobblemon/bedrock/pokemon/posers/")

    if not os.path.exists(path):
        debug("  Poser directory does not exist")
        return

    poser_name = f"{normalize_name(name,'poser')}.json"
    for root, dirs, files in os.walk(path):
        if poser_name in files:
            poser = os.path.join(root, poser_name)
            debug(f"  Found poser in \"{poser}\"")
            return poser

    poser_name = f"{incorrect_region_name(name)}.json"
    for root, dirs, files in os.walk(path):
        if poser_name in files:
            poser = os.path.join(root, poser_name)
            status["imp_unconventional_name"] = os.path.basename(poser_name)
            debug(f"  Found poser in \"{poser}\"")
            return poser
    
    path = os.path.join(script_dir, "../..", f"common/src/main/kotlin/com/cobblemon/mod/common/client/render/models/blockbench/pokemon/")
    poser_name = f"{name.title().replace('_Female','').replace('_Male','').replace('_', '')}Model.kt"
    for root, dirs, files in os.walk(path):
        if poser_name in files:
            poser = os.path.join(root, poser_name)
            status["imp_using_kotlin_poser"] = True
            debug(f"  Found poser in \"{poser}\"")
            return poser
        
    debug(f"  Could not find poser")
    return False

def find_animation(side, name):
    if side == "mod":
        path = os.path.join(script_dir, "../..", f"common/src/main/resources/assets/cobblemon/bedrock/pokemon/animations/")
        if not os.path.exists(path):
            debug("  Animation directory does not exist")
            return
    elif side == "repo":
        path = os.path.join(assets_dir, f"blockbench/pokemon/")
        if not os.path.exists(path):
            debug("  Repository directory does not exist")
            return

    animation_name = f"{normalize_name(name,'animation')}.animation.json"
    for root, dirs, files in os.walk(path):
        if animation_name in files:
            animation = os.path.join(root, animation_name)
            if side == "mod": debug(f"  Found mod animations in \"{animation}\"")
            elif side == "repo": debug(f"  Found assets repository animations in \"{animation}\"")
            return animation

    animation_name = f"{incorrect_region_name(name)}.animation.json"
    for root, dirs, files in os.walk(path):
        if animation_name in files:
            animation = os.path.join(root, animation_name)
            if side == "mod":
                status["ani_unconventional_name"] = os.path.basename(animation)
                debug(f"  Found unconventionally named mod animations in \"{animation}\"")
            elif side == "repo": debug(f"  Found unconventionally named assets repository animations in \"{animation}\"")
            return animation
        
    if side == "mod": status["ani_file_missing"] = True
    debug(f"  Could not find animation files")
    return False

def find_geo(side, name):
    if side == "mod":
        path = os.path.join(script_dir, "../..", f"common/src/main/resources/assets/cobblemon/bedrock/pokemon/models/")
        if not os.path.exists(path):
            debug("  Model directory does not exist")
            return
    elif side == "repo":
        path = os.path.join(assets_dir, f"blockbench/pokemon/")
        if not os.path.exists(path):
            debug("  Repository directory does not exist")
            return

    geo_name = f"{normalize_name(name,'geo')}.geo.json"
    for root, dirs, files in os.walk(path):
        if geo_name in files:
            geo = os.path.join(root, geo_name)
            if side == "mod": debug(f"  Found mod geo in \"{geo}\"")
            elif side == "repo": debug(f"  Found assets repository geo in \"{geo}\"")
            return geo

    geo_name = f"{incorrect_region_name(name)}.geo.json"
    for root, dirs, files in os.walk(path):
        if geo_name in files:
            geo = os.path.join(root, geo_name)
            if side == "mod":
                status["geo_unconventional_name"] = os.path.basename(geo)
                debug(f"  Found unconventionally named mod geo in \"{geo}\"")
            elif side == "repo": debug(f"  Found unconventionally named assets repository geo in \"{geo}\"")
            return geo
        
    if side == "mod": status["geo_file_missing"] = True
    debug(f"  Could not find geo files")
    return False


def check_implementation(status, name, dex, gen, model_status):
    debug(f" Checking Implementation for {name} ({sanitize_name(name)})")
    name = sanitize_name(name)
    dex = f"{dex:>04}"

    poser_path = find_poser(name)

    if not poser_path:
        if model_status == "Implemented":
            status["sht_erroneous_implemented_flag"] = True
            debug("  Pokémon is not implemented, but flagged as such on the assets sheet")
            return
        else:
            debug("  Not flagging the Pokémon as implemented, skipping!")
            return
    else:
        status["imp_implemented"] = True

    # Check implementation against the Models spreadsheet
    debug("  Checking if the implementation status on the Models spreadsheet matches...")
    if ("imp_implemented" in status) and (model_status != "Implemented"):
        if model_status == "Work in Progress": status["sht_erroneous_work_in_progress_flag"] = True
        elif model_status == "Not Started": status["sht_erroneous_not_started_flag"] = True
        elif model_status == "Needs Approval": status["sht_erroneous_needs_approval_flag"] = True
        elif model_status == "Needs Implementation": status["sht_erroneous_needs_implementation_flag"] = True
        elif model_status == "Needs Rework": status["sht_erroneous_needs_rework_flag"] = True
        elif model_status == "Needs Help": status["sht_erroneous_needs_help_flag"] = True
        debug("  Found paperwork mismatch")
    else: debug("  Implementation status and mod implementation match")


def scan_animation(status, name, gen, dex):
    debug(" Scanning the animations")
    name = sanitize_name(name)
    dex = f"{dex:>04}"
    
    if name in use_default_name: name = default_name(name)

    ani_mod_path = find_animation("mod", name)
    if not ani_mod_path: return
    ani_repo_path = find_animation("repo", name)
    if not ani_repo_path: return

    with open(ani_mod_path, "r") as file:
        ani_data = json.load(file)
    with open(ani_repo_path, "r") as file:
        repo_data = json.load(file)

    # Check for issues with the cry animation
    if status.get("ani_unconventional_name", False): cry_animation = ani_data.get("animations", {}).get(f"animation.{incorrect_region_name(normalize_name(name,'animation'))}.cry")
    else: cry_animation = ani_data.get("animations", {}).get(f"animation.{normalize_name(name,'animation')}.cry")

    debug(f"  Scanning animations for potential issues with the cry...")
    if (not cry_animation) or cry_animation == None:
        status["ani_cry_animation_missing"] = True
        debug(f"  Could not find the cry animation")
    else:
        cry_animation_sound_effects = cry_animation.get("sound_effects")
        if (not cry_animation_sound_effects) or cry_animation_sound_effects == None:
            status["ani_cry_keyframe_missing"] = True
            debug(f"  Could not find the cry keyframe")
        else:
            for key, value in cry_animation_sound_effects.items():
                if not isinstance(value, dict):
                    debug(f"   Omitting {key}")
                    continue
                effect = value.get("effect")
                if (not effect) or effect == None:
                    status["ani_cry_keyframe_missing"] = True
                    debug(f"  Could not find the cry keyframe in {cry_animation}")
                elif effect != f"pokemon.{normalize_name(name,'cry')}.cry":
                    status["ani_cry_keyframe_invalid"] = effect
                    debug(f"  Found an incorrectly named cry keyframe ({effect})")
                else: debug(f"  Found no issues with the cry implementation")

    # Check for wing flaps in flight animations
    air_animations = {
        air_ani_name: air_ani_content for air_ani_name, air_ani_content in ani_data.get("animations", {}).items()
        if air_ani_name.split('.')[-1] in flight_animations
    }

    if air_animations:
        debug(f"  Checking found air animations for wing flap sounds...")

        valid_wing_flaps = [
            f"animation.{t}.wing_flap.{s}".lower()
            for t in wing_flap_variants["textures"]
            for s in wing_flap_variants["sizes"]
        ]

        missing_wing_flaps = []
        incorrect_wing_flaps = []

        for air_ani_name, air_ani_content in air_animations.items():
            timeline = air_ani_content.get("timeline", {})
            if not timeline:
                debug(f"    Could not find keyframe timeline")
                missing_wing_flaps.append(air_ani_name)
                break
            debug(f"   Air Animation: {air_ani_name}")
            for time, molang in timeline.items():
                valid_molang = False
                for s in valid_wing_flaps:
                    if s in molang:
                        valid_molang = True
                        debug(f"    Time: {time} | Found valid molang ({molang})")
                if not valid_molang:
                    incorrect_wing_flaps.append(molang)
                    debug(f"    Time: {time} | Could not find valid molang ({molang})")

        if missing_wing_flaps:
            status["ani_wing_flap_keyframes_missing"] = missing_wing_flaps
        if incorrect_wing_flaps:
            status["ani_wing_flap_keyframes_incorrect"] = incorrect_wing_flaps


    # Check for disparity with repository assets
    debug(f"  Checking parity with the assets repository...")
    if not os.path.isfile(ani_repo_path):
        debug(f"  Could not find \"{ani_repo_path}")
    else:
        if ani_data != repo_data:
            status["ani_file_repo_disparity"] = True
            debug(f"  Animations files do not match")
        else: debug(f"  Animation files are identical")


def scan_model(status, name, gen, dex):
    debug(" Scanning the model")
    name = sanitize_name(name)
    dex = f"{dex:>04}"

    if name in use_default_name: name = default_name(name)

    geo_mod_path = find_geo("mod", name)
    if not geo_mod_path: return
    geo_repo_path = find_geo("repo", name)
    if not geo_repo_path: return

    
    with open(geo_mod_path, "r") as file:
        geo_data = json.load(file)
    with open(geo_repo_path, "r") as file:
        geo_repo_data = json.load(file)

    ## Check if locators match with the assets respository
    debug("  Checking if locators match the assets repository")
    # Get locators
    mod_bones = geo_data['minecraft:geometry'][0]['bones']
    mod_locators = {bone['name']: bone for bone in mod_bones if bone['name'].startswith('locator_')}
    repo_bones = geo_repo_data['minecraft:geometry'][0]['bones']
    repo_locators = {bone['name']: bone for bone in repo_bones if bone['name'].startswith('locator_')}

    if set(mod_locators.keys()) == set(repo_locators.keys()):
        debug("  Locators match, checking their contents")
        mismatched_locators = []
        for name in mod_locators:
            if mod_locators[name] != repo_locators[name]:
                debug(f"  Content mismatch found in locator: {name}")
                mismatched_locators.append(name)
                status["geo_locator_content_mismatch"] = mismatched_locators
    else:
        debug(f"  Locators present on the repository are missing in the mod: {set(repo_locators.keys()) - set(mod_locators.keys())}")
        status["geo_missing_locators"] = set(repo_locators.keys()) - set(mod_locators.keys())


    # Check for disparity with repository assets
    debug(f"  Checking parity with the assets repository")
    if not os.path.isfile(geo_repo_path):
        debug(f"  Could not find \"{geo_repo_path}")
    else:
        with open(geo_repo_path, "r") as file:
            repo_data = json.load(file)
        if geo_data != repo_data:
            status["geo_file_repo_disparity"] = True
            debug(f"  Geo files do not match")
        else: debug(f"  Geo files are identical")

def scan_poser(status, name, gen, dex):
    debug(" Scanning the poser")
    name = sanitize_name(name)
    dex = f"{dex:>04}"

    # Return if no JSON poser exists
    if status.get("imp_using_kotlin_poser", False): 
        debug(f"  Cannot check a Kotlin poser")
        return

    imp_mod_path = find_poser(name)
    if not imp_mod_path: return
    ani_mod_path = find_animation("mod", name)
    if not ani_mod_path: return

    # Check for special animation implementation
    possible_targets = ["faint", "cry", "recoil", "physical", "special", "air_physical", "air_special", "status"]
    with open(ani_mod_path, "r", encoding="utf-8") as file:
        anim_data = json.load(file)
        existing_animations = anim_data.get("animations", {}).keys()
        targets = [t for t in possible_targets if f"animation.{name}.{t}" in existing_animations]
    if targets:
        debug(f"  Found animations {targets} and checking if they are implemented in the poser...")
        with open(imp_mod_path, "r", encoding="utf-8") as file:
            imp_data = json.load(file)
            animations = imp_data.get("animations", {}).keys()
            missing_animations = [a for a in targets if a not in animations]
            if missing_animations:
                status["imp_missing_animations"] = missing_animations
                debug(f"  Could not find {missing_animations} in the poser")
            else:
                debug(f"  All aforementioned animations are implemented in the poser")

def scan_sound(status, name, gen, dex):
    debug(" Scanning audio stuff")
    name = sanitize_name(name)
    dex = f"{dex:>04}"

    aud_mod_path = os.path.join(script_dir, "../..", f"common/src/main/resources/assets/cobblemon/sounds/pokemon/{normalize_name(name,'directory')}/{normalize_name(name,'cry')}_cry.ogg")
    aud_repo_path = os.path.join(assets_dir, f"sounds/pokemon/gen{gen}/{dex}_{normalize_name(name,'directory')}/{normalize_name(name,'cry')}_cry.ogg")

    # Check if files exist
    debug(f"  Searching for cry audio files...")
    if not os.path.isfile(aud_mod_path):
        debug(f"  Could not find mod's audio file in \"{aud_mod_path}\"")
        status["aud_cry_file_missing"] = True
        no_mod_cry_very_sad = True
    else:
        debug(f"  Found cry in the mod in \"{aud_mod_path}\"")
        no_mod_cry_very_sad = False
    if not os.path.isfile(aud_repo_path):
        debug(f"  Could not find repository's audio file \"{aud_repo_path}\"")
        no_repo_cry_very_sad = True
    else:
        debug(f"  Found cry on the repository in \"{aud_repo_path}\"")
        no_repo_cry_very_sad = False

    # If cries exist, check if the files are the same
    if not (no_mod_cry_very_sad or no_repo_cry_very_sad):
        # Read the audio data and sample rate
        mod_audio_data, mod_audio_samplerate = sf.read(aud_mod_path)
        repo_audio_data, repo_audio_samplerate = sf.read(aud_repo_path)

        # Check if sample rates match
        if mod_audio_samplerate != repo_audio_samplerate:
            status["aud_cry_file_disparity"] = True
            debug(f"  Sample rate of audio files does not match")
        else:
            debug(f"  Sample rate of audio files matches")

    # Check if the Pokémon is in the sounds.json
    sounds_json_path = os.path.join(script_dir, "../..", f"common/src/main/resources/assets/cobblemon/sounds.json")
    if not os.path.isfile(sounds_json_path):
        debug(f"  Somehow did not find the sounds.json idk how Sterr can mess up a script this bad")
    else:
        with open(sounds_json_path) as file:
            sounds_json_data = json.load(file)
            sound_event = f"pokemon.{normalize_name(name,'cry')}.cry"
            if sound_event in sounds_json_data:
                debug(f"  Found \"{sound_event}\" in sounds.json")
            else:
                # Check if an incorrectly named file exists instead
                unconventional_sound_event = f"pokemon.{incorrect_region_name(name)}.cry"
                if unconventional_sound_event in sounds_json_data:
                    debug(f"  Found \"{unconventional_sound_event}\" in sounds.json")
                    status["aud_cry_sound_event_unconventional_name"] = os.path.basename(unconventional_sound_event)
                else:
                    status["aud_cry_sound_event_missing"] = True
                    debug(f"  Could not find \"{sound_event}\" in sounds.json")


flight_animations = {
    "air_idle",
    "air_fly",
}

wing_flap_variants = {
    "textures": {"leather", "chitin", "steel", "plumage"},
    "sizes": {"large", "medium", "small"}
}

# These Pokémon use their base form for implementation, but have their own row on the sheet
use_default_name = {
    "weezing_galarian",
    "farfetchd_galarian"
}

exclude_from_checks = {
    "Substitute": "(Always skipped)",
    "Unown": "(Ignore this! Only Unown with letters are checked and implemented)",
    "Butterfree": "(Specific check is a work in progress)",
    "Steelix": "(Specific check is a work in progress)",
    "Frillish": "(Specific check is a work in progress)",
    "Jellicent": "(Specific check is a work in progress)",
    "Rotom": "(Specific check is a work in progress)",
    "Mime Jr.": "(Specific check is a work in progress)",
    "Gastrodon": "(Specific check is a work in progress)",
    "Basculin": "(Specific check is a work in progress)",
    "Darmanitan [Zen]": "(Specific check is a work in progress)",
    "Wishiwashi": "(Specific check is a work in progress)",
    "Aegislash": "(Specific check is a work in progress)",
    "Clauncher": "(Specific check is a work in progress)",
    "Clawitzer": "(Specific check is a work in progress)",
    "Eiscue": "(Specific check is a work in progress)",
    "Palafin": "(Specific check is a work in progress)",
    "Dudunsparce": "(Specific check is a work in progress)",
    "Gimmighoul": "(Specific check is a work in progress)"
}

# These are Pokémon whose gender difference implementation separated their poser
# Does not include Pokémon already split into [Male] and [Female] on the assets sheet
gender_variant_pokemon ={
    "Bulbasaur",
    "Ivysaur",
    "Venusaur",
    "Rattata",
    "Kadabra",
    "Alakazam",
    "Milotic", # (Male is missing from the poser name, there is only "milotic" and "milotic_female")
    "Pyroar",
    "Indeedee",
    "Oinkologne"
}

# Exclude specific keywords, only Mega and G-Max for now
exclude_keywords = {
    "Mega",
    "G-Max"
}

# Special Pokémon variants
def split_variants(name):
    if name in gender_variant_pokemon:
        debug(f" {name} will be split into \"{name} [Male]\" and \"{name} [Female]\"")
        return [f"{name} [Male]", f"{name} [Female]"]
    
    elif "Sinistcha" in name:
        debug(f" {name} will be split into \"{name} [Unremarkable]\" and \"{name} [Masterpiece]\"")
        return [f"{name} [Unremarkable]", f"{name} [Masterpiece]"]
    
    elif "Sinistea" in name:
        debug(f" {name} will be split into \"{name} [Antique]\" and \"{name} [Phony]\"")
        return [f"{name} [Antique]", f"{name} [Phony]"]
    
    elif "Poltchageist" in name:
        debug(f" {name} will be split into \"{name} [Artisan]\" and \"{name} [Counterfeit]\"")
        return [f"{name} [Artisan]", f"{name} [Counterfeit]"]
    
    return [name]

## Main loop
for i, row in df_implementation.iterrows():
    # Skip first three rows
    if i < 3: continue

    debug(f"Checking {row['Name']}...")

    # Skip keywords
    for k in exclude_keywords:
        if re.search(rf"\b{k}\b", (df_implementation.loc[i, "Name"])):
            debug(f" Keyword \"{k}\" is excluded, skipping!\n")
            skip = True
            break
        else: skip = False
    if skip: continue

    # Skip if Pokémon is excluded
    if df_implementation.loc[i, "Name"] in exclude_from_checks:
        debug(f" {row['Name']} is excluded, skipping!\n")
        continue
    for c in exclude_from_checks:
        if re.search(rf"\b{c}\b", (df_implementation.loc[i, "Name"])):
            debug(f" \"{c}\" is excluded, skipping!\n")
            skip = True
            break
        else: skip = False
    if skip: continue

    ## Set up variables
    # Gen
    gen = df_implementation.loc[i,"Gen"]
    # Dex
    dex = df_implementation.loc[i,"Dex"]
    # Name
    name = df_implementation.loc[i,"Name"]

    # Split the checks if Pokémon has variants with separate implementation
    variants = split_variants(name)

    for variant in variants:
        name = variant

        # Clear error flags
        try: status.clear()
        except: status = {}
        status = {}

        # Check implementation status, skip if unimplemented in the mod
        check_implementation(status, name, dex, gen, df_models.loc[i,"Model Status"])

        if not status.get("imp_implemented", True):
            debug(" Pokémon is not implemented in the mod, skipping!")
            continue

        # Cry sound status
        if (df_cries.loc[i, "Cry"] == "Approved"): cry_available = True
        else: cry_available = False

        # Scan for issues
        if "imp_implemented" in status:
            scan_poser(status, name, gen, dex)
            scan_animation(status, name, gen, dex)
            scan_model(status, name, gen, dex)
            scan_sound(status, name, gen, dex)
            
            # Delete non-issues when done
            del status["imp_implemented"]

        # Exclude checks from basic mode
        if mode == "basic":
            if not cry_available: # Issues with cries are ignored if the cry is not made, only in basic mode
                if "ani_cry_animation_missing" in status: del status["ani_cry_animation_missing"]
                if "ani_cry_keyframe_missing" in status: del status["ani_cry_keyframe_missing"]
                if "ani_cry_keyframe_invalid" in status: del status["ani_cry_keyframe_invalid"]
                if "aud_cry_file_disparity" in status: del status["aud_cry_file_disparity"]
                if "aud_cry_file_missing" in status: del status["aud_cry_file_missing"]
                if "aud_cry_sound_event_missing" in status: del status["aud_cry_sound_event_missing"]
                
            if "sht_erroneous_work_in_progress_flag" in status: del status["sht_erroneous_work_in_progress_flag"]
            if "sht_erroneous_not_started_flag" in status: del status["sht_erroneous_not_started_flag"]
            if "sht_erroneous_needs_approval_flag" in status: del status["sht_erroneous_needs_approval_flag"]
            if "sht_erroneous_needs_implementation_flag" in status: del status["sht_erroneous_needs_implementation_flag"]
            if "sht_erroneous_needs_rework_flag" in status: del status["sht_erroneous_needs_rework_flag"]
            if "sht_erroneous_needs_help_flag" in status: del status["sht_erroneous_needs_help_flag"]

            if "imp_using_kotlin_poser" in status: del status["imp_using_kotlin_poser"]

            if "ani_file_repo_disparity" in status: del status["ani_file_repo_disparity"]
            if "geo_file_repo_disparity" in status: del status["geo_file_repo_disparity"]

            if "ani_unconventional_name" in status: del status["ani_unconventional_name"]
            if "aud_cry_sound_event_unconventional_name" in status: del status["aud_cry_sound_event_unconventional_name"]
            if "imp_unconventional_name" in status: del status["imp_unconventional_name"]
            if "geo_unconventional_name" in status: del status["geo_unconventional_name"]

            if "ani_wing_flap_keyframes_missing" in status: del status["ani_wing_flap_keyframes_missing"]
            if "ani_wing_flap_keyframes_incorrect" in status: del status["ani_wing_flap_keyframes_incorrect"]

            if "geo_file_missing" in status: del status["geo_file_missing"] # This is ommited for now until I cover all variants
            if "ani_file_missing" in status: del status["ani_file_missing"] # This is ommited for now until I cover all variants


        # Print out issues
        if any(status.values()):
            print_color("white")
            print(f"✘ {name} has the following issues:")
            if len(variants)>1: print(f"(The variant split is present only in implementation, not on the spreadsheet)")

            name = sanitize_name(name)

            # Sheet Issues
            print_color("red")
            if status.get("sht_erroneous_implemented_flag", False): print(f"  ► Pokémon is not implemented, but flagged as such on the Models sheet")
            if status.get("sht_erroneous_work_in_progress_flag", False): print(f"  ► Flagged as Work in Progress on the Models sheet, while implemented in the mod")
            if status.get("sht_erroneous_not_started_flag", False): print(f"  ► Flagged as Not Started on the Models sheet, while implemented in the mod")
            if status.get("sht_erroneous_needs_approval_flag", False): print(f"  ► Flagged as Needs Approval on the Models sheet, while implemented in the mod")
            if status.get("sht_erroneous_needs_implementation_flag", False): print(f"  ► Flagged as Needs Implementation on the Models sheet, while implemented in the mod")
            if status.get("sht_erroneous_needs_rework_flag", False): print(f"  ► Flagged as Needs Rework on the Models sheet, while implemented in the mod")
            if status.get("sht_erroneous_needs_help_flag", False): print(f"  ► Flagged as Needs Help on the Models sheet, while implemented in the mod")
            if status.get("sht_erroneous_implemented_flag", False) or status.get("sht_erroneous_work_in_progress_flag", False) or status.get("sht_erroneous_not_started_flag", False) or status.get("sht_erroneous_needs_approval_flag", False) or status.get("sht_erroneous_needs_implementation_flag", False) or status.get("sht_erroneous_needs_rework_flag", False) or status.get("sht_erroneous_needs_help_flag", False):
                print(f"    ▲ Check notes and ask approvers, before changing anything on the Models sheet")

            # Implementation Issues
            print_color("green")
            if status.get("imp_unconventional_name", False):
                print(f"  ► Poser is using an unconventional name \"{status['imp_unconventional_name']}\"")
                region_name = status["imp_unconventional_name"]
                if "_galar" in region_name: print(f"    ▲ The convention is to use \"_galarian\" instead of \"_galar\"")
                if "_alola" in region_name: print(f"    ▲ The convention is to use \"_alolan\" instead of \"_alola\"")
                if "_hisui" in region_name: print(f"    ▲ The convention is to use \"_hisuian\" instead of \"_hisui\"")
                if "_paldea" in region_name: print(f"    ▲ The convention is to use \"_paldean\" instead of \"_paldea\"")

            if status.get("imp_using_kotlin_poser", False): print(f"  ► Still using a Kotlin poser, the script cannot check for poser issues")
            if status.get("imp_missing_animations", False): print(f"  ► Found animations in \"{normalize_name(name,'animation')}.animation.json\" that are not implemented in the poser: {status['imp_missing_animations']}")

            # Animation Issues
            print_color("cyan")
            if status.get("ani_unconventional_name", False):
                print(f"  ► Animation is using an unconventional name \"{status['ani_unconventional_name']}\"")
                region_name = status["ani_unconventional_name"]
                if "_galar" in region_name: print(f"    ▲ The convention is to use \"_galarian\" instead of \"_galar\"")
                elif "_alola" in region_name: print(f"    ▲ The convention is to use \"_alolan\" instead of \"_alola\"")
                elif "_hisui" in region_name: print(f"    ▲ The convention is to use \"_hisuian\" instead of \"_hisui\"")
                elif "_paldea" in region_name: print(f"    ▲ The convention is to use \"_paldean\" instead of \"_paldea\"")
                else: print(f"    ▲ Whatever triggered this is likely incorrect, report this pls")
            

            if status.get("ani_file_missing", False): print(f"  ► Animation file does not exist, could not find \"pokemon/animations/{dex:>04}_{normalize_name(name,'directory')}/{normalize_name(name,'animation')}.animation.json\"")
            if status.get("ani_file_repo_disparity", False): print(f"  ► Animation file \"{sanitize_name(normalize_name(name, 'animation'))}.animation.json\" does not match the Assets Repository")

            if status.get("ani_cry_animation_missing", False): print(f"  ► Cry animation is missing, could not find \"animation.{normalize_name(name,'animation')}.cry\"")
            if status.get("ani_wing_flap_keyframes_missing", False): print(f"  ► Could not find wing flap sounds in air animations: {status['ani_wing_flap_keyframes_missing']}")
            if status.get("ani_wing_flap_keyframes_incorrect", False): print(f"  ► Found incorrect wing flap sounds in air animations: {status['ani_wing_flap_keyframes_incorrect']}")
            if status.get("ani_cry_keyframe_missing", False): print(f"  ► Cry keyframe is missing in \"animation.{normalize_name(name,'animation')}.cry\"")
            if status.get("ani_cry_keyframe_invalid", False): print(f"  ► Cry keyframe in \"animation.{normalize_name(name,'animation')}.cry\" has an incorrect name, expected \"pokemon.{normalize_name(name,'animation')}.cry\" and found \"{status['ani_cry_keyframe_invalid']}\"")
            if (status.get("ani_cry_animation_missing", False) or status.get("ani_cry_keyframe_missing", False)) and not cry_available:
                print(f"    ▲ Cry sound doesn't exist, but a cry animation and sound keyframe should always be implemented at least as a placeholder")

            # Model Issues
            print_color("cyan")
            if status.get("geo_unconventional_name", False):
                print(f"  ► Model is using an unconventional name \"{status['geo_unconventional_name']}\"")
                region_name = status["geo_unconventional_name"]
                if "_galar" in region_name: print(f"    ▲ The convention is to use \"_galarian\" instead of \"_galar\"")
                elif "_alola" in region_name: print(f"    ▲ The convention is to use \"_alolan\" instead of \"_alola\"")
                elif "_hisui" in region_name: print(f"    ▲ The convention is to use \"_hisuian\" instead of \"_hisui\"")
                elif "_paldea" in region_name: print(f"    ▲ The convention is to use \"_paldean\" instead of \"_paldea\"")
                else: print(f"    ▲ Whatever triggered this is likely incorrect, report this pls")
            

            if status.get("geo_file_missing", False):
                print(f"  ► Geo file does not exist, could not find \"pokemon/models/{dex:>04}_{normalize_name(name,'directory')}/{normalize_name(name,'geo')}.geo.json\"")
                print(f"    ▲ This is likely an issue with the script not not recognizing variants")
            if status.get("geo_file_repo_disparity", False): print(f"  ► Geo file \"{sanitize_name(normalize_name(name,'geo'))}.geo.json\" does not match the Assets Repository")
            if status.get("geo_missing_locators", False): print(f"  ► Locators present on the assets repository are missing in the mod: {status['geo_missing_locators']}")
            if status.get("geo_locator_content_mismatch", False): print(f"  ► Content of these locators does not match the assets repository: {status['geo_locator_content_mismatch']}")

            # Audio issues
            print_color("magenta")
            if status.get("aud_cry_sound_event_unconventional_name", False):
                print(f"  ► Sound event is using an unconventional name \"{status['aud_cry_sound_event_unconventional_name']}\"")
                region_name = status["aud_cry_sound_event_unconventional_name"]
                if "_galar" in region_name: print(f"    ▲ The convention is to use \"_galarian\" instead of \"_galar\"")
                if "_alola" in region_name: print(f"    ▲ The convention is to use \"_alolan\" instead of \"_alola\"")
                if "_hisui" in region_name: print(f"    ▲ The convention is to use \"_hisuian\" instead of \"_hisui\"")
                if "_paldea" in region_name: print(f"    ▲ The convention is to use \"_paldean\" instead of \"_paldea\"")

            if status.get("aud_cry_sound_event_missing", False): print(f"  ► Could not find a sound event \"pokemon.{normalize_name(name,'cry')}.cry\" in sounds.json")
            if status.get("aud_cry_file_disparity", False): print(f"  ► Replace \"{normalize_name(name,'cry')}_cry.ogg\" with the updated file from the asset repository")
            if status.get("aud_cry_file_missing", False): print(f"  ► Cry sound is missing, at least an empty \"{normalize_name(name,'cry')}_cry.ogg\" is needed")

            print_color("reset")
            if not log_debug: print("\n")
    if log_debug: print("\n")



# We done
print("\nWe done!\n")
print("The following Pokémon are excluded from this script and must be checked manually:")
for c, v in exclude_from_checks.items():
    print(f"► {c} {v}")
print("\nPress [Esc] to exit")
while True:
    input = getch()
    if input == b'\x1b':
        quit()