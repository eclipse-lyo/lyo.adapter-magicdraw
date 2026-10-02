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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

class MagicDrawSysmlV2SurfaceTest {
    private static final String PLUGIN_RELATIVE_PATH = "plugins/com.nomagic.magicdraw.sysml2";
    private static final String SYSML_MODEL_CLASS =
            "com/dassault_systemes/modeler/sysml/model/sysml/RequirementUsage.class";
    private static final String KERML_MODEL_CLASS =
            "com/dassault_systemes/modeler/kerml/model/kerml/Feature.class";

    @TempDir
    Path tempDir;

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

    @Test
    void generatedSysmlModelCanCreateEditAndRoundTripAnEmfResource() throws Exception {
        Path home = magicDrawHome();
        assumeTrue(home != null, "Set -Dmagicdraw.home or MAGICDRAW_HOME to inspect the installed bundles");

        try (URLClassLoader loader = new URLClassLoader(installedJars(home), ClassLoader.getPlatformClassLoader())) {
            try {
            Class<?> packageType = Class.forName(
                    "com.dassault_systemes.modeler.sysml.model.sysml.SysMLPackage", true, loader);
            Object sysmlPackage = packageType.getField("eINSTANCE").get(null);
            String nsUri = (String) packageType.getField("eNS_URI").get(null);

            Class<?> factoryType = Class.forName(
                    "com.dassault_systemes.modeler.sysml.model.sysml.SysMLFactory", true, loader);
            Object sysmlFactory = factoryType.getField("eINSTANCE").get(null);
            Object requirement = factoryType.getMethod("createRequirementUsage").invoke(sysmlFactory);
            requirement.getClass().getMethod("setReqId", String.class).invoke(requirement, "REQ-PROBE-001");
            @SuppressWarnings("unchecked")
            List<String> text = (List<String>) requirement.getClass().getMethod("getText").invoke(requirement);
            text.add("Created in the disposable API probe");

            Object firstResourceSet = newResourceSet(loader, nsUri, sysmlPackage);
            Object uri = emfFileUri(loader, tempDir.resolve("sysml-v2-probe.xmi"));
            Class<?> emfUriType = Class.forName("org.eclipse.emf.common.util.URI", true, loader);
            Object resource = firstResourceSet.getClass()
                    .getMethod("createResource", emfUriType)
                    .invoke(firstResourceSet, uri);
            @SuppressWarnings("unchecked")
            List<Object> contents = (List<Object>) resource.getClass().getMethod("getContents").invoke(resource);
            contents.add(requirement);
            resource.getClass().getMethod("save", Map.class).invoke(resource, Collections.emptyMap());

            Object secondResourceSet = newResourceSet(loader, nsUri, sysmlPackage);
            Object loadedResource = secondResourceSet.getClass()
                    .getMethod("getResource", emfUriType, boolean.class)
                    .invoke(secondResourceSet, uri, true);
            Object loadedRequirement = firstContent(loadedResource);
            assertEquals("REQ-PROBE-001",
                    loadedRequirement.getClass().getMethod("getReqId").invoke(loadedRequirement));

            loadedRequirement.getClass().getMethod("setReqId", String.class)
                    .invoke(loadedRequirement, "REQ-PROBE-001-EDITED");
            @SuppressWarnings("unchecked")
            List<String> loadedText = (List<String>) loadedRequirement.getClass().getMethod("getText")
                    .invoke(loadedRequirement);
            loadedText.add("Edited after reload");
            loadedResource.getClass().getMethod("save", Map.class).invoke(loadedResource, Collections.emptyMap());

            Object reloadedResourceSet = newResourceSet(loader, nsUri, sysmlPackage);
            Object reloadedResource = reloadedResourceSet.getClass()
                    .getMethod("getResource", emfUriType, boolean.class)
                    .invoke(reloadedResourceSet, uri, true);
            Object reloadedRequirement = firstContent(reloadedResource);
            assertEquals("REQ-PROBE-001-EDITED",
                    reloadedRequirement.getClass().getMethod("getReqId").invoke(reloadedRequirement));
            @SuppressWarnings("unchecked")
            List<String> reloadedText = (List<String>) reloadedRequirement.getClass().getMethod("getText")
                    .invoke(reloadedRequirement);
            assertTrue(reloadedText.contains("Edited after reload"));
            assertTrue(Files.size(tempDir.resolve("sysml-v2-probe.xmi")) > 0);

            System.out.println("SysML v2 generated-model EMF XMI create/edit/save/reload passed; "
                    + "this runs outside the Magic Systems of Systems Architect application.");
            } catch (Throwable failure) {
                if (requiresApplicationRuntime(failure)) {
                    assumeTrue(false, "The installed SysML v2 model objects require the application's EsiConfig service; "
                            + "run the application-hosted probe for object creation and persistence.");
                }
                if (failure instanceof Exception exception) {
                    throw exception;
                }
                throw new AssertionError("Standalone SysML v2 EMF probe failed", failure);
            }
        }
    }

    private static boolean requiresApplicationRuntime(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current.getMessage() != null && current.getMessage().contains("EsiConfig.getService()")) {
                return true;
            }
        }
        return false;
    }

    private static Object newResourceSet(URLClassLoader loader, String sysmlNsUri, Object sysmlPackage)
            throws Exception {
        Object resourceSet = Class.forName("org.eclipse.emf.ecore.resource.impl.ResourceSetImpl", true, loader)
                .getConstructor().newInstance();
        Object packageRegistry = resourceSet.getClass().getMethod("getPackageRegistry").invoke(resourceSet);
        ((Map<Object, Object>) packageRegistry).put(sysmlNsUri, sysmlPackage);

        Object resourceFactoryRegistry = resourceSet.getClass().getMethod("getResourceFactoryRegistry")
                .invoke(resourceSet);
        Object extensionMap = resourceFactoryRegistry.getClass().getMethod("getExtensionToFactoryMap")
                .invoke(resourceFactoryRegistry);
        Object xmiFactory = Class.forName(
                "org.eclipse.emf.ecore.xmi.impl.XMLResourceFactoryImpl", true, loader)
                .getConstructor().newInstance();
        ((Map<Object, Object>) extensionMap).put("xmi", xmiFactory);
        return resourceSet;
    }

    private static Object emfFileUri(URLClassLoader loader, Path path) throws Exception {
        Class<?> emfUri = Class.forName("org.eclipse.emf.common.util.URI", true, loader);
        return emfUri.getMethod("createFileURI", String.class).invoke(null, path.toString());
    }

    private static Object firstContent(Object resource) throws Exception {
        @SuppressWarnings("unchecked")
        List<Object> contents = (List<Object>) resource.getClass().getMethod("getContents").invoke(resource);
        assertTrue(!contents.isEmpty(), "Loaded SysML resource should have a root element");
        return contents.get(0);
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
