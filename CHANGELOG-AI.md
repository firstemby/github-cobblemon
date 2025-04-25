### Additions
- Rebuilt Pokémon AI to use Minecraft's Brain system, allowing for more complex behaviours and interactions.
  - As an early pass of this, pastured Pokémon will now sleep when appropriate, and may come up to you when they see you.
  - Probably also added a whole bunch of bugs.
  - Growlithe and Arcanine intimidate Skeletons.
  - Meowth intimidate Creepers and Phantoms.
- Added a Behaviour Editor to the NPC editing screen.
- Added /behaviouredit command for opening the Behaviour Editor on Pokémon and NPCs.
  - Editing variables for Pokémon is not yet supported.

### MoLang & Datapacks
- Added a `behaviours` datapack folder which motivates the Behaviour Editor screen on NPCs and Pokémon.
- Added many default behaviours.
- Removed many NPC presets that now exist as behaviours. This is a breaking change if you are using NPCs with these presets. 
  - NPCs with these presets should be modified to instead list under `"behaviours"` all the equivalent behaviours.