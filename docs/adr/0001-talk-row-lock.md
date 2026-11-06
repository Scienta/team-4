# 1. Every change to a talk locks the talk row

Status: accepted

Several requests can change the same talk at once: two committee members deciding, a speaker withdrawing
while the committee accepts, two co-speakers being added when one seat is left, a rush of attendees for the
last workshop seats. Each of these reads something (the status, the co-speakers, the registration count),
checks a rule, and writes. Without serialisation two requests can both pass the check and together break the
rule (three co-speakers, 21 registrations, a decision on a talk that was just withdrawn).

So every change to a talk, its co-speakers or its workshop registrations starts its transaction with
`SELECT … FROM talk WHERE id = ? FOR UPDATE` (`TalkDao.lock`), and only then reads and checks. Requests on the
same talk run one after the other, and each sees the result of the one before; requests on different talks
don't wait for each other. We use the row lock rather than advisory locks because advisory locks are not
guaranteed on the platform's database (AGENTS.md). Submitting a talk locks the speaker row instead, for the
three-talk limit, and ratings need no lock: one upsert per reviewer and talk.
