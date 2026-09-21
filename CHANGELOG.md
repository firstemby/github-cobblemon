# Changelog
## [1.8.2 (MONTH xth, 2026)](#1-8-2)

### Fixes
- Fixed Ampharos' eyes not emitting particles
- Fixed Alolan Raticate's cry not playing
- Removed unnecessary conditions for Mimikyu's standing pose. Hopefully fixes the rare instance of it using its shoulder animations during battle in multiplayer scenarios.
- Fixed Rockruff line not blinking
- Fixed Flygon sometimes using its running animation instead of walking
- Fixed Bramblin always sleeping outside of battle
- Fixed Dragonair using its battle idle as a normal idle
### TEST
### Pokémon Added
#### Generation 4
- Snover
- Abomasnow
- Burmy
- Wormadam
- Mothim

#### Generation 5
- Tympole
- Palpitoad
- Seismitoad

#### Generation 6
- Helioptile
- Heliolisk

#### Generation 7
- Grubbin
- Charjabug
- Vikavolt

#### Generation 9
- Greavard
- Houndstone

### Remodels
- Cacnea
- Cacturne
- Binacle
- Barbaracle
- Glimmet
- Glimmora
- Deerling
- Sawsbuck (All forms)
- Absol

### New Animations
- Cacnea
- Cacturne

## [1.8.0 (MONTH Xth, 2026)](#1-8-0)

### Additions
- The following items can now be placed on the ground as blocks:
  - King's Rock, Galarica Cuff, Unremarkable Teacup, Masterpiece Teacup, Chipped Pot, Cracked Pot, Sachet, Metal Powder, Quick Powder, Bright Powder, Icy Rock, Heat Rock, Smooth Rock, Damp Rock, Destiny Knot, Luminous Moss, Never-Melt Ice, Wide Lens, Scope Lens, and Zoom Lens.
  - Iron Ball, Metal Coat, Throat Spray, HP Up, Protein, Iron, Carbos, Calcium, Zinc, PP Up, PP Max, Upgrade, Dubious Disc, and Loaded Dice.
- The following items can now be placed on various surfaces:
  - Galarica Wreath, Magnet, Flame Orb, Life Orb, Toxic Orb, Light Ball, and Smoke Ball.
- The following items can now be placed as blocks, and have additional functionality:
  - Ring Target, Metronome, and Eject Button.
- The following items can now be worn by Players and Pokémon:
  - Galarica Wreath, Cleanse Tag, Spell Tag, Sharp Beak, Covert Cloak, Shell Helmet, and Never-Melt Ice.
- Wearable items can now be equipped by a player by holding it and right-clicking (or rebound use/interact)
  - Wearable items that can be placed down are exempt from this behaviour.

### New rideable Pokémon

### Pokémon Added

#### Regional Forms

### Model updates for the following Pokémon

### Animation updates for the following Pokémon

#### Added sounds to the following moves

### Changes
- Updated various recipes to display in Recipe Groups within the Recipe Book
- Updated most recipes to display more logically/consistently within the Recipe Book's categories
- Make ride rotations less jittery in multiplayer.

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
- Correctly grants "They're Minerals!" advancement when planting a type gem
- Type Gem Blocks now have advancements to give you the recipe in the recipe book (the data for the advancements were inside the recipes somehow??)
- Some other recipes without advancements have also been corrected
- Corrected the TM Recipes for Charge Beam and Payday
- Type Gem blocks now properly generate clusters off of the block.
- Starter Screen Categories will no longer stop rendering if they are bigger than the viewport, and you are not viewing top or bottom edges 
- Random Starter option now properly handled in height calculation, so now it won't run off into other categories or be un-accessible due to scroll limits if it causes a new row
- Mochi and Feathers no longer drop glass bottles on use
- Fixed a rare set of concurrency bugs related to reloading packs on NeoForge; fixes a ModernFix issue

### Developer

### Molang & Datapacks
- A new `negate` variant for evolution requirements has been added, which will negate (invert) the provided inner condition.

### Particles

#### New Move Animations

### Localization
- Updated translations for: