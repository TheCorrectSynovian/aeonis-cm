<p align="center">
  <img src="https://media.forgecdn.net/avatars/console-avatars/avatar_15441b09-293b-46b5-9f56-a72f7d35a1b7.png" alt="Aeonis: Command Master Banner"/>
</p>

<h1 align="center">⚡ Aeonis: Command Master v1.3 ⚡</h1>

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
  <img src="https://img.shields.io/badge/Version-1.3-purple?style=for-the-badge" alt="Version">
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


## 🆕 What's New in v1.3?

### ✨ Major Overhaul
- **Command system refactored:** `/x` split into `/prank`, `/ability`, `/game`, `/aeonis`, `/ai`, `/transform`.
- **Multi-target pranks:** `/prank smite @a` and more.
- **/aeonis help** now shows all new commands and groups.
- **/ai chaotic <entities>**: Experimental chaotic AI engine (lava-seeking, cliff-jumping, spinning, speed boost).
- **/ai chaotic stop**: Disable chaotic AI for all affected entities.
- **/aeonis reload**: Reload all mod features and reset states.
- **/aeonis sys ping**: Real-time server/mod stats.

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

### 💀 Game Tools (now `/game`)

| Command | Description |
|---------|-------------|
| `/game ambush` | Surprise mob ambush |
| `/game scan` | Scan for hostiles nearby |
| `/game thunder` | Start thunder dome event |
| `/game copper` | Drop copper on players |
| `/game time <ticks>` | Warp time forward |
| `/game cleanse <players>` | Remove all effects |
| `/game crit_save` | Critical save (revive) |
| `/game pro_gamer` | Pro gamer mode |
| `/game exitbody <s|c>` | Exit body (Survival/Creative) |

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

### 📊 System & Feature Commands

| Command | Description |
|---------|-------------|
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
- **Version:** 1.3

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
