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
- Corrected the TM Recipes for Charge Beam and Payday
- Fixed the `stat_compare` and `stat_equal` evolution requirements crashing the game when given `special_attack` or `special_defence`, and made them treat any non-permanent stat as a failed requirement instead of crashing.
- Fixed the Beast Ball not applying its 0.1× penalty to Pokémon that are not Ultra Beasts
- Fixed the Moon Ball reading the world's total elapsed time instead of the time of day, so its moon phase bonus applied during the day
- Fixed the Love Ball never awarding its 2.5× opposite gender bonus, and matching species by display name instead of identifier
- Fixed the Timer Ball being weaker than a regular Poké Ball on early turns and only reaching 4× on turn 14 instead of turn 10
- Fixed the Luxury Ball only doubling Friendship gains of 1, leaving level up and berry gains unaffected
- Fixed NPCs getting their own TM progress file.
- Fixed the TM Machine refusing move selections that the `unlockAllMoveDexMovesByDefault` config had made visible, leaving the move slot blank and the craft button unresponsive.
- Fixed Rufflet, Braviary, and Hisuian Braviary flooding logs with errors about missing sound events.
- Fixed Pokémon property typos in Pangoro and Dedenne herd spawn files.
- Fixed form typo in Rockruff dusk spawn entry.
- Fixed a potential world gen stall with the Type Gem clusters.
- Fixed Rowap and Jacoba berries not correctly altering Pokémon sizes when used as bait
- Fixed Ampharos' eyes not emitting particles
- Fixed Alolan Raticate's cry not playing
- Fixed Pokémon cries and animation sounds played from GUIs, such as the starter selection screen and the Pokédex, being tied to the Master volume slider instead of Friendly Creatures


### Developer

### Molang & Datapacks

### Localization
- Updated translations for:
