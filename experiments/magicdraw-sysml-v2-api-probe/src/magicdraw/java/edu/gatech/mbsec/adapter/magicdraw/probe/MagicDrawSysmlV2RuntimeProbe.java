package edu.gatech.mbsec.adapter.magicdraw.probe;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;

import com.nomagic.esi.api.EsiObject;
import com.nomagic.esi.api.EsiResource;
import com.nomagic.magicdraw.core.Application;
import com.nomagic.magicdraw.core.Project;
import com.nomagic.magicdraw.core.project.ProjectDescriptor;
import com.nomagic.magicdraw.core.project.ProjectDescriptorsFactory;
import com.nomagic.ci.persistence.IPrimaryProject;
import com.nomagic.magicdraw.openapi.uml.SessionManager;
import com.nomagic.magicdraw.utils.StateChangeHandler;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.NamedElement;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Package;

/** Application-hosted checks for the installed SysML v2 model and project APIs. */
final class MagicDrawSysmlV2RuntimeProbe {
    private static final String SYSML_PROJECT_HELPER =
            "com.dassault_systemes.modeler.magic.sysml.core.SysMLProjectHelper";
    private static final String SYSML_PLUGIN_ID = "com.dassault_systemes.modeler.magic.sysml";
    private static final String COMMITTED_PACKAGE = "Codex SysML v2 Transaction Commit Probe";
    private static final String CANCELLED_PACKAGE = "Codex SysML v2 Transaction Cancel Probe";

    private MagicDrawSysmlV2RuntimeProbe() {
    }

    static void run() throws Exception {
        ClassLoader sysmlLoader = findSysmlPluginClassLoader();
        runGeneratedModelApi(sysmlLoader);
        runSysmlProjectLoadRoundTrip(sysmlLoader);
    }

    private static ClassLoader findSysmlPluginClassLoader() throws Exception {
        Object pluginManager = Application.getInstance().getPluginManager();
        for (Method method : pluginManager.getClass().getMethods()) {
            if (method.getParameterCount() != 0 || !List.class.isAssignableFrom(method.getReturnType())) {
                continue;
            }
            Object result = method.invoke(pluginManager);
            if (!(result instanceof List<?> plugins)) {
                continue;
            }
            for (Object plugin : plugins) {
                Object descriptor = plugin.getClass().getMethod("getDescriptor").invoke(plugin);
                String id = (String) descriptor.getClass().getMethod("getID").invoke(descriptor);
                if (SYSML_PLUGIN_ID.equals(id)) {
                    ClassLoader loader = plugin.getClass().getClassLoader();
                    Class.forName(SYSML_PROJECT_HELPER, false, loader);
                    return loader;
                }
            }
        }
        throw new IllegalStateException("Could not find the active SysML v2 plugin class loader");
    }

    private static void runGeneratedModelApi(ClassLoader loader) throws Exception {
        Class<?> packageType = Class.forName(
                "com.dassault_systemes.modeler.sysml.model.sysml.SysMLPackage", true, loader);
        Object sysmlPackage = packageType.getField("eINSTANCE").get(null);
        Class<?> factoryType = Class.forName(
                "com.dassault_systemes.modeler.sysml.model.sysml.SysMLFactory", true, loader);
        Object sysmlFactory = factoryType.getField("eINSTANCE").get(null);
        Object requirement = factoryType.getMethod("createRequirementUsage").invoke(sysmlFactory);
        requirement.getClass().getMethod("setReqId", String.class).invoke(requirement, "REQ-APP-PROBE-001");
        @SuppressWarnings("unchecked")
        List<String> text = (List<String>) requirement.getClass().getMethod("getText").invoke(requirement);

        String actualReqId = (String) requirement.getClass().getMethod("getReqId").invoke(requirement);
        require("REQ-APP-PROBE-001".equals(actualReqId), "SysML v2 requirement ID setter/getter did not round-trip");
        require(text != null, "SysML v2 requirement text feature did not return a collection");
        require(sysmlPackage != null, "SysML v2 EPackage singleton was not initialized");
        System.out.println("SYSMLV2_GENERATED_API_RUNTIME_OK object=" + requirement.getClass().getName()
                + " reqId=" + actualReqId + " textFeature=" + text.getClass().getName()
                + " textEntries=" + text.size());
    }

    private static void runSysmlProjectLoadRoundTrip(ClassLoader loader) throws Exception {
        String projectName = "Codex SysML v2 probe " + UUID.randomUUID().toString().substring(0, 8);
        Class<?> helper = Class.forName(SYSML_PROJECT_HELPER, true, loader);
        Project createdProject = null;
        ProjectDescriptor cleanupDescriptor = null;
        Throwable primaryFailure = null;
        try {
            createdProject = (Project) helper.getMethod("createUPSProject", String.class)
                    .invoke(null, projectName);
            require(createdProject != null, "SysMLProjectHelper did not create a SysML v2 project");
            require(projectName.equals(createdProject.getName()), "Created SysML v2 project name did not match");
            cleanupDescriptor = ProjectDescriptorsFactory.getDescriptorForProject(createdProject);
            require(cleanupDescriptor != null && cleanupDescriptor.isRemote(),
                    "Created SysML v2 project did not get a repository descriptor");
            System.out.println("SYSMLV2_PROJECT_CREATED project=" + createdProject.getName()
                    + " editable=" + createdProject.isEditable()
                    + " isEsiProject=" + createdProject.isEsiProject()
                    + " primaryModel=" + (createdProject.getPrimaryModel() != null)
                    + " descriptor=" + cleanupDescriptor.getClass().getName());

            boolean transactionSaved = false;
            String committedRequirementId = null;
            if (createdProject.isEditable() && createdProject.getPrimaryModel() != null) {
                runSysmlProjectTransactions(createdProject);
                transactionSaved = Application.getInstance().getProjectsManager().saveProject(cleanupDescriptor, true);
                require(transactionSaved, "MagicDraw did not save the UML-backed SysML v2 project probe");
                System.out.println("SYSMLV2_TRANSACTION_SAVE_OK project=" + projectName);
            } else if (createdProject.isEditable()) {
                inspectEsiProjectResources(createdProject);
                committedRequirementId = runSysmlEsiResourceTransactions(createdProject, loader);
                transactionSaved = true;
                System.out.println("SYSMLV2_ESI_RESOURCE_COMMIT_OK project=" + projectName
                        + " reqId=" + committedRequirementId);
            } else {
                System.out.println("SYSMLV2_TRANSACTION_SKIPPED_READ_ONLY project=" + projectName);
            }

            Application.getInstance().getProjectsManager().closeProjectNoSave(createdProject);
            createdProject = null;
            helper.getMethod("loadUPSProject", String.class).invoke(null, projectName);
            Project reopened = Application.getInstance().getProjectsManager().getActiveProject();
            require(reopened != null && projectName.equals(reopened.getName()),
                    "SysMLProjectHelper did not reopen the created SysML v2 project");
            createdProject = reopened;
            inspectEsiProjectResources(reopened);
            if (transactionSaved) {
                if (committedRequirementId != null) {
                    require(containsRequirementId(reopened, committedRequirementId),
                            "Committed SysML v2 requirement was not present after project reopen");
                    System.out.println("SYSMLV2_ESI_RESOURCE_REOPEN_OK project=" + projectName
                            + " reqId=" + committedRequirementId);
                } else {
                    require(containsNamed(reopened.getPrimaryModel(), COMMITTED_PACKAGE),
                            "Committed package was not present after SysML v2 project reopen");
                    require(!containsNamed(reopened.getPrimaryModel(), CANCELLED_PACKAGE),
                            "Cancelled package was present after SysML v2 project reopen");
                    System.out.println("SYSMLV2_TRANSACTION_REOPEN_OK project=" + projectName);
                }
            }
            System.out.println("SYSMLV2_PROJECT_REOPEN_OK project=" + reopened.getName());
        } catch (Throwable failure) {
            primaryFailure = unwrap(failure);
            if (primaryFailure instanceof Exception exception) {
                throw exception;
            }
            throw new AssertionError("SysML v2 project create/reopen probe failed", primaryFailure);
        } finally {
            Throwable cleanupFailure = null;
            try {
                deleteUpsProject(loader, cleanupDescriptor);
                System.out.println("SYSMLV2_PROJECT_CLEANUP_OK project=" + projectName);
            } catch (Throwable cleanupError) {
                cleanupFailure = unwrap(cleanupError);
            }
            try {
                Project active = Application.getInstance().getProjectsManager().getActiveProject();
                if (active != null && projectName.equals(active.getName())) {
                    Application.getInstance().getProjectsManager().closeProjectNoSave(active);
                } else if (createdProject != null) {
                    Application.getInstance().getProjectsManager().closeProjectNoSave(createdProject);
                }
            } catch (Throwable closeFailure) {
                if (cleanupFailure == null) {
                    cleanupFailure = unwrap(closeFailure);
                } else {
                    cleanupFailure.addSuppressed(unwrap(closeFailure));
                }
            }
            if (cleanupFailure != null) {
                try {
                    deleteUpsProject(loader, cleanupDescriptor);
                    System.out.println("SYSMLV2_PROJECT_CLEANUP_OK project=" + projectName + " afterClose=true");
                } catch (Throwable retryFailure) {
                    cleanupFailure.addSuppressed(unwrap(retryFailure));
                    if (primaryFailure != null) {
                        primaryFailure.addSuppressed(cleanupFailure);
                    } else {
                        throw new AssertionError("Could not clean up the disposable SysML v2 project " + projectName,
                                cleanupFailure);
                    }
                }
            }
        }
    }

    private static void inspectEsiProjectResources(Project project) {
        IPrimaryProject primaryProject = project.getPrimaryProject();
        require(primaryProject != null, "SysML v2 project has no ESI primary project");
        ResourceSet resourceSet = primaryProject.getResourceSet();
        require(resourceSet != null, "SysML v2 ESI primary project has no EMF resource set");
        System.out.println("SYSMLV2_ESI_RESOURCE_SET project=" + project.getName()
                + " primaryType=" + primaryProject.getClass().getName()
                + " resourceSetType=" + resourceSet.getClass().getName()
                + " resourceCount=" + resourceSet.getResources().size()
                + " attachedProjectCount=" + primaryProject.getProjects().size());
        int resourceIndex = 0;
        for (Resource resource : resourceSet.getResources()) {
            StringBuilder details = new StringBuilder("SYSMLV2_ESI_RESOURCE index=")
                    .append(resourceIndex++)
                    .append(" type=").append(resource.getClass().getName())
                    .append(" uri=").append(resource.getURI())
                    .append(" loaded=").append(resource.isLoaded())
                    .append(" roots=").append(resource.getContents().size());
            if (resource instanceof EsiResource esiResource) {
                details.append(" esiId=").append(esiResource.getID())
                        .append(" readOnly=").append(esiResource.isReadOnly())
                        .append(" added=").append(esiResource.getAddedObjects().size())
                        .append(" dirty=").append(esiResource.getDirtyObjects().size());
            }
            for (EObject root : resource.getContents()) {
                details.append(" root=").append(root.eClass().getName());
            }
            System.out.println(details);
        }
    }

    private static String runSysmlEsiResourceTransactions(Project project, ClassLoader loader) throws Exception {
        IPrimaryProject primaryProject = project.getPrimaryProject();
        require(primaryProject != null, "SysML v2 project has no ESI primary project");
        ResourceSet resourceSet = primaryProject.getResourceSet();
        EsiResource resource = resourceSet.getResources().stream()
                .filter(EsiResource.class::isInstance)
                .map(EsiResource.class::cast)
                .filter(candidate -> !candidate.isReadOnly())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("SysML v2 project has no writable ESI resource"));
        System.out.println("SYSMLV2_ESI_TX_TARGET resource=" + resource.getURI()
                + " roots=" + resource.getContents().size());

        String cancelledReqId = "REQ-CANCEL-" + UUID.randomUUID().toString().substring(0, 8);
        EObject cancelled = createRequirementUsage(loader, cancelledReqId);
        resource.getContents().add(cancelled);
        require(cancelled.eResource() == resource, "Cancelled SysML v2 object was not attached to the ESI resource");
        require(isTrackedAsAdded(resource, cancelled), "ESI resource did not track the new object as added");
        resource.reset();
        require(!containsRequirementId(resource, cancelledReqId),
                "Resetting the ESI resource did not roll back the uncommitted SysML v2 object");
        System.out.println("SYSMLV2_ESI_RESOURCE_RESET_OK reqId=" + cancelledReqId);

        String committedReqId = "REQ-COMMIT-" + UUID.randomUUID().toString().substring(0, 8);
        EObject committed = createRequirementUsage(loader, committedReqId);
        resource.getContents().add(committed);
        require(committed.eResource() == resource, "Committed SysML v2 object was not attached to the ESI resource");
        require(isTrackedAsAdded(resource, committed), "ESI resource did not track the committed object");
        resource.createCommitter().commit("Codex SysML v2 resource API probe commit");
        require(containsRequirementId(resource, committedReqId),
                "ESI resource commit did not retain the SysML v2 requirement in memory");
        return committedReqId;
    }

    private static EObject createRequirementUsage(ClassLoader loader, String reqId) throws Exception {
        Class<?> factoryType = Class.forName(
                "com.dassault_systemes.modeler.sysml.model.sysml.SysMLFactory", true, loader);
        Object factory = factoryType.getField("eINSTANCE").get(null);
        Object requirement = factoryType.getMethod("createRequirementUsage").invoke(factory);
        requirement.getClass().getMethod("setReqId", String.class).invoke(requirement, reqId);
        return (EObject) requirement;
    }

    private static boolean isTrackedAsAdded(EsiResource resource, EObject element) {
        if (!(element instanceof EsiObject esiObject)) {
            return false;
        }
        return resource.getAddedObjects().stream().anyMatch(added -> added.esiID().equals(esiObject.esiID()));
    }

    private static boolean containsRequirementId(Project project, String reqId) {
        IPrimaryProject primaryProject = project.getPrimaryProject();
        return primaryProject != null && primaryProject.getResourceSet().getResources().stream()
                .filter(EsiResource.class::isInstance)
                .map(EsiResource.class::cast)
                .anyMatch(resource -> containsRequirementId(resource, reqId));
    }

    private static boolean containsRequirementId(EsiResource resource, String reqId) {
        for (EObject root : resource.getContents()) {
            if (hasRequirementId(root, reqId)) {
                return true;
            }
            TreeIterator<EObject> contents = root.eAllContents();
            while (contents.hasNext()) {
                if (hasRequirementId(contents.next(), reqId)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasRequirementId(EObject element, String reqId) {
        if (!"RequirementUsage".equals(element.eClass().getName())) {
            return false;
        }
        return element.eClass().getEAllStructuralFeatures().stream()
                .filter(feature -> "reqId".equals(feature.getName()))
                .findFirst()
                .map(feature -> reqId.equals(element.eGet(feature)))
                .orElse(false);
    }

    private static void deleteUpsProject(ClassLoader loader, ProjectDescriptor descriptor) throws Exception {
        if (descriptor == null) {
            return;
        }
        Class<?> utilsImpl = Class.forName("com.nomagic.magicdraw.esi.EsiUtilsImpl", true, loader);
        Object upsUtils = utilsImpl.getMethod("getUPS").invoke(null);
        Class<?> utilsType = Class.forName("com.nomagic.magicdraw.esi.EsiUtils", true, loader);
        utilsType.getMethod("deleteProject", ProjectDescriptor.class).invoke(upsUtils, descriptor);
    }

    private static void runSysmlProjectTransactions(Project project) {
        require(project.getPrimaryModel() != null, "SysML v2 project has no primary model");
        SessionManager sessions = SessionManager.getInstance();
        require(!sessions.isSessionCreated(project), "SysML v2 project already has an active session");
        Package root = project.getPrimaryModel();

        sessions.createSession(project, "Commit SysML v2 project API probe edit");
        try {
            Package committed = createPackage(project, root, COMMITTED_PACKAGE);
            sessions.closeSession(project);
            require(containsNamed(root, COMMITTED_PACKAGE), "SysML v2 project commit did not retain its package");
            require(committed.getOwner() == root, "Committed SysML v2 probe package has the wrong owner");

            sessions.createSession(project, "Cancel SysML v2 project API probe edit");
            createPackage(project, root, CANCELLED_PACKAGE);
            sessions.cancelSession(project);
            require(!containsNamed(root, CANCELLED_PACKAGE), "SysML v2 project cancel retained its package");
            require(!sessions.isSessionCreated(project), "SysML v2 project transaction session remained open");

            boolean dirtyBeforeSave = project.isDirty();
            if (!dirtyBeforeSave) {
                project.setDirty(true, StateChangeHandler.DirtyType.HARD_DIRTY);
            }
            require(project.isDirty(), "Editable SysML v2 project did not become dirty after transaction");
            System.out.println("SYSMLV2_TRANSACTION_IN_MEMORY_OK editable=" + project.isEditable()
                    + " dirtyBeforeSave=" + dirtyBeforeSave);
        } catch (RuntimeException failure) {
            if (sessions.isSessionCreated(project)) {
                sessions.cancelSession(project);
            }
            throw failure;
        }
    }

    private static Package createPackage(Project project, Package owner, String name) {
        Package created = project.getElementsFactory().createPackageInstance();
        created.setName(name);
        created.setOwner(owner);
        return created;
    }

    private static boolean containsNamed(Package owner, String name) {
        if (owner == null) {
            return false;
        }
        for (Element element : owner.getOwnedElement()) {
            if (element instanceof NamedElement namedElement && name.equals(namedElement.getName())) {
                return true;
            }
        }
        return false;
    }

    private static Throwable unwrap(Throwable failure) {
        if (failure instanceof InvocationTargetException invocation && invocation.getCause() != null) {
            return invocation.getCause();
        }
        return failure;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
