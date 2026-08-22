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
