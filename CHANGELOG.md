# Aeonis: Command Master - Changelog

## 1.7.0 - The Prop Hunt Update 🎭 (2026-01-03)
- **Complete Prop Hunt Minigame System:**
  - Full-featured hide-and-seek minigame with props and hunters
  - Comprehensive command system with `/prophunt` (or `/ph` alias)
  - Create games, join, leave, manage arenas all from commands
  
- **Prop Disguise System:**
  - **50+ disguise types** including animals, mobs, boats, armor stands, minecarts
  - Categorized props: Small (chicken, rabbit), Medium (pig, zombie), Large (horse, iron golem), Objects (armor stand, minecart)
  - **Rotation Lock** - Lock your disguise's rotation to mimic inanimate objects
  - **Movement Freeze** - Completely freeze in place for perfect hiding
  - **Taunt System** - Sound decoys, particle bursts, fake footsteps, fake deaths
  - Visual entity follows player while player remains invisible
  
- **Hunter Arsenal:**
  - **Scanner Ability** - Pulse that reveals nearby props temporarily (30s cooldown)
  - **Tracker Dart** - Marks the nearest prop with glowing effect through walls
  - **Stun Grenades** - Area stun that freezes props and forces them to unfreeze
  - **Prop Compass** - Direction hints toward nearest prop
  - **False Hit Penalty** - Hitting non-props damages the hunter
  
- **Game Phases & Timing:**
  - Waiting → Starting countdown → Hiding Phase → Seeking Phase → Round End
  - Configurable hide time (default 30s), round time (default 3 minutes)
  - Hunters frozen and blinded during hiding phase
  - Automatic round restart and multi-round games (default 5 rounds)
  
- **Arena System:**
  - Create arenas at current position or with custom boundaries
  - Arena presets: small, medium, large, village, forest, cave, nether
  - Safe spawn point generation with validation
  - Chunk preloading for smooth teleportation
  - Border enforcement with push-back mechanic
  
- **Team Balancing:**
  - Dynamic team assignment based on player count
  - Configurable hunter ratio (default 30%)
  - Role rotation between rounds for fair play
  - Scoreboard integration with colored team names
  
- **Quality of Life:**
  - Actionbar status display for both teams
  - Countdown sounds and visual effects
  - Periodic hunter hints (configurable)
  - Spectator mode for eliminated props with fly-around ability
  - Comprehensive help system and control tips
  
- **Rewards & Statistics:**
  - XP rewards for survival, finding props, winning
  - Item loot drops for winners
  - Achievement system (Master Hider, Perfect Hunter, etc.)
  - Per-player statistics tracking
  
- **Configuration Options:**
  - `/prophunt settings` - View and modify all settings
  - Adjustable rounds, timers, cooldowns, team sizes
  - Toggle hunter hints on/off
  - Customize rewards and penalties

## 1.6.0 - The AI Edition 🤖 (2026-01-02)
- **Aeonis AI Assistant - Your In-Game AI Companion:**
  - Spawn a fully autonomous AI-controlled fake player named "Aeonis"
  - Powered by LLM (Ollama/OpenAI-compatible) with conversational memory
  - Follows players, navigates terrain, jumps obstacles automatically
  - `/ai summon_ai` - Spawn Aeonis AI assistant
  - `/ai dismiss_ai` - Despawn Aeonis
  - `/ai config` - Configure LLM endpoint and model
  
- **AI Command Execution System:**
  - Aeonis can execute ANY Minecraft command with OP privileges
  - Safety-blocked dangerous commands (stop, ban, kick, op, deop, whitelist)
  - Use `[CMD: /command]` tag in chat to request command execution
  - Perfect for hands-free gameplay automation
  
- **Script System:**
  - Create reusable multi-command scripts via AI
  - `[SCRIPT: name | cmd1 | cmd2 | ...]` - Create and save scripts
  - `[RUNSCRIPT: name]` - Execute saved scripts
  - Scripts persist in `<game_dir>/aeonis/scripts/`
  
- **Custom Commands:**
  - Define your own command aliases through AI
  - `[NEWCMD: name | cmd1 | cmd2 | ...]` - Create custom command
  - `[RUNCMD: name]` - Execute custom command
  - Commands persist in `<game_dir>/aeonis/commands/`
  
- **Automation Tasks:**
  - Schedule recurring tasks with customizable intervals
  - `[AUTOMATION: name | interval | cmd1 | cmd2 | ...]` - Create automation
  - `[STOPAUTO: name]` - Stop specific automation
  - Great for auto-time set, weather control, periodic effects
  
- **Improved Navigation & Movement:**
  - Enhanced obstacle detection and automatic jumping
  - Better pathfinding to follow players smoothly
  - Proper physics for jumps and momentum
  
- **Comprehensive AI Manual:**
  - Detailed system prompt teaches Aeonis all capabilities
  - Context-aware responses based on game state
  - Remembers conversation history per session

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
