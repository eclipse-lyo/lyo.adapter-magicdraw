package edu.gatech.mbsec.adapter.magicdraw.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

/** Runs the repository backend inside the installed MagicDraw 2026 runtime. */
public class MagicDraw2026BackendIT {

	private static final long TIMEOUT_MINUTES = 15;

	@Test
	void mapsTheInstalledSysmlV1SampleThroughThe2026Backend() throws Exception {
		final Path home = Paths.get(requiredProperty("magicdraw.home")).toAbsolutePath().normalize();
		final Path sample = home.resolve("samples/SysML v1/Introduction to SysML v1.mdzip");
		final Path javaExecutable = home.resolve("jre/bin").resolve(isWindows() ? "java.exe" : "java");
		final Path classpathFile = Paths.get(requiredProperty("magicdraw.test.classpath"));
		final Path moduleDirectory = Paths.get(requiredProperty("magicdraw.module.basedir")).toAbsolutePath().normalize();
		final Path target = moduleDirectory.resolve("target/magicdraw-2026-it");
		final Path models = target.resolve("models");
		final Path diagramImages = target.resolve("diagram-images");
		final Path blockDiagramImages = target.resolve("block-diagram-images");
		assertTrue(Files.isRegularFile(javaExecutable), "MagicDraw bundled Java runtime is missing: " + javaExecutable);
		assertTrue(Files.isRegularFile(sample), "Installed SysML v1 sample is missing: " + sample);
		assertTrue(Files.isRegularFile(classpathFile), "Maven test classpath file is missing: " + classpathFile);

		Files.createDirectories(models);
		Files.createDirectories(diagramImages);
		Files.createDirectories(blockDiagramImages);
		final Path project = models.resolve("Introduction to SysML v1.mdzip");
		Files.copy(sample, project, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		final Path log = target.resolve("magicdraw-console.log");
		final Path resultFile = target.resolve("mapping-result.txt");
		Files.deleteIfExists(resultFile);
		final Path logDirectory = log.getParent();
		Files.createDirectories(logDirectory);

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
		command.add("-Dmagicdraw.integration.result=" + resultFile);
		command.add("-Dfile.encoding=UTF-8");
		command.add("@bin/vm.options");
		command.add(MagicDraw2026BackendProbe.class.getName());
		command.add("project=" + project);

		final Process process = new ProcessBuilder(command)
				.directory(home.toFile())
				.redirectErrorStream(true)
				.redirectOutput(log.toFile())
				.start();
		final boolean completed = process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES);
		if (!completed) {
			process.destroyForcibly();
			process.waitFor(30, TimeUnit.SECONDS);
		}
		final String output = Files.exists(log) ? new String(Files.readAllBytes(log), StandardCharsets.UTF_8) : "";
		assertTrue(completed, "MagicDraw 2026 integration process timed out. Log: " + log + "\n" + output);
		assertEquals(0, process.exitValue(), "MagicDraw 2026 integration process failed. Log: " + log + "\n" + output);
		assertTrue(Files.isRegularFile(resultFile),
				"MagicDraw 2026 backend did not write its success result. Process log: " + log + "\n" + output);
		final String result = new String(Files.readAllBytes(resultFile), StandardCharsets.UTF_8);
		assertTrue(result.contains("SYSMLV1_2026_BACKEND_OK"),
				"MagicDraw 2026 backend did not report successful model mapping. Result: " + result
						+ "\nProcess log: " + log + "\n" + output);
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
