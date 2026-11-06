# 1. Every change to a talk locks the talk row

Status: accepted

Several requests can change the same talk at once: two committee members deciding, a speaker withdrawing
while the committee accepts, the same talk being put in two slots, two co-speakers being added when one seat is left, a rush of attendees for the
last workshop seats. Each of these reads something (the status, the co-speakers, the registration count),
checks a rule, and writes. Without serialisation two requests can both pass the check and together break the
rule (three co-speakers, 21 registrations, a decision on a talk that was just withdrawn).

So every change to a talk, its co-speakers, its workshop registrations, or putting it on the programme, starts its transaction with
`SELECT … FROM talk WHERE id = ? FOR UPDATE` (`TalkDao.lock`), and only then reads and checks. Requests on the
same talk run one after the other, and each sees the result of the one before; requests on different talks
don't wait for each other. We use the row lock rather than advisory locks because advisory locks are not
guaranteed on the platform's database (AGENTS.md). Putting a talk on the programme takes the talk lock and
then the slot's row in `programme_slot`, always in that order, so placements can't deadlock. Submitting a talk locks the speaker row instead, for the
three-talk limit, and ratings need no lock: one upsert per reviewer and talk.

## Addendum: a bounded wait for the lock

A request waiting for a row lock holds one of the five pooled connections, with no upper bound on the wait: one
stuck holder, and the requests piling up behind it, could take the whole pool and starve every endpoint, `/health`
included. So every `FOR UPDATE` (the talk, the speaker, the programme slot) goes through `RowLocks.forUpdate`, which
first runs `set_config('lock_timeout', …, true)`: the `SET LOCAL` form, so the setting ends with the transaction and
never leaks to the next user of the pooled connection (AGENTS.md). It refuses to run outside a transaction, where
both the lock and the timeout would end with the statement; every caller is `@Transactional`.

The timeout is 5 s (`cfp.lock-timeout`, the tests use 500 ms). Our transactions hold a lock for milliseconds, so a
5 s wait only happens when something is wrong, and it matches the 5 s `/health` gives the database. A shorter value
would turn ordinary contention under a burst into errors; a longer one keeps connections tied up longer.

Past the timeout Postgres raises SQLSTATE 55P03. Spring 7 leaves that an `UncategorizedSQLException`, so
`RowLocks` turns it into a `CannotAcquireLockException`, and `ApiErrors` answers any `PessimisticLockingFailureException`
with **503** and `Retry-After: 1`, in the usual error body (`sendError`). Not 500: nothing broke and the transaction
rolled back with nothing written. Not 409: the request isn't in conflict with the talk's state, the same request
succeeds once the lock is free, and 409 is what our rules use for "not eligible" (`.claude/rules/status-codes.md`).
