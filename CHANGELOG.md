# Changelog
## [1.8.2 (MONTH xth, 2026)](#1-8-2)

### Additions
- The following items can now be placed on the ground as blocks:
  - King's Rock, Galarica Cuff, Unremarkable Teacup, Masterpiece Teacup, Chipped Pot, Cracked Pot, Sachet, Metal Powder, Quick Powder, Bright Powder, Soft Sand, Electirizer, Magmarizer, Icy Rock, Heat Rock, Smooth Rock, Damp Rock, Destiny Knot, Luminous Moss, Never-Melt Ice, Wide Lens, Scope Lens, and Zoom Lens.
  - Iron Ball, Metal Coat, Throat Spray, Terrain Extender, Medicinal Brew, Moomoo Milk, HP Up, Protein, Iron, Carbos, Calcium, Zinc, PP Up, PP Max, Upgrade, Dubious Disc, and Loaded Dice.
- The following items can now be placed on various surfaces:
  - Galarica Wreath, Magnet, Flame Orb, Life Orb, Toxic Orb, Light Ball, and Smoke Ball.
- The following items can now be placed as blocks and have additional functionality:
  - Ring Target, Metronome, and Eject Button.
- The following items can now be worn by Players and Pokémon:
  - Galarica Wreath, Cleanse Tag, Spell Tag, Sharp Beak, Covert Cloak, Shell Helmet, and Never-Melt Ice.
- Wearable items can now be equipped by a player by holding it and right-clicking (or rebound use/interact)
  - Wearable items that can be placed down are exempt from this behaviour.
- Mint Leaves can now be placed on the ground as Leaf Litter, or in Flower Pots
- Added Oricorio Flowers
- Added Terrain Plants
- Berries can now be placed in Flower Pots
- The evolution tree can now be viewed on the Pokédex, showing a Pokémon's full family line and what's needed to evolve into each stage.
- Added custom sounds to a bunch of items that can be placed as blocks: liquid bottles, pill bottles, powders and dice.
- New Cosmetic Item for Pikachu: Paper
- Burmy changes its cloak after a battle it took part in, based on the biome: Trash Cloak in the Deep Dark, Nether and End, Plant Cloak in lush biomes such as forests, jungles, plains and swamps, and Sandy Cloak in sandy biomes, beaches and caves.
- The evolution tree can now be viewed on the Pokédex, showing a Pokémon's full family line and what's needed to evolve into each stage.
- Added custom sounds to a bunch of items that can be placed as blocks: liquid bottles, pill bottles, powders and dice.

### Fixes
- Fixed #updateMovesOnFormChange not properly initializing a form-swapped Pokemon's non-form_change moves.
### TEST
### Pokémon Added
#### Generation 4
- Snover
- Abomasnow
- Burmy
- Wormadam
- Mothim

#### Generation 5
- Tympole
- Palpitoad
- Seismitoad

#### Generation 6
- Helioptile
- Heliolisk

#### Generation 7
- Grubbin
- Charjabug
- Vikavolt
- Minior

#### Generation 8
- Eternatus

#### Generation 9
- Greavard
- Houndstone

### Model updates for the following Pokémon
- Sudowoodo
- Spheal
- Cacnea
- Cacturne
- Binacle
- Barbaracle
- Glimmet
- Glimmora
- Deerling
- Sawsbuck (All forms)
- Absol
- Poliwag
- Politoed
- Ekans
- Oddish
- Gloom
- Vileplume
- Bellossom
- Shroomish
- Chespin
- Quilladin
- Chesnaught
- Fennekin
- Braixen
- Delphox
- Froakie
- Frogadier
- Greninja
- Shinx
- Luxio
- Luxray
- Slakoth
- Riolu
- Lucario

### Animation updates for the following Pokémon
- Treecko (New Shoulder Mount!)
- Bonsly (New Shoulder Mount!)
- Sudowoodo
- Cacnea
- Cacturne
- Ekans
- Arbok
- Oddish
- Gloom
- Vileplume
- Bellossom
- Pichu
- Pikachu
- Raichu (+ Alolan)
- Greninja
- Slakoth

### Changes
- Terrain Seeds can now be planted
- Terrain Seeds are now consumed during PvP and NPC battles
- Glow Item Frames can be crafted using Bright Powder as an alternative to Glow Ink Sacs
- Bright Powder can be used on Signs and Hanging Signs to make text glow, as an alternative to Glow Ink Sacs
- Updated various recipes to display in Recipe Groups within the Recipe Book
- Updated most recipes to display more logically/consistently within the Recipe Book's categories
- Updated the following recipes
  - Never-Melt Ice, Focus Sash, Blunder Policy, Weakness Policy, Blank TM
- Make ride rotations less jittery in multiplayer.
- Changes Disc Shelf TMs to use textures instead of tinting and overlays to line up with item display
- Blank TM can now be inserted into the Disc Shelf
- Added TM creative tab
- Recall animation now renders ball in hand with the player arm raised.
- Made regional and Vivillon split-evo locations now mutually exclusive
- Vivillon's patterns are now viewable as forms with form names and unique Pokedex entries displayed.
- Item evolutions now unlock **all** locked evolutions whose requirements are met instead of stopping after the first.
  - Exceptions being "forced" evolutions which will continue to stop after the first one
- Removed Mega Drain as a TM
- Vivillon's patterns are now viewable as forms with form names and unique Pokedex entries displayed.
- Slowed the speed of Type Gem growth, and revamped their generation to grow in a more crystal-like shape
- PokeSnacks (and other area spawns) will now start the vertical offset checking from the center rather than base coordinate
  - This will help PokeSnacks to work inside caves or tighter spaces better

### Fixes
- Correctly grants "They're Minerals!" advancement when planting a type gem
- Shoulder mounted Pokémon now follow player body animations, improving their positioning with custom player animations and default actions.
- Shoulder mounted Pokémon now sit slightly higher with chest armor equipped for better alignment, with a configurable blacklist.
- Type Gem Blocks now have advancements to give you the recipe in the recipe book (the data for the advancements were inside the recipes somehow??)
- Some other recipes without advancements have also been corrected
- Corrected the TM Recipes for Charge Beam and Payday
- Type Gem blocks now properly generate clusters off of the block.
- Starter Screen Categories will no longer stop rendering if they are bigger than the viewport, and you are not viewing top or bottom edges
- Random Starter option now properly handled in height calculation, so now it won't run off into other categories or be un-accessible due to scroll limits if it causes a new row
- Mochi and Feathers no longer drop glass bottles on use
- Fixed a rare set of concurrency bugs related to reloading packs on NeoForge; fixes a ModernFix issue
- Removed unnecessary conditions for Mimikyu's standing pose. Hopefully fixes the rare instance of it using its shoulder animations during battle in multiplayer scenarios.
- Fixed Rockruff line not blinking
- Fixed Flygon sometimes using its running animation instead of walking
- Fixed Bramblin always sleeping outside of battle
- Fixed Dragonair using its battle idle as a normal idle
- Fixed Lycanroc's head movement
- Added head movement to Archaludon
- Fixed Duraludon line not having any sleep animations
- Fixed Diglett line not having their faint animations
- Fixed Kingambit moving its legs while walking
- Fixed Alolan Diglett's cry not playing
- Fixed shiny alpha Snorlax using the non-shiny texture
- Various Structure fixes
- Fixed shiny Pokémon under certain circumstances forgetting taught TMs
- Fixed certain Pokémon sounds sometimes being doubled when played at specific times in their animations
- Fixed ball capture beam origin coming from behind the ball.
- Fixed Safari Ball catch rate outside of battle
- Fixed alpha eye (and other) particles not respecting client particle density setting in video options

### Developer
- Evolution requirements can now set `pokedexIcon` and `pokedexTooltip` directly in their JSON to control how they're shown on the Pokédex's evolution tree, for example:
  ```json
  {
    "variant": "attack_defence_ratio",
    "pokedexIcon": "minecraft:diamond_sword",
    "pokedexTooltip": "cobblemon.ui.pokedex.requirement.attack_defence_ratio.tooltip"
  }
  ```
- Fixed wrong order of operations in the code flow prior to the `FOSSIL_REVIVED` event causing pokemon to appear without entity link
- Fixed TestPartySlot command not correctly comparing the provided spec with the target Pokémon

### Molang & Datapacks
- A new `negate` variant for evolution requirements has been added, which will negate (invert) the provided inner condition.
- Added Molang functions for individual Pokédex ids, wherein a subdex is passed in as a string to `dex_caught_count` etc.
- Added a `battle_ended` callback, backed by the new `BATTLE_ENDED` event, which fires whenever a battle ends for any reason, alongside a multitude of flags not previously covered by `BATTLE_VICTORY`.
- Added `battle_pokemon` to battle actor structs and `has_faced_opponents` to battle Pokémon structs.
- Added the `cobblemon:form_change/bagworm_cloak/plant_cloak`, `sandy_cloak` and `trash_cloak` biome tags, which control Burmy's cloak after battle.

### Particles

#### New Move Animations

### Localization
- Updated translations for:
