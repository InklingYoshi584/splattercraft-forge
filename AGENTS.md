# AGENTS.md

## Quick Facts

- **Mod/Platform**: Splatcraft on Minecraft Forge 1.20.1 (Forge 47.3.0, Java 17)
- **Build**: `./gradlew compileJava` (no tests, no lint — this is the only validation)
- **Shell**: PowerShell 5.1 — do not use `&&`, chain with `;` or run sequentially
- **Mixin config**: `src/main/resources/splatcraft.mixins.json` — must be updated when adding mixins

## Must-Touch Files When Adding Features

| Change | File(s) to update |
|--------|-------------------|
| New item/block | `registries/SplatcraftItems.java` or `SplatcraftBlocks.java`, `assets/splatcraft/lang/en_us.json` |
| New entity | `registries/SplatcraftEntities.java` (type + renderer) |
| New packet | `network/c2s/` or `network/s2c/`, then `SplatcraftPacketHandler.registerMessages()` |
| New screen | Bind in `ClientSetupHandler.bindScreenContainers()` |
| New mixin | Add entry to `splatcraft.mixins.json`; client-only mixins go in the `client` list |
| New damage type | Key in `registries/SplatcraftDamageTypes.java`, JSON under `data/splatcraft/damage_type/` |

## Special Weapon Architecture

- **Base class**: `items/weapons/SpecialWeaponItem.java` — all lifecycle hooks live here.
- **Tick driver**: `handlers/SpecialHandler.java` (server-only, `TickEvent.Phase.START`).
- **Runtime state**: Store in `PlayerInfo.getSpecialData()` (per-player `CompoundTag`), not scattered fields.
- **Active tracking**: `PlayerInfo.hasActiveSpecial()` is the authority. `WeaponBaseItem.setActiveSpecial(weapon, true)` mirrors it on the stack for render mixin detection.

### Weapon replacement during active special
- Override `replacesMainWeapon(...)` — can return conditional values (e.g., only while throws remain), not just `true`.
- Override `getMainWeaponReplacementRenderStack(...)` when the held model differs from the special item.
- `ItemRendererMixin.java` intercepts the render call; `WeaponHandler.onPlayerTick` routes use-ticks.

### Re-equip animation suppression
- `WeaponBaseItem.shouldCauseReequipAnimation(...)` strips `SpecialPoints` and `ActiveSpecial` tags before comparing stacks so NBT-only changes don't retrigger the first-person equip animation.
- `SubWeaponItem` has a similar override stripping `EntityData`.

### Early termination
- `shouldInterruptActiveSpecial(...)` — return `true` to end early (e.g., when all throws used + all sequences finished).

### Active hint suppression
- `SpecialHudHandler.getActiveHint()` returns the translatable hint for each special. Return `null` to suppress.

## Ink Strike Specifics

### Tornado (`InkstrikeTornadoEntity.java`)
- Height: `TORNADO_HEIGHT = 40`. Inks full cylindrical volume via direct `InkBlockUtils.inkBlock` calls (no `InkExplosion`).
- `minY` starts one block below tornado position (`getY() - 1`) to catch the ground.
- Profiles: `InkstrikeProfile.SINGLE` (d=10, dur=30, dmg=4) and `TRIPLE` (d=6, dur=10, dmg=10).

### Tactical overlay (`InkstrikeTacticalOverlayHandler.java`)
- Single `InkstrikeSpecialItem` only (not Triple).
- **HUD hiding**: `RenderGuiOverlayEvent.Pre` cancels all overlay types (including hotbar). Render the map in Pre before cancelling.
- **Dynamic sizing**: `avail = min(sw, sh) - 24`, `cellPx = max(1, avail / CELLS)`. No hardcoded pixel constants.
- **Preview circle**: use tornado *radius* (`tornadoDiameter * 0.5F`), not full diameter.
- **Cursor**: scales with `cellPx` — `cursorHalf = max(1, cellPx/2)`, `cursorLen = max(4, cellPx*2)`.
- Hint text on the map is hardcoded English. Bottom-HUD hint for single inkstrike is suppressed.

## HUD Patterns

- Hide HUD elements by cancelling `RenderGuiOverlayEvent.Pre` for the relevant overlay type.
- If your rendering depends on a specific overlay's Post, move it to Pre (before cancel) or exclude that overlay from cancellation.
- No `RenderGuiEvent` or Gui mixins exist. Use overlay events.

## Code Style / Conventions

- **Match the file you're editing** — brace placement, indentation, and import order vary (older files use own-line braces; newer files use same-line).
- Never do reformat-only diffs. Fix whitespace only in lines you're already changing.
- Item classes end with `Item`, entity classes end with `Entity`, packet classes end with `Packet`.
- Client-only code lives under `net.splatcraft.forge.client`. Never load it on a dedicated server.
- Registry names and JSON keys use `lowercase_snake_case`.
- Imports: `java.*` → Minecraft/Forge → project. Avoid wildcards unless the file already uses them.
- Watch for preserved legacy typos (e.g. `SplatcraftRegisties`).
