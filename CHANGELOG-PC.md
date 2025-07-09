### Additions
- Added ability to rename PC Boxes by clicking on the name of a box.
- Added box options buttons for boxes, toggleable by clicking the right icon button in the bottom bar.
  - The button on the right allows for changing the box wallpaper.
  - The option buttons on the left side allow for sorting the box by name, level, Pokédex number, gender, and type. Shift clicking allows for sorting in reverse order.
- Added filter functionality in PC UI, which supports `PokemonProperties` (e.g. `shiny=yes` shows all shiny Pokémon).
  - Names can be filtered by exact or partial matches, e.g. entering "cha" will show Charmander, Charmeleon, etc.
- Added `/changewallpaper <player> <boxNumber> <wallpaper>` command to change a box wallpaper through commands.
- Added `/renamebox <player> <boxNumber> <name>` command to rename a PC box through commands.
- Added IVs and EVs stat displays in PC.
- Added ability to change boxes by scrolling.
- PC will now open to last box viewed within a session.

### Changes
- Slots in pastures will now have darker backgrounds and italicised Pokémon names if the Pokémon is not owned by the player.
- The pasture slot will show the name of the Pokémon owner when hovered if the Pokémon is not owned.

### Developer
- Added `RenamePCBoxEvent.Pre` and `RenamePCBoxEvent.Post` events to prevent players from renaming a box or changing their input.
- Added `ChangePCBoxWallpaperEvent.Pre` and `ChangePCBoxWallpaperEvent.Post` to prevent players from changing wallpapers or changing their selection.
- Added `WallpaperCollectionEvent` which gets called when clients connect to a server, allowing the server to handle which of the client-found wallpapers it's allowed to move (collected wallpapers can be removed for example to make it "vanish" client-side).
- Added `WallpaperUnlockedEvent`.
- Renamed `SetPCBoxPokemonPacket` and the respective handler to `SetPCBoxPacket`.

### Addons
- Added Flows for `player_tick_pre`, `player_tick_post`, `advancement_earned`, `right_clicked_entity`, `right_clicked_block`, `player_died`, `wallpaper_unlocked`.
- Flows now have `q.` for any context properties in addition to the `c.` properties, making it a bit more convenient when calling other scripts from Flows.
- Added `unlockable_pc_box_wallpapers` datapack folder for wallpapers that need to be unlocked before they're usable.
- Wallpapers are loaded from `assets/<namespace>/textures/gui/pc/wallpaper/` and all wallpapers in this folder are available to the client to choose by default.
