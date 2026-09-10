# Changelog
## [1.8.3 (MONTH xth, 2026)](#1-8-3)

### Additions

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
- Various Structure fixes
- Fixed the `stat_compare` and `stat_equal` evolution requirements crashing the game when given `special_attack` or `special_defence`, and made them treat any non-permanent stat as a failed requirement instead of crashing.
- Fixed the Beast Ball not applying its 0.1× penalty to Pokémon that are not Ultra Beasts.
- Fixed the Moon Ball reading the world's total elapsed time instead of the time of day, so its bonus applied during the day instead.
- Fixed the Love Ball never awarding its 2.5× opposite gender bonus, and matching species by display name instead of identifier.
- Fixed the Timer Ball being weaker than a regular Poké Ball on early turns and only reaching 4× on turn 14 instead of turn 10.
- Fixed the Luxury Ball only doubling Friendship gains of 1, leaving level up and berry gains unaffected.
- Fixed NPCs getting their own TM progress file.
- Fixed the TM Machine refusing move selections that the `unlockAllMoveDexMovesByDefault` config had made visible, leaving the move slot blank and the craft button unresponsive.
- Fixed Rufflet, Braviary, and Hisuian Braviary flooding logs with errors about missing sound events.
- Fixed Pokémon property typos in Pangoro and Dedenne herd spawn files.
- Fixed form typo in Rockruff dusk spawn entry.
- Fixed a potential world gen stall with the Type Gem clusters.
- Fixed Rowap and Jacoba berries not correctly altering Pokémon sizes when used as bait
- Fixed Ampharos' eyes not emitting particles
- Fixed Alolan Raticate's cry not playing
- Fixed unintentional watermelon cosmetic for Hisuian Electrode. 
- Fixed Blastoise having an unintentional passenger seat
- Fixed odd NeoForge crash due to a dependency being outdated. It will now flag the dependency for being outdated correctly.
- Fixed form typo in Rockruff-Dusk spawn, preventing it from being able to spawn.
- Fixed a potential world gen stall caused by Type Gem clusters.
- Fixed Rowap and Jacoba berries not correctly altering Pokémon sizes when used as bait.
- Fixed Ampharos' eyes not emitting particles.
- Fixed Alolan Raticate's cry not playing.
- Fixed odd NeoForge crash due to a dependency being outdated. It will now correctly flag the dependency for being outdated.
- Fixed several incorrect light-based spawning conditions for ghosts and bugs. 
- Removed unused, placeholder habitat pools.
- Fixed Mantyke, Mantine, and Quaxly getting stuck in a recoil 'expression' during battles due to a having an expression mistakenly assigned as a recoil animation. 
- Fixed Pokémon on shoulders not animating if they are visibly holding a held item.
- Fixed Mantyke, Mantine, and Quaxly getting stuck in a recoil 'expression' during battles due to a having an expression mistakenly assigned as a recoil animation.
- Fixed Lycanroc's head movement
- Added head movement to Archaludon
- Fixed Duraludon line not having their sleep animations
- Fixed Diglett line not having their faint animations
- Fixed Kingambit moving its legs while walking
- Fixed `/spectatebattle` not starting the battle theme music, unlike spectating through the interaction wheel
- Fixed spectating not being cancelled when a battle starts for the spectator, which merged the two battles into an unplayable state, and fixed spectating a second battle leaving the first one still sending updates


### Developer

### Molang & Datapacks

### Localization
- Updated translations for:
