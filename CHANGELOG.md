# Changelog
## [1.8.3 (MONTH xth, 2026)](#1-8-3)

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
- Added custom footstep sounds to the following Pokémon: Arcanine, Wyrdeer, Pidgeot, Corviknight.
- Mint Leaves can now be placed on the ground as Leaf Litter, or in Flower Pots
- Added Oricorio Flowers
- Added Terrain Plants
- Berries can now be placed in Flower Pots
- The evolution tree can now be viewed on the Pokédex, showing a Pokémon's full family line and what's needed to evolve into each stage.
- Added custom sounds to a bunch of items that can be placed as blocks: liquid bottles, pill bottles, powders and dice.
- New Cosmetic Item for Pikachu: Paper
- Burmy changes its cloak after a battle it took part in, based on the biome: Trash Cloak in the Deep Dark, Nether and End, Plant Cloak in lush biomes such as forests, jungles, plains and swamps, and Sandy Cloak in sandy biomes, beaches and caves.

### Pokémon Added
#### Generation 4

#### Generation 5

#### Generation 6

#### Generation 7

#### Generation 8

#### Generation 9

### Model updates for the following Pokémon

### Animation updates for the following Pokémon

### Changes

### Fixes
- Fixed datapacks with item interaction evolutions that don't specify an item crashing the world on load.
- Fixed the Pokédex evolution tree crashing when a datapack's dex entry is missing an evolution's form.
- Fixed the Pokédex evolution tree crashing with a stack overflow on evolutions that loop back on themselves.
- Fixed form-changing evolutions sometimes resolving to the source form in the Pokédex evolution tree.
- Broken datapack evolutions are now logged and skipped in the Pokédex evolution tree instead of crashing.

### Developer

### Molang & Datapacks
- A new `negate` variant for evolution requirements has been added, which will negate (invert) the provided inner condition.
- Added Molang functions for individual Pokédex ids, wherein a subdex is passed in as a string to `dex_caught_count` etc.
- `q.is_standing_on_blocks` now properly checks for non-full blocks like slabs, Mud or Soul Sand
- Added Molang functions to play footstep sounds based on the corresponding surface. These are intended to be used by Pokémon animations.
- Added a `battle_ended` callback, backed by the new `BATTLE_ENDED` event, which fires whenever a battle ends for any reason, alongside a multitude of flags not previously covered by `BATTLE_VICTORY`.
- Added `battle_pokemon` to battle actor structs and `has_faced_opponents` to battle Pokémon structs.
- Added the `cobblemon:form_change/bagworm_cloak/plant_cloak`, `sandy_cloak` and `trash_cloak` biome tags, which control Burmy's cloak after battle.

### Particles

#### New Move Animations

### Localization
- Updated translations for:
