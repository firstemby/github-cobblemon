# Changelog
## [1.7.2 (January 25th, 2026)](#1-7-2)

### Additions
- Added Saccharine wood furniture for Adorn.
- Added the Partner Mark, which party Pokémon have a very small chance to earn as the player walks, provided they have enough friendship.
- Added trades for Saccharine Saplings, Hearty Grains, Chipped Pot, and Masterpiece Teacup to the Wandering Trader.
- Added brewing recipe for Throat Spray.
- Added official Controlify Support
- Added new effects
  - Mental Restoration: Reduces "Time Since Last Rest" statistic, which controls Insomnia, by half a minute per second per effect level
  - Negative Effect Cleanse: Removes all negative effects
  - Effect Cleanse: Removes all effects
- Added more seasonings for sinister tea
  - Mental Herb - Mental Restoration I for 10 seconds
  - White Herb - Negative Effect Cleanse
  - Milk - Effect Cleanse
  - Moomoo Milk - Effect Cleanse
- Added decorative block models for existing items: Potion, Super Potion, Hyper Potion, Max Potion, Full Restore, Antidote, Awakening, Burn Heal, Ice Heal, Paralyze Heal, Full Heal, Ether, Elixir, Max Ether, Max Elixir, X Accuracy, X Attack, X Defence, X Special Attack, X Special Defence, X Speed, Dire Hit, Guard Spec, Weakness Policy, Blunder Policy, Cleanse Tag, and Spell Tag.
- Added decorative sign blocks called Plaques.
- Added ride style labels to ride stats in Pokédex, as well as ride style icons in the summary GUI.
- Added more seasonings for sinister tea:
  - Mental Herb - Mental Restoration I for 10 seconds, which staves off Insomnia.
  - White Herb - Removes negative effects.
  - Milk, Moomoo Milk - Removes effects.
- Added dispenser support for:
  - Applying a Honey Bottle to Saccharine Logs.
  - Washing honey off of Saccharine Slathered Logs.
  - Applying and removing honey on Saccharine Leaves.
- Added riding statistics: `Distance by Pokémon on Land`, `Distance by Pokémon in Air` and `Distance by Pokémon in Liquid`.

### New rideable Pokémon
- Honchkrow 
- Mantine

### Pokémon Added

#### Gen 5
- Throh
- Sawk

#### Gen 6
- Litleo
- Pyroar
- Spritzee
- Aromatisse
- Swirlix
- Slurpuff

### Model updates for the following Pokémon
- Falinks
- Cottonee
- Whimsicott
- Ferroseed
- Ferrothorn
- Chingling
- Chimecho

### Animation updates for the following Pokémon
- Mantine (ride animations)
- Flygon (ride jump animation)
- Honchkrow (ride animations)
- Croagunk
- Toxicroak

### Changes
- Added a new config option, `Enable In-Flight Dismounting` (default: off), which lets you dismount while riding a Pokémon in the air. This is the textbook definition of caveat emptor.
- Ride sounds have been separated into stereo for passengers and mono for other players. Riding will now sound more spacious.
- Battle AI now uses a smarter threshold for switching, reducing unnecessary switches.
- AI will always use the most damaging move when at low HP and when it is not switching, improving endgame decision-making.
- Items given from interacting with Saccharine Leaves are placed in the active hotbar slot if possible.
- Added `label` as an alternate key to the `tag` property.
## [1.7.3 (January 31st, 2026)](#1-7-3)

### Fixes
- Fixed mounted Pokémon not playing sounds.
- Fixed players failing to join servers randomly.
- Fixed some Pokémon having no stamina for flying.
- Fixed `/pctake` not respecting the actual size of the PC.

### Developer
- Poké Ball GUI textures now respect the ball's namespace, enabling proper texture loading for custom balls from other mods.

### Molang & Datapacks
- Added a `chance` requirement type for Pokémon interactions.

### Localization
- Updated translations for:
  - French
  - Canadian French
  - Korean
  - Portuguese
  - Simplified Chinese
  - Spanish