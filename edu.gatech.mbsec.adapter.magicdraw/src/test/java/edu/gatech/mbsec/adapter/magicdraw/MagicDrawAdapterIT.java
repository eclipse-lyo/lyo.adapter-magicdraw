package edu.gatech.mbsec.adapter.magicdraw;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.restassured.RestAssured;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * Acceptance tests that exercise the full OSLC discovery chain end-to-end
 * against the adapter deployed in Jetty (see the {@code acceptance} Maven profile):
 *
 * <pre>
 *   Service Provider Catalog --&gt; Service Provider --&gt; Query Capability --&gt; individual resource
 * </pre>
 *
 * <p>The adapter is populated from a file-based SysML fixture ({@code sysml-workdir.xml})
 * loaded by {@code SysmlRepositoryStandaloneImpl}, so no licensed MagicDraw is required.
 * The assertions verify that the RDF returned by the resources corresponds to the fixture
 * data (project name, contained blocks, etc.).
 */
public class MagicDrawAdapterIT {

	private static final String BASE = "http://localhost:8080";

	/** SysML RDF vocabulary as declared by the OSLC resource classes. */
	private static final String SYSML_VOCAB = "http://localhost:8080/oslc4jmagicdraw/services/sysml-rdfvocabulary#";
	private static final Property BLOCK_NAME = prop(SYSML_VOCAB + "NamedElement_name");
	private static final Resource SYSML_BLOCK = res(SYSML_VOCAB + "Block");

	private static final String OSLC_CORE = "http://open-services.net/ns/core#";
	private static final Property QUERY_CAPABILITY = prop(OSLC_CORE + "queryCapability");
	private static final Property QUERY_BASE = prop(OSLC_CORE + "queryBase");
	private static final Resource SERVICE_PROVIDER = res(OSLC_CORE + "ServiceProvider");
	private static final Property IDENTIFIER = prop("http://purl.org/dc/terms/identifier");

	@BeforeEach
	void setUp() {
		RestAssured.baseURI = BASE;
	}

	// Step 1: the service provider catalog exposes the SysML project
	@Test
	void catalogPublishesSysmlProject() {
		final Model catalog = getRdf("/services/catalog/singleton");

		final Set<String> ids = new HashSet<>();
		final StmtIterator sps = catalog.listStatements(null, RDF.type, SERVICE_PROVIDER);
		while (sps.hasNext()) {
			final Resource subject = sps.next().getSubject();
			final Statement id = subject.getProperty(IDENTIFIER);
			if (id != null) {
				ids.add(id.getString());
			}
		}

		assertTrue(ids.contains("SUV_Example"),
				"catalog should expose a service provider for the SUV_Example project, got: " + ids);
	}

	// Steps 2 + 3: service provider -> query capability -> query base
	@Test
	void projectServiceProviderAdvertisesBlockQuery() {
		final Model sp = getRdf("/services/serviceProviders/SUV_Example");

		final Set<String> queryBases = new HashSet<>();
		final StmtIterator qcs = sp.listStatements(null, QUERY_CAPABILITY, (RDFNode) null);
		while (qcs.hasNext()) {
			final Resource qc = qcs.next().getObject().asResource();
			if (qc.hasProperty(QUERY_BASE)) {
				queryBases.add(qc.getProperty(QUERY_BASE).getResource().getURI());
			}
		}
		assertFalse(queryBases.isEmpty(), "service provider should advertise at least one query capability");
		assertTrue(queryBases.stream().anyMatch(u -> u.endsWith("/SUV_Example/blocks")),
				"expected a block query capability whose query base ends with /SUV_Example/blocks, got: "
						+ queryBases);
	}

	// Step 4: follow the query base and confirm the fixture block is present
	@Test
	void blockQueryReturnsFixtureBlock() {
		final String body = given()
				.header("OSLC-Core-Version", "2.0")
				.accept("application/rdf+xml")
				.when()
				.get("/services/SUV_Example/blocks")
				.then()
				.statusCode(200)
				.extract()
				.body()
				.asString();
		assertTrue(body.contains("Wheel"),
				"block query should return the Wheel block from the fixture, body was:\n" + body);
	}

	// Drill into the individual block resource and verify it matches the fixture
	@Test
	void blockResourceMatchesFixture() {
		final Model block = getRdf("/services/SUV_Example/blocks/Wheel");

		final StmtIterator it = block.listStatements(null, BLOCK_NAME, (RDFNode) null);
		boolean found = false;
		while (it.hasNext()) {
			final Statement st = it.next();
			final RDFNode obj = st.getObject();
			if (obj.isLiteral() && "Wheel".equals(obj.asLiteral().getString())
					&& st.getSubject().hasProperty(RDF.type, SYSML_BLOCK)) {
				found = true;
				break;
			}
		}
		assertTrue(found, "expected a Block resource named Wheel in the RDF graph");
	}

	private static Model getRdf(final String path) {
		final String body = given()
				.header("OSLC-Core-Version", "2.0")
				.accept("application/rdf+xml")
				.when()
				.get(path)
				.then()
				.statusCode(200)
				.extract()
				.body()
				.asString();
		final Model m = ModelFactory.createDefaultModel();
		m.read(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)), "", "RDF/XML");
		return m;
	}

	private static Property prop(final String uri) {
		return ModelFactory.createDefaultModel().createProperty(uri);
	}

	private static Resource res(final String uri) {
		return ModelFactory.createDefaultModel().createResource(uri);
	}
}
