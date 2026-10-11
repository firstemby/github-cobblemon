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
- Restructured Botany Pots integration folder structure
- Decrease default pastured mon wander radius to 32.
- Factor in the weight of pokemon when calculating pushing forces between hitboxes.
- Remade the Starter Selection Screen with new assets
- Refactored berry trees to be less impactful on TPS (more performant)
- Trading has been overhauled to allow offering multiple Pokémon, up to the entire party, in a single trade
  - Selected Pokémon are now highlighted in both parties with new party slot assets
  - Hovering an offered Pokémon now shows its summary, and clicking the active Pokémon no longer exits the summary view
  - The trade animation now moves the offered Pokémon towards the Poké Ball
  - Parties are compacted after a trade so no empty slots are left between Pokémon
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
- Item evolutions now unlock **all** locked evolutions whose requirements are met instead of stopping after the first.
  - Exceptions being "forced" evolutions which will continue to stop after the first one

### Fixes
- Fixed datapacks with item interaction evolutions that don't specify an item crashing the world on load.
- Fixed the Pokédex evolution tree crashing when a datapack's dex entry is missing an evolution's form.
- Fixed the Pokédex evolution tree crashing with a stack overflow on evolutions that loop back on themselves.
- Fixed form-changing evolutions sometimes resolving to the source form in the Pokédex evolution tree.
- Broken datapack evolutions are now logged and skipped in the Pokédex evolution tree instead of crashing.

### Developer
- Added `PokeSnackSpawnerFactory` which allows influence registration on PokeSnack encounters
- Added `FishingSpawnerFactory` which allows influence registration on fishing encounters
- Added `BattleFleeAttemptEvent` fired from `PokemonBattle.checkFleeAttempt`, allowing developers to intercept and control flee attempts
- Trade network packets (`TradeUpdatedPacket`, `UpdateTradeOfferPacket`, and `TradeCompletedPacket`) now receive an array of Pokémon instead of a single Pokémon to execute trades
- `TradeEvent.Pre` and `TradeEvent.Post` now expose every Pokémon each participant receives (`tradeParticipant1Pokemon` and `tradeParticipant2Pokemon` are now lists), and their Molang context includes `trade_participant_1_pokemon_list` and `trade_participant_2_pokemon_list`
- Evolution requirements can now set `pokedexIcon` and `pokedexTooltip` directly in their JSON to control how they're shown on the Pokédex's evolution tree, for example:
  ```json
  {
    "variant": "attack_defence_ratio",
    "pokedexIcon": "minecraft:diamond_sword",
    "pokedexTooltip": "cobblemon.ui.pokedex.requirement.attack_defence_ratio.tooltip"
  }
  ```
- Fixed wrong order of operations in the code flow prior to the `FOSSIL_REVIVED` event causing pokemon to appear without entity link
- Fixed TestPartySlot command not correctly comparing the provided spec with the target Pokémon

### Molang & Datapacks

### Localization
- Updated translations for:
