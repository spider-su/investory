package com.smartbox.investory.config;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.stereotype.Component;

@Component
public class BuildMetadata {
  private static final DateTimeFormatter FOOTER_FORMAT =
      DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
          .withLocale(Locale.ENGLISH)
          .withZone(ZoneId.systemDefault());

  private final BuildProperties buildProperties;
  private final GitProperties gitProperties;
  private final String configuredCommit;
  private final String configuredBranch;

  public static BuildMetadata development() {
    return new BuildMetadata((BuildProperties) null, (GitProperties) null, "", "");
  }

  @Autowired
  public BuildMetadata(
      ObjectProvider<BuildProperties> buildProperties,
      ObjectProvider<GitProperties> gitProperties,
      @Value("${app.build.commit:}") String configuredCommit,
      @Value("${app.build.branch:}") String configuredBranch) {
    this(
        buildProperties.getIfAvailable(),
        gitProperties.getIfAvailable(),
        configuredCommit,
        configuredBranch);
  }

  BuildMetadata(
      BuildProperties buildProperties,
      GitProperties gitProperties,
      String configuredCommit,
      String configuredBranch) {
    this.buildProperties = buildProperties;
    this.gitProperties = gitProperties;
    this.configuredCommit = configuredCommit;
    this.configuredBranch = configuredBranch;
  }

  public String displayText() {
    if (buildProperties == null || buildProperties.getTime() == null) return "Development build";
    String commit = commit();
    return "Build: "
        + FOOTER_FORMAT.format(buildProperties.getTime())
        + (commit.isBlank() ? "" : " · " + commit);
  }

  public boolean available() {
    return buildProperties != null && buildProperties.getTime() != null;
  }

  public String builtAt() {
    return buildProperties == null || buildProperties.getTime() == null
        ? ""
        : FOOTER_FORMAT.format(buildProperties.getTime());
  }

  public String commit() {
    String commit = configuredCommit;
    if (commit == null || commit.isBlank()) {
      commit = gitProperties == null ? "" : gitProperties.get("git.commit.id.abbrev");
    }
    return commit == null ? "" : commit;
  }

  public String branch() {
    String branch = configuredBranch;
    if (branch == null || branch.isBlank()) {
      branch = gitProperties == null ? "" : gitProperties.get("git.branch");
    }
    return branch == null ? "" : branch;
  }
}
