package io.github.micfabian.snapito.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

class SnapitoPluginTest {
  @Test
  void registersSnapshotWorkflowTasks() {
    Project project = ProjectBuilder.builder().build();
    project.getPluginManager().apply("java");
    project.getPluginManager().apply(SnapitoPlugin.class);

    org.gradle.api.tasks.testing.Test verify =
      (org.gradle.api.tasks.testing.Test) project.getTasks().getByName("verifySnapito");
    org.gradle.api.tasks.testing.Test update =
      (org.gradle.api.tasks.testing.Test) project.getTasks().getByName("updateSnapito");
    org.gradle.api.tasks.testing.Test cleanup =
      (org.gradle.api.tasks.testing.Test) project.getTasks().getByName("cleanObsoleteSnapito");
    org.gradle.api.tasks.testing.Test reportMissing =
      (org.gradle.api.tasks.testing.Test) project.getTasks().getByName("reportMissingSnapito");
    org.gradle.api.tasks.testing.Test index =
      (org.gradle.api.tasks.testing.Test) project.getTasks().getByName("indexSnapito");

    assertNotNull(verify);
    assertEquals("true", verify.getSystemProperties().get("snapito.failOnMissing"));
    assertEquals("true", update.getSystemProperties().get("snapito.snapshot.update"));
    assertEquals("true", cleanup.getSystemProperties().get("snapito.snapshot.clean"));
    assertEquals("true", reportMissing.getSystemProperties().get("snapito.reportMissing"));
    assertEquals("true", index.getSystemProperties().get("snapito.writeIndex"));
  }
}
