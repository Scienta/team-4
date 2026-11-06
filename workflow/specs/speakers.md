# Speakers: gap analysis and plan

Spec: harness `/specs/speakers/spec.md` (v1, base feature, claimed from tick 0; a copy is in `spec1.md`).
At planning time (tick 11) the harness reported speakers check-1..4 OK, so the work is closing gaps
between spec and code **without breaking what passes**.

## Spec rules vs the deployed service (probed 2026-11-06 ~11:38 UTC)

| Spec rule | Observed | Gap? |
|---|---|---|
| Register, read back exactly as registered (mixed-case email, `Ådne Øst`) | 201 → identical `GET` | ok |
| Bio optional | 201, `"bio": null` | ok |
| Real name: blank / missing | 400 | ok (`"name": 123` accepted as `"123"`) |
| **Real email** | `not-an-email` 201, `a@b` 201, `"  x@ex.com "` 201 stored padded | **no email validation on POST** |
| Bio ≤ 500: 500 / 501 chars | 201 / 400 | ok |
| Bio ≤ 500 *characters* (emoji) | 300 emoji → 400 (Java counts UTF-16 units) | yes |
| One email = one speaker, any case | 409; unique index on `lower(email)` covers races | ok |
| Survives restart | Postgres | ok (no test) |
| Trailing slash `GET /speakers/{id}/`, `POST /speakers/` | 200 / 201 / 404 | ok (Spring 5.3 default; would break on Boot 3) |
| Unknown id / non-numeric id / malformed JSON / no body | 404 / 400 / 400 / 400 | ok |

Outside the spec, but threatening "registered speakers stay registered":

- **SQL injection** in `SpeakerDao.search` (`/speakers/search?q=`), concatenated SQL.
- `PUT /speakers/{id}` allows a 1000-char bio and NPEs (500) on a missing name/email.

## Decisions (the spec is silent)

1. **Email format:** 400 unless `local@domain.tld` with no whitespace. Reject rather than trim:
   trimming contradicts "exactly as they registered it", and storing padded emails breaks
   one-email-one-speaker.
2. **Characters = Unicode code points**, so an emoji counts as one.
3. **Non-string name** (`123`): leave as is; not worth the risk to passing checks.
4. **Error format:** Spring's default `{status, error, message, path}`, unchanged.
5. `PUT /speakers/{id}` follows the same rules as `POST` (name, email, bio ≤ 500), 400 otherwise.

## Tests

`SpeakersTest` (Testcontainers, real app on a random port):

- register → `GET` returns identical fields (mixed-case email, non-ASCII name); bio omitted → null; `""` round-trips
- name missing / blank → 400
- email missing / blank / `not-an-email` / `a@b` / `" a@b.no"` / `"a b@c.no"` → 400; valid mixed-case → 201
- bio 500 → 201, 501 → 400, 500 emoji → 201
- duplicate any case → 409 and the first speaker unchanged; concurrent race → one 201 (existed)
- `GET` unknown → 404, `/speakers/abc` → 400
- trailing slash `POST /speakers/`, `GET /speakers/{id}/` (incl. 404)
- search with injection payloads matches only literally, table still there
- `PUT` with missing name/email → 400, bio 501 → 400

`SpeakerPersistenceTest`: register, close the app context, start a fresh one on the same database, `GET` → 200.

`examples/speakers-edge.hurl`: the 400/409/404/trailing-slash cases, run against local and `TEAM_URL`.

## Steps

1. Failing tests first.
2. Fix: email validation (`Utils.isValidEmail` + `@ValidEmail` on `SpeakerForm`), code-point bio
   length, parameterised search, `PUT` uses the same rules.
3. `./mvnw package` green; Hurl green locally.
4. Push; `/health` shows the new SHA; Hurl green against `TEAM_URL`; harness speakers checks still OK;
   request logs show no unexpected 400s on harness traffic.
5. Log in `REVIEW.md` §3.

**Risk:** the email rule is the one change that could turn a passing check red (e.g. the harness
registering `x@localhost`). Step 4 catches it; the request logs show the 400 immediately.
