# Assumptions

The brief leaves a number of points to the implementer's judgement. This is every decision that was
made, and why. Where a decision was reversible, it was made configurable instead.

---

## The lifecycle

**Deleting and rejecting are state transitions, not row deletions.** The diagram draws `DELETED` and
`REJECTED` as states, and the brief asks for a reason and for the complete history of state changes.
Both only make sense if the request survives. A deleted request therefore stays readable, keeps its
audit trail, and can no longer be acted on. Nothing purges it; a retention policy would be a business
decision that was not part of the brief.

**`PUBLISHED`, `REJECTED` and `DELETED` are terminal**, because the diagram gives them no outgoing
edge. Terminality is not declared anywhere: a state is terminal when no transition leaves it, so it
follows automatically from the configured lifecycle rather than from a second list that could drift.

**A reason is a property of the transition, not of the target state.** `DELETE` and `REJECT` are
marked as requiring one. A reason supplied on a transition that does not require one is accepted and
recorded rather than rejected — it is useful audit information and refusing it would help nobody.

**Assigning the publication number is likewise a property of the transition**, carried by the
`ACCEPTED → PUBLISHED` edge. This keeps the rule out of the aggregate, which would otherwise have to
hard-code the name of a state to know when to draw a number.

## Editing

**Only the body can be edited; the name is fixed at creation.** The brief restricts the editing of
the *body* and says nothing about editing the name. Freezing the name is the narrower reading and the
safer one for a regulated workflow, and the endpoint reflects it: `PUT /api/v1/requests/{id}/body`.
Allowing the name to change would be a one-line addition if the business wants it.

**Editing in a state that does not allow it is a conflict, not a validation error** — the payload is
fine, the state of the resource is what refuses it — so it answers `409`, not `400`.

## Identity and the publication number

**A request is identified by a UUID from creation**, so callers have a stable identifier long before
publication. The publication number is a *separate business identifier*, handed out only on
publication.

Where that UUID comes from is a `RequestIdGenerator` port rather than a call to
`UUID.randomUUID()` inside the service. Identity is an infrastructure decision — a random UUID today,
a client supplied idempotency key or a broker allocated identity tomorrow — and routing it through a
port is what lets a test hand the service predictable identifiers instead of asserting against
whatever the JVM produced.

**Publication numbers come from a database sequence**, starting at 1, never reused, and protected by
a unique constraint on the column. Uniqueness therefore holds across several instances of the service
rather than only within one JVM. The number is drawn only once the transition is known to be legal,
so a refused publication burns nothing, and it never changes afterwards. The statement drawing the
next value is configurable, because its syntax is vendor specific.

## The API

**`POST /{id}/delete` rather than `DELETE /{id}`.** A reason has to be supplied, and a body on an
HTTP `DELETE` is not reliably supported by intermediaries or clients. `DELETE` would also suggest the
resource disappears, which is exactly what does not happen.

**One endpoint per action** (`/verify`, `/accept`, `/publish`, `/reject`, `/delete`) rather than a
single generic `/transitions` endpoint. It documents itself, it lets the mandatory reason be part of
the payload contract, and each endpoint is a three-line delegation to the same use case — the domain
stays generic even though the API is explicit.

**Every response carries `allowedActions` and `bodyEditable`**, derived from the configured workflow,
so clients do not have to duplicate the state diagram to know what to offer next.

**Errors are RFC 7807 problem documents** and carry the machine-readable context a caller needs
(`currentState`, `allowedActions`, `editableStates`, the offending field). The API is versioned under
`/api/v1`.

**Browsing** defaults to 10 results per page as specified, caps the page size at 100 to protect the
service, sorts the most recently created first, filters on a case-insensitive fragment of the name
and on an exact state, and returns summaries without the body to keep list payloads small.

## Validation

Both optional features of the brief — the audit log and browsing — are implemented.

* `name` at most 200 characters, `body` at most 10 000, `reason` at most 500.
* Values are trimmed, and a blank string counts as missing.
* The limits are enforced by the domain *and* by bean validation on the payloads, so the same rule
  holds whichever way the application is driven. They are bounded so the database columns can be too;
  they are conservative guesses in the absence of a stated requirement.
* The audit trail records the creation of the request as its first entry, so the history is complete
  from the beginning. Entries are append-only and are never rewritten on update.

## Technical

**No authentication or authorisation.** None was asked for, and it belongs to the platform a service
like this is deployed onto. One consequence is visible in the model: audit entries record *what*
changed and *why*, but not *who* did it, because there is no authenticated principal to record. Adding
an actor to `StateChange` is where that would go.

**Concurrency.** Requests carry an optimistic lock version, so two competing transitions on the same
request produce a `409` rather than a lost update.

**The database is not part of the deliverable**, as instructed. An in-memory H2 is configured so the
service runs out of the box, the schema is owned by a Flyway migration written in SQL that runs
unchanged on H2 and PostgreSQL, and Hibernate is set to *validate* that schema rather than to create
it. Pointing `DATABASE_URL` at a real instance is all that is needed.

**Timestamps are UTC**, read from an injected `Clock` rather than from `Instant.now()`, so tests are
deterministic and every audit entry written in one transaction agrees on the time.

**Java 21 and Spring Boot 3.5** — the current long-term-support JDK and a current Spring Boot line.
The JUnit BOM is pinned one minor version above the one Spring Boot manages, because Cucumber 7.34 is
built against JUnit Platform 1.13; that is the only version override in the build.

**Dependency injection is used to the end.** Every collaborator arrives through a constructor and is
typed as an abstraction: the repository, both generators, the workflow, the clock, and — inside the
persistence and web adapters — the entity mapper, the specification factory and the response
assembler. There is no static utility class and no field injection anywhere; `ArchitectureTest`
fails the build if an `@Autowired` field appears. The cost is a handful of small interfaces that
each have one implementation today, which is a deliberate trade: it keeps every class stating what
it needs, and keeps every one of them constructible in a test without a container.

**Lombok removes boilerplate, not meaning.** It generates the injection constructors
(`@RequiredArgsConstructor`), the loggers (`@Slf4j`) and the accessors of the JPA entities. The
aggregate uses `@Accessors(fluent = true)` so its API reads like a record's, and the two accessors
that do more than return a field are still hand-written. `lombok.config` sets
`addLombokGeneratedAnnotation`, so generated members are excluded from the coverage figures instead
of inflating them.

**Formatting is a stated convention, not a matter of taste**, and it is recorded in `.editorconfig`:
one call per line once a chain does more than one thing, one argument per line when they do not fit,
and a named local variable instead of a call nested inside another call's argument list. No line in
the project exceeds 120 characters.

**The build gate.** `./mvnw verify` fails if line coverage drops below 90 %, if branch coverage drops
below 85 %, or if the ArchUnit rules on the layering and on constructor injection are broken. The
gate is deliberately part of the build rather than a convention.
