# Changelog
## [1.x.x (MONTH Xth, 2026)](#1-x-x)

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
- Fixed the `stat_compare` and `stat_equal` evolution requirements crashing the server when given `special_attack` or `special_defence`, and made them treat any non-permanent stat as a failed requirement instead of crashing
- Fixed NPCs getting their own TM progress file.
- Fixed the TM Machine refusing move selections that the `unlockAllMoveDexMovesByDefault` config had made visible, leaving the move slot blank and the craft button unresponsive.
- Fixed Rufflet, Braviary, and Hisuian Braviary flooding logs with errors about missing sound events.
- Fixed pokemon property typos in Pangoro and Dedenne herd spawn files.
- Fixed form typo in Rockruff dusk spawn entry.
- Fixed a potential world gen stall with the Type Gem clusters.

### Developer

### Molang & Datapacks

### Particles

### Localization
- Updated translations for: