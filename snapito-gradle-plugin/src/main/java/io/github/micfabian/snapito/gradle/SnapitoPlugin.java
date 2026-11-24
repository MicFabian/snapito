package io.github.micfabian.snapito.gradle;

import java.util.Map;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.testing.Test;

/** Adds isolated Gradle test tasks for verifying, updating, and cleaning Snapito snapshots. */
public final class SnapitoPlugin implements Plugin<Project> {
  @Override
  public void apply(Project project) {
    project.getPluginManager().withPlugin("java", ignored -> {
      SourceSet test = project.getExtensions().getByType(SourceSetContainer.class)
        .getByName(SourceSet.TEST_SOURCE_SET_NAME);

      registerTestTask(project, test, "verifySnapito",
        "Verifies snapshots and fails when a baseline is missing",
        Map.of("snapito.snapshot.update", "false", "snapito.failOnMissing", "true"));
      registerTestTask(project, test, "updateSnapito",
        "Updates snapshots for the selected tests",
        Map.of("snapito.snapshot.update", "true"));
      registerTestTask(project, test, "cleanObsoleteSnapito",
        "Runs snapshot tests and removes unreferenced snapshots",
        Map.of("snapito.snapshot.update", "false", "snapito.snapshot.clean", "true"));
      registerTestTask(project, test, "reportMissingSnapito",
        "Writes every missing baseline and reports them without failing on mismatches",
        Map.of("snapito.snapshot.update", "false", "snapito.reportMissing", "true"));
      registerTestTask(project, test, "indexSnapito",
        "Runs snapshot tests and writes the snapshot provenance index",
        Map.of("snapito.snapshot.update", "false", "snapito.writeIndex", "true"));
      registerReviewTasks(project);
    });
  }

  private static void registerReviewTasks(Project project) {
    Object configured = project.findProperty("snapito.snapshot.dir");
    java.io.File root = project.file(configured != null ? configured.toString() : "src/test/resources/snapshots");
    project.getTasks().register("reviewSnapito", task -> {
      task.setGroup("verification");
      task.setDescription("Writes an HTML report of pending snapshot changes to build/reports/snapito/index.html");
      java.io.File report = new java.io.File(project.getLayout().getBuildDirectory().get().getAsFile(),
        "reports/snapito/index.html");
      task.getOutputs().file(report);
      task.getOutputs().upToDateWhen(ignored -> false);
      task.doLast(ignored -> {
        java.util.List<SnapshotReview.Pending> pending = SnapshotReview.scan(root.toPath());
        try {
          java.nio.file.Files.createDirectories(report.toPath().getParent());
          java.nio.file.Files.writeString(report.toPath(), SnapshotReview.renderHtml(pending));
        } catch (java.io.IOException e) {
          throw new java.io.UncheckedIOException(e);
        }
        project.getLogger().lifecycle("{} pending snapshot change(s), report: {}", pending.size(), report);
        pending.forEach(item -> project.getLogger().lifecycle("  {} {}", item.created() ? "new    " : "changed", item.name()));
      });
    });
    project.getTasks().register("acceptSnapito", task -> {
      task.setGroup("verification");
      task.setDescription("Accepts pending snapshot changes selected with -Psnapito.accept=<glob|all>");
      task.getOutputs().upToDateWhen(ignored -> false);
      task.doLast(ignored -> {
        Object selector = project.findProperty("snapito.accept");
        if (selector == null) {
          throw new org.gradle.api.GradleException(
            "Select what to accept with -Psnapito.accept=<glob> or -Psnapito.accept=all (see reviewSnapito)");
        }
        int accepted = SnapshotReview.accept(root.toPath(), selector.toString());
        project.getLogger().lifecycle("Accepted {} snapshot change(s)", accepted);
      });
    });
  }

  private static void registerTestTask(
      Project project,
      SourceSet sourceSet,
      String name,
      String description,
      Map<String, String> properties) {
    project.getTasks().register(name, Test.class, task -> {
      task.setGroup("verification");
      task.setDescription(description);
      task.setTestClassesDirs(sourceSet.getOutput().getClassesDirs());
      task.setClasspath(sourceSet.getRuntimeClasspath());
      task.useJUnitPlatform();
      properties.forEach(task::systemProperty);
      forwardProperty(project, task, "snapito.updateOnly");
      forwardProperty(project, task, "snapito.writeIndex");
      forwardProperty(project, task, "snapito.reportMissing");
      forwardProperty(project, task, "snapito.snapshot.dir");
      task.getOutputs().upToDateWhen(ignored -> false);
    });
  }

  private static void forwardProperty(Project project, Test task, String name) {
    Object value = project.findProperty(name);
    if (value != null) {
      task.systemProperty(name, value.toString());
    }
  }
}
