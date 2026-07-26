/*********************************************************************************************
 * Copyright (c) 2014 Model-Based Systems Engineering Center, Georgia Institute of Technology.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 *  
 *  The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 *  and the Eclipse Distribution License is available at
 *  http://www.eclipse.org/org/documents/edl-v10.php.
 *  
 *  Contributors:
 *  
 *	   Axel Reichwein (axel.reichwein@koneksys.com)		- initial implementation       
 *******************************************************************************************/
package edu.gatech.mbsec.adapter.magicdraw.services;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;

import edu.gatech.mbsec.adapter.magicdraw.resources.Constants;
import edu.gatech.mbsec.adapter.magicdraw.resources.SysMLFullPort;
import edu.gatech.mbsec.adapter.magicdraw.resources.SysMLPartProperty;
import edu.gatech.mbsec.adapter.magicdraw.resources.SysMLPort;
import edu.gatech.mbsec.adapter.magicdraw.resources.SysMLProxyPort;
import edu.gatech.mbsec.adapter.magicdraw.resources.SysMLReferenceProperty;
import org.eclipse.lyo.oslc4j.core.annotation.OslcCreationFactory;
import org.eclipse.lyo.oslc4j.core.annotation.OslcName;
import org.eclipse.lyo.oslc4j.core.annotation.OslcNamespace;
import org.eclipse.lyo.oslc4j.core.annotation.OslcQueryCapability;
import org.eclipse.lyo.oslc4j.core.annotation.OslcResourceShape;
import org.eclipse.lyo.oslc4j.core.annotation.OslcService;
import org.eclipse.lyo.oslc4j.core.model.OslcConstants;
import org.eclipse.lyo.oslc4j.core.model.OslcMediaType;

import edu.gatech.mbsec.adapter.magicdraw.application.MagicDrawManager;

/**
 * This servlet contains the implementation of OSLC RESTful web services for SysMLFullPort resources.
 * 
 * The servlet contains web services for: <ul margin-top: 0;>
 * <li> returning specific SysMLFullPort resources in HTML and other formats </li>
 * <li> returning all SysMLFullPort resources within a specific MagicDraw project
 *  in HTML and other formats </li>
 *  </ul>
 *  
 * @author Axel Reichwein (axel.reichwein@koneksys.com)
 */
@OslcService(Constants.SYSML_FULLPORT_DOMAIN)
@Path("{projectId}/fullports")
public class SysMLFullPortService extends HttpServlet{
	
	@Context
	private HttpServletRequest httpServletRequest;
	@Context
	private HttpServletResponse httpServletResponse;
	@Context
	private UriInfo uriInfo;
	
	@OslcQueryCapability
	(
			title = "SysML Full Port Query Capability",
	        label = "SysML Full Port Catalog Query",
	        resourceShape = OslcConstants.PATH_RESOURCE_SHAPES + "/" + Constants.PATH_SYSML_FULLPORT,
	        resourceTypes = {Constants.TYPE_SYSML_FULLPORT},
	        usages = {OslcConstants.OSLC_USAGE_DEFAULT}
	)
	
	@GET
	@Produces({ OslcMediaType.APPLICATION_RDF_XML,
			OslcMediaType.APPLICATION_XML, OslcMediaType.APPLICATION_JSON })
	public List<edu.gatech.mbsec.adapter.magicdraw.resources.SysMLFullPort> getFullPorts(
			@PathParam("projectId") final String projectId,
			@QueryParam("oslc.where") final String where,
			@QueryParam("oslc.select") final String select,
			@QueryParam("oslc.prefix") final String prefix,
			@QueryParam("page") final String pageString,
			@QueryParam("oslc.orderBy") final String orderBy,
			@QueryParam("oslc.searchTerms") final String searchTerms,
			@QueryParam("oslc.paging") final String paging,
			@QueryParam("oslc.pageSize") final String pageSize)
			throws IOException, ServletException {
		MagicDrawManager.loadSysMLProjects();
		return MagicDrawManager.getFullPorts(projectId);
	}
	
	@GET
	@Path("{propertyQualifiedName}")
	@Produces({ OslcMediaType.APPLICATION_RDF_XML, OslcMediaType.APPLICATION_JSON})
	public edu.gatech.mbsec.adapter.magicdraw.resources.SysMLFullPort getFullPort(@PathParam("projectId") final String projectId, @PathParam("propertyQualifiedName") final String propertyQualifiedName) throws URISyntaxException  {
		MagicDrawManager.loadSysMLProjects();
		SysMLFullPort sysMLFullPort = MagicDrawManager.getFullPortByQualifiedName(projectId, propertyQualifiedName);
		return sysMLFullPort;
	}

	@GET
    @Produces(MediaType.TEXT_HTML)
    public void getHtmlFullPorts(@PathParam("projectId") final String projectId)
    {				
		MagicDrawManager.loadSysMLProjects();
		List<SysMLFullPort> sysmlFullPorts = MagicDrawManager.getFullPorts(projectId);		
		String requestURL = httpServletRequest.getRequestURL().toString();
    	if (sysmlFullPorts !=null )
    	{	        
	        httpServletRequest.setAttribute("elements", sysmlFullPorts);	        
	        httpServletRequest.setAttribute("requestURL",requestURL);
	        httpServletRequest.setAttribute("projectId",projectId);	        
	        RequestDispatcher rd = httpServletRequest.getRequestDispatcher("/sysml/sysml_fullports_html.jsp");
    		try {
				rd.forward(httpServletRequest, httpServletResponse);
			} catch (Exception e) {				
				e.printStackTrace();
				throw new WebApplicationException(e);
			} 
    	}
    }
	
	@GET
	@Path("{qualifiedName}")
	@Produces(MediaType.TEXT_HTML)
	public void getHtmlFullPort(
			@PathParam("projectId") final String projectId,
			@PathParam("qualifiedName") final String qualifiedName,
			@QueryParam("oslc.properties") final String propertiesString,
			@QueryParam("oslc.prefix") final String prefix)
			throws URISyntaxException, IOException {
		MagicDrawManager.loadSysMLProjects();
		edu.gatech.mbsec.adapter.magicdraw.resources.SysMLFullPort sysmlFullPort = MagicDrawManager.getFullPortByQualifiedName(projectId, qualifiedName);
	
		String requestURL = httpServletRequest.getRequestURL().toString();
		if (sysmlFullPort !=null )
    	{
	        
	        httpServletRequest.setAttribute("fullPort", sysmlFullPort);
	        httpServletRequest.setAttribute("requestURL",requestURL);
	        RequestDispatcher rd = httpServletRequest.getRequestDispatcher("/sysml/sysml_fullport_html.jsp");
    		try {
				rd.forward(httpServletRequest, httpServletResponse);
			} catch (Exception e) {				
				e.printStackTrace();
				throw new WebApplicationException(e);
			} 
    	}	
	}
	
	
}
