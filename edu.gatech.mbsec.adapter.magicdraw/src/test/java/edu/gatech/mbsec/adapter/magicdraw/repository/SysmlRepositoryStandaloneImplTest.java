package edu.gatech.mbsec.adapter.magicdraw.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.gatech.mbsec.adapter.magicdraw.resources.SysMLBlock;
import edu.gatech.mbsec.adapter.magicdraw.resources.SysMLConnector;
import edu.gatech.mbsec.adapter.magicdraw.resources.SysMLFlowProperty;
import edu.gatech.mbsec.adapter.magicdraw.resources.SysMLInterfaceBlock;
import edu.gatech.mbsec.adapter.magicdraw.resources.SysMLItemFlow;
import edu.gatech.mbsec.adapter.magicdraw.resources.SysMLModel;
import edu.gatech.mbsec.adapter.magicdraw.resources.SysMLPackage;
import edu.gatech.mbsec.adapter.magicdraw.resources.SysMLPartProperty;
import edu.gatech.mbsec.adapter.magicdraw.resources.SysMLPort;
import edu.gatech.mbsec.adapter.magicdraw.resources.SysMLRequirement;
import edu.gatech.mbsec.adapter.magicdraw.resources.SysMLValueProperty;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Unit test for {@link SysmlRepositoryStandaloneImpl}: it loads the file-based
 * SysML fixture ({@code sysml-workdir.xml} on the classpath) and exposes the parsed
 * model through the {@link SysmlRepository} read API.
 */
public class SysmlRepositoryStandaloneImplTest {

	private SysmlRepositoryStandaloneImpl repository;

	@BeforeEach
	void setUp() {
		repository = new SysmlRepositoryStandaloneImpl();
		repository.refresh();
	}

	@Test
	void loadsProjectNamesFromFixture() {
		final List<String> names = repository.getProjectIds();
		assertTrue(names.contains("SUV_Example"), "expected SUV_Example project, got: " + names);
	}

	@Test
	void exposesModelForProject() {
		final List<SysMLModel> models = repository.getModels("SUV_Example");
		assertEquals(1, models.size());
		final SysMLModel model = models.get(0);
		assertNotNull(model, "model for SUV_Example should be present");
		assertEquals("SUV_Example", model.getName());
		assertEquals("http://localhost:8080/services/SUV_Example/model/SUV_Example",
				model.getAbout().toString());
	}

	@Test
	void scopesItemFlowsToTheirProject() {
		assertEquals(1, repository.getItemFlows("SUV_Example").size());
		assertTrue(repository.getItemFlows("missing-project").isEmpty());
	}

	@Test
	void supportsDemoMutationsWithoutReloadingTheFixture() throws Exception {
		final SysMLValueProperty property = new SysMLValueProperty();
		property.setName("payload");
		property.setOwner(java.net.URI.create(
				"http://localhost:8080/services/SUV_Example/blocks/Vehicle"));
		property.setDefaultValue("10");

		repository.createValueProperty(property, "SUV_Example");

		assertEquals(property,
				repository.getValuePropertyByQualifiedName("SUV_Example/valueproperties/payload"));
		assertTrue(Arrays.stream(repository.getBlockByQualifiedName(
				"SUV_Example/blocks/Vehicle").getValueProperties())
				.anyMatch(link -> property.getAbout().equals(link.getValue())));

		property.setDefaultValue("20");
		repository.updateValueProperty(property, "SUV_Example");
		assertEquals("20", repository.getValuePropertyByQualifiedName(
				"SUV_Example/valueproperties/payload").getDefaultValue());

		final int originalItemFlowCount = repository.getItemFlows("SUV_Example").size();
		final SysMLItemFlow itemFlow = new SysMLItemFlow();
		repository.createItemFlow(itemFlow, "SUV_Example");
		assertNotNull(itemFlow.getAbout());
		assertEquals(originalItemFlowCount + 1, repository.getItemFlows("SUV_Example").size());
	}

	@Test
	void supportsRepresentativeCreationOperationsAndOwnerLinks() throws Exception {
		final java.net.URI owner = java.net.URI.create(
				"http://localhost:8080/services/SUV_Example/blocks/Vehicle");
		final SysMLBlock vehicle = repository.getBlockByQualifiedName("SUV_Example/blocks/Vehicle");

		final SysMLPartProperty part = new SysMLPartProperty();
		part.setName("demoPart");
		part.setOwner(owner);
		repository.createPartProperty(part, "SUV_Example");

		final SysMLFlowProperty flow = new SysMLFlowProperty();
		flow.setName("demoFlow");
		flow.setOwner(owner);
		repository.createFlowProperty(flow, "SUV_Example");

		final SysMLPort port = new SysMLPort();
		port.setName("demoPort");
		port.setOwner(owner);
		repository.createPort(port, "SUV_Example");

		final SysMLConnector connector = new SysMLConnector();
		connector.setName("demoConnector");
		connector.setOwner(owner);
		repository.createConnector(connector, "SUV_Example");

		assertTrue(Arrays.stream(vehicle.getPartProperties())
				.anyMatch(link -> part.getAbout().equals(link.getValue())));
		assertTrue(Arrays.stream(vehicle.getFlowProperties())
				.anyMatch(link -> flow.getAbout().equals(link.getValue())));
		assertTrue(Arrays.stream(vehicle.getPorts())
				.anyMatch(link -> port.getAbout().equals(link.getValue())));
		assertTrue(Arrays.stream(vehicle.getConnectors())
				.anyMatch(link -> connector.getAbout().equals(link.getValue())));

		final SysMLBlock block = new SysMLBlock();
		block.setName("DemoBlock");
		repository.createBlock(block, "SUV_Example");

		final SysMLPackage sysmlPackage = new SysMLPackage();
		sysmlPackage.setName("DemoPackage");
		repository.createPackage(sysmlPackage, "SUV_Example");

		final SysMLRequirement requirement = new SysMLRequirement();
		requirement.setIdentifier("DEMO-1");
		repository.createRequirement(requirement, "SUV_Example");

		final SysMLInterfaceBlock interfaceBlock = new SysMLInterfaceBlock();
		interfaceBlock.setName("DemoInterface");
		repository.createInterfaceBlock(interfaceBlock, "SUV_Example");

		assertEquals(block, repository.getBlockByQualifiedName("SUV_Example/blocks/DemoBlock"));
		assertEquals(sysmlPackage, repository.getPackageByQualifiedName(
				"SUV_Example/packages/DemoPackage"));
		assertEquals(requirement, repository.getRequirementByID(
				"SUV_Example/requirements/DEMO-1"));
		assertEquals(interfaceBlock, repository.getInterfaceBlockByQualifiedName(
				"SUV_Example/interfaceblocks/DemoInterface"));
	}

	@Test
	void rejectsUnknownProjectsAndRefreshDiscardsDemoChanges() throws Exception {
		final SysMLBlock unknownProjectBlock = new SysMLBlock();
		unknownProjectBlock.setName("NotCreated");
		assertThrows(IllegalArgumentException.class,
				() -> repository.createBlock(unknownProjectBlock, "missing-project"));

		final SysMLBlock temporaryBlock = new SysMLBlock();
		temporaryBlock.setName("Temporary");
		repository.createBlock(temporaryBlock, "SUV_Example");
		assertNotNull(repository.getBlockByQualifiedName("SUV_Example/blocks/Temporary"));

		repository.refresh();
		assertNull(repository.getBlockByQualifiedName("SUV_Example/blocks/Temporary"));
	}

	@Test
	void mapsBlocksWithQualifiedAboutUris() {
		final List<SysMLBlock> blocks = repository.getBlocks("SUV_Example");
		assertTrue(blocks.size() >= 3, "fixture defines at least three blocks (Wheel, Chassis, Vehicle), got: " + blocks.size());

		final Set<String> names = blocks.stream().map(SysMLBlock::getName).collect(Collectors.toSet());
		assertTrue(names.containsAll(Arrays.asList("Wheel", "Chassis", "Vehicle")),
				"block names should match the fixture, got: " + names);

		final SysMLBlock wheel = blocks.stream()
				.filter(b -> "Wheel".equals(b.getName())).findFirst().orElseThrow();
		assertEquals("http://localhost:8080/services/SUV_Example/blocks/Wheel",
				wheel.getAbout().toString());
	}
}
