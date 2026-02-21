<p align="center">
  <img src="https://media.forgecdn.net/avatars/console-avatars/avatar_15441b09-293b-46b5-9f56-a72f7d35a1b7.png" alt="Aeonis Banner"/>
</p>

<h1 align="center">⚡ Aeonis v3.0.0 — The Revolution ⚡</h1>

<p align="center">
  <b>Take control. Command anything. Master the game.</b>
</p>

<p align="center">
  <i>From the creators of <b>EMBERVEIL MODPACK</b></i>
</p>

<p align="center">
  <a href="https://fabricmc.net/"><img src="https://img.shields.io/badge/Fabric-1.21.11-blue?style=for-the-badge&logo=fabric&logoColor=white" alt="Fabric"></a>
  <a href="https://quiltmc.org/"><img src="https://img.shields.io/badge/Quilt-1.21.11-2E2E2E?style=for-the-badge&logo=quilt&logoColor=white" alt="Quilt"></a>
  <a href="https://www.curseforge.com/minecraft/mc-mods/aeonis-command-master"><img src="https://img.shields.io/badge/CurseForge-Download-orange?style=for-the-badge&logo=curseforge&logoColor=white" alt="CurseForge"></a>
  <a href="https://modrinth.com/mod/aeonis-command-master"><img src="https://img.shields.io/badge/Modrinth-Download-green?style=for-the-badge&logo=modrinth&logoColor=white" alt="Modrinth"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-yellow?style=for-the-badge" alt="License"></a>
  <img src="https://img.shields.io/badge/Version-3.0.0-purple?style=for-the-badge" alt="Version">
</p>

<p align="center">
  <a href="https://www.youtube.com/@quantumcreeper"><img src="https://img.shields.io/badge/YouTube-Subscribe-red?style=for-the-badge&logo=youtube&logoColor=white" alt="YouTube"></a>
  <a href="https://github.com/TheCorrectSynovian/Aeonis-mod"><img src="https://img.shields.io/badge/GitHub-Source-181717?style=for-the-badge&logo=github&logoColor=white" alt="GitHub"></a>
</p>

---

# Aeonis — The Revolution (v3.0.0)

**Aeonis** is a multi-system Minecraft framework combining **mob possession**, **AI-assisted command systems**, **custom dimensions**, **unique mobs**, and **advanced rendering** into one unified experience.

This release is a massive evolution: new dimensions, new worldgen, dozens of new mobs, new blocks, and a deeper framework for visuals and progression.

---

## 🆕 What’s New in 3.0.0 — The Revolution

### 🌌 New Dimension: **Deeper Dark**
A full-scale underworld built from the ground up.
- **7 unique biomes**: Amethyst Mines, Ancient Dark, Deep Oasis, Deeper Dark, Deeper Dark Cavern, Obsidian Barrier, Volcanic Caverns
- **New worldgen structures**: Ancient Fortress, Ancient Village, Laboratory, Amethyst Mineshaft, Sculk Trap, and more
- **Custom noise + placed features** for caves, lava systems, fossil fields, sculk growth, and rare terrain shapes

### 🕳️ Ancard Expansion & Atmosphere
The Ancard dimension gets a full pipeline for visuals and events.
- **Custom fog + post-processing** and dimension renderer
- **Dynamic lightning + eclipse events**
- **New Ancard blocks** (stone, deepslate, basalt, obsidian shale, bloodroot/veilshade biomes, custom ores)

### 🧟 New Mob Ecosystems (Ancard + Arda)
A huge roster of new mobs and bosses:
- **Ancard creatures**: Ancard Sovereign, Ancient Colossus, Ash Stalker, Bloodroot Fiend, Boneweaver, Crypt Mite, Echo Wisp, Obelisk Sentinel, Rift Screecher, Ruin Hound, Shade Lurker, Sporeback, Veil Mimic, Veilshade Watcher, and more
- **Arda/ported mobs**: Radioactive Warden, Sculk Boss 1, Sculk Golem Boss Reloaded, Shadow Hunter, Sculk Enderman, Sculk Skeleton, Sculk Slime, Sculk Creeper, Sculk Creaking, and more

### 🤝 Companion Bot System
Summon your own helper bot with new commands:
- `/comp spawn` — Spawn or replace your companion
- `/comp comehere` — Recall it to you
- `/comp stats` — View its status and strength
- `/comp dismiss` — Remove it

### 🧱 New Blocks & Mechanics
- **Safe Chest** — high‑resistance storage that can connect as single, double, or triple units
- **Permanent Flame** — dark red fire that cannot be extinguished by interaction
- **New enchantments**: ClearSight, DarkSpeed, Resonate, SafeFall, Shrieker Sense, Sonic Boom, Undermine
- **New advancements, effects, and custom loot/recipes** to support progression

---

## ⭐ Core Features (Still Here, Stronger Than Ever)

### 🧠 Mob Possession System (Star Feature)
Transform into **any mob** and control it in full first-person with attacks, flight, swimming, and special abilities.

**Main commands:**
```
/transform <entity>
/untransform
/exitbody s
/exitbody c
```

### 🤖 Aeonis AI Assistant
A fully in-game AI companion that can execute commands, manage scripts, and automate gameplay.

**Quick commands:**
```
/ai llm spawn
/ai llm despawn
/ai llm config
```

### 🎭 Minigames & Event Systems
Includes the experimental **Prop Hunt** system, Manhunt improvements, and event tools for servers.

---

## 🌍 Dimensions & Travel

### Ancard Dimension
- Built on **reinforced deepslate portals**
- **Ancard Lighter** activates the portal using soul fire
- The corrupted Deep Dark experience with custom ambience

### Deeper Dark Dimension
- Noise‑driven caves and ancient ecosystems
- Multiple biomes and structure sets for deep exploration

---

## ✅ Requirements

- **Minecraft** 1.21.11
- **Java** 21+
- **Fabric Loader** 0.18.4+ **or** **Quilt Loader** 0.17.0+
- **Fabric API** 0.141.1+ **or** **Quilted Fabric API**
- **Fabric Language Kotlin** 1.13.8+
- **GeckoLib** 5.4.3+

---

## 📦 Installation

1. Install **Fabric** or **Quilt** Loader for Minecraft 1.21.11
2. Install **Fabric API** (or **Quilted Fabric API**)
3. Install **Fabric Language Kotlin**
4. Install **GeckoLib**
5. Drop **Aeonis** into your `mods` folder

---

## 📌 Helpful Commands

**Feature toggle:**
```
/aeonis features extra_mobs true
```

**System info:**
```
/aeonis sys ping
```

---

## 📜 Changelog
See `CHANGELOG.md` for full technical details and history.

---

## 🤝 Credits
- **Developer:** QuantumCreeper / TheCorrectSynovian
- **Mod ID:** `aeonis-manager`
- **Version:** 3.0.0

---

## 📄 License
This project is licensed under the **MIT License** — see `LICENSE`.

---

<p align="center">
  <b>⚠️ IMPORTANT NOTICE ⚠️</b>
</p>

<p align="center">
  <b>NOT TO BE CONFUSED WITH ANY OTHER MOD!</b><br/>
  <i>I've <b>RENAMED</b> the EMBERVEIL modpack (previously "Aeonis") to <b>BLITZ</b> to avoid confusion.</i><br/>
  <b>AEONIS = This Mod ONLY!</b>
</p>

---

<p align="center">
  <img src="https://capsule-render.vercel.app/api?type=waving&color=gradient&customColorList=6,12,19&height=100&section=footer" alt="Footer Wave"/>
</p>

<p align="center">
  <b>⚡ Transform. Attack. Dominate. ⚡</b>
</p>

<p align="center">
  <sub>⚡ Aeonis — Unleash Your Power ⚡</sub>
</p>
