package edu.gatech.mbsec.adapter.magicdraw.probe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

class MagicDrawSysmlV2SurfaceTest {
    private static final String PLUGIN_RELATIVE_PATH = "plugins/com.nomagic.magicdraw.sysml2";
    private static final String SYSML_MODEL_CLASS =
            "com/dassault_systemes/modeler/sysml/model/sysml/RequirementUsage.class";
    private static final String KERML_MODEL_CLASS =
            "com/dassault_systemes/modeler/kerml/model/kerml/Feature.class";

    @Test
    void descriptorDeclaresSysmlV2AndItsSysmlV1HostDependency() throws Exception {
        Path home = magicDrawHome();
        assumeTrue(home != null, "Set -Dmagicdraw.home or MAGICDRAW_HOME to inspect the installed bundles");

        Path descriptor = home.resolve(PLUGIN_RELATIVE_PATH).resolve("plugin.xml");
        assertTrue(Files.isRegularFile(descriptor), "Missing SysML v2 plugin descriptor: " + descriptor);

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        Document document = factory.newDocumentBuilder().parse(descriptor.toFile());
        Element plugin = document.getDocumentElement();

        assertEquals("com.dassault_systemes.modeler.magic.sysml", plugin.getAttribute("id"));
        assertEquals("2026x Refresh1", plugin.getAttribute("version"));
        NodeList requiredPlugins = document.getElementsByTagName("required-plugin");
        assertTrue(containsRequiredPlugin(requiredPlugins, "com.nomagic.magicdraw.plugins.impl.sysml"),
                "SysML v2 declares a dependency on the SysML v1 plugin");

        System.out.printf("SysML v2 plugin: %s (%s), requires SysML v1%n",
                plugin.getAttribute("id"), plugin.getAttribute("version"));
    }

    @Test
    void generatedSysmlV2AndKermlModelInterfacesExposeTheirTypedFeatures() throws Exception {
        Path home = magicDrawHome();
        assumeTrue(home != null, "Set -Dmagicdraw.home or MAGICDRAW_HOME to inspect the installed bundles");

        Path pluginRoot = home.resolve(PLUGIN_RELATIVE_PATH);
        Path sysmlBundle = findJar(pluginRoot, "com.dassault_systemes.modeler.sysml-");
        Path kermlBundle = findJar(pluginRoot, "com.dassault_systemes.modeler.kerml-");
        assertJarContains(sysmlBundle, SYSML_MODEL_CLASS);
        assertJarContains(kermlBundle, KERML_MODEL_CLASS);

        URL[] bundleUrls = installedJars(home);
        try (URLClassLoader loader = new URLClassLoader(bundleUrls, ClassLoader.getPlatformClassLoader())) {
            Class<?> requirementUsage = Class.forName(
                    "com.dassault_systemes.modeler.sysml.model.sysml.RequirementUsage", false, loader);
            Class<?> partUsage = Class.forName(
                    "com.dassault_systemes.modeler.sysml.model.sysml.PartUsage", false, loader);
            Class<?> usage = Class.forName(
                    "com.dassault_systemes.modeler.sysml.model.sysml.Usage", false, loader);
            Class<?> feature = Class.forName(
                    "com.dassault_systemes.modeler.kerml.model.kerml.Feature", false, loader);

            assertTrue(requirementUsage.isInterface());
            assertTrue(partUsage.isInterface());
            assertTrue(feature.isInterface());
            assertTrue(methodNames(requirementUsage).containsAll(Set.of(
                    "getRequirementDefinition", "getReqId", "getText", "getSubjectParameter")));
            assertTrue(methodNames(partUsage).contains("getPartDefinition"));
            assertTrue(methodNames(usage).containsAll(Set.of("getNestedRequirement", "getNestedPart")));

            printMethods(requirementUsage);
            printMethods(partUsage);
            printMethods(usage);
        }
    }

    private static Path magicDrawHome() {
        String value = System.getProperty("magicdraw.home");
        if (value == null || value.isBlank()) {
            value = System.getenv("MAGICDRAW_HOME");
        }
        if (value == null || value.isBlank()) {
            return null;
        }
        Path home = Path.of(value).toAbsolutePath().normalize();
        return Files.isDirectory(home) ? home : null;
    }

    private static boolean containsRequiredPlugin(NodeList requiredPlugins, String id) {
        for (int i = 0; i < requiredPlugins.getLength(); i++) {
            if (id.equals(((Element) requiredPlugins.item(i)).getAttribute("id"))) {
                return true;
            }
        }
        return false;
    }

    private static Path findJar(Path root, String prefix) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith(prefix))
                    .filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No " + prefix + "*.jar under " + root));
        }
    }

    private static void assertJarContains(Path jarPath, String classEntry) throws IOException {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            assertTrue(jar.getEntry(classEntry) != null, jarPath + " is missing " + classEntry);
        }
    }

    private static URL[] installedJars(Path home) throws IOException {
        try (Stream<Path> paths = Files.walk(home)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .map(MagicDrawSysmlV2SurfaceTest::toUrl)
                    .toArray(URL[]::new);
        }
    }

    private static URL toUrl(Path path) {
        try {
            return path.toUri().toURL();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot build JAR URL for " + path, e);
        }
    }

    private static Set<String> methodNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());
    }

    private static void printMethods(Class<?> type) {
        List<String> signatures = Arrays.stream(type.getDeclaredMethods())
                .map(Method::toGenericString)
                .sorted()
                .toList();
        System.out.println(type.getName() + " API:");
        signatures.forEach(signature -> System.out.println("  " + signature));
    }
}
