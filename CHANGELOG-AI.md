### Additions
- Rebuilt Pokémon AI to use Minecraft's Brain system, allowing for more complex behaviours and interactions.
  - As an early pass of this, pastured Pokémon will now sleep when appropriate, and may come up to you when they see you.
  - Probably also added a whole bunch of bugs.
  - Dog Pokémon (Growlithe, Arcanine, etc.) intimidate Skeletons.
  - Cat Pokémon (Meowth, Persian, etc.) intimidate Creepers and Phantoms.
  - Sleep-related Pokémon (Munsharna, Komala, etc.) intimidate Phantoms.
  - Some Pokémon naturally hunt each other.
- Added a Behaviour Editor to the NPC editing screen.
- Added /behaviouredit command for opening the Behaviour Editor on Pokémon and NPCs.
  - Editing variables for Pokémon is not yet supported.

### Developer
- baseScale in NPCs has been removed from classes and presets, and in the entity it is replaced with hitboxScale and renderScale.

### MoLang & Datapacks
- Added a `behaviours` datapack folder which motivates the Behaviour Editor screen on NPCs and Pokémon.
- Added very many behaviours to choose from.
- Removed many NPC presets that now exist as behaviours. This is a breaking change if you are using NPCs with these presets. 
  - NPCs with these presets should be modified to instead list under `"behaviours"` all the equivalent behaviours.