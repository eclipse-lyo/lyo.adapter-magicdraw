/*******************************************************************************
 * OSLC MagicDraw SysML adapter - manager facade.
 *
 * This class is the backwards-compatible static facade the JAX-RS services
 * talk to. It no longer depends on the proprietary MagicDraw Java API: it
 * delegates all data access to a SysmlRepository implementation. By default
 * (and for the integration tests) that implementation is
 * SysmlRepositoryStandaloneImpl, which reads a SysML model file. The
 * MagicDraw-backed implementation (SysmlRepositoryMagicDrawImpl) is provided in
 * a separate reactor module and can be selected when the MagicDraw SDK is
 * available.
 *******************************************************************************/
package edu.gatech.mbsec.adapter.magicdraw.application;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.lyo.oslc4j.core.OSLC4JUtils;

import edu.gatech.mbsec.adapter.magicdraw.repository.SysmlRepository;
import edu.gatech.mbsec.adapter.magicdraw.repository.SysmlRepositoryConfiguration;
import edu.gatech.mbsec.adapter.magicdraw.repository.SysmlRepositoryStandaloneImpl;
import edu.gatech.mbsec.adapter.magicdraw.resources.SysMLAssociationBlock;
import edu.gatech.mbsec.adapter.magicdraw.resources.SysMLBlock;
import edu.gatech.mbsec.adapter.magicdraw.resources.SysMLBlockDiagram;
import edu.gatech.mbsec.adapter.magicdraw.resources.SysMLConnector;
import edu.gatech.mbsec.adapter.magicdraw.resources.SysMLConnectorEnd;
import edu.gatech.mbsec.adapter.magicdraw.resources.SysMLFlowProperty;
import edu.gatech.mbsec.adapter.magicdraw.resources.SysMLFullPort;
import edu.gatech.mbsec.adapter.magicdraw.resources.SysMLInterfaceBlock;
import edu.gatech.mbsec.adapter.magicdraw.resources.SysMLInternalBlockDiagram;
import edu.gatech.mbsec.adapter.magicdraw.resources.SysMLItemFlow;
import edu.gatech.mbsec.adapter.magicdraw.resources.SysMLModel;
import edu.gatech.mbsec.adapter.magicdraw.resources.SysMLPackage;
import edu.gatech.mbsec.adapter.magicdraw.resources.SysMLPartProperty;
import edu.gatech.mbsec.adapter.magicdraw.resources.SysMLPort;
import edu.gatech.mbsec.adapter.magicdraw.resources.SysMLProxyPort;
import edu.gatech.mbsec.adapter.magicdraw.resources.SysMLReferenceProperty;
import edu.gatech.mbsec.adapter.magicdraw.resources.SysMLRequirement;
import edu.gatech.mbsec.adapter.magicdraw.resources.SysMLValueProperty;
import edu.gatech.mbsec.adapter.magicdraw.resources.SysMLValueType;
import java.util.logging.Logger;

public class MagicDrawManager {

	private static final Logger LOG = Logger.getLogger(MagicDrawManager.class.getName());

	private static final SysmlRepositoryConfiguration CONFIGURATION = SysmlRepositoryConfiguration.load();

	/** Selected repository implementation (standalone by default). */
	private static SysmlRepository repository = createRepository();

	/** Base URI used to build resource about-URIs. Mirrors the server's public URI. */
	public static String baseHTTPURI = computeBaseURI();

	/** Directory the (MagicDraw-backed) adapter would scan for project files. */
	public static String magicdrawModelsDirectory = CONFIGURATION.getMagicDrawModelsDirectory();

	public static boolean areSysMLProjectsLoaded = false;

	private static SysmlRepository createRepository() {
		final String className = CONFIGURATION.getRepositoryClassName();
		if (className != null && !className.isEmpty()) {
			try {
				final Class<?> implementationClass = Class.forName(className);
				SysmlRepository repo;
				try {
					repo = (SysmlRepository) implementationClass
							.getDeclaredConstructor(SysmlRepositoryConfiguration.class)
							.newInstance(CONFIGURATION);
				} catch (final NoSuchMethodException e) {
					repo = (SysmlRepository) implementationClass.getDeclaredConstructor().newInstance();
				}
				LOG.info("Using SysmlRepository implementation: " + className);
				return repo;
			} catch (final Exception e) {
				throw new IllegalStateException("Cannot instantiate configured SysmlRepository " + className, e);
			}
		}
		LOG.info("Using SysmlRepository implementation: SysmlRepositoryStandaloneImpl "
				+ "(SysML model file from classpath, no MagicDraw required)");
		return new SysmlRepositoryStandaloneImpl(CONFIGURATION);
	}

	private static String computeBaseURI() {
		final String configuredPublicUri = CONFIGURATION.getPublicUri();
		if (configuredPublicUri != null) {
			return configuredPublicUri;
		}
		final String publicURI = OSLC4JUtils.getPublicURI();
		if (publicURI != null && !publicURI.isEmpty()) {
			return publicURI;
		}
		return "http://localhost:" + CONFIGURATION.getPort();
	}

	public static SysmlRepositoryConfiguration getConfiguration() {
		return CONFIGURATION;
	}

	public static void setRepository(final SysmlRepository repo) {
		repository = repo;
		areSysMLProjectsLoaded = false;
	}

	/* ---- Loading --------------------------------------------------------- */

	public static synchronized void loadSysMLProjects() {
		if (areSysMLProjectsLoaded) {
			return;
		}
		repository.setBaseURI(baseHTTPURI);
		repository.refresh();
		areSysMLProjectsLoaded = true;
	}

	public static synchronized void reloadSysMLProjects() {
		areSysMLProjectsLoaded = false;
		loadSysMLProjects();
	}

	public static void loadSysMLProject(final String projectId) {
		loadSysMLProjects();
	}

	public static void makeSysMLProjectActive(final String projectId) {
		// no-op in standalone mode
	}

	/* ---- Projects -------------------------------------------------------- */

	public static List<String> getProjectNames() {
		return repository.getProjectIds();
	}

	/* ---- Models ---------------------------------------------------------- */

	public static List<SysMLModel> getModels(final String projectId) {
		return repository.getModels(projectId);
	}

	public static SysMLModel getModelByName(final String modelName) {
		return repository.getModelByName(modelName);
	}

	/* ---- Blocks and their properties ------------------------------------- */

	public static List<SysMLBlock> getBlocks(final String projectName) {
		return repository.getBlocks(projectName);
	}

	public static SysMLBlock getBlockByQualifiedName(final String qualifiedName) {
		return repository.getBlockByQualifiedName(qualifiedName);
	}

	public static SysMLBlock getBlockByQualifiedName(final String projectId, final String blockName) {
		return getBlockByQualifiedName(resourceQualifiedName(projectId, "blocks", blockName));
	}

	public static List<SysMLPartProperty> getPartProperties(final String projectName) {
		return repository.getPartProperties(projectName);
	}

	public static SysMLPartProperty getPartPropertyByQualifiedName(final String qualifiedName) {
		return repository.getPartPropertyByQualifiedName(qualifiedName);
	}

	public static SysMLPartProperty getPartPropertyByQualifiedName(final String projectId,
			final String partPropertyName) {
		return getPartPropertyByQualifiedName(resourceQualifiedName(projectId, "partproperties", partPropertyName));
	}

	public static List<SysMLReferenceProperty> getReferenceProperties(final String projectName) {
		return repository.getReferenceProperties(projectName);
	}

	public static SysMLReferenceProperty getReferencePropertyByQualifiedName(final String qualifiedName) {
		return repository.getReferencePropertyByQualifiedName(qualifiedName);
	}

	public static SysMLReferenceProperty getReferencePropertyByQualifiedName(final String projectId,
			final String referencePropertyName) {
		return getReferencePropertyByQualifiedName(
				resourceQualifiedName(projectId, "referenceproperties", referencePropertyName));
	}

	public static List<SysMLValueProperty> getValueProperties(final String projectName) {
		return repository.getValueProperties(projectName);
	}

	public static SysMLValueProperty getValuePropertyByQualifiedName(final String qualifiedName) {
		return repository.getValuePropertyByQualifiedName(qualifiedName);
	}

	public static SysMLValueProperty getValuePropertyByQualifiedName(final String projectId,
			final String valuePropertyName) {
		return getValuePropertyByQualifiedName(resourceQualifiedName(projectId, "valueproperties", valuePropertyName));
	}

	public static List<SysMLValueType> getValueTypes(final String projectName) {
		return repository.getValueTypes(projectName);
	}

	public static SysMLValueType getValueTypeByQualifiedName(final String qualifiedName) {
		return repository.getValueTypeByQualifiedName(qualifiedName);
	}

	public static SysMLValueType getValueTypeByQualifiedName(final String projectId, final String valueTypeName) {
		return getValueTypeByQualifiedName(resourceQualifiedName(projectId, "valuetypes", valueTypeName));
	}

	public static List<SysMLFlowProperty> getFlowProperties(final String projectName) {
		return repository.getFlowProperties(projectName);
	}

	public static SysMLFlowProperty getFlowPropertyByQualifiedName(final String qualifiedName) {
		return repository.getFlowPropertyByQualifiedName(qualifiedName);
	}

	public static SysMLFlowProperty getFlowPropertyByQualifiedName(final String projectId,
			final String flowPropertyName) {
		return getFlowPropertyByQualifiedName(resourceQualifiedName(projectId, "flowproperties", flowPropertyName));
	}

	/* ---- Blocks composition ---------------------------------------------- */

	public static List<SysMLInterfaceBlock> getInterfaceBlocks(final String projectName) {
		return repository.getInterfaceBlocks(projectName);
	}

	public static SysMLInterfaceBlock getInterfaceBlockByQualifiedName(final String qualifiedName) {
		return repository.getInterfaceBlockByQualifiedName(qualifiedName);
	}

	public static SysMLInterfaceBlock getInterfaceBlockByQualifiedName(final String projectId,
			final String interfaceBlockName) {
		return getInterfaceBlockByQualifiedName(
				resourceQualifiedName(projectId, "interfaceblocks", interfaceBlockName));
	}

	public static List<SysMLItemFlow> getItemFlows(final String projectId) {
		return repository.getItemFlows(projectId);
	}

	public static SysMLItemFlow getItemFlowByQualifiedName(final String qualifiedName) {
		return repository.getItemFlowByQualifiedName(qualifiedName);
	}

	public static SysMLItemFlow getItemFlowByQualifiedName(final String projectId,
			final String itemFlowName) {
		return getItemFlowByQualifiedName(resourceQualifiedName(projectId, "itemflows", itemFlowName));
	}

	public static List<SysMLPort> getPorts(final String projectName) {
		return repository.getPorts(projectName);
	}

	public static SysMLPort getPortByQualifiedName(final String qualifiedName) {
		return repository.getPortByQualifiedName(qualifiedName);
	}

	public static SysMLPort getPortByQualifiedName(final String projectId, final String portName) {
		return getPortByQualifiedName(resourceQualifiedName(projectId, "ports", portName));
	}

	public static List<SysMLProxyPort> getProxyPorts(final String projectName) {
		return repository.getProxyPorts(projectName);
	}

	public static SysMLProxyPort getProxyPortByQualifiedName(final String qualifiedName) {
		return repository.getProxyPortByQualifiedName(qualifiedName);
	}

	public static SysMLProxyPort getProxyPortByQualifiedName(final String projectId,
			final String proxyPortName) {
		return getProxyPortByQualifiedName(resourceQualifiedName(projectId, "proxyports", proxyPortName));
	}

	public static List<SysMLFullPort> getFullPorts(final String projectName) {
		return repository.getFullPorts(projectName);
	}

	public static SysMLFullPort getFullPortByQualifiedName(final String qualifiedName) {
		return repository.getFullPortByQualifiedName(qualifiedName);
	}

	public static SysMLFullPort getFullPortByQualifiedName(final String projectId, final String fullPortName) {
		return getFullPortByQualifiedName(resourceQualifiedName(projectId, "fullports", fullPortName));
	}

	public static List<SysMLConnector> getConnectors(final String projectName) {
		return repository.getConnectors(projectName);
	}

	public static SysMLConnector getConnectorByQualifiedName(final String qualifiedName) {
		return repository.getConnectorByQualifiedName(qualifiedName);
	}

	public static SysMLConnector getConnectorByQualifiedName(final String projectId, final String connectorName) {
		return getConnectorByQualifiedName(resourceQualifiedName(projectId, "connectors", connectorName));
	}

	public static List<SysMLConnectorEnd> getConnectorEnds(final String projectName) {
		return repository.getConnectorEnds(projectName);
	}

	public static SysMLConnectorEnd getConnectorEndByQualifiedName(final String qualifiedName) {
		return repository.getConnectorEndByQualifiedName(qualifiedName);
	}

	public static SysMLConnectorEnd getConnectorEndByQualifiedName(final String projectId,
			final String connectorEndName) {
		return getConnectorEndByQualifiedName(resourceQualifiedName(projectId, "connectorends", connectorEndName));
	}

	/* ---- Requirements, packages, diagrams, association blocks ----------- */

	public static List<SysMLRequirement> getRequirements(final String projectName) {
		return repository.getRequirements(projectName);
	}

	public static SysMLRequirement getRequirementByID(final String qualifiedName) {
		return repository.getRequirementByID(qualifiedName);
	}

	public static SysMLRequirement getRequirementByID(final String projectId, final String requirementId) {
		return getRequirementByID(resourceQualifiedName(projectId, "requirements", requirementId));
	}

	public static List<SysMLPackage> getPackages(final String projectName) {
		return repository.getPackages(projectName);
	}

	public static SysMLPackage getPackageByQualifiedName(final String qualifiedName) {
		return repository.getPackageByQualifiedName(qualifiedName);
	}

	public static SysMLPackage getPackageByQualifiedName(final String projectId, final String packageName) {
		return getPackageByQualifiedName(resourceQualifiedName(projectId, "packages", packageName));
	}

	public static List<SysMLAssociationBlock> getAssociationBlocks(final String projectName) {
		return repository.getAssociationBlocks(projectName);
	}

	public static SysMLAssociationBlock getAssociationBlockByQualifiedName(final String qualifiedName) {
		return repository.getAssociationBlockByQualifiedName(qualifiedName);
	}

	public static SysMLAssociationBlock getAssociationBlockByQualifiedName(final String projectId,
			final String associationBlockName) {
		return getAssociationBlockByQualifiedName(
				resourceQualifiedName(projectId, "associationblocks", associationBlockName));
	}

	public static List<SysMLBlockDiagram> getBlockDiagrams(final String projectName) {
		return repository.getBlockDiagrams(projectName);
	}

	public static SysMLBlockDiagram getBlockDiagramByQualifiedName(final String qualifiedName) {
		return repository.getBlockDiagramByQualifiedName(qualifiedName);
	}

	public static SysMLBlockDiagram getBlockDiagramByQualifiedName(final String projectId,
			final String blockDiagramName) {
		return getBlockDiagramByQualifiedName(resourceQualifiedName(projectId, "blockdiagrams", blockDiagramName));
	}

	public static List<SysMLInternalBlockDiagram> getInternalBlockDiagrams(final String projectName) {
		return repository.getInternalBlockDiagrams(projectName);
	}

	public static SysMLInternalBlockDiagram getInternalBlockDiagramByQualifiedName(final String qualifiedName) {
		return repository.getInternalBlockDiagramByQualifiedName(qualifiedName);
	}

	public static SysMLInternalBlockDiagram getInternalBlockDiagramByQualifiedName(final String projectId,
			final String internalBlockDiagramName) {
		return getInternalBlockDiagramByQualifiedName(
				resourceQualifiedName(projectId, "internalblockdiagrams", internalBlockDiagramName));
	}

	/* ---- Creation -------------------------------------------------------- */

	public static void createSysMLBlock(final SysMLBlock sysMLBlock, final String projectId) {
		repository.createBlock(sysMLBlock, projectId);
	}

	public static void createSysMLPartProperty(final SysMLPartProperty sysMLPart, final String projectId) {
		repository.createPartProperty(sysMLPart, projectId);
	}

	public static SysMLReferenceProperty createSysMLReferenceProperty(final String newElementName,
			final String ownerName, final String projectId) {
		return repository.createReferenceProperty(newElementName, ownerName, projectId);
	}

	public static void createSysMLValueProperty(final SysMLValueProperty sysMLValueProperty,
			final String projectId) {
		repository.createValueProperty(sysMLValueProperty, projectId);
	}

	public static void updateSysMLValueProperty(final SysMLValueProperty sysMLValuePropertyToUpdate,
			final String projectId) {
		repository.updateValueProperty(sysMLValuePropertyToUpdate, projectId);
	}

	public static void createSysMLRequirement2(final SysMLRequirement sysmlRequirement,
			final String projectId) {
		repository.createRequirement(sysmlRequirement, projectId);
	}

	public static void createSysMLConnector(final SysMLConnector sysMLConnector, final String projectId) {
		repository.createConnector(sysMLConnector, projectId);
	}

	public static void createSysMLConnectorEnd(final SysMLConnectorEnd sysMLConnectorEnd, final String projectId) {
		repository.createConnectorEnd(sysMLConnectorEnd, projectId);
	}

	public static void createSysMLPort(final SysMLPort sysMLPort, final String projectId) {
		repository.createPort(sysMLPort, projectId);
	}

	public static void createSysMLPackage(final SysMLPackage sysMLPackage, final String projectId) {
		repository.createPackage(sysMLPackage, projectId);
	}

	public static void createSysMLItemFlow(final SysMLItemFlow sysMLItemFlow, final String projectId) {
		repository.createItemFlow(sysMLItemFlow, projectId);
	}

	public static void createSysMLInterfaceBlock(final SysMLInterfaceBlock sysMLInterfaceBlock, final String projectId) {
		repository.createInterfaceBlock(sysMLInterfaceBlock, projectId);
	}

	public static void createSysMLFlowProperty(final SysMLFlowProperty sysMLFlowProperty, final String projectId) {
		repository.createFlowProperty(sysMLFlowProperty, projectId);
	}

	/* ---- URI / qualified-name helpers ------------------------------------ */

	private static String resourceQualifiedName(final String projectId, final String resourcePath,
			final String resourceName) {
		return projectId + "/" + resourcePath + "/" + resourceName;
	}

	public static URI getURIFromQualifiedName(final String typeAndQualifiedName) {
		// Best-effort reconstruction used by creation handlers.
		final String path = typeAndQualifiedName.replaceFirst("_", "/");
		return URI.create(baseHTTPURI + "/services/" + path);
	}

	public static String getQualifiedNameFromURI(final URI uri) {
		String path = uri.getRawPath();
		path = path.replace("/services/", "");
		final String[] segments = path.split("/");
		return segments[segments.length - 1];
	}

	public static ArrayList<java.io.File> getMagicDrawModels(final String directoryName,
			final ArrayList<java.io.File> files) {
		return files;
	}
}
