# Co-speakers: decisions and plan

Spec: harness `/specs/co-speakers/spec.md` (v1, drip feature, released tick 0; copy in
`co-speakers-spec.md`). Not claimed yet (tick 17).

## API

- `PUT /talks/{id}/co-speakers/{speakerId}` (no body) → 200 Talk
- `DELETE /talks/{id}/co-speakers/{speakerId}` → 200 Talk
- Every Talk (`GET /talks`, `GET /talks/{id}`, `POST /talks`, `GET /speakers/{id}/talks`, the PUT/DELETE
  above) has `coSpeakerIds`: ids in the order they were added, `[]` when none.

## Decisions (the spec is silent)

| Case | Answer |
|---|---|
| Talk doesn't exist (PUT/DELETE) | 404 |
| Speaker doesn't exist (PUT/DELETE) | 404 |
| Non-numeric id in path | 400 (Spring default) |
| Add someone already a co-speaker | 200, talk unchanged, keeps original position |
| Add a third co-speaker | 409 |
| Add the main speaker as co-speaker | 400 (was 409; harness check-2 rejected it, tick 57) |
| Remove someone who isn't a co-speaker (but exists) | 404 (was 200; harness check-1 rejected it, tick 57) |
| Remove then re-add | goes to the end of the list |
| Concurrent PUTs on the same talk | never more than 2: the talk row is locked (`FOR UPDATE`) |

Unchanged: `GET /speakers/{id}/talks` and `GET /talks?speakerId=` list only talks where the speaker is
the main speaker; the 3-talk limit counts only main-speaker talks.

Error format: Spring's default `{status, error, message, path}`, as elsewhere.

## Storage

New table `talk_co_speaker (id SERIAL, talk_id, speaker_id, PRIMARY KEY (talk_id, speaker_id))`; order
by `id`. Added at the bottom of `schema.sql` with `IF NOT EXISTS`.

## Tests

`CoSpeakersTest` (Testcontainers, real app on a random port): new talk has `[]`; add two in order;
idempotent add; third → 409; main speaker → 400; unknown talk/speaker → 404; remove; remove
non-co-speaker → 404; re-add goes last; co-speakers visible in `GET /talks` and `GET /talks/{id}`;
co-speaker's own talk list excludes it; speaker at 3 talks can still co-speak; concurrent PUTs of
5 speakers → exactly 2 co-speakers.

`examples/co-speakers.hurl`: happy path + the error cases.
