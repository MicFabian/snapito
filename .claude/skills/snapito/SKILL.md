---
name: snapito
description: Write JUnit 5 snapshot tests with Snapito, covering both the values a system under test produces and the interactions it has with its Mockito collaborators. Use when adding or changing tests that assert a large or structured result (JSON, XML, HTML, CSV, PNG, binary), when a test would otherwise need a long wall of assertEquals or verify(...) calls, when a snapshot mismatches and needs diagnosing or updating, or when a snapshot is unstable because of timestamps, ids, or ordering. Do not use for small scalar assertions, which are clearer as a plain assertEquals.
---

# Snapshot testing with Snapito

Snapito records a reviewed baseline on disk and compares later runs against it. It snapshots two things:
the **values** code produces, and the **interactions** code has with its Mockito mocks.

Reach for it when the expected result is large or structured. A single scalar is clearer as `assertEquals`.

## Setup

```groovy
testImplementation 'io.github.micfabian:snapito:1.0.0'
```

Compile tests with `-parameters`, or `@SnapshotKey` cannot use real parameter names:

```groovy
tasks.withType(JavaCompile).configureEach { options.compilerArgs << '-parameters' }
```

Register the extension per class, or globally through
`junit.jupiter.extensions.autodetection.enabled=true` in `src/test/resources/junit-platform.properties`:

```java
@ExtendWith(SnapitoExtension.class)
class OrderServiceTest { }
```

The extension supplies the snapshot file name from the test class and method, tracks parameterized
iterations, and cleans obsolete baselines. **Without it, snapshots fall back to a stack-trace-derived
name** — always register it.

## Value snapshots

```java
Snapito.expect(service.fetch());
```

The format is detected automatically: JSON, XML, HTML, multi-row CSV, text, arrays, PNG, binary. Anything
else is stored as canonical JSON. Force a format when detection would guess wrong:

```java
Snapito.expect(payload, Comparisons.JSON);
Snapito.expect(page, Comparisons.HTML);
Snapito.expect(imageBytes, Comparisons.PNG);
```

Name a snapshot when a test writes more than one, otherwise they get numeric suffixes that shift when
you reorder assertions:

```java
Snapito.expectNamed("response", response);
Snapito.expectNamed("audit-trail", auditTrail);
```

## Interaction snapshots

Prefer this over a wall of `verify(...)` calls. It captures method, arguments, order, return values, and
thrown exceptions in one reviewable file:

```java
LedgerGateway ledger = RecordingMocks.mock(LedgerGateway.class);
AuditLog audit = RecordingMocks.mock(AuditLog.class);

new PaymentService(ledger, audit).book("acc-1", 250);

Snapito.expectInteractions(ledger, audit);
```

Use `RecordingMocks.mock(...)` rather than `Mockito.mock(...)` when return values matter — it attaches the
listener that records them. Plain Mockito mocks still work, but record only method and arguments.

`verify(...)` is still the better tool for asserting one specific call in isolation. Interaction snapshots
are for capturing a whole collaboration.

## Making snapshots stable

A snapshot containing a timestamp, generated id, or unordered collection will fail on the next run. Fix the
snapshot, never the production code:

```java
var comparison = Comparisons.json(json -> json
  .excludingPaths("$.request.id", "$.items[*].createdAt")
  .excludingProperties("traceId")
  .excludingTypes(Instant.class)
  .unordered("$.roles")
  .sortedBy("$.items", "sku")
  .replacing("[0-9a-f]{8}-[0-9a-f-]{27}", "<uuid>")
  .within(0.01));

Snapito.expect(response, comparison);
```

For interactions:

```java
Snapito.expectInteractions(
  Interactions.configured(i -> i
    .ignoringMethods("toString", "hashCode")
    .replacing("acc-[0-9]+", "<account>")
    .unordered()),
  ledger);
```

The normalized form is what gets written, so excluded values never appear in the file and never create
review noise.

**The shared constants are immutable.** `Comparisons.JSON.excludingProperties("id")` throws, because it
would change every test in the JVM. Derive one instead:

```java
var derived = Comparisons.JSON.with(json -> json.excludingProperties("id"));
```

## Reading a failure

A mismatch names the differing path and both values:

```
Snapshot mismatch for .../books-a-payment.json
Rerun with -Dsnapito.snapshot.update=true to update this snapshot
Differences (1):
 - $.amount expected 250, but was 300
```

It also writes `*.actual` and `*.diff.txt` next to the baseline, plus `*.diff.png` for pixel comparisons.
These are deleted once the snapshot matches again.

**A mismatch is a question, not a chore.** Read the diff and decide:

- The change is a bug → fix the code, leave the baseline alone.
- The change is intended → update the baseline and review the diff in code review, as you would any change.

Never update a baseline to make a red test green without reading what changed. That is the one failure mode
that makes snapshot testing worthless.

## Updating baselines

```bash
./gradlew test -Dsnapito.snapshot.update=true      # whole run
./gradlew updateSnapito --tests '*OrderServiceTest*'   # one test class
```

In code, for a single call or block:

```java
Snapito.updateSnapshot(result);
Snapito.withUpdate(() -> Snapito.expect(result));
```

Updates are blocked in CI unless `-Dsnapito.allowUpdateInCi=true` is set explicitly.

## CI behaviour

A missing baseline is created locally but **fails in CI**, because a snapshot nobody reviewed asserts
nothing. Commit baselines alongside the code they describe. `snapshot()` honours the same guard.

## Several snapshots in one test

```java
Snapito.verifyAll(session -> {
  session.json("response", response);
  session.interactions("ledger", ledger);
  session.png("chart", chartBytes);
});
```

Every snapshot is evaluated and all failures reported together, instead of stopping at the first.

## Parameterized tests

Iterations get `-iteration-1`, `-iteration-2` by default. That is positional and breaks when cases are
reordered, so prefer a key:

```java
@ParameterizedTest
@CsvSource({"EUR, 5", "USD, 7"})
@SnapshotKey("currency")
void booksAPayment(String currency, int amount) {
  Snapito.expect(convert(currency, amount));
}
```

writes `books-a-payment-currency-eur.json` and `books-a-payment-currency-usd.json`.

## Getting the value back

`Snapito.snapshot(...)` asserts exactly like `expect(...)` — same diff, artifacts, and CI guard — and
additionally returns the compared value: the recorded value on a first run, the reviewed baseline
afterwards. It fails on a mismatch rather than returning a stale value.

## Layout

```
src/test/resources/snapshots/<package>/<class-kebab>/<test-kebab>.ext
```

Cleanup of unreferenced baselines runs only when a class ran **all** its tests, so a filtered run
(`--tests 'X.oneMethod'`) never deletes baselines belonging to tests it did not execute.

## Gradle tasks

Available via `id 'io.github.micfabian.snapito'`:

- `verifySnapito` — fails on a missing baseline
- `updateSnapito` — updates only the selected tests
- `cleanObsoleteSnapito` — removes unreferenced baselines
- `reportMissingSnapito` — writes and reports every missing baseline
- `indexSnapito` — writes the snapshot provenance index

## Configuration

Common properties, all prefixed `snapito.`:

| Property | Default | Meaning |
|---|---:|---|
| `snapshot.dir` | `src/test/resources/snapshots` | Baseline root |
| `snapshot.update` | `false` | Rewrite baselines |
| `failOnMissingInCi` | `true` | Missing baseline fails CI |
| `allowUpdateInCi` | `false` | Permit updates in CI |
| `updateOnly` | *(empty)* | Restrict updates to matching names |
| `writeActual` / `writeDiff` | `true` | Review artifacts on mismatch |

`Snapito.configure(...)` changes JVM-global state. Call it from a fixture that applies to every test, not
from one test that runs alongside others.
