# Deploying: only when a commit says DEPLOY

A push to `main` runs the tests, but only deploys if one of its commits has the word `DEPLOY` (capitals) in
its message (`.github/workflows/deploy.yml`, job `gate`). For a PR, put `DEPLOY` in the PR title: the merge
commit carries it. A manual run of the workflow always deploys.

Why: every swap of instances can fail a tick of harness checks across all features (REVIEW.md section 4), so
we deploy less often, with more in each deploy.

- **Add `DEPLOY`** when production must change now: a fix for a FAULTY harness check (every tick it stays
  faulty bleeds points), or a feature ready to be claimed.
- **Leave it out** for docs, `REVIEW.md`, `workflow/`, `.claude/`, refactors and anything that can wait for the
  next deploy. Those still go out with the next commit that says `DEPLOY`, since a deploy builds all of `main`.
- After pushing with `DEPLOY`, check that `/health` shows the new SHA before telling anyone it's live. Without
  `DEPLOY`, say plainly that production is unchanged.
