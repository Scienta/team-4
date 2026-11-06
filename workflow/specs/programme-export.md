# Programme export: decisions and plan

Spec: harness `/specs/programme-export/spec.md` (v1, drip feature, released tick 133; copy in
`programme-export-spec.md`). Not claimed yet. Read-only views of scheduling (`scheduling.md`).

## API

- `GET /programme` → 200 JSON list of `{slot, start, end, room, talkId, title, format, speakers}`
- `GET /programme.csv` → 200 `text/csv; charset=UTF-8`, header `slot,start,end,room,talkId,title,format,speakers`

## Rules

- One row per programme entry, nothing else; the same rows in the same order in both.
- Order: slot by start time (S1 … S5), then room (R1, R2, R3).
- Talk fields and speaker names as they are *now* (not when the talk was placed).
- Speakers: main speaker's name first, then co-speakers in the order they were added. CSV: one field, joined
  with `"; "`.
- CSV: RFC 4180, `\n` line endings, every line ends with `\n` (the header too), no blank line at the end.
  A field is quoted only if it has a comma, a double quote, `\r` or `\n`; quotes inside are doubled.
  Otherwise written as is. Empty programme: just the header line.
- `text/csv` even when the client sends `Accept: application/json` (no 406).

## Decisions (the spec is silent)

| Case | Answer |
|---|---|
| A withdrawn (or otherwise changed) talk still on the programme | listed, with its current title, format and speakers ("everything on the programme") |
| `format` | as stored on the talk |
| `Content-Type` of the CSV | `text/csv; charset=UTF-8` |
| `Content-Disposition` | none (not asked for; the print shop fetches the URL) |
| Leading `=`, `+`, `-`, `@` in a title (spreadsheet formulas) | written as is: the spec says "otherwise written as they are" |
| `GET /programme.csv/` or `/programme/` | same as without the slash (the app-wide trailing-slash filter) |

## Code

Pure Java, no Spring or DB: `ProgrammeRow` (the JSON record, `of(entry, talk, speaker names)`) and
`ProgrammeCsv.write(rows)`. `ProgrammeService.export()` loads entries, their talks, co-speakers and names in a
few queries and sorts. `ProgrammeController` gets the two GETs.

## Tests

`ProgrammeCsvTest` (plain JUnit): header only when empty; exact bytes for a few rows; quoting of comma, quote,
CR, LF; no quoting otherwise; speakers joined with `"; "`; UTF-8 names. `ProgrammeExportTest` (Testcontainers):
order across slots and rooms; JSON and CSV agree; speakers main-first then co-speakers in order; current title
and names after a change; withdrawn talk still listed; `text/csv` with `Accept: application/json`; empty programme.

`examples/programme-export.hurl`.
