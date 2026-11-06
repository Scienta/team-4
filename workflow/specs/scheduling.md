# Programme scheduling: decisions and plan

Spec: harness `/specs/scheduling/spec.md` (v1, drip feature, released tick 100; copy in `scheduling-spec.md`).
Not claimed yet. Depends on acceptance and co-speakers.

## API

- `PUT /programme/{room}/{slot}` body `{"talkId": n}` → 200 Entry
- `GET /programme/{room}/{slot}` → 200 Entry
- `DELETE /programme/{room}/{slot}` → 204, no body
- Entry: `{room, slot, start, end, talkId}`, times `HH:mm` (`09:00`).

## Venue (fixed)

Rooms `R1`, `R2`, `R3`. Slots `S1` 09:00–10:00, `S2` 10:15–11:15, `S3` 11:30–12:30, `S4` 13:30–15:30 (the long
one), `S5` 15:45–16:45. Same slots in every room.

## Rules, checked when a talk is put on the programme (in this order)

1. The same talk already in this room and slot: 200, nothing changes (checked first, so it stays 200 even if the
   talk has changed since, "the programme stays as it is").
2. The talk must be `ACCEPTED`.
3. A workshop needs the long slot `S4`; every other format fits any slot, `S4` included.
4. The room and slot must be free.
5. The talk must not be on the programme anywhere else (move = DELETE, then PUT).
6. Nobody in two rooms at once: the main speaker and co-speakers of this talk must not be the main speaker or a
   co-speaker of any other talk in the same slot (any room).

Changes to a talk afterwards (withdrawn, co-speaker added) don't touch the programme.

## Decisions (the spec is silent)

Per `.claude/rules/status-codes.md`.

| Case | Answer |
|---|---|
| Unknown room or slot in the path (`R4`, `S6`, `r1`) | 404 |
| Body missing, `talkId` missing or not a whole number | 400 |
| `talkId` of a talk that doesn't exist | 400 (as POST /talks with an unknown `speakerId`) |
| Rule 2–6 broken | 409, programme unchanged |
| GET or DELETE on an empty room and slot | 404 |
| DELETE of a filled room and slot | 204 |
| Order of checks | 404 (path) → 400 (body) → rules 1–6 (409) |

## Concurrency

PUT locks the talk row (as every talk change does, docs/adr) and then the slot's row in `programme_slot`
(`SELECT … FOR UPDATE`), always in that order. So two placements in the same slot run one after the other
(room taken, speaker clash), and two placements of the same talk too (on programme once). DELETE locks only the slot.
Backstops in the schema: primary key `(room, slot)` and `UNIQUE (talk_id)`; a violation becomes 409.

## Code

Pure Java, no Spring or DB: `Room`, `Slot` (times, `isLong()`), `ProgrammeEntry`, `ProgrammeRules.place(...)`,
which returns whether the talk must be inserted (false: already there) or throws `Refused`.
`ProgrammeService` locks, loads, asks the rules, writes. `ProgrammeController` for the three endpoints.

## Storage

`programme_slot (slot PRIMARY KEY)` with the five slots inserted at start-up (`ON CONFLICT DO NOTHING`), for the
lock. `programme_entry (room, slot, talk_id UNIQUE, PRIMARY KEY (room, slot))`.

## Tests

`ProgrammeRulesTest` (plain JUnit): every rule and the order. `SchedulingTest` (Testcontainers): put/get/delete;
entry shape and times; 404 path; 400 body; 400 unknown talk; 409 not accepted / workshop outside S4 / taken /
elsewhere / speaker clash (main and co-speaker); move by delete + put; concurrent puts into one room and slot →
exactly one 200; concurrent puts of the same talk into five slots → exactly one 200; concurrent puts of two
talks sharing a speaker into the same slot → exactly one 200.

`examples/scheduling.hurl`: happy path + the error cases.
