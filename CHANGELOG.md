# Splattercraft 3.3.1

## Fixes
- Weapon Workbench (block) recipe is now unlocked for all players — it always appears in the recipe book instead of requiring a pickup-triggered advancement (which failed to fire for players who already had the materials). It is the base crafting gate; weapons crafted at the workbench remain blueprint-gated
- Sardinium recipe advancement no longer references a non-existent recipe; Grate Ramp advancement no longer rewards a non-existent flipped recipe
- Salmon no longer drop Power Eggs
- Sunken crates and Sardinium deposits now actually generate on ocean floors (the features were registered but never attached to any biome)
- Weapons at the workbench require blueprints again — the unlock advancements stripped in 3.3.0 are restored (starter Splattershot Jr / Ink Tank Jr stay unlocked)

## Previous Versions

### 3.3.0
- Splat Zones match type: zone marker tool, per-zone capture, penalties, overtime, knockout
- Ink Armor applies to all teammates; shield HP halved (12 → 6); break sound
- Super Jump: target selection while airborne, auto-launch on landing, airborne cooldown
- New gamerule `splatcraft:inkAbilityWhitelist` (default: false)
- Various Splat Zones HUD/knockout/penalty fixes

### 3.0.0 — The Turf War Update
- `/match` command with Turf War mode, HUD, result screen
- Special weapon hand-use and `/startinfinitespecial`
- Death recap cinematic with auto-respawn
- Various bug fixes and zh_cn translations
