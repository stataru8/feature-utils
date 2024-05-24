package feature.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.maven.project.MavenProject;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.osgi.framework.Version;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

@Mojo(name = "update-versions", defaultPhase = LifecyclePhase.PROCESS_SOURCES)
public class FeaturesUtilsMojo extends AbstractMojo {

    @Parameter(property = "featuresFilePath", required = true)
    private String featuresFilePath;

    @Parameter(property = "targetFeature", required = true)
    private String targetFeature = null;
    
    private static final String pomFilePath = "C:\\Users\\stataru\\Documents\\gitTalend\\camel-karaf\\pom.xml";
	
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
	    // Define the pattern with wildcards
	    Pattern wrapPattern = Pattern.compile("^<bundle.*wrap:mvn:.*</bundle>$");
	    
	    try {
	        // Read all lines from the file
	        List<String> lines = Files.readAllLines(Paths.get(featuresFilePath));
	        
	        // Edit the lines
	        List<String> modifiedLines = lines.stream().map(line -> {
	            if(targetFeature != null) {
	                
	                
	                Pattern featurePattern = Pattern.compile("^<feature.*"+ targetFeature +".*>$");
	                
	                
	            } else {
	                
	            }
	            return processAllfeatures(wrapPattern, line);
	        }).collect(Collectors.toList());
	        
	        // Write the modified lines back to the file
	        Files.write(Paths.get(featuresFilePath), modifiedLines);
	        
	        getLog().info("File updated successfully.");
	    } catch (IOException e) {
	        e.printStackTrace();
	    }        
    }

    private String processAllfeatures(Pattern wrapPattern, String line) {
        // Create a Matcher object
        Matcher matcher = wrapPattern.matcher(line.trim());
        if (matcher.matches()) {
            return addVersionToWrapProtocol(line);
        }
        
        return line;
    }

	private String addVersionToWrapProtocol(String line) {

		
		int versionStartIndex = getVersionFirstIndex(line); 
		int versionEndIndex = getVersionEndIndex(line, versionStartIndex);
		
		
		String version = getVersion(line, versionStartIndex, versionEndIndex);
		try {
		    String resolvedVersion = (version.charAt(0) == '$') ? getPropertyValueFromPom(pomFilePath, version) : version;
		    try {
		        
		        //String sanitizedVersion = VersionCleaner.clean(resolvedVersion);
		        
		        new Version(resolvedVersion);// test if it will work in the Karaf container!
		    } catch (Exception e ) {
		        String message = String.format("Line '%s' was ignored because %s is not a valid OSGi Version: %s", line, resolvedVersion, e.getMessage());
		        getLog().error(message);      
		        return line;
		    }		    
		} catch (Exception e ) {
		    String message = String.format("Line '%s' was ignored because failed to read value of placeholder %s from location %s is not a valid OSGi Version: %s", line, version, pomFilePath, e.getMessage());
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
	
	@Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;
	
	String getPropertyValueFromPom(String placeholder) throws Exception {
	    return getPropertyValueFromPom(placeholder, null);
	}
	
	String getPropertyValueFromPom(String placeholder, String pomFilePath) throws Exception {
        // Extract the property name from the placeholder
        String propertyName = placeholder.substring(2, placeholder.length() - 1);

        // tests only ?
        if (null != pomFilePath) {
            // Parse the pom.xml file
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(pomFilePath);
            doc.getDocumentElement().normalize();
            
            // Find the properties element
            Element propertiesElement = (Element) doc.getElementsByTagName("properties").item(0);
            if (propertiesElement != null) {
                // Get the property value
                String propertyValue = propertiesElement.getElementsByTagName(propertyName).item(0).getTextContent();
                return propertyValue;
            } else {
                throw new Exception(String.format("Properties element <%s> not found in pom.xml", propertyName));
            }   
            
        } else {
            String propertyValue = project.getProperties().getProperty(propertyName);
            if (propertyValue != null) {
                return propertyValue;
            } else {
                throw new Exception(String.format("Property <%s> not found in pom.xml", propertyName));
            }
        }
    }
	
}