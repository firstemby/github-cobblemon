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

### Changes
- Ride sounds have been separated into stereo for passengers and mono for other players. Riding will now sound more spacious.

### Fixes
- Fixed orientation not getting reset after crashing while flying and getting back on the Pokémon.
- Fixed Furfrou not being trimmable on NeoForge.
- Fixed shiny Pokémon not respecting the silent flag on spawn.
- Fixed honey from Saccharine Leaves being harvestable at less than the max age.
- Items given from interacting with Saccharine Leaves are placed in the active hotbar slot if possible.
- Fix all Pokémon being saved to chunks and never despawning.
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

### Developer
- Changed the `owner` parameter in the `OwnerQueryRequirement` interface from `ServerPlayer` to `Player`. This method is now also called on the client to verify whether a Pokémon interaction succeeded, so make sure to update your implementations to handle both server and client contexts. 

### Molang & Datapacks
- Ride sounds can now be set as exclusive to passengers
- Ride sounds no longer play when submerged unless specified with a new setting

### Particles

### Localization
- Updated translations for: