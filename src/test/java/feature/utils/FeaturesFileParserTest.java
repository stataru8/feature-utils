package feature.utils;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.regex.Pattern;

import org.apache.felix.utils.version.VersionCleaner;
import org.apache.felix.utils.version.VersionTable;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Version;
import org.apache.karaf.util.*;

public class FeaturesFileParserTest {

    private static final String bundleVersion = "Bundle-Version=9.9.9";
    
    private static final String featuresFilePath = "C:\\Users\\stataru\\Documents\\gitTalend\\camel-karaf\\features\\src\\main\\feature\\camel-features.xml";
    private static final String pomFilePath = "C:\\Users\\stataru\\Documents\\gitTalend\\camel-karaf\\pom.xml";
    
    
    @Test
    void osgiVersionTest() {
        String versionValue = "v3-rev20240123-2.0.0"; 
        
        // will result in exception
        String proposal1 = "0.0.0" + versionValue;
        //new Version(proposal1);
        
        // will result in exception
        String proposal2 = "0.0.0." + versionValue;
        //new Version(proposal2);
        
        String proposal3 = org.apache.felix.utils.version.VersionCleaner.clean(versionValue);
        new Version(proposal3);
        assertEquals("0.0.0.v3-rev20240123-2_0_0", proposal3); 
        
        // Bundle-Version header in MANIFEST.MF
        String guavaVersion = "33.2.0-jre";
        String cleanGuavaVersion = org.apache.felix.utils.version.VersionCleaner.clean(guavaVersion);
        assertEquals("33.2.0.jre", cleanGuavaVersion);
    }
    
    /*
     * Test  rejex
     * 
     */
    @Test
    void testEscapeChars() {
        String testString = "${t}est";
        
        assertEquals("test",  testString.replaceFirst(Pattern.quote("${t}"), "t"));
    }
  
    
    /*
     * Test other String apis
     * 
     */
    
    @Test
    void testStringApis() {
        String input = "<bundle dependency='true'>wrap:mvn:org.apache.olingo/odata-server-core/5.0.0$overwrite=merge&amp;Export-Package=org.apache.olingo.*;version=5.0.0</bundle>"; 
        int insertIndex = input.indexOf("Export-Package");
        assertEquals(97, insertIndex);
        
        
       // before was "&amp;"
        String expectedInput = "<bundle dependency='true'>wrap:mvn:org.apache.olingo/odata-server-core/5.0.0$overwrite=merge&amp;Bundle-Version=99&amp;Export-Package=org.apache.olingo.*;version=5.0.0</bundle>";
        String newInput = new StringBuilder(input).insert(insertIndex, String.format("%s&amp;", "Bundle-Version=99")).toString();
        assertEquals(newInput, expectedInput);
        
        // before was '$'
        input = "<bundle dependency='true'>wrap:mvn:org.apache.olingo/odata-server-core/5.0.0$Export-Package=org.apache.olingo.*;version=5.0.0</bundle>";
        insertIndex = input.indexOf("Export-Package");
        expectedInput = "<bundle dependency='true'>wrap:mvn:org.apache.olingo/odata-server-core/5.0.0$Bundle-Version=9.9.9&amp;Export-Package=org.apache.olingo.*;version=5.0.0</bundle>";
        newInput = new StringBuilder(input).insert(insertIndex, String.format("%s&amp;", bundleVersion)).toString();
        assertEquals(expectedInput, newInput);
        
    }
    
    @Test
    void replaceWrongVersionTest() {
        String line = "<bundle dependency='true'>wrap:mvn:org.apache.olingo/odata-server-core/5.0.0$Bundle-Version=4.4.4</bundle>";
        int startIndex = line.indexOf("Bundle-Version=");
        assertEquals(77, startIndex);
        int endIndex = FeaturesUtilsMojo.getBundleVersionHeaderLastCharIndex(line, startIndex);
        assertEquals(96, endIndex);
        
        String wrongBundleVersionOsgiHeader = FeaturesUtilsMojo.getVersion(line, startIndex, endIndex);
        assertEquals("Bundle-Version=4.4.4", wrongBundleVersionOsgiHeader);
     
        String expected = "<bundle dependency='true'>wrap:mvn:org.apache.olingo/odata-server-core/5.0.0$Bundle-Version=9.9.9</bundle>";
        assertEquals(expected, FeaturesUtilsMojo.replaceWrongVersion(line, bundleVersion));
    }
    
    @Test
    void getWrapLastCharIndexFirstOsgiHeaderTest() {
        String line = "<bundle dependency='true'>wrap:mvn:org.apache.olingo/odata-server-core/5.0.0</bundle>";
        int versionEndIndex = FeaturesUtilsMojo.getVersionEndIndex(line);
        int endIndex = FeaturesUtilsMojo.getWrapLastCharIndex(line);
        
        String wrapOsgiDeclaration = line.substring(versionEndIndex+1, endIndex+1);
        String expected = "";
        
        assertEquals(expected, wrapOsgiDeclaration);
        
        String result = new StringBuilder(line).insert(endIndex + 1, String.format("$%s", bundleVersion)).toString();
        String expectedLine = "<bundle dependency='true'>wrap:mvn:org.apache.olingo/odata-server-core/5.0.0$Bundle-Version=9.9.9</bundle>";
        assertEquals(expectedLine, result);
    }

    @Test
    void getWrapLastCharIndexNotFirstOsgiHeaderTest() {
        String line = "<bundle dependency='true'>wrap:mvn:org.apache.olingo/odata-server-core/5.0.0$overwrite=merge&amp;Export-Package=org.apache.olingo.*;version=5.0.0</bundle>";
        int versionEndIndex = FeaturesUtilsMojo.getVersionEndIndex(line);
        int endIndex = FeaturesUtilsMojo.getWrapLastCharIndex(line);
        
        String wrapOsgiDeclaration = line.substring(versionEndIndex+1, endIndex+1);
        String expected = "$overwrite=merge&amp;Export-Package=org.apache.olingo.*;version=5.0.0";
        
        assertEquals(expected, wrapOsgiDeclaration);
        
        String result = new StringBuilder(line).insert(endIndex + 1, String.format("&amp;%s", bundleVersion)).toString();
        String expectedLine = "<bundle dependency='true'>wrap:mvn:org.apache.olingo/odata-server-core/5.0.0$overwrite=merge&amp;Export-Package=org.apache.olingo.*;version=5.0.0&amp;Bundle-Version=9.9.9</bundle>";
        assertEquals(expectedLine, result);
    }
    
    
    @Test
    void addVersionToWrapProtocolTest() {
    	//assertEquals("<bundle dependency='true'>mvn:org.apache.qpid/qpid-jms-client/${qpid-jms-client-version}</bundle>", FeaturesFileParser.addVersionToWrapProtocol("<bundle dependency='true'>mvn:org.apache.qpid/qpid-jms-client/${qpid-jms-client-version}$Bundle-Version=${qpid-jms-client-version}</bundle>"));
    	
    	
    }
    
    @Test
    void getVersionStartIndexTest() {
    	assertEquals(62, FeaturesUtilsMojo.getVersionFirstIndex("<bundle dependency='true'>mvn:org.apache.qpid/qpid-jms-client/${qpid-jms-client-version}$Bundle-Version=${qpid-jms-client-version}</bundle>"));
    }
    
    @Test
    void getVersionEndIndexTest() {
    	assertEquals(87, FeaturesUtilsMojo.getVersionEndIndex("<bundle dependency='true'>mvn:org.apache.qpid/qpid-jms-client/${qpid-jms-client-version}$Bundle-Version=${qpid-jms-client-version}</bundle>"));
    }
    
    @Test
    void getVersionValueTest() {
    	assertEquals("${qpid-jms-client-version}", FeaturesUtilsMojo.getVersion("<bundle dependency='true'>mvn:org.apache.qpid/qpid-jms-client/${qpid-jms-client-version}$Bundle-Version=${qpid-jms-client-version}</bundle>"));
    	
    	assertEquals("8.44.0.Final", FeaturesUtilsMojo.getVersion("<bundle>wrap:mvn:org.kie/kie-api/8.44.0.Final</bundle>"));
    	
    	assertEquals("5.0.0", FeaturesUtilsMojo.getVersion("<bundle dependency='true'>wrap:mvn:org.apache.olingo/odata-server-core/5.0.0$overwrite=merge&amp;Export-Package=org.apache.olingo.*;version=5.0.0</bundle>"));
    	
    	assertEquals("${grpc-version}", FeaturesUtilsMojo.getVersion("<bundle dependency='true'>wrap:mvn:io.grpc/grpc-core/${grpc-version}$${spi-provider}</bundle>"));
    }
    
}