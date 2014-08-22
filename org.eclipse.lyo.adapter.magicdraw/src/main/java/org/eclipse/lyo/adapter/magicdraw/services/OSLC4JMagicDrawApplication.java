/*******************************************************************************
 * Copyright (c) 2012, 2014 IBM Corporation.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 *  
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * Contributors:
 *
 *     Michael Fiedler     - initial API and implementation for Bugzilla adapter
 *     
 * Modifications performed by:    
 *     Axel Reichwein		- implementation for MagicDraw adapter
 *     (axel.reichwein@koneksys.com)
 *     Sebastian Herzig (sebastian.herzig@me.gatech.edu) - support for publishing OSLC resource shapes     
 *******************************************************************************/
package org.eclipse.lyo.adapter.magicdraw.services;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

















import org.eclipse.lyo.adapter.magicdraw.application.MagicDrawManager;
import org.eclipse.lyo.oslc4j.application.OslcWinkApplication;
import org.eclipse.lyo.oslc4j.core.exception.OslcCoreApplicationException;
import org.eclipse.lyo.oslc4j.core.model.AllowedValues;
import org.eclipse.lyo.oslc4j.core.model.Compact;
import org.eclipse.lyo.oslc4j.core.model.CreationFactory;
import org.eclipse.lyo.oslc4j.core.model.Dialog;
import org.eclipse.lyo.oslc4j.core.model.Error;
import org.eclipse.lyo.oslc4j.core.model.ExtendedError;
import org.eclipse.lyo.oslc4j.core.model.Link;
import org.eclipse.lyo.oslc4j.core.model.OAuthConfiguration;
import org.eclipse.lyo.oslc4j.core.model.OslcConstants;
import org.eclipse.lyo.oslc4j.core.model.PrefixDefinition;
import org.eclipse.lyo.oslc4j.core.model.Preview;
import org.eclipse.lyo.oslc4j.core.model.Property;
import org.eclipse.lyo.oslc4j.core.model.Publisher;
import org.eclipse.lyo.oslc4j.core.model.QueryCapability;
import org.eclipse.lyo.oslc4j.core.model.ResourceShape;
import org.eclipse.lyo.oslc4j.core.model.Service;
import org.eclipse.lyo.oslc4j.core.model.ServiceProvider;
import org.eclipse.lyo.oslc4j.core.model.ServiceProviderCatalog;
import org.eclipse.lyo.oslc4j.provider.jena.JenaProvidersRegistry;
import org.eclipse.lyo.oslc4j.provider.json4j.Json4JProvidersRegistry;

import com.nomagic.magicdraw.commandline.CommandLine;

/**
 * OSLC4JMagicDrawApplication registers all entity providers for converting POJOs into 
 * RDF/XML, JSON and other formats. OSLC4JMagicDrawApplication registers also registers each 
 * servlet class containing the implementation of OSLC RESTful web services. 
 * 
 * OSLC4JMagicDrawApplication also reads the user-defined configuration file 
 * with loadPropertiesFile(). This is done at the initialization of the web application, 
 * for example when the first resource or service of the OSLC MagicDraw adapter is requested. 
 * 
 * @author Axel Reichwein (axel.reichwein@koneksys.com)
 * @author Sebastian Herzig (sebastian.herzig@me.gatech.edu)
 */
public class OSLC4JMagicDrawApplication extends OslcWinkApplication {

    private static final Set<Class<?>>         RESOURCE_CLASSES                          = new HashSet<Class<?>>();
    private static final Map<String, Class<?>> RESOURCE_SHAPE_PATH_TO_RESOURCE_CLASS_MAP = new HashMap<String, Class<?>>();

    public static String sysmlEcoreLocation = null;
    public static String magicdrawModelsDirectory = null;
    public static String portNumber = null;
    
    static
    {
    	RESOURCE_CLASSES.addAll(JenaProvidersRegistry.getProviders());
    	RESOURCE_CLASSES.addAll(Json4JProvidersRegistry.getProviders());
    	   	   	        	   	 
    	RESOURCE_CLASSES.add(CommandLine.class);
    	RESOURCE_CLASSES.add(MagicDrawManager.class); 
    	
    	RESOURCE_CLASSES.add(Link.class);
    	
    	
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
    	
    	RESOURCE_CLASSES.add(OSLC4MBSESpecificationService.class);
    	
    	
    	RESOURCE_SHAPE_PATH_TO_RESOURCE_CLASS_MAP.put(OslcConstants.PATH_ALLOWED_VALUES,           AllowedValues.class);
        RESOURCE_SHAPE_PATH_TO_RESOURCE_CLASS_MAP.put(OslcConstants.PATH_COMPACT,                  Compact.class);
        RESOURCE_SHAPE_PATH_TO_RESOURCE_CLASS_MAP.put(OslcConstants.PATH_CREATION_FACTORY,         CreationFactory.class);
        RESOURCE_SHAPE_PATH_TO_RESOURCE_CLASS_MAP.put(OslcConstants.PATH_DIALOG,                   Dialog.class);
        RESOURCE_SHAPE_PATH_TO_RESOURCE_CLASS_MAP.put(OslcConstants.PATH_ERROR,                    Error.class);
        RESOURCE_SHAPE_PATH_TO_RESOURCE_CLASS_MAP.put(OslcConstants.PATH_EXTENDED_ERROR,           ExtendedError.class);
        RESOURCE_SHAPE_PATH_TO_RESOURCE_CLASS_MAP.put(OslcConstants.PATH_OAUTH_CONFIGURATION,      OAuthConfiguration.class);
        RESOURCE_SHAPE_PATH_TO_RESOURCE_CLASS_MAP.put(OslcConstants.PATH_PREFIX_DEFINITION,        PrefixDefinition.class);
        RESOURCE_SHAPE_PATH_TO_RESOURCE_CLASS_MAP.put(OslcConstants.PATH_PREVIEW,                  Preview.class);
        RESOURCE_SHAPE_PATH_TO_RESOURCE_CLASS_MAP.put(OslcConstants.PATH_PROPERTY,                 Property.class);
        RESOURCE_SHAPE_PATH_TO_RESOURCE_CLASS_MAP.put(OslcConstants.PATH_PUBLISHER,                Publisher.class);
        RESOURCE_SHAPE_PATH_TO_RESOURCE_CLASS_MAP.put(OslcConstants.PATH_QUERY_CAPABILITY,         QueryCapability.class);
        RESOURCE_SHAPE_PATH_TO_RESOURCE_CLASS_MAP.put(OslcConstants.PATH_RESOURCE_SHAPE,           ResourceShape.class);
        RESOURCE_SHAPE_PATH_TO_RESOURCE_CLASS_MAP.put(OslcConstants.PATH_SERVICE,                  Service.class);
        RESOURCE_SHAPE_PATH_TO_RESOURCE_CLASS_MAP.put(OslcConstants.PATH_SERVICE_PROVIDER,         ServiceProvider.class);
        RESOURCE_SHAPE_PATH_TO_RESOURCE_CLASS_MAP.put(OslcConstants.PATH_SERVICE_PROVIDER_CATALOG, ServiceProviderCatalog.class);
    	
        loadPropertiesFile();
    }

    public OSLC4JMagicDrawApplication()
           throws OslcCoreApplicationException,
                  URISyntaxException
    {
        super(RESOURCE_CLASSES,
              OslcConstants.PATH_RESOURCE_SHAPES,
              RESOURCE_SHAPE_PATH_TO_RESOURCE_CLASS_MAP);
    }

	private static void loadPropertiesFile() {
		Properties prop = new Properties();
		InputStream input = null;
	 
		try {
			// loading properties file
			input = new FileInputStream("./configuration/config.properties");
						
			// load property file content and convert backslashes into forward slashes
			String str = readFile("./configuration/config.properties", Charset.defaultCharset());
			prop.load(new StringReader(str.replace("\\","/")));
			
			// get the property value 
			String sysmlEcoreLocationFromUser = prop.getProperty("sysmlEcoreLocation");			
			String magicdrawModelsDirectoryFromUser = prop.getProperty("magicdrawModelsDirectory");
			
			// add trailing slash if missing
			if(!magicdrawModelsDirectoryFromUser.endsWith("/")){
				magicdrawModelsDirectoryFromUser = magicdrawModelsDirectoryFromUser + "/";
			}
			magicdrawModelsDirectory = magicdrawModelsDirectoryFromUser;
			sysmlEcoreLocation = sysmlEcoreLocationFromUser;
			portNumber = prop.getProperty("portNumber");
	 
		} catch (IOException ex) {
			ex.printStackTrace();
		} finally {
			if (input != null) {
				try {
					input.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		
	}
	
	static String readFile(String path, Charset encoding) 
			  throws IOException 
			{
			  byte[] encoded = Files.readAllBytes(Paths.get(path));
			  return encoding.decode(ByteBuffer.wrap(encoded)).toString();
			}
}
