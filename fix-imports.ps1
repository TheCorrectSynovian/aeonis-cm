$files = Get-ChildItem -Path "c:\Prenxy Packages\AEONIS\mc-mod\aeonis-cm\src\main\kotlin" -Recurse -Filter "*.kt"

$replacements = @{
    # ResourceLocation -> Identifier
    'net\.minecraft\.resources\.ResourceLocation' = 'net.minecraft.resources.Identifier'
    'ResourceLocation\.fromNamespaceAndPath' = 'Identifier.fromNamespaceAndPath'
    'ResourceLocation\(' = 'Identifier('
    
    # GameRules
    'net\.minecraft\.world\.level\.GameRules' = 'net.minecraft.world.level.gamerules.GameRules'
    
    # Monster subpackages
    'net\.minecraft\.world\.entity\.monster\.Zombie(?!Entity)' = 'net.minecraft.world.entity.monster.zombie.Zombie'
    'net\.minecraft\.world\.entity\.monster\.Skeleton(?!Entity)' = 'net.minecraft.world.entity.monster.skeleton.Skeleton'
    'net\.minecraft\.world\.entity\.monster\.AbstractSkeleton' = 'net.minecraft.world.entity.monster.skeleton.AbstractSkeleton'
    'net\.minecraft\.world\.entity\.monster\.Pillager' = 'net.minecraft.world.entity.monster.illager.Pillager'
    'net\.minecraft\.world\.entity\.monster\.Evoker' = 'net.minecraft.world.entity.monster.illager.Evoker'
    
    # Animal subpackages
    'net\.minecraft\.world\.entity\.animal\.IronGolem' = 'net.minecraft.world.entity.animal.golem.IronGolem'
    'net\.minecraft\.world\.entity\.animal\.SnowGolem' = 'net.minecraft.world.entity.animal.golem.SnowGolem'
    'net\.minecraft\.world\.entity\.animal\.Chicken' = 'net.minecraft.world.entity.animal.chicken.Chicken'
    'net\.minecraft\.world\.entity\.animal\.Pig' = 'net.minecraft.world.entity.animal.pig.Pig'
    'net\.minecraft\.world\.entity\.animal\.Cow' = 'net.minecraft.world.entity.animal.cow.Cow'
    'net\.minecraft\.world\.entity\.animal\.horse\.Horse' = 'net.minecraft.world.entity.animal.equine.Horse'
    'net\.minecraft\.world\.entity\.animal\.Parrot' = 'net.minecraft.world.entity.animal.parrot.Parrot'
    'net\.minecraft\.world\.entity\.animal\.Dolphin' = 'net.minecraft.world.entity.animal.dolphin.Dolphin'
    'net\.minecraft\.world\.entity\.animal\.Bee' = 'net.minecraft.world.entity.animal.bee.Bee'
    
    # NPC
    'net\.minecraft\.world\.entity\.npc\.Villager' = 'net.minecraft.world.entity.npc.villager.Villager'
    
    # Projectiles
    'net\.minecraft\.world\.entity\.projectile\.Arrow(?!Event)' = 'net.minecraft.world.entity.projectile.arrow.Arrow'
    'net\.minecraft\.world\.entity\.projectile\.Snowball' = 'net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball'
    'net\.minecraft\.world\.entity\.projectile\.WitherSkull' = 'net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull'
    'net\.minecraft\.world\.entity\.projectile\.DragonFireball' = 'net.minecraft.world.entity.projectile.hurtingprojectile.DragonFireball'
    'net\.minecraft\.world\.entity\.projectile\.SmallFireball' = 'net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball'
    'net\.minecraft\.world\.entity\.projectile\.LargeFireball' = 'net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball'
    'net\.minecraft\.world\.entity\.projectile\.windcharge\.BreezeWindCharge' = 'net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.BreezeWindCharge'
    
    # Vehicle
    'net\.minecraft\.world\.entity\.vehicle\.Minecart' = 'net.minecraft.world.entity.vehicle.minecart.Minecart'
}

foreach ($file in $files) {
    $content = Get-Content $file.FullName -Raw
    $modified = $false
    
    foreach ($pattern in $replacements.Keys) {
        if ($content -match $pattern) {
            $content = $content -replace $pattern, $replacements[$pattern]
            $modified = $true
        }
    }
    
    if ($modified) {
        Set-Content -Path $file.FullName -Value $content -NoNewline
        Write-Host "Updated: $($file.Name)"
    }
}

Write-Host "Done!"
