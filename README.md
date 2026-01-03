<p align="center">
  <img src="https://media.forgecdn.net/avatars/console-avatars/avatar_15441b09-293b-46b5-9f56-a72f7d35a1b7.png" alt="Aeonis: Command Master Banner"/>
</p>

<h1 align="center">⚡ Aeonis: Command Master v1.7.0 ⚡</h1>

<p align="center">
  <b>Take control. Command anything. Master the game.</b>
</p>

<p align="center">
  <i>From the creators of <b>EMBERVEIL MODPACK</b></i>
</p>

<p align="center">
  <a href="https://fabricmc.net/"><img src="https://img.shields.io/badge/Fabric-1.21.10-blue?style=for-the-badge&logo=fabric&logoColor=white" alt="Fabric"></a>
  <a href="https://www.curseforge.com/minecraft/mc-mods/aeonis-command-master"><img src="https://img.shields.io/badge/CurseForge-Download-orange?style=for-the-badge&logo=curseforge&logoColor=white" alt="CurseForge"></a>
  <a href="https://modrinth.com/mod/aeonis-command-master"><img src="https://img.shields.io/badge/Modrinth-Download-green?style=for-the-badge&logo=modrinth&logoColor=white" alt="Modrinth"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-yellow?style=for-the-badge" alt="License"></a>
  <img src="https://img.shields.io/badge/Version-1.7.0-purple?style=for-the-badge" alt="Version">
</p>

<p align="center">
  <a href="https://www.youtube.com/@quantumcreeper"><img src="https://img.shields.io/badge/YouTube-Subscribe-red?style=for-the-badge&logo=youtube&logoColor=white" alt="YouTube"></a>
  <a href="https://github.com/TheCorrectSynovian/Aeonis-mod"><img src="https://img.shields.io/badge/GitHub-Source-181717?style=for-the-badge&logo=github&logoColor=white" alt="GitHub"></a>
</p>

---

<p align="center">
  <img src="https://readme-typing-svg.herokuapp.com?font=Fira+Code&weight=600&size=22&pause=1000&color=00D9FF&center=true&vCenter=true&width=700&lines=🎮+Control+ANY+Mob+in+Minecraft!;🐉+Fly+as+Ender+Dragon+-+Shoot+Fireballs!;💀+Become+the+Wither+-+Launch+Skulls!;🐔+Be+a+Chicken+-+Lay+Eggs!;💨+Breeze+Wind+Charges!;🧚+Summon+Pet+Vex+Companions!;⚡+35%2B+Epic+Commands!" alt="Typing SVG" />
</p>

---


# Aeonis: Command Master - Changelog

## 1.7.0 - The Hotfix Update 🔧 (2026-01-03)
- **Prop Hunt Minigame (Experimental):**
  - Complete Prop Hunt minigame system (WORK IN PROGRESS!)
  - Players now receive experimental warning on world join
  - Additional warning when starting a Prop Hunt match
  - Many features still under development

## 1.6.0 - The AI Edition 🤖 (2026-01-02)
- **Aeonis AI Assistant** - Spawn an LLM-powered AI companion that follows, chats, and executes commands
- **Command Execution** - AI can run any Minecraft command with safety blocks
- **Script System** - Create and save reusable multi-command scripts
- **Custom Commands** - Define your own command aliases through AI
- **Automation Tasks** - Schedule recurring commands with intervals
- **Improved Navigation** - Better jumping and pathfinding

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


---

## 🌟 What is Aeonis: Command Master?

**Aeonis: Command Master** is the ultimate Fabric mod for Minecraft that lets you **become ANY mob** and use their unique abilities! Whether you want to rain terror as the Ender Dragon, shoot explosive skulls as the Wither, or just be a chicken laying eggs — this mod makes it possible!

---

## 🎯 Main Features

### 🧠 MOB POSSESSION SYSTEM *(Star Feature!)*

<table>
<tr>
<td width="55%">

**Experience Minecraft through the eyes of ANY mob!**

The revolutionary **Mob Possession System** lets you:

- 🎮 **Full WASD Control** — Move, jump, swim, and fly naturally
- 👀 **First-Person View** — See the world from their perspective  
- ⚔️ **Unique Attacks** — Use each mob's special abilities!
- 💀 **Wither Boss** — Shoot explosive Wither Skulls!
- 🐉 **Ender Dragon** — Launch devastating Dragon Fireballs!
- 💨 **Breeze** — Fire Wind Charges that knock enemies back!
- 🔥 **Blaze** — Rapid-fire small fireballs!
- 👻 **Ghast** — Explosive large fireballs!
- 🐔 **Chicken** — Lay eggs! (Yes, really!)
- 🦅 **Flying Mobs** — Full 3D flight with pitch control
- 🌊 **Aquatic Movement** — Swim and dive naturally
- ❤️ **Health Display** — See mob health in action bar

</td>
<td width="45%">

**Commands:**
```
/transform <entity>
/untransform
/exitbody s    (exit to Survival)
/exitbody c    (exit to Creative)
```

**Special Attack Mobs:**
| Mob | Attack | Cooldown |
|-----|--------|----------|
| 💀 Wither | Skulls | 300ms |
| 🐉 Dragon | Fireballs | 400ms |
| 💨 Breeze | Wind Charges | 350ms |
| 🔥 Blaze | Small Fireballs | 250ms |
| 👻 Ghast | Large Fireballs | 600ms |
| 🐔 Chicken | Eggs! | 700ms |
| ⚔️ Others | Melee | 500ms |

**All mob types supported!**

</td>
</tr>
</table>

### 🔥 Special Boss Attacks

| Mob | Attack (Left Click) | Cooldown |
|-----|---------------------|----------|
| 💀 **Wither** | Explosive Wither Skulls | 300ms |
| 🐉 **Ender Dragon** | Dragon Fireballs (Area Damage) | 400ms |
| 💨 **Breeze** | Wind Charges (Knockback) | 350ms |
| 🔥 Others | Melee Attack | 500ms |

---

### 🦾 Custom Mobs & Entities

<table>
<tr>
<td align="center" width="33%">
<h3>🥉 Stalker</h3>
<p>A mysterious copper-infused entity that lurks in the shadows...</p>
</td>
<td align="center" width="33%">
<h3>🔮 More Coming Soon!</h3>
<p>New custom mobs are in development!</p>
</td>
<td align="center" width="33%">
<h3>💡 Suggestions?</h3>
<p>Open an issue with your ideas!</p>
</td>
</tr>
</table>

---

## ⚡ Complete Command Reference

### 🔄 Transformation Commands

| Command | Description | Example |
|---------|-------------|---------|
| `/transform <entity> [variant...]` | Transform into any mob (optionally specify variants) | `/transform minecraft:wither` |
| `/untransform` | Return to your normal player form | `/untransform` |
| `/exitbody s` | Exit to Survival mode | `/exitbody s` |
| `/exitbody c` | Exit to Creative mode | `/exitbody c` |

**Transform Examples:**
```
/transform minecraft:zombie
/transform minecraft:wither
/transform minecraft:ender_dragon
/transform minecraft:breeze
/transform minecraft:phantom
/transform minecraft:ghast
/transform minecraft:bee
/transform minecraft:dolphin
/transform minecraft:creeper
/transform minecraft:skeleton
/transform minecraft:blaze
/transform minecraft:enderman
```

---

### 🎭 Prank & Fun Commands (now `/prank`)

| Command | Description | Example |
|---------|-------------|---------|
| `/prank smite <players>` | Strike with lightning ⚡ | `/prank smite @a` |
| `/prank yeet <players>` | Launch into the sky 🚀 | `/prank yeet Alex` |
| `/prank disco <players>` | Party effects! 🎵 | `/prank disco Steve` |
| `/prank supersize <players>` | Make HUGE 🦖 | `/prank supersize Alex` |
| `/prank smol <players>` | Make tiny 🐜 | `/prank smol Steve` |
| `/prank chaos <players>` | 3 random effects 🎲 | `/prank chaos Alex` |
| `/prank rocket <players>` | Rocket launch! 🚀 | `/prank rocket Steve` |
| `/prank spin <players> <times>` | Spin around 🌀 | `/prank spin Alex 20` |
| `/prank freeze <players>` | Freeze solid ❄️ | `/prank freeze Steve` |
| `/prank burn <players>` | Set on fire 🔥 | `/prank burn Alex` |
| `/prank drunk <players>` | Nausea + blindness 🍺 | `/prank drunk @a` |

**Examples:**
```
/prank smite @a
/prank yeet Alex
/prank disco Steve
/prank supersize Alex
/prank smol Steve
/prank chaos Alex
/prank rocket Steve
/prank spin Alex 20
/prank freeze Steve
/prank burn Alex
/prank drunk @a
```

---

### 💀 Event Tools (`/event`)

| Command | Description |
|---------|-------------|
| `/event ambush` | Surprise mob ambush |
| `/event scan` | Scan for hostiles nearby |
| `/event thunder` | Start thunder dome event |
| `/event copper` | Drop copper on players |
| `/event time <ticks>` | Warp time forward |
| `/event cleanse <players>` | Remove all effects |
| `/event crit_save` | Critical save (revive) |
| `/event pro_gamer` | Pro gamer mode |
| `/event exitbody <s|c>` | Exit body (Survival/Creative) |

---

### 🔊 Ability Commands (now `/ability`)

| Command | Description |
|---------|-------------|
| `/ability mimic zombie` | 🧟 Play zombie ambient sound |
| `/ability mimic wither` | 💀 Play wither ambient sound |
| `/ability mimic ghast` | 👻 Play ghast cry sound |
| `/ability mimic dragon` | 🐉 Play dragon growl sound |
| `/ability dash` | Quick dash forward |
| `/ability blink <range>` | Teleport a short distance |
| `/ability jump` | Moon jump |
| `/ability roar` | Warden roar |
| `/ability darkness` | Darkness pulse |
| `/ability summon vex` | Summon pet vex |
| `/ability summon wolves <count>` | Summon spirit wolves |

---

### 🤖 AI Tools (now `/ai`)

| Command | Description |
|---------|-------------|
| `/ai chaotic <entities>` | Enable chaotic AI (experimental) |
| `/ai chaotic stop` | Disable chaotic AI |
| `/ai director <entities> walk_to <x y z> [speed]` | Walk entities to position |
| `/ai director <entities> look_at <target>` | Make entities look at a target |
| `/ai director <entities> attack <target>` | Make entities attack a target |
| `/ai director <entities> stop` | Stop all orders for entities |

---

## 🤖 Aeonis AI Assistant - Complete Manual

<p align="center">
  <img src="https://readme-typing-svg.herokuapp.com?font=Fira+Code&weight=600&size=20&pause=1000&color=00FF88&center=true&vCenter=true&width=600&lines=AI-Powered+In-Game+Assistant;Execute+Commands+with+OP+Privileges;Create+Scripts+%26+Automations;LLM+Integration+(OpenAI%2C+Gemini%2C+OpenRouter)" alt="AI Features" />
</p>

### 🚀 Quick Start

| Command | Description |
|---------|-------------|
| `/ai llm spawn` | Spawn Aeonis AI assistant |
| `/ai llm despawn` | Despawn Aeonis |
| `/ai llm config` | Open configuration UI (client-side) |

### 🔌 Supported LLM Providers & Models

<table>
<tr>
<th>Provider</th>
<th>API Endpoint</th>
<th>Supported Models</th>
</tr>
<tr>
<td><b>🟢 OpenAI</b></td>
<td><code>https://api.openai.com/v1</code></td>
<td>
• gpt-4o<br>
• gpt-4o-mini (default)<br>
• gpt-4-turbo<br>
• gpt-5 / gpt-5.1 / gpt-5.2<br>
• gpt-4<br>
• gpt-3.5-turbo
</td>
</tr>
<tr>
<td><b>🔵 Gemini</b></td>
<td><code>https://generativelanguage.googleapis.com/v1beta</code></td>
<td>
• gemini-2.0-flash-exp<br>
• gemini-2.5-pro<br>
• gemini-2.5-flash<br>
• gemini-1.0-pro
</td>
</tr>
<tr>
<td><b>🟣 OpenRouter</b></td>
<td><code>https://openrouter.ai/api/v1</code></td>
<td>
• openai/gpt-oss-20b:free<br>
• nousresearch/deephermes-3-llama-3-8b-preview:free<br>
• mistralai/mistral-small-3.1-24b-instruct:free<br>
• anthropic/claude-3.5-sonnet<br>
• google/gemini-pro-1.5<br>
• meta-llama/llama-3.1-70b-instruct<br>
• mistralai/mixtral-8x7b-instruct
</td>
</tr>
</table>

> 💡 **Tip:** OpenRouter offers FREE models! Perfect for testing without API costs.

---

### ⚡ AI Command Execution

Aeonis can execute **ANY Minecraft command** with full OP privileges! Just chat with Aeonis and ask.

**Tag Syntax:** `[CMD: /command]`

**Examples:**
```
User: "Can you give me some diamonds?"
Aeonis: "Sure! [CMD: /give @p diamond 64]"

User: "Make it daytime please"
Aeonis: "Done! [CMD: /time set day]"

User: "Spawn some pigs around me"
Aeonis: "Here they come! [CMD: /summon pig ~ ~ ~] [CMD: /summon pig ~2 ~ ~] [CMD: /summon pig ~-2 ~ ~]"
```

**Common Commands Aeonis Uses:**
| Request | Command Executed |
|---------|-----------------|
| Give items | `/give @p <item> <count>` |
| Set time | `/time set day/night/noon` |
| Weather | `/weather clear/rain/thunder` |
| Teleport | `/tp @p <x> <y> <z>` |
| Spawn mobs | `/summon <entity> ~ ~ ~` |
| Effects | `/effect give @p <effect> <duration>` |
| Gamemode | `/gamemode creative/survival @p` |
| Build | `/fill` and `/setblock` commands |

---

### 📜 Script System

Create **persistent scripts** that save to disk and survive game restarts!

**Location:** `<game_dir>/aeonis/scripts/`

**Create Script:**
```
[SCRIPT: starter_kit]
# Give basic survival gear
/give @p iron_sword 1
/give @p iron_pickaxe 1
/give @p torch 64
/give @p cooked_beef 32
/effect give @p regeneration 60 1
[/SCRIPT]
```

**Run Script:** `[RUNSCRIPT: starter_kit]`

**Example Scripts:**
| Script Name | Purpose |
|-------------|---------|
| `starter_kit` | Basic survival gear |
| `quick_base` | Build a simple shelter |
| `combat_prep` | Battle buffs and gear |
| `night_vision` | Permanent cave exploration |

---

### 🎮 Custom Commands

Create **new abilities** that Aeonis (or you) can use anytime!

**Location:** `<game_dir>/aeonis/commands/`

**Create Custom Command:**
```
[NEWCMD: battle_ready | Prepares player for combat]
/effect give @p strength 300 2
/effect give @p resistance 300 1
/effect give @p speed 300 1
/give @p golden_apple 8
[/NEWCMD]
```

**Run Custom Command:** `[RUNCMD: battle_ready]`

---

### ⏰ Automation System

Schedule **repeating tasks** that run automatically!

**Location:** `<game_dir>/aeonis/automation/`

**Create Automation:**
```
[AUTOMATION: auto_heal | 60]
/effect give @a regeneration 5 1
[/AUTOMATION]
```

**Stop Automation:** `[STOPAUTO: auto_heal]`

**Automation Ideas:**
| Name | Interval | Purpose |
|------|----------|---------|
| `keep_day` | 300s | Maintain daytime |
| `auto_heal` | 60s | Periodic healing |
| `mob_alert` | 30s | Creeper warnings |
| `hunger_fix` | 120s | Auto saturation |

---

### 🚶 Physical Movement

Aeonis has a **physical body** and can move around!

| Command | Description |
|---------|-------------|
| `/ai llm follow <player>` | Follow a player |
| `/ai llm walkTo <x> <y> <z>` | Walk to coordinates |
| `/ai llm build <preset>` | Build structures |
| `/ai llm stop` | Stop current action |

**Build Presets:** `small_hut`, `tower`, `wall_segment`, `platform`

---

### 🔒 Safety Restrictions

For server safety, these commands are **blocked**:
- `/stop` (server shutdown)
- `/ban`, `/ban-ip`
- `/kick`
- `/op`, `/deop`
- `/whitelist`

---

### 📋 Quick Reference

| Tag | Purpose | Example |
|-----|---------|---------|
| `[CMD: /...]` | Execute command | `[CMD: /give @p diamond 64]` |
| `[SCRIPT: name]...[/SCRIPT]` | Create script | Multi-line script |
| `[RUNSCRIPT: name]` | Run script | `[RUNSCRIPT: starter_kit]` |
| `[NEWCMD: name \| desc]...[/NEWCMD]` | Create command | Custom ability |
| `[RUNCMD: name]` | Run command | `[RUNCMD: battle_ready]` |
| `[AUTOMATION: name \| sec]...[/AUTOMATION]` | Auto task | Repeating action |
| `[STOPAUTO: name]` | Stop automation | `[STOPAUTO: keep_day]` |

---

### 📊 System & Feature Commands

| Command | Description |
|---------|-------------|
| `/aeonis soul` | Enter soul mode (spectator + possess with P key) |
| `/aeonis unsoul` | Exit soul mode |
| `/aeonis sys ping` | Show server/mod stats |
| `/aeonis sys story` | Aeonis story info |
| `/aeonis help` | Show help message |
| `/aeonis reload` | Reload all mod features and reset states |
| `/aeonis features extra_mobs <true/false>` | Toggle Aeonis custom mobs |

---

### ⚙️ Feature Toggle Commands

| Command | Description |
|---------|-------------|
| `/aeonis features extra_mobs` | Check if extra mobs (Stalkers) are enabled |
| `/aeonis features extra_mobs true` | Enable Aeonis custom mob spawning |
| `/aeonis features extra_mobs false` | Disable Aeonis custom mob spawning |

---

## 🦾 Custom Mobs

### 🥉 Copper Stalker

A mysterious copper-infused entity that:
- Spawns at night in the Overworld (when extra_mobs enabled)
- Periodically goes invisible
- Lurks in the shadows...

Enable with: `/aeonis features extra_mobs true`

---

## 🎮 Controls While Transformed

| Key | Action |
|-----|--------|
| **W/A/S/D** | Move in that direction |
| **Space** | Jump (ground) / Fly up (flying mobs) / Swim up (water) |
| **Shift** | Sneak / Fly down / Sink |
| **Mouse** | Look around (entity rotates with you) |
| **Left Click** | Attack / Shoot projectile |
| **T** | Teleport (Enderman only) |
| **P** | Soul Possess (in Soul Mode) |

---

## 📦 Installation

1. Install **Fabric Loader** for Minecraft 1.21.10
2. Install **Fabric API**
3. Install **Fabric Language Kotlin**
4. Download **Aeonis: Command Master** and place in `mods` folder
5. Launch and enjoy!

---

## 📋 Requirements

- Minecraft **1.21.10**
- Fabric Loader **0.18.4+**
- Fabric API **0.138.4+**
- Fabric Language Kotlin **1.13.8+**

---

## 🤝 Credits

- **Developer:** QuantumCreeper / TheCorrectSynovian
- **Mod ID:** `aeonis-manager`
- **Version:** 1.6.0

---

## 📜 License

This project is licensed under the **MIT License** - see the [LICENSE](LICENSE) file for details.

---

## 💖 Support the Mod

**If you enjoy Aeonis: Command Master, consider:**

<p align="center">
  <a href="https://www.youtube.com/@quantumcreeper"><img src="https://img.shields.io/badge/Subscribe-YouTube-red?style=for-the-badge&logo=youtube" alt="YouTube"></a>
  <a href="https://github.com/TheCorrectSynovian/Aeonis-mod"><img src="https://img.shields.io/badge/Star-GitHub-yellow?style=for-the-badge&logo=github" alt="GitHub Stars"></a>
</p>

---

<p align="center">
  <b>⚠️ IMPORTANT NOTICE ⚠️</b>
</p>

<p align="center">
  <b>NOT TO BE CONFUSED WITH ANY OTHER MOD!</b><br/>
  <i>I've <b>RENAMED</b> the EMBERVEIL modpack (previously "Aeonis") to <b>BLITZ</b> to avoid confusion.</i><br/>
  <b>AEONIS = This Command Master Mod ONLY!</b>
</p>

---

<p align="center">
  <img src="https://capsule-render.vercel.app/api?type=waving&color=gradient&customColorList=6,12,19&height=100&section=footer" alt="Footer Wave"/>
</p>

<p align="center">
  <b>⚡ Transform. Attack. Dominate. ⚡</b>
</p>

<p align="center">
  <img src="https://readme-typing-svg.herokuapp.com?font=Fira+Code&weight=500&size=18&pause=1000&color=FFD700&center=true&vCenter=true&width=500&lines=Made+with+%E2%9D%A4%EF%B8%8F+by+QuantumCreeper;Subscribe+on+YouTube!;Star+%E2%AD%90+the+repo+if+you+like+it!" alt="Footer" />
</p>

<p align="center">
  <sub>⚡ Aeonis: Command Master — Unleash Your Power ⚡</sub>
</p>
