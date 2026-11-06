# REVIEW

> **Showcase brief (17:00, 4 minutes, hard stop).** You screen-share this repo from the shared
> presenter laptop, in a random order drawn at 17:00. No slides, and no harness screens.
>
> 1. **Our workflow in one minute:** section 1, pointing at the files in `workflow/` and `.claude/`.
> 2. **One story:** a bug our review caught before the harness did, or one the harness caught that
>    we missed, and why. Use the claim log.
> 3. **One artifact we'd reuse**, opened on screen.
>
> Keep this file as a running log from t = 0, so 17:00–17:05 is for tidying, not writing. To be
> eligible to win, every heading below needs some text. The comments under each heading are prompts
> and don't count; replace them with your own words.

## 1. Workflow

From spec to claim, per feature (one person driving Claude Code, teammates on parallel PRs):

1. **Fetch the spec** from the harness and keep a copy next to our notes: `workflow/specs/<feature>-spec.md`.
2. **Write the decisions down before code**: `workflow/specs/<feature>.md` (API, rules, a table of every case the
   spec is silent on and the status code we chose, concurrency, storage, tests). Examples:
   [acceptance](workflow/specs/acceptance.md), [workshop registration](workflow/specs/workshop-registration.md),
   [co-speakers](workflow/specs/co-speakers.md).
3. **Tests first** (red, then green): a plain-JUnit test of the rules and a Testcontainers test against the real
   app, including a concurrency test (e.g. 30 registrations at once → exactly 20 × 201).
4. **Rules as plain Java**, no Spring or DB: `TalkStatus` (sealed interface, one record per state, illegal
   transitions don't compile), `WorkshopRules`. Services only lock, load, ask the rules, write.
5. **Our own Hurl example** per feature (`examples/<feature>.hurl`), run locally against the jar.
6. **Push to `main`**: the deploy workflow runs the tests first and only deploys if they pass
   (`.github/workflows/deploy.yml`).
7. **Check production** before claiming: `/health` shows the new SHA, `examples/run.sh $TEAM_URL` is green, and
   the logs (`.github/fagdag/logs.sh`) show the harness traffic with no errors.
8. **Claim** (a person, on the harness), then read the check feedback; a FAULTY check's transcript tells us the
   request and our answer. Fix test-first, and record the change in the decisions table as
   "was X; harness check-N rejected it, tick T".

Agent instructions live in `.claude/rules/` (loaded every session), e.g.
[keeping this file up to date](.claude/rules/review-log.md) and [status code conventions](.claude/rules/status-codes.md).

## 2. How we reviewed agent output

- **Tests as the gate.** Every feature: rules unit tests + integration tests on a real Postgres, and the deploy
  workflow refuses to deploy on a red build. Teammates added tests that pin existing behaviour before refactors
  (`ResponseShapesTest`, `TalkListQueriesTest`), so refactors that change JSON or SQL output fail.
- **Hurl examples** against the deployed URL after every deploy (all files, not just the new one).
- **A person reviewing design, not just output.** The driver pushed back on the agent's first version twice: "move
  the logic into its own class so it can be tested without integrations", and "it's a state machine, aren't
  sealed classes good for this?" Both made the code better (`AcceptanceRules` → sealed `TalkStatus`).
- **Production logs** after each deploy: counted status codes per route and checked for `ERROR` lines, and checked
  which instance (old or new) served the 404s.
- **The harness transcripts** after claiming were the final reviewer, and caught what we got wrong (section 4).
- Not done: a separate review agent on the feature PRs. Feature commits went straight to `main` after a green build.

## 3. Claim log

|Feature                 |Claimed or skipped|Tick|Evidence before claiming                                                                                                                                                                                                       |
|------------------------|------------------|----|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
|Speakers, Talks, Ratings|claimed (base)    |0   |base features, shipped with the app                                                                                                                                                                                            |
|Co-speakers             |claimed           |56  |`CoSpeakersTest` (incl. 6 concurrent adds → exactly 2), `examples/co-speakers.hurl`                                                                                                                                            |
|Spring Boot 4           |claimed           |73  |upgrade PR by a teammate, full build green                                                                                                                                                                                     |
|Acceptance              |claimed           |77  |`TalkStatusTest` (34 cases, every cell of the transition table), `AcceptanceTest` (15, incl. concurrent accept + reject), all 6 Hurl files green against production at `5444c5c`, logs clean. All 5 checks OK on the first run.|
|Workshop registration   |claimed           |83  |`WorkshopRulesTest` (11), `WorkshopRegistrationTest` (9, incl. a rush of 30 → exactly 20), all 7 Hurl files green against production at `45f178e`, logs clean. check-2 failed, see section 4.                                  |
|Scheduling              |in progress       |    |                                                                                                                                                                                                                               |

## 4. What the harness caught that we didn't

Both misses are the same mistake: answering **404 for something that exists but isn't eligible**.

- **Co-speakers, check-1 and check-2 (FAULTY ticks 57–63, again at 72).** We answered 409 for adding the main
  speaker as their own co-speaker (harness wanted 400) and 200 for removing someone who wasn't a co-speaker
  (harness wanted 404). Our decisions table had picked these without a rule behind them.
- **Workshop registration, check-2 (FAULTY ticks 84–94, about 10 points bled).** Registering for an *accepted
  lightning talk* got 404 "not a workshop" from us; the harness wanted 409, the same as for a workshop that isn't
  accepted. After that fix the check got one step further and failed on GET of the same talk's registrations: we
  said 404, it wanted 200 `[]`. Two deploys, one per step, because a check stops at its first failure.
- **Why we missed them:** we reasoned from REST purity ("`/workshops/{id}` of a non-workshop doesn't exist"), and
  our own tests encoded our choice, so they couldn't disagree with us. The spec says nothing; the harness treats an
  existing-but-ineligible thing like any other rule violation. Now written down as a convention
  (`.claude/rules/status-codes.md`).
- **Our own process slips (caught before the harness):** an IDE hook auto-stages new files, so a test for a class we
  had just deleted got into a commit; caught by reading `git diff --cached` before pushing, fixed in a follow-up
  commit. A push was rejected because teammates had merged meanwhile; we rebased and re-ran the full build before
  pushing again.
- **Deploys cost points too.** At ticks 103 and 114 (the deploys of PR #7 and scheduling) checks in five features
  failed on their very first request, `POST /speakers`, with no response at all (`status: null`): the instance swap,
  not a bug. Start-up on Scaleway took 18–27 s. We cut start-up with a Java 25 AOT cache built in the Dockerfile
  (locally at 1 CPU: 4.0 s → 1.8 s), and we batch fixes into fewer deploys. Not every deploy blips: PR #10's didn't.

## 5. Reusable artifacts

- **Decisions file per feature** (`workflow/specs/<feature>.md`): the "spec is silent" table with our answer per
  case, and a history column-in-text ("was X; check-N rejected it, tick T"). Makes the status codes reviewable and
  the harness feedback traceable.
- **Rules as a sealed state machine** (`src/main/java/no/scienta/cfp/TalkStatus.java`, test `TalkStatusTest.java`):
  per-state capabilities so illegal moves don't compile, exhaustive `switch` for requests that arrive as data.
- **Concurrency tests that fire N requests at once** and assert the exact count of successes
  (`WorkshopRegistrationTest.rushNeverOverfills`, `CoSpeakersTest.concurrentAddsNeverExceedTwo`).
- **Behaviour-pinning tests before a refactor** (`ResponseShapesTest`, `TalkListQueriesTest`).
- **Request logging with bodies** (`RequestLoggingFilter.java`): the only way to see what checks send.
- **Agent rules** in `.claude/rules/` (status-code conventions, keeping REVIEW.md as a running log).

## 6. One thing we'd do differently

Agree on a status-code convention for the whole team at t = 0 (what's 400 vs 404 vs 409, and that listing an
existing id returns 200 `[]`) instead of deciding per feature. Both harness failures, and about 20 bled points,
came from the same per-feature call. A close second: claim drip features sooner, since every tick a working
feature sits unclaimed is points we don't earn (acceptance was released at tick 20 and claimed at 77).

