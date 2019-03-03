package hudson.plugins.gradle;

public interface BuildScanPublishedListener {
    void onBuildScanPublished(String scanUrl);
}
