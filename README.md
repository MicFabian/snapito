# Snapito

Typed and structured snapshot testing for JUnit 5 and Mockito.

Snapito snapshots two things: the **values** your code produces, and the **interactions** your code has with its
collaborators. Value snapshots give automatic format detection, canonical JSON/XML/CSV/HTML storage, path-based
normalization, structured diagnostics, binary and PNG snapshots. Interaction snapshots record what was called on your
Mockito mocks, with which arguments, in which order, and what came back — as a reviewable baseline instead of a wall of
hand-written `verify(...)` calls.

Both share one engine: CI-safe baseline handling, review artifacts, and Gradle workflow tasks.

This is a JUnit 5 / Mockito counterpart to [snappo](https://github.com/MicFabian/snappo), which does the same for Spock.

## Install

```groovy
repositories {
  mavenCentral()
}

dependencies {
  testImplementation 'io.github.micfabian:snapito:1.0.0' // replace with latest
  testImplementation 'org.junit.jupiter:junit-jupiter:5.14.1'
  testImplementation 'org.mockito:mockito-core:5.20.0'
}
```

Mockito is only needed for interaction snapshots; value snapshots work without it.

Compile your tests with `-parameters` so `@SnapshotKey` can use real parameter names:

```groovy
tasks.withType(JavaCompile).configureEach {
  options.compilerArgs << '-parameters'
}
```

## Quick Start

```java
import io.github.micfabian.snapito.Snapito;
import io.github.micfabian.snapito.junit.SnapitoExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(SnapitoExtension.class)
class ApiTest {
  @Test
  void responseMatchesSnapshot() {
    var result = service.fetch();

    Snapito.expect(result);
  }
}
```

Snapito detects JSON, XML, HTML, multiline CSV, text, arrays, PNGs, and binary values. Other objects use canonical JSON.

On a local first run, a missing baseline is created. Subsequent runs compare against it. Missing baselines fail in CI by
default, because snapshots must be reviewed and committed before CI runs.

The extension supplies the test class and method name used for the snapshot file, tracks parameterized iterations, and
cleans obsolete baselines. It can also be registered globally instead of per class:

```
# src/test/resources/junit-platform.properties
junit.jupiter.extensions.autodetection.enabled=true
```

## Interaction snapshots

```java
@ExtendWith(SnapitoExtension.class)
class PaymentServiceTest {
  @Test
  void booksAPayment() {
    LedgerGateway ledger = RecordingMocks.mock(LedgerGateway.class);
    AuditLog audit = RecordingMocks.mock(AuditLog.class);

    new PaymentService(ledger, audit).book("acc-1", 250);

    Snapito.expectInteractions(ledger, audit);
  }
}
```

produces `books-a-payment.interactions.json`:

```json
[ {
  "sequence" : 1,
  "mock" : "LedgerGateway",
  "method" : "LedgerGateway.debit(String, int)",
  "arguments" : [ "acc-1", 250 ]
}, {
  "sequence" : 2,
  "mock" : "AuditLog",
  "method" : "AuditLog.log(String)",
  "arguments" : [ "debited acc-1" ]
} ]
```

`RecordingMocks.mock(...)` is a plain Mockito mock with an invocation listener attached, so return values and thrown
exceptions are recorded too. Ordinary `Mockito.mock(...)` mocks work as well — you just get method and arguments
without return values.

The `sequence` field is a per-snapshot ordinal, not Mockito's JVM-global sequence number, so it stays stable across
runs and across other tests in the same JVM. Return values are tracked per mock instance, so two mocks of the same
type never borrow each other's results, and repeated identical calls keep their own individual return values.

Shape the recording when the raw call log is too strict:

```java
Snapito.expectInteractions(
  Interactions.configured(interactions -> interactions
    .ignoringMethods("toString", "hashCode")
    .replacing("acc-[0-9]+", "<account>")
    .withoutReturnValues()
    .unordered()),
  ledger, audit);
```

- `ignoringMethods` / `onlyMethods` — filter which calls are recorded
- `replacing` — redact volatile argument and return values
- `unordered` — sort deterministically by mock, method, and arguments instead of by call order
- `withoutSequence` — keep call order but drop the ordinal from the file
- `withoutReturnValues` — record calls only
- `onlyVerified` — record only invocations already matched by a `verify(...)`
- `withQualifiedMethodNames` — use fully qualified declaring class names

Named interaction snapshots keep a stable identity:

```java
Snapito.expectInteractionsNamed("ledger-calls", ledger);
```

## Explicit comparisons

```java
import io.github.micfabian.snapito.Comparisons;

Snapito.expect(payload, Comparisons.JSON);
Snapito.expect(document, Comparisons.XML);
Snapito.expect(page, Comparisons.HTML);
Snapito.expect(csv, Comparisons.CSV);
Snapito.expect(text, Comparisons.TXT);
Snapito.expect(imageBytes, Comparisons.PNG);
Snapito.expect(bytes, Comparisons.BINARY);
```

The shared constants are immutable: calling a configuring method on `Comparisons.JSON` and friends throws, because the
change would leak into every other test in the JVM. Derive an independent instance with `Comparisons.json(...)` or
`existing.with(...)` instead.

Built-ins:

- `JSON`
- `OBJECT_AS_JSON`
- `API_RESPONSE`, excluding `id`, `createdAt`, and `lastModified`
- `CSV`
- `XML`
- `HTML`
- `TXT`
- `PNG`
- `BINARY`
- `ARRAY`
- `INTERACTIONS`

## Stable JSON snapshots

Simple exclusions:

```java
Snapito.expect(result, Comparisons.jsonExcludingProperties("id", "createdAt"));
Snapito.expect(result, Comparisons.jsonExcludingTypes(java.time.Instant.class));
Snapito.expect(result, Comparisons.jsonExcludingPaths("$.request.id", "$.items[*].createdAt"));
```

For composed normalization:

```java
var comparison = Comparisons.json(json -> json
  .excludingPaths("$.request.id", "$.items[*].createdAt")
  .unordered("$.roles")
  .sortedBy("$.items", "sku")
  .replacing("[0-9a-f]{8}-[0-9a-f-]{27}", "<uuid>")
  .within(0.01));

Snapito.expect(response, comparison);
```

Supported path syntax covers object properties, numeric indexes, and wildcards, for example `$.items[*].id`. Normalized
JSON is written to the snapshot, so ignored volatile values no longer create review noise.

Derive a variant without touching the original:

```java
var stricter = comparison.with(json -> json.excludingProperties("traceId"));
```

## PNG comparison and visual diffs

```java
import io.github.micfabian.snapito.comparison.PngComparison;

Snapito.expect(imageBytes, Comparisons.png(PngComparison.Mode.SIZE));

var pixels = new PngComparison(PngComparison.Mode.PIXEL)
  .tolerating(3, 0.001); // channel delta, allowed different-pixel ratio

Snapito.expect(imageBytes, pixels);
```

Pixel mismatches produce a red-overlay `*.diff.png` alongside the textual diff.

Pixel mode compares decoded pixel buffers directly; the stored snapshot stays an ordinary PNG file, so a full-page
screenshot costs its real size rather than an inflated text encoding.

## Review artifacts

On mismatch Snapito can write:

```text
response.json
response.json.actual
response.json.diff.txt
image.png.diff.png
```

Artifacts are removed after the snapshot matches or is updated.

```bash
-Dsnapito.writeActual=false
-Dsnapito.writeDiff=false
```

## Updating snapshots

Update the full run:

```bash
SNAPITO_UPDATE=true ./gradlew test
```

or:

```bash
./gradlew test -Dsnapito.snapshot.update=true
```

Update a single call or block:

```java
Snapito.updateSnapshot(result, Comparisons.JSON);

Snapito.withUpdate(() -> Snapito.expect(result));
```

Updates are blocked in CI unless `-Dsnapito.allowUpdateInCi=true` is explicitly configured before Snapito initializes.

### IntelliJ IDEA

Mismatch messages include an IntelliJ-specific rerun hint when IDEA is detected. Use a second run configuration or a
temporary VM option:

```text
-Dsnapito.snapshot.update=true
```

## Gradle plugin

```groovy
// settings.gradle
pluginManagement {
  repositories {
    gradlePluginPortal()
    mavenCentral()
  }
}
```

```groovy
plugins {
  id 'io.github.micfabian.snapito' version '1.0.0' // replace with latest
}
```

The plugin adds independent `Test` tasks that support Gradle's `--tests` filter:

```bash
./gradlew verifySnapito
./gradlew updateSnapito --tests '*ApiTest*'
./gradlew cleanObsoleteSnapito
```

- `verifySnapito` fails on missing baselines.
- `updateSnapito` updates only the selected snapshot tests.
- `cleanObsoleteSnapito` runs without updating mismatches and removes unreferenced baselines.

Obsolete-snapshot cleanup only runs for a test class when **every** test in that class actually executed. If you
filter a run (`--tests 'ApiTest.oneMethod'`, `@Disabled`, tag filters), cleanup is skipped for that class and the
reason is logged, so a narrow run can never delete baselines belonging to the tests it did not run.
- `reportMissingSnapito` writes every missing baseline and reports them without failing on mismatches.
- `indexSnapito` writes the snapshot provenance index.

## Named and data-driven snapshots

```java
Snapito.expectNamed("users-list", users, Comparisons.JSON);
Snapito.updateSnapshotNamed("raw-payload", payload, Comparisons.TXT);
```

Named snapshots are stable explicit identities. `@ParameterizedTest` iterations automatically receive `-iteration-1`,
`-iteration-2`, and so on. Multiple unnamed snapshots in one iteration receive numeric suffixes.

Use `@SnapshotKey` for readable, order-independent iteration names:

```java
@ParameterizedTest
@CsvSource({"EUR, 5", "USD, 7"})
@SnapshotKey("currency")
void booksAPayment(String currency, int amount) {
  Snapito.expect(convert(currency, amount));
}
```

writes `books-a-payment-currency-eur.json` and `books-a-payment-currency-usd.json`. Without a value, every parameter
becomes part of the key.

## Multiple snapshots per test

```java
Snapito.verifyAll(session -> {
  session.json("response", response);
  session.interactions("ledger", ledger);
  session.png("chart", chartBytes);
});
```

Every snapshot is evaluated, and all failures are reported together.

## Parallel execution

Snapito is safe under `junit.jupiter.execution.parallel.enabled=true`. Configuration is published as an immutable
snapshot, `Snapito.getConfig()` returns a copy rather than the live instance, and each test's naming state is
thread-confined and cleared after the test.

Note that `Snapito.configure(...)` still changes global state for the whole JVM, so call it from a fixture that
applies to every test rather than from one test that runs alongside others.

## Configuration

```java
Snapito.configure(config -> config
  .setRootPath(java.nio.file.Paths.get("src/test/resources/snapshots"))
  .setFailOnMissing(false)
  .setFailOnMissingInCi(true)
  .setAllowUpdateInCi(false)
  .setWriteActualOnMismatch(true)
  .setWriteDiffOnMismatch(true)
  .setCleanObsoleteSnapshots(true)
  .setAtomicWrites(true));
```

System properties:

| Property | Default |
|---|---:|
| `snapito.snapshot.dir` | `src/test/resources/snapshots` |
| `snapito.failOnMissing` | `false` |
| `snapito.failOnMissingInCi` | `true` |
| `snapito.allowUpdateInCi` | `false` |
| `snapito.writeActual` | `true` |
| `snapito.writeDiff` | `true` |
| `snapito.cleanObsolete` | `true` |
| `snapito.atomicWrites` | `true` |
| `snapito.reportMissing` | `false` |
| `snapito.writeIndex` | `false` |
| `snapito.updateOnly` | *(empty)* |

## Custom formats and detection

Implement `Comparison` for serialization and normalization. Implement `AdvancedComparison` when the format also supplies
custom equality, diagnostics, or binary diff artifacts.

Auto-detection is extensible through `ComparisonProvider`:

```java
public class YamlProvider implements ComparisonProvider {
  @Override
  public int priority() {
    return 100;
  }

  @Override
  public Comparison detect(Object value) {
    return looksLikeYaml(value) ? new YamlComparison() : null;
  }
}

Comparisons.register(new YamlProvider());
```

Providers may also be discovered through Java `ServiceLoader` using
`META-INF/services/io.github.micfabian.snapito.ComparisonProvider`.

## Snapshot layout

```text
src/test/resources/snapshots/<package>/<class-kebab>/<test>.ext
```

Test names are converted to kebab-case on word boundaries, including acronyms, so `booksAPaymentInEuros` becomes
`books-a-payment-in-euros.json` and `parseHTTPResponse` becomes `parse-http-response.json`.

Snapshot writes use a per-path lock, temporary file, and atomic move where the filesystem supports it.

## Build and release

```bash
./gradlew clean check :snapito-gradle-plugin:validatePlugins
./gradlew test -PtestJavaVersion=21
./gradlew test -PtestJavaVersion=25
```

Release:

```bash
./gradlew publishToMavenCentral -PreleaseVersion=1.0.0
```

Required release secrets are `SONATYPE_USERNAME`, `SONATYPE_PASSWORD`, `SIGNING_KEY`, and `SIGNING_PASSWORD`.

## License

Apache License 2.0.
