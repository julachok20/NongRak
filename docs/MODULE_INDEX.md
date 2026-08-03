# NongRak Android — module & read index (for AI / humans)

**Goal:** Open only the folders that match the task so context stays small.

**Gradle modules (real):** `:app`, `:core:navigation`, `:core:model`, `:core:common`  
**Logical modules (same repo, still under `:app`):** doctor UI, pedia-connect, parent child, data layer — until Phase 2 `:core:data` lands.

---

## Gradle dependency graph

```mermaid
flowchart BT
  app["`:app` — UI, VM, data, nav wiring"]
  nav["`:core:navigation` — Screen routes"]
  model["`:core:model` — data classes"]
  common["`:core:common` — pure utils"]
  app --> nav
  app --> model
  app --> common
  common --> model
```

**Build:** `JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"` then `.\gradlew.bat :app:compileDebugKotlin`

**Cross-module rule:** Types from `:core:model` are not smart-cast across modules — copy to a local `val` before use (e.g. `log.notes.orEmpty()`).

---

## Where code lives

| Scope | Gradle | Path |
|--------|--------|------|
| Routes / `Screen.*` | `:core:navigation` | `LullabyAI/core/navigation/.../Screen.kt` |
| Domain models | `:core:model` | `LullabyAI/core/model/.../data/model/` |
| Shared utils (no Compose) | `:core:common` | `LullabyAI/core/common/.../util/` |
| Nav graph wiring | `:app` | `app/.../ui/navigation/AppNavigation.kt` |
| Repositories + remote | `:app` | `app/.../data/repository/`, `data/remote/` |
| Supabase SQL (repo) | root | `Child-Project/.../supabase/migrations/` (see MEMORY_GRAPH) |
| Doctor screens + VMs | `:app` (logical `:feature:doctor`) | `app/.../ui/screens/doctor/`, `viewmodel/Doctor*.kt` |
| Pedia / parent booking | `:app` | `app/.../ui/screens/doctorconnect/` |
| Child / health / nutrition | `:app` | `app/.../ui/screens/child/`, `nutrition/` |
| Access / roles / QR | `:app` | `app/.../ui/screens/access/` |
| Design system | `:app` | `app/.../ui/theme/`, `ui/components/` |
| Stitch HTML (read-only) | cache | `LullabyAI/.stitch-cache/` — **never edit**; implement in Kotlin |

**Phase 2 (not done):** extract `app/.../data/` → `:core:data`, then `:feature:doctor` + `:core:ui` again.

**Stale dirs:** `core/ui/`, `feature/doctor/`, `core/data/` may still exist on disk without `settings.gradle` includes — ignore; source of truth is `:app`.

---

## Task → read first

| If you are working on… | Read first (in order) |
|------------------------|------------------------|
| New route / deep link | `Screen.kt` → `AppNavigation.kt` (one owner for both) |
| Doctor auth / dashboard / slots | `doctor/*Screen.kt`, `Doctor*ViewModel.kt`, `DoctorRepository.kt` |
| Pedia register / book / approve | `doctorconnect/*`, `DoctorPatientRegistration*`, `DoctorRepository.kt`, Supabase pedia tables in MEMORY_GRAPH |
| Child permissions / roles | `access/`, `AccessRole*`, migrations BATCH W in MEMORY_GRAPH |
| Nutrition hub | `nutrition/NutritionScreen.kt`, `NutritionViewModel.kt`, `NutritionRepository.kt` |
| Sync / QR child profile | `access/SyncChildProfileScreen.kt`, `AccessQrScanner.kt`, `ChildRepository.kt` |
| AI chat / avatar | `chat/`, `AiProxyClient.kt`, `AiAvatar.kt` |
| Compile / Gradle | `settings.gradle.kts`, `app/build.gradle.kts`, `core/*/build.gradle.kts` |
| Stitch fidelity | `tools/stitch.ps1` → `.stitch-cache/*.html` → target Kotlin screen |

---

## Graphify (code graph — primary reader)

**User preference (2026-07-29):** use Graphify to understand the project before wide grep/repo scans.

- **Latest graph:** `LullabyAI/graphify-out/graph.json` · report: `graphify-out/GRAPH_REPORT.md`
- **From `LullabyAI/`:** see `~/.claude/skills/graphify/SKILL.md`

| When | Command |
|------|---------|
| How does X work? | `graphify query "How does …?"` |
| Link A ↔ B | `graphify path "A" "B"` |
| Explain a node | `graphify explain "NodeName"` |
| After big structural changes | `graphify . --update` |

Use Graphify for call paths and god nodes; use **MEMORY_GRAPH + this file** for task status and product decisions.

---

## Token budget hints

- **Small change (one screen):** that screen + its ViewModel + relevant repository only.
- **Do not** load all of `app/.../ui/screens/` unless doing a cross-cutting audit.
- **Models only:** `:core:model` (~single `Models.kt` + a few files) instead of whole app.
