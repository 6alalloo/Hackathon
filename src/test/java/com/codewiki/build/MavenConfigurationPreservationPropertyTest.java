package com.codewiki.build;

import net.jqwik.api.*;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for preservation of non-Lombok Maven configuration.
 * 
 * **Validates: Requirements 3.1, 3.2, 3.3, 3.4**
 * 
 * These tests verify that non-Lombok compilation behavior, test execution,
 * packaging, and runtime behavior remain unchanged after the fix.
 */
public class MavenConfigurationPreservationPropertyTest {

    /**
     * Property 2: Preservation - Test Framework Configuration
     * 
     * Verifies that JUnit 5 and jqwik test framework configuration remains unchanged.
     */
    @Property
    @Label("Test framework dependencies are preserved")
    void testFrameworkDependenciesArePreserved(
            @ForAll("testFramework") String framework) throws Exception {
        
        File pomFile = new File("pom.xml");
        assertThat(pomFile).exists();
        
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(pomFile);
        
        NodeList dependencies = doc.getElementsByTagName("dependency");
        boolean foundFramework = false;
        
        for (int i = 0; i < dependencies.getLength(); i++) {
            Element dependency = (Element) dependencies.item(i);
            NodeList artifactIds = dependency.getElementsByTagName("artifactId");
            
            if (artifactIds.getLength() > 0) {
                String artifactId = artifactIds.item(0).getTextContent();
                if (artifactId.equals(framework)) {
                    foundFramework = true;
                    
                    // Verify test scope
                    NodeList scopes = dependency.getElementsByTagName("scope");
                    if (scopes.getLength() > 0) {
                        assertThat(scopes.item(0).getTextContent())
                                .as(framework + " should have test scope")
                                .isEqualTo("test");
                    }
                    break;
                }
            }
        }
        
        assertThat(foundFramework)
                .as(framework + " dependency should be present")
                .isTrue();
    }

    /**
     * Property 2: Preservation - Spring Boot Plugin Configuration
     * 
     * Verifies that spring-boot-maven-plugin configuration remains unchanged,
     * including Lombok exclusion from runtime JAR.
     */
    @Test
    void springBootPluginConfigurationIsPreserved() throws Exception {
        File pomFile = new File("pom.xml");
        assertThat(pomFile).exists();
        
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(pomFile);
        
        NodeList plugins = doc.getElementsByTagName("plugin");
        boolean foundSpringBootPlugin = false;
        boolean lombokExcluded = false;
        
        for (int i = 0; i < plugins.getLength(); i++) {
            Element plugin = (Element) plugins.item(i);
            NodeList artifactIds = plugin.getElementsByTagName("artifactId");
            
            if (artifactIds.getLength() > 0 && 
                "spring-boot-maven-plugin".equals(artifactIds.item(0).getTextContent())) {
                foundSpringBootPlugin = true;
                
                // Check for Lombok exclusion
                NodeList excludes = plugin.getElementsByTagName("exclude");
                for (int j = 0; j < excludes.getLength(); j++) {
                    Element exclude = (Element) excludes.item(j);
                    NodeList groupIds = exclude.getElementsByTagName("groupId");
                    NodeList excludeArtifactIds = exclude.getElementsByTagName("artifactId");
                    
                    if (groupIds.getLength() > 0 && excludeArtifactIds.getLength() > 0) {
                        String groupId = groupIds.item(0).getTextContent();
                        String artifactId = excludeArtifactIds.item(0).getTextContent();
                        
                        if ("org.projectlombok".equals(groupId) && "lombok".equals(artifactId)) {
                            lombokExcluded = true;
                            break;
                        }
                    }
                }
                break;
            }
        }
        
        assertThat(foundSpringBootPlugin)
                .as("spring-boot-maven-plugin should be present")
                .isTrue();
        
        assertThat(lombokExcluded)
                .as("Lombok should be excluded from runtime JAR")
                .isTrue();
    }

    /**
     * Property 2: Preservation - Frontend Maven Plugin Configuration
     * 
     * Verifies that frontend-maven-plugin configuration remains unchanged.
     */
    @Test
    void frontendMavenPluginConfigurationIsPreserved() throws Exception {
        File pomFile = new File("pom.xml");
        assertThat(pomFile).exists();
        
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(pomFile);
        
        NodeList plugins = doc.getElementsByTagName("plugin");
        boolean foundFrontendPlugin = false;
        
        for (int i = 0; i < plugins.getLength(); i++) {
            Element plugin = (Element) plugins.item(i);
            NodeList artifactIds = plugin.getElementsByTagName("artifactId");
            
            if (artifactIds.getLength() > 0 && 
                "frontend-maven-plugin".equals(artifactIds.item(0).getTextContent())) {
                foundFrontendPlugin = true;
                
                // Verify working directory
                NodeList workingDirs = plugin.getElementsByTagName("workingDirectory");
                if (workingDirs.getLength() > 0) {
                    assertThat(workingDirs.item(0).getTextContent())
                            .as("Frontend working directory should be 'frontend'")
                            .isEqualTo("frontend");
                }
                
                // Verify executions exist
                NodeList executions = plugin.getElementsByTagName("execution");
                assertThat(executions.getLength())
                        .as("Frontend plugin should have executions")
                        .isGreaterThan(0);
                
                break;
            }
        }
        
        assertThat(foundFrontendPlugin)
                .as("frontend-maven-plugin should be present")
                .isTrue();
    }

    /**
     * Property 2: Preservation - Maven Resources Plugin Configuration
     * 
     * Verifies that maven-resources-plugin configuration remains unchanged.
     */
    @Test
    void mavenResourcesPluginConfigurationIsPreserved() throws Exception {
        File pomFile = new File("pom.xml");
        assertThat(pomFile).exists();
        
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(pomFile);
        
        NodeList plugins = doc.getElementsByTagName("plugin");
        boolean foundResourcesPlugin = false;
        
        for (int i = 0; i < plugins.getLength(); i++) {
            Element plugin = (Element) plugins.item(i);
            NodeList artifactIds = plugin.getElementsByTagName("artifactId");
            
            if (artifactIds.getLength() > 0 && 
                "maven-resources-plugin".equals(artifactIds.item(0).getTextContent())) {
                foundResourcesPlugin = true;
                
                // Verify copy-react-build execution exists
                NodeList executions = plugin.getElementsByTagName("execution");
                boolean foundCopyReactBuild = false;
                
                for (int j = 0; j < executions.getLength(); j++) {
                    Element execution = (Element) executions.item(j);
                    NodeList ids = execution.getElementsByTagName("id");
                    
                    if (ids.getLength() > 0 && 
                        "copy-react-build".equals(ids.item(0).getTextContent())) {
                        foundCopyReactBuild = true;
                        break;
                    }
                }
                
                assertThat(foundCopyReactBuild)
                        .as("copy-react-build execution should be present")
                        .isTrue();
                
                break;
            }
        }
        
        assertThat(foundResourcesPlugin)
                .as("maven-resources-plugin should be present")
                .isTrue();
    }

    /**
     * Property 2: Preservation - Java Version Configuration
     * 
     * Verifies that Java version settings remain unchanged.
     */
    @Property
    @Label("Java version properties are preserved")
    void javaVersionPropertiesArePreserved(
            @ForAll("javaProperty") String property) throws Exception {
        
        File pomFile = new File("pom.xml");
        assertThat(pomFile).exists();
        
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(pomFile);
        
        NodeList properties = doc.getElementsByTagName(property);
        
        if (properties.getLength() > 0) {
            String value = properties.item(0).getTextContent();
            assertThat(value)
                    .as(property + " should be set to 17")
                    .isEqualTo("17");
        }
    }

    /**
     * Property 2: Preservation - Project Encoding
     * 
     * Verifies that project encoding remains UTF-8.
     */
    @Test
    void projectEncodingIsPreserved() throws Exception {
        File pomFile = new File("pom.xml");
        assertThat(pomFile).exists();
        
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(pomFile);
        
        NodeList encodings = doc.getElementsByTagName("project.build.sourceEncoding");
        
        assertThat(encodings.getLength())
                .as("project.build.sourceEncoding should be present")
                .isGreaterThan(0);
        
        assertThat(encodings.item(0).getTextContent())
                .as("Project encoding should be UTF-8")
                .isEqualTo("UTF-8");
    }

    @Provide
    Arbitrary<String> testFramework() {
        return Arbitraries.of("jqwik", "junit-jupiter", "spring-boot-starter-test");
    }

    @Provide
    Arbitrary<String> javaProperty() {
        return Arbitraries.of("java.version", "maven.compiler.source", "maven.compiler.target");
    }
}
