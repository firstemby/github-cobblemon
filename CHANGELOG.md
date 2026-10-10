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
- Added riding statistics: `Distance by Pokémon on Land`, `Distance by Pokémon in Air` and `Distance by Pokémon in Liquid`.
- Added the Partner Mark, which party Pokémon have a very small chance to earn as the player walks, provided they have enough friendship.
- Added trades for Saccharine Saplings, Hearty Grains, Chipped Pot, and Masterpiece Teacup to the Wandering Trader.
- Added brewing recipe for Throat Spray.
- Added mood particles! Pokémon now visually react to certain actions:
    - Angry particles when attacking a mob.
    - Annoyed particles when a player tries to feed them while they’re already full.
    - Sweat droplets when they run out of stamina while being ridden.
- Added config options to award experience to fainted Pokémon for opponents they helped defeat (`awardExperienceToFaintedPokemon`) and to still award experience for defeated enemies even after battle loss/forfeit (`awardExperienceOnBattleLoss`)
- Party Overlay Exp Gained Animation will now display the amount of EXP the Pokémon gained
- Added a toast to notify players of how they can scroll through their party. Did you know you can hold R and scroll instead of using arrow keys??!
- Added a new layer property that makes a layer scroll in a direction. "scrolling": {"speedU": 0.1, "speedV": 0.1}
- Added missing crops to Botany Pots integration
- Added a way to obtain Porygon by inserting the Upgrade or Dubious Disc into a data monitor
- Added a new `cobblemon:structure`, which is a feature without any restrictions allowing the Builders to place certain structures in much more specific manner.
- Added Configs for Infinite Use TMs and Unlocking all TMs
- Added Type Gem blocks
- Added Type Gem Clusters
- Added TMs
- Added TM Machine
- Added Deepslate Core Block
- Added /givetm Command
- Added advancement triggers for learning TMs: `cobblemon:has_learn_specific_tm` (per-TM), `cobblemon:has_learn_all_tm` (all TMs learned), and `cobblemon:tms_learned` (count-based), backed by a new `totalTMLearnedCount` on player advancement data.
- Added `cobblemon:catch_alpha_pokemon` advancement trigger and `totalAlphaCaptureCount` tracking for capturing Alpha Pokémon.
- Added Move Dex to Pokédex
- Added the Habitat Block, a spawner block for servers and adventure maps that controls spawning in an area.
- Added Disk Rack for storing disks and being an audio sequencer for Noteblocks
- Added intrinsic scaling to all Pokémon (+-5%). Some rendering cases, such as shoulder mounting, revert to 1.0 scaling to preserve animation accuracy.
- Added scaling to "baby" Pokémon (smaller (-20%) at level 1 → normally sized at level 10)
- Added conditional seats for riding. Seats can be gated by molang such as "is_alpha".
- Added Alpha Pokémon
    - Added alpha scaling (alphas are larger than their normal counterparts)
    - Alphas will always lead herds and will spawn in herds
    - Alphas spawn with two random TM moves from their learnset
    - Alpha Pokémon will always defend themselves from players that attack them
    - Added alpha level matching and stat boost (based on level)
    - Added pitch-down and reverb to alpha cries
    - Added alpha Pokémon bait effect
    - Added alpha eye rendering including eye overlay, bloom, eye trail, and particles.
- Added cobweb block slowness immunity for spider Pokémon
- Added 49 new habitat structures. 34 as typical Minecraft structures and 15 as structures that get placed as biome features to allow for more natural placement in generation. Structures placed as biome features will not be able to be located with the `/locate structure` command.
- Vanilla village house template pools have been adjusted to account for the new abandoned village structure habitat. Weights have changed within the JSON file for every piece to ensure too many habitats don't generate in a single village.
- Added 10 new ruin structures.
- rooted_arch_ruins & crumbling_arch_ruins have been transferred over to biome features, allowing for better placement in generation.
- All ruin structures that did not generate with a gimmighoul/gilded chest before will now generate with one.
- All ruin structures gilded chest loot tables have been updated to include a guaranteed TM not learned naturally by any Pokémon through their level-up moveset. Each ruin gets its own specific TM, so you can now go hunting ruins to complete your move dex!
- All fossil structures have been transferred over to biome features, allowing for better placement in generation.
- The prehistoric_underwater_fissure & prehistoric_submerged_impact fossil structures now use the biome tag `#cobblemon:is_temperate_ocean`.
- Gimmighoul towers now generate at ground level rather than sinking one block into the ground.
- A new shipwreck cove structure called the "magma_shipwreck_cove" has been added.
- Shipwreck coves now generate slightly more frequently.
- Added equipment loot tables for shipwreck cove enemies.
- Maps from abandoned fishing boats leading to shipwreck coves now will skip explored chunks, allowing the player to find more shipwreck coves easier.
- The lush shipwreck cove's phantom mob spawner has been replaced with breezes.
- The lush shipwreck cove structure itself has been slightly cleaned up to be less noisy with the blocks it is built out of.
- All shipwreck coves' spawners have been adjusted to give compatible mobs enchanted iron or diamond armour and weapons while in its ominous state. The armour given to the mobs will also use the automaton trim. We upped the difficulty because it was too easy now that your Pokémon can fight for you.
- One TM of the elemental hyper beams has been added as guaranteed loot to the main treasure chest in every shipwreck cove. (Magma cove gets blast burn, lush cove gets frenzy plant, and submerged cove gets hydro cannon)
- Added 7 new advancements
- Pressing the inventory key now closes the Pokémon interact wheel GUI
- Pokémon cries now play when clicking Pokémon in the summary screen.
- Pressing the Summary key while either the starter selection or summary screen are open now closes those screens
- Ice type Pokémon are now immune to Packed Snow
- Held item visibility can now be controlled by the held_item_visible command argument.

### New rideable Pokémon
- Crobat
- Skarmory
- Milotic
- Archeops
- Goodra 
- Goodra (Hisuian Form)
- Drampa
- Duraludon
- Archaludon

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
- The Random Starter option is now properly handled in height calculation, so now it won't run off into other categories or be unaccessible due to scroll limits if it causes a new row
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
- Fixed Crabominable's head breaking when looking around
- Fixed Shiny Dipplin and Hydrapple's textures
- Various Structure fixes
- Fixed shiny Pokémon under certain circumstances forgetting taught TMs
- Fixed certain Pokémon sounds sometimes being doubled when played at specific times in their animations
- Fixed ball capture beam origin coming from behind the ball.
- Fixed Safari Ball catch rate outside of battle
- Fixed alpha eye (and other) particles not respecting client particle density setting in video options
- Fixed TM-Machine UI not closing when player is moving away from it
- Fixed the config category for alpha/shiny chance config fields for fossil machine and honey log slathering to Spawning
- Fixed crashes for addon species with non-Cobblemon namespaces, in evolution, fishing bait and Pokédex lookups.
- Bees now grow Apricorns and the Galarica Nut Bush, which were in the bee growables tag but never handled, and correctly spend their nectar when they do
- Saccharine Leaves can now be bone mealed
- Fixed equipping a stack of items instead of one item of a stack
- Fixed TM stack consumption in TMM screen when player teaches a move to species
- Fixed every Miltank giving mushroom stew, not only mushroom variants 
- Medicinal Leeks can now be eaten when they aren't being planted, matching their food properties.
- Sweeping attacks no longer set owned Pokémon on fire (e.g. with Fire Aspect) or knock them back.
- Fixed #updateMovesOnFormChange not properly initializing a form-swapped Pokemon's non-form_change moves.
- Fixed Oricorio Pa'u form dex description
- Fixed PC playing sound on close when opened via command

### Developer
- Evolution requirements can now set `pokedexIcon` and `pokedexTooltip` directly in their JSON to control how they're shown on the Pokédex's evolution tree, for example:
  ```json
  {
    "variant": "attack_defence_ratio",
    "pokedexIcon": "minecraft:diamond_sword",
    "pokedexTooltip": "cobblemon.ui.pokedex.requirement.attack_defence_ratio.tooltip"
  }
  ```
- Fixed the wrong order of operations in the code flow prior to the `FOSSIL_REVIVED` event causing Pokémon to appear without the entity link
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
  - Brazilian Portuguese
  - Canadian French
  - Japanese
  - Korean
  - Russian
  - Ukrainian
