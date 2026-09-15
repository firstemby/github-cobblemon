Composed pool parties are a type of NPC party that allows for a more complex party than usual. It relies on several
datapack folders to function.

### Moveset Builders
The `moveset_builders` datapack folder has all the ways that a moveset can be composed
logically. This consists of up to 4 slots of moves defined with some list of move selectors.

The full list of possible move selectors is as follows:

| Type                      | Description                                                                                                   |
|---------------------------|---------------------------------------------------------------------------------------------------------------|
| `none`                    | Blank move slot                                                                                               |
| `last_levelup`            | Picks the last level-up move (rolls if there are several)                                                     |
| `last_offensive`          | Same as last_levelup but no status moves                                                                      |
| `last_suitable_offensive` | Same as last_offensive but restricts to whichever is stronger of physical/special in the Pokémon's base stats |
| `levelup`                 | Picks a random level-up move                                                                                  |
| `stab`                    | Picks a random STAB level-up move                                                                             |
| `stab_physical`           | Picks a random physical STAB level-up move                                                                    |
| `stab_special`            | Picks a random special STAB level-up move                                                                     |
| `physical`                | Picks a random physical level-up move                                                                         |
| `special`                 | Picks a random special level-up move                                                                          |
| `offensive`               | Picks a random physical or special level-up move                                                              |
| `status`                  | Picks a random status level-up move                                                                           |
| `tm`                      | Picks a random TM move                                                                                        |
| `stab_tm`                 | Picks a random STAB TM move                                                                                   |
| `last_status`             | Same as last_levelup but only status moves                                                                    |
| `egg`                     | Picks a random egg move                                                                                       |

When selecting from one of multiple possible moves, the selection is a weighted random. The base move weight is drawn 
from the `move_weights.json` file in the root data folder. If a move is not in that JSON then the weight will be `50`. 
If a move is mentioned in the `signatureMoves` list of the Pokémon's species or form, its weight is multiplied by `5`.

If you add a datapack with a `move_weights.json` in the `cobblemon` namespace, it will combine with the default values,
with the datapack layering over the top. In other words, you can override a few of the existing weights with a small 
JSON file, if you want.

For example, take `alpha.json`, used for alpha Pokémon:

```json
{
  "slot1": ["last_suitable_offensive", "last_offensive", "last_levelup"],
  "slot2": ["last_offensive", "last_levelup"],
  "slot3": ["last_levelup"],
  "slot4": ["tm", "last_levelup"]
}
```

This moveset builder will try to fill slot 1 with the last suitable offensive move the Pokémon can learn,
falling back to the last offensive move, and then the last level-up move if all else fails.

### Molang Expressions in Composed Parties
For the Molang expression properties in the following sections, the following Molang queries will be available:

`q.players` - A list of players that are challenging the NPC.

`q.player` - The player challenging the NPC, specifically if being challenged only by one player. If there are multiple players, this will be 0.

`q.npc` - The NPC entity.

`q.level` - The seed level of the NPC.


### Party Pools
The `party_pools` datapack folder has all the pools of Pokémon that can be used to draw from when composing a party. 
Each entry of the pool has some number of labels used by the party composition.

| Property             | Description                                                                                                                                                                                                                                                                      |
|----------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `pokemon`            | The guaranteed Pokémon information, such as `geodude shiny=true`                                                                                                                                                                                                                 |
| `labels`             | The identifying labels of the Pokémon. These can be anything you want. Definitely shouldn't be empty though.                                                                                                                                                                     |
| `npcLevels`          | The seed levels that the NPC can be for which this entry is possible. 1-30 would mean a level 40 NPC cannot use that entry. You can also provide an object with `min` and `max` properties to use MoLang expressions.                                                            |
| `npcAspects`         | The aspects that the NPC must have for this entry to be possible. If the list is empty, as is the default, then it won't check the NPC aspects.                                                                                                                                  |
| `weight`             | The relative weight of this entry compared to others in the pool. Defaults to 50. You can also use a Molang expression here.                                                                                                                                                     |
| `levelVariation`     | The possible variation around the NPC's seed level that is possible. `-2-3` would mean the Pokémon's level could be anywhere from 2 levels below the NPC seed level to 3 levels above. You can also provide an object with `min` and `max` properties to use MoLang expressions. |
| `maxTimesSelectable` | The maximum number of times this entry can be selected for a party. Defaults to 6. You can also use a Molang expression here.                                                                                                                                                    |
| `movesetBuilders`    | A list of moveset builders from the `moveset_builders` folder that can be used to create this Pokémon's moveset. One will be selected at random if there are multiple.                                                                                                           |
| `requires`           | A list of labels that must be present in the party for this entry to be selected. These are from the `labels` of entries that were already selected.                                                                                                                             |
| `incompatible`       | A list of labels that cannot be present in the party for this entry to be selected. These are from the `labels` of entries that were already selected.                                                                                                                           |

As an example, a trivially small pool might look like:
```json
{
  "displayName": "Some Name",
  "entries": [
    {
      "pokemon": "geodude",
      "labels": [
        "basic",
        "rocky"
      ],
      "npcLevels": "1-30",
      "weight": 20,
      "levelVariation": {
        "min": "q.level < 10 ? -1 : -3",
        "max": "q.level < 10 ? 1 : 3"
      },
      "maxTimesSelectable": 4,
      "movesetBuilders": ["cobblemon:npc_pokemon"],
      "requires": [
        "golem-ace"
      ],
      "incompatible": [
        "steelix-ace"
      ]
    },
    {
      "pokemon": "golem",
      "labels": [
        "ace",
        "strong"
      ],
      "npcLevels": "1-30",
      "weight": 20,
      "levelVariation": "1-5",
      "maxTimesSelectable": 1,
      "movesetBuilders": ["cobblemon:offensive_npc_pokemon", "cobblemon:npc_pokemon"],
      "incompatible": [
        "steelix-ace",
        "ace"
      ]
    },
    {
      "pokemon": "steelix shiny=true",
      "labels": [
        "ace",
        "strong"
      ],
      "npcLevels": "15-30",
      "npcAspects": ["steel-boots"],
      "weight": "q.level < 20 ? 10 : 30",
      "levelVariation": "0-2",
      "maxTimesSelectable": "q.level > 25 ? 2 : 1",
      "movesetBuilders": ["cobblemon:offensive_npc_pokemon"],
      "requires": [],
      "incompatible": []
    }
  ]
}
```

### Party Compositions
The `party_compositions` datapack folder has all the ways that a party can be composed of a pool of Pokémon. This allows 
you to dictate the preferred Pokémon categories for each slot of the party.

For example, a standard NPC's party composition might look like:

```json
{
  "scrambleOrder": true,
  "slot1": ["ace", "strong", "fodder"],
  "slot2": ["strong", "fodder"],
  "slot3": ["strong", "fodder"],
  "slot4": ["fodder"],
  "slot5": ["fodder"],
  "slot6": ["fodder"]
}
```

This will make a party that is randomly ordered. One slot will have a Pokémon with the `ace` label if it can find any
suitable ones for this seed level and NPC aspects, falling back to `strong` or `fodder` if not, and if all else fails 
the slot will be empty. That logic is applied to all the remaining slots. The party composition is given the number of 
Pokémon that is desired, and in the case of a 3 Pokémon party, slot1, slot, and slot3 will be used even if 
`scrambleOrder` is true. The scrambling occurs only after the Pokémon have been selected. The only exception is if any
of slots 1-3 failed to produce a Pokémon, in which case the later slots will be used to make up the desired number of
Pokémon.

### Composed Pool Party Provider
The `composed_pool` party provider type uses a party pool and a party composition to create a potentially quite complex
party. When generating the party, it first decides how many Pokémon to select, based on the `minPokemon` and `maxPokemon`
expressions. It then uses the party composition and pool to select that many Pokémon and builds a party.


| Property         | Description                                                                                                                                                                                                          |
|------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `isStatic`       | True if the party will be permanently saved onto the NPC. This will mean that if defeated, the NPC's party will remain fainted until it can heal.                                                                    |
| `useFixedRandom` | Whether the randomizer should produce the same party consistently. This is for when you have a non-static party but once it generates a party, it should always generate an identical one each time it's challenged. |
| `minPokemon`     | A Molang expression representing how many Pokémon is the minimum it will have. Defaults to "1"                                                                                                                       |
| `maxPokemon`     | A Molang expression representing how many Pokémon is the minimum it will have. Defaults to "6"                                                                                                                       |
| `pool`           | The party pool to choose from, such as `"youraddon:rocky_pokemon"`.                                                                                                                                                  |
| `composition`    | The party composition to use to compose the pool into a party, such as `"youraddon:gym_leader"`                                                                                                                      |

An example `composed_pool` party provider might look like:

```json
{
  "type": "composed_pool",
  "isStatic": false,
  "useFixedRandom": true,
  "minPokemon": "q.level > 30 ? 3 : 1",
  "maxPokemon": "q.level > 50 ? 6 : 4",
  "pool": "youraddon:rocky_pokemon",
  "composition": "youraddon:gym_leader"
}
```