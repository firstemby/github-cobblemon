# Habitat Block GUI Editor - Developer Instructions
These instructions are brought to you by AI slop with Hiroku reviewing it afterwards. It does seem fairly accurate 
though.

## Overview
The Habitat Block is a spawning tool that allows creative mode players to configure custom Pokémon spawning behavior. We need a GUI editor so read below to understand what it needs to do.

Note: As you'll see, "Phases" aren't implemented just yet. The idea will be that each habitat block will have a current "phase", which changes every day(?). The number of different phases is something that can be configured per block, as well as whether the phases follow a consistent pattern or if every time a day ends, a random phase is chosen.

The idea is like, if you had set 3 phases for a habitat block, you could make Rattata spawn in phase 1, Patrat in phase 2, and Zigzagoon in phase 3. Then you can randomize phases, and that will mean every day it's a random choice which of those three is going to be able to spawn that day. If randomize phases was false, there will be a fixed order of the phases so it might end up being Patrat-Zigzagoon-Rattata every 3 days forever.

## Entry Point
- GUI opens when a creative mode player right-clicks a Habitat Block
- Packet: `OpenHabitatBlockEditorPacket` sends current settings to client
- When you're done you send a `SaveHabitatBlockSettingsPacket` back to the server with the same settings DTO instance (but modified, one would assume)
- Settings are applied via `HabitatBlockEntity.applySettings(HabitatSettingsDTO)`

## Data Structure

### Root: `HabitatSettingsDTO`
All settings are contained in this DTO which is sent between client and server.

#### Common Settings (always present)
```kotlin
numberOfPhases: Int = 1              // Number of spawn phases (TODO: not yet implemented)
randomizePhases: Boolean = false     // Whether to randomize phase order
levelRange: IntRange                 // Min..Max level for spawned Pokémon
modifiers: String                    // PokemonProperties string (e.g., "shiny gender=male")
mimicId: ResourceLocation            // Block appearance (e.g., "minecraft:stone")
```

#### Spawning Mode Toggle
```kotlin
isActivatedSpawning: Boolean         // true = Activated mode, false = Natural mode
```

Based on this boolean, EITHER `activatedSettings` OR `naturalSettings` will be populated (never both).

---

## Spawning Modes

### Mode 1: Activated Spawning (`ActivatedHabitatSettingsDTO`)
Triggered spawning system - spawns occur based on specific triggers.

```kotlin
trigger: Enum                        // REDSTONE, TICK, or RANDOM_TICK
chance: Float = 1.0                  // Probability (0.0-1.0) spawn attempt occurs on trigger
cancelledNaturalSpawningRange: Int   // Radius (blocks) where natural spawning is disabled
spawnRange: Int                      // Radius (blocks) where spawning can occur
maxSpawns: Int = -1                  // Max concurrent spawns from this block (-1 = infinite)
maxSpawnsPerActivation: Int = 1      // Max spawns per single trigger event
habitatPool: HabitatPoolSettingsDTO  // What Pokémon can spawn (see below)
```

**Trigger Types:**
- `REDSTONE`: Spawns on redstone pulse (rising edge)
- `TICK`: Attempts spawn every game tick (very frequent)
- `RANDOM_TICK`: Attempts spawn on random tick events

### Mode 2: Natural Spawning (`NaturalHabitatSettingsDTO`)
Modifies the natural spawning system in the area.

```kotlin
replaceSpawns: Boolean               // true = only this pool spawns, false = adds to natural spawns
rangeOfInfluence: Int                // Radius (blocks) where this influences spawning
habitatPool: HabitatPoolSettingsDTO  // What Pokémon can spawn (see below)
```

---

## Habitat Pool Settings (`HabitatPoolSettingsDTO`)

Defines what Pokémon can spawn. Can reference existing pools or define custom ones.

```kotlin
id: ResourceLocation                 // Pool identifier
isReference: Boolean                 // true = use existing pool, false = custom pool
isActivated: Boolean                 // Must match parent spawning mode

// Only present when isReference = false:
name: String?                        // Display name for custom pool
spawns: List<HabitatSpawn>?          // List of spawn entries
```

**Pool Types:**
- **Reference Pool**: `isReference = true` - Uses a pre-defined pool by ID (e.g., `cobblemon:forest_day`)
- **Custom Pool**: `isReference = false` - Defines spawns inline with `name` and `spawns` list

**Custom Pool IDs**: Must start with `custom__` prefix (e.g., `cobblemon:custom__my_pool`)

---

## GUI Requirements

### Layout Sections

#### 1. Common Settings Panel
- **Number of Phases**: Integer input (default: 1) - Note: Feature not yet implemented
- **Randomize Phases**: Checkbox (default: false) - Note: Feature not yet implemented
- **Level Range**: Two integer inputs (Min/Max) - Default: 1 to config max
- **Modifiers**: Text input for PokemonProperties string
  - Examples: `shiny`, `gender=male`, `shiny gender=female level=50`
- **Mimic Block**: Block selector/dropdown
  - Default: `minecraft:stone`
  - See `HabitatBlockEntity.mimicCycle` for suggested blocks

#### 2. Spawning Mode Selector
Radio buttons or toggle:
- **Activated Spawning** (trigger-based)
- **Natural Spawning** (world spawning influenced)

#### 3. Mode-Specific Settings

**If Activated Spawning:**
- **Trigger**: Dropdown (REDSTONE / TICK / RANDOM_TICK)
- **Chance**: Float slider (0.0 - 1.0, default: 1.0)
- **Cancel Natural Spawning Range**: Integer input (blocks)
- **Spawn Range**: Integer input (blocks)
- **Max Spawns**: Integer input (-1 for infinite, default: -1)
- **Max Spawns Per Activation**: Integer input (default: 1)
- **Habitat Pool**: Pool selector (see below)

**If Natural Spawning:**
- **Replace Spawns**: Checkbox (true = exclusive, false = additive)
- **Range of Influence**: Integer input (blocks)
- **Habitat Pool**: Pool selector (see below)

#### 4. Habitat Pool Selector
Two options:
- **Use Existing Pool**: Dropdown of registered pools
  - Set `isReference = true`
  - Set `id` to selected pool
  - Ensure `isActivated` matches spawning mode
- **Create Custom Pool**: Opens pool editor
  - Set `isReference = false`
  - Generate ID with `custom__` prefix
  - Set `name` and `spawns` list
  - User defines spawn entries (implementation depends on `HabitatSpawn` structure)

---

## Validation Rules

1. **Level Range**: Min must be ≥ 1, Max must be ≤ config max, Min ≤ Max
2. **Chance**: Must be between 0.0 and 1.0
3. **Ranges**: All range values must be ≥ 0
4. **Pool Validation**: 
   - If `isActivated = true`, pool must be `ActivatedHabitatPool`
   - If `isActivated = false`, pool must be `NaturalHabitatPool`
   - If reference pool not found, server logs error and rejects settings
5. **Custom Pool IDs**: Must start with `custom__`

---

## Network Flow

1. **Opening GUI**: Server sends `OpenHabitatBlockEditorPacket` with current `HabitatSettingsDTO`
2. **Saving Changes**: Client sends updated `HabitatSettingsDTO` back to server
3. **Server Processing**: 
   - `HabitatBlockEntity.applySettings()` validates and applies changes
   - Calls `refreshFromSettings()` to regenerate spawn details
   - Marks block entity as changed and syncs to clients

---

## Implementation Notes

- **Creative Mode Only**: GUI should only be accessible to creative mode players
- **Phase System**: `numberOfPhases` and `randomizePhases` are saved but not yet functional

---

## Example Configurations

### Example 1: Redstone-Triggered Shiny Spawner
```kotlin
isActivatedSpawning = true
levelRange = 50..60
modifiers = "shiny"
mimicId = "minecraft:diamond_block"

activatedSettings:
  trigger = REDSTONE
  chance = 1.0
  spawnRange = 10
  maxSpawns = 1
  maxSpawnsPerActivation = 1
  habitatPool = (reference to existing pool)
```

### Example 2: Natural Spawn Replacement Zone
```kotlin
isActivatedSpawning = false
levelRange = 1..100
modifiers = ""
mimicId = "minecraft:moss_block"

naturalSettings:
  replaceSpawns = true
  rangeOfInfluence = 32
  habitatPool = (custom pool with specific Pokémon)
```

---

## Related Files
- `HabitatBlockEntity.kt` - Main block entity logic
- `HabitatSettingsDTO.kt` - Root settings container
- `ActivatedHabitatSettingsDTO.kt` - Activated mode settings
- `NaturalHabitatSettingsDTO.kt` - Natural mode settings
- `HabitatPoolSettingsDTO.kt` - Pool configuration
- `ActivatedHabitatSpawning.kt` - Activated spawning implementation
- `NaturalHabitatSpawning.kt` - Natural spawning implementation
