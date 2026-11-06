# Status codes for cases the spec doesn't cover

Learned from harness failures (REVIEW.md section 4). Use these unless a spec or an earlier decision in
`workflow/specs/` says otherwise:

- **400**: the request itself is malformed or breaks a rule about its own content (missing or wrong-typed field,
  invalid email, the main speaker as their own co-speaker).
- **400** also for an id in the *body* that doesn't exist (as `POST /talks` with an unknown `speakerId`).
- **404**: the thing in the *path* doesn't exist (unknown talk id, unknown speaker id, removing something that
  isn't there).
- **409**: the thing exists but isn't eligible right now: wrong status, wrong kind (a non-workshop talk for workshop
  registration), full, duplicate, already somewhere else. Not 404, even if a REST purist would say the sub-resource
  "doesn't exist".
- **200 `[]`** when listing for an id that exists but has nothing to list (registrations of a non-workshop talk).
- **403**: the caller is identified and isn't allowed (someone other than the main speaker withdrawing).

When a harness check rejects a code: fix it test-first and record it in the feature's decisions table as
"was X; harness check-N rejected it, tick T".
