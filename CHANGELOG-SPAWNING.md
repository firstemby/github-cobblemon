
### Additions
- Pokémon can now spawn in herds, either as distinct herd groupings or simply several Pokémon spawning at once.

### Fixes
- Fixed the "enabled" property in spawn files not actually being respected. Where do they find these devs?
- Fixed Terralith's shrubland not counting as plains for spawning purposes the way it was intended to.

### Developer
- Spawning Influences now have the context of what the other buckets are when adjusting bucket weights. This will break existing influences that do bucket weight adjustment.
- Renamed heaps of things in the spawning system to make more sense.
  - SpawningContext is now SpawnablePosition
  - WorldSlice is SpawningZone
  - SpawningProspector is now SpawningZoneGenerator
-  Majorly refactored the hierarchy of Spawner
  - The base Spawner interface provides more functions to allow single-point and area spawning given appropriate inputs.
  - TickingSpawner is removed in favour of outside code handling ticking logic.
  - AreaSpawner is removed.
  - BasicSpawner is the first implementation of Spawner which can be used for any purpose.
  - PlayerSpawners are now mixin'd into ServerPlayer and ticked from ServerPlayer#tick.
  - A hierarchy diagram can be found at `./docs/spawner-hierarchy.png` in the mod repository.
- The SpawnerManager class has been removed as its functionality is all now handled elsewhere.
- Renamed things in Spawn Rules to go with the other renames:
    - contextSelector is now spawnablePositionSelector
  - context is now spawnable_position