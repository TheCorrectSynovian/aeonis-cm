package com.qc.aeonis.command

import com.mojang.brigadier.CommandDispatcher
import net.minecraft.world.entity.ai.goal.Goal
import net.minecraft.world.level.block.Blocks
import java.util.EnumSet
import net.minecraft.core.BlockPos
import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import net.minecraft.commands.arguments.coordinates.Vec3Argument
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.PathfinderMob
import com.mojang.brigadier.context.CommandContext
import com.qc.aeonis.config.AeonisFeatures
import com.qc.aeonis.network.AeonisNetworking
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.commands.arguments.ResourceArgument
import net.minecraft.core.registries.Registries
import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LightningBolt
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.animal.IronGolem
import net.minecraft.world.entity.animal.wolf.Wolf
import net.minecraft.world.entity.boss.enderdragon.EnderDragon
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.entity.monster.Vex
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.GameType
import net.minecraft.world.Difficulty
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.world.entity.monster.Zombie
import net.minecraft.world.entity.monster.Skeleton
import net.minecraft.world.entity.monster.Creeper
import net.minecraft.world.entity.monster.Slime
import net.minecraft.world.entity.animal.horse.Horse
import net.minecraft.world.entity.animal.Pig
import net.minecraft.world.entity.animal.axolotl.Axolotl
import net.minecraft.world.entity.animal.Parrot
import net.minecraft.world.entity.animal.Dolphin
import net.minecraft.world.entity.animal.goat.Goat
import net.minecraft.world.entity.animal.frog.Frog
import net.minecraft.world.item.DyeColor
import net.minecraft.nbt.CompoundTag

object AeonisCommands {
    
    // Track original gamemode for untransform
    private val originalGameModes = mutableMapOf<java.util.UUID, GameType>()
    private val transformedEntities = mutableMapOf<java.util.UUID, Entity>()
    
    // Track pet vex ownership: vex entity ID -> owner player UUID
    internal val petVexOwners = mutableMapOf<Int, java.util.UUID>()
    
    // Director Mode: Active Orders
    private val actorWalkTargets = mutableMapOf<Int, Vec3>()
    private val actorLookTargets = mutableMapOf<Int, Int>() // EntityID -> TargetEntityID
    private val actorAttackTargets = mutableMapOf<Int, Int>() // EntityID -> TargetEntityID
    
    /**
     * Called every tick to enforce Director Mode orders
     */
    fun tickActors(server: net.minecraft.server.MinecraftServer) {
        // 1. Handle Walking
        val walkIterator = actorWalkTargets.iterator()
        while (walkIterator.hasNext()) {
            val entry = walkIterator.next()
            val entityId = entry.key
            val targetPos = entry.value
            
            var found = false
            for (level in server.allLevels) {
                val entity = level.getEntity(entityId)
                if (entity is PathfinderMob && entity.isAlive) {
                    found = true
                    
                    // Check if arrived (within 1.5 blocks)
                    if (entity.position().distanceToSqr(targetPos) < 2.25) {
                        walkIterator.remove() // Arrived
                        entity.navigation.stop()
                    } else {
                        // Re-issue move command if not moving or every 20 ticks (approx)
                        if (entity.tickCount % 10 == 0 || entity.navigation.isDone) {
                            entity.navigation.moveTo(targetPos.x, targetPos.y, targetPos.z, 1.2)
                        }
                    }
                    break
                }
            }
            if (!found) {
                walkIterator.remove() // Entity dead or gone
            }
        }

        // 2. Handle Looking
        val lookIterator = actorLookTargets.iterator()
        while (lookIterator.hasNext()) {
            val entry = lookIterator.next()
            val entityId = entry.key
            val targetId = entry.value
            
            var found = false
            for (level in server.allLevels) {
                val entity = level.getEntity(entityId)
                if (entity is Mob && entity.isAlive) {
                    found = true
                    val target = level.getEntity(targetId)
                    if (target != null && target.isAlive) {
                        entity.lookControl.setLookAt(target, 30.0f, 30.0f)
                    } else {
                        lookIterator.remove() // Target gone
                    }
                    break
                }
            }
            if (!found) {
                lookIterator.remove() // Entity gone
            }
        }

        // 3. Handle Attacking
        val attackIterator = actorAttackTargets.iterator()
        while (attackIterator.hasNext()) {
            val entry = attackIterator.next()
            val entityId = entry.key
            val targetId = entry.value
            
            var found = false
            for (level in server.allLevels) {
                val entity = level.getEntity(entityId)
                if (entity is Mob && entity.isAlive) {
                    found = true
                    val target = level.getEntity(targetId) as? LivingEntity
                    if (target != null && target.isAlive) {
                        if (entity.target != target) {
                            entity.target = target
                        }
                    } else {
                        attackIterator.remove() // Target gone
                    }
                    break
                }
            }
            if (!found) {
                attackIterator.remove() // Entity gone
            }
        }
    }

    /**
     * Called every tick to redirect pet vex targets away from their owners
     */
    fun tickPetVexes(server: net.minecraft.server.MinecraftServer) {
        val toRemove = mutableListOf<Int>()
        
        for ((vexId, ownerUUID) in petVexOwners) {
            var found = false
            for (level in server.allLevels) {
                val entity = level.getEntity(vexId)
                if (entity is Vex && entity.isAlive) {
                    found = true
                    val owner = server.playerList.getPlayer(ownerUUID)
                    
                    // If vex is targeting owner, redirect to a hostile mob
                    if (entity.target == owner || entity.target is Player) {
                        val nearbyHostile = level.getEntitiesOfClass(
                            Monster::class.java,
                            entity.boundingBox.inflate(24.0)
                        ) { it !is Vex && it.isAlive }.minByOrNull { it.distanceToSqr(entity) }
                        
                        entity.setTarget(nearbyHostile)
                    }
                    break
                }
            }
            if (!found) {
                toRemove.add(vexId)
            }
        }
        
        // Clean up dead vexes
        toRemove.forEach { petVexOwners.remove(it) }
    }
    
    fun register() {
        CommandRegistrationCallback.EVENT.register { dispatcher, registryAccess, _ ->
            registerCommands(dispatcher)
            registerTransformCommand(dispatcher, registryAccess)
            registerAiCommand(dispatcher)
            registerPrankCommand(dispatcher)
            registerAbilityCommand(dispatcher)
            registerGameCommand(dispatcher)
        }
    }
    
    private fun registerTransformCommand(
        dispatcher: CommandDispatcher<CommandSourceStack>,
        registryAccess: net.minecraft.commands.CommandBuildContext
    ) {
        // /transform <entity> [variants...]
        dispatcher.register(
            Commands.literal("transform")
                .requires { it.hasPermission(2) }
                .then(Commands.argument("entity", ResourceArgument.resource(registryAccess, Registries.ENTITY_TYPE))
                    .executes { transformIntoEntity(it, emptyList()) }
                    .then(Commands.argument("variant", StringArgumentType.word())
                        .executes { 
                            val variants = listOf(StringArgumentType.getString(it, "variant"))
                            transformIntoEntity(it, variants)
                        }
                        .then(Commands.argument("variant2", StringArgumentType.word())
                            .executes { 
                                val variants = listOf(
                                    StringArgumentType.getString(it, "variant"),
                                    StringArgumentType.getString(it, "variant2")
                                )
                                transformIntoEntity(it, variants)
                            }
                        )
                    )
                )
        )
        
        // /untransform - Exit spectator and kill the entity
        dispatcher.register(
            Commands.literal("untransform")
                .requires { it.hasPermission(2) }
                .executes { untransform(it) }
        )
    }



    private fun actorWalkTo(ctx: CommandContext<CommandSourceStack>, speed: Double): Int {
        val targets = EntityArgument.getEntities(ctx, "targets")
        val pos = Vec3Argument.getVec3(ctx, "pos")
        var count = 0
        
        for (entity in targets) {
            if (entity is PathfinderMob) {
                // Clear other orders
                actorLookTargets.remove(entity.id)
                actorAttackTargets.remove(entity.id)
                
                // Set new order
                actorWalkTargets[entity.id] = pos
                
                // Initial move
                entity.navigation.moveTo(pos.x, pos.y, pos.z, speed)
                count++
            }
        }
        
        ctx.source.sendSuccess({ Component.literal("§aOrdered $count entities to walk to ${pos.x}, ${pos.y}, ${pos.z}") }, true)
        return count
    }

    private fun actorLookAt(ctx: CommandContext<CommandSourceStack>): Int {
        val targets = EntityArgument.getEntities(ctx, "targets")
        val target = EntityArgument.getEntity(ctx, "target")
        var count = 0
        
        for (entity in targets) {
            if (entity is Mob) {
                // Clear other orders
                actorWalkTargets.remove(entity.id)
                
                // Set new order
                actorLookTargets[entity.id] = target.id
                
                // Initial look
                entity.lookControl.setLookAt(target, 30.0f, 30.0f)
                count++
            }
        }
        
        ctx.source.sendSuccess({ Component.literal("§aOrdered $count entities to look at ${target.name.string}") }, true)
        return count
    }

    private fun actorAttack(ctx: CommandContext<CommandSourceStack>): Int {
        val targets = EntityArgument.getEntities(ctx, "targets")
        val target = EntityArgument.getEntity(ctx, "target")
        var count = 0
        
        if (target !is LivingEntity) {
            ctx.source.sendFailure(Component.literal("§cTarget must be a living entity!"))
            return 0
        }

        for (entity in targets) {
            if (entity is Mob) {
                // Clear other orders
                actorWalkTargets.remove(entity.id)
                
                // Set new order
                actorAttackTargets[entity.id] = target.id
                
                // Initial attack
                entity.target = target
                count++
            }
        }
        
        ctx.source.sendSuccess({ Component.literal("§aOrdered $count entities to attack ${target.name.string}") }, true)
        return count
    }

    private fun actorStop(ctx: CommandContext<CommandSourceStack>): Int {
        val targets = EntityArgument.getEntities(ctx, "targets")
        var count = 0
        
        for (entity in targets) {
            if (entity is Mob) {
                // Clear all orders
                actorWalkTargets.remove(entity.id)
                actorLookTargets.remove(entity.id)
                actorAttackTargets.remove(entity.id)
                
                entity.navigation.stop()
                entity.target = null
                count++
            }
        }
        
        ctx.source.sendSuccess({ Component.literal("§aStopped $count entities") }, true)
        return count
    }

    private fun actorIgnite(ctx: CommandContext<CommandSourceStack>): Int {
        val targets = EntityArgument.getEntities(ctx, "targets")
        var count = 0
        
        for (entity in targets) {
            if (entity is Creeper) {
                entity.ignite()
                count++
            }
        }
        
        if (count == 0) {
             ctx.source.sendFailure(Component.literal("§cNo creepers found in selection!"))
             return 0
        }
        
        ctx.source.sendSuccess({ Component.literal("§aIgnited $count creepers") }, true)
        return count
    }

    private fun registerAiCommand(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("ai")
                .requires { it.hasPermission(2) }
                .then(Commands.literal("chaotic")
                    .then(Commands.argument("targets", EntityArgument.entities())
                        .executes { makeChaotic(it) }
                    )
                )
                .then(Commands.literal("director")
                    .then(Commands.argument("targets", EntityArgument.entities())
                        .then(Commands.literal("walk_to")
                            .then(Commands.argument("pos", Vec3Argument.vec3())
                                .executes { actorWalkTo(it, 1.0) }
                                .then(Commands.argument("speed", DoubleArgumentType.doubleArg(0.1, 5.0))
                                    .executes { actorWalkTo(it, DoubleArgumentType.getDouble(it, "speed")) }
                                )
                            )
                        )
                        .then(Commands.literal("look_at")
                            .then(Commands.argument("target", EntityArgument.entity())
                                .executes { actorLookAt(it) }
                            )
                        )
                        .then(Commands.literal("attack")
                            .then(Commands.argument("target", EntityArgument.entity())
                                .executes { actorAttack(it) }
                            )
                        )
                        .then(Commands.literal("stop")
                            .executes { actorStop(it) }
                        )
                        .then(Commands.literal("ignite")
                            .executes { actorIgnite(it) }
                        )
                    )
                )
        )
    }
    
    private fun registerCommands(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("aeonis")
                .then(Commands.literal("help").executes { showHelp(it) })
                .then(Commands.literal("features")
                    .then(Commands.literal("extra_mobs")
                        .requires { it.hasPermission(2) }
                        .executes { showExtraMobsStatus(it) }
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                            .executes { setExtraMobs(it) })))
                .then(Commands.literal("sys")
                    .then(Commands.literal("ping")
                        .requires { it.hasPermission(2) }
                        .executes { systemPing(it) })
                    .then(Commands.literal("story")
                        .requires { it.hasPermission(2) }
                        .executes { tellStory(it) }))
        )
    }

    private fun registerPrankCommand(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("prank")
                .requires { it.hasPermission(2) }
                .then(Commands.literal("smite")
                    .then(Commands.argument("targets", EntityArgument.players())
                        .executes { smitePlayer(it) }))
                .then(Commands.literal("yeet")
                    .then(Commands.argument("targets", EntityArgument.players())
                        .executes { yeetPlayer(it) }))
                .then(Commands.literal("disco")
                    .then(Commands.argument("targets", EntityArgument.players())
                        .executes { discoMode(it) }))
                .then(Commands.literal("supersize")
                    .then(Commands.argument("targets", EntityArgument.players())
                        .executes { supersizePlayer(it) }))
                .then(Commands.literal("smol")
                    .then(Commands.argument("targets", EntityArgument.players())
                        .executes { shrinkPlayer(it) }))
                .then(Commands.literal("chaos")
                    .then(Commands.argument("targets", EntityArgument.players())
                        .executes { chaosMode(it) }))
                .then(Commands.literal("rocket")
                    .then(Commands.argument("targets", EntityArgument.players())
                        .executes { rocketPlayer(it) }))
                .then(Commands.literal("spin")
                    .then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.argument("times", IntegerArgumentType.integer(1, 50))
                            .executes { spinPlayer(it) })))
                .then(Commands.literal("freeze")
                    .then(Commands.argument("targets", EntityArgument.players())
                        .executes { freezePlayer(it) }))
                .then(Commands.literal("burn")
                    .then(Commands.argument("targets", EntityArgument.players())
                        .executes { burnPlayer(it) }))
                .then(Commands.literal("drunk")
                    .then(Commands.argument("targets", EntityArgument.players())
                        .executes { drunkVision(it) }))
        )
    }

    private fun registerAbilityCommand(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("ability")
                .requires { it.hasPermission(2) }
                .then(Commands.literal("mimic")
                    .then(Commands.literal("zombie").executes { mimicSound(it, "zombie") })
                    .then(Commands.literal("wither").executes { mimicSound(it, "wither") })
                    .then(Commands.literal("ghast").executes { mimicSound(it, "ghast") })
                    .then(Commands.literal("dragon").executes { mimicSound(it, "dragon") }))
                .then(Commands.literal("dash")
                    .executes { dash(it) })
                .then(Commands.literal("blink")
                    .then(Commands.argument("range", IntegerArgumentType.integer(1, 25))
                        .executes { blink(it) }))
                .then(Commands.literal("jump")
                    .executes { moonJump(it) })
                .then(Commands.literal("roar")
                    .executes { wardenRoar(it) })
                .then(Commands.literal("darkness")
                    .executes { darknessPulse(it) })
                .then(Commands.literal("summon")
                    .then(Commands.literal("vex").executes { petVex(it) })
                    .then(Commands.literal("wolves")
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 5))
                            .executes { spiritWolves(it) })))
        )
    }

    private fun registerGameCommand(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("game")
                .requires { it.hasPermission(2) }
                .then(Commands.literal("ambush")
                    .executes { ambushMode(it) })
                .then(Commands.literal("scan")
                    .executes { detectHostiles(it) })
                .then(Commands.literal("thunder")
                    .executes { thunderDome(it) })
                .then(Commands.literal("copper")
                    .executes { copperDrop(it) })
                .then(Commands.literal("time")
                    .then(Commands.argument("ticks", IntegerArgumentType.integer(1, 24000))
                        .executes { timeWarp(it) }))
                .then(Commands.literal("cleanse")
                    .then(Commands.argument("targets", EntityArgument.players())
                        .executes { clearEffects(it) }))
                .then(Commands.literal("crit_save")
                    .executes { critSave(it) })
                .then(Commands.literal("pro_gamer")
                    .executes { proGamerMode(it) })
                .then(Commands.literal("exitbody")
                    .then(Commands.literal("s").executes { exitBody(it, GameType.SURVIVAL) })
                    .then(Commands.literal("c").executes { exitBody(it, GameType.CREATIVE) })
                )
        )
    }
    
    private fun showHelp(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        source.sendSuccess({ Component.literal("§6=== Aeonis Plus Commands ===") }, false)

        // Main Features
        source.sendSuccess({ Component.literal("§2=== Main Features ===") }, false)
        source.sendSuccess({ Component.literal("§a/aeonis features extra_mobs <true/false>§7 - Toggle Aeonis mobs") }, false)
        source.sendSuccess({ Component.literal("§a/transform <entity> [variant...]§7 - Become any mob! Optionally specify variants.") }, false)
        source.sendSuccess({ Component.literal("§a/untransform§7 - Return to normal") }, false)

        // AI Tools
        source.sendSuccess({ Component.literal("§5=== AI Tools (/ai) ===") }, false)
        source.sendSuccess({ Component.literal("§d/ai chaotic <entities>§7 - Enable chaotic AI on entities") }, false)
        source.sendSuccess({ Component.literal("§d/ai director <entities> walk_to <x y z> [speed]§7 - Walk entities to position") }, false)
        source.sendSuccess({ Component.literal("§d/ai director <entities> look_at <target>§7 - Make entities look at a target") }, false)
        source.sendSuccess({ Component.literal("§d/ai director <entities> attack <target>§7 - Make entities attack a target") }, false)
        source.sendSuccess({ Component.literal("§d/ai director <entities> stop§7 - Stop all orders for entities") }, false)

        // Pranks
        source.sendSuccess({ Component.literal("§e=== Pranks (/prank) ===") }, false)
        source.sendSuccess({ Component.literal("§e/prank smite <players>§7 - Strike with lightning") }, false)
        source.sendSuccess({ Component.literal("§e/prank yeet <players>§7 - Launch into air") }, false)
        source.sendSuccess({ Component.literal("§e/prank disco <players>§7 - Disco mode") }, false)
        source.sendSuccess({ Component.literal("§e/prank supersize <players>§7 - Make huge") }, false)
        source.sendSuccess({ Component.literal("§e/prank smol <players>§7 - Make tiny") }, false)
        source.sendSuccess({ Component.literal("§e/prank chaos <players>§7 - Chaotic effects") }, false)
        source.sendSuccess({ Component.literal("§e/prank rocket <players>§7 - Rocket launch") }, false)
        source.sendSuccess({ Component.literal("§e/prank spin <players> <times>§7 - Spin players") }, false)
        source.sendSuccess({ Component.literal("§e/prank freeze <players>§7 - Freeze in place") }, false)
        source.sendSuccess({ Component.literal("§e/prank burn <players>§7 - Set on fire") }, false)
        source.sendSuccess({ Component.literal("§e/prank drunk <players>§7 - Drunk vision") }, false)

        // Abilities
        source.sendSuccess({ Component.literal("§b=== Abilities (/ability) ===") }, false)
        source.sendSuccess({ Component.literal("§b/ability mimic <zombie|wither|ghast|dragon>§7 - Mimic mob sounds") }, false)
        source.sendSuccess({ Component.literal("§b/ability dash§7 - Quick dash forward") }, false)
        source.sendSuccess({ Component.literal("§b/ability blink <range>§7 - Teleport a short distance") }, false)
        source.sendSuccess({ Component.literal("§b/ability jump§7 - Moon jump") }, false)
        source.sendSuccess({ Component.literal("§b/ability roar§7 - Warden roar") }, false)
        source.sendSuccess({ Component.literal("§b/ability darkness§7 - Darkness pulse") }, false)
        source.sendSuccess({ Component.literal("§b/ability summon vex§7 - Summon pet vex") }, false)
        source.sendSuccess({ Component.literal("§b/ability summon wolves <count>§7 - Summon spirit wolves") }, false)

        // Game Tools
        source.sendSuccess({ Component.literal("§c=== Game Tools (/game) ===") }, false)
        source.sendSuccess({ Component.literal("§c/game ambush§7 - Surprise mob ambush") }, false)
        source.sendSuccess({ Component.literal("§c/game scan§7 - Scan for hostiles nearby") }, false)
        source.sendSuccess({ Component.literal("§c/game thunder§7 - Start thunder dome event") }, false)
        source.sendSuccess({ Component.literal("§c/game copper§7 - Drop copper on players") }, false)
        source.sendSuccess({ Component.literal("§c/game time <ticks>§7 - Warp time forward") }, false)
        source.sendSuccess({ Component.literal("§c/game cleanse <players>§7 - Remove all effects") }, false)
        source.sendSuccess({ Component.literal("§c/game crit_save§7 - Critical save (revive)") }, false)
        source.sendSuccess({ Component.literal("§c/game pro_gamer§7 - Pro gamer mode") }, false)
        source.sendSuccess({ Component.literal("§c/game exitbody <s|c>§7 - Exit body (Survival/Creative)") }, false)

        // System
        source.sendSuccess({ Component.literal("§7=== System (/aeonis sys) ===") }, false)
        source.sendSuccess({ Component.literal("§7/aeonis sys ping§7 - Show server/mod stats") }, false)
        source.sendSuccess({ Component.literal("§7/aeonis sys story§7 - Aeonis story info") }, false)
        return 1
    }
    
    // ========== FEATURES ==========
    
    private fun showExtraMobsStatus(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val server = source.server
        val enabled = AeonisFeatures.isExtraMobsEnabled(server)
        val status = if (enabled) "§aENABLED" else "§cDISABLED"
        source.sendSuccess({ Component.literal("§6Aeonis Extra Mobs: $status") }, false)
        return 1
    }
    
    private fun setExtraMobs(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val server = source.server
        val enabled = BoolArgumentType.getBool(ctx, "enabled")
        
        AeonisFeatures.setExtraMobsEnabled(server, enabled)
        
        if (enabled) {
            source.sendSuccess({ Component.literal("§a§l✓ Aeonis Extra Mobs ENABLED!") }, true)
            source.sendSuccess({ Component.literal("§7Stalkers will now spawn at night in the Overworld.") }, false)
        } else {
            source.sendSuccess({ Component.literal("§c§l✗ Aeonis Extra Mobs DISABLED!") }, true)
            source.sendSuccess({ Component.literal("§7Stalkers will no longer spawn naturally.") }, false)
        }
        
        return 1
    }
    
    // ========== ENTITY TRANSFORM ==========
    
    @Suppress("UNCHECKED_CAST")
    private fun transformIntoEntity(ctx: CommandContext<CommandSourceStack>, variants: List<String> = emptyList()): Int {
        val source = ctx.source
        val player = source.player as? ServerPlayer ?: run {
            source.sendFailure(Component.literal("§cOnly players can transform!"))
            return 0
        }
        val level = player.level() as net.minecraft.server.level.ServerLevel
        
        // Get entity type from argument
        val entityTypeRef = ResourceArgument.getEntityType(ctx, "entity")
        val entityType = entityTypeRef.value() as? EntityType<Entity> ?: run {
            source.sendFailure(Component.literal("§cInvalid entity type!"))
            return 0
        }
        
        // Check if already transformed
        if (transformedEntities.containsKey(player.uuid)) {
            source.sendFailure(Component.literal("§cYou're already transformed! Use /untransform first."))
            return 0
        }
        
        // Spawn the entity at player's location
        val entity = entityType.spawn(
            level,
            player.blockPosition(),
            EntitySpawnReason.COMMAND
        )
        
        if (entity == null) {
            source.sendFailure(Component.literal("§cFailed to spawn entity!"))
            return 0
        }
        
        // Apply variants
        var variantInfo = ""
        if (variants.isNotEmpty()) {
            variantInfo = applyVariants(entity, variants)
            if (variantInfo.isNotEmpty()) {
                variantInfo = " §7(§e$variantInfo§7)"
            }
        }
        
        // Store original gamemode and entity reference
        originalGameModes[player.uuid] = player.gameMode.gameModeForPlayer
        transformedEntities[player.uuid] = entity
        
        // Disable the mob's AI so it doesn't interfere with player control
        // EXCEPTION: Ender Dragon needs AI to update its multi-part body and animations!
        if (entity is Mob && entity !is EnderDragon) {
            entity.setNoAi(true)
        }
        
        // Special handling for Ender Dragon - set to hover phase
        if (entity is EnderDragon) {
            entity.phaseManager.setPhase(EnderDragonPhase.HOVERING)
            // Ensure AI is enabled for Dragon
            entity.setNoAi(false)
        }
        
        // Register entity for control
        AeonisNetworking.setControlledEntity(player, entity.id)
        
        // Set player to spectator mode
        player.setGameMode(GameType.SPECTATOR)
        
        // Make player spectate the entity
        player.setCamera(entity)
        
        val entityName = entityType.description.string
        source.sendSuccess({ 
            Component.literal("§a✨ You transformed into a §b$entityName$variantInfo§a! Use WASD to move, SPACE to jump. §e/untransform§a to return.") 
        }, true)
        return 1
    }
    
    // NEW: Helper function to apply variants to entities
    private fun applyVariants(entity: Entity, variants: List<String>): String {
        val appliedVariants = mutableListOf<String>()
        val level = entity.level() as net.minecraft.server.level.ServerLevel
        
        // Zombie variants
        if (entity is Zombie) {
            for (variant in variants) {
                when (variant.lowercase()) {
                    "baby" -> {
                        entity.isBaby = true
                        appliedVariants.add("Baby")
                    }
                    "drowned" -> {
                        convertEntity(entity, EntityType.DROWNED)
                        appliedVariants.add("Drowned")
                    }
                    "husk" -> {
                        convertEntity(entity, EntityType.HUSK)
                        appliedVariants.add("Husk")
                    }
                    "chicken_jockey" -> {
                        entity.isBaby = true
                        val chicken = EntityType.CHICKEN.spawn(
                            level,
                            entity.blockPosition(),
                            EntitySpawnReason.COMMAND
                        )
                        if (chicken != null) {
                            entity.startRiding(chicken)
                            appliedVariants.add("Chicken Jockey")
                        }
                    }
                }
            }
        }
        
        // Skeleton variants
        if (entity is Skeleton) {
            for (variant in variants) {
                when (variant.lowercase()) {
                    "wither" -> {
                        convertEntity(entity, EntityType.WITHER_SKELETON)
                        appliedVariants.add("Wither")
                    }
                    "stray" -> {
                        convertEntity(entity, EntityType.STRAY)
                        appliedVariants.add("Stray")
                    }
                    "charged" -> {
                        entity.addEffect(MobEffectInstance(MobEffects.GLOWING, Int.MAX_VALUE, 0))
                        appliedVariants.add("Charged (Visual)")
                    }
                }
            }
        }
        
        // Creeper variants
        if (entity is Creeper) {
            for (variant in variants) {
                when (variant.lowercase()) {
                    "powered", "charged" -> {
                        // NBT access issues, skipping for now
                        entity.addEffect(MobEffectInstance(MobEffects.GLOWING, Int.MAX_VALUE, 0))
                        appliedVariants.add("Charged (Visual)")
                    }
                }
            }
        }
        
        // Slime variants
        if (entity is Slime) {
            for (variant in variants) {
                when (variant.lowercase()) {
                    "big" -> {
                        entity.setSize(4, true)
                        appliedVariants.add("Big")
                    }
                    "tiny", "small" -> {
                        entity.setSize(1, true)
                        appliedVariants.add("Tiny")
                    }
                }
            }
        }
        
        // Horse variants
        if (entity is Horse) {
            for (variant in variants) {
                when (variant.lowercase()) {
                    "baby" -> {
                        entity.isBaby = true
                        appliedVariants.add("Baby")
                    }
                    "saddle" -> {
                        entity.equipItemIfPossible(level, ItemStack(Items.SADDLE))
                        appliedVariants.add("Saddled")
                    }
                    "armor" -> {
                        entity.equipItemIfPossible(level, ItemStack(Items.DIAMOND_HORSE_ARMOR))
                        appliedVariants.add("Armored")
                    }
                }
            }
        }
        
        /* Sheep variants - Temporarily disabled due to resolution issues
        if (entity is net.minecraft.world.entity.animal.Sheep) {
            for (variant in variants) {
                val color = DyeColor.entries.firstOrNull { it.name.lowercase() == variant.lowercase() }
                if (color != null) {
                    entity.color = color
                    appliedVariants.add(color.toString())
                }
            }
        }
        */
        
        // Pig variants
        if (entity is Pig) {
            for (variant in variants) {
                when (variant.lowercase()) {
                    "baby" -> {
                        entity.isBaby = true
                        appliedVariants.add("Baby")
                    }
                    "saddle" -> {
                        entity.equipItemIfPossible(level, ItemStack(Items.SADDLE))
                        appliedVariants.add("Saddled")
                    }
                }
            }
        }
        
        // Axolotl variants - Disabled due to NBT issues
        /*
        if (entity is Axolotl) {
            // ...
        }
        */
        
        // Wolf variants - Disabled due to NBT issues
        /*
        if (entity is Wolf) {
            // ...
        }
        */
        
        // Parrot variants - Disabled due to NBT issues
        /*
        if (entity is Parrot) {
            // ...
        }
        */
        
        // Goat variants
        if (entity is Goat) {
            for (variant in variants) {
                when (variant.lowercase()) {
                    "baby" -> {
                        entity.isBaby = true
                        appliedVariants.add("Baby")
                    }
                    "screaming" -> {
                        entity.isScreamingGoat = true
                        appliedVariants.add("Screaming")
                    }
                }
            }
        }
        
        // Frog variants - Disabled due to NBT issues
        /*
        if (entity is Frog) {
            // ...
        }
        */
        
        return appliedVariants.joinToString(", ")
    }

    private fun <T : Mob> convertEntity(oldEntity: Mob, newType: EntityType<T>): T? {
        val level = oldEntity.level() as net.minecraft.server.level.ServerLevel
        val newEntity = newType.create(level, EntitySpawnReason.COMMAND) ?: return null
        
        newEntity.copyPosition(oldEntity)
        newEntity.yBodyRot = oldEntity.yBodyRot
        newEntity.yHeadRot = oldEntity.yHeadRot
        
        level.addFreshEntity(newEntity)
        oldEntity.discard()
        
        // Update our tracking maps
        val ownerUUID = transformedEntities.entries.find { it.value == oldEntity }?.key
        if (ownerUUID != null) {
            transformedEntities[ownerUUID] = newEntity
            val player = level.server.playerList.getPlayer(ownerUUID)
            if (player != null) {
                player.setCamera(newEntity)
                AeonisNetworking.setControlledEntity(player, newEntity.id)
                newEntity.setNoAi(true)
            }
        }
        
        return newEntity
    }


    private fun untransform(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val player = source.player as? ServerPlayer ?: run {
            source.sendFailure(Component.literal("§cOnly players can untransform!"))
            return 0
        }
        
        // Check if transformed
        val entity = transformedEntities.remove(player.uuid)
        if (entity == null) {
            source.sendFailure(Component.literal("§cYou're not transformed!"))
            return 0
        }
        
        // Stop controlling
        AeonisNetworking.removeControlledEntity(player)
        
        // Stop spectating
        player.setCamera(player)
        
        // Restore original gamemode
        val originalMode = originalGameModes.remove(player.uuid) ?: GameType.SURVIVAL
        player.setGameMode(originalMode)
        
        // Teleport player to entity's last position before killing it
        player.teleportTo(entity.x, entity.y, entity.z)
        
        // Kill the spawned entity
        entity.discard()
        
        source.sendSuccess({ 
            Component.literal("§a✨ You returned to your normal form!") 
        }, true)
        return 1
    }
    
    private fun smitePlayer(ctx: CommandContext<CommandSourceStack>): Int {
        val targets = EntityArgument.getPlayers(ctx, "targets")
        var count = 0
        
        for (target in targets) {
            val level = target.level() as? net.minecraft.server.level.ServerLevel ?: continue
            
            val lightning = LightningBolt(EntityType.LIGHTNING_BOLT, level)
            lightning.setPos(target.x, target.y, target.z)
            lightning.setVisualOnly(false)
            level.addFreshEntity(lightning)
            count++
        }
        
        ctx.source.sendSuccess({ Component.literal("§c⚡ Smitten $count player(s)!") }, true)
        return count
    }
    
    private fun yeetPlayer(ctx: CommandContext<CommandSourceStack>): Int {
        val targets = EntityArgument.getPlayers(ctx, "targets")
        var count = 0
        
        for (target in targets) {
            target.setDeltaMovement(target.deltaMovement.add(0.0, 3.0, 0.0))
            target.hurtMarked = true
            
            val level = target.level() as? net.minecraft.server.level.ServerLevel
            level?.playSound(null, target.x, target.y, target.z, 
                SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 1.0f, 1.0f)
            count++
        }
        
        ctx.source.sendSuccess({ Component.literal("§b🚀 YEETED $count player(s)!") }, true)
        return count
    }
    
    private fun discoMode(ctx: CommandContext<CommandSourceStack>): Int {
        val targets = EntityArgument.getPlayers(ctx, "targets")
        var count = 0
        
        for (target in targets) {
            val level = target.level() as? net.minecraft.server.level.ServerLevel ?: continue
            
            // Give glowing, speed, and jump boost
            target.addEffect(MobEffectInstance(MobEffects.GLOWING, 200, 0))
            target.addEffect(MobEffectInstance(MobEffects.SPEED, 200, 2))
            target.addEffect(MobEffectInstance(MobEffects.JUMP_BOOST, 200, 1))
            
            // Spawn colorful particles
            repeat(50) {
                level.sendParticles(ParticleTypes.NOTE,
                    target.x + (Math.random() - 0.5) * 2,
                    target.y + Math.random() * 2,
                    target.z + (Math.random() - 0.5) * 2,
                    1, 0.0, 0.0, 0.0, 0.0)
            }
            
            level.playSound(null, target.x, target.y, target.z,
                SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 1.0f, 1.0f)
            count++
        }
        
        ctx.source.sendSuccess({ Component.literal("§d🎵 DISCO MODE for $count player(s)!") }, true)
        return count
    }
    
    private fun supersizePlayer(ctx: CommandContext<CommandSourceStack>): Int {
        val targets = EntityArgument.getPlayers(ctx, "targets")
        var count = 0
        
        for (target in targets) {
            // Slowness + resistance + strength to simulate being big
            target.addEffect(MobEffectInstance(MobEffects.SLOWNESS, 400, 1))
            target.addEffect(MobEffectInstance(MobEffects.RESISTANCE, 400, 1))
            target.addEffect(MobEffectInstance(MobEffects.STRENGTH, 400, 2))
            target.addEffect(MobEffectInstance(MobEffects.HEALTH_BOOST, 400, 4))
            count++
        }
        
        ctx.source.sendSuccess({ Component.literal("§a🦖 SUPERSIZED $count player(s)!") }, true)
        return count
    }
    
    private fun shrinkPlayer(ctx: CommandContext<CommandSourceStack>): Int {
        val targets = EntityArgument.getPlayers(ctx, "targets")
        var count = 0
        
        for (target in targets) {
            // Speed + weakness + invisibility to simulate being small
            target.addEffect(MobEffectInstance(MobEffects.SPEED, 400, 2))
            target.addEffect(MobEffectInstance(MobEffects.WEAKNESS, 400, 1))
            target.addEffect(MobEffectInstance(MobEffects.INVISIBILITY, 400, 0))
            target.addEffect(MobEffectInstance(MobEffects.JUMP_BOOST, 400, 2))
            count++
        }
        
        ctx.source.sendSuccess({ Component.literal("§7🐜 Shrunk $count player(s)!") }, true)
        return count
    }
    
    private fun chaosMode(ctx: CommandContext<CommandSourceStack>): Int {
        val targets = EntityArgument.getPlayers(ctx, "targets")
        var count = 0
        
        for (target in targets) {
            val random = target.random
            
            val effects = listOf(
                MobEffects.LEVITATION,
                MobEffects.BLINDNESS,
                MobEffects.NAUSEA,
                MobEffects.SPEED,
                MobEffects.SLOWNESS,
                MobEffects.JUMP_BOOST,
                MobEffects.GLOWING,
                MobEffects.NIGHT_VISION,
                MobEffects.POISON,
                MobEffects.REGENERATION
            )
            
            repeat(3) {
                val effect = effects[random.nextInt(effects.size)]
                val amplifier = random.nextInt(3)
                target.addEffect(MobEffectInstance(effect, 200, amplifier))
            }
            count++
        }
        
        ctx.source.sendSuccess({ Component.literal("§c🎲 CHAOS unleashed on $count player(s)!") }, true)
        return count
    }
    
    private fun rocketPlayer(ctx: CommandContext<CommandSourceStack>): Int {
        val targets = EntityArgument.getPlayers(ctx, "targets")
        var count = 0
        
        for (target in targets) {
            val level = target.level() as? net.minecraft.server.level.ServerLevel ?: continue
            
            // Launch high with slow falling
            target.setDeltaMovement(Vec3(0.0, 5.0, 0.0))
            target.hurtMarked = true
            target.addEffect(MobEffectInstance(MobEffects.SLOW_FALLING, 200, 0))
            
            // Particles and sound
            level.sendParticles(ParticleTypes.FLAME, target.x, target.y, target.z, 30, 0.3, 0.1, 0.3, 0.1)
            level.playSound(null, target.x, target.y, target.z,
                SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 1.0f, 0.5f)
            count++
        }
        
        ctx.source.sendSuccess({ Component.literal("§6🚀 LAUNCHED $count player(s)!") }, true)
        return count
    }
    
    private fun spinPlayer(ctx: CommandContext<CommandSourceStack>): Int {
        val targets = EntityArgument.getPlayers(ctx, "targets")
        val times = IntegerArgumentType.getInteger(ctx, "times")
        var count = 0
        
        for (target in targets) {
            // Apply nausea for spinning effect
            target.addEffect(MobEffectInstance(MobEffects.NAUSEA, times * 20, 0))
            
            // Rotate player rapidly
            val newYaw = target.yRot + (360f * times)
            target.setYRot(newYaw)
            count++
        }
        
        ctx.source.sendSuccess({ Component.literal("§e🌀 Spinning $count player(s) ${times} times!") }, true)
        return count
    }
    
    private fun freezePlayer(ctx: CommandContext<CommandSourceStack>): Int {
        val targets = EntityArgument.getPlayers(ctx, "targets")
        var count = 0
        
        for (target in targets) {
            val level = target.level() as? net.minecraft.server.level.ServerLevel ?: continue
            
            target.addEffect(MobEffectInstance(MobEffects.SLOWNESS, 100, 255))
            target.addEffect(MobEffectInstance(MobEffects.JUMP_BOOST, 100, 128))
            target.ticksFrozen = 300
            
            level.sendParticles(ParticleTypes.SNOWFLAKE, target.x, target.y + 1, target.z, 20, 0.5, 0.5, 0.5, 0.0)
            level.playSound(null, target.x, target.y, target.z,
                SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.0f, 1.5f)
            count++
        }
        
        ctx.source.sendSuccess({ Component.literal("§b❄ FROZE $count player(s)!") }, true)
        return count
    }
    
    private fun burnPlayer(ctx: CommandContext<CommandSourceStack>): Int {
        val targets = EntityArgument.getPlayers(ctx, "targets")
        var count = 0
        
        for (target in targets) {
            val level = target.level() as? net.minecraft.server.level.ServerLevel ?: continue
            
            target.remainingFireTicks = 100
            level.sendParticles(ParticleTypes.FLAME, target.x, target.y + 1, target.z, 30, 0.5, 0.5, 0.5, 0.05)
            count++
        }
        
        ctx.source.sendSuccess({ Component.literal("§c🔥 Set $count player(s) on FIRE!") }, true)
        return count
    }
    
    // ========== TRANSFORM ABILITIES ==========
    
    private fun wardenRoar(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val player = source.player ?: run {
            source.sendFailure(Component.literal("§cOnly players can use this!"))
            return 0
        }
        val level = player.level() as? net.minecraft.server.level.ServerLevel ?: return 0
        
        // Play warden roar sound
        level.playSound(
            null, player.x, player.y, player.z,
            SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS,
            3.0f, 0.8f
        )
        
        // Sonic boom particles in a ring
        val radius = 8.0
        for (i in 0 until 36) {
            val angle = Math.toRadians(i * 10.0)
            val px = player.x + Math.cos(angle) * radius
            val pz = player.z + Math.sin(angle) * radius
            level.sendParticles(
                ParticleTypes.SONIC_BOOM,
                px, player.y + 1.0, pz,
                1, 0.0, 0.0, 0.0, 0.0
            )
        }
        
        // Knockback all nearby players (within 10 blocks)
        val nearbyPlayers = level.getEntitiesOfClass(
            Player::class.java,
            AABB.ofSize(player.position(), 20.0, 10.0, 20.0)
        ) { it != player }
        
        for (target in nearbyPlayers) {
            val direction = target.position().subtract(player.position()).normalize()
            val knockback = direction.multiply(2.5, 1.0, 2.5).add(0.0, 0.8, 0.0)
            target.setDeltaMovement(knockback)
            target.hurtMarked = true
            
            // Apply brief weakness
            if (target is ServerPlayer) {
                target.addEffect(MobEffectInstance(MobEffects.WEAKNESS, 60, 1))
            }
        }
        
        val count = nearbyPlayers.size
        source.sendSuccess({ 
            Component.literal("§5🔊 You unleash a WARDEN ROAR! $count player(s) affected!") 
        }, true)
        return 1
    }
    
    private fun darknessPulse(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val player = source.player ?: run {
            source.sendFailure(Component.literal("§cOnly players can use this!"))
            return 0
        }
        val level = player.level() as? net.minecraft.server.level.ServerLevel ?: return 0
        
        // Play warden heartbeat/darkness sounds
        level.playSound(
            null, player.x, player.y, player.z,
            SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS,
            2.0f, 0.5f
        )
        level.playSound(
            null, player.x, player.y, player.z,
            SoundEvents.WARDEN_TENDRIL_CLICKS, SoundSource.PLAYERS,
            1.5f, 0.7f
        )
        
        // Dark particles emanating outward
        for (i in 0 until 50) {
            val angle = Math.random() * Math.PI * 2
            val dist = Math.random() * 8.0
            val px = player.x + Math.cos(angle) * dist
            val pz = player.z + Math.sin(angle) * dist
            level.sendParticles(
                ParticleTypes.SCULK_SOUL,
                px, player.y + Math.random() * 2.0, pz,
                1, 0.2, 0.2, 0.2, 0.02
            )
        }
        
        // Apply darkness to all nearby players (within 15 blocks)
        val nearbyPlayers = level.getEntitiesOfClass(
            Player::class.java,
            AABB.ofSize(player.position(), 30.0, 15.0, 30.0)
        ) { it != player }
        
        for (target in nearbyPlayers) {
            if (target is ServerPlayer) {
                // Apply darkness effect (like warden)
                target.addEffect(MobEffectInstance(MobEffects.DARKNESS, 200, 0))
                // Also apply brief blindness for extra spook
                target.addEffect(MobEffectInstance(MobEffects.BLINDNESS, 40, 0))
            }
        }
        
        val count = nearbyPlayers.size
        source.sendSuccess({ 
            Component.literal("§8🌑 You release a DARKNESS PULSE! $count player(s) blinded!") 
        }, true)
        return 1
    }
    
    // ========== NEW COMMANDS ==========
    
    private fun mimicSound(ctx: CommandContext<CommandSourceStack>, mob: String): Int {
        val source = ctx.source
        val player = source.player ?: run {
            source.sendFailure(Component.literal("§cOnly players can use this!"))
            return 0
        }
        val level = player.level() as? net.minecraft.server.level.ServerLevel ?: return 0
        
        val sound = when (mob) {
            "zombie" -> SoundEvents.ZOMBIE_AMBIENT
            "wither" -> SoundEvents.WITHER_AMBIENT
            "ghast" -> SoundEvents.GHAST_AMBIENT
            "dragon" -> SoundEvents.ENDER_DRAGON_GROWL
            else -> SoundEvents.ZOMBIE_AMBIENT
        }
        
        level.playSound(null, player.x, player.y, player.z, sound, SoundSource.HOSTILE, 2.0f, 1.0f)
        source.sendSuccess({ Component.literal("§a🔊 Mimicked $mob sound!") }, false)
        return 1
    }
    
    private fun ambushMode(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val player = source.player ?: run {
            source.sendFailure(Component.literal("§cOnly players can use this!"))
            return 0
        }
        val level = player.level() as? net.minecraft.server.level.ServerLevel ?: return 0
        
        // Spawn 4 iron golems in a circle
        val radius = 3.0
        for (i in 0 until 4) {
            val angle = (i * 90.0) * (Math.PI / 180.0)
            val x = player.x + Math.cos(angle) * radius
            val z = player.z + Math.sin(angle) * radius
            
            val golem = EntityType.IRON_GOLEM.create(level, EntitySpawnReason.COMMAND)
            golem?.let {
                it.setPos(x, player.y, z)
                level.addFreshEntity(it)
            }
        }
        
        level.playSound(null, player.x, player.y, player.z, SoundEvents.IRON_GOLEM_REPAIR, SoundSource.NEUTRAL, 1.0f, 1.0f)
        source.sendSuccess({ Component.literal("§6⚔ AMBUSH MODE! 4 Iron Golems summoned to protect you!") }, true)
        return 1
    }
    
    private fun detectHostiles(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val player = source.player ?: run {
            source.sendFailure(Component.literal("§cOnly players can use this!"))
            return 0
        }
        val level = player.level() as? net.minecraft.server.level.ServerLevel ?: return 0
        
        // Find all hostile mobs in 40-block radius
        val hostiles = level.getEntitiesOfClass(
            Monster::class.java,
            AABB.ofSize(player.position(), 80.0, 40.0, 80.0)
        )
        
        for (hostile in hostiles) {
            hostile.addEffect(MobEffectInstance(MobEffects.GLOWING, 200, 0)) // 10 seconds
        }
        
        source.sendSuccess({ Component.literal("§e👁 Detected ${hostiles.size} hostile mobs! They are now glowing.") }, true)
        return 1
    }
    
    private fun moonJump(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val player = source.player as? ServerPlayer ?: run {
            source.sendFailure(Component.literal("§cOnly players can use this!"))
            return 0
        }
        
        // Low gravity = high jump boost + slow falling
        player.addEffect(MobEffectInstance(MobEffects.JUMP_BOOST, 600, 4)) // 30 sec, level 5
        player.addEffect(MobEffectInstance(MobEffects.SLOW_FALLING, 600, 0)) // 30 sec
        
        source.sendSuccess({ Component.literal("§b🌙 MOON JUMP activated! Low gravity for 30 seconds.") }, true)
        return 1
    }
    
    // Track active thunder domes: player UUID -> remaining waves
    private val activeThunderDomes = mutableMapOf<java.util.UUID, Int>()
    
    /**
     * Called every tick to process thunder dome waves
     */
    fun tickThunderDomes(server: net.minecraft.server.MinecraftServer) {
        val iterator = activeThunderDomes.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val playerUUID = entry.key
            var wavesRemaining = entry.value
            
            val player = server.playerList.getPlayer(playerUUID)
            if (player == null || wavesRemaining <= 0) {
                iterator.remove()
                continue
            }
            
            val level = player.level() as? net.minecraft.server.level.ServerLevel ?: continue
            
            // Only spawn lightning every 20 ticks (1 second)
            if (server.tickCount % 20 == 0) {
                for (j in 0 until 8) {
                    val angle = Math.random() * Math.PI * 2
                    val dist = 5.0 + Math.random() * 25.0
                    val x = player.x + Math.cos(angle) * dist
                    val z = player.z + Math.sin(angle) * dist
                    
                    val lightning = LightningBolt(EntityType.LIGHTNING_BOLT, level)
                    lightning.setPos(x, player.y, z)
                    lightning.setVisualOnly(true) // Don't damage
                    level.addFreshEntity(lightning)
                }
                
                wavesRemaining--
                activeThunderDomes[playerUUID] = wavesRemaining
            }
        }
    }
    
    private fun thunderDome(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val player = source.player ?: run {
            source.sendFailure(Component.literal("§cOnly players can use this!"))
            return 0
        }
        
        // Start 15 waves of lightning
        activeThunderDomes[player.uuid] = 15
        
        source.sendSuccess({ Component.literal("§c⚡ THUNDER DOME activated! Lightning storm for 15 seconds!") }, true)
        return 1
    }
    
    private fun copperDrop(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val player = source.player ?: run {
            source.sendFailure(Component.literal("§cOnly players can use this!"))
            return 0
        }
        val level = player.level() as? net.minecraft.server.level.ServerLevel ?: return 0
        
        // Rain copper ingots from above
        for (i in 0 until 32) {
            val x = player.x + (Math.random() - 0.5) * 10
            val y = player.y + 15 + Math.random() * 5
            val z = player.z + (Math.random() - 0.5) * 10
            
            val stack = ItemStack(Items.COPPER_INGOT, 1)
            val itemEntity = ItemEntity(level, x, y, z, stack)
            itemEntity.setDeltaMovement(0.0, -0.2, 0.0)
            level.addFreshEntity(itemEntity)
        }
        
        level.playSound(null, player.x, player.y, player.z, SoundEvents.COPPER_BREAK, SoundSource.BLOCKS, 1.0f, 1.0f)
        source.sendSuccess({ Component.literal("§6🥉 COPPER DROP! Copper is raining from the sky!") }, true)
        return 1
    }
    
    private fun timeWarp(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val ticks = IntegerArgumentType.getInteger(ctx, "ticks")
        val level = source.level
        
        val newTime = level.dayTime + ticks
        level.setDayTime(newTime)
        
        source.sendSuccess({ Component.literal("§d⏰ TIME WARP! Shifted time forward by $ticks ticks.") }, true)
        return 1
    }
    
    private fun drunkVision(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val targets = EntityArgument.getPlayers(ctx, "targets")
        
        for (target in targets) {
            target.addEffect(MobEffectInstance(MobEffects.NAUSEA, 160, 0)) // 8 sec
            target.addEffect(MobEffectInstance(MobEffects.SLOWNESS, 160, 1)) // 8 sec
            target.addEffect(MobEffectInstance(MobEffects.BLINDNESS, 60, 0)) // 3 sec
        }
        
        source.sendSuccess({ Component.literal("§a🍺 DRUNK VISION applied to ${targets.size} player(s)!") }, true)
        return 1
    }
    
    private fun critSave(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val player = source.player as? ServerPlayer ?: run {
            source.sendFailure(Component.literal("§cOnly players can use this!"))
            return 0
        }
        
        if (player.health <= 10f) {
            player.addEffect(MobEffectInstance(MobEffects.ABSORPTION, 200, 3)) // 10 sec, 8 hearts
            player.addEffect(MobEffectInstance(MobEffects.REGENERATION, 100, 2)) // 5 sec, regen III
            player.addEffect(MobEffectInstance(MobEffects.RESISTANCE, 100, 1)) // 5 sec
            
            val level = player.level() as? net.minecraft.server.level.ServerLevel
            level?.playSound(null, player.x, player.y, player.z, SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0f, 1.0f)
            
            source.sendSuccess({ Component.literal("§c💔 CRIT SAVE! Emergency healing activated!") }, true)
        } else {
            source.sendFailure(Component.literal("§eYour HP is above 10, no crit save needed."))
        }
        return 1
    }
    
    private fun proGamerMode(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val player = source.player as? ServerPlayer ?: run {
            source.sendFailure(Component.literal("§cOnly players can use this!"))
            return 0
        }
        val level = source.level
        val server = source.server
        
        // Set difficulty to hard
        server.setDifficulty(Difficulty.HARD, true)
        
        // Set time to night (13000)
        level.setDayTime(13000)
        
        // Give speed II for 20 sec
        player.addEffect(MobEffectInstance(MobEffects.SPEED, 400, 1))
        player.addEffect(MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0))
        
        source.sendSuccess({ Component.literal("§4🎮 PRO GAMER MODE! Difficulty: HARD, Time: NIGHT, Speed II active!") }, true)
        return 1
    }
    
    private fun systemPing(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val server = source.server
        val player = source.player as? ServerPlayer
        val level = source.level as? net.minecraft.server.level.ServerLevel
        
        // Calculate TPS from tick times
        val tickTimes = server.tickTimesNanos
        val avgTickTime = if (tickTimes.isNotEmpty()) tickTimes.average() else 0.0
        val mspt = avgTickTime / 1_000_000.0
        val tps = (1000.0 / mspt).coerceAtMost(20.0)
        val tpsColor = when {
            tps >= 19.0 -> "§a"
            tps >= 15.0 -> "§e"
            tps >= 10.0 -> "§6"
            else -> "§c"
        }
        
        // Memory info
        val runtime = Runtime.getRuntime()
        val usedMem = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
        val maxMem = runtime.maxMemory() / 1024 / 1024
        val memPercent = (usedMem * 100 / maxMem).toInt()
        val memColor = when {
            memPercent < 60 -> "§a"
            memPercent < 80 -> "§e"
            else -> "§c"
        }
        
        // Player ping (latency)
        val pingMs = player?.connection?.latency() ?: 0
        val pingColor = when {
            pingMs < 50 -> "§a"
            pingMs < 100 -> "§e"
            pingMs < 200 -> "§6"
            else -> "§c"
        }
        
        // World info
        val worldTime = level?.dayTime ?: 0L
        val dayCount = (worldTime / 24000L) + 1
        val timeOfDay = worldTime % 24000L
        val timeStr = when {
            timeOfDay < 6000 -> "§e☀ Morning"
            timeOfDay < 12000 -> "§6☀ Noon"
            timeOfDay < 13000 -> "§6☀ Afternoon"
            timeOfDay < 18000 -> "§c🌅 Evening"
            else -> "§8🌙 Night"
        }
        
        // Entity counts
        val entityCount = level?.allEntities?.count() ?: 0
        val loadedChunks = level?.chunkSource?.loadedChunksCount ?: 0
        
        // Player counts
        val onlinePlayers = server.playerCount
        val maxPlayers = server.maxPlayers
        
        // Difficulty
        val difficulty = server.worldData.difficulty.displayName.string
        
        // Active Aeonis systems
        val transformedCount = transformedEntities.size
        val activeVexes = petVexOwners.size
        val activeThunder = activeThunderDomes.size
        val activeDirector = actorWalkTargets.size + actorLookTargets.size + actorAttackTargets.size
        
        source.sendSuccess({ Component.literal("§6§l═══════ AEONIS SYSTEM ═══════") }, false)
        source.sendSuccess({ Component.literal("§e§lMOD INFO") }, false)
        source.sendSuccess({ Component.literal("  §7ID: §baeonis-command-master §7| §7Ver: §b1.2.8b") }, false)
        source.sendSuccess({ Component.literal("  §7Extra Mobs: " + if (AeonisFeatures.isExtraMobsEnabled(server)) "§aON" else "§cOFF") }, false)
        
        source.sendSuccess({ Component.literal("§c§l⚡ PERFORMANCE") }, false)
        source.sendSuccess({ Component.literal("  §7TPS: ${tpsColor}%.1f §7/ 20 §8(%.2fms/tick)".format(tps, mspt)) }, false)
        source.sendSuccess({ Component.literal("  §7Memory: ${memColor}${usedMem}MB §7/ ${maxMem}MB §8(${memPercent}%%)") }, false)
        source.sendSuccess({ Component.literal("  §7Latency: ${pingColor}${pingMs}ms") }, false)
        
        source.sendSuccess({ Component.literal("§a§l🌍 WORLD") }, false)
        source.sendSuccess({ Component.literal("  §7Time: $timeStr §8(Day $dayCount)") }, false)
        source.sendSuccess({ Component.literal("  §7Difficulty: §e$difficulty") }, false)
        source.sendSuccess({ Component.literal("  §7Entities: §b$entityCount §7| Chunks: §b$loadedChunks") }, false)
        source.sendSuccess({ Component.literal("  §7Players: §a$onlinePlayers§7/$maxPlayers") }, false)
        
        source.sendSuccess({ Component.literal("§d§l✦ AEONIS ACTIVE") }, false)
        source.sendSuccess({ Component.literal("  §7Transforms: §b$transformedCount §7| Pet Vexes: §b$activeVexes") }, false)
        source.sendSuccess({ Component.literal("  §7Thunder Domes: §b$activeThunder §7| Director Orders: §b$activeDirector") }, false)
        
        source.sendSuccess({ Component.literal("§6§l═════════════════════════════") }, false)
        return 1
    }
    
    private fun tellStory(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val stories = listOf(
            "§5§o\"The ancient ones whisper of a power beyond comprehension...\"",
            "§5§o\"In the shadows, something stirs. The Aeonis awakens.\"",
            "§5§o\"They said it was impossible. They were wrong.\"",
            "§5§o\"The world trembles as a new legend is born.\"",
            "§5§o\"From the void, power flows. Will you seize it?\"",
            "§5§o\"Heroes are not born. They are forged in chaos.\"",
            "§5§o\"The end is merely another beginning...\"",
            "§5§o\"Reality bends to those who dare command it.\""
        )
        
        val story = stories.random()
        source.server.playerList.broadcastSystemMessage(Component.literal(story), false)
        return 1
    }

    private fun blink(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val player = source.player as? ServerPlayer ?: run {
            source.sendFailure(Component.literal("§cOnly players can blink!"))
            return 0
        }
        val range = IntegerArgumentType.getInteger(ctx, "range").coerceIn(1, 25)
        val look = player.lookAngle.normalize()
        val eye = player.eyePosition
        val target = eye.add(look.scale(range.toDouble()))
        val hit = player.level().clip(
            ClipContext(eye, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)
        )

        val destination = when (hit.type) {
            HitResult.Type.BLOCK -> hit.location.subtract(look.scale(0.5))
            else -> target
        }

        player.teleportTo(destination.x, destination.y, destination.z)
        player.addEffect(MobEffectInstance(MobEffects.RESISTANCE, 40, 4)) // brief invuln
        source.sendSuccess({ Component.literal("§b✦ Blinked $range blocks!") }, true)
        return 1
    }

    private fun dash(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val player = source.player as? ServerPlayer ?: run {
            source.sendFailure(Component.literal("§cOnly players can dash!"))
            return 0
        }
        val dir = player.lookAngle.normalize().scale(1.5)
        player.setDeltaMovement(dir)
        player.hurtMarked = true
        source.sendSuccess({ Component.literal("§e⚡ Dashed forward!") }, true)
        return 1
    }

    private fun petVex(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val player = source.player as? ServerPlayer ?: run {
            source.sendFailure(Component.literal("§cOnly players can summon pets!"))
            return 0
        }
        val level = player.level() as? net.minecraft.server.level.ServerLevel ?: return 0

        val vex = EntityType.VEX.spawn(level, player.blockPosition().above(), EntitySpawnReason.COMMAND)
        if (vex == null) {
            source.sendFailure(Component.literal("§cFailed to summon pet vex!"))
            return 0
        }
        
        // Track this vex as owned by the player
        petVexOwners[vex.id] = player.uuid
        
        // Give vex effects to make it visible and strong
        vex.addEffect(MobEffectInstance(MobEffects.GLOWING, 1200, 0))
        vex.addEffect(MobEffectInstance(MobEffects.STRENGTH, 1200, 1))
        vex.setLimitedLife(1200) // 60s
        
        // Find nearest hostile mob and set as target
        val nearbyHostile = level.getEntitiesOfClass(
            Monster::class.java,
            vex.boundingBox.inflate(16.0)
        ) { it !is Vex }.minByOrNull { it.distanceToSqr(vex) }
        
        if (nearbyHostile != null) {
            vex.setTarget(nearbyHostile)
        }

        source.sendSuccess({ Component.literal("§d🧚 Pet Vex summoned! It will attack nearby hostiles.") }, true)
        return 1
    }

    private fun spiritWolves(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val player = source.player as? ServerPlayer ?: run {
            source.sendFailure(Component.literal("§cOnly players can summon wolves!"))
            return 0
        }
        val level = player.level() as? net.minecraft.server.level.ServerLevel ?: return 0
        val count = IntegerArgumentType.getInteger(ctx, "count").coerceIn(1, 5)

        repeat(count) {
            val wolf = EntityType.WOLF.spawn(level, player.blockPosition(), EntitySpawnReason.COMMAND) ?: return@repeat
            // Apply effects for spirit wolf appearance
            wolf.setPos(player.x + (Math.random() - 0.5) * 2, player.y, player.z + (Math.random() - 0.5) * 2)
            wolf.addEffect(MobEffectInstance(MobEffects.GLOWING, 900, 0))
            wolf.addEffect(MobEffectInstance(MobEffects.STRENGTH, 900, 1))
            wolf.addEffect(MobEffectInstance(MobEffects.SPEED, 900, 1))
        }

        source.sendSuccess({ Component.literal("§f🐺 Spirit Wolves summoned ($count)!") }, true)
        return 1
    }

    private fun clearEffects(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val targets = EntityArgument.getPlayers(ctx, "targets")
        for (t in targets) {
            t.removeAllEffects()
        }
        source.sendSuccess({ Component.literal("§a✨ Cleared effects for ${targets.size} player(s).") }, true)
        return 1
    }
    
    private fun exitBody(ctx: CommandContext<CommandSourceStack>, targetMode: GameType): Int {
        val source = ctx.source
        val player = source.player as? ServerPlayer ?: run {
            source.sendFailure(Component.literal("§cOnly players can use this!"))
            return 0
        }
        
        // Check if transformed
        val entity = transformedEntities.remove(player.uuid)
        if (entity == null) {
            source.sendFailure(Component.literal("§cYou're not transformed!"))
            return 0
        }
        
        // Stop controlling
        AeonisNetworking.removeControlledEntity(player)
        
        // Stop spectating
        player.setCamera(player)
        
        // Remove tracked original gamemode since we're using explicit mode
        originalGameModes.remove(player.uuid)
        
        // Set to requested gamemode
        player.setGameMode(targetMode)
        
        // Teleport player to entity's last position before killing it
        player.teleportTo(entity.x, entity.y, entity.z)
        
        // Kill the spawned entity
        entity.discard()
        
        val modeName = if (targetMode == GameType.SURVIVAL) "Survival" else "Creative"
        source.sendSuccess({ 
            Component.literal("§a✨ Exited body! Now in $modeName mode.") 
        }, true)
        return 1
    }

    private fun makeChaotic(ctx: CommandContext<CommandSourceStack>): Int {
        val targets = EntityArgument.getEntities(ctx, "targets")
        var count = 0
        
        // Reflection to access goalSelector
        var goalSelectorField: java.lang.reflect.Field? = null
        try {
            // Try mapped name first
            goalSelectorField = Mob::class.java.getDeclaredField("goalSelector")
        } catch (e: NoSuchFieldException) {
             // Fallback: find by type (first one is usually goalSelector)
             goalSelectorField = Mob::class.java.declaredFields.find { it.type == net.minecraft.world.entity.ai.goal.GoalSelector::class.java }
        }
        goalSelectorField?.isAccessible = true
        
        for (entity in targets) {
            if (entity is PathfinderMob) {
                try {
                    val goalSelector = goalSelectorField?.get(entity) as? net.minecraft.world.entity.ai.goal.GoalSelector
                    
                    if (goalSelector != null) {
                        // Add chaotic goals with high priority (0 and 1)
                        goalSelector.addGoal(0, SuicidalLavaGoal(entity))
                        goalSelector.addGoal(1, CliffJumpGoal(entity))
                        goalSelector.addGoal(2, CrazySpinGoal(entity))
                        
                        // Add speed effect to make it funnier
                        entity.addEffect(MobEffectInstance(MobEffects.SPEED, Int.MAX_VALUE, 2))
                        count++
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        
        ctx.source.sendSuccess({ Component.literal("§c🤪 Made $count entities chaotic!") }, true)
        return count
    }

    class SuicidalLavaGoal(private val mob: PathfinderMob) : Goal() {
        private var lavaPos: BlockPos? = null

        init {
            this.flags = EnumSet.of(Flag.MOVE)
        }

        override fun canUse(): Boolean {
            if (mob.tickCount % 20 != 0) return false
            val pos = mob.blockPosition()
            val level = mob.level()
            
            // Scan for lava
            for (x in -15..15) {
                for (y in -5..5) {
                    for (z in -15..15) {
                        val checkPos = pos.offset(x, y, z)
                        if (level.getBlockState(checkPos).block == Blocks.LAVA) {
                            lavaPos = checkPos
                            return true
                        }
                    }
                }
            }
            return false
        }

        override fun start() {
            lavaPos?.let {
                mob.navigation.moveTo(it.x.toDouble(), it.y.toDouble(), it.z.toDouble(), 1.5)
            }
        }
    }

    class CliffJumpGoal(private val mob: PathfinderMob) : Goal() {
        private var jumpPos: BlockPos? = null

        init {
            this.flags = EnumSet.of(Flag.MOVE)
        }

        override fun canUse(): Boolean {
            if (mob.tickCount % 20 != 0) return false
            val pos = mob.blockPosition()
            val level = mob.level()
            
            // Scan for drops
            for (x in -10..10) {
                for (z in -10..10) {
                    val checkPos = pos.offset(x, 0, z)
                    if (level.isEmptyBlock(checkPos.below()) && 
                        level.isEmptyBlock(checkPos.below(2)) && 
                        level.isEmptyBlock(checkPos.below(3))) {
                        jumpPos = checkPos
                        return true
                    }
                }
            }
            return false
        }

        override fun start() {
            jumpPos?.let {
                mob.navigation.moveTo(it.x.toDouble(), it.y.toDouble(), it.z.toDouble(), 1.5)
            }
        }
    }

    class CrazySpinGoal(private val mob: PathfinderMob) : Goal() {
        init {
            this.flags = EnumSet.of(Flag.MOVE, Flag.LOOK)
        }

        override fun canUse(): Boolean {
            return mob.random.nextFloat() < 0.05f // 5% chance to start spinning
        }

        override fun tick() {
            mob.yRot += 45f
            mob.yBodyRot += 45f
            mob.navigation.moveTo(mob.x + mob.random.nextGaussian(), mob.y, mob.z + mob.random.nextGaussian(), 2.0)
        }
    }
}
