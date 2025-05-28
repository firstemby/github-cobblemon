
### Additions
- Pokémon can now spawn in herds, either as distinct herd groupings or simply several Pokémon spawning at once.

### Fixes
- Fixed the "enabled" property in spawn files not actually being respected. Where do they find these devs?

### Developer
- Spawning Influences now have the context of what the other buckets are when adjusting bucket weights. This will break existing influences that do bucket weight adjustment.
- Renamed heaps of things in the spawning system to make more sense.
  - SpawningContext is now SpawnablePosition
  - WorldSlice is SpawningZone
  - SpawningProspector is now SpawningZoneGenerator
- Renamed things in Spawn Rules to go with the other renames:
  - contextSelector is now spawnablePositionSelector
  - context is now spawnable_position