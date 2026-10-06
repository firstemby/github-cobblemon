# Changelog
## [1.8.2 (MONTH xth, 2026)](#1-8-2)

### Additions
- The following items can now be placed on the ground as blocks:
  - King's Rock, Galarica Cuff, Unremarkable Teacup, Masterpiece Teacup, Chipped Pot, Cracked Pot, Sachet, Metal Powder, Quick Powder, Bright Powder, Icy Rock, Heat Rock, Smooth Rock, Damp Rock, Destiny Knot, Luminous Moss, Never-Melt Ice, Wide Lens, Scope Lens, and Zoom Lens.
  - Iron Ball, Metal Coat, Throat Spray, HP Up, Protein, Iron, Carbos, Calcium, Zinc, PP Up, PP Max, Upgrade, Dubious Disc, and Loaded Dice.
- The following items can now be placed on various surfaces:
  - Galarica Wreath, Magnet, Flame Orb, Life Orb, Toxic Orb, Light Ball, and Smoke Ball.
- The following items can now be placed as blocks and have additional functionality:
  - Ring Target, Metronome, and Eject Button.
- The following items can now be worn by Players and Pokémon:
  - Galarica Wreath, Cleanse Tag, Spell Tag, Sharp Beak, Covert Cloak, Shell Helmet, and Never-Melt Ice.
- Wearable items can now be equipped by a player by holding it and right-clicking (or rebound use/interact)
  - Wearable items that can be placed down are exempt from this behaviour.
- New Cosmetic Item for Pikachu: Paper
- Burmy changes its cloak after a battle it took part in, based on the biome: Trash Cloak in the Deep Dark, Nether and End, Plant Cloak in lush biomes such as forests, jungles, plains and swamps, and Sandy Cloak in sandy biomes, beaches and caves.

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
- Updated various recipes to display in Recipe Groups within the Recipe Book
- Updated most recipes to display more logically/consistently within the Recipe Book's categories
- Make ride rotations less jittery in multiplayer.
- Made regional and Vivillon split-evo locations now mutually exclusive
- Vivillon's patterns are now viewable as forms with form names and unique Pokedex entries displayed.

### Fixes
- Correctly grants "They're Minerals!" advancement when planting a type gem
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

### Developer

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