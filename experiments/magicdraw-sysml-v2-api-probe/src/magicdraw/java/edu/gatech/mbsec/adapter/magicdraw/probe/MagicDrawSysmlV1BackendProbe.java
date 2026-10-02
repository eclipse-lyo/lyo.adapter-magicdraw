package edu.gatech.mbsec.adapter.magicdraw.probe;

import java.util.Properties;

import com.nomagic.magicdraw.commandline.ProjectCommandLine;
import com.nomagic.magicdraw.core.Project;
import com.nomagic.magicdraw.openapi.uml.SessionManager;

import edu.gatech.mbsec.adapter.magicdraw.repository.SysmlRepositoryMagicDraw2026Impl;

/** Exercises the 2026 repository backend against a real SysML v1 project. */
public final class MagicDrawSysmlV1BackendProbe extends ProjectCommandLine {
    public static void main(String[] args) throws InstantiationException {
        new MagicDrawSysmlV1BackendProbe().launch(args);
    }

    @Override
    protected byte execute(Properties properties, Project project) {
        String projectId = project == null ? "unknown" : project.getName();
        try {
            require(project != null, "ProjectCommandLine did not load the SysML v1 project");
            require(project.getModel() != null, "The loaded SysML v1 project has no UML model");

            SysmlRepositoryMagicDraw2026Impl repository = new SysmlRepositoryMagicDraw2026Impl();
            repository.setBaseURI("http://localhost:8080");
            System.out.println("SYSMLV1_2026_BACKEND_LOAD_START project=" + projectId);
            repository.loadSysMLProjectFromLoadedProject(projectId, project);

            int models = repository.getModels(projectId).size();
            int blocks = repository.getBlocks(projectId).size();
            int blockDiagrams = repository.getBlockDiagrams(projectId).size();
            int internalBlockDiagrams = repository.getInternalBlockDiagrams(projectId).size();
            require(repository.getProjectIds().contains(projectId), "The repository did not register the project");
            require(models > 0, "The repository did not map a SysML model");
            require(blocks > 0, "The repository did not map SysML blocks");
            require(blockDiagrams > 0, "The repository did not map SysML Block Definition diagrams");
            require(internalBlockDiagrams > 0, "The repository did not map SysML Internal Block diagrams");

            System.out.println("SYSMLV1_2026_BACKEND_OK"
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
                    + " internalBlockDiagrams=" + internalBlockDiagrams);
            return 0;
        } catch (Throwable failure) {
            failure.printStackTrace(System.err);
            return -1;
        } finally {
            SessionManager sessions = SessionManager.getInstance();
            if (project != null && sessions.isSessionCreated(project)) {
                sessions.cancelSession(project);
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
