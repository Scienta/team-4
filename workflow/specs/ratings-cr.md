# Change request: ratings — decisions and plan

Spec: harness `/specs/ratings-cr/spec.md` (v1, change request, released tick 167; copy in `ratings-cr-spec.md`).
Changes how `averageRating` is calculated, and so `GET /talks?sort=rating`. Nothing else about ratings changes.

## Rule (in `RatingSummary.average()`)

- Fewer than 5 ratings: every score counts. 5 or more: leave out exactly one highest and one lowest score (even if
  several reviewers gave that score), i.e. `(sum − min − max) / (count − 2)`.
- 2 decimals, ties at the third decimal to the even neighbour (banker's rounding, `RoundingMode.HALF_EVEN`):
  `25/8 = 3.125 → 3.12`, `27/8 = 3.375 → 3.38`. Exact decimal division of the integer sum, so no floating-point
  error decides a tie.
- `ratingCount` still counts every reviewer. No ratings: no average (`null`).
- The ranking compares the new rounded average (unchanged comparator, it reads `averageRating`).

## Decisions (the spec is silent)

| Case | Answer |
|---|---|
| `/admin/stats` `averageRating` | unchanged rule (mean of the talks' averages, cut off to 1 decimal, CFP-61); it now averages the new talk averages |
| `/admin/talks.csv` rating column | the new average, decimal comma: `4,25` |
| Storage | none new: `RatingDao.summaries` also selects `MIN(score)` and `MAX(score)`, still one query for all talks |

## Tests

`RatingSummaryTest` (plain JUnit): under 5, exactly 5, duplicates at top and bottom, and banker's ties (eighths with
10 ratings). `ConferenceServiceTest`: averages through the service, and the ranking using the trimmed average.
`TalkListQueriesTest` pins updated: talk 2 (5,4,4,4) is now `4.25` (was `4.3`).
