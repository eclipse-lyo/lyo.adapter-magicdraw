package edu.gatech.mbsec.adapter.magicdraw.probe;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import com.nomagic.magicdraw.commandline.ProjectCommandLine;
import com.nomagic.magicdraw.core.Project;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element;
import com.nomagic.uml2.ext.magicdraw.mdprofiles.Stereotype;

/**
 * Read-only smoke probe for a real SysML v1 .mdzip in the installed product.
 */
public final class MagicDrawSysmlV1ModelReadProbe extends ProjectCommandLine {
    private static final String[] SYSML_V1_STEREOTYPES = {
        "Block", "Requirement", "InterfaceBlock", "ValueType", "PartProperty",
        "ReferenceProperty", "ValueProperty", "FlowProperty", "ItemFlow",
        "ProxyPort", "FullPort", "AssociationBlock"
    };

    public static void main(String[] args) throws InstantiationException {
        new MagicDrawSysmlV1ModelReadProbe().launch(args);
    }

    @Override
    protected byte execute(Properties properties, Project project) {
        try {
            require(project != null, "ProjectCommandLine did not load the SysML v1 project");
            require(project.getModel() != null, "The loaded SysML v1 project has no UML model");

            Map<String, Integer> stereotypeCounts = new LinkedHashMap<>();
            for (String name : SYSML_V1_STEREOTYPES) {
                stereotypeCounts.put(name, 0);
            }

            Set<Element> visited = Collections.newSetFromMap(new IdentityHashMap<Element, Boolean>());
            Deque<Element> pending = new ArrayDeque<>();
            pending.add(project.getModel());
            while (!pending.isEmpty()) {
                Element element = pending.removeFirst();
                if (!visited.add(element)) {
                    continue;
                }

                for (Stereotype stereotype : element.getAppliedStereotype()) {
                    Integer currentCount = stereotypeCounts.get(stereotype.getName());
                    if (currentCount != null) {
                        stereotypeCounts.put(stereotype.getName(), currentCount + 1);
                    }
                }
                pending.addAll(element.getOwnedElement());
            }

            int sysmlElementCount = 0;
            for (Integer count : stereotypeCounts.values()) {
                sysmlElementCount += count;
            }
            require(sysmlElementCount > 0, "No recognized SysML v1 stereotypes were found");

            System.out.println("SYSMLV1_MDZIP_READ_OK"
                    + " project=" + project.getName()
                    + " model=" + project.getModel().getName()
                    + " elements=" + visited.size()
                    + " diagrams=" + project.getDiagrams().size()
                    + " stereotypes=" + stereotypeCounts);
            return 0;
        } catch (Throwable failure) {
            failure.printStackTrace(System.err);
            return -1;
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
