package hudson.plugins.gradle;

import org.kohsuke.stapler.export.ExportedBean;

@ExportedBean
public class ScanDetail {

  private String projectName;
  private String buildToolType;
  private String buildToolVersion;
  private String requestedTasks;
  private String hasFailed;
  private String url;

  private ScanDetail() {}

  public String getProjectName() {
    return projectName;
  }

  public String getBuildToolType() {
    return buildToolType;
  }

  public String getBuildToolVersion() {
    return buildToolVersion;
  }

  public String getRequestedTasks() {
    return requestedTasks;
  }

  public boolean getHasFailed() {
    return "true".equals(hasFailed);
  }

  public String getUrl() {
    return url;
  }

  static class ScanDetailBuilder{

    private String projectName;
    private String buildToolType;
    private String buildToolVersion;
    private String requestedTasks;
    private String hasFailed;
    private String url;

    public ScanDetailBuilder withProjectName(String projectName) {
      this.projectName = projectName;
      return this;
    }

    public ScanDetailBuilder withBuildToolType(String buildToolType) {
      this.buildToolType = buildToolType;
      return this;
    }

    public ScanDetailBuilder withBuildToolVersion(String buildToolVersion) {
      this.buildToolVersion = buildToolVersion;
      return this;
    }

    public ScanDetailBuilder withRequestedTasks(String requestedTasks) {
      this.requestedTasks = requestedTasks;
      return this;
    }

    public ScanDetailBuilder withHasFailed(String hasFailed) {
      this.hasFailed = hasFailed;
      return this;
    }

    public ScanDetailBuilder withUrl(String url) {
      this.url = url;
      return this;
    }

    public ScanDetail build(){
      ScanDetail scanDetail = new ScanDetail();
      scanDetail.buildToolType = buildToolType;
      scanDetail.buildToolVersion = buildToolVersion;
      scanDetail.projectName = projectName;
      scanDetail.requestedTasks = requestedTasks;
      scanDetail.hasFailed = hasFailed;
      scanDetail.url = url;
      return scanDetail;
    }

  }
}

