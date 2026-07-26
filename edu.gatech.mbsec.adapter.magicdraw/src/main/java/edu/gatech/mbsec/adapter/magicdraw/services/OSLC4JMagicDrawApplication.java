/*******************************************************************************
 * OSLC4JMagicDrawApplication registers every JAX-RS service / servlet class
 * that implements the OSLC RESTful web services, plus the MagicDrawManager
 * (which exposes the model data through a SysmlRepository).
 *
 * The JAX-RS entity providers (Jena / JSON4J) are contributed by the
 * oslc4j-jena-provider and oslc4j-json4j-provider artifacts via the JAX-RS
 * ServiceLoader mechanism, so they do not need to be registered here.
 *******************************************************************************/
package edu.gatech.mbsec.adapter.magicdraw.services;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.ws.rs.core.Application;

import edu.gatech.mbsec.adapter.magicdraw.application.MagicDrawManager;
import org.eclipse.lyo.oslc4j.provider.jena.JenaProvidersRegistry;
import org.eclipse.lyo.oslc4j.provider.json4j.Json4JProvidersRegistry;
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
import edu.gatech.mbsec.adapter.magicdraw.serviceproviders.MagicDrawServiceProviderFactory;

/**
 * @author Axel Reichwein (axel.reichwein@koneksys.com)
 * @author Sebastian Herzig (sebastian.herzig@me.gatech.edu)
 */
public class OSLC4JMagicDrawApplication extends Application {

	private static final Logger LOG = Logger.getLogger(OSLC4JMagicDrawApplication.class.getName());

	public static String portNumber = MagicDrawManager.getConfiguration().getPort();

	/** Directory the (MagicDraw-backed) adapter would scan for project files. */
	public static String magicdrawModelsDirectory =
			MagicDrawManager.getConfiguration().getMagicDrawModelsDirectory();

	/** Location of the SysML .ecore metamodel (used for HTML shape rendering). */
	public static String sysmlEcoreLocation = MagicDrawManager.getConfiguration().getEcoreLocation();

	/** File holding the configured Subversion file URLs. */
	public static String svnURLsFilePath =
			MagicDrawManager.getConfiguration().getSubversionUrlsFile();

	/** Maps a resource-shape path (e.g. "blocks") to its resource class. */
	public static final Map<String, Class<?>> RESOURCE_SHAPE_PATH_TO_RESOURCE_CLASS_MAP = new HashMap<String, Class<?>>();

	private static final Set<Class<?>> RESOURCE_CLASSES = new HashSet<Class<?>>();
	private static ScheduledExecutorService refreshExecutor;

	static {
		RESOURCE_SHAPE_PATH_TO_RESOURCE_CLASS_MAP.put("model", SysMLModel.class);
		RESOURCE_SHAPE_PATH_TO_RESOURCE_CLASS_MAP.put("packages", SysMLPackage.class);
		RESOURCE_SHAPE_PATH_TO_RESOURCE_CLASS_MAP.put("blocks", SysMLBlock.class);
		RESOURCE_SHAPE_PATH_TO_RESOURCE_CLASS_MAP.put("partproperties", SysMLPartProperty.class);
		RESOURCE_SHAPE_PATH_TO_RESOURCE_CLASS_MAP.put("referenceproperties", SysMLReferenceProperty.class);
		RESOURCE_SHAPE_PATH_TO_RESOURCE_CLASS_MAP.put("valueproperties", SysMLValueProperty.class);
		RESOURCE_SHAPE_PATH_TO_RESOURCE_CLASS_MAP.put("valuetypes", SysMLValueType.class);
		RESOURCE_SHAPE_PATH_TO_RESOURCE_CLASS_MAP.put("flowproperties", SysMLFlowProperty.class);
		RESOURCE_SHAPE_PATH_TO_RESOURCE_CLASS_MAP.put("interfaceblocks", SysMLInterfaceBlock.class);
		RESOURCE_SHAPE_PATH_TO_RESOURCE_CLASS_MAP.put("itemflows", SysMLItemFlow.class);
		RESOURCE_SHAPE_PATH_TO_RESOURCE_CLASS_MAP.put("ports", SysMLPort.class);
		RESOURCE_SHAPE_PATH_TO_RESOURCE_CLASS_MAP.put("proxyports", SysMLProxyPort.class);
		RESOURCE_SHAPE_PATH_TO_RESOURCE_CLASS_MAP.put("fullports", SysMLFullPort.class);
		RESOURCE_SHAPE_PATH_TO_RESOURCE_CLASS_MAP.put("connectors", SysMLConnector.class);
		RESOURCE_SHAPE_PATH_TO_RESOURCE_CLASS_MAP.put("connectorends", SysMLConnectorEnd.class);
		RESOURCE_SHAPE_PATH_TO_RESOURCE_CLASS_MAP.put("requirements", SysMLRequirement.class);
		RESOURCE_SHAPE_PATH_TO_RESOURCE_CLASS_MAP.put("associationblocks", SysMLAssociationBlock.class);
		RESOURCE_SHAPE_PATH_TO_RESOURCE_CLASS_MAP.put("blockdiagrams", SysMLBlockDiagram.class);
		RESOURCE_SHAPE_PATH_TO_RESOURCE_CLASS_MAP.put("internalblockdiagrams", SysMLInternalBlockDiagram.class);

		// OSLC4J entity providers (RDF/XML, JSON, ...) — these artifacts do not
		// ship a META-INF/services file, so they must be registered explicitly.
		RESOURCE_CLASSES.addAll(JenaProvidersRegistry.getProviders());
		RESOURCE_CLASSES.addAll(Json4JProvidersRegistry.getProviders());

		RESOURCE_CLASSES.add(ServiceProviderCatalogService.class);
		RESOURCE_CLASSES.add(ServiceProviderService.class);

		RESOURCE_CLASSES.add(SysMLRequirementService.class);
		RESOURCE_CLASSES.add(SysMLBlockService.class);
		RESOURCE_CLASSES.add(SysMLPartPropertyService.class);
		RESOURCE_CLASSES.add(SysMLReferencePropertyService.class);
		RESOURCE_CLASSES.add(SysMLModelService.class);
		RESOURCE_CLASSES.add(SysMLPackageService.class);
		RESOURCE_CLASSES.add(SysMLAssociationBlockService.class);
		RESOURCE_CLASSES.add(SysMLConnectorService.class);
		RESOURCE_CLASSES.add(SysMLConnectorEndService.class);
		RESOURCE_CLASSES.add(SysMLPortService.class);
		RESOURCE_CLASSES.add(SysMLProxyPortService.class);
		RESOURCE_CLASSES.add(SysMLFullPortService.class);
		RESOURCE_CLASSES.add(SysMLInterfaceBlockService.class);
		RESOURCE_CLASSES.add(SysMLFlowPropertyService.class);
		RESOURCE_CLASSES.add(SysMLItemFlowService.class);
		RESOURCE_CLASSES.add(SysMLValuePropertyService.class);
		RESOURCE_CLASSES.add(SysMLValueTypeService.class);
		RESOURCE_CLASSES.add(SysMLBlockDiagramService.class);
		RESOURCE_CLASSES.add(SysMLInternalBlockDiagramService.class);

		RESOURCE_CLASSES.add(SysMLSVNFileURLService.class);
		RESOURCE_CLASSES.add(ResourceShapeService.class);
		RESOURCE_CLASSES.add(RDFVocabularyService.class);

		// Load the model(s) through the repository (standalone file backend by
		// default). Safe to call repeatedly; the repository replaces its data.
		MagicDrawManager.loadSysMLProjects();

		final int refreshInterval = MagicDrawManager.getConfiguration().getRefreshIntervalSeconds();
		if (refreshInterval > 0) {
			refreshExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
				final Thread thread = new Thread(runnable, "sysml-repository-refresh");
				thread.setDaemon(true);
				return thread;
			});
			refreshExecutor.scheduleWithFixedDelay(() -> {
				try {
					reloadModels();
				} catch (final RuntimeException e) {
					LOG.log(Level.SEVERE, "Scheduled SysML repository refresh failed", e);
				}
			}, refreshInterval, refreshInterval, TimeUnit.SECONDS);
		}

		if (MagicDrawManager.getConfiguration().syncWithSubversionRepository()
				|| MagicDrawManager.getConfiguration().useIndividualSubversionFiles()) {
			LOG.warning("Subversion synchronization was configured, but the migrated reactor does not "
					+ "contain the historical Subversion client modules. Repository refresh uses files "
					+ "already present in the configured models directory.");
		}
	}

	public OSLC4JMagicDrawApplication() {
		super();
	}

	@Override
	public Set<Class<?>> getClasses() {
		return RESOURCE_CLASSES;
	}

	/** Reload the model(s) from the backing store. */
	public static void reloadModels() {
		MagicDrawManager.reloadSysMLProjects();
	}

	/** Parse the Subversion file URLs from the cached CSV rows (no-op store in standalone mode). */
	public static java.util.List<String> readSVNFileURLs(final java.util.List<String[]> svnFileURLs) {
		final java.util.List<String> urls = new java.util.ArrayList<String>();
		if (svnFileURLs != null) {
			for (final String[] row : svnFileURLs) {
				if (row != null && row.length > 0) {
					urls.add(row[0]);
				}
			}
		}
		return urls;
	}
}
