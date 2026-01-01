# Aeonis: Command Master - Changelog

## 1.5.0 - The Horror Update Phase 1 🎃 (2026-01-01)
- **Herobrine - The Legend Returns:**
  - New mysterious entity that spawns naturally when `extra_mobs` is enabled
  - Appears behind players (~10 blocks away), disappears when spotted
  - Multiple behavior states: watching, roaming with sword, staring, hunting animals
  - Builds creepy "infested cobblestone crosses" with 50% chance kill shrines
  - Kill shrines include oak sign with "Im watching" and meat drops
  - Classic creepypasta leaf decay while roaming near trees
  - Breaking shrine blocks spawns Herobrine 5 blocks behind you for a 1.5s stare
  - Max 2 Herobrines per player, spawn cycle every 2-5 minutes
  - `/aeonis summon_herobrine <mode>` command for testing (behind/roaming/staring/hunting)
  
- **Herobrine Transform Scare Sequence:**
  - Attempting `/transform aeonis:herobrine` triggers horror sequence
  - 5 seconds of Darkness + brief Blindness effects
  - 5 Herobrines spawn in circle around player, staring and "laughing"
  - Creepy messages: "You chose to be ME??? ENJOY..." and more
  - Transformation DENIED - you cannot become Herobrine!
  
- **Copper Stalker Improvements:**
  - Health increased: 10 → 30 HP (more tanky)
  - Damage increased: 4 → 6 (Zombie x2)
  - Added knockback resistance (0.2)
  - New Copper Stalker Spawn Egg
  
- **Spawn Eggs:**
  - Herobrine Spawn Egg (special attack-then-teleport behavior)
  - Copper Stalker Spawn Egg ("Stalker Spawn Egg")

## 1.4.0 (2025-12-28)
- Previous stable release

## 1.3 (2025-12-27)
- **Command System Overhaul:**
  - Major refactor: `/x` command split into logical, themed groups: `/prank`, `/ability`, `/game`, `/aeonis`, `/ai`, `/transform`.
  - All command names, usages, and help output are now consistent and grouped logically.
  - Multi-target support for prank commands (e.g., `/prank smite @a`).
  - Help command (`/aeonis help`) updated to reflect new structure and subcommands.
- **System Diagnostics:**
  - `/aeonis sys ping` now shows real-time server and mod stats: TPS, memory, latency, entity/chunk/player counts, and Aeonis system activity.
- **Experimental Chaotic AI Engine:**
  - New `/ai chaotic <entities>` command: enables experimental chaotic AI (lava-seeking, cliff-jumping, spinning, speed boost) on any mob.
  - `/ai chaotic stop`: disables chaotic AI for all affected entities.
- **Reload Command:**
  - `/aeonis reload`: reloads all mod features and resets tracked states (experimental, for dev/admin use).
- **Other Improvements:**
  - Thunder dome logic refactored to use tick-based scheduling (no server thread blocking).
  - Codebase-wide consistency and logical error fixes.
  - Version updated to 1.3 everywhere (build, runtime, system ping).

## 1.2.8b and earlier
- See previous releases for details.

---
For more info, see `/aeonis help` in-game or the README.
