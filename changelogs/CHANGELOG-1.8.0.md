# Changelog
## [1.8.0 - Make Your Move Update (September 6th, 2026)](#1-8-0)
#### I think the red eyes mean they're friendly!

### Additions
- Added Alpha Pokémon
    - Alpha Pokémon are bigger, stronger, and more aggressive than regular Pokémon.
    - They are also scarier.
    - They always know a TM move and will adapt to be stronger than your party.
    - Alphas will always lead herds and spawn in herds.
- Added alpha Pokémon attraction bait effect to Hopo Berries.
- Added intrinsic scaling to all Pokémon (anywhere from 5% smaller to 5% bigger than normal). Some cases, such as shoulder mounting, will not scale so that the shoulder animations don't look silly.
- Added scaling to "baby" Pokémon (10% smaller at level 1 → normally sized at level 10).
- Added TMs.
    - Added TM Machine. TMs are made from elemental type gems, and their recipes are unlocked by having Pokémon that know the move.
    - Added config options for making TMs infinite-use and instant unlocking of all TM recipes.
    - Added Type Gem blocks as well as crystal clusters and deepslate core blocks that are used for growing elemental type gems.
- Added a new Starter Selection menu that looks way better.
- Added a "Move Dex" to the Pokédex to help identify what moves a Pokémon can learn and what Pokémon can get a move.
- Added the Habitat Block, a spawner block for servers and adventure maps that controls spawning in an area.
- Added 49 new habitat structures. In these habitats you will find rarer Pokémon and sometimes different Pokémon will appear on different days.
- Added a toast to notify players of how they can scroll through their party. Did you know you can hold R and scroll instead of using arrow keys? It's true!!!
- Added a way to obtain Porygon by inserting an Upgrade or Dubious Disc into a data monitor.
- Added Disk Rack for storing disks and being an audio sequencer for Noteblocks. What does that have to do with Pokémon? How about you mind your own business!
- Added missing crops to Botany Pots integration.
- Added conditional seats for riding. Seats can be gated by Molang such as "is_alpha".
- Added 10 new ruin structures.
- Added a new shipwreck cove structure called the Magma Shipwreck Cove.
- Added 7 new advancements.
- Pokémon cries now play when clicking Pokémon in the summary screen.
- Held item visibility can now be controlled by the `held_item_visible` command argument.
- Added config option to disable Pokémon spawning on a per-world basis.
- Added config options to award experience to fainted Pokémon for opponents they helped defeat (`awardExperienceToFaintedPokemon`) and to still award experience for defeated enemies even after battle loss/forfeit (`awardExperienceOnBattleLoss`).
- Added `/givetm` command.
- Added advancement triggers for learning TMs: `cobblemon:has_learn_specific_tm` (per-TM), `cobblemon:has_learn_all_tm` (all TMs learned), and `cobblemon:tms_learned` (count-based), backed by a new `totalTMLearnedCount` on player advancement data.
- Added `cobblemon:catch_alpha_pokemon` advancement trigger and `totalAlphaCaptureCount` stat for captured Alpha Pokémon.

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
#### Generation 3
- Wurmple
- Silcoon
- Beautifly
- Cascoon
- Dustox
- Skitty
- Delcatty
- Seviper
- Zangoose

#### Generation 4
- Cherubi
- Cherrim
- Stunky
- Skuntank

#### Generation 5
- Audino
- Pawniard
- Bisharp

#### Generation 6
- Pancham (+ Cosmetic with Black Glasses)
- Pangoro (+ Cosmetic with Black Glasses)

#### Generation 7
- Rockruff
- Lycanroc

#### Generation 8
- Rolycoly
- Carkol
- Coalossal
- Runerigus
- Snom
- Frosmoth
- Indeedee
- Duraludon
- Applin
- Flapple
- Appletun

#### Generation 9

- Nymble
- Lokix
- Kingambit
- Dipplin
- Archaludon
- Hydrapple

#### Regional Forms
- Sandshrew (Alolan Form)
- Sandslash (Alolan Form)
- Yamask (Galarian Form)
- Avalugg (Hisuian Form)

### Model updates for the following Pokémon

- Sandshrew
- Sandslash
- Nidoking
- Zubat
- Golbat
- Crobat
- Ditto
- Porygon
- Porygon2
- Porygon-Z
- Skarmory
- Smoochum
- Jynx
- Popplio
- Brionne
- Primarina
- Fletchling
- Fletchinder
- Talonflame
- Sableye
- Mawile
- Goomy
- Sliggoo
- Goodra
- Nacli
- Naclstack
- Garganacl
- Bidoof
- Cranidos
- Chatot
- Yamask
- Cofagrigus
- Tyranitar
- Turtonator
- Golett
- Corsola
- Arbok (New Patterns: Angry, Cartoon, Clown, Comedic, Crow, Grin, Ink, Modern, Rain, Scribble, Silence, Sloth, Suspicious, Will)
- ... and Substitute

#### Model changes to the regional bias resource pack:
- Added Mime Jr. (Galar Bias)
- Added Bergmite (Hisui Bias)
- Changed models for every existing regional bias

### Animation updates for the following Pokémon

- Kakuna
- Grimer
- Nidoran♀ (+ New shoulder mount)
- Nidorina
- Nidoran♂ (+ New shoulder mount)
- Sandshrew
- Diglett
- Dugtrio
- Graveler
- Happiny
- Chansey
- Blissey
- Poliwhirl
- Poliwrath
- Zubat
- Vulpix
- Drowzee
- Hypno
- Noctowl
- Skarmory
- Skitty
- Flygon
- Shellos
- Riolu
- Lucario
- Purrloin
- Liepard
- Joltik
- Galvantula
- Nidoking
- Fletchling
- Fletchinder
- Talonflame
- Archeops
- Drampa
- Sewaddle
- Swadloon
- Leavanny
- Goomy
- Sliggoo
- Goodra
- Shedinja
- Skwovet
- Greedent
- Bidoof
- Cranidos
- Chatot
- Yamask
- Cofagrigus
- Luvdisc
- Staryu
- Starmie
- Togetic
- Rellor
- Rabsca
- Yungoos
- Gumshoos
- Cottonee
- Whimsicott
- Corsola

#### Added sounds to the following moves

- Absorb
- Aurora Beam
- Double Team
- Draco Meteor
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
- Removed the penalty for block destruction speed when on a hover mount, making them better for digging.
- Golden Apples can now be crafted using the Applin Apples.
- Prismarine Crystals and Amethyst Shards can now be used to craft Pokedexes.
- Updated Pokédex Screens item tag to support tagged variants of Redstone and Glowstone Dust.
- Sharp Beak can now be crafted using bones and Flying Gems.
- Metal Alloy can now be crafted using iron and Thunder Stones.
- Ability Capsules can now be brewed from Medicinal Brew and Dragon Breath.
- Using an Ability Capsule will now return an Empty Bottle.
- Decreased the default pastured wander radius to 32 blocks.
- Added alternate versions of the basic PC Wallpapers based on the original design of Cobblemon's PC, before public release.
- Rooted Arch Ruins, Crumbling Arch Ruins, and all fossil structures have been improved so that they generate more naturally in terrain.
- Gimmighoul towers now generate at ground-level rather than sinking one block into the ground.
- All ruin structures that did not generate with a gimmighoul/gilded chest before will now generate with one.
- All ruin structures gilded chest loot tables have been updated to include a guaranteed TM not learned naturally by any Pokémon through their level-up moveset. Each ruin gets its own specific TM, so you can now go hunting ruins to complete your move dex!
- The Prehistoric Underwater Fissure and Prehistoric Submerged Impact fossil structures now use the biome tag `#cobblemon:is_temperate_ocean`.
- The lush shipwreck cove's phantom mob spawner has been replaced with breezes.
- Shipwreck coves now generate slightly more frequently.
- Added equipment loot tables for shipwreck cove enemies.
- Maps from abandoned fishing boats leading to shipwreck coves now will skip explored chunks, allowing the player to find more shipwreck coves more easily.
- All shipwreck coves' spawners have been adjusted to give compatible mobs enchanted iron or diamond armour and weapons while in its ominous state. The armour given to the mobs will also use the automaton trim. We upped the difficulty because it was too easy now that your Pokémon can fight for you.
- One TM of the elemental hyper beams has been added as guaranteed loot to the main treasure chest in every shipwreck cove. (Magma Cove gets Blast Burn, Lush Cove gets Frenzy Plant, and Submerged Cove gets Hydro Cannon.)
- The following items can now be obtained by brushing the following Pokémon:
    - Pink Wool - Wigglytuff
    - White Wool - Swablu, Altaria
    - Shed Shell - Ekans, Arbok, Dratini, Dragonair, Dunsparce, Treecko, Grovyle, Sceptile, Seviper, Scraggy, Scrafty, Helioptile, Heliolisk, Salandit, Salazzle, Sobble, Drizzile, Inteleon, Silicoblra, Sandaconda, Toxel, Toxtricity, Orthworm, Dudunsparce
    - Phantom Membrane - Dreepy, Drakloak, Dragapult
- The following items can now be obtained by evolving the following Pokémon into any of their evolutions:
    - Turtle Scute - Squirtle, Wartortle, Turtwig, Grotle, Tirtouga, Chewtle
    - Shed Shell - Metapod, Kakuna, Ekans, Paras, Venonat, Krabby, Scyther, Kabuto, Dratini, Dragonair, Ledyba, Spinarak, Yanma, Pineco, Dunsparce, GLigar, Pupitar, Treecko, Grovyle, Silcoon, Cascoon, Surskit, Nincada, Trapinch, Vibrava, Corphish, Anorith, Bagon, Shelgon, Kricketot, Burmy, Combee, Skorupi, Swadloon, Whirlipede, Dwebble, Scraggy, Karrablast, Joltik, Larvesta, Spewpa, Clauncher, Helioptile, Charjabug, Crabrawler, Cutiefly, Dewpider, Salandit, Wimpod, Poipole, Sobble, Drizzile, Dottler, Silicobra, Toxel, Sizzlipede, Snom, Tarountula, Nymble, Rellor
- Saccharine Leaves now drop Tart, Sweet, or Syrupy Apples, depending on their honey coating stage, instead of vanilla Apples.
- Fixed the height of the Tumblestone Bud items when held in hand, placed on head, viewed in a GUI, and placed in an Item Frame.
- Refactored all advancements, removing several deemed unnecessary. Due to this cleanup, all previously obtained advancements will be reset besides the root (for picking a starter Pokémon). This sucks, and we're sorry. We had to do this eventually so better now than later.
- Forces between colliding hitboxes now factors in the weight of Pokémon.
- Improved berry tree performance on singleplayer and servers.
- Updated stat increase and decrease particle effects with the addition of alpha boost particles.
- Improved the performance of pasture blocks on singleplayer and servers.
- Changed the bait effects of the following items: (Vanilla) Sweet & Glow Berries, Golden & Enchanted Golden Apples (Cobblemon) Custap, Eggant, Hopo & Micle Berries, Sweet Hearts.
- Wild Pokémon now reliably have at least one offensive move if they can have offensive moves at that level. No more free lunches with Pokémon that can't even hurt you.
- Data Monitor can accept Disks (TMs, Music Disks, Upgrade, Dubious Disk, etc.) to change what's being displayed on the screen.
- The party overlay's experience-gained animation now displays the amount of EXP the Pokémon gained.
- Pressing the inventory key now closes the Pokémon interact wheel GUI.
- Updated the sound for harvesting Apricorns.
- Lowered the volume of harvesting berries.
- Improved compatibility with various mods
    - Farmer's Delight's cutting board can now be used to strip Apricorn and Saccharine's logs, or salvage their planks from various blocks. It can also separate their boats from chests.
    - Farmer's Delight's cutting board can be used to get two white dye from Pep-Up Flowers and two magenta dye from Medicinal Leeks.
    - Farmer's Delight's cutting board can separate Tumblestone Blocks back into Tumblestones.
    - Farmer's Delight's cutting board can salvage Type Gems from their Gem Blocks.
    - Farmer's Delight's Onions, Tomatoes, and Cabbages can be used to cook Open-Faced Sandwiches. Nature's Spirit's Olives also can be used.
    - Farmer's Delight's cutting board can be used on Hearty Grains to obtain rice and straw, similarly to Farmer's Delight's Rice Panicles.
    - Farmer's Delight's Apple Pies and Apple Ciders can now be made using Applin Apples, alongside the Vanilla counterpart.
    - Amendments allows for cooking Leek and Potato Stew in a Cauldron.
    - Any mods that utilise Moonlight's Soft Fluid system can store Leek and Potato Stew, Medicinal Brew, Antidote, Awakening, Burn Heal, Ice Heal, Paralyze Heal, Full Heal, Potion, Super Potion, Hyper Potion, Max Potion, Full Restore, Ability Capsules, Moomoo Milk, and Berry Juice in liquid form (such as Suplementaries' Jar or Goblet, or Cauldrons while using Amendments).
    - Supplementaries' Hourglass can now use Bright Powder, Quick Powder, Metal Powder, Heal Powder, Silver Powder, and Remedy.
    - Berry Trees are now compatible with the Planter properly
    - Saccharine Way Signs (added by Supplementaries automagically) now use a premade texture to prevent odd visual issues.
    - Supplementaries' Jar can now store various Cobblemon cookies, candies, and similar items.
    - Some of Supplementaries' items have been added to the `#cobblemon:held/container_held_items` item tag, including sacks, safes, and lunch baskets.
    - Experience Nuggets can be used in place of Sculk for making the EXP Candies if using Create.
    - Rage Candy Bar has a Create Mixing recipe.
    - All Cobblemon ores can now be crushed in Create's Crushing Wheels. All Gem and Tumblestone Clusters can be crushed for automatic, low return, gems.
    - Hearty Grains can be milled like Farmer's Delight Rice Panicles if both Farmer's Delight and Create are installed.
    - Big Roots and Revival Herbs can be milled to their respective powders.
    - Pep-Up Flowers, Medicinal Leeks, and all 70 berries can be milled in Create's Millstone for additional dye drops.
    - Auspicious Armour and Upgrades can be Haunted using Create's crafting systems.
    - Applin Apples can be used to make Honeyed Apples in Create.
- Sounds for interacting with various Cobblemon blocks now play from the centre of the block, rather than the corner. You probably didn't notice.
- Dragonite's mailbag cosmetic can now also be obtained with a chest in the cosmetic slot.
- Adjusted the walk speed for Croagunk and Aerodactyl.
- The `/pc` command no longer plays PC activation sounds
- Pressing the Summary key while either the starter selection or summary screen are open now closes those screens.
- Ice type Pokémon are now immune to Packed Snow.
- Added cobweb block slowness immunity for spider Pokémon

### Fixes
- Fixed rare Pokémon being far too likely to spawn multiple times in a single spawn cycle. The dev told me that the exact bug is too complicated to explain.
- Fixed Omanyte and Omastar incorrectly using water animations on land.
- Fixed Pokédex layout for genderless variations.
- Fixed odd stacking behaviour with Poké Baits.
- Fixed a compatibility issue with World Game Rules which caused some log spam in specific cases.
- Fixed improper validation of Pokémon when performing a trade.
- Fixed an issue where particles like beams can disappear when you don't see the beginning of the beam, or you look away.
- Fixed Bedrock particle materials sometimes being incorrectly invisible from some sides.
- Fixed an occasional crash on retrieving revived fossils.
- Fixed brushing not working on Neoforge. Again.
- Fixed braking not working for submarine-style mounts (whoops).
- Fixed strafing not having the same friction as forward movement (funny whoops).
- Fixed submarine-style mounts' surface-water animations not playing when supposed to.
- Fixed submarine-style mounts' camera freaking out when looking up or down on the surface.
- Fixed pastured Pokémon despawning and running away.
- Fixed Apricorn trees crashing when other mods bonemeal them.
- Fixed the issue that caused hover-style mounts without a ground style to be rendered useless upon touching the ground on a server.
- Fixed horizontal and vertical collisions with hover-style mounts so they no longer accumulate or retain momentum.
- Fixed NeoForge brewing stand unexpectedly crashing users when shift clicking items within its menu. Well, I suppose all crashes are unexpected. You know what I mean.
- Fixed NPCs switching Pokémon very often during battles.
- Fixed Campfire Pot causing crashes under certain circumstances.
- Fixed Type Gems not being consumed upon use in battles.
- Fixed several Ancient Pokéball's not using the correct capture multiplier.
- Fixed a server warning that could occur when recalling Pokémon.
- Fixed sliding particles incorrectly handling block collision.
- Fixed Full Heal not curing status conditions in battle. Literally it's one job.
- Fixed a rare issue with battles locking up when you are forced to switch, with the switch menu appearing instantly.
- Fixed the combined effects of Pledge moves having broken Showdown message handling and localisation.
- Fixed a very niche issue where structures would spawn in an incorrect location, creating very funky world gen.
- Fixed Galarian Ponyta missing its cry.
- Fixed various log warnings caused by redundant files and sound events.
- Traded Pokémon will now retain the original friendship level when traded back to its OT.
- Tumblestone Clusters now display correctly on maps.
- Fixed removing mulch from berry trees with a shovel not working. That was meant to be a thing.
- Fixed collecting honey from Saccharine Leaf blocks deleting Honey Bottles when the inventory was full.
- Fixed Stripped Saccharine Wood recipe being... not there.
- Fixed some performance issues with a kind of sound event causing lag by attempting to create sounds every tick when out of range of hearing.
- Fixed stat-reducing Aprijuice not being possible to apply on a maxed out Pokémon. Also fixed the possibility of going into negatives.
- Fixed exploit where you could take out the bait or drop your Pokérod with bait to avoid consuming bait, now the bobber will just disappear if you do so.
- Fixed Apricorn and Saccharine boats incorrectly using chest boat models.
- Fixed wild and trainer battle AI refusing to select moves with the `adjacentAlly` target type, even when it's their only option.
- Fixed gilded chests being more accessible than intended.
- Fixed fished up Pokémon not being added as "seen" to the Pokédex.
- Fixed surprised Gimmighoul encounters not being added as "seen" to the Pokédex.
- Fixed a crash involving berry trees that have been world edited.
- Fixed visual desync in multiplayer for players mounting Pokémon outside other players' loaded chunks.
- Fixed Pokémon entities leveled by rare candy not getting stronger as Minecraft entities unless recalled.
- Fixed a freeze during game load that occurred when multiple mods tried to load Cobblemon API classes at the same time.
- Lowered the volume of Blitzle and Zebstrika's cry (shut up!).
- Regional Foods will now prioritise being eaten instead of used for healing Pokémon - shift clicking will use it as healing.
- Resolved z-fighting (texture flickering) on Pokédex Models.
- Restored Ninjask's ability to do barrel rolls.
- Fixed Raichu (male) and Alolan Raichu (both genders) not displaying the correct Partner Cap when Pewter Crunchies are in the cosmetic slot.
- Fixed a crash caused by adding enchanted items to the Resurrection Machine.
- Fixed a crash that could occur when starting a PvN battle with only fainted Pokémon.
- Fixed the Remember Riding Camera config not being respected when dismounting and resolved the incompatibility with the Auto Third Person mod.
- Fixed Full Heal block not dropping Full Heals. Lmao.
- Fixed status cure items not returning an empty bottle when used.
- Fixed Panpour consuming its held item when evolving with a Water Stone.
- Fixed Smoked Tail Curry returning a Wooden Bowl when consumed despite it not being included in the recipe. Free bowls!
- Fixed `/giveallpokemon` not respecting the min and max arguments.
- Fixed the choose starter toast not disappearing when capturing a Pokémon instead of opening the starter selection screen.
- Fixed an issue where certain Sinister Tea seasonings were only applying as instant effects and would not apply over time.
- Long config option labels now scroll instead of overflowing in the configuration menu.
- Fixed some z-fighting and clipping on Dewott and Hisui-Bias Dewott.
- Eggant Berries can now be composted properly on NeoForge.
- Composting chances are now the same on NeoForge as on Fabric.
- Fixed problem where furnaces which had smelted Revival Herbs into Heal Powder in older versions would glitch out on trying to get the experience out of the block.
- Fixed the Battle AI not recognizing Surging Strikes as a multistrike move.
- Fixed Tentacool not using its swim animation when on water surface.
- Fixed Eevee using its left shoulder pose when in battle.
- Fixed Magikarp and Gyarados Jump Pattern resourcepacks replacing shiny textures of addons targeting Magikarp or Gyarados.
- Fixed Combee's pollinated texture overwriting the shiny and female textures.
- Fixed a colour mistake with Dracovish's shiny.
- Fixed Togedemaru's back spikes z-fighting.
- Fixed Sliggoo (+ Hisuian) evolving in the Nether and End where it can't actually rain.
- Corrected the scales, hitbox sizes, and eye heights for Ariados, Cetoddle, Durant, Galvantula, Klink, Lanturn, Lumineon, Mantine, Mantyke, Mudbray, Numel, Staravia, and Torkoal.
- Fixed Torkoal's walk speed not matching its animation. He will no longer move faster than Bunnelby and Buneary. This tortoise would've won.
- Fixed Lanturn T-posing due to missing ground walk and sleep animations.
- Fixed Oshawott and Dewott using their sleep animation in unintended scenarios.
- Fixed Dudunsparce and Maushold always having the same form instead of having the 1 in 100 chance of being different.
- Fixed bird mounts rapidly switching between their flying and gliding animations when flying at low speed.
- Fixed some battles being startable with fainted Pokémon in the initial sendout.
- Fixed random gender selection for forms with strict gender divides (such as Meowstic and Oinkologne) never choosing female (that's sexist).
- Fixed the "Press R to battle" prompt not disappearing after you finish your first battle, at least until you relogged.
- Fixed the composter block being listed as "composer" in the cosmetic items species feature
- Fixed Castform's forms being tagged as introduced in generation 4 instead of 3.
- Fixed Blitzle's base scale being `1` rather than the intended `0.8`.
- Fixed Magnezone's emissive layer for the shiny having the wrong name, thus displaying wrongly.
- Fixed Litleo not blinking.

### Developer
- Added `PokeSnackSpawnerFactory` which allows influence registration on PokeSnack encounters.
- Added `FishingSpawnerFactory` which allows influence registration on fishing encounters.
- Added `BattleFleeAttemptEvent` fired from `PokemonBattle.checkFleeAttempt`, allowing developers to intercept and control flee attempts.
- Tweaked the spawning selector interface to take a bucket function rather than a bucket directly.
- Added a new `cobblemon:height_range` processor type to allow developers to restrict structure-spawning ranges.
- Separated all the functions of MoLangFunctions.kt into their own files.
- Spawn buckets and their base weights are now provided by the spawner, with different base weights for world, fishing, snack, and habitat spawn buckets.
- Removed "SpawnBucket" class in favour of just storing maps of bucket names to weights.
- Fixed nicknames not being applied to Pokémon spawned from `spawn_pool_world` files.
- Added support for `profilePokedexScale`, `profilePokedexTranslation`, `profileSummaryScale`, and `profileSummaryTranslation` in poser files for finer control over profile displays.
- Fixed riding camera offsets defined on Pokémon forms not being applied.
- Marks can now provide aspects.

### Molang & Datapacks
- Added `moveset_builders` datapack folder for different styles of movesets.
- Added `party_pools` datapack folder for defining groups of Pokémon for complex NPC party definitions.
- Added `party_compositions` datapack folder for arranging parties on NPCs that rely on party pools.
- Added `composed_pool` NPC party provider type which combines a party pool with a party composition to create more complex NPC parties.
- Added basic chatter NPC Behaviour.
- Added `q.item.is_enchanted()`.
- Added `q.item.has_enchantment(minecraft:sharpness, 3)`.
- Added `has_chosen_starter` and `get_starter_uuid` as available Molang functions.
- Added `has_tm_move_unlocked`, `unlock_tm_move`, and `lock_tm_move` as available Molang functions for Players.
- Added `marks`, `has_mark`, and `remove_marks` as available Molang functions for Pokémon.
- Added Data Component support for species drops, eg `"components": {"minecraft:custom_model_data": 123}`
- Added the new `sortOrder` field to mark entries, (defaults to 0 if not specified) which controls the order in which marks are rendered on the HUD, with higher numbers being rendered on top of lower numbers.
- Added optional `order` field to starter categories. It will allow explicitly sorting categories in the starter selection screen.
- SpeciesAdditions append vs override now matches the wiki documentation.
- Added `weighted_choice` as a new feature type. It requires setting weights for each choice and allows developers to simplify the process of assigning random aspect choice based on marble chance.
- Added `q.world.spawn_loot_table_items(loot_table_id, x, y, z)` which spawns the items from the loot table at the position. Use type: `minecraft:chest` for the loot table.
- Added `q.pokemon.entity` to access the entity of Pokémon.
- Flamethrower and Seismic Toss had their action effect JSON cleaned up.
- Ride sounds can now be set as exclusive to passengers.
- Ride sounds no longer play when submerged unless specified with a new setting.
- NPC Trainers can now call a `command` animation when issuing a command in battle.
- Added a q.failed function to move action effects.
- Herd spawned Pokémon have their levels controlled differently. The `levelRange` property now acts as a pre-offset clamp instead of a filter, with a new `herdLevelRange` property to do the filter behaviour.
- `q.sound` now accepts three parameters (sound event, volume, and pitch) instead of just the sound event and pitch.
- Added a new layer property that makes a layer scroll in a direction. "scrolling": {"speedU": 0.1, "speedV": 0.1}
- Cleaned up seat data. Removed all now unused fields from seats in all species files. Also made locator seat names explicit in the species files (however, this doesn't break the legacy format)

### Particles

#### New Move Animations
- Blizzard
- Disarming Voice
- Draco Meteor
- Dragon Breath
- Dragon Rage
- Drain Punch
- Fairy Wind
- Fire Fang
- Gust
- Horn Drill
- Hydro Pump
- Ice Fang
- Leafage
- Leaf Storm
- Light Screen
- Night Shade
- Play Rough
- Pyro Ball
- Reflect
- Rock Slide
- Sonic Boom
- Sucker Punch
- Supersonic
- Thunder Fang

#### Tweaks and Fixes (Moves)

- Confusion
- Dragon Claw
- Eruption
- Fire Punch
- Flame Burst
- Flame Charge
- Flamethrower
- Frost Breath
- Hypnosis
- Ice Punch
- Icy Wind
- Mud Sport
- Poison Fang
- Protect
- Psychic
- Quick Attack
- Rage Powder
- Razor Leaf
- Sand Attack
- Scratch
- Shadow Claw
- Sing
- Spore
- String Shot
- Tackle
- Tail Whip
- Thunder
- Thunder Punch
- Thunder Wave
- Water Gun
- Water Pulse
- Water Sport

#### Miscellaneous Particles

- Added Wild Might particles for Alpha Pokémon.
- Updated stat increase and decrease particles.

### Localization
- Updated translations for:
    - Japanese
    - German
    - Spanish
    - Ecuadorian Spanish
    - Portuguese
    - Brazilian Portuguese
    - Simplified Chinese
    - Traditional Chinese
    - Korean
    - Russian