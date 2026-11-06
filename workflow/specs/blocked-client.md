# Blocking a flooding client

A scripted client (office IP 195.1.11.215, `curl/8.5.0`) hammered the deployed service ~5/s, creating speakers,
talks, ratings and — the damage — scheduling talks into the shared programme. The programme is 3 rooms x 5 slots =
15 cells (one global grid, by spec); when the flood holds a cell the harness's scheduling and programme-export
checks get a correct 409 and fail. Everything else is keyed by id, so only the programme collides.

## What we did

`BlockedClientFilter` rejects (403) any request whose `User-Agent` starts with a prefix in `cfp.block.user-agents`
(comma-separated; set in `application.properties`, overridable by the env var of the same name). `/health` is always
exempt so the platform probe can't be throttled. The active value is `curl/8.5.0`, the exact flood.

## Why it's narrow and temporary

- Blocking by User-Agent is **spec-visible**: the spec lets any client call the API, so a real client sending a
  listed agent is wrongly turned away. The harness uses `Java-http-client`, so it is not blocked *today* — but if a
  check ever used `curl`, we'd fail it and it would look like a bug. Keep the list to the exact flood.
- It is **evadable**: the flood can change its agent. If it does, this stops working and we stop the source instead.
- **Empty the list the moment the flood stops** (`cfp.block.user-agents=` in application.properties, or the env var):
  with no prefixes it blocks nobody.

## The real fix

The flood is from our own office IP, so the clean separation is at the source: point that script at a local
instance (`docker compose up` + the jar), not the deployed URL. This filter is a stopgap, not that fix.
