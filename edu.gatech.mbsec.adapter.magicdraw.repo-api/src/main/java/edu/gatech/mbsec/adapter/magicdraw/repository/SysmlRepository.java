/*******************************************************************************
 * OSLC MagicDraw SysML adapter - repository abstraction.
 *******************************************************************************/
package edu.gatech.mbsec.adapter.magicdraw.repository;

import java.util.List;

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

/**
 * Provides the SysML resources exposed by the adapter and applies supported
 * changes to the underlying repository.
 *
 * <p>A project identifier is repository metadata used to partition resources
 * and identify an OSLC Service Provider. It is not a SysML resource or an Ecore
 * containment relationship. Qualified names use the form
 * {@code <projectId>/<typePath>/<elementName>}.</p>
 *
 * <p>Implementations must either complete a mutation or throw an exception;
 * they must not report success for an ignored operation.</p>
 */
public interface SysmlRepository {

	/**
	 * Refreshes the repository view from its configured source.
	 *
	 * <p>A failed refresh must not expose a partially replaced repository
	 * view.</p>
	 */
	void refresh();

	/**
	 * Override the base URI used when building resource {@code about} URIs.
	 * If not set, implementations derive it from the request / a default.
	 */
	void setBaseURI(String baseURI);

	/**
	 * Returns the stable repository identifiers of the available projects.
	 * One OSLC Service Provider is published for each identifier.
	 */
	List<String> getProjectIds();

	/* ---- Models --------------------------------------------------------- */

	/** Returns the models exposed through the identified project. */
	List<SysMLModel> getModels(String projectId);

	/** Resolves a model by its repository-qualified name. */
	SysMLModel getModelByName(String modelName);

	/* ---- Blocks and their properties ------------------------------------ */

	/** Returns the blocks in a project partition. */
	List<SysMLBlock> getBlocks(String projectId);

	/** Resolves a block by its repository-qualified name. */
	SysMLBlock getBlockByQualifiedName(String qualifiedName);

	/** Returns the part properties in a project partition. */
	List<SysMLPartProperty> getPartProperties(String projectId);

	/** Resolves a part property by its repository-qualified name. */
	SysMLPartProperty getPartPropertyByQualifiedName(String qualifiedName);

	/** Returns the reference properties in a project partition. */
	List<SysMLReferenceProperty> getReferenceProperties(String projectId);

	/** Resolves a reference property by its repository-qualified name. */
	SysMLReferenceProperty getReferencePropertyByQualifiedName(String qualifiedName);

	/** Returns the value properties in a project partition. */
	List<SysMLValueProperty> getValueProperties(String projectId);

	/** Resolves a value property by its repository-qualified name. */
	SysMLValueProperty getValuePropertyByQualifiedName(String qualifiedName);

	/** Returns the value types in a project partition. */
	List<SysMLValueType> getValueTypes(String projectId);

	/** Resolves a value type by its repository-qualified name. */
	SysMLValueType getValueTypeByQualifiedName(String qualifiedName);

	/** Returns the flow properties in a project partition. */
	List<SysMLFlowProperty> getFlowProperties(String projectId);

	/** Resolves a flow property by its repository-qualified name. */
	SysMLFlowProperty getFlowPropertyByQualifiedName(String qualifiedName);

	/* ---- Blocks composition --------------------------------------------- */

	/** Returns the interface blocks in a project partition. */
	List<SysMLInterfaceBlock> getInterfaceBlocks(String projectId);

	/** Resolves an interface block by its repository-qualified name. */
	SysMLInterfaceBlock getInterfaceBlockByQualifiedName(String qualifiedName);

	/** Returns the item flows in a project partition. */
	List<SysMLItemFlow> getItemFlows(String projectId);

	/** Resolves an item flow by its repository-qualified name. */
	SysMLItemFlow getItemFlowByQualifiedName(String qualifiedName);

	/** Returns the ports in a project partition. */
	List<SysMLPort> getPorts(String projectId);

	/** Resolves a port by its repository-qualified name. */
	SysMLPort getPortByQualifiedName(String qualifiedName);

	/** Returns the proxy ports in a project partition. */
	List<SysMLProxyPort> getProxyPorts(String projectId);

	/** Resolves a proxy port by its repository-qualified name. */
	SysMLProxyPort getProxyPortByQualifiedName(String qualifiedName);

	/** Returns the full ports in a project partition. */
	List<SysMLFullPort> getFullPorts(String projectId);

	/** Resolves a full port by its repository-qualified name. */
	SysMLFullPort getFullPortByQualifiedName(String qualifiedName);

	/** Returns the connectors in a project partition. */
	List<SysMLConnector> getConnectors(String projectId);

	/** Resolves a connector by its repository-qualified name. */
	SysMLConnector getConnectorByQualifiedName(String qualifiedName);

	/** Returns the connector ends in a project partition. */
	List<SysMLConnectorEnd> getConnectorEnds(String projectId);

	/** Resolves a connector end by its repository-qualified name. */
	SysMLConnectorEnd getConnectorEndByQualifiedName(String qualifiedName);

	/* ---- Requirements, packages, diagrams, association blocks ----------- */

	/** Returns the requirements in a project partition. */
	List<SysMLRequirement> getRequirements(String projectId);

	/** Resolves a requirement by its repository-qualified identifier. */
	SysMLRequirement getRequirementByID(String qualifiedName);

	/** Returns the packages in a project partition. */
	List<SysMLPackage> getPackages(String projectId);

	/** Resolves a package by its repository-qualified name. */
	SysMLPackage getPackageByQualifiedName(String qualifiedName);

	/** Returns the association blocks in a project partition. */
	List<SysMLAssociationBlock> getAssociationBlocks(String projectId);

	/** Resolves an association block by its repository-qualified name. */
	SysMLAssociationBlock getAssociationBlockByQualifiedName(String qualifiedName);

	/** Returns the block diagrams in a project partition. */
	List<SysMLBlockDiagram> getBlockDiagrams(String projectId);

	/** Resolves a block diagram by its repository-qualified name. */
	SysMLBlockDiagram getBlockDiagramByQualifiedName(String qualifiedName);

	/** Returns the internal block diagrams in a project partition. */
	List<SysMLInternalBlockDiagram> getInternalBlockDiagrams(String projectId);

	/** Resolves an internal block diagram by its repository-qualified name. */
	SysMLInternalBlockDiagram getInternalBlockDiagramByQualifiedName(String qualifiedName);

	/* ---- Mutations ------------------------------------------------------ */

	/** Creates a block in the identified project. */
	void createBlock(SysMLBlock block, String projectId);

	/** Creates a part property in the identified project. */
	void createPartProperty(SysMLPartProperty partProperty, String projectId);

	/** Creates a reference property owned by the named block. */
	SysMLReferenceProperty createReferenceProperty(String name, String ownerName, String projectId);

	/** Creates a value property in the identified project. */
	void createValueProperty(SysMLValueProperty valueProperty, String projectId);

	/** Replaces the identified value property's mutable data. */
	void updateValueProperty(SysMLValueProperty valueProperty, String projectId);

	/** Creates a requirement in the identified project. */
	void createRequirement(SysMLRequirement requirement, String projectId);

	/** Creates a connector in the identified project. */
	void createConnector(SysMLConnector connector, String projectId);

	/** Creates a connector end in the identified project. */
	void createConnectorEnd(SysMLConnectorEnd connectorEnd, String projectId);

	/** Creates a port in the identified project. */
	void createPort(SysMLPort port, String projectId);

	/** Creates a package in the identified project. */
	void createPackage(SysMLPackage sysmlPackage, String projectId);

	/** Creates an item flow in the identified project. */
	void createItemFlow(SysMLItemFlow itemFlow, String projectId);

	/** Creates an interface block in the identified project. */
	void createInterfaceBlock(SysMLInterfaceBlock interfaceBlock, String projectId);

	/** Creates a flow property in the identified project. */
	void createFlowProperty(SysMLFlowProperty flowProperty, String projectId);
}
