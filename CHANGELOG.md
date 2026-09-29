# Changelog
## [1.x.x (MONTH Xth, 2026)](#1-x-x)

### Changes
- Pokémon learnsets have been updated to include the additional contexts up to Champions, and existing learnsets have been tweaked to apply past-gen level-up moves more generously for TM unlocks.

### Fixes
- Fixed Ampharos' eyes not emitting particles
- Fixed Alolan Raticate's cry not playing
- Fixed Spinda's level up learnset from BDSP data mixups
- Removed Freeze-Dry from Darmanitan-Zen

### TEST
### Pokémon Added
#### Generation 4
- Snover
- Abomasnow

#### Generation 7
- Grubbin
- Charjabug
- Vikavolt

#### Generation 9
- Greavard
- Houndstone

## [1.8.0 (MONTH Xth, 2026)](#1-8-0)

### Additions

### New rideable Pokémon

### Pokémon Added

### Model updates for the following Pokémon

### Animation updates for the following Pokémon

### Changes

### Fixes
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
- Fixed Lycanroc's head movement
- Added head movement to Archaludon
- Fixed Duraludon line not having their sleep animations
- Fixed Diglett line not having their faint animations
- Fixed Kingambit moving its legs while walking
### Developer

### Molang & Datapacks

### Particles

### Localization
- Updated translations for: