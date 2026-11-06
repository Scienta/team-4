# Change request: submission cap — decisions

Spec: harness `/specs/submission-cap-cr/spec.md` (v1, change request, released tick 180; copy in
`submission-cap-cr-spec.md`). Written as a guess at tick 178 and **checked against the released spec at tick 181:
every rule below matches it** (co-speaking counts; rejected and withdrawn talks don't, for anyone on them; taking
someone off as co-speaker frees their place; at the limit you can't submit or be added to a talk still in the
running, but can be added to a rejected or withdrawn one; must hold under concurrent submissions and invitations;
nothing existing is removed; a speaker's list still shows only their own talks). The submit 409 keeps its old
message, "a speaker can submit at most 3 talks".

## Rules

1. A speaker is still capped at **3** talks (`Submission.MAX_TALKS_PER_SPEAKER`).
2. **Co-speaking counts.** A talk counts for every speaker on it: the main speaker and each co-speaker.
3. **Only live talks count.** `REJECTED` and `WITHDRAWN` talks don't count, so rejecting or withdrawing a talk
   frees a slot for everyone on it. `SUBMITTED`, `WAITLISTED` and `ACCEPTED` count.

## API

No new endpoints. Changed behaviour:

- `POST /talks`: 409 if the main speaker is already on 3 live talks (as main speaker or co-speaker).
- `PUT /talks/{id}/co-speakers/{speakerId}`: 409 if the talk is live and the speaker is already on 3 live talks.

## Decisions (our guess is silent too)

| Case | Answer |
|---|---|
| Submit with 3 live talks (any mix of main and co-speaker) | 409 (unchanged code) |
| Submit with 3 talks of which one is rejected or withdrawn | 201 |
| Add a co-speaker who is on 3 live talks | 409 (same as other "not eligible" cases, `.claude/rules/status-codes.md`) |
| Add a co-speaker who is on 3 live talks, to a rejected or withdrawn talk | 200: that talk doesn't count, so the cap isn't passed |
| Add someone who is already a co-speaker of this talk, while they're at the cap | 200, unchanged (idempotent, nothing is added) |
| Order of checks on PUT co-speaker | talk 404 → speaker 404 → main speaker 400 → duplicate 200 → 2 co-speakers 409 → cap 409 |
| `GET /speakers/{id}/talks`, `GET /talks?speakerId=` | unchanged: main-speaker talks only |
| Talks already over the cap when this is deployed | left alone; the speaker just can't add more until under 3 |

## Concurrency

Every write that can raise a speaker's count locks that speaker's row (`SELECT … FOR UPDATE`) before counting:

- `POST /talks` locks the main speaker's row (as before).
- `PUT co-speaker` locks the talk row, then the co-speaker's row (lock order talk → speaker; nothing locks
  speaker → talk, so no deadlock).

Counts only go down without the speaker lock (reject and withdraw are terminal), which can't break the cap.

## Tests

`SubmissionCapTest` (Testcontainers): co-speaking counts against submitting; co-speaking counts against being added
as co-speaker; rejected and withdrawn talks free a slot; adding to a rejected talk is allowed at the cap; idempotent
add at the cap; concurrent submit + co-speaker add for a speaker at 2 never gets them to 4.
`CoSpeakersTest.coSpeakingDoesNotCountAsOwnTalks` changed: the cap part moved here, the listing part kept.
