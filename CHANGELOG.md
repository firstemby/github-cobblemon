# Changelog
## [1.7.4 (MONTH Xth, 2026)](#1-7-4)

### Additions
- Added config options to award experience to fainted Pokémon for opponents they helped defeat (`awardExperienceToFaintedPokemon`) and to still award experience for defeated enemies even after battle loss/forfeit (`awardExperienceOnBattleLoss`)
- Party Overlay Exp Gained Animation will now display the amount of EXP the Pokémon gained
- Added a toast to notify players of how they can scroll through their party. Did you know you can hold R and scroll instead of using arrow keys??!
- Added new layer property that makes a layer scroll in a direction. "scrolling": {"speedU": 0.1, "speedV": 0.1}
- Added missing crops to Botany Pots integration

### New rideable Pokémon

### Pokémon Added

### Model updates for the following Pokémon

### Animation updates for the following Pokémon

### Changes
- Restructured Botany Pots integration folder structure
- Decrease default pastured mon wander radius to 32.
- Factor in the weight of pokemon when calculating pushing forces between hitboxes.
- Remade the Starter Selection Screen with new assets
- Refactored berry trees to be less impactful on TPS (more performant)
- Changed the bait effects of the following items: (Vanilla) Sweet & Glow Berries, Golden & Enchanted Golden Apples (Cobblemon) Custap, Eggant, Hopo & Micle Berries, Sweet Hearts

### Fixes
- Fixed Pokédex layout for genderless variations
- Fixes https://gitlab.com/cable-mc/cobblemon/-/issues/1943
- Fix occasional crash on retrieving revived fossils
- Fixed a compatibility issue with World Game Rules which caused some log spam in specific cases.
- Fixed improper validation of Pokémon when performing a trade.
- Fixed issue where particle like beam can disappear when you don't see the beginning of the beam or you look away
 - Fixes https://gitlab.com/cable-mc/cobblemon/-/issues/1943
 - Fix occasional crash on retrieving revived fossils
 - Fix brushing not working on Neoforge
 - [Various Submarine Ride Fixes](https://gitlab.com/cable-mc/cobblemon/-/merge_requests/2157)
 - Fix Pastured mons despawning and running away.
 - Fix Apricorn trees crashing when other mods bonemeal it
 - [Various UFO Ride Improvements](https://gitlab.com/cable-mc/cobblemon/-/merge_requests/2156)
- Fixed NeoForge brewing stand unexpectedly crashing users when shift clicking items within it's menu
- Fixed NPCs switching pokemon very often during battles
- Fixed Campfire pot causing crashes under certain circumstances
- Fixed Type Gems not being consumed upon use
- Fixed Ancient Pokeball's to their respective modifiers
- Fixed a Server warning when recalling Pokemon
- Fixed sliding particles incorrectly handling block collision
- Fixed Full Heal not curing status conditions in battle
- Fixed a rare issue with battles locking up when you are forced to switch, with the switch menu appearing instantly.
- Fixed the combined effects of Pledge moves having broken Showdown message handling and localization

### Developer
- Added `PokeSnackSpawnerFactory` which allows influence registration on PokeSnack encounters
- Added `FishingSpawnerFactory` which allows influence registration on fishing encounters
- Added `BattleFleeAttemptEvent` fired from `PokemonBattle.checkFleeAttempt`, allowing developers to intercept and control flee attempts

### Molang & Datapacks
- Added basic chatter NPC Behaviour
- q.item.is_enchanted()
- q.item.has_enchantment(minecraft:sharpness, 3)
- Added `has_chosen_starter` & `get_starter_uuid` as available Molang functions
- Added `marks`, `has_mark`, & `remove_marks` as available Molang functions for pokemon
- Added Data Component support for species drops, eg `"components": {"minecraft:custom_model_data": 123}`
- Added new sortOrder field to mark entries, (defaults to 0 if not specified) which controls the order in which marks are rendered on the HUD, with higher numbers being rendered on top of lower numbers
- Added optional `order` field to starter categories. It will allow to explicitly sort categories in starter selection screen
 - SpeciesAdditions append vs override now matches wiki documentation

### Localization
- Updated translations for:
