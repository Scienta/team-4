# Domain model

How the code maps the domain language of [GLOSSARY.md](../GLOSSARY.md). The rules live in plain Java types
(no Spring, no database); services load, lock and save around them. Decisions per feature are in
`workflow/specs/`.

## Domain types

```mermaid
classDiagram
    class Speaker {
        <<record>>
        long id
        String name
        String email
        String bio
        MAX_BIO = 500
        check(name, email, bio)$
    }
    class Submission {
        <<record>>
        String title
        Format format
        Level level
        long speakerId
        MAX_TALKS_PER_SPEAKER = 3
        of(TalkForm)$ Submission
    }
    class Talk {
        <<record>>
        long id
        String title
        String format
        String level
        long mainSpeakerId
        int durationMinutes
        TalkStatus status
        Instant submittedAt
        isWorkshop() boolean
    }
    class TalkStatus {
        <<sealed interface>>
        decide(Decision, reviewers) TalkStatus
        withdraw(owner, requester) Withdrawn
    }
    class Decision {
        <<enum>>
        ACCEPTED
        REJECTED
        WAITLISTED
    }
    class Format {
        <<enum>>
        WORKSHOP 120
        PRESENTATION 45
        LIGHTNING_20 20
        LIGHTNING_10 10
    }
    class Level {
        <<enum>>
        BEGINNER
        INTERMEDIATE
        ADVANCED
    }
    class CoSpeakers {
        <<record>>
        long mainSpeakerId
        List~Long~ speakerIds
        MAX = 2
        mustAdd(speakerId) boolean
        requireCoSpeaker(speakerId)
    }
    class Score {
        <<record>>
        int value
        parse(Object)$ Score
    }
    class RatingSummary {
        <<record>>
        int count
        long total
        average() Double
    }
    class WorkshopRules {
        CAPACITY = 20
        register(Talk, registered, alreadyRegistered)$
    }
    class TalkView {
        <<record>>
        of(Talk, coSpeakerIds, RatingSummary)$ TalkView
    }
    class Refused {
        <<exception>>
        Kind kind
    }

    Submission --> Format
    Submission --> Level
    Submission ..> Talk : becomes
    Talk --> TalkStatus
    TalkStatus ..> Decision
    Speaker "1" <-- "*" Talk : main speaker
    CoSpeakers ..> Talk : beside
    RatingSummary ..> Talk : beside
    Score ..> RatingSummary : summed into
    WorkshopRules ..> Talk
    TalkView ..> Talk
    TalkView ..> CoSpeakers
    TalkView ..> RatingSummary
```

- A `Talk` keeps format, level and duration as stored: older rows may hold values today's rules don't
  accept. New talks come from a `Submission`, which only takes a valid `Format` and `Level`.
- Co-speakers and ratings live in their own tables. `CoSpeakers` and `RatingSummary` are built beside the
  talk when needed, and `TalkView` joins all three into the API's talk.
- Any rule that says no throws `Refused` with a kind: `INVALID`, `NOT_FOUND`, `NOT_PERMITTED` or
  `NOT_ALLOWED`. The message is what the client sees.

## Talk status

Implemented by `TalkStatus`: one record per state, and each state only has the moves it allows.

```mermaid
stateDiagram-v2
    [*] --> SUBMITTED : submission
    SUBMITTED --> WAITLISTED : waitlist
    SUBMITTED --> ACCEPTED : accept (2+ reviewers)
    WAITLISTED --> ACCEPTED : accept (2+ reviewers)
    SUBMITTED --> REJECTED : reject
    WAITLISTED --> REJECTED : reject
    SUBMITTED --> WITHDRAWN : withdraw (main speaker)
    WAITLISTED --> WITHDRAWN : withdraw (main speaker)
    ACCEPTED --> WITHDRAWN : withdraw (main speaker)
    REJECTED --> [*]
    WITHDRAWN --> [*]
```

Accepting needs ratings from at least two different reviewers. Any move not drawn is refused as
`NOT_ALLOWED` (409), and anyone but the main speaker withdrawing is `NOT_PERMITTED` (403), whatever the state.

## Tables

From `src/main/resources/schema.sql`. There are no foreign keys; the relations below are kept by the code.

```mermaid
erDiagram
    speaker ||--o{ talk : "main speaker of"
    speaker ||--o{ talk_co_speaker : "co-speaker in"
    talk ||--o{ talk_co_speaker : has
    talk ||--o{ rating : has
    talk ||--o{ workshop_registration : has

    speaker {
        serial id PK
        text name
        text email "unique on lower(email)"
        text bio
        text created "dd.MM.yyyy"
    }
    talk {
        serial id PK
        text title
        text abstract "never exposed"
        text format
        integer duration "from format, read by the reporting tool"
        text level
        text status "default SUBMITTED"
        integer speaker_id "main speaker"
        bigint submitted_at "epoch millis"
    }
    talk_co_speaker {
        serial id "order added"
        integer talk_id PK
        integer speaker_id PK
    }
    rating {
        integer talk_id PK
        integer reviewer_id PK
        integer score "1 to 5"
        text rated_at "yyyy-MM-dd HH:mm:ss UTC"
    }
    workshop_registration {
        serial id PK
        integer talk_id
        text name
        text email "unique per talk on lower(email)"
        bigint registered_at "epoch millis"
    }
```

The legacy `vote` table (2019 voting, replaced by ratings) is still created but not used by the code.

## Layers

```mermaid
flowchart LR
    client([HTTP client]) --> controllers[Controllers]
    controllers --> services[Services]
    services --> domain[Domain rules<br/>Talk, TalkStatus, Submission,<br/>CoSpeakers, Score, WorkshopRules, Speaker]
    services --> daos[DAOs]
    daos --> pg[(Postgres)]
    domain -. throws Refused .-> errors[ApiErrors]
    services -. throws Refused .-> errors
    errors -. "400 / 404 / 403 / 409" .-> client
```

Controllers only map HTTP to service calls. Services own the transaction, take the talk row lock where
needed, load what the rules need, call them and save. `ApiErrors` turns a `Refused` into the status code with
`sendError`, so the error body is Spring's default `{timestamp, status, error, message, path}`.

## A workshop rush

Every change to a talk, its co-speakers or its registrations locks the talk row first
([ADR 1](adr/0001-talk-row-lock.md)). Two attendees going for the last seat:

```mermaid
sequenceDiagram
    participant A as Attendee A
    participant B as Attendee B
    participant S as WorkshopService
    participant DB as Postgres

    A->>S: POST /workshops/7/registrations
    B->>S: POST /workshops/7/registrations
    S->>DB: (A) SELECT talk 7 FOR UPDATE
    DB-->>S: (A) locked
    S->>DB: (B) SELECT talk 7 FOR UPDATE
    Note over DB: B waits for A's lock
    S->>DB: (A) count = 19, email not registered
    Note over S: WorkshopRules.register: room for one
    S->>DB: (A) INSERT registration, COMMIT
    S-->>A: 201 Created
    DB-->>S: (B) locked
    S->>DB: (B) count = 20
    Note over S: WorkshopRules.register: full, Refused
    S->>DB: (B) ROLLBACK
    S-->>B: 409 the workshop is full (20 attendees)
```
