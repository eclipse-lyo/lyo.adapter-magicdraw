package edu.gatech.mbsec.adapter.magicdraw.repository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;

import com.nomagic.magicdraw.commandline.ProjectCommandLine;
import com.nomagic.magicdraw.core.Project;
import com.nomagic.magicdraw.openapi.uml.SessionManager;
import org.glassfish.jersey.servlet.ServletContainer;

import edu.gatech.mbsec.adapter.magicdraw.application.MagicDrawManager;

/** Entrypoint run by {@link MagicDraw2026BackendIT} inside the product runtime. */
public final class MagicDraw2026BackendProbe extends ProjectCommandLine {

	public static void main(final String[] args) throws InstantiationException {
		new MagicDraw2026BackendProbe().launch(args);
	}

	@Override
	protected byte execute(final Properties properties, final Project project) {
		final String projectId = project == null ? "unknown" : project.getName();
		Server httpServer = null;
		try {
			require(project != null, "ProjectCommandLine did not load the SysML v1 project");
			require(project.getModel() != null, "The loaded SysML v1 project has no UML model");
			final int port = Integer.parseInt(requiredProperty("magicdraw.integration.http.port"));
			final String baseUri = "http://127.0.0.1:" + port;

			final SysmlRepositoryMagicDraw2026Impl repository = new SysmlRepositoryMagicDraw2026Impl();
			repository.setBaseURI(baseUri);
			System.out.println("SYSMLV1_2026_BACKEND_LOAD_START project=" + projectId);
			repository.loadSysMLProjectFromLoadedProject(projectId, project);

			final int models = repository.getModels(projectId).size();
			final int blocks = repository.getBlocks(projectId).size();
			final int blockDiagrams = repository.getBlockDiagrams(projectId).size();
			final int internalBlockDiagrams = repository.getInternalBlockDiagrams(projectId).size();
			require(repository.getProjectIds().contains(projectId), "The repository did not register the project");
			require(models > 0, "The repository did not map a SysML model");
			require(blocks > 0, "The repository did not map SysML blocks");
			require(blockDiagrams > 0, "The repository did not map SysML Block Definition diagrams");
			require(internalBlockDiagrams > 0, "The repository did not map SysML Internal Block diagrams");

			MagicDrawManager.setRepository(repository);
			MagicDrawManager.baseHTTPURI = baseUri;
			MagicDrawManager.areSysMLProjectsLoaded = true;
			httpServer = startHttpServer(port);
			writeSignal("magicdraw.integration.http.ready", "READY");
			final String httpResult = awaitHttpChecks();
			httpServer.stop();
			httpServer = null;

			final String result = "SYSMLV1_2026_HTTP_GETS_OK"
					+ " project=" + projectId
					+ " models=" + models
					+ " blocks=" + blocks
					+ " requirements=" + repository.getRequirements(projectId).size()
					+ " interfaceBlocks=" + repository.getInterfaceBlocks(projectId).size()
					+ " valueTypes=" + repository.getValueTypes(projectId).size()
					+ " partProperties=" + repository.getPartProperties(projectId).size()
					+ " referenceProperties=" + repository.getReferenceProperties(projectId).size()
					+ " valueProperties=" + repository.getValueProperties(projectId).size()
					+ " flowProperties=" + repository.getFlowProperties(projectId).size()
					+ " itemFlows=" + repository.getItemFlows(projectId).size()
					+ " proxyPorts=" + repository.getProxyPorts(projectId).size()
					+ " fullPorts=" + repository.getFullPorts(projectId).size()
					+ " blockDiagrams=" + blockDiagrams
					+ " internalBlockDiagrams=" + internalBlockDiagrams
					+ " " + httpResult;
			System.out.println(result);
			writeResult(result);
			return 0;
		} catch (final Throwable failure) {
			failure.printStackTrace(System.err);
			return -1;
		} finally {
			if (httpServer != null) {
				try {
					httpServer.stop();
				} catch (final Exception stopFailure) {
					stopFailure.printStackTrace(System.err);
				}
			}
			final SessionManager sessions = SessionManager.getInstance();
			if (project != null && sessions.isSessionCreated(project)) {
				sessions.cancelSession(project);
			}
		}
	}

	private static void require(final boolean condition, final String message) {
		if (!condition) {
			throw new IllegalStateException(message);
		}
	}

	private static void writeResult(final String result) throws IOException {
		final String resultFile = System.getProperty("magicdraw.integration.result");
		require(resultFile != null && !resultFile.trim().isEmpty(),
				"System property magicdraw.integration.result was not provided");
		Files.write(Paths.get(resultFile), result.getBytes(StandardCharsets.UTF_8));
	}

	private static Server startHttpServer(final int port) throws Exception {
		final Server server = new Server();
		final ServerConnector connector = new ServerConnector(server);
		connector.setPort(port);
		server.addConnector(connector);

		final ServletContextHandler context = new ServletContextHandler();
		context.setContextPath("/");
		final ServletHolder jersey = new ServletHolder(ServletContainer.class);
		jersey.setInitOrder(1);
		jersey.setInitParameter("jakarta.ws.rs.Application",
				"edu.gatech.mbsec.adapter.magicdraw.services.OSLC4JMagicDrawApplication");
		context.addServlet(jersey, "/services/*");
		server.setHandler(context);
		server.start();
		return server;
	}

	private static String awaitHttpChecks() throws Exception {
		final String doneFile = requiredProperty("magicdraw.integration.http.done");
		final long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(10);
		while (System.nanoTime() < deadline && !Files.isRegularFile(Paths.get(doneFile))) {
			Thread.sleep(250);
		}
		require(Files.isRegularFile(Paths.get(doneFile)), "Timed out waiting for the HTTP integration test");
		final String result = new String(Files.readAllBytes(Paths.get(doneFile)), StandardCharsets.UTF_8);
		require(result.startsWith("httpGets=4 "), "The parent process did not complete all four GET checks: " + result);
		return result;
	}

	private static void writeSignal(final String propertyName, final String value) throws IOException {
		final String signalFile = requiredProperty(propertyName);
		Files.write(Paths.get(signalFile), value.getBytes(StandardCharsets.UTF_8));
	}

	private static String requiredProperty(final String name) {
		final String value = System.getProperty(name);
		if (value == null || value.trim().isEmpty()) {
			throw new IllegalStateException("Required system property is missing: " + name);
		}
		return value;
	}
}
