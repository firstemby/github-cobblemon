# Changelog
## [1.8.0 (MONTH Xth, 2026)](#1-8-0)

### Additions
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
- Added Move Dex to Pokedex
- Added the Habitat Block, a spawner block for servers and adventure maps that controls spawning in an area.
- Added Disk Rack for storing disks and being an audio sequencer for Noteblocks
- Added intrinsic scaling to all mons (+-5%). Some rendering cases such as shoulder mounting revert to 1.0 scaling to preserve animation accuracy.
- Added scaling to "baby" pokemon (smaller (-20%) at level 1 -> normal sized at level 10)
- Added conditional seats for riding. Seats can be gated by molang such as "is_alpha".
- Added Alpha Pokemon
    - Added alpha scaling (alphas are larger than thier normal counterparts)
    - Alphas will always lead herds and will spawn in herds
    - Alphas spawn with 2 random TM moves from their learnset
    - Alpha pokemon will always defend themselves from players that attack them
    - Added alpha level matching and stat boost (based on level)
    - Added pitch-down and reverb to alpha cries
    - Added alpha pokemon bait effect
    - Added alpha eye rendering including: eye overlay, bloom, eye trail, and particles.
- Added cobweb block slowness immunity for spider Pokemon
- Added 49 new habitat structures. 34 as typical Minecraft structures and 15 as structures that get placed as biome features to allow for more natural placement in generation. Structures placed as biome features will not be able to be located with the "/locate structure" command.
- Vanilla village house template pools have been adjusted to account for the new abandoned village structure habitat. Weights have changed within the json file for every piece to ensure too many habitats don't generate in a single village.
- Added 10 new ruin structures.
- rooted_arch_ruins & crumbling_arch_ruins have been transferred over to biome features, allowing for better placement in generation.
- All ruin structures that did not generate with a gimmighoul/gilded chest before will now generate with one.
- All ruin structures gilded chest loot tables have been updated to include a guaranteed TM that is not learned naturally by any Pokemon through their level-up moveset. Each ruin gets its own specific TM, so you can now go hunting ruins to complete your move dex!
- All fossil structures have been transferred over to biome features, allowing for better placement in generation.
- The prehistoric_underwater_fissure & prehistoric_submerged_impact fossil structures now use the biome tag "#cobblemon:is_temperate_ocean".
- New Biome tag called "#cobblemon:is_stony_beach" has been added.
- Gimmighoul towers now generate at ground level rather than sinking one block into the ground.
- 1 New shipwreck cove structure called the "magma_shipwreck_cove" has been added.
- Shipwreck coves now generate slightly more frequently.
- Added equipment loot tables for shipwreck cove enemies.
- Maps from abandoned fishing boats leading to shipwreck coves now will skip explored chunks, allowing the player to find more shipwreck coves easier.
- The lush shipwreck cove's phantom mob spawner has been replaced with breezes.
- The lush shipwreck cove structure itself has been slightly cleaned up to be less noisy with the blocks it is built out of.
- All shipwreck coves' spawners have been adjusted to give compatible mobs enchanted iron or diamond armor and weapons while in its ominous state. The armor given to the mobs will also use the automaton trim. We upped the difficulty because it was too easy now that your Pokémon can fight for you.
- One TM of the elemental hyper beams have been added as guaranteed loot to the main treasure chest in every shipwreck cove. (Magma cove gets blast burn, lush cove gets frenzy plant, and submerged cove gets hydro cannon)

#### Added sounds to the following moves
- Absorb
- Aurora Beam
- Double Team
- Fire Punch
- Fire Spin
- Flame Charge
- Flame Wheel
- Giga Drain
- Ice Punch
- Leech Life
- Magical Leaf
- Mega Drain
- Minimize
- Poison Gas
- Rock Throw
- Scary Face
- Seed bomb
- Smokescreen
- String Shot
- Tail Whip

### Changes
- Data Monitor can accept Disks (TMs, Music Disks, Upgrade, Dubious Disk, etc) to change look
- Rider positioning is now client-driven (Remove poseOffsets and offset per seat/pose) in favor of sending real time passenger position.
- Cleaned up seat data. Removed all now unused fields from seats in all species files. Also made locator seat names explicit in species file (however, this doesn't break legacy format)
- Marks can now provide aspects.
- Added new shiny and Alpha chance configs

### Fixes
- Fixed rare Pokémon being far too likely to spawn multiple times in a single spawn cycle. The dev told me that the exact bug is too complicated to explain.
- Resolved the niche issue where Structures would spawn in an incorrect location, creating very funky world gen.
- Fixed Galarian Ponyta missing its cry.
- Fixed various log warnings caused by redundant files and sound events.

### Developer
- Tweaked the spawning selector interface to take a bucket function rather than a bucket directly.
- Added a new `cobblemon:height_range` processor type to allow developers to restrict structure spawning ranges.
- Separated all the functions of MoLangFunctions.kt into their own files.

### Molang & Datapacks
- Added q.world.spawn_loot_table_items(loot_table_id, x, y, z) which spawns the items from the loot table at the position. (Use type: "minecraft:chest" for the loot table.)
- Added q.pokemon.entity to access the entity of pokemon.
- Flamethrower and Seismic Toss had their action effect json cleaned up

### Particles

### Localization
- Updated translations for: