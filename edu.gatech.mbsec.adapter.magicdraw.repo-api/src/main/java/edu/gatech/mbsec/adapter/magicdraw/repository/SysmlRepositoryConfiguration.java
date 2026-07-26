package edu.gatech.mbsec.adapter.magicdraw.repository;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Resolved configuration used to construct a {@link SysmlRepository}.
 *
 * <p>Each setting is resolved from its environment variable, JVM property,
 * configuration-file property, and default value, in that order.</p>
 */
public final class SysmlRepositoryConfiguration {

	private static final String[] DEFAULT_CONFIG_LOCATIONS = {
			"../oslc4jmagicdraw configuration/config.properties",
			"oslc4jmagicdraw configuration/config.properties",
			"edu.gatech.mbsec.adapter.magicdraw/oslc4jmagicdraw configuration/config.properties"
	};

	private final Properties fileProperties;
	private final Path configurationFile;

	private SysmlRepositoryConfiguration(final Properties fileProperties, final Path configurationFile) {
		this.fileProperties = fileProperties;
		this.configurationFile = configurationFile;
	}

	/** Loads the first explicitly configured or conventional properties file. */
	public static SysmlRepositoryConfiguration load() {
		final String explicitPath = firstNonBlank(
				System.getenv("SYSML_ADAPTER_CONFIG_FILE"),
				System.getProperty("sysml.adapter.configFile"));
		if (explicitPath != null) {
			return load(Paths.get(explicitPath), true);
		}
		for (final String candidate : DEFAULT_CONFIG_LOCATIONS) {
			final Path path = Paths.get(candidate);
			if (Files.isRegularFile(path)) {
				return load(path, false);
			}
		}
		return new SysmlRepositoryConfiguration(new Properties(), null);
	}

	private static SysmlRepositoryConfiguration load(final Path path, final boolean required) {
		final Properties properties = new Properties();
		if (!Files.isRegularFile(path)) {
			if (required) {
				throw new IllegalArgumentException("Configured adapter properties file does not exist: " + path);
			}
			return new SysmlRepositoryConfiguration(properties, null);
		}
		try (InputStream input = Files.newInputStream(path)) {
			properties.load(input);
			return new SysmlRepositoryConfiguration(properties, path.toAbsolutePath().normalize());
		} catch (final IOException e) {
			throw new IllegalStateException("Cannot read adapter properties file " + path, e);
		}
	}

	public Path getConfigurationFile() {
		return configurationFile;
	}

	public String getRepositoryClassName() {
		return value("SYSML_REPOSITORY_CLASS", "sysml.repository.class",
				"sysml.repository.class", null);
	}

	public String getStandaloneModelFile() {
		return value("SYSML_REPOSITORY_MODEL_FILE", "sysml.repository.modelFile",
				"standaloneModelFile", null);
	}

	public String getMagicDrawModelsDirectory() {
		return value("SYSML_MAGICDRAW_MODELS_DIRECTORY", "sysml.magicdraw.modelsDirectory",
				"magicdrawModelsDirectory", "magicdrawmodels");
	}

	public boolean useIndividualSubversionFiles() {
		return Boolean.parseBoolean(value("SYSML_MAGICDRAW_INDIVIDUAL_SVN_FILES",
				"sysml.magicdraw.individualSvnFiles", "useIndividualSubversionFiles", "false"));
	}

	public boolean syncWithSubversionRepository() {
		return Boolean.parseBoolean(value("SYSML_MAGICDRAW_SYNC_WITH_SVN",
				"sysml.magicdraw.syncWithSvn", "syncWithSvnRepo", "false"));
	}

	public String getPort() {
		return value("SYSML_ADAPTER_PORT", "sysml.adapter.port", "portNumber", "8080");
	}

	public String getPublicUri() {
		return value("SYSML_ADAPTER_PUBLIC_URI", "sysml.adapter.publicUri", "publicURI", null);
	}

	public String getEcoreLocation() {
		return value("SYSML_ECORE_LOCATION", "sysml.ecore.location",
				"sysmlEcoreLocation", "sysml.ecore");
	}

	public String getSubversionUrlsFile() {
		final String configured = value("SYSML_SVN_URLS_FILE", "sysml.svn.urlsFile",
				"svnURLsFilePath", null);
		if (configured != null) {
			return configured;
		}
		final String[] candidates = {
				"oslc4jmagicdraw configuration/subversionfiles.csv",
				"edu.gatech.mbsec.adapter.magicdraw/oslc4jmagicdraw configuration/subversionfiles.csv"
		};
		for (final String candidate : candidates) {
			if (Files.isRegularFile(Paths.get(candidate))) {
				return candidate;
			}
		}
		return candidates[0];
	}

	public int getRefreshIntervalSeconds() {
		final String configured = value("SYSML_REFRESH_INTERVAL_SECONDS",
				"sysml.refresh.intervalSeconds", "delayInSecondsBetweenDataRefresh", null);
		if (configured == null) {
			return 0;
		}
		try {
			final int seconds = Integer.parseInt(configured);
			return Math.max(0, seconds);
		} catch (final NumberFormatException e) {
			return 0;
		}
	}

	private String value(final String environmentName, final String systemPropertyName,
			final String filePropertyName, final String defaultValue) {
		return firstNonBlank(
				System.getenv(environmentName),
				System.getProperty(systemPropertyName),
				fileProperties.getProperty(filePropertyName),
				defaultValue);
	}

	private static String firstNonBlank(final String... values) {
		for (final String value : values) {
			if (value != null && !value.trim().isEmpty()) {
				return value.trim();
			}
		}
		return null;
	}
}
