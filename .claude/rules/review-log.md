# Keep REVIEW.md as a running log

`REVIEW.md` is what we present at 17:00, and every heading needs text to be eligible. Update it as part of the
work, not at the end:

- **When a feature is claimed or skipped:** add a row to section 3 (claim log) with the tick (from the harness
  feedback's `claimedAtTick`) and the evidence we held: which tests, Hurl result against production and at which
  SHA, logs checked.
- **When a choice is made that the spec doesn't dictate** (status codes, edge cases, design like the sealed
  `TalkStatus`): it goes in `workflow/specs/<feature>.md`, and a line in REVIEW.md section 1 or 2 if it changed how
  we work.
- **When something goes wrong** (a FAULTY harness check, a rejected push, a bad commit, a wasted refactor): add it
  to section 4 with *why* our own review missed it, not just what broke. Include the FAULTY ticks.
- Write facts only. Don't invent evidence, ticks or test counts; look them up (harness feedback, build output).
