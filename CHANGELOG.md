# Changelog
## [1.7.2 (MONTH Xth, YEAR)](#1-7-2)

### Additions
- Added riding statistics: `Distance by Pokémon on Land`, `Distance by Pokémon in Air` and `Distance by Pokémon in Liquid`.
- Added the Partner Mark, which party Pokémon have a very small chance to earn as the player walks, provided they have enough friendship.
- Added trades for Saccharine Saplings, Hearty Grains, Chipped Pot, and Masterpiece Teacup to the Wandering Trader.
- Added brewing recipe for Throat Spray.
- Added new effects
  - Mental Restoration: Reduces "Time Since Last Rest" statistic, which controls Insomnia, by half a minute per second per effect level
  - Negative Effect Cleanse: Removes all negative effects
  - Effect Cleanse: Removes all effects
- Added more seasonings for sinister tea
  - Mental Herb - Mental Restoration I for 10 seconds
  - White Herb - Negative Effect Cleanse
  - Milk - Effect Cleanse
  - Moomoo Milk - Effect Cleanse
- Added `label` as an alternate key to the `tag` property.
- Added Saccharine wood furniture for Adorn.

### Changes
- Ride sounds have been separated into stereo for passengers and mono for other players. Riding will now sound more spacious.
- Added a new config option, `Enable In-Flight Dismounting` (default: off), which lets you dismount while riding a Pokémon in the air.
- Battle AI now uses a smarter threshold for switching, reducing unnecessary switches.
- AI will always use the most damaging move when at low HP and when it is not switching, improving endgame decision-making.
- Added support for optional message variants in battle activate instructions for more context-specific battle text.
  - Example: `this.add('-activate', pokemon, 'ability: example', '[msg]message1');` will parse to lang key `cobblemon.battle.activate.example.message1`

### Fixes
- Fixed an issue in the OmniPathNodeMaker/Navigation that prevented Combees from pathing into and out of Saccharine leaves.
- Fixed orientation not getting reset after crashing while flying and getting back on the Pokémon.
- Fixed Furfrou not being trimmable on NeoForge.
- Fixed shiny Pokémon not respecting the silent flag on spawn.
- Fixed honey from Saccharine Leaves being harvestable at less than the max age.
- Items given from interacting with Saccharine Leaves are placed in the active hotbar slot if possible.
- Fix all Pokémon being saved to chunks and never despawning.
- Fixed possible error on world generation when Combees are placed in naturally generated bee nests.
- Fixed PokeSnacks crashing if there was no available spawn.
- Fixed Pokémon spawn influences not being specific to regional forms
- Fixed crashing when viewing another player in spectator mode
- Fixed missing apricorn textures for Adorn blocks
- Fixed crashing when riding due to malformed json file, instead resetting it
- Fixed mochi items consuming two items at a time
- Fix item interaction sometimes not working properly when playing on servers.
- Fixed Soothe Bell not being properly tagged as a held item.
- Fixed Mint Leaves not being usable for filling the Resurrection Machine.
- Added Saccharine Boats and Saccharine Boat with Chests to relevant boat tags.
- Fixed the `run_script` Molang function to not fail if the environment's context is null.
- Fixed the movesets of certain Pokémon being incorrectly sanitized upon reload.
- Fixed the ordering of Aprijuice's riding stat boosts to always have the same order.
- Fixed the Molang functions `date_local_time`, `date_of`, `date_is_after` to use the correct date format.
- Fixed the bottom half of pasture blocks having an incorrect hit-box when facing north or south.
- Fixed Galarian Mr. Mime incorrectly being rideable.
- Fixed gimmicks not being usable in double / triple battles
- Fixed gimmick buttons showing when already used in the same turn in double / triple battles
- Fixed Apricorn block model rotation for counters/sinks/cupboards/drawers to match that of most recent version of Adorn.
- Fixed Fresh Start Mochi EV reset not syncing health changes properly.
- Fixed requirements such as `biomeCondition` crashing the game when used to define Pokémon interactions.
- Fixed Pokémon interactions not playing sound effects when the volume was not specified.
- Fixed Pokémon interaction cooldowns taking longer than intended when the Pokémon was in the player’s party.
- Fixed Pokémon interactions occasionally crashing the game when spamming an interaction with a 0 cooldown.
- Fixed Pokémon fullness decreasing more than intended when the Pokémon was pastured.

### Developer
- Changed the `owner` parameter in the `OwnerQueryRequirement` interface from `ServerPlayer` to `Player`. This method is now also called on the client to verify whether a Pokémon interaction succeeded, so make sure to update your implementations to handle both server and client contexts.

### Molang & Datapacks
- Ride sounds can now be set as exclusive to passengers
- Ride sounds no longer play when submerged unless specified with a new setting
- Exception handling has been added to `run_molang`, resolving some crashes caused by malformed MoLang expressions.

### Particles

### Localization
- Updated translations for:
