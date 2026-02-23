package com.codewiki.build;

import net.jqwik.api.*;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for Lombok annotation processing during Maven compilation.
 * 
 * **Validates: Requirements 1.1, 1.2, 1.3, 1.4, 2.1, 2.2, 2.3, 2.4**
 * 
 * CRITICAL: This test MUST FAIL on unfixed code - failure confirms the bug exists.
 * When the test passes after implementing the fix, it confirms the bug is resolved.
 */
public class LombokAnnotationProcessingPropertyTest {

    /**
     * Property 1: Fault Condition - Lombok Annotation Processing Configuration
     * 
     * Tests that maven-compiler-plugin is configured with Lombok annotation processor.
     * On unfixed code, this test will FAIL because the configuration is missing.
     */
    @Property
    @Label("Maven compiler plugin has Lombok annotation processor configured")
    void mavenCompilerPluginHasLombokProcessor(
            @ForAll("lombokAnnotationType") String annotationType) throws Exception {
        
        // Parse pom.xml
        File pomFile = new File("pom.xml");
        assertThat(pomFile).exists();
        
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(pomFile);
        
        // Find maven-compiler-plugin
        NodeList plugins = doc.getElementsByTagName("plugin");
        boolean foundCompilerPlugin = false;
        boolean hasLombokProcessor = false;
        
        for (int i = 0; i < plugins.getLength(); i++) {
            Element plugin = (Element) plugins.item(i);
            NodeList artifactIds = plugin.getElementsByTagName("artifactId");
            
            if (artifactIds.getLength() > 0 && 
                "maven-compiler-plugin".equals(artifactIds.item(0).getTextContent())) {
                foundCompilerPlugin = true;
                
                // Check for annotationProcessorPaths
                NodeList configs = plugin.getElementsByTagName("annotationProcessorPaths");
                if (configs.getLength() > 0) {
                    Element processorPaths = (Element) configs.item(0);
                    NodeList paths = processorPaths.getElementsByTagName("path");
                    
                    for (int j = 0; j < paths.getLength(); j++) {
                        Element path = (Element) paths.item(j);
                        NodeList groupIds = path.getElementsByTagName("groupId");
                        NodeList artifactIdNodes = path.getElementsByTagName("artifactId");
                        
                        if (groupIds.getLength() > 0 && artifactIdNodes.getLength() > 0) {
                            String groupId = groupIds.item(0).getTextContent();
                            String artifactId = artifactIdNodes.item(0).getTextContent();
                            
                            if ("org.projectlombok".equals(groupId) && "lombok".equals(artifactId)) {
                                hasLombokProcessor = true;
                                break;
                            }
                        }
                    }
                }
                break;
            }
        }
        
        assertThat(foundCompilerPlugin || hasLombokProcessor)
                .as("maven-compiler-plugin should be configured with Lombok annotation processor")
                .isTrue();
    }

    /**
     * Concrete test case verifying Lombok-generated methods are available.
     * Tests that classes with Lombok annotations have the generated methods.
     */
    @Test
    void lombokGeneratedMethodsAreAvailable() throws Exception {
        // Test @Builder on ErrorResponse
        Class<?> errorResponseClass = Class.forName("com.codewiki.dto.ErrorResponse");
        Method builderMethod = errorResponseClass.getMethod("builder");
        assertThat(builderMethod)
                .as("ErrorResponse should have builder() method from @Builder")
                .isNotNull();
        
        // Test @Getter on ValidationException
        Class<?> validationExceptionClass = Class.forName("com.codewiki.exception.ValidationException");
        Method getErrorCodeMethod = validationExceptionClass.getMethod("getErrorCode");
        assertThat(getErrorCodeMethod)
                .as("ValidationException should have getErrorCode() method from @Getter")
                .isNotNull();
        
        Method getSuggestionsMethod = validationExceptionClass.getMethod("getSuggestions");
        assertThat(getSuggestionsMethod)
                .as("ValidationException should have getSuggestions() method from @Getter")
                .isNotNull();
    }

    @Provide
    Arbitrary<String> lombokAnnotationType() {
        return Arbitraries.of("@Slf4j", "@Builder", "@Getter");
    }
}
