### Additions
- Added functionality for marks.
- Added data for all marks and all title ribbons from Pokémon.
- Added marks screen within the summary UI.
- Added `/givemark <player> <partySlot> <mark>` command to give a mark to a Pokémon.
- Added `/takemark <player> <partySlot> <mark>` command to remove a mark from a Pokémon.
- Added `/giveallmarks <player> <partySlot>` command to give all available marks to a Pokémon.

### MoLang & Datapacks
- Added Flows for `bobber_spawn_pokemon_post`, `pokemon_entity_spawn`, and `fossil_revived`.
- Added `marks` datapack folder.
- Added MoLang functions `entity.is_in_rain`, `pokemon.add_marks(Mark...)`, `pokemon.add_potential_marks(Mark...)`, `pokemon.add_marks_with_chance(Mark...)`, `pokemon.apply_potential_marks`, and `world.is_snowing_at(x, y, z)`.