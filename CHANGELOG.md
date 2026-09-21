# Changelog
## [1.8.2 (MONTH xth, 2026)](#1-8-2)

### Additions
- The following items can now be placed on the ground as blocks:
  - King's Rock, Galarica Cuff, Unremarkable Teacup, Masterpiece Teacup, Chipped Pot, Cracked Pot, Sachet, Metal Powder, Quick Powder, Bright Powder, Icy Rock, Heat Rock, Smooth Rock, Damp Rock, Destiny Knot, Luminous Moss, Never-Melt Ice, Wide Lens, Scope Lens, and Zoom Lens.
  - Iron Ball, Metal Coat, Throat Spray, HP Up, Protein, Iron, Carbos, Calcium, Zinc, PP Up, PP Max, Upgrade, Dubious Disc, and Loaded Dice.
- The following items can now be placed on various surfaces:
  - Galarica Wreath, Magnet, Flame Orb, Life Orb, Toxic Orb, Light Ball, and Smoke Ball.
- The following items can now be placed as blocks, and have additional functionality:
  - Ring Target, Metronome, and Eject Button.
- The following items can now be worn by Players and Pokémon:
  - Galarica Wreath, Cleanse Tag, Spell Tag, Sharp Beak, Covert Cloak, Shell Helmet, and Never-Melt Ice.
- Wearable items can now be equipped by a player by holding it and right-clicking (or rebound use/interact)
  - Wearable items that can be placed down are exempt from this behaviour.

### New rideable Pokémon

### Pokémon Added

#### Regional Forms

### Model updates for the following Pokémon

### Animation updates for the following Pokémon

#### Added sounds to the following moves

### Changes
- Updated various recipes to display in Recipe Groups within the Recipe Book
- Updated most recipes to display more logically/consistently within the Recipe Book's categories
- Make ride rotations less jittery in multiplayer.

### Fixes
- Correctly grants "They're Minerals!" advancement when planting a type gem
- Type Gem Blocks now have advancements to give you the recipe in the recipe book (the data for the advancements were inside the recipes somehow??)
- Some other recipes without advancements have also been corrected
- Corrected the TM Recipes for Charge Beam and Payday
- Type Gem blocks now properly generate clusters off of the block.
- Starter Screen Categories will no longer stop rendering if they are bigger than the viewport, and you are not viewing top or bottom edges 
- Random Starter option now properly handled in height calculation, so now it won't run off into other categories or be un-accessible due to scroll limits if it causes a new row
- Mochi and Feathers no longer drop glass bottles on use
- Fixed Blitzle's base scale being `1` rather than the intended `0.8`
- Fixed Magnezone's emissive layer for the shiny having the wrong name, thus displaying wrongly
- Fixed Litleo not blinking
- Fixed Omanyte and Omastar incorrectly using water animations on land.
- Fixed Pokédex layout for genderless variations
- Fixed odd stacking behaviour with the poke_bait item.
- Fixed a compatibility issue with World Game Rules which caused some log spam in specific cases.
- Fixed improper validation of Pokémon when performing a trade.
- Fixed an issue where particle like beam can disappear when you don't see the beginning of the beam, or you look away
- Fixed Bedrock particle materials (alpha/opaque/blend/add) sharing one render batch, which caused incorrect culling on mixed batches; each material now batches and draws with its own blend and cull state, and the alpha material renders as a true cutout
- Fixed an occasional crash on retrieving revived fossils
- Fixed brushing not working on Neoforge
- Fixed braking not working for submarines (whoops).
- Fixed strafing not having the same friction as forward movement (funny whoops).
- Fixed submarine's surface-water animations not playing when supposed to.
- Fixed submarine camera freaking out when looking up or down on the surface.
- Fixed pastured mons despawning and running away.
- Fixed Apricorn trees crashing when other mods bonemeal it
- Fixed the issue that caused Hover Behaviour rides without a ground style to be rendered useless upon touching the ground on a server.
- Removed the "inAir" penalty for block destruction speed when on a hover mount.
- Fixed horizontal and vertical collisions with hover mounts so they no longer accumulate or retain momentum
- Fixed NeoForge brewing stand unexpectedly crashing users when shift clicking items within its menu
- Fixed NPCs switching Pokémon very often during battles
- Fixed Campfire pot causing crashes under certain circumstances
- Fixed Type Gems not being consumed upon use
- Fixed Ancient Pokéball's to their respective modifiers
- Fixed a Server warning when recalling Pokémon
- Fixed sliding particles incorrectly handling block collision
- Fixed Full Heal not curing status conditions in battle
- Fixed a rare issue with battles locking up when you are forced to switch, with the switch menu appearing instantly.
- Fixed the combined effects of Pledge moves having broken Showdown message handling and localisation
- Fixed rare Pokémon being far too likely to spawn multiple times in a single spawn cycle. The dev told me that the exact bug is too complicated to explain.
- Resolved the niche issue where Structures would spawn in an incorrect location, creating very funky world gen.
- Fixed Galarian Ponyta missing its cry.
- Fixed various log warnings caused by redundant files and sound events.
- Traded Pokémon will now retain the original friendship level when traded back to its OT
- Clusters now display correctly on maps
- Fixed removing mulch from berry trees with a shovel not working.
- Fixed collecting honey from Saccharine Leaf blocks deleting Honey Bottles when the inventory was full
- Fixed Stripped Saccharine Wood recipe being missing
- Fixed CancelableSoundInstance causing lag by attempting to create sounds every tick when out of range of hearing
- Fixed Stat Reducing Aprijuice not being possible to apply on a max out Pokémon, and the possibility of going into negatives
- Fixed Exploit where you could take out the bait or drop your Pokérod with bait to avoid consuming bait, now the bobber will just disappear if you do so
- Fixed Apricorn and Saccharine boats incorrectly using chest boat models
- Fixed wild and trainer battle AI refusing to select moves with the `adjacentAlly` target type, even when it's their only option
- Fixed gilded chests being more accessible than intended
- Fixed fished up Pokémon not being added as "seen" to the Pokedex
- Fixed surprised Gimmighoul encounters not being added as "seen" to the Pokedex
- Fixed a crash involving berry trees that have been world edited.
- Fixed visual desync in multiplayer for players mounting Pokémon outside other players' loaded chunks
- Fixed Pokémon entities leveled by rare candy not getting stronger as Minecraft entities unless recalled.
- Fixed a freeze during game load that occurred when multiple mods tried to load Cobblemon API classes at the same time.
- Fixed random tick crashes from berry trees
- Lowered the volume of Blitzle and Zebstrika's cry
- Regional Foods will now prioritise being eaten instead of used for healing Pokémon, shift clicking will use it as healing
- Resolved Z-Fighting on Pokédex Models
- Restored Ninjask's ability to do barrel rolls
- Fixed Raichu (male) and Alolan Raichu (both genders) not displaying the correct Partner Cap when Pewter Crunchies are in the cosmetic slot.
- Fixed a crash caused by adding enchanted items to the Resurrection Machine
- Fixed a crash that could occur when starting a PvN battle with only fainted Pokémon.
- Fixed the Remember Riding Camera config not being respected when dismounting and resolved the incompatibility with the Auto Third Person mod
- Fixed Full Heal block not dropping Full Heals
- Fixed status cure items not returning an empty bottle when used
- Fixed Panpour consuming its held item when evolving with a Water Stone
- Fixed Smoked Tail Curry returning a Wooden Bowl when consumed despite it not being included in the recipe
- Fixed `/giveallpokemon` not respecting the min and max parameters
- Fixed the choose starter toast not disappearing when capturing a Pokémon instead of opening the starter selection screen
- Fixed an issue where certain Sinister Tea seasonings were only applying as instant effects and would not apply over time
- Fixed a rare set of concurrency bugs related to reloading packs on NeoForge; fixes a ModernFix issue

### Developer

### Molang & Datapacks
- A new `negate` variant for evolution requirements has been added, which will negate (invert) the provided inner condition.

### Particles

#### New Move Animations

### Localization
- Updated translations for: