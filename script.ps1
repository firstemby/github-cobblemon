Set-Location (git rev-parse --show-toplevel)
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
git fetch origin
$docs = [Environment]::GetFolderPath('MyDocuments')
$out = Join-Path $docs "cobblemon_ctx.txt"
Remove-Item $out -ErrorAction SilentlyContinue
function Dump($title, $text) {
    Add-Content $out -Encoding UTF8 "`n===== $title =====`n"
    Add-Content $out -Encoding UTF8 ($text | Out-String)
}
function DumpRef($ref, $name) {
    $p = git ls-tree -r --name-only $ref | Where-Object { $_ -like "*/$name" } | Select-Object -First 1
    if (-not $p) { Dump "NOT FOUND: $ref $name" ""; return }
    Dump "${ref}:$p" ((git show "${ref}:$p") -join "`n")
}

$rework = "origin/bigger-brains-pathfinder-rework"
$smooth = "origin/mon-smoothening"

# Rework side: path creation, navigation, tracker, callers
foreach ($n in "OmniPathNodeMaker.kt","OmniPathNavigation.kt","NavigationContext.kt","RegularMovementTracker.kt",
               "WanderType.kt","StayNearOwner.kt","AttackFixation.kt","AttackTargetMelee.kt",
               "LookAndMoveFixation.kt","MovementTrackerFixation.kt","SwimBehaviour.kt","WalkBehaviour.kt") {
    DumpRef $rework $n
}
Dump "grep: floats behaviour on rework" (git grep -n "cobblemon:floats" $rework -- common/src/main/resources)
Dump "grep: OmniPathingEntity impl in PokemonEntity on rework" (git grep -n -E "override fun (canWalk|canFly|canSwimInWater|canSwimInLava|canSwimUnderFluid|canWalkOnWater|canWalkOnLava|canBreatheUnderwater|fireImmune)" $rework -- common/src/main/kotlin/com/cobblemon/mod/common/entity/pokemon/PokemonEntity.kt)

# Smoothening side: the files that meet the rework
foreach ($n in "OmniPathNavigation.kt","NavigationContext.kt","PokemonMoveControl.kt","CobblemonWalkTarget.kt") {
    DumpRef $smooth $n
}
Get-Item $out