# Changelog
## [1.8.0 (MONTH Xth, 2026)](#1-8-0)

### Additions
- Added a new `cobblemon:structure`, which is a feature without any restrictions allowing the Builders to place certain structures in much more specific manner.
- Added Configs for Infinite Use TMs and Unlocking all TMs
- Added Type Gem blocks
- Added Type Gem Clusters
- Added TMs
- Added TM Machine
- Added Deepslate Core Block
- Added /givetm Command
- Added Move Dex to Pokedex
- Added Disk Rack for storing disks and being an audio sequencer for Noteblocks

#### Added sounds to the following moves
- Absorb
- Aurora Beam
- Double Team
- Fire Punch
- Fire Spin
- Flame Charge
- Flame Wheel
- Giga Drain
- Ice Punch
- Leech Life
- Magical Leaf
- Mega Drain
- Minimize
- Poison Gas
- Rock Throw
- Scary Face
- Seed bomb
- Smokescreen
- String Shot
- Tail Whip

### Changes
- Data Monitor can accept Disks (TMs, Music Disks, Upgrade, Dubious Disk, etc) to change look

### Fixes
- Fixed rare Pokémon being far too likely to spawn multiple times in a single spawn cycle. The dev told me that the exact bug is too complicated to explain.
- Resolved the niche issue where Structures would spawn in an incorrect location, creating very funky world gen.

### Developer
- Tweaked the spawning selector interface to take a bucket function rather than a bucket directly.
- Added a new `cobblemon:height_range` processor type to allow developers to restrict structure spawning ranges.

### Molang & Datapacks
- Flamethrower and Seismic Toss had their action effect json cleaned up

### Particles

### Localization
- Updated translations for: