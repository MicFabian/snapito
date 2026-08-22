package io.github.micfabian.snapito.environment;

import java.util.List;
import java.util.Map;

public final class ContinuousIntegration {
  private static final List<String> CI_VARIABLES = List.of(
    "CI",
    "GITHUB_ACTIONS",
    "GITLAB_CI",
    "JENKINS_URL",
    "BUILD_NUMBER",
    "TF_BUILD",
    "BUILDKITE");

  private ContinuousIntegration() {
  }

  public static boolean isCi() {
    String override = System.getProperty("snapito.ci");
    if (override != null) {
      return override.equalsIgnoreCase("true");
    }
    Map<String, String> environment = System.getenv();
    return CI_VARIABLES.stream().anyMatch(environment::containsKey);
  }
}
