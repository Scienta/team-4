# Change requests: preparation (before the specs are out)

Roadmap (tick 154): **Change request: ratings** at tick 167; **Change request: submission cap** at tick 180,
touching co-speakers and acceptance. Specs are 404 until then.

Why it's urgent: these change features we've already claimed, so their checks will likely test the new rules from
the release tick, and we bleed every tick until the change is deployed. Plan: fetch the spec the moment it's out,
decide, test first, push with `DEPLOY`.

## Where the rules live today

**Ratings** (`ratings` spec v1):
- score 1–5, whole number: `Score.parse`
- reviewer id positive: `RatingService.rate`
- one rating per reviewer per talk, re-rating replaces, concurrent-safe: `RatingDao.save` (one upsert)
- average to one decimal, halves up; none without ratings: `RatingSummary.average()`
- ranking (`?sort=rating`): comparator in `TalkService`; the `/admin/stats` average is cut off instead
  (`Utils.roundRating`, CFP-61), pinned by tests
- `ratedAtDisplay` format: `Utils.usShort`
- acceptance needs ≥ 2 distinct reviewers: `TalkStatus.MIN_REVIEWERS_TO_ACCEPT`, `RatingDao.reviewers`

**Submission cap** (`talks` spec v1: at most 3 talks per speaker, also under a rush):
- the number: `Submission.MAX_TALKS_PER_SPEAKER`
- the count and the race: `TalkDao.insertIfBelow` locks the speaker row, then inserts only if
  `COUNT(*) FROM talk WHERE speaker_id = ?` is below the max. Only main-speaker talks count today; co-speaking doesn't
  (`CoSpeakersTest.coSpeakingDoesNotCountAsOwnTalks`).
- Likely changes, given it touches co-speakers and acceptance: co-speaker talks counting, and/or withdrawn or
  rejected talks not counting. If co-speaking counts, adding a co-speaker (`CoSpeakerService`) must also respect the
  cap, and it must lock the co-speaker's speaker row too (lock order: talk row, then speaker row).
