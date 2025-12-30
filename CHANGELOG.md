# Aeonis: Command Master - Changelog

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
