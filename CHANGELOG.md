# Splatcraft 3.0.0

> 2168 insertions, 26 files — The Turf War Update

## /match Command — Turf War Battles

- **`/match start <stage> <time> turf`** — Start a turf war match on any configured stage
- **`/match stop <stage>`** — Stop an active match by stage name (Tab auto-complete)

### Match Flow
- **Ready** → **Set** → **GO!** countdown with screen-filling titles
- Players frozen during countdown and result phases
- Real-time turf ink coverage scanning (every second)
- 1-minute warning at top of screen
- Last 10 seconds countdown displayed on action bar
- **GAME!** title when time runs out

### Match HUD
- Centered timer with black background, turns yellow at 1 minute
- Player head icons with team ink color borders
- Death: grayscale icon + gray **X** slam-in animation
- Special ready: rainbow cycling border
- Danger state: icons shrink + warning when opponent leads by >15%
- Adaptive sizing to screen resolution

### Result Screen (Splatroom 3 Table Turf Style)
- Full bar fills from edges → pause → **CRASH** to final split point
- Percentage numbers count up smoothly during animation
- **YOU WIN!** / **YOU LOSE** large text with fade-in
- Green/red vignette border effects
- Sound effects: pling during fill, bass on crash, level-up fanfare on win
- View locked downward over stage with 3-second delay before animation starts

### Team System
- Auto-detect teams by ink color matching stage team colors
- Auto-assign un-matched players to smallest team
- Force ink color sync for accurate turf scanning
- Spawn point set to each player's team spawn pad

---

## Special Weapon Improvements

### Hand-Useable Special Items
- Hold any special weapon item + long-press (2s) → activates the special
- Auto-finds a main weapon in inventory to use as context
- Bypasses special points requirement for direct use

### `/startinfinitespecial <item> [players]`
- Grants infinite-duration special to target players
- Tab auto-complete lists all registered special weapons
- Works with all specials: Inkstrike, Inkzooka, Ultra Stamp, Zipcaster, Ink Armor, etc.
- Death terminates the special (intentional)

### Death Messages
- Special weapon kills now show the correct weapon name (Inkstrike, Inkzooka, Ultra Stamp, Zipcaster)
- Previously showed the main weapon name instead of the special that dealt damage

---

## Bug Fixes

- **Death recap corpse pushing** — Removed `setCamera(killer)` which teleported the corpse to the killer's location, causing collision and visibility issues
- **Corpse collision** — Added `noPhysics = true` to death recap corpses
- **Inkstrike crash** — Fixed NPE in `getRuntimeData()` when rendering other players with `ItemRendererMixin`
- **Health bar** — Now properly hidden during match countdown (was missing `PLAYER_HEALTH` overlay cancellation)

---

## Quality of Life

- Debug messages removed from match system and team assignment
- `zh_cn` translations for all new features
- `/match stop` uses stage names instead of UUIDs with Tab auto-complete

---

## Files Changed

```
26 files changed, 2168 insertions(+), 214 deletions(-)
```

**New files (12):**
- `data/match/Match.java`, `MatchPhase.java`, `MatchType.java`
- `handlers/MatchHandler.java`
- `commands/MatchCommand.java`, `StartInfiniteSpecialCommand.java`
- `network/s2c/SyncMatchStatePacket.java`, `MatchResultPacket.java`
- `client/data/ClientMatchData.java`
- `client/handlers/MatchHudHandler.java`

**Modified files (14):**
- `PlayerInfo.java` — `infiniteSpecial` field
- `SpecialHandler.java` — infinite special skip logic
- `SpecialWeaponItem.java` — hand-usable long-press
- `InkstrikeSpecialItem.java` — NPE fix
- `ZipcasterSpecialItem.java` — death weapon name fix
- `InkstrikeTornadoEntity.java` — death weapon name fix
- `InkzookaTornadoEntity.java` — death weapon name fix
- `UltraStampThrownEntity.java` — death weapon name fix
- `SplatcraftCommonHandler.java` — corpse noPhysics + remove setCamera(killer)
- `SplatcraftCommands.java` — command registrations
- `SplatcraftPacketHandler.java` — packet registrations
- `PlayerMovementHandler.java` — match freeze input
- `WeaponHotkeyMixin.java` — match freeze weapon use
- `en_us.json` + `zh_cn.json` — translations
