/*******************************************************************************
 * OSLC MagicDraw SysML adapter - standalone (file based) repository.
 *
 * Reads a SysML model description from an XML file (by default the classpath
 * resource /sysml-workdir.xml, overridable with the system property
 * sysml.repository.modelFile) and maps it to the OSLC resource objects the
 * adapter publishes. This is the default backing store: it requires no
 * MagicDraw installation and is what the integration tests exercise.
 *
 * The mapping reproduces the qualified-name keying scheme of the original
 * MagicDrawManager: an element of type T belonging to project P and named N is
 * stored under the key "P/<typePath>/N" and published at the URI
 * <baseURI>/services/P/<typePath>/N.
 *******************************************************************************/
package edu.gatech.mbsec.adapter.magicdraw.repository;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import edu.gatech.mbsec.adapter.magicdraw.resources.Constants;
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
import org.eclipse.lyo.oslc4j.core.model.Link;

public class SysmlRepositoryStandaloneImpl implements SysmlRepository {

	private String baseURI = "http://localhost:8080";
	private final SysmlRepositoryConfiguration configuration;

	public SysmlRepositoryStandaloneImpl() {
		this(SysmlRepositoryConfiguration.load());
	}

	public SysmlRepositoryStandaloneImpl(final SysmlRepositoryConfiguration configuration) {
		this.configuration = configuration;
	}

	// qualified-name -> resource maps (key = "<projectId>/<typePath>/<name>")
	private final Map<String, SysMLModel> models = new HashMap<>();
	private final Map<String, SysMLBlock> blocks = new HashMap<>();
	private final Map<String, SysMLPartProperty> partProperties = new HashMap<>();
	private final Map<String, SysMLReferenceProperty> referenceProperties = new HashMap<>();
	private final Map<String, SysMLValueProperty> valueProperties = new HashMap<>();
	private final Map<String, SysMLValueType> valueTypes = new HashMap<>();
	private final Map<String, SysMLFlowProperty> flowProperties = new HashMap<>();
	private final Map<String, SysMLInterfaceBlock> interfaceBlocks = new HashMap<>();
	private final Map<String, SysMLItemFlow> itemFlows = new HashMap<>();
	private final Map<String, SysMLPort> ports = new HashMap<>();
	private final Map<String, SysMLProxyPort> proxyPorts = new HashMap<>();
	private final Map<String, SysMLFullPort> fullPorts = new HashMap<>();
	private final Map<String, SysMLConnector> connectors = new HashMap<>();
	private final Map<String, SysMLConnectorEnd> connectorEnds = new HashMap<>();
	private final Map<String, SysMLRequirement> requirements = new HashMap<>();
	private final Map<String, SysMLPackage> packages = new HashMap<>();
	private final Map<String, SysMLAssociationBlock> associationBlocks = new HashMap<>();
	private final Map<String, SysMLBlockDiagram> blockDiagrams = new HashMap<>();
	private final Map<String, SysMLInternalBlockDiagram> internalBlockDiagrams = new HashMap<>();

	private final List<String> projectNames = new ArrayList<>();

	private static final String PATH_MODEL = "model";
	private static final String PATH_PACKAGE = "packages";
	private static final String PATH_BLOCK = "blocks";
	private static final String PATH_PARTPROPERTY = "partproperties";
	private static final String PATH_REFERENCEPROPERTY = "referenceproperties";
	private static final String PATH_VALUEPROPERTY = "valueproperties";
	private static final String PATH_VALUETYPE = "valuetypes";
	private static final String PATH_FLOWPROPERTY = "flowproperties";
	private static final String PATH_INTERFACEBLOCK = "interfaceblocks";
	private static final String PATH_ITEMFLOW = "itemflows";
	private static final String PATH_PORT = "ports";
	private static final String PATH_PROXYPORT = "proxyports";
	private static final String PATH_FULLPORT = "fullports";
	private static final String PATH_CONNECTOR = "connectors";
	private static final String PATH_CONNECTOREND = "connectorends";
	private static final String PATH_REQUIREMENT = "requirements";
	private static final String PATH_ASSOCIATIONBLOCK = "associationblocks";
	private static final String PATH_BLOCKDIAGRAM = "blockdiagrams";
	private static final String PATH_INTERNALBLOCKDIAGRAM = "internalblockdiagrams";

	public void setBaseURI(final String baseURI) {
		this.baseURI = baseURI;
	}

	private URI uri(final String projectId, final String typePath, final String name) {
		return URI.create(baseURI + "/services/" + projectId + "/" + typePath + "/" + encode(name));
	}

	private static String encode(final String name) {
		return name == null ? "" : name.replaceAll(" ", "_");
	}

	private static <T> T instantiate(final Class<T> clazz) {
		try {
			return clazz.getDeclaredConstructor().newInstance();
		} catch (final Exception e) {
			throw new RuntimeException("Failed to instantiate " + clazz.getName(), e);
		}
	}

	private static void setAbout(final Object resource, final URI about) {
		try {
			resource.getClass().getMethod("setAbout", URI.class).invoke(resource, about);
		} catch (final NoSuchMethodException e) {
			// resource type does not expose an about URI
		} catch (final Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static void setName(final Object resource, final String name) {
		try {
			resource.getClass().getMethod("setName", String.class).invoke(resource, name);
		} catch (final NoSuchMethodException e) {
			// resource type does not expose a name (e.g. SysMLItemFlow)
		} catch (final Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static void setRdfType(final Object resource, final String typeUri) {
		try {
			resource.getClass().getMethod("setRdfTypes", URI[].class)
					.invoke(resource, (Object) new URI[] { URI.create(typeUri) });
		} catch (final NoSuchMethodException e) {
			// resource type does not expose rdf:types
		} catch (final Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static void setLinks(final Object resource, final String setter, final List<Link> links) {
		if (links == null || links.isEmpty()) {
			return;
		}
		try {
			resource.getClass().getMethod(setter, Link[].class)
					.invoke(resource, (Object) links.toArray(new Link[0]));
		} catch (final NoSuchMethodException nsme) {
			// some resource types simply do not expose this link collection
		} catch (final Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public synchronized void refresh() {
		final SysmlRepositoryStandaloneImpl replacement = new SysmlRepositoryStandaloneImpl(configuration);
		replacement.setBaseURI(baseURI);
		final InputStream in = replacement.openModelFile();
		if (in == null) {
			throw new IllegalStateException("No standalone SysML model source was found");
		}
		try (InputStream input = in) {
			final DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			dbf.setNamespaceAware(false);
			final DocumentBuilder db = dbf.newDocumentBuilder();
			final Document doc = db.parse(input);
			replacement.parse(doc.getDocumentElement());
			replaceWith(replacement);
		} catch (final Exception e) {
			throw new RuntimeException("Failed to parse SysML model file", e);
		}
	}

	private void replaceWith(final SysmlRepositoryStandaloneImpl replacement) {
		models.clear();
		models.putAll(replacement.models);
		blocks.clear();
		blocks.putAll(replacement.blocks);
		partProperties.clear();
		partProperties.putAll(replacement.partProperties);
		referenceProperties.clear();
		referenceProperties.putAll(replacement.referenceProperties);
		valueProperties.clear();
		valueProperties.putAll(replacement.valueProperties);
		valueTypes.clear();
		valueTypes.putAll(replacement.valueTypes);
		flowProperties.clear();
		flowProperties.putAll(replacement.flowProperties);
		interfaceBlocks.clear();
		interfaceBlocks.putAll(replacement.interfaceBlocks);
		itemFlows.clear();
		itemFlows.putAll(replacement.itemFlows);
		ports.clear();
		ports.putAll(replacement.ports);
		proxyPorts.clear();
		proxyPorts.putAll(replacement.proxyPorts);
		fullPorts.clear();
		fullPorts.putAll(replacement.fullPorts);
		connectors.clear();
		connectors.putAll(replacement.connectors);
		connectorEnds.clear();
		connectorEnds.putAll(replacement.connectorEnds);
		requirements.clear();
		requirements.putAll(replacement.requirements);
		packages.clear();
		packages.putAll(replacement.packages);
		associationBlocks.clear();
		associationBlocks.putAll(replacement.associationBlocks);
		blockDiagrams.clear();
		blockDiagrams.putAll(replacement.blockDiagrams);
		internalBlockDiagrams.clear();
		internalBlockDiagrams.putAll(replacement.internalBlockDiagrams);
		projectNames.clear();
		projectNames.addAll(replacement.projectNames);
	}

	private InputStream openModelFile() {
		final String file = configuration.getStandaloneModelFile();
		if (file != null) {
			try {
				return new java.io.FileInputStream(file);
			} catch (final java.io.FileNotFoundException e) {
				throw new IllegalArgumentException("Configured standalone model file does not exist: " + file, e);
			}
		}
		return getClass().getResourceAsStream("/sysml-workdir.xml");
	}

	private void parse(final Element root) {
		final NodeList projects = root.getElementsByTagName("project");
		for (int i = 0; i < projects.getLength(); i++) {
			final Element project = (Element) projects.item(i);
			final String projectId = project.getAttribute("id");
			if (projectId == null || projectId.isEmpty()) {
				continue;
			}
			projectNames.add(projectId);
			parseProject(projectId, project);
		}
	}

	private void parseProject(final String projectId, final Element project) {
		// model (one per project, named after the project)
		final Element modelEl = firstChild(project, "model");
		if (modelEl != null) {
			final String modelName = attr(modelEl, "name", projectId);
			final SysMLModel model = instantiate(SysMLModel.class);
			setAbout(model, uri(projectId, PATH_MODEL, modelName));
			setName(model, modelName);
			setRdfType(model, Constants.TYPE_SYSML_MODEL);
			models.put(projectId, model);
		}

		for (final Element pkg : children(project, "package")) {
			final SysMLPackage r = instantiate(SysMLPackage.class);
			setAbout(r, uri(projectId, PATH_PACKAGE, pkg.getAttribute("name")));
			setName(r, pkg.getAttribute("name"));
			setRdfType(r, Constants.TYPE_SYSML_PACKAGE);
			packages.put(key(projectId, PATH_PACKAGE, pkg.getAttribute("name")), r);
		}

		for (final Element req : children(project, "requirement")) {
			final String id = req.getAttribute("id");
			final SysMLRequirement r = instantiate(SysMLRequirement.class);
			setAbout(r, uri(projectId, PATH_REQUIREMENT, id));
			setName(r, attr(req, "name", id));
			setRdfType(r, Constants.TYPE_SYSML_REQUIREMENT);
			requirements.put(key(projectId, PATH_REQUIREMENT, id), r);
		}

		for (final Element vt : children(project, "valueType")) {
			final SysMLValueType r = instantiate(SysMLValueType.class);
			setAbout(r, uri(projectId, PATH_VALUETYPE, vt.getAttribute("name")));
			setName(r, vt.getAttribute("name"));
			setRdfType(r, Constants.TYPE_SYSML_VALUETYPE);
			valueTypes.put(key(projectId, PATH_VALUETYPE, vt.getAttribute("name")), r);
		}

		for (final Element ib : children(project, "interfaceBlock")) {
			final SysMLInterfaceBlock r = instantiate(SysMLInterfaceBlock.class);
			setAbout(r, uri(projectId, PATH_INTERFACEBLOCK, ib.getAttribute("name")));
			setName(r, ib.getAttribute("name"));
			setRdfType(r, Constants.TYPE_SYSML_INTERFACEBLOCK);
			interfaceBlocks.put(key(projectId, PATH_INTERFACEBLOCK, ib.getAttribute("name")), r);
		}

		for (final Element ab : children(project, "associationBlock")) {
			final SysMLAssociationBlock r = instantiate(SysMLAssociationBlock.class);
			setAbout(r, uri(projectId, PATH_ASSOCIATIONBLOCK, ab.getAttribute("name")));
			setName(r, ab.getAttribute("name"));
			setRdfType(r, Constants.TYPE_SYSML_ASSOCIATIONBLOCK);
			associationBlocks.put(key(projectId, PATH_ASSOCIATIONBLOCK, ab.getAttribute("name")), r);
		}

		for (final Element iflow : children(project, "itemFlow")) {
			final SysMLItemFlow r = instantiate(SysMLItemFlow.class);
			setAbout(r, uri(projectId, PATH_ITEMFLOW, iflow.getAttribute("name")));
			setName(r, iflow.getAttribute("name"));
			setRdfType(r, Constants.TYPE_SYSML_ITEMFLOW);
			itemFlows.put(key(projectId, PATH_ITEMFLOW, iflow.getAttribute("name")), r);
		}

		for (final Element bd : children(project, "blockDiagram")) {
			final SysMLBlockDiagram r = instantiate(SysMLBlockDiagram.class);
			setAbout(r, uri(projectId, PATH_BLOCKDIAGRAM, bd.getAttribute("name")));
			setName(r, bd.getAttribute("name"));
			setRdfType(r, Constants.TYPE_SYSML_BLOCKDIAGRAM);
			blockDiagrams.put(key(projectId, PATH_BLOCKDIAGRAM, bd.getAttribute("name")), r);
		}

		for (final Element ibd : children(project, "internalBlockDiagram")) {
			final SysMLInternalBlockDiagram r = instantiate(SysMLInternalBlockDiagram.class);
			setAbout(r, uri(projectId, PATH_INTERNALBLOCKDIAGRAM, ibd.getAttribute("name")));
			setName(r, ibd.getAttribute("name"));
			setRdfType(r, Constants.TYPE_SYSML_INTERNALBLOCKDIAGRAM);
			internalBlockDiagrams.put(key(projectId, PATH_INTERNALBLOCKDIAGRAM, ibd.getAttribute("name")), r);
		}

		for (final Element block : children(project, "block")) {
			parseBlock(projectId, block);
		}
	}

	private void parseBlock(final String projectId, final Element blockEl) {
		final String blockName = blockEl.getAttribute("name");
		final SysMLBlock block = instantiate(SysMLBlock.class);
		setAbout(block, uri(projectId, PATH_BLOCK, blockName));
		setName(block, blockName);
		setRdfType(block, Constants.TYPE_SYSML_BLOCK);
		blocks.put(key(projectId, PATH_BLOCK, blockName), block);

		final List<Link> partLinks = new ArrayList<>();
		final List<Link> valueLinks = new ArrayList<>();
		final List<Link> referenceLinks = new ArrayList<>();
		final List<Link> flowLinks = new ArrayList<>();
		final List<Link> portLinks = new ArrayList<>();
		final List<Link> proxyPortLinks = new ArrayList<>();
		final List<Link> fullPortLinks = new ArrayList<>();
		final List<Link> connectorLinks = new ArrayList<>();
		final List<Link> connectorEndLinks = new ArrayList<>();
		final List<Link> nestedBlockLinks = new ArrayList<>();
		final List<Link> internalBlockDiagramLinks = new ArrayList<>();

		for (final Element child : childElements(blockEl)) {
			final String tag = child.getTagName();
			final String name = child.getAttribute("name");
			switch (tag) {
			case "partProperty":
				addSimple(projectId, child, SysMLPartProperty.class, partProperties, PATH_PARTPROPERTY,
						Constants.TYPE_SYSML_PARTPROPERTY, partLinks);
				break;
			case "valueProperty":
				addSimple(projectId, child, SysMLValueProperty.class, valueProperties, PATH_VALUEPROPERTY,
						Constants.TYPE_SYSML_VALUEPROPERTY, valueLinks);
				break;
			case "referenceProperty":
				addSimple(projectId, child, SysMLReferenceProperty.class, referenceProperties, PATH_REFERENCEPROPERTY,
						Constants.TYPE_SYSML_REFERENCEPROPERTY, referenceLinks);
				break;
			case "flowProperty":
				addSimple(projectId, child, SysMLFlowProperty.class, flowProperties, PATH_FLOWPROPERTY,
						Constants.TYPE_SYSML_FLOWPROPERTY, flowLinks);
				break;
			case "port":
				addSimple(projectId, child, SysMLPort.class, ports, PATH_PORT, Constants.TYPE_SYSML_PORT, portLinks);
				break;
			case "proxyPort":
				addSimple(projectId, child, SysMLProxyPort.class, proxyPorts, PATH_PROXYPORT,
						Constants.TYPE_SYSML_PROXYPORT, proxyPortLinks);
				break;
			case "fullPort":
				addSimple(projectId, child, SysMLFullPort.class, fullPorts, PATH_FULLPORT,
						Constants.TYPE_SYSML_FULLPORT, fullPortLinks);
				break;
			case "connector":
				addSimple(projectId, child, SysMLConnector.class, connectors, PATH_CONNECTOR,
						Constants.TYPE_SYSML_CONNECTOR, connectorLinks);
				break;
			case "connectorEnd":
				addSimple(projectId, child, SysMLConnectorEnd.class, connectorEnds, PATH_CONNECTOREND,
						Constants.TYPE_SYSML_CONNECTOREND, connectorEndLinks);
				break;
			case "nestedBlock":
				addSimple(projectId, child, SysMLBlock.class, blocks, PATH_BLOCK, Constants.TYPE_SYSML_BLOCK,
						nestedBlockLinks);
				break;
			case "internalBlockDiagram":
				addSimple(projectId, child, SysMLInternalBlockDiagram.class, internalBlockDiagrams,
						PATH_INTERNALBLOCKDIAGRAM, Constants.TYPE_SYSML_INTERNALBLOCKDIAGRAM, internalBlockDiagramLinks);
				break;
			default:
				break;
			}
		}

		setLinks(block, "setPartProperties", partLinks);
		setLinks(block, "setValueProperties", valueLinks);
		setLinks(block, "setReferenceProperties", referenceLinks);
		setLinks(block, "setFlowProperties", flowLinks);
		setLinks(block, "setPorts", portLinks);
		setLinks(block, "setProxyPorts", proxyPortLinks);
		setLinks(block, "setFullPorts", fullPortLinks);
		setLinks(block, "setConnectors", connectorLinks);
		setLinks(block, "setConnectorEnds", connectorEndLinks);
		setLinks(block, "setNestedBlocks", nestedBlockLinks);
		setLinks(block, "setInternalBlockDiagrams", internalBlockDiagramLinks);
	}

	private <T> void addSimple(final String projectId, final Element el, final Class<T> clazz,
			final Map<String, T> map, final String typePath, final String typeUri, final List<Link> links) {
		final String name = el.getAttribute("name");
		final T r = instantiate(clazz);
		setAbout(r, uri(projectId, typePath, name));
		setName(r, name);
		setRdfType(r, typeUri);
		map.put(key(projectId, typePath, name), r);
		links.add(new Link(uri(projectId, typePath, name)));
	}

	private static String key(final String projectId, final String typePath, final String name) {
		return projectId + "/" + typePath + "/" + name;
	}

	private static String attr(final Element el, final String name, final String dflt) {
		final String v = el.getAttribute(name);
		return (v == null || v.isEmpty()) ? dflt : v;
	}

	private static Element firstChild(final Element parent, final String tag) {
		final NodeList nl = parent.getElementsByTagName(tag);
		return nl.getLength() > 0 ? (Element) nl.item(0) : null;
	}

	private static List<Element> children(final Element parent, final String tag) {
		final List<Element> result = new ArrayList<>();
		final NodeList nl = parent.getElementsByTagName(tag);
		for (int i = 0; i < nl.getLength(); i++) {
			result.add((Element) nl.item(i));
		}
		return result;
	}

	private static List<Element> childElements(final Element parent) {
		final List<Element> result = new ArrayList<>();
		final NodeList nl = parent.getChildNodes();
		for (int i = 0; i < nl.getLength(); i++) {
			final Node n = nl.item(i);
			if (n.getNodeType() == Node.ELEMENT_NODE) {
				result.add((Element) n);
			}
		}
		return result;
	}

	/* ---- SysmlRepository -------------------------------------------------- */

	@Override
	public List<String> getProjectIds() {
		return new ArrayList<>(projectNames);
	}

	@Override
	public List<SysMLModel> getModels(final String projectId) {
		final List<SysMLModel> result = new ArrayList<>();
		final SysMLModel model = models.get(projectId);
		if (model != null) {
			result.add(model);
		}
		return result;
	}

	@Override
	public SysMLModel getModelByName(final String modelName) {
		return models.get(modelName);
	}

	@Override
	public List<SysMLBlock> getBlocks(final String projectName) {
		return byPrefix(blocks, projectName + "/" + PATH_BLOCK + "/");
	}

	@Override
	public SysMLBlock getBlockByQualifiedName(final String qualifiedName) {
		return blocks.get(qualifiedName);
	}

	@Override
	public List<SysMLPartProperty> getPartProperties(final String projectName) {
		return byPrefix(partProperties, projectName + "/" + PATH_PARTPROPERTY + "/");
	}

	@Override
	public SysMLPartProperty getPartPropertyByQualifiedName(final String qualifiedName) {
		return partProperties.get(qualifiedName);
	}

	@Override
	public List<SysMLReferenceProperty> getReferenceProperties(final String projectName) {
		return byPrefix(referenceProperties, projectName + "/" + PATH_REFERENCEPROPERTY + "/");
	}

	@Override
	public SysMLReferenceProperty getReferencePropertyByQualifiedName(final String qualifiedName) {
		return referenceProperties.get(qualifiedName);
	}

	@Override
	public List<SysMLValueProperty> getValueProperties(final String projectName) {
		return byPrefix(valueProperties, projectName + "/" + PATH_VALUEPROPERTY + "/");
	}

	@Override
	public SysMLValueProperty getValuePropertyByQualifiedName(final String qualifiedName) {
		return valueProperties.get(qualifiedName);
	}

	@Override
	public List<SysMLValueType> getValueTypes(final String projectName) {
		return byPrefix(valueTypes, projectName + "/" + PATH_VALUETYPE + "/");
	}

	@Override
	public SysMLValueType getValueTypeByQualifiedName(final String qualifiedName) {
		return valueTypes.get(qualifiedName);
	}

	@Override
	public List<SysMLFlowProperty> getFlowProperties(final String projectName) {
		return byPrefix(flowProperties, projectName + "/" + PATH_FLOWPROPERTY + "/");
	}

	@Override
	public SysMLFlowProperty getFlowPropertyByQualifiedName(final String qualifiedName) {
		return flowProperties.get(qualifiedName);
	}

	@Override
	public List<SysMLInterfaceBlock> getInterfaceBlocks(final String projectName) {
		return byPrefix(interfaceBlocks, projectName + "/" + PATH_INTERFACEBLOCK + "/");
	}

	@Override
	public SysMLInterfaceBlock getInterfaceBlockByQualifiedName(final String qualifiedName) {
		return interfaceBlocks.get(qualifiedName);
	}

	@Override
	public List<SysMLItemFlow> getItemFlows(final String projectId) {
		return byPrefix(itemFlows, projectId + "/" + PATH_ITEMFLOW + "/");
	}

	@Override
	public SysMLItemFlow getItemFlowByQualifiedName(final String qualifiedName) {
		return itemFlows.get(qualifiedName);
	}

	@Override
	public List<SysMLPort> getPorts(final String projectName) {
		return byPrefix(ports, projectName + "/" + PATH_PORT + "/");
	}

	@Override
	public SysMLPort getPortByQualifiedName(final String qualifiedName) {
		return ports.get(qualifiedName);
	}

	@Override
	public List<SysMLProxyPort> getProxyPorts(final String projectName) {
		return byPrefix(proxyPorts, projectName + "/" + PATH_PROXYPORT + "/");
	}

	@Override
	public SysMLProxyPort getProxyPortByQualifiedName(final String qualifiedName) {
		return proxyPorts.get(qualifiedName);
	}

	@Override
	public List<SysMLFullPort> getFullPorts(final String projectName) {
		return byPrefix(fullPorts, projectName + "/" + PATH_FULLPORT + "/");
	}

	@Override
	public SysMLFullPort getFullPortByQualifiedName(final String qualifiedName) {
		return fullPorts.get(qualifiedName);
	}

	@Override
	public List<SysMLConnector> getConnectors(final String projectName) {
		return byPrefix(connectors, projectName + "/" + PATH_CONNECTOR + "/");
	}

	@Override
	public SysMLConnector getConnectorByQualifiedName(final String qualifiedName) {
		return connectors.get(qualifiedName);
	}

	@Override
	public List<SysMLConnectorEnd> getConnectorEnds(final String projectName) {
		return byPrefix(connectorEnds, projectName + "/" + PATH_CONNECTOREND + "/");
	}

	@Override
	public SysMLConnectorEnd getConnectorEndByQualifiedName(final String qualifiedName) {
		return connectorEnds.get(qualifiedName);
	}

	@Override
	public List<SysMLRequirement> getRequirements(final String projectName) {
		return byPrefix(requirements, projectName + "/" + PATH_REQUIREMENT + "/");
	}

	@Override
	public SysMLRequirement getRequirementByID(final String qualifiedName) {
		return requirements.get(qualifiedName);
	}

	@Override
	public List<SysMLPackage> getPackages(final String projectName) {
		return byPrefix(packages, projectName + "/" + PATH_PACKAGE + "/");
	}

	@Override
	public SysMLPackage getPackageByQualifiedName(final String qualifiedName) {
		return packages.get(qualifiedName);
	}

	@Override
	public List<SysMLAssociationBlock> getAssociationBlocks(final String projectName) {
		return byPrefix(associationBlocks, projectName + "/" + PATH_ASSOCIATIONBLOCK + "/");
	}

	@Override
	public SysMLAssociationBlock getAssociationBlockByQualifiedName(final String qualifiedName) {
		return associationBlocks.get(qualifiedName);
	}

	@Override
	public List<SysMLBlockDiagram> getBlockDiagrams(final String projectName) {
		return byPrefix(blockDiagrams, projectName + "/" + PATH_BLOCKDIAGRAM + "/");
	}

	@Override
	public SysMLBlockDiagram getBlockDiagramByQualifiedName(final String qualifiedName) {
		return blockDiagrams.get(qualifiedName);
	}

	@Override
	public List<SysMLInternalBlockDiagram> getInternalBlockDiagrams(final String projectName) {
		return byPrefix(internalBlockDiagrams, projectName + "/" + PATH_INTERNALBLOCKDIAGRAM + "/");
	}

	@Override
	public SysMLInternalBlockDiagram getInternalBlockDiagramByQualifiedName(final String qualifiedName) {
		return internalBlockDiagrams.get(qualifiedName);
	}

	@Override
	public void createBlock(final SysMLBlock block, final String projectId) {
		addNamed(block, projectId, PATH_BLOCK, Constants.TYPE_SYSML_BLOCK, blocks);
	}

	@Override
	public void createPartProperty(final SysMLPartProperty partProperty, final String projectId) {
		addNamed(partProperty, projectId, PATH_PARTPROPERTY, Constants.TYPE_SYSML_PARTPROPERTY, partProperties);
		linkToOwnerBlock(partProperty, "getPartProperties", "setPartProperties");
	}

	@Override
	public SysMLReferenceProperty createReferenceProperty(final String name, final String ownerName,
			final String projectId) {
		requireProject(projectId);
		final SysMLReferenceProperty property = instantiate(SysMLReferenceProperty.class);
		setName(property, name);
		setAbout(property, uri(projectId, PATH_REFERENCEPROPERTY, name));
		setRdfType(property, Constants.TYPE_SYSML_REFERENCEPROPERTY);
		property.setOwner(uri(projectId, PATH_BLOCK, ownerName));
		referenceProperties.put(key(projectId, PATH_REFERENCEPROPERTY, name), property);
		linkToOwnerBlock(property, "getReferenceProperties", "setReferenceProperties");
		return property;
	}

	@Override
	public void createValueProperty(final SysMLValueProperty valueProperty, final String projectId) {
		addNamed(valueProperty, projectId, PATH_VALUEPROPERTY, Constants.TYPE_SYSML_VALUEPROPERTY, valueProperties);
		linkToOwnerBlock(valueProperty, "getValueProperties", "setValueProperties");
	}

	@Override
	public void updateValueProperty(final SysMLValueProperty valueProperty, final String projectId) {
		final String resourceKey = resourceKey(valueProperty, projectId, PATH_VALUEPROPERTY);
		if (!valueProperties.containsKey(resourceKey)) {
			throw new IllegalArgumentException("Unknown value property " + resourceKey);
		}
		valueProperties.put(resourceKey, valueProperty);
	}

	@Override
	public void createRequirement(final SysMLRequirement requirement, final String projectId) {
		addResource(requirement, projectId, PATH_REQUIREMENT, Constants.TYPE_SYSML_REQUIREMENT,
				requirements, stringProperty(requirement, "getIdentifier"));
	}

	@Override
	public void createConnector(final SysMLConnector connector, final String projectId) {
		addNamed(connector, projectId, PATH_CONNECTOR, Constants.TYPE_SYSML_CONNECTOR, connectors);
		linkToOwnerBlock(connector, "getConnectors", "setConnectors");
	}

	@Override
	public void createConnectorEnd(final SysMLConnectorEnd connectorEnd, final String projectId) {
		addResource(connectorEnd, projectId, PATH_CONNECTOREND, Constants.TYPE_SYSML_CONNECTOREND,
				connectorEnds, null);
	}

	@Override
	public void createPort(final SysMLPort port, final String projectId) {
		addNamed(port, projectId, PATH_PORT, Constants.TYPE_SYSML_PORT, ports);
		linkToOwnerBlock(port, "getPorts", "setPorts");
	}

	@Override
	public void createPackage(final SysMLPackage sysmlPackage, final String projectId) {
		addNamed(sysmlPackage, projectId, PATH_PACKAGE, Constants.TYPE_SYSML_PACKAGE, packages);
	}

	@Override
	public void createItemFlow(final SysMLItemFlow itemFlow, final String projectId) {
		addResource(itemFlow, projectId, PATH_ITEMFLOW, Constants.TYPE_SYSML_ITEMFLOW, itemFlows, null);
	}

	@Override
	public void createInterfaceBlock(final SysMLInterfaceBlock interfaceBlock, final String projectId) {
		addNamed(interfaceBlock, projectId, PATH_INTERFACEBLOCK, Constants.TYPE_SYSML_INTERFACEBLOCK,
				interfaceBlocks);
	}

	@Override
	public void createFlowProperty(final SysMLFlowProperty flowProperty, final String projectId) {
		addNamed(flowProperty, projectId, PATH_FLOWPROPERTY, Constants.TYPE_SYSML_FLOWPROPERTY, flowProperties);
		linkToOwnerBlock(flowProperty, "getFlowProperties", "setFlowProperties");
	}

	private <T> void addNamed(final T resource, final String projectId, final String typePath,
			final String rdfType, final Map<String, T> target) {
		addResource(resource, projectId, typePath, rdfType, target, stringProperty(resource, "getName"));
	}

	private <T> void addResource(final T resource, final String projectId, final String typePath,
			final String rdfType, final Map<String, T> target, final String preferredName) {
		requireProject(projectId);
		if (resource == null) {
			throw new IllegalArgumentException("Resource must not be null");
		}
		String name = preferredName;
		final URI existingAbout = about(resource);
		if (name == null || name.isEmpty()) {
			name = lastPathSegment(existingAbout);
		}
		if (name == null || name.isEmpty()) {
			name = typePath + "-" + (target.size() + 1);
		}
		final String resourceKey = key(projectId, typePath, name);
		if (target.containsKey(resourceKey)) {
			throw new IllegalArgumentException("Resource already exists: " + resourceKey);
		}
		if (existingAbout == null) {
			setAbout(resource, uri(projectId, typePath, name));
		}
		setRdfType(resource, rdfType);
		target.put(resourceKey, resource);
	}

	private void requireProject(final String projectId) {
		if (projectId == null || !projectNames.contains(projectId)) {
			throw new IllegalArgumentException("Unknown project: " + projectId);
		}
	}

	private String resourceKey(final Object resource, final String projectId, final String typePath) {
		String name = stringProperty(resource, "getName");
		if (name == null || name.isEmpty()) {
			name = lastPathSegment(about(resource));
		}
		if (name == null || name.isEmpty()) {
			throw new IllegalArgumentException("Resource has neither a name nor an about URI");
		}
		return key(projectId, typePath, name);
	}

	private void linkToOwnerBlock(final Object resource, final String getter, final String setter) {
		final URI owner = uriProperty(resource, "getOwner");
		if (owner == null) {
			return;
		}
		SysMLBlock block = null;
		for (final SysMLBlock candidate : blocks.values()) {
			if (owner.equals(candidate.getAbout())) {
				block = candidate;
				break;
			}
		}
		if (block == null) {
			return;
		}
		try {
			final Method getterMethod = block.getClass().getMethod(getter);
			final Link[] existing = (Link[]) getterMethod.invoke(block);
			final Link[] updated = new Link[existing.length + 1];
			System.arraycopy(existing, 0, updated, 0, existing.length);
			updated[existing.length] = new Link(about(resource));
			block.getClass().getMethod(setter, Link[].class).invoke(block, (Object) updated);
		} catch (final Exception e) {
			throw new RuntimeException("Failed to update the owning block relationship", e);
		}
	}

	private static String stringProperty(final Object resource, final String getter) {
		try {
			return (String) resource.getClass().getMethod(getter).invoke(resource);
		} catch (final NoSuchMethodException e) {
			return null;
		} catch (final Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static URI uriProperty(final Object resource, final String getter) {
		try {
			return (URI) resource.getClass().getMethod(getter).invoke(resource);
		} catch (final NoSuchMethodException e) {
			return null;
		} catch (final Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static URI about(final Object resource) {
		return uriProperty(resource, "getAbout");
	}

	private static String lastPathSegment(final URI uri) {
		if (uri == null || uri.getPath() == null) {
			return null;
		}
		final String path = uri.getPath();
		final int slash = path.lastIndexOf('/');
		return slash >= 0 ? path.substring(slash + 1) : path;
	}

	private static <T> List<T> byPrefix(final Map<String, T> map, final String prefix) {
		final List<T> result = new ArrayList<>();
		for (final Map.Entry<String, T> e : map.entrySet()) {
			if (e.getKey().startsWith(prefix)) {
				result.add(e.getValue());
			}
		}
		return result;
	}
}
