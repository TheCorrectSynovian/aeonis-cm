<!--
CurseForge description note:
Keep this file lightweight and clarity-first.
Avoid heavy HTML, animation embeds, and overly decorative formatting.
Use concise sections and practical command examples.
-->

# AEONIS: Command Master

Transform into mobs, control unique abilities, run AI-powered command workflows, and explore custom dimension content in one Fabric mod.

From the creators of BLITZ (formerly Emberveil).

## Version and Platform

- Current release: 4.0.0 (First Stable 26.1 Release)
- Minecraft: 26.1 (26.1.1 hotfix compatible)
- Loader: Fabric
- License: MIT

## What Aeonis Does

Aeonis combines several systems in one mod:

- Mob possession and transformed combat
- AI assistant command execution and automation tools
- Utility/admin commands for events, abilities, and fun tools
- Custom entities and progression content
- Dimension and worldgen expansions

## Main Features

### 1) Mob Possession System

Take direct control of many vanilla mobs in first-person with movement adapted to each mob type.

- Flight support for flying mobs
- Aquatic movement for water mobs
- Left-click special attacks for select mobs
- Cooldown-aware combat flow

Core commands:

```mcfunction
/transform <entity>
/untransform
/exitbody s
/exitbody c
```

Examples of special attacks:

- Wither: explosive skulls
- Ender Dragon: dragon fireballs
- Breeze: wind charges
- Blaze/Ghast: projectile fire attacks

### 2) AI Assistant (LLM)

Spawn an AI companion that can chat, generate commands, and execute allowed in-game workflows.

Core commands:

```mcfunction
/ai llm spawn
/ai llm despawn
/ai llm config
```

Supports:

- OpenAI
- Google Gemini
- OpenRouter

Workflow features:

- Command execution tags
- Persistent scripts
- Custom command packs
- Scheduled automation tasks

### 3) Command Systems

Aeonis command families include:

- /prank (fun and chaos tools)
- /ability (mob-like powers)
- /event (event/admin actions)
- /ai (AI controls)
- /aeonis (system/help/features)

Sample prank commands:

```mcfunction
/prank smite <players>
/prank yeet <players>
/prank disco <players>
/prank morph <players> <chicken|pig|goat|frog|parrot|random_funny>
```

Sample ability commands:

```mcfunction
/ability dash
/ability blink <range>
/ability roar
/ability summon vex
```

### 4) World and Entity Content

- Ancard content line and visual atmosphere systems
- Deeper Dark dimension expansion content (biomes, structures, worldgen pipeline)
- Custom mobs including Copper Stalker line and additional ecosystem entities

## 4.0.0 Highlights (First Stable 26.1)

- First non-snapshot stable build on Minecraft 26.1
- Migration cycle completed from 1.21.11 to 26.1
- Transform/untransform cleanup reliability improvements
- Camera/interaction stabilization for transformed gameplay
- Warning policy remaster (less global spam, targeted notices only)
- Localization refresh for active Aeonis namespaces

## Gameplay Notes

- Soul mode command: /aeonis soul
- Possess target in soul mode with P
- Use /aeonis help for full in-game command help

## Installation

1. Install Fabric Loader for Minecraft 26.1
2. Install Fabric API
3. Install Fabric Language Kotlin
4. Place Aeonis jar in the mods folder
5. Launch Minecraft

## Requirements

- Minecraft 26.1
- Java 25+ recommended for this release line
- Fabric Loader 0.18.5+
- Fabric API 0.144.3+26.1
- Fabric Language Kotlin 1.13.10+kotlin.2.3.20

## Quick Start

```mcfunction
/aeonis help
/transform minecraft:ender_dragon
/untransform
/ai llm spawn
```

For technical detail and full release history, see the project README and changelog in the source repository.