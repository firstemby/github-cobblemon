# Changelog
## [1.8.2 (October 11th, 2026)](#1-8-2)

### Additions
- The following items can now be placed on the ground as blocks:
  - King's Rock, Galarica Cuff, Unremarkable Teacup, Masterpiece Teacup, Chipped Pot, Cracked Pot, Sachet, Metal Powder, Quick Powder, Bright Powder, Soft Sand, Electirizer, Magmarizer, Icy Rock, Heat Rock, Smooth Rock, Damp Rock, Destiny Knot, Luminous Moss, Never-Melt Ice, Wide Lens, Scope Lens, and Zoom Lens.
  - I wasn't finished. Iron Ball, Metal Coat, Throat Spray, Terrain Extender, Medicinal Brew, Moomoo Milk, HP Up, Protein, Iron, Carbos, Calcium, Zinc, PP Up, PP Max, Upgrade, Dubious Disc, and Loaded Dice.
- The following items can now be placed on various surfaces:
  - Galarica Wreath, Magnet, Flame Orb, Life Orb, Toxic Orb, Light Ball, and Smoke Ball.
- The following items can now be placed as blocks and have additional functionality:
  - Ring Target, Metronome, and Eject Button.
- The following items can now be worn by Players and Pokémon:
  - Galarica Wreath, Cleanse Tag, Spell Tag, Sharp Beak, Covert Cloak, Shell Helmet, and Never-Melt Ice.
- Wearable items can now be equipped by a player by holding it and right-clicking (or whatever you've rebound use/interact to).
  - Wearable items that can be placed down are exempt from this behaviour.
- Mint Leaves can now be placed on the ground as Leaf Litter, or in Flower Pots.
- The recall animation now renders the ball in hand with the player's arm raised.
- Added Oricorio Flowers.
- Added Terrain Plants, four plants grown from Electric, Grassy, Misty, and Psychic Seeds, which glow under specific environmental conditions and can be farmed for more of their seeds.
- Berries can now be placed in Flower Pots.
- The evolution tree for a Pokémon can now be viewed in the Pokédex, showing the Pokémon's full family line and what's needed to evolve into each stage.
- Added custom sounds to a bunch of items that can be placed as blocks: liquid bottles, pill bottles, powders, and dice.
- Added paper as a new cosmetic item for Pikachu.
- Burmy changes its cloak after a battle it took part in, based on the biome.
  - Trash Cloak in the Deep Dark, Nether and End.
  - Plant Cloak in lush biomes such as forests, jungles, plains and swamps.
  - Sandy Cloak in sandy biomes, beaches and caves.

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
- Corsola
- Mime Jr.

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
- Slowed the speed of Type Gem growth, and revamped their generation to grow in a more crystal-like shape.
- Terrain Seeds can now be planted.
- Terrain Seeds are now consumed during PvP and NPC battles.
- Glow Item Frames can be crafted using Bright Powder as an alternative to Glow Ink Sacs.
- Bright Powder can be used on Signs and Hanging Signs to make text glow, as an alternative to Glow Ink Sacs.
- Updated various recipes to display in Recipe Groups within the Recipe Book.
- Updated most recipes to display more logically and consistently within the Recipe Book's categories.
- Updated the following recipes
  - Never-Melt Ice, Focus Sash, Blunder Policy, Weakness Policy, Blank TM.
- Made ride rotations less jittery in multiplayer.
- Changes Disc Shelf TMs to use textures instead of tinting and overlays to line up with item display.
- Blank TM can now be inserted into the Disc Shelf.
- Added a TM creative tab.
- Made regional and Vivillon split-evo locations mutually exclusive.
- Vivillon's patterns are now viewable as forms with form names and unique Pokédex entries displayed.
- Item evolutions now unlock **all** locked evolutions whose requirements are met instead of stopping after the first.
  - Exceptions being "forced" evolutions which stop after the first one.
- Removed Mega Drain as a TM.
- PokeSnacks (and other area spawns) will now start the vertical offset checking from the center rather than base coordinate
  - This will help PokeSnacks to work inside caves or tighter spaces better.

### Fixes
- Type Gem blocks now properly generate clusters off of the block.
- Correctly grant "They're Minerals!" advancement when planting a Type Gem.
- Shoulder mounted Pokémon now follow player body animations, improving their positioning with custom player animations and default actions.
- Shoulder mounted Pokémon now sit slightly higher with chest armor equipped for better alignment, with a configurable blacklist.
- Type Gem Blocks now have advancements to give you the recipe in the recipe book (the data for the advancements were inside the recipes somehow??).
  - Some other recipes without advancements have also been corrected.
- Corrected the TM Recipes for Charge Beam and Payday.
- Starter Screen Categories will no longer stop rendering if they are bigger than the viewport and you are not viewing the top or bottom edges.
- The Random Starter option no longer runs off into other categories or becomes unaccessible due to scroll limits if it causes a new row.
- Mochi and Feathers no longer drop glass bottles on use.
- Fixed a rare set of concurrency bugs related to reloading packs on NeoForge; fixes a ModernFix compatibility issue.
- Removed unnecessary conditions for Mimikyu's standing pose. Hopefully fixes the rare instance of it using its shoulder animations during battle in multiplayer scenarios.
- Fixed Rockruff line not blinking.
- Fixed Flygon sometimes using its running animation instead of walking.
- Fixed Bramblin always sleeping outside of battle.
- Fixed Dragonair using its battle idle as a normal idle.
- Fixed Lycanroc's head movement.
- Added head movement to Archaludon.
- Fixed Duraludon line not having any sleep animations.
- Fixed Diglett line not having their faint animations.
- Fixed Kingambit moving its legs while walking.
- Fixed Alolan Diglett's cry not playing.
- Fixed shiny alpha Snorlax using the non-shiny texture.
- Fixed Crabominable's head breaking when looking around.
- Fixed Shiny Dipplin and Hydrapple's textures.
- Various structure fixes.
- Fixed shiny Pokémon under certain circumstances forgetting taught TMs.
- Fixed certain Pokémon sounds sometimes being doubled when played at specific times in their animations.
- Fixed ball capture beam origin coming from behind the ball.
- Fixed Safari Ball catch rate outside of battle.
- Fixed alpha eye (and other) particles not respecting the client particle density setting in video options.
- Fixed the TM Machine UI not closing when player is moving away from it.
- Moved the config category for alpha/shiny chance config fields for fossil machine and honey log slathering to Spawning.
- Fixed crashes for addon species with non-Cobblemon namespaces in evolution, fishing bait, and Pokédex lookups.
- Bees will now grow Apricorns and the Galarica Nut Bush, which were in the bee growables tag but never handled, and correctly spend their nectar when they do.
- Saccharine Leaves can now be bone mealed.
- Fixed equipping a stack of wearable items instead of one item of a stack.
- Fixed TM stack consumption in TM Machine screen when a player teaches a move to a Pokémon.
- Fixed every Miltank giving mushroom stew, not only mushroom variants.
- Medicinal Leeks can now be eaten when they aren't being planted, matching their food properties.
- Sweeping attacks no longer set owned Pokémon on fire (e.g. with Fire Aspect) or knock them back. Friendly Fire is now off.
- Fixed Pokémon not losing their previous form's signature moves when changing forms (e.g. Rotom keeping Overheat after leaving its Heat form).
- Form-specific moves gained from a form change are now only granted if the Pokémon is high enough level to learn them, and no longer create duplicates in benched moves.
- Spinda now learns Teeter Dance at level 32 instead of via TM.
- Fixed Oricorio Pa'u form description in the Pokédex.
- Fixed PC playing sound on close when opened via command.

### Developer
- Evolution requirements can now set `pokedexIcon` and `pokedexTooltip` directly in their JSON to control how they're shown on the Pokédex's evolution tree, for example:
  ```json
  {
    "variant": "attack_defence_ratio",
    "pokedexIcon": "minecraft:diamond_sword",
    "pokedexTooltip": "cobblemon.ui.pokedex.requirement.attack_defence_ratio.tooltip"
  }
  ```
- Fixed the wrong order of operations in the code flow prior to the `FOSSIL_REVIVED` event causing Pokémon to appear without the entity link.
- Fixed TestPartySlot command not correctly comparing the provided spec with the target Pokémon.

### Molang & Datapacks
- A new `negate` variant for evolution requirements has been added, which will negate (invert) the provided inner condition.
- Added Molang functions for individual Pokédex ids, wherein a subdex is passed in as a string to `dex_caught_count` etc.
- Added a `battle_ended` callback, backed by the new `BATTLE_ENDED` event, which fires whenever a battle ends for any reason, alongside a multitude of flags not previously covered by `BATTLE_VICTORY`.
- Added `battle_pokemon` to battle actor structs and `has_faced_opponents` to battle Pokémon structs.
- Added the `cobblemon:form_change/bagworm_cloak/plant_cloak`, `sandy_cloak` and `trash_cloak` biome tags, which control Burmy's cloak after battle.

### Localization
- Updated translations for:
  - Brazilian Portuguese
  - Canadian French
  - Japanese
  - Korean
  - Russian
  - Ukrainian
