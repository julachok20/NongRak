# Real-time Sync — one child, several accounts

Structure for making every carer of the same child see each other's work as it happens.

---

## 0. What already exists

| Channel | Table | Status |
|---|---|---|
| `care-logs:$childId` | `care_logs` insert/update/delete | ✅ live |
| `care-messages:$childId:$peerId` | `care_messages` | ✅ live |
| `presence:care:$childId` | who is currently viewing | ✅ live |
| `child-sync-requests:$ownerUserId` | access requests | ✅ live |

The pattern in `watchCareLogs` is: **subscribe → emit `Unit` on any change → the caller refetches**.
Simple, correct, and the right default. Keep it.

**What is missing:** every other record type. Sleep, milk, food, nappies, growth, vaccines, medicine,
symptoms, expenses and calendar entries do **not** push. The nanny logs a nap and the parent sees
nothing until they pull to refresh.

---

## 1. Target structure

```
                    ┌───────────────────────────────────┐
   nanny's phone ──►│ Postgres (single source of truth) │◄── parent's phone
                    │ RLS decides who may see a row     │
                    └───────────────┬───────────────────┘
                                    │ logical replication
                            ┌───────▼────────┐
                            │  Realtime      │  one channel per child
                            │  child:$id     │  many tables multiplexed
                            └───────┬────────┘
                                    │ ChildDataKind events
              ┌─────────────────────┴─────────────────────┐
              ▼                                           ▼
      ChildSyncRepository                          ChildSyncRepository
      → debounce 250 ms                            → debounce 250 ms
      → refetch only the affected screen           → refetch
      → StateFlow                                  → StateFlow
              ▼                                           ▼
          nanny's UI                                 parent's UI
```

**One channel per child, not one per table.** Supabase limits concurrent channels, and a family with
three children on one phone would otherwise open thirty subscriptions.

---

## 2. Steps, in build order

### Step 1 — Publish the tables

Realtime only emits for tables in the publication. Nothing works until this runs:

```sql
alter publication supabase_realtime add table
  sleep_logs, milk_logs, food_logs, waste_records,
  growth_records, vaccine_records, medication_doses,
  health_symptom_logs, child_expenses, child_activities;
```

### Step 2 — Make DELETE events usable

By default a delete event carries **only the primary key**. If the client needs to know which child a
deleted row belonged to in order to filter it, it cannot:

```sql
alter table sleep_logs replica identity full;   -- and each of the others
```

Without this, deletes either cannot be filtered by `child_id` or arrive as unusable stubs. This is the
most-missed step in Supabase realtime work.

### Step 3 — One typed change stream

```kotlin
enum class ChildDataKind { SLEEP, MILK, FOOD, WASTE, GROWTH, VACCINE,
                           MEDICINE, SYMPTOM, EXPENSE, ACTIVITY, CARE_LOG }

/** Emits which KIND of data changed, so a screen refetches only what it shows. */
fun watchChild(childId: String): Flow<ChildDataKind>
```

One channel, one `postgresChangeFlow` per table, all merged, each mapped to its kind. Screens already
watching `care_logs` keep working — `watchCareLogs` becomes a filter over this.

### Step 4 — Debounce, then refetch

A carer saving three records in a row must not trigger three refetches:

```kotlin
watchChild(childId)
    .filter { it in screenKinds }
    .debounce(250)
    .collect { refresh() }
```

### Step 5 — Close the reconnect gap ⚠️

**The correctness step everyone forgets.** While the phone is asleep, in a tunnel, or the socket
drops, events are *not queued* — they are lost. Reconnecting mid-stream leaves the screen silently
stale, which is worse than no sync at all, because by then the user trusts it.

So: **on every (re)subscribe, refetch once.** Treat the socket as a hint that something changed, never
as the source of truth.

### Step 6 — Show who else is here

Presence already exists. Surface it: *"นาน่ากำลังดูอยู่"* on the activity screen. Most duplicate work
disappears once people can see each other.

---

## 3. Table coverage

| Table | Kind | Watch |
|---|---|---|
| `care_logs` | CARE_LOG | ✅ exists |
| `sleep_logs` | SLEEP | add |
| `milk_logs` | MILK | add |
| `food_logs` | FOOD | add |
| `waste_records` | WASTE | add |
| `growth_records` | GROWTH | add |
| `vaccine_records` | VACCINE | add |
| `medication_doses` | MEDICINE | add |
| `health_symptom_logs` | SYMPTOM | add |
| `child_expenses` | EXPENSE | add |
| `child_activities` | ACTIVITY | add |

---

## 4. Conflicts — what actually needs solving

Care logs are **append-only events**. Two carers logging at once produce two rows, both true. There is
almost nothing to merge, which is why a CRDT layer would add machinery without adding capability
against a single authoritative Postgres.

The three real problems:

**Duplicate entries** — the actual pain. Both carers log the same 14:00 feed.
→ Server-side check on insert for a near-identical row for that child within a few minutes, returning
a warning the UI can show: *"นาน่าบันทึกนม 120ml ไปแล้วเมื่อ 5 นาทีที่แล้ว — บันทึกซ้ำหรือไม่?"*
A CRDT would faithfully converge on both feeds; only a semantic check catches this.

**Lost updates** — both edit one record, one silently overwrites.
→ Optimistic concurrency: `update … where updated_at = :seen`. Zero rows means someone got there
first, so say who and what rather than discarding their change.

**Stale view** — solved by steps 1–5 above.

---

## 5. Security

RLS applies to realtime: a carer only receives change events for rows they could already read. No
extra checks belong in the client — the same rule as everywhere else in this codebase.

Worth verifying per table after step 1, because publishing a table makes its changes flow, and RLS is
the only thing stopping them flowing to the wrong person.

---

## 6. Phases

| Phase | Work | Result |
|---|---|---|
| **1** | Steps 1–2 (SQL), step 3 for sleep/milk/food/waste | the daily record goes live |
| **2** | Steps 4–5, remaining tables | complete and gap-free |
| **3** | Presence indicator, duplicate detection | duplicate work stops happening |
| **4** | Optimistic concurrency on edits | no silent overwrites |

Phase 1 is the one people feel: the nanny saves a nap and it appears on the parent's phone.
