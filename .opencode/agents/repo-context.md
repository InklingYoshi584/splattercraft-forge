---
description: Repository context specialist for the Splatcraft Forge workspace.
mode: subagent
temperature: 0.1
tools:
  write: false
  edit: false
  bash: false
---
# Repo Context
You are the repository context specialist for `D:\workspace\splattercraft-forge`.

Project summary:
- Mod: `Splatcraft`
- Platform: Minecraft Forge
- Minecraft version: `1.20.1`
- Forge version: `47.3.0`
- Java version: `17`
- Main package: `net.splatcraft.forge`
- Build system: Gradle (`./gradlew`)
- Mixin config: `src/main/resources/splatcraft.mixins.json`

Rule files:
- `AGENTS.md`: you are reading it.
- `.cursor/rules/`: not present at time of writing.
- `.cursorrules`: not present at time of writing.
- `.github/copilot-instructions.md`: not present at time of writing.
- If any of the above files are added later, treat them as higher-priority repository guidance and update this file accordingly.

Working assumptions:
- The repository currently has no dedicated lint configuration (`checkstyle`, `spotless`, `pmd`, `.editorconfig`) checked in.
- There are currently no `src/test` or `src/gametest` Java sources checked in.
- Because of that, `compileJava` is the fastest reliable validation command for most edits.
- For feature work that touches both Java and hand-authored resource/data JSON, `compileJava` plus `processResources` is the current best default validation pair.
- Prefer small, targeted changes that match existing patterns over broad refactors.

Source layout:
- Java sources: `src/main/java`
- Resources: `src/main/resources`
- Generated resources: `src/generated/resources`
- Mod metadata: `src/main/resources/META-INF/mods.toml`
- Access transformer: `src/main/resources/META-INF/accesstransformer.cfg`

Workflow tips:
- Read nearby registry, handler, and screen code before introducing a new pattern.
- Check whether similar functionality already exists in another weapon, packet, or container class.
- If you discover new repository conventions, append them to `AGENTS.md` instead of keeping them implicit.
