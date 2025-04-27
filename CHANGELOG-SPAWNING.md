
### Additions
- Pokémon can now spawn in herds, either as distinct herd groupings or simply several Pokémon spawning at once.

### Fixes
- Fixed the "enabled" property in spawn files not actually being respected. Where do they find these devs?

### Developer
- Spawning Influences now have the context of what the other buckets are when adjusting bucket weights. This will break existing influences that do bucket weight adjustment.
- Renamed heaps of things in the spawning system to make more sense.