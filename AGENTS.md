# AGENTS.md

Repository guide for coding agents working in `D:\workspace\splattercraft-forge`.

## Project Summary

- Mod: `Splatcraft`
- Platform: Minecraft Forge
- Minecraft version: `1.20.1`
- Forge version: `47.3.0`
- Java version: `17`
- Main package: `net.splatcraft.forge`
- Build system: Gradle (`./gradlew`)
- Mixin config: `src/main/resources/splatcraft.mixins.json`

## Rule Files

- `AGENTS.md`: you are reading it.
- `.cursor/rules/`: not present at time of writing.
- `.cursorrules`: not present at time of writing.
- `.github/copilot-instructions.md`: not present at time of writing.
- If any of the above files are added later, treat them as higher-priority repository guidance and update this file accordingly.

## Working Assumptions

- The repository currently has no dedicated lint configuration (`checkstyle`, `spotless`, `pmd`, `.editorconfig`) checked in.
- There are currently no `src/test` or `src/gametest` Java sources checked in.
- Because of that, `compileJava` is the fastest reliable validation command for most edits.
- For feature work that touches both Java and hand-authored resource/data JSON, `compileJava` plus `processResources` is the current best default validation pair.
- Prefer small, targeted changes that match existing patterns over broad refactors.

## Build Commands

- Compile main sources: `./gradlew compileJava`
- Full build: `./gradlew build`
- Reobfuscate jar: `./gradlew reobfJar`
- Build publishable jar: `./gradlew jar`
- Process resources only: `./gradlew processResources`
- Clean build outputs: `./gradlew clean`

## Run Commands

- Run client dev environment: `./gradlew runClient`
- Run dedicated server dev environment: `./gradlew runServer`
- Run GameTest server: `./gradlew runGameTestServer`
- Generate data resources: `./gradlew runData`

## Test Commands

- Run JVM tests: `./gradlew test`
- Run a single JVM test class: `./gradlew test --tests "com.example.MyTest"`
- Run a single JVM test method: `./gradlew test --tests "com.example.MyTest.myMethod"`
- Dry-run test selection: `./gradlew test --test-dry-run --tests "com.example.MyTest"`

## GameTest Notes

- ForgeGradle exposes `runGameTestServer` and accepts `--args`.
- There are no GameTests in the repo right now, but if you add them, use the game test server to validate them.
- Typical pattern for a specific GameTest once tests exist: `./gradlew runGameTestServer --args "--tests <namespace>.<test_name>"`
- Typical pattern for a namespace once tests exist: `./gradlew runGameTestServer --args "--tests splatcraft.*"`
- If you add GameTests, document the exact invocation in your PR or follow-up notes.

## Recommended Validation by Change Type

- Java-only gameplay logic: `./gradlew compileJava`
- Resource or lang changes: `./gradlew processResources`
- Recipe, loot, tag, or damage type JSON changes: `./gradlew processResources`
- Registry, mixin, menu, networking, or item changes: `./gradlew compileJava`
- New gameplay features that touch code and JSON/resources: `./gradlew compileJava` and `./gradlew processResources`
- Data generation changes: `./gradlew runData`
- Release-oriented changes: `./gradlew build`

## Source Layout

- Java sources: `src/main/java`
- Resources: `src/main/resources`
- Generated resources: `src/generated/resources`
- Mod metadata: `src/main/resources/META-INF/mods.toml`
- Access transformer: `src/main/resources/META-INF/accesstransformer.cfg`

## Architecture Notes

- Registries are centralized under `net.splatcraft.forge.registries`.
- Network packets live under `net.splatcraft.forge.network.c2s` and `net.splatcraft.forge.network.s2c`.
- Shared packet registration is in `SplatcraftPacketHandler`.
- Client-only setup is typically under `net.splatcraft.forge.client`.
- Menus/containers often live under `net.splatcraft.forge.tileentities.container`.
- Data-driven weapon settings are loaded by reload listeners in `handlers/DataHandler.java`.
- Special weapon lifecycle hooks live on `items/weapons/SpecialWeaponItem.java`; runtime per-special state can be stored in `PlayerInfo.getSpecialData()`.
- Active special held-item replacement is intercepted in `mixin/ItemRendererMixin.java`; specials can override `getMainWeaponReplacementRenderStack(...)` when the held model should differ from the registered special item.
- Custom entity registration and renderer binding are both handled in `registries/SplatcraftEntities.java`.
- Item-like projectile/entity visuals can often reuse `client/renderer/ItemStackEntityRenderer.java` instead of needing a bespoke model renderer.
- Custom damage types are keyed in `registries/SplatcraftDamageTypes.java`, defined in `data/splatcraft/damage_type`, and can pick up vanilla behavior through files under `data/minecraft/tags/damage_type`.

## Code Style: General

- Follow the existing local style instead of imposing a new formatter.
- Preserve mixed legacy style when editing old files; avoid reformat-only diffs.
- Use UTF-8 source encoding.
- Prefer ASCII unless the file already uses special characters for gameplay text.
- Use braces on their own line in many legacy classes; newer files sometimes use same-line braces. Match the file you are editing.
- Keep methods reasonably small, but do not split simple Forge event handlers into unnecessary helpers.

## Code Style: Imports

- Imports are not strictly sorted by formatter; match existing file conventions.
- Most files group `java.*` imports first, then Minecraft/Forge imports, then project imports.
- Static imports are used sparingly.
- Avoid wildcard imports unless the file already uses them and the package is tightly scoped.

## Code Style: Naming

- Classes: `PascalCase`
- Methods and fields: `camelCase`
- Constants: `UPPER_SNAKE_CASE`
- Registry objects usually use `camelCase` field names.
- Resource ids, JSON names, and registry names use lowercase snake_case.
- Packet classes usually end with `Packet` and are grouped by direction.
- Item subclasses usually end with `Item`; entity subclasses usually end with `Entity`.

## Code Style: Types and APIs

- Prefer explicit concrete types when they improve readability in registry and networking code.
- Use generics consistently; this codebase already uses parameterized registry and item types heavily.
- Use `@NotNull` and `@Nullable` from JetBrains annotations when overriding Minecraft/Forge APIs that already follow that convention.
- Respect client/server separation; keep client-only classes under client packages and avoid loading them on a dedicated server.
- Use Forge and Minecraft helper APIs already present in the codebase before introducing new abstractions.

## Code Style: Control Flow

- Early returns are common and encouraged, especially in packet handlers and event callbacks.
- Guard clauses are preferred for invalid state, wrong side, empty stacks, and missing capabilities.
- Use pattern matching `instanceof` where the codebase already does so.
- Keep event handlers defensive: check side, nullability, cooldown state, and capability presence.

## Error Handling

- Prefer safe no-op returns for invalid gameplay state instead of throwing, unless the condition indicates protocol corruption or an impossible invariant.
- In packet execution, validate sender state before mutating the world.
- Throwing `IllegalStateException` is acceptable for truly impossible states, as seen in some network code.
- When loading data-driven content, follow existing patterns that log or surface parse failures without crashing more than necessary.
- Do not swallow exceptions silently if they indicate broken resource loading or packet misuse.

## Forge and Minecraft Conventions

- Register content through deferred registers in registry classes.
- Keep side-specific setup in the correct mod lifecycle hooks.
- When opening menus, use Forge networking/menu helpers already established in the repository.
- When adding packets, update `SplatcraftPacketHandler.registerMessages()`.
- When adding client screens, bind them in `ClientSetupHandler.bindScreenContainers()`.
- When adding mixins, update `splatcraft.mixins.json` and keep client-only mixins in the `client` list.

## Resources and Data

- Lang keys belong in `assets/splatcraft/lang/en_us.json` and should follow existing naming patterns.
- Item/block model ids should match registry names.
- Recipes live under `data/splatcraft/recipes`.
- Weapon settings JSON lives under `data/splatcraft/weapon_settings`.
- Custom damage type JSON lives under `data/splatcraft/damage_type`.
- Vanilla tag overrides used by the mod, such as damage type behavior tags, live under `data/minecraft/tags`.
- Simple model aliases are acceptable when multiple items intentionally share the same item render.
- Data generation outputs should go to `src/generated/resources` via `runData`.
- Do not hand-edit generated output unless the project already treats that file as source.

## General Feature-Adding Process

- Start by finding the closest existing weapon, entity, packet, renderer, or handler and copy its pattern before introducing a new abstraction.
- For new special weapons, add the item class under `items/weapons`, register it in `SplatcraftItems`, expose it in `SplatcraftItemGroups` if appropriate, add lang entries in `assets/splatcraft/lang/en_us.json`, and add recipe/model files under `src/main/resources`.
- If a special replaces the main weapon while active, use `replacesMainWeapon(...)`; if the held render should be different from the stored special item, also override `getMainWeaponReplacementRenderStack(...)`.
- Store per-activation counters, UUIDs, and other transient special runtime state in `PlayerInfo.getSpecialData()` instead of scattering one-off fields elsewhere.
- For new entities, add the class under `entities` or `entities/subs`, register the `EntityType` and renderer in `SplatcraftEntities`, and prefer existing renderer helpers where possible.
- For new damage behaviors, add a key in `SplatcraftDamageTypes`, add the matching JSON under `data/splatcraft/damage_type`, and use vanilla damage type tags under `data/minecraft/tags/damage_type` when behavior such as bypassing armor or cooldown should be data-driven.
- For mixed feature work, validate the narrowest useful commands first: `processResources` for JSON/resource wiring, `compileJava` for code wiring, and both for full feature additions.

## When Editing Legacy Files

- Expect inconsistent indentation and brace placement in older classes.
- Fix the local area you touch, but do not normalize entire files without a strong reason.
- Avoid large rename-only diffs unless the user explicitly requests cleanup.
- Watch for typos preserved as public API or registry names, for example `SplatcraftRegisties`.

## Agent Workflow Tips

- Read nearby registry, handler, and screen code before introducing a new pattern.
- Check whether similar functionality already exists in another weapon, packet, or container class.
- Validate the narrowest useful command after changes.
- If you add tests, also update this file with the exact command for running them.
- If you discover new repository conventions, append them here instead of keeping them implicit.
