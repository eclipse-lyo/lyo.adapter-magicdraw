package edu.gatech.mbsec.adapter.magicdraw.repository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

import com.nomagic.magicdraw.commandline.ProjectCommandLine;
import com.nomagic.magicdraw.core.Project;
import com.nomagic.magicdraw.openapi.uml.SessionManager;

/** Entrypoint run by {@link MagicDraw2026BackendIT} inside the product runtime. */
public final class MagicDraw2026BackendProbe extends ProjectCommandLine {

	public static void main(final String[] args) throws InstantiationException {
		new MagicDraw2026BackendProbe().launch(args);
	}

	@Override
	protected byte execute(final Properties properties, final Project project) {
		final String projectId = project == null ? "unknown" : project.getName();
		try {
			require(project != null, "ProjectCommandLine did not load the SysML v1 project");
			require(project.getModel() != null, "The loaded SysML v1 project has no UML model");

			final SysmlRepositoryMagicDraw2026Impl repository = new SysmlRepositoryMagicDraw2026Impl();
			repository.setBaseURI("http://localhost:8080");
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

			final String result = "SYSMLV1_2026_BACKEND_OK"
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
					+ " internalBlockDiagrams=" + internalBlockDiagrams;
			System.out.println(result);
			writeResult(result);
			return 0;
		} catch (final Throwable failure) {
			failure.printStackTrace(System.err);
			return -1;
		} finally {
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
}
