package edu.gatech.mbsec.adapter.magicdraw.probe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;

import org.eclipse.lyo.oslc4j.core.model.Link;
import org.junit.jupiter.api.Test;

class LyoLinkApiTest {

    @Test
    void beta3StillProvidesLinkThroughTheManagedCoreDependency() {
        Link link = new Link(URI.create("urn:example:sysml:v2:requirement"), "Requirement");

        assertEquals(URI.create("urn:example:sysml:v2:requirement"), link.getValue());
        assertEquals("Requirement", link.getLabel());
        assertTrue(Link.class.getProtectionDomain().getCodeSource().getLocation().toString()
                .contains("lyo-core-model-7.0.0.Beta3"));
    }
}
