package com.qc.aeonis.minigame.prophunt

import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.slf4j.LoggerFactory

/**
 * ╔════════════════════════════════════════════════════════════════════════════╗
 * ║                      PROP HUNT REWARDS SYSTEM                              ║
 * ║                                                                            ║
 * ║  Handles all reward mechanics for Prop Hunt including:                     ║
 * ║  - XP rewards for various achievements                                     ║
 * ║  - Item rewards for winning                                                ║
 * ║  - Status effect bonuses                                                   ║
 * ║  - Unlock system for special props                                         ║
 * ║  - Achievement tracking                                                    ║
 * ╚════════════════════════════════════════════════════════════════════════════╝
 */
object PropHuntRewards {
    private val logger = LoggerFactory.getLogger("aeonis-prophunt-rewards")
    
    // ════════════════════════════════════════════════════════════════════════
    // XP REWARDS
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * Rewards for various in-game actions.
     */
    object XPRewards {
        const val PROP_SURVIVE_ROUND = 50
        const val PROP_SURVIVE_LONG = 100 // Survived more than 2 minutes
        const val PROP_TAUNT_BONUS = 10 // Taunted and didn't get caught
        const val PROP_WIN_TEAM = 150
        
        const val HUNTER_FIND_PROP = 30
        const val HUNTER_FIRST_BLOOD = 50 // First prop found
        const val HUNTER_LAST_KILL = 75 // Found the last prop
        const val HUNTER_WIN_TEAM = 150
        const val HUNTER_PERFECT = 200 // Found all props, no false hits
        
        const val PARTICIPATION = 25
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // REWARD DISTRIBUTION
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * Awards XP to a player with visual feedback.
     */
    fun awardXP(player: ServerPlayer, amount: Int, reason: String) {
        player.giveExperiencePoints(amount)
        
        // Show message
        player.sendSystemMessage(Component.literal("§a+$amount XP §7($reason)"))
        
        // Play level up sound if significant
        if (amount >= 50) {
            player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5f, 1.2f)
        }
    }
    
    /**
     * Awards all end-of-round rewards.
     */
    fun awardRoundRewards(game: PropHuntGame, winner: PropHuntTeam) {
        val level = game.level
        
        for (playerData in game.players.values) {
            val player = level.server?.playerList?.getPlayer(playerData.uuid) ?: continue
            
            // Participation reward
            awardXP(player, XPRewards.PARTICIPATION, "Participation")
            
            when (playerData.team) {
                PropHuntTeam.PROP -> {
                    // Prop survived
                    if (playerData.isAlive) {
                        awardXP(player, XPRewards.PROP_SURVIVE_ROUND, "Survived as prop")
                        
                        // Long survival bonus
                        if (playerData.survivalTicks > 2400) { // 2 minutes
                            awardXP(player, XPRewards.PROP_SURVIVE_LONG, "Long survival bonus")
                        }
                    }
                    
                    // Team win bonus
                    if (winner == PropHuntTeam.PROP) {
                        awardXP(player, XPRewards.PROP_WIN_TEAM, "Props won!")
                        awardWinnerEffects(player)
                    }
                }
                
                PropHuntTeam.HUNTER -> {
                    // Props found bonus
                    if (playerData.propsFound > 0) {
                        awardXP(player, XPRewards.HUNTER_FIND_PROP * playerData.propsFound, 
                            "Found ${playerData.propsFound} prop(s)")
                    }
                    
                    // Perfect hunter bonus (no false hits)
                    if (playerData.propsFound > 0 && playerData.falseHits == 0) {
                        awardXP(player, XPRewards.HUNTER_PERFECT, "Perfect hunter!")
                    }
                    
                    // Team win bonus
                    if (winner == PropHuntTeam.HUNTER) {
                        awardXP(player, XPRewards.HUNTER_WIN_TEAM, "Hunters won!")
                        awardWinnerEffects(player)
                    }
                }
                
                PropHuntTeam.NONE -> {
                    // Spectators get participation only
                }
            }
        }
    }
    
    /**
     * Gives temporary positive effects to winners.
     */
    private fun awardWinnerEffects(player: ServerPlayer) {
        player.addEffect(MobEffectInstance(MobEffects.REGENERATION, 600, 1, false, true, true))
        player.addEffect(MobEffectInstance(MobEffects.ABSORPTION, 1200, 2, false, true, true))
        player.addEffect(MobEffectInstance(MobEffects.GLOWING, 200, 0, false, true, true))
        
        // Victory particles
        val level = player.level() as? ServerLevel ?: return
        level.sendParticles(
            ParticleTypes.TOTEM_OF_UNDYING,
            player.x, player.y + 1, player.z,
            50, 0.5, 1.0, 0.5, 0.1
        )
    }
    
    /**
     * Awards special bonuses for first blood.
     */
    fun awardFirstBlood(hunter: ServerPlayer, game: PropHuntGame) {
        awardXP(hunter, XPRewards.HUNTER_FIRST_BLOOD, "First Blood!")
        
        game.broadcast("§c§l🩸 FIRST BLOOD! §e${hunter.name.string} §7found the first prop!")
        
        hunter.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 1.0f)
    }
    
    /**
     * Awards bonus for finding the last prop.
     */
    fun awardLastKill(hunter: ServerPlayer, game: PropHuntGame) {
        awardXP(hunter, XPRewards.HUNTER_LAST_KILL, "Final Kill!")
        
        hunter.playNotifySound(SoundEvents.WITHER_DEATH, SoundSource.PLAYERS, 0.5f, 1.5f)
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // ITEM REWARDS
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * Awards random loot to game winners.
     */
    fun awardLoot(player: ServerPlayer, winner: Boolean) {
        val lootTable = if (winner) WINNER_LOOT else PARTICIPATION_LOOT
        val roll = Math.random()
        
        var cumulative = 0.0
        for ((item, chance) in lootTable) {
            cumulative += chance
            if (roll < cumulative) {
                val stack = item.invoke()
                if (!player.inventory.add(stack)) {
                    player.drop(stack, false)
                }
                player.sendSystemMessage(Component.literal("§a[PropHunt] §7You received: §e${stack.displayName.string}"))
                break
            }
        }
    }
    
    /** Winner loot table with probabilities */
    private val WINNER_LOOT: List<Pair<() -> ItemStack, Double>> = listOf(
        { ItemStack(Items.DIAMOND, 1 + (Math.random() * 2).toInt()) } to 0.15,
        { ItemStack(Items.EMERALD, 2 + (Math.random() * 4).toInt()) } to 0.20,
        { ItemStack(Items.GOLDEN_APPLE, 1) } to 0.25,
        { ItemStack(Items.ENDER_PEARL, 2 + (Math.random() * 3).toInt()) } to 0.20,
        { ItemStack(Items.EXPERIENCE_BOTTLE, 3 + (Math.random() * 5).toInt()) } to 0.20
    )
    
    /** Participation loot table */
    private val PARTICIPATION_LOOT: List<Pair<() -> ItemStack, Double>> = listOf(
        { ItemStack(Items.IRON_INGOT, 1 + (Math.random() * 3).toInt()) } to 0.30,
        { ItemStack(Items.GOLD_INGOT, 1 + (Math.random() * 2).toInt()) } to 0.25,
        { ItemStack(Items.EMERALD, 1) } to 0.20,
        { ItemStack(Items.COOKED_BEEF, 4 + (Math.random() * 4).toInt()) } to 0.25
    )
}

/**
 * ╔════════════════════════════════════════════════════════════════════════════╗
 * ║                      PROP HUNT SOUND MANAGER                               ║
 * ║                                                                            ║
 * ║  Centralized sound management for immersive audio feedback.                ║
 * ╚════════════════════════════════════════════════════════════════════════════╝
 */
object PropHuntSounds {
    
    /**
     * Plays countdown tick sounds.
     */
    fun playCountdownTick(player: ServerPlayer, secondsLeft: Int) {
        val pitch = 1.0f + (10 - secondsLeft.coerceIn(1, 10)) * 0.1f
        player.playNotifySound(SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.MASTER, 0.5f, pitch)
    }
    
    /**
     * Plays round start horn.
     */
    fun playRoundStart(player: ServerPlayer) {
        player.playNotifySound(SoundEvents.RAID_HORN.value(), 
            SoundSource.MASTER, 1.0f, 1.0f)
    }
    
    /**
     * Plays hunters released sound.
     */
    fun playHuntersReleased(player: ServerPlayer) {
        player.playNotifySound(SoundEvents.ENDER_DRAGON_GROWL, SoundSource.MASTER, 0.5f, 1.5f)
    }
    
    /**
     * Plays prop found sound.
     */
    fun playPropFound(level: ServerLevel, x: Double, y: Double, z: Double) {
        level.playSound(null, x, y, z, SoundEvents.PLAYER_DEATH, SoundSource.PLAYERS, 1.0f, 1.0f)
    }
    
    /**
     * Plays victory fanfare.
     */
    fun playVictory(player: ServerPlayer) {
        player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 1.0f, 1.0f)
    }
    
    /**
     * Plays defeat sound.
     */
    fun playDefeat(player: ServerPlayer) {
        player.playNotifySound(SoundEvents.WITHER_DEATH, SoundSource.MASTER, 0.3f, 0.5f)
    }
    
    /**
     * Plays time warning sound.
     */
    fun playTimeWarning(player: ServerPlayer, urgent: Boolean) {
        if (urgent) {
            player.playNotifySound(SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.MASTER, 1.0f, 0.5f)
        } else {
            player.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.MASTER, 0.5f, 1.0f)
        }
    }
}

/**
 * ╔════════════════════════════════════════════════════════════════════════════╗
 * ║                      PROP HUNT SPECTATOR MODE                              ║
 * ║                                                                            ║
 * ║  Enhanced spectator experience for eliminated players.                     ║
 * ╚════════════════════════════════════════════════════════════════════════════╝
 */
object PropHuntSpectator {
    
    /**
     * Sets up spectator mode for an eliminated player.
     */
    fun enterSpectatorMode(player: ServerPlayer, game: PropHuntGame) {
        // Already handled by setting game mode to spectator
        // This provides additional features
        
        player.sendSystemMessage(Component.literal(""))
        player.sendSystemMessage(Component.literal("§8§l════════ SPECTATOR MODE ════════"))
        player.sendSystemMessage(Component.literal("§7You can now fly around and watch!"))
        player.sendSystemMessage(Component.literal("§7• §eLeft-click§7 a player to follow them"))
        player.sendSystemMessage(Component.literal("§7• §eScroll§7 to cycle through players"))
        player.sendSystemMessage(Component.literal("§7• §eShift§7 to stop following"))
        player.sendSystemMessage(Component.literal("§8§l══════════════════════════════════"))
        player.sendSystemMessage(Component.literal(""))
    }
    
    /**
     * Teleports spectator to a random alive player.
     */
    fun teleportToRandomPlayer(spectator: ServerPlayer, game: PropHuntGame) {
        val alivePlayers = game.players.values.filter { it.isAlive }
        if (alivePlayers.isEmpty()) return
        
        val target = alivePlayers.random()
        val player = game.level.server?.playerList?.getPlayer(target.uuid) ?: return
        
        spectator.teleportTo(player.x, player.y, player.z)
        spectator.sendSystemMessage(Component.literal("§7Teleported to §e${player.name.string}"))
    }
    
    /**
     * Shows spectator tips periodically.
     */
    fun showSpectatorTip(player: ServerPlayer, game: PropHuntGame) {
        val tips = listOf(
            "§7Tip: §eDouble-tap jump§7 to fly faster!",
            "§7Tip: You can §epass through walls§7 in spectator mode!",
            "§7Tip: Watch carefully to learn new hiding spots!",
            "§7Tip: §e${game.getProps().count { it.isAlive }}§7 props remaining!",
            "§7Tip: The round ends when all props are found or time runs out!"
        )
        
        player.sendSystemMessage(Component.literal(tips.random()))
    }
}

/**
 * ╔════════════════════════════════════════════════════════════════════════════╗
 * ║                      PROP HUNT PARTICLE EFFECTS                            ║
 * ║                                                                            ║
 * ║  Centralized particle effect management for visual feedback.               ║
 * ╚════════════════════════════════════════════════════════════════════════════╝
 */
object PropHuntParticles {
    
    /**
     * Creates transformation particles.
     */
    fun spawnTransformEffect(level: ServerLevel, x: Double, y: Double, z: Double) {
        level.sendParticles(ParticleTypes.WITCH, x, y + 1, z, 30, 0.5, 1.0, 0.5, 0.1)
        level.sendParticles(ParticleTypes.PORTAL, x, y + 0.5, z, 50, 0.5, 0.5, 0.5, 0.5)
    }
    
    /**
     * Creates prop caught explosion.
     */
    fun spawnCaughtEffect(level: ServerLevel, x: Double, y: Double, z: Double) {
        level.sendParticles(ParticleTypes.EXPLOSION, x, y + 1, z, 5, 0.5, 0.5, 0.5, 0.0)
        level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, x, y + 1, z, 20, 0.5, 0.5, 0.5, 0.1)
        level.sendParticles(ParticleTypes.SOUL, x, y + 0.5, z, 15, 0.3, 0.5, 0.3, 0.05)
    }
    
    /**
     * Creates scanner pulse rings.
     */
    fun spawnScannerPulse(level: ServerLevel, x: Double, y: Double, z: Double, radius: Double) {
        val particles = (radius * 8).toInt()
        for (i in 0 until particles) {
            val angle = (2 * Math.PI / particles) * i
            val px = x + kotlin.math.cos(angle) * radius
            val pz = z + kotlin.math.sin(angle) * radius
            level.sendParticles(ParticleTypes.SONIC_BOOM, px, y + 1, pz, 1, 0.0, 0.0, 0.0, 0.0)
        }
    }
    
    /**
     * Creates freeze effect.
     */
    fun spawnFreezeEffect(level: ServerLevel, x: Double, y: Double, z: Double) {
        level.sendParticles(ParticleTypes.SNOWFLAKE, x, y + 0.5, z, 20, 0.3, 0.5, 0.3, 0.02)
        level.sendParticles(ParticleTypes.CLOUD, x, y + 0.5, z, 10, 0.2, 0.3, 0.2, 0.01)
    }
    
    /**
     * Creates stun grenade flash.
     */
    fun spawnStunEffect(level: ServerLevel, x: Double, y: Double, z: Double, radius: Double) {
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, x, y + 1, z, 1, 0.0, 0.0, 0.0, 0.0)
        level.sendParticles(ParticleTypes.END_ROD, x, y + 1, z, 50, radius / 2, 1.0, radius / 2, 0.1)
        level.sendParticles(ParticleTypes.FIREWORK, x, y + 1, z, 30, radius / 3, 0.5, radius / 3, 0.05)
    }
    
    /**
     * Creates victory fireworks.
     */
    fun spawnVictoryEffect(level: ServerLevel, x: Double, y: Double, z: Double) {
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, x, y + 1, z, 100, 1.0, 1.5, 1.0, 0.3)
        level.sendParticles(ParticleTypes.FIREWORK, x, y + 2, z, 50, 0.5, 1.0, 0.5, 0.1)
    }
    
    /**
     * Creates trail effect for tracked prop.
     */
    fun spawnTrackerTrail(level: ServerLevel, x: Double, y: Double, z: Double) {
        level.sendParticles(ParticleTypes.GLOW_SQUID_INK, x, y + 0.5, z, 3, 0.1, 0.1, 0.1, 0.0)
    }
    
    /**
     * Creates hint arrow particles pointing in a direction.
     */
    fun spawnHintArrow(level: ServerLevel, x: Double, y: Double, z: Double, dirX: Double, dirZ: Double) {
        for (i in 1..5) {
            val px = x + dirX * i * 0.5
            val pz = z + dirZ * i * 0.5
            level.sendParticles(ParticleTypes.WAX_OFF, px, y + 1.5, pz, 1, 0.0, 0.0, 0.0, 0.0)
        }
    }
}

/**
 * ╔════════════════════════════════════════════════════════════════════════════╗
 * ║                     PROP HUNT ACHIEVEMENT TRACKER                          ║
 * ║                                                                            ║
 * ║  Tracks and awards achievements for memorable moments.                     ║
 * ╚════════════════════════════════════════════════════════════════════════════╝
 */
object PropHuntAchievements {
    
    /**
     * Achievement definitions.
     */
    enum class Achievement(val title: String, val description: String, val icon: String) {
        FIRST_WIN("First Victory", "Win your first Prop Hunt game", "🏆"),
        MASTER_HIDER("Master Hider", "Survive an entire round as a prop", "🎭"),
        SHARP_EYES("Sharp Eyes", "Find 5 props in a single game", "👁"),
        PERFECT_HUNTER("Perfect Hunter", "Win as hunter with no false hits", "🎯"),
        TAUNT_MASTER("Taunt Master", "Use 10 taunts without being caught", "📢"),
        LAST_PROP("Last Prop Standing", "Be the last prop alive (and survive)", "🦸"),
        SPEED_RUNNER("Speed Runner", "Find all props in under 60 seconds", "⚡"),
        STATUE("Living Statue", "Stay frozen for 2 minutes straight", "🗿"),
        TEAM_PLAYER("Team Player", "Play 50 Prop Hunt games", "🤝"),
        VETERAN("Prop Hunt Veteran", "Win 100 Prop Hunt rounds", "⭐")
    }
    
    /**
     * Awards an achievement to a player.
     */
    fun award(player: ServerPlayer, achievement: Achievement) {
        // Check if already has achievement (would need persistent storage)
        
        player.sendSystemMessage(Component.literal(""))
        player.sendSystemMessage(Component.literal("§6§l★ ACHIEVEMENT UNLOCKED ★"))
        player.sendSystemMessage(Component.literal("§e${achievement.icon} ${achievement.title}"))
        player.sendSystemMessage(Component.literal("§7${achievement.description}"))
        player.sendSystemMessage(Component.literal(""))
        
        player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 1.0f, 1.0f)
        
        // Broadcast to game
        val game = PropHuntManager.getPlayerGame(player)
        game?.broadcast("§6★ §e${player.name.string}§7 unlocked: §e${achievement.title}§6 ★")
    }
    
    /**
     * Checks for achievement conditions at round end.
     */
    fun checkRoundEndAchievements(player: ServerPlayer, playerData: PropHuntPlayerData, game: PropHuntGame) {
        // Master Hider - survived entire round as prop
        if (playerData.team == PropHuntTeam.PROP && playerData.isAlive) {
            award(player, Achievement.MASTER_HIDER)
        }
        
        // Perfect Hunter
        if (playerData.team == PropHuntTeam.HUNTER && 
            playerData.propsFound > 0 && playerData.falseHits == 0) {
            award(player, Achievement.PERFECT_HUNTER)
        }
        
        // Sharp Eyes - found 5+ props
        if (playerData.propsFound >= 5) {
            award(player, Achievement.SHARP_EYES)
        }
    }
}
