# Acceptance state machine: decisions and plan

Spec: harness `/specs/acceptance/spec.md` (v1, drip feature, released tick 20; copy in
`acceptance-spec.md`). Not claimed yet.

## API

- `PUT /talks/{id}/status` body `{"status": "ACCEPTED"|"REJECTED"|"WAITLISTED"}` → 200 Talk
- `POST /talks/{id}/withdraw` body `{"speakerId": n}` → 200 Talk
- `status` on every Talk (already there) can now be any of SUBMITTED, ACCEPTED, REJECTED, WAITLISTED, WITHDRAWN.

## Transitions

| From \ To   | ACCEPTED        | REJECTED | WAITLISTED | WITHDRAWN (speaker) |
|-------------|-----------------|----------|------------|---------------------|
| SUBMITTED   | ok if ≥2 raters | ok       | ok         | ok                  |
| WAITLISTED  | ok if ≥2 raters | ok       | 409        | ok                  |
| ACCEPTED    | 409             | 409      | 409        | ok                  |
| REJECTED    | 409             | 409      | 409        | 409                 |
| WITHDRAWN   | 409             | 409      | 409        | 409                 |

"≥2 raters" = at least two distinct `reviewerId`s in `rating` (the table's primary key already makes a
re-rating replace the old row, so `COUNT(*)` is distinct reviewers).

## Decisions (the spec is silent)

| Case | Answer |
|---|---|
| Talk doesn't exist (either endpoint) | 404 |
| Non-numeric id in path | 400 (Spring default) |
| Decision body missing, `status` missing, unknown, or `SUBMITTED`/`WITHDRAWN` | 400 |
| Decision not allowed from the current status (table above), incl. same status again | 409, talk unchanged |
| Accept with fewer than 2 distinct reviewers | 409, talk unchanged |
| Withdraw body missing / `speakerId` missing or not an integer | 400 |
| Withdraw by anyone but the talk's main speaker (incl. co-speakers, unknown speaker ids) | 403 |
| Withdraw from REJECTED or WITHDRAWN | 409 |
| Order of checks | 400 (body) → 404 (talk) → 403 (speaker) → 409 (state) |

Open question for the product owner (a person asks): may a **co-speaker** withdraw? We say no: "their own
talk" read as the main speaker.

Error format: Spring's default `{status, error, message, path}`, as elsewhere.

## Concurrency

Both endpoints lock the talk row (`SELECT … FOR UPDATE`, as co-speakers does), then read the status
and rating count, then write. Two committee members deciding at once are serialised: the second sees
the first's result and gets 409 if its transition is no longer allowed.

## Storage

None new: `talk.status` already exists with default `SUBMITTED`.

## Code

The state machine is `TalkStatus`, a sealed interface with one record per state. Capability interfaces
(`Undecided`: accept/reject, `Withdrawable`: withdraw) and `Submitted.waitlist()` mean each state only
has the moves it allows, so illegal transitions don't compile. Requests arrive as data, so
`decide(Decision, reviewers)` and `withdraw(owner, requester)` switch exhaustively over the states and
throw `Refused` (INVALID / NOT_THE_SPEAKER / NOT_ALLOWED) for what a state doesn't offer.
`ConferenceService` locks and loads the talk, calls the state machine, writes `name()`, and maps a
refusal to 400 / 403 / 409.

## Tests

`TalkStatusTest` (plain JUnit, no containers): the typed transitions, every cell of the transition
table, the reviewer threshold, decision parsing, owner checks, stored-name round trip.

`AcceptanceTest` (Testcontainers, real app on a random port): every allowed transition; every
forbidden one gives 409 and leaves the status; accept needs two distinct reviewers (one reviewer
rating twice isn't enough); 400s for bad bodies; 404 unknown talk; 403 for another speaker and a
co-speaker; status visible in `GET /talks/{id}`, `GET /talks`, `GET /speakers/{id}/talks`;
concurrent ACCEPTED + REJECTED on the same talk → exactly one 200, final status matches it.

`examples/acceptance.hurl`: happy path + the error cases.
