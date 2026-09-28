# Changelog
## [1.x.x (MONTH Xth, 2026)](#1-x-x)

### Additions
- The evolution tree can now be viewed on the Pokédex, showing a Pokémon's full family line and what's needed to evolve into each stage.

### New rideable Pokémon

### Pokémon Added

### Model updates for the following Pokémon

### Animation updates for the following Pokémon

### Changes

### Fixes
- Fixed the `stat_compare` and `stat_equal` evolution requirements crashing the game when given `special_attack` or `special_defence`, and made them treat any non-permanent stat as a failed requirement instead of crashing.
- Fixed NPCs getting their own TM progress file.
- Fixed the TM Machine refusing move selections that the `unlockAllMoveDexMovesByDefault` config had made visible, leaving the move slot blank and the craft button unresponsive.
- Fixed Rufflet, Braviary, and Hisuian Braviary flooding logs with errors about missing sound events.

### Developer
- Evolution requirements can now set `pokedexIcon` and `pokedexTooltip` directly in their JSON to control how they're shown on the Pokédex's evolution tree, for example:
  ```json
  {
    "variant": "attack_defence_ratio",
    "pokedexIcon": "minecraft:diamond_sword",
    "pokedexTooltip": "cobblemon.ui.pokedex.requirement.attack_defence_ratio.tooltip"
  }
  ```

### Molang & Datapacks

### Particles

### Localization
- Updated translations for: