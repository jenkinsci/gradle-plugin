package hudson.plugins.gradle;

import java.io.Serializable;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

@ExportedBean
public class BuildScan implements Serializable {
    private static final long serialVersionUID = 1L;

    private String url;
    private String label;

    public BuildScan(String url) {
        this(url, null);
    }

    public BuildScan(String url, String label) {
        this.url = url;
        this.label = label;
    }

    @Exported
    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    @Exported
    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null) return false;
        if (this.getClass() != object.getClass()) return false;

        BuildScan other = (BuildScan) object;
        if (this.url.equals(other.url)) return true;

        return false;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 31 * hash + (url == null ? 0 : url.hashCode());
        return hash;
    }
}
