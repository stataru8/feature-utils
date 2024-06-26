package feature.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.osgi.framework.Version;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Repository;
import org.apache.maven.model.building.DefaultModelBuilderFactory;
import org.apache.maven.model.building.DefaultModelBuildingRequest;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.resolution.ModelResolver;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import shaded.org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.impl.DefaultServiceLocator.ErrorHandler;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;

import java.util.ArrayList;
import java.util.List;


@Mojo(name = "ensure-wrap-bundle-version", defaultPhase = LifecyclePhase.PROCESS_SOURCES)
public class FeaturesUtilsMojo extends AbstractMojo {

    @Parameter(property = "featuresFilePath", required = true)
    private String featuresFilePath;

    @Parameter(property = "targetFeature", required = false)
    private String targetFeature = null;
    
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;
	
	public static final List<String> OSGI_HEADERS_AFTER_BUNDLE_VEIRSION = Arrays.asList(
	        //"Bundle-Version",
	        "DynamicImport-Package",
	        "Export-Package",
	        "Export-Service",
	        "Fragment-Host",
	        "Import-Package",
	        "Import-Service",
	        "Provide-Capability",
	        "Require-Bundle",
	        "Require-Capability"
	    );
	
	@Override
    public void execute() throws MojoExecutionException {
        try {
            // Read all lines from the file
            List<String> lines = Files.readAllLines(Paths.get(featuresFilePath));

            int firstIndex = 0;
            int lastIndex = lines.size()-1;
            if (targetFeature != null) {
                Pattern featureStartPattern = Pattern.compile("^<feature.*" + targetFeature + ".*>$");
                Pattern featureEndPattern = Pattern.compile("^</feature>$");
                
                for (int i = 0; i < lines.size(); i++) {
                    Matcher featureStartMatcher = featureStartPattern.matcher(lines.get(i).trim());
                    if(featureStartMatcher.matches()) {
                        firstIndex = i + 1;
                    }
                    
                    // start of the feature alrady found
                    if(firstIndex != 0) {                        
                        Matcher featureEndMatcher = featureEndPattern.matcher(lines.get(i).trim());
                        if(featureEndMatcher.matches()) {
                            lastIndex = i - 1;
                            break;
                        }
                    }
                }
                
                if (firstIndex == 0) {
                    getLog().error(String.format("Feature %s not found, no lines will be processed", targetFeature));
                    return;
                }
            }
            modifyLines(lines, firstIndex, lastIndex);
	        
	        // Write the modified lines back to the file
	        Files.write(Paths.get(featuresFilePath), lines);
	        
	        getLog().info("File updated successfully.");
	    } catch (IOException e) {
	        e.printStackTrace();
	    }        
    }

    private void modifyLines(List<String> lines, int firstIndex, int lastIndex) {

        // Define the pattern with wildcards
        Pattern wrapPattern = Pattern.compile("^<bundle.*wrap:mvn:.*</bundle>$");

        // Edit the lines
        for (int i = firstIndex; i <= lastIndex; i++) {
            // Create a Matcher object
            Matcher matcher = wrapPattern.matcher(lines.get(i).trim());
            if (matcher.matches()) {
                lines.set(i, addVersionToWrapProtocol(lines.get(i)));
            }
        }
    }

	private String addVersionToWrapProtocol(String line) {
        int versionStartIndex = getVersionFirstIndex(line); 
		int versionEndIndex = getVersionEndIndex(line, versionStartIndex);
		
		String version = getVersion(line, versionStartIndex, versionEndIndex);
		try {
		    String resolvedVersion = (version.charAt(0) == '$') ? getPropertyValueFromPom(version) : version;
		    try {
		        new Version(resolvedVersion);// test if it will work in the Karaf container!
		        
		    } catch (Exception e ) {
		        // TODO: only use cleanVersion if the artifact is non-osgi
		        
		        String cleanVersion = org.apache.felix.utils.version.VersionCleaner.clean(resolvedVersion);
		        try {
		            new Version(cleanVersion);// test if it will work in the Karaf container again!
		            // WARN: placeholder for version will be removed here
		            String messagePlaceholder = (version.charAt(0) == '$') ? ". Placeholder from artifact version was replaced with value from clean function" : ".";
		            String message = String.format("Line '%s' was set with Bundle-Version '%s', the output of org.apache.felix.utils.version.VersionCleaner.clean(%s)%s", line, cleanVersion, resolvedVersion, messagePlaceholder);
		            getLog().warn(message);
		            
		            version = cleanVersion;
		        } catch (Exception e2 ) {
		            String message = String.format("Line '%s' was ignored because '%s' is not a valid OSGi Version: %s", line, cleanVersion, e.getMessage());
		            getLog().warn(message);      
		            return line;		            
		        }		        
		    }		    
		} catch (Exception e ) {
		    StringWriter sw = new StringWriter();
		    PrintWriter pw = new PrintWriter(sw);
		    e.printStackTrace(pw);
		    String sStackTrace = sw.toString(); // stack trace as a string
		    String message = String.format("Line '%s' was ignored because it wasn't possible to read value of placeholder '%s' from '%s': %s", line, clearPlaceHolderChars(version), project, sStackTrace);
		    getLog().error(message);      
            return line;   
		}
		
		String bundleVersionOsgiHeader = String.format("Bundle-Version=%s", version);
		
		if (line.contains(bundleVersionOsgiHeader)) {
		    return line;
		}
		
		if(line.contains("Bundle-Version=")) {
            return replaceWrongVersion(line, bundleVersionOsgiHeader);
		}
		
		StringBuilder sb = new StringBuilder(line);
		for(String osgiHeader : OSGI_HEADERS_AFTER_BUNDLE_VEIRSION) {
			// add Bundle-Version before
			if(line.contains(osgiHeader)) {
				int insertIndex = line.indexOf(osgiHeader);
				return sb.insert(insertIndex, String.format("%s&amp;", bundleVersionOsgiHeader)).toString();
			}
		}
		
		int endIndex = getWrapLastCharIndex(line);
		// add + 1, ignroe last version char and 
		String wrapOsgiDeclaration = line.substring(versionEndIndex+1, endIndex+1);
		
		if(wrapOsgiDeclaration.contains("$")) {
			return sb.insert(endIndex + 1, String.format("&amp;%s", bundleVersionOsgiHeader)).toString();
		} else {
			return sb.insert(endIndex + 1, String.format("$%s", bundleVersionOsgiHeader)).toString();
		}
    }

    static String replaceWrongVersion(String line, String bundleVersionOsgiHeader) {
        int startIndex = line.indexOf("Bundle-Version=");
        int endIndex = getBundleVersionHeaderLastCharIndex(line, startIndex);
        String wrongBundleVersionOsgiHeader = getVersion(line, startIndex, endIndex);
        
        return line.replaceFirst(Pattern.quote(wrongBundleVersionOsgiHeader), Matcher.quoteReplacement(bundleVersionOsgiHeader));
    }
        	
	static int getBundleVersionHeaderLastCharIndex(String line, int insertIndex) {
	    char[] chars = line.toCharArray();
	    
	    boolean versionPlaceHolderFound = false;
	    for(int i = insertIndex; i<line.length(); i++) {
	        if(chars[i] == '$') {
	            if(!versionPlaceHolderFound) {
	                versionPlaceHolderFound = !versionPlaceHolderFound;
	            } else {
	                return i-1;
	            }
	            
	        }
	        
	        if (chars[i] == '<' || chars[i] == '&') {// last one probably not needed
	            return i-1;
	        }
	    }
        
	    return -1;
    }

    static int getWrapLastCharIndex(String line) {
		char[] chars = line.toCharArray();
		
		for(int i = line.length()-1; i>0; i--) {
			if (chars[i] == '<') {
				return i-1;
			}
		}
		
		return -1;
	}

	static String getVersion(String line) {
		return getVersion(line, getVersionFirstIndex(line), getVersionEndIndex(line));
	}
	
	static String getVersion(String line, int versionStartIndex, int versionEndIndex) {
		return line.substring(versionStartIndex, versionEndIndex +1);
	}

	static int getVersionEndIndex(String line) {
		return getVersionEndIndex(line, getVersionFirstIndex(line));
	}
	
	static int getVersionEndIndex(String line, int versionStartIndex) {
		char[] chars = line.toCharArray();
		
		// start at versionStartIndex + 1 to ignore the $ in version placeholder use case
		for(int i = versionStartIndex+1; i< chars.length; i++) {
		    if('}' == chars[i]) {
		        return i;
		    }
		    
			if('$' == chars[i] || '<' == chars[i]) {
				return i-1;
			}
		}
		
		return versionStartIndex;
	}

	static int getVersionFirstIndex(String line) {
		char[] chars = line.toCharArray();
		
		boolean iteratedOverGroupId = false;
		for(int i = 0; i< chars.length; i++) {
			if('/' == chars[i]) {
				if(!iteratedOverGroupId) {
					iteratedOverGroupId = true;
				} else {
					return i + 1;
				}
			}
		}
		
		return -1;
	}
	
	private String getPropertyValueFromPom(String placeholder) throws Exception {
        // Extract the property name from the placeholder
        String propertyName = clearPlaceHolderChars(placeholder);

        String propertyValue = project.getProperties().getProperty(propertyName);
        
        if (propertyValue != null) {
            return propertyValue;
        } else {
            throw new Exception(String.format("Property <%s> not found in pom.xml", propertyName));
        }
    }

    private String clearPlaceHolderChars(String placeholder) {
        return placeholder.substring(2, placeholder.length() - 1);
    }
/*
    public String getBundleVersionFromManifest(String line) throws IOException {
        
        try (JarFile jarFile = new JarFile(downloadJarFile(artifactUrl))) {
            Manifest manifest = jarFile.getManifest();
            if (manifest != null) {
                String bundleVersion = manifest.getMainAttributes().getValue("Bundle-Version");
                if (bundleVersion != null) {
                    System.out.println("Artifact is an OSGi bundle.");
                    // You can further inspect the Manifest for other OSGi-specific headers
                } else {
                    System.out.println("Artifact is not an OSGi bundle.");
                }
            } else {
                System.out.println("Manifest file not found. Cannot determine if the artifact is an OSGi bundle.");
            }
        }
        
        return null;
    }

    public static InputStream downloadJarFile(String artifactUrl) throws IOException {
        URL url = new URL(artifactUrl);
        return url.openStream();
    }
    private static RepositorySystem newRepositorySystem() {
        DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
        locator.setErrorHandler(new ErrorHandler() {
            public void serviceCreationFailed(Class<?> type, Class<?> impl, Throwable ex) {
                throw new IllegalStateException(ex);
            }
        });
        return locator.getService(RepositorySystem.class);
    }

    private static RepositorySystemSession newSession(RepositorySystem system) {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();

        // Set up the repositories from the Maven settings
        for (Repository repository : MavenRepositorySystemUtils.loadSettings().getRepositories()) {
            RemoteRepository.Builder builder = new RemoteRepository.Builder(repository.getId(), "default", repository.getUrl());
            remoteRepos.add(builder.build());
        }

        return session;
    } */
}