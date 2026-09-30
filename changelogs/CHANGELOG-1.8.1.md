# Changelog
## [1.8.1 (September 13th, 2026)](#1-8-1)

### Animation updates for the following Pokémon
- Flapple, Appletun, Dipplin, Hydrapple (placeholder animations only).

### Fixes
- Fixed a potential world generation stall caused by Type Gem clusters.
- Fixed Pokémon on shoulders not animating if they are visibly holding a held item.
- Fixed NPCs getting their own TM progress file.
- Fixed odd NeoForge crash due to a dependency being outdated. It will now correctly flag the dependency for being outdated.
- Fixed the TM Machine refusing move selections that the `unlockAllMoveDexMovesByDefault` config had made visible, leaving the move slot blank and the craft button unresponsive.
- Fixed the Beast Ball not applying its 0.1× penalty to Pokémon that are not Ultra Beasts.
- Fixed the Moon Ball reading the world's total elapsed time instead of the time of day, so its bonus applied during the day instead.
- Fixed the Love Ball never awarding its 2.5× opposite gender bonus, and matching species by display name instead of identifier.
- Fixed the Timer Ball being weaker than a regular Poké Ball on early turns and only reaching 4× on turn 14 instead of turn 10.
- Fixed the Luxury Ball only doubling Friendship gains of 1, leaving level up and berry gains unaffected.
- Fixed Rufflet, Braviary, and Hisuian Braviary flooding logs with errors about missing sound events.
- Fixed Pokémon property typos in Pangoro and Dedenne herd spawn files.
- Fixed unintentional watermelon cosmetic for Hisuian Electrode. 
- Fixed Blastoise having an unintentional passenger seat.
- Fixed form typo in Rockruff-Dusk spawn, preventing it from being able to spawn.
- Fixed the `stat_compare` and `stat_equal` evolution requirements crashing the game when given `special_attack` or `special_defence`, and made them treat any non-permanent stat as a failed requirement instead of crashing.
- Fixed Rowap and Jacoba berries not correctly altering Pokémon sizes when used as bait.
- Fixed Ampharos' eyes not emitting particles.
- Fixed Alolan Raticate's cry not playing.
- Fixed several incorrect light-based spawning conditions for ghosts and bugs. 
- Removed unused, placeholder habitat pools.
- Fixed Mantyke, Mantine, and Quaxly getting stuck in a recoil 'expression' during battles due to having an expression assigned as a recoil animation. 
- Fixed Lucario's aura particles not emitting.
- Fixed Lycanroc's head movement.
- Added head movement to Archaludon.
- Fixed Duraludon line not having their sleep animations.
- Fixed Diglett line not having their faint animations.
- Fixed Kingambit moving its legs while walking.

### Localization
- Updated translations for:
  - Brazilian Portuguese
  - Simplified Chinese
  - Japanese