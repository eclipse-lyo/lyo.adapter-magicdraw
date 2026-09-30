package edu.gatech.mbsec.adapter.magicdraw.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.Test;

/** Runs the adapter's read-only HTTP routes inside the installed MagicDraw 2026 runtime. */
public class MagicDraw2026BackendIT {

	private static final long STARTUP_TIMEOUT_MINUTES = 15;
	private static final long SHUTDOWN_TIMEOUT_MINUTES = 2;
	private static final String OSLC_CORE = "http://open-services.net/ns/core#";
	private static final String DCTERMS = "http://purl.org/dc/terms/";

	@Test
	void servesTheMappedSysmlV1SampleOverReadOnlyHttpRoutes() throws Exception {
		final Path home = Paths.get(requiredProperty("magicdraw.home")).toAbsolutePath().normalize();
		final Path sample = home.resolve("samples/SysML v1/Introduction to SysML v1.mdzip");
		final Path javaExecutable = home.resolve("jre/bin").resolve(isWindows() ? "java.exe" : "java");
		final Path classpathFile = Paths.get(requiredProperty("magicdraw.test.classpath"));
		final Path moduleDirectory = Paths.get(requiredProperty("magicdraw.module.basedir")).toAbsolutePath().normalize();
		final Path target = moduleDirectory.resolve("target/magicdraw-2026-it");
		final Path models = target.resolve("models");
		final Path diagramImages = target.resolve("diagram-images");
		final Path blockDiagramImages = target.resolve("block-diagram-images");
		final Path log = target.resolve("magicdraw-console.log");
		final Path resultFile = target.resolve("mapping-result.txt");
		final Path readyFile = target.resolve("http-ready.txt");
		final Path doneFile = target.resolve("http-test-done.txt");
		final Path project = models.resolve("Introduction to SysML v1.mdzip");

		assertTrue(Files.isRegularFile(javaExecutable), "MagicDraw bundled Java runtime is missing: " + javaExecutable);
		assertTrue(Files.isRegularFile(sample), "Installed SysML v1 sample is missing: " + sample);
		assertTrue(Files.isRegularFile(classpathFile), "Maven test classpath file is missing: " + classpathFile);

		Files.createDirectories(models);
		Files.createDirectories(diagramImages);
		Files.createDirectories(blockDiagramImages);
		Files.deleteIfExists(resultFile);
		Files.deleteIfExists(readyFile);
		Files.deleteIfExists(doneFile);
		Files.copy(sample, project, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		final String originalSampleHash = sha256(sample);
		final String copiedSampleHash = sha256(project);
		final int port = reservePort();

		final List<String> command = new ArrayList<>();
		command.add(javaExecutable.toString());
		command.add("-Xmx1200M");
		command.add("-Xss1024K");
		command.add("-cp");
		command.add(runtimeClasspath(home, moduleDirectory, classpathFile));
		command.add("-Dmd.plugins.dir=" + home.resolve("plugins"));
		command.add("-Dsysml.magicdraw.modelsDirectory=" + models);
		command.add("-Dmagicdraw.sysml.diagramImageDirectory=" + diagramImages);
		command.add("-Dmagicdraw.sysml.blockDiagramImageDirectory=" + blockDiagramImages);
		command.add("-Dmagicdraw.sysml.mappingLog=" + target.resolve("magicdraw-log"));
		command.add("-Dmagicdraw.integration.http.port=" + port);
		command.add("-Dmagicdraw.integration.http.ready=" + readyFile);
		command.add("-Dmagicdraw.integration.http.done=" + doneFile);
		command.add("-Dmagicdraw.integration.result=" + resultFile);
		command.add("-Dfile.encoding=UTF-8");
		command.add("@bin/vm.options");
		command.add(MagicDraw2026BackendProbe.class.getName());
		command.add("project=" + project);

		Files.createDirectories(log.getParent());
		final Process process = new ProcessBuilder(command)
				.directory(home.toFile())
				.redirectErrorStream(true)
				.redirectOutput(log.toFile())
				.start();

		Throwable httpFailure = null;
		try {
			awaitReady(readyFile, process, log);
			final String baseUri = "http://127.0.0.1:" + port;
			final String httpSummary = exerciseReadOnlyHttpRoutes(baseUri, "Introduction to SysML v1");
			Files.write(doneFile, httpSummary.getBytes(StandardCharsets.UTF_8));
		} catch (final Throwable failure) {
			httpFailure = failure;
			Files.write(doneFile, "HTTP integration test failed".getBytes(StandardCharsets.UTF_8));
		}

		boolean completed = process.waitFor(SHUTDOWN_TIMEOUT_MINUTES, TimeUnit.MINUTES);
		if (!completed) {
			process.destroyForcibly();
			completed = process.waitFor(30, TimeUnit.SECONDS);
		}
		final String output = Files.exists(log) ? new String(Files.readAllBytes(log), StandardCharsets.UTF_8) : "";
		if (httpFailure != null) {
			throw new AssertionError("Read-only HTTP checks failed. Product log: " + log + "\n" + output,
					httpFailure);
		}
		assertTrue(completed, "MagicDraw 2026 process did not stop after the HTTP checks. Log: " + log + "\n" + output);
		assertEquals(0, process.exitValue(), "MagicDraw 2026 process failed. Log: " + log + "\n" + output);
		assertTrue(Files.isRegularFile(resultFile),
				"MagicDraw 2026 backend did not write its success result. Product log: " + log + "\n" + output);
		final String result = new String(Files.readAllBytes(resultFile), StandardCharsets.UTF_8);
		assertTrue(result.contains("SYSMLV1_2026_HTTP_GETS_OK"),
				"MagicDraw 2026 backend did not report successful HTTP checks. Result: " + result
						+ "\nProduct log: " + log + "\n" + output);
		assertEquals(originalSampleHash, copiedSampleHash, "The disposable sample copy changed before the test");
		assertEquals(copiedSampleHash, sha256(project), "The HTTP-only test changed the disposable sample file");
	}

	private static String exerciseReadOnlyHttpRoutes(final String baseUri, final String projectId) throws Exception {
		final List<String> requests = new ArrayList<>();
		final Model catalog = getRdf(URI.create(baseUri + "/services/catalog/singleton"), requests);
		final Resource serviceProvider = findServiceProvider(catalog, projectId);
		assertNotNull(serviceProvider, "Catalog did not publish project " + projectId);

		final Model providerModel = getRdf(URI.create(serviceProvider.getURI()), requests);
		final URI blocksQuery = findBlocksQuery(providerModel);
		assertNotNull(blocksQuery, "Service provider did not advertise a block query base");

		final Model blocks = getRdf(blocksQuery, requests);
		final Resource block = findBlock(blocks);
		assertNotNull(block, "Block query returned no SysML Block resources");

		final Model individualBlock = getRdf(URI.create(block.getURI()), requests);
		assertTrue(hasBlockType(individualBlock, block.getURI()),
				"Individual block response did not describe the queried block " + block.getURI());
		return "httpGets=" + requests.size() + " project=" + projectId + " requests=" + requests;
	}

	private static Model getRdf(final URI uri, final List<String> requests) throws Exception {
		final HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
		connection.setRequestMethod("GET");
		connection.setRequestProperty("OSLC-Core-Version", "2.0");
		connection.setRequestProperty("Accept", "application/rdf+xml");
		connection.setConnectTimeout(30_000);
		connection.setReadTimeout(120_000);
		requests.add("GET " + uri);
		try {
			final int status = connection.getResponseCode();
			assertEquals(200, status, "GET " + uri + " returned " + status + ": " + readResponse(connection));
			try (InputStream response = connection.getInputStream()) {
				final Model model = ModelFactory.createDefaultModel();
				model.read(response, uri.toASCIIString(), "RDF/XML");
				return model;
			}
		} finally {
			connection.disconnect();
		}
	}

	private static String readResponse(final HttpURLConnection connection) throws Exception {
		final InputStream response = connection.getErrorStream();
		if (response == null) {
			return "";
		}
		try (InputStream input = response) {
			final java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
			final byte[] buffer = new byte[4096];
			int length;
			while ((length = input.read(buffer)) != -1) {
				output.write(buffer, 0, length);
			}
			return new String(output.toByteArray(), StandardCharsets.UTF_8);
		}
	}

	private static Resource findServiceProvider(final Model catalog, final String expectedId) {
		final Resource serviceProviderType = catalog.createResource(OSLC_CORE + "ServiceProvider");
		final Property identifier = catalog.createProperty(DCTERMS + "identifier");
		final StmtIterator providers = catalog.listStatements(null, RDF.type, serviceProviderType);
		while (providers.hasNext()) {
			final Resource candidate = providers.next().getSubject();
			if (candidate.hasProperty(identifier)
					&& expectedId.equals(candidate.getProperty(identifier).getString())) {
				return candidate;
			}
		}
		return null;
	}

	private static URI findBlocksQuery(final Model serviceProvider) {
		final Property queryCapability = serviceProvider.createProperty(OSLC_CORE + "queryCapability");
		final Property queryBase = serviceProvider.createProperty(OSLC_CORE + "queryBase");
		final StmtIterator capabilities = serviceProvider.listStatements(null, queryCapability, (RDFNode) null);
		while (capabilities.hasNext()) {
			final RDFNode capabilityNode = capabilities.next().getObject();
			if (capabilityNode.isResource()) {
				final Resource capability = capabilityNode.asResource();
				if (capability.hasProperty(queryBase)) {
					final RDFNode base = capability.getProperty(queryBase).getObject();
					if (base.isURIResource() && base.asResource().getURI().endsWith("/blocks")) {
						return URI.create(base.asResource().getURI());
					}
				}
			}
		}
		return null;
	}

	private static Resource findBlock(final Model blocks) {
		final StmtIterator types = blocks.listStatements(null, RDF.type, (RDFNode) null);
		while (types.hasNext()) {
			final org.apache.jena.rdf.model.Statement type = types.next();
			if (type.getObject().isResource()
					&& "Block".equals(type.getObject().asResource().getLocalName())
					&& type.getSubject().isURIResource()) {
				return type.getSubject();
			}
		}
		return null;
	}

	private static boolean hasBlockType(final Model model, final String blockUri) {
		final Resource block = model.createResource(blockUri);
		final StmtIterator types = model.listStatements(block, RDF.type, (RDFNode) null);
		while (types.hasNext()) {
			final RDFNode type = types.next().getObject();
			if (type.isResource() && "Block".equals(type.asResource().getLocalName())) {
				return true;
			}
		}
		return false;
	}

	private static void awaitReady(final Path readyFile, final Process process, final Path log) throws Exception {
		final long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(STARTUP_TIMEOUT_MINUTES);
		while (System.nanoTime() < deadline) {
			if (Files.isRegularFile(readyFile)) {
				return;
			}
			if (!process.isAlive()) {
				break;
			}
			Thread.sleep(1000);
		}
		final String output = Files.exists(log) ? new String(Files.readAllBytes(log), StandardCharsets.UTF_8) : "";
		assertTrue(Files.isRegularFile(readyFile),
				"MagicDraw did not expose the HTTP test server before timeout. Log: " + log + "\n" + output);
	}

	private static int reservePort() throws Exception {
		try (ServerSocket socket = new ServerSocket(0)) {
			return socket.getLocalPort();
		}
	}

	private static String runtimeClasspath(final Path home, final Path moduleDirectory, final Path classpathFile)
			throws Exception {
		final String dependencies = new String(Files.readAllBytes(classpathFile), StandardCharsets.UTF_8).trim();
		final StringBuilder classpath = new StringBuilder();
		append(classpath, moduleDirectory.resolve("target/test-classes").toString());
		append(classpath, moduleDirectory.resolve("target/classes").toString());
		append(classpath, moduleDirectory.getParent()
				.resolve("edu.gatech.mbsec.adapter.magicdraw.repo-api/target/classes").toString());
		append(classpath, moduleDirectory.getParent()
				.resolve("edu.gatech.mbsec.adapter.magicdraw.resources/target/classes").toString());
		append(classpath, moduleDirectory.getParent()
				.resolve("edu.gatech.mbsec.adapter.magicdraw/target/classes").toString());
		for (final String dependency : dependencies.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
			if (!dependency.isEmpty()
					&& !dependency.toLowerCase().startsWith(home.toString().toLowerCase())) {
				append(classpath, dependency);
			}
		}
		append(classpath, home.resolve("lib/classpath.jar").toString());
		return classpath.toString();
	}

	private static void append(final StringBuilder classpath, final String entry) {
		if (classpath.length() > 0) {
			classpath.append(File.pathSeparatorChar);
		}
		classpath.append(entry);
	}

	private static String sha256(final Path path) throws Exception {
		final byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));
		final StringBuilder hex = new StringBuilder(digest.length * 2);
		for (final byte value : digest) {
			hex.append(String.format("%02x", value & 0xff));
		}
		return hex.toString();
	}

	private static String requiredProperty(final String name) {
		final String value = System.getProperty(name);
		if (value == null || value.trim().isEmpty()) {
			throw new IllegalStateException("Required Maven property is missing: " + name);
		}
		return value;
	}

	private static boolean isWindows() {
		return System.getProperty("os.name", "").toLowerCase().contains("win");
	}
}
