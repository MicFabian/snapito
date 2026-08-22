# Snapito

Snapshot testing for JUnit 5 and Mockito. Records a reviewed baseline on disk and compares later runs
against it, for both the **values** code produces and the **interactions** code has with its Mockito mocks.

This file tells an agent how to *use* the library. To work on the library itself, read `README.md` and the
sources under `src/main/java/io/github/micfabian/snapito/`.

## When to reach for it

Use a snapshot when the expected result is large or structured — a JSON payload, a rendered document, a
collaboration across several mocks. A single scalar is clearer as a plain `assertEquals`, so do not
snapshot one.

## Setup

```groovy
testImplementation 'io.github.micfabian:snapito:1.0.0'
tasks.withType(JavaCompile).configureEach { options.compilerArgs << '-parameters' }
```

`-parameters` matters: without it `@SnapshotKey` produces `arg0`, `arg1` instead of real parameter names.

Register the extension per class, or globally via
`junit.jupiter.extensions.autodetection.enabled=true` in `src/test/resources/junit-platform.properties`:

```java
@ExtendWith(SnapitoExtension.class)
class OrderServiceTest { }
```

It supplies the snapshot file name, tracks parameterized iterations, and cleans obsolete baselines.
Without it, naming falls back to a stack-trace guess.

## Value snapshots

```java
Snapito.expect(service.fetch());                 // format detected automatically
Snapito.expect(payload, Comparisons.JSON);       // or forced
Snapito.expectNamed("response", response);       // name when a test writes several
```

Detected formats: JSON, XML, HTML, multi-row CSV, text, arrays, PNG, binary. Everything else becomes
canonical JSON.

## Interaction snapshots

Prefer these over a long series of `verify(...)` calls — they capture method, arguments, order, return
values, and thrown exceptions in one reviewable file:

```java
LedgerGateway ledger = RecordingMocks.mock(LedgerGateway.class);
new PaymentService(ledger).book("acc-1", 250);
Snapito.expectInteractions(ledger);
```

Use `RecordingMocks.mock(...)` when return values matter; it attaches the listener that records them.
Plain `Mockito.mock(...)` works but captures only method and arguments. Keep `verify(...)` for asserting
one specific call in isolation.

## Keeping snapshots stable

Timestamps, generated ids, and unordered collections make a snapshot fail on every run. Normalize the
snapshot rather than changing production code:

```java
var comparison = Comparisons.json(json -> json
  .excludingPaths("$.request.id", "$.items[*].createdAt")
  .excludingTypes(Instant.class)
  .unordered("$.roles")
  .replacing("[0-9a-f]{8}-[0-9a-f-]{27}", "<uuid>")
  .within(0.01));

Snapito.expect(response, comparison);
```

For interactions use `Interactions.configured(i -> i.ignoringMethods(...).replacing(...).unordered())`.

The shared constants are immutable: `Comparisons.JSON.excludingProperties("id")` throws, because it would
affect every test in the JVM. Derive with `Comparisons.JSON.with(json -> ...)` instead.

## Handling a mismatch

The failure names the differing path and both values, and writes `*.actual` and `*.diff.txt` beside the
baseline:

```
Differences (1):
 - $.amount expected 250, but was 300
```

Decide what the diff means before doing anything:

- Unintended change → fix the code, leave the baseline.
- Intended change → update the baseline, and review the diff like any other change.

**Never update a baseline just to turn a test green.** An unreviewed baseline asserts nothing.

```bash
./gradlew test -Dsnapito.snapshot.update=true
./gradlew updateSnapito --tests '*OrderServiceTest*'
```

Updates are blocked in CI unless `-Dsnapito.allowUpdateInCi=true` is set explicitly.

## CI

A missing baseline is created locally but fails in CI, by design. Commit baselines together with the code
they describe.

## Useful extras

```java
Snapito.verifyAll(session -> {            // all failures reported together
  session.json("response", response);
  session.interactions("ledger", ledger);
});
```

```java
@ParameterizedTest
@CsvSource({"EUR, 5", "USD, 7"})
@SnapshotKey("currency")                  // stable names instead of -iteration-1
void booksAPayment(String currency, int amount) { }
```

`Snapito.snapshot(...)` asserts exactly like `expect(...)` and additionally returns the compared value.

## Layout

```
src/test/resources/snapshots/<package>/<class-kebab>/<test-kebab>.ext
```

Cleanup of unreferenced baselines runs only when a class ran all its tests, so a filtered run never deletes
baselines belonging to tests it did not execute.
