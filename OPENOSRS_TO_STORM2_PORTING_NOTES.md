# OpenOSRS -> Storm2 Porting Notes

## What We Learned (DKS port)

### 1) Library and API Compatibility Strategy
- Prefer **Storm plugin/config abstractions**:
  - `net.storm.api.plugins.Plugin`
  - `net.storm.api.plugins.PluginDescriptor`
  - `net.storm.api.plugins.config.*`
- Keep **RuneLite types where Storm still depends on them**:
  - overlay UI (`net.runelite.client.ui.overlay.*`)
  - event bus annotation (`net.runelite.client.eventbus.Subscribe`)
  - many game enums/types (`Prayer`, `GameState`, etc.)
- Use **Storm SDK wrappers/helpers** where available:
  - `net.storm.sdk.widgets.Prayers` for prayer state/toggles
  - `net.storm.sdk.widgets.Widgets`, `Tabs`
  - `net.storm.sdk.entities.Players`

### 2) Event Model Reality
- Storm has its own events for some domains (e.g. `net.storm.api.events.NpcSpawned/NpcDespawned`).
- Some loop/tick/login-state flows are still easiest via RuneLite events in this environment (`GameTick`, `GameStateChanged`).
- Mixed usage is acceptable, but keep it intentional and documented.

### 3) Critical API Mismatch Found
- `Prayer.getWidgetInfo()` was not available in this setup.
- Correct replacement was:
  - `net.storm.api.prayer.PrayerInfo.MAP.get(prayer)`
  - then widget lookup via `Widgets.get(prayerInfo.getComponent())`
- Avoid deprecated APIs when practical (we replaced `InterfaceAddress` path with `getComponent()` lookup).

### 4) NPC ID Namespace Caution
- Older OpenOSRS code may reference legacy IDs.
- In this repo/client combination, we used `net.runelite.api.gameval.NpcID` and `DAGCAVE_*` boss IDs.
- Always verify IDs against current client namespace before porting logic.

### 5) World/NPC Access Differences
- Prefer top-level world view methods where needed:
  - `client.getTopLevelWorldView().npcs()`
  - `client.getTopLevelWorldView().getMapRegions()`
- This reduced deprecation friction versus older direct client calls.

## Praying Against NPCs: Practical Pattern

### Recommended Pattern
1. Track relevant NPCs on spawn/despawn.
2. Maintain per-NPC attack metadata:
   - attack style -> prayer
   - attack animation id
   - attack speed
   - optional attack range
3. On each tick:
   - update `ticksUntilNextAnimation`
   - compute one **priority target** via a single selector method
   - derive recommended prayer from that target
4. Auto-toggle only with guards:
   - config enabled
   - `Prayers.canUse(prayer)`
   - do not retoggle if already enabled
   - cache last recommendation to avoid thrashing

### Fallback Behavior We Added
- Primary priority: soonest incoming attack (lowest positive tick), with optional "ignore non-attacking".
- Fallback: if no valid attacker candidate exists, choose nearest in-range boss using that boss's `attackRange`.

## DRY + Single Source of Truth (SSOT)

### Rule
Use **one method** to compute priority/recommended prayer, then reuse it everywhere:
- prayer widget rendering
- auto-prayer toggling
- any future HUD/notification output

### Why
- We saw mismatched behavior when infobox and widget used different selection logic.
- Divergent logic causes user-facing inconsistency and hard-to-debug prayer mistakes.

### Implementation Guideline
- Create one selector method (example: `getPriorityKingForPrayerWidget()`).
- If a second feature needs the same result, call this method instead of recomputing.
- If logic changes (new filter/fallback), update one place only.

## Build and Validation Workflow

### During Porting
- Run `.\gradlew.bat build` after each significant change.
- Fix compile issues immediately before proceeding.

### Runtime Validation Checklist
- Verify the same target/prayer is used by:
  - overlay highlight/timer
  - auto-toggle behavior
- Test edge cases:
  - no current attacker
  - multiple NPCs with different tick timings
  - entering/leaving region
  - NPC despawn while selected

## Notes for Inferno Port (next agent)

### Porting Priorities
1. Build a dependency map first (OpenOSRS imports -> Storm/RuneLite equivalents in this repo).
2. Identify all calculation-heavy selectors (e.g. target NPC, attack prediction, tile danger).
3. Centralize each core calculation in SSOT methods before adding overlays/automation.
4. Reuse Storm SDK helpers where they reduce client-specific breakage.
5. Keep mixed-event usage explicit and minimal.

### Likely Risk Areas
- projectile/animation hooks and timing
- widget/address APIs that changed from legacy RuneLite/OpenOSRS assumptions
- NPC ID/type namespace differences
- duplicated logic across overlays vs behavior modules

### Suggested Deliverables for Inferno
- short compatibility matrix (import-by-import)
- explicit list of unsupported hooks (if any)
- SSOT design note for danger/prayer calculations
- incremental build logs with checkpoints

---

## Inferno Plugin Compatibility Matrix

### RuneLite symbol -> Storm replacement

| OpenOSRS/RuneLite | Storm2 Replacement |
|-------------------|-------------------|
| `client.isPrayerActive(prayer)` | `Prayers.isEnabled(prayer)` |
| `client.getWidget(WidgetInfo.PRAYER_*)` | `PrayerInfo.MAP.get(prayer)` + `Widgets.get(prayerInfo.getComponent())` |
| `client.getNpcs()` | `client.getTopLevelWorldView().npcs()` |
| Region check via `client.getLocalPlayer().getWorldLocation()` | `client.getTopLevelWorldView().getMapRegions()` + `Arrays.stream(regions).anyMatch(r -> r == INFERNO_REGION)` |
| `NPCManager.getHealth(npc)` | `ZUK_MAX_HP = 1400` constant (OpenOSRS-specific API) |
| `WorldArea.calculateNextTravellingPoint` | `WorldArea.canTravelInDirection` custom pathfinding |
| `InfoBox` / `InfoBoxManager` | `InfernoSpawnTimerInfobox` POJO + timer text in overlay |
| `SpriteManager.getSprite(SpriteID.*)` | Text label fallback in InfernoInfoBoxOverlay (Melee/Range/Magic) |

### Kept RuneLite (required by Storm runtime)
- `net.runelite.api.*` – Client, NPC, Prayer, Perspective, Point, etc.
- `net.runelite.api.events.*` – GameTick, NpcSpawned, NpcDespawned, AnimationChanged, GameStateChanged, ChatMessage
- `net.runelite.client.eventbus.Subscribe`
- `net.runelite.client.ui.overlay.*` – Overlay, OverlayPosition, OverlayUtil, PanelComponent, etc.

