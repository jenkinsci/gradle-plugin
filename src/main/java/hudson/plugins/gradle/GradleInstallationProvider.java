package hudson.plugins.gradle;

/**
 * Interface for providing {@link GradleInstallation} independent of build type.
 * 
 * @author Sönke Küper
 */
public interface GradleInstallationProvider {

    /**
     * Provides all available {@link GradleInstallation}.
     */
    GradleInstallation[] getInstallations();
    
}
