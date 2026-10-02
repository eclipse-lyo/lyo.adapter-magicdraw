package edu.gatech.mbsec.adapter.magicdraw.probe;

import java.nio.file.Path;
import java.util.Properties;

import com.nomagic.magicdraw.commandline.ProjectCommandLine;
import com.nomagic.magicdraw.core.Application;
import com.nomagic.magicdraw.core.Project;
import com.nomagic.magicdraw.core.project.ProjectDescriptor;
import com.nomagic.magicdraw.core.project.ProjectDescriptorsFactory;
import com.nomagic.magicdraw.openapi.uml.SessionManager;
import com.nomagic.magicdraw.utils.StateChangeHandler;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.NamedElement;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Package;

/**
 * Runs a disposable commit/cancel/save/reopen check inside MagicDraw.
 * The caller must pass a copy of a local .mdzip project.
 */
public final class MagicDrawProjectTransactionProbe extends ProjectCommandLine {
    private static final String COMMITTED_PACKAGE = "Codex Transaction Commit Probe";
    private static final String CANCELLED_PACKAGE = "Codex Transaction Cancel Probe";

    public static void main(String[] args) throws InstantiationException {
        new MagicDrawProjectTransactionProbe().launch(args);
    }

    @Override
    protected byte execute(Properties properties, Project project) {
        try {
            require(project != null, "ProjectCommandLine did not load the disposable project");
            require(project.getPrimaryModel() != null, "The loaded UML project has no primary model");
            SessionManager sessions = SessionManager.getInstance();
            require(!sessions.isSessionCreated(project), "A session was already active before the probe");
            System.out.println("TRANSACTION_PROJECT_STATE editable=" + project.isEditable()
                    + " active=" + Application.getInstance().getProjectsManager().isProjectActive(project)
                    + " dirty=" + project.isDirty());

            Package root = project.getPrimaryModel();
            if ("verify".equals(properties.getProperty("probe.mode"))) {
                if (!project.isEditable()) {
                    System.out.println("TRANSACTION_REOPEN_SKIPPED_READ_ONLY project=" + project.getName());
                    return 0;
                }
                require(containsNamed(root, COMMITTED_PACKAGE), "The committed package was not present after reopen");
                require(!containsNamed(root, CANCELLED_PACKAGE), "The cancelled package was present after reopen");
                System.out.println("TRANSACTION_REOPEN_OK project=" + project.getName()
                        + " committed=" + COMMITTED_PACKAGE
                        + " cancelledAbsent=" + CANCELLED_PACKAGE);
                return 0;
            }

            Package committed = createPackage(project, sessions, root, COMMITTED_PACKAGE, true);
            require(containsNamed(root, COMMITTED_PACKAGE), "Closing the session did not commit the package");

            createPackage(project, sessions, root, CANCELLED_PACKAGE, false);
            require(!containsNamed(root, CANCELLED_PACKAGE), "Cancelling the session retained the package");
            require(!sessions.isSessionCreated(project), "A transaction session remained open");

            ProjectDescriptor descriptor = ProjectDescriptorsFactory.getDescriptorForProject(project);
            require(descriptor != null, "No local descriptor found for the disposable project");
            boolean saved = false;
            if (project.isEditable()) {
                Path projectPath = Path.of(descriptor.getURI());
                ProjectDescriptor saveDescriptor = ProjectDescriptorsFactory.createLocalProjectDescriptor(
                        project, projectPath.toFile());
                boolean dirtyBeforeSave = project.isDirty();
                if (!dirtyBeforeSave) {
                    project.setDirty(true, StateChangeHandler.DirtyType.HARD_DIRTY);
                }
                System.out.println("TRANSACTION_SAVE_ATTEMPT dirty=" + project.isDirty()
                        + " dirtyBefore=" + dirtyBeforeSave
                        + " dirtyType=" + project.getDirtyType()
                        + " editable=" + project.isEditable()
                        + " descriptor=" + descriptor.getClass().getName()
                        + " uri=" + descriptor.getURI());
                saved = Application.getInstance().getProjectsManager().saveProject(saveDescriptor, false);
                require(saved, "MagicDraw did not save the disposable project");
                System.out.println("TRANSACTION_FILE_SAVE_OK uri=" + descriptor.getURI());
            } else {
                System.out.println("TRANSACTION_FILE_SAVE_SKIPPED_READ_ONLY project=" + project.getName());
            }

            MagicDrawSysmlV2RuntimeProbe.run();

            System.out.println("TRANSACTION_WRITE_OK project=" + project.getName()
                    + " committed=" + committed.getName()
                    + " cancelled=" + CANCELLED_PACKAGE
                    + " descriptor=" + descriptor.getURI()
                    + " fileSaved=" + saved);
            return 0;
        } catch (Throwable failure) {
            failure.printStackTrace(System.err);
            return -1;
        }
    }

    private static Package createPackage(
            Project project,
            SessionManager sessions,
            Package owner,
            String name,
            boolean commit) {
        sessions.createSession(project, commit ? "Commit API probe edit" : "Cancel API probe edit");
        try {
            Package created = project.getElementsFactory().createPackageInstance();
            created.setName(name);
            created.setOwner(owner);
            if (commit) {
                sessions.closeSession(project);
                return created;
            }
            sessions.cancelSession(project);
            return created;
        } catch (RuntimeException failure) {
            if (sessions.isSessionCreated(project)) {
                sessions.cancelSession(project);
            }
            throw failure;
        }
    }

    private static boolean containsNamed(Package owner, String name) {
        for (Element element : owner.getOwnedElement()) {
            if (element instanceof NamedElement namedElement && name.equals(namedElement.getName())) {
                return true;
            }
        }
        return false;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
