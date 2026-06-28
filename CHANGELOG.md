# Splattercraft 3.3.0

## What's New

### Splat Zones Match Type
- New match type: **Splat Zones** (`/match start <stage> <time> zones`)
- Zone Marker tool (`/give @s splatcraft:splat_zones`) — define capture zones on stages
- Per-zone capture tracking with HUD indicators showing ink percentage per team
- 70%+ ink in a zone captures it; control all zones to start scoring
- Score counts down from 100 per team; penalties applied on control loss
- Overtime system with drain timer
- Knockout: reaching 0 ends the match immediately

### Ink Armor Rework
- Now applies to **all teammates** when activated (same ink color)
- Shield HP halved: 12 → 6 (3 hearts)
- Plays shield break sound effect on destruction

### Super Jump
- Can now select a jump target while airborne via the GUI overlay
- Forces squid form mid-air; jump launches automatically on landing
- Cooldown starts ticking immediately while airborne

## Changes

### Game Mechanics
- New gamerule `splatcraft:inkAbilityWhitelist` (default: false) — when true, restricts ink abilities to players in the ability whitelist config
- Splat Zones: each zone independently inks when captured at 70%+ instead of waiting for all zones
- Super jump: lead messages no longer appear when both teams are still at 100 (initial state)

### Fixes
- Splat Zones: knockout score bar updates immediately instead of after a 3-second delay
- Splat Zones: penalty tracking and overtime drain timing fixed
- Splat Zones: zone highlight colors now render per-zone instead of using a single global color
- Splat Zones: improper "we have the lead" message no longer fires on first control capture

## Previous Versions

### 3.0.0 — The Turf War Update
- `/match` command with Turf War mode, HUD, result screen
- Special weapon hand-use and `/startinfinitespecial`
- Death recap cinematic with auto-respawn
- Various bug fixes and zh_cn translations
