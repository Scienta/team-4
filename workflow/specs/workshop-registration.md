# Workshop registration: decisions and plan

Spec: harness `/specs/workshop-registration/spec.md` (v1, drip feature, released tick 67; copy in
`workshop-registration-spec.md`). Not claimed yet. Depends on acceptance (`acceptance.md`).

## API

- `POST /workshops/{id}/registrations` body `{"name", "email"}` → 201 Registration
- `GET /workshops/{id}/registrations` → 200 list of Registration, in the order they registered
- Registration: `{id, workshopId, name, email}`, email as the attendee typed it.

## Rules

- Only a talk with format `WORKSHOP` and status `ACCEPTED` (at the moment of registering) takes registrations.
- At most 20 per workshop, never more, also under a rush.
- One per email per workshop, case-insensitive (`lower(email)`). Same email on other workshops is fine.
- Registrations stay when the workshop is withdrawn later; the list still shows them.

## Decisions (the spec is silent)

| Case | Answer |
|---|---|
| Talk doesn't exist (POST and GET) | 404 |
| POST for a talk that isn't a `WORKSHOP` | 409 (was 404; harness check-2 rejected it, tick 84) |
| GET for a talk that isn't a `WORKSHOP` | 200 `[]` (was 404; harness check-2 rejected it, tick 89) |
| Non-numeric id in path | 400 (Spring default) |
| Body missing, name or email missing/blank | 400 |
| Email not a valid email (same check as speakers) | 400 |
| Workshop not `ACCEPTED` (SUBMITTED, WAITLISTED, REJECTED, WITHDRAWN) | 409 |
| Workshop full (20 registered) | 409 |
| Email already registered for this workshop (any case) | 409 |
| GET on a workshop in any status (incl. not accepted, withdrawn) | 200, possibly `[]` |
| Order of checks | 400 (body) → 404 (workshop) → 409 (not open → duplicate → full) |
| Name/email stored | as sent (not trimmed or lower-cased); comparison uses `lower(email)` |

Error format: Spring's default `{status, error, message, path}`, as elsewhere.

## Concurrency

The POST locks the talk row (`SELECT … FOR UPDATE`), then reads status, count and duplicate, then
inserts, all in one transaction. A rush of registrations for one workshop is serialised on that row,
and so is a concurrent withdrawal or decision (they lock the same row). Backstop: a unique index on
`(talk_id, lower(email))`; a violation becomes 409.

## Code

`WorkshopRules` is plain Java (no Spring, no database): given the talk's format, its `TalkStatus`, how
many are registered and whether the email is already there, it either allows the registration or throws
`Refused` with a reason (NOT_A_WORKSHOP / NOT_OPEN / DUPLICATE / FULL). `WorkshopService` locks, loads,
asks the rules, inserts, and maps every reason to 409.

## Storage

New table `workshop_registration (id SERIAL PRIMARY KEY, talk_id, name, email, registered_at)` + unique
index `(talk_id, lower(email))`. Ordered by `id`. Added at the bottom of `schema.sql` with `IF NOT EXISTS`.

## Tests

`WorkshopRulesTest` (plain JUnit): every refusal reason, the 20 limit, order of refusals.

`WorkshopRegistrationTest` (Testcontainers, real app): register and list in order; email case-insensitive
duplicate → 409; same email on two workshops; 400s; 404 unknown talk and non-workshop; 409 for
SUBMITTED / WAITLISTED / REJECTED; withdrawn keeps its list but takes no more; 21st → 409; a rush of 30
concurrent registrations → exactly 20 × 201 and 10 × 409, list has 20.

`examples/workshop-registration.hurl`: happy path + the error cases.
