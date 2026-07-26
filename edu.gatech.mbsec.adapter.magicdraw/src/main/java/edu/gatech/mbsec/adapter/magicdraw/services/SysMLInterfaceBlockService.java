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
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import edu.gatech.mbsec.adapter.magicdraw.resources.Constants;
import edu.gatech.mbsec.adapter.magicdraw.resources.SysMLBlock;
import edu.gatech.mbsec.adapter.magicdraw.resources.SysMLInterfaceBlock;
import edu.gatech.mbsec.adapter.magicdraw.resources.SysMLModel;
import edu.gatech.mbsec.adapter.magicdraw.resources.SysMLPackage;
import org.eclipse.lyo.oslc4j.core.annotation.OslcCreationFactory;
import org.eclipse.lyo.oslc4j.core.annotation.OslcQueryCapability;
import org.eclipse.lyo.oslc4j.core.annotation.OslcService;
import org.eclipse.lyo.oslc4j.core.model.OslcConstants;
import org.eclipse.lyo.oslc4j.core.model.OslcMediaType;

import edu.gatech.mbsec.adapter.magicdraw.application.MagicDrawManager;

/**
 * This servlet contains the implementation of OSLC RESTful web services for SysMLInterfaceBlock resources.
 * 
 * The servlet contains web services for: <ul margin-top: 0;>
 * <li> returning specific SysMLInterfaceBlock resources in HTML and other formats </li>
 * <li> returning all SysMLInterfaceBlock resources within a specific MagicDraw project
 *  in HTML and other formats </li>
 *  <li> adding new SysMLInterfaceBlock resources to a specific MagicDraw project</li>
 *  </ul>
 *  
 * @author Axel Reichwein (axel.reichwein@koneksys.com)
 */
@OslcService(Constants.SYSML_INTERFACEBLOCK_DOMAIN)
@Path("{projectId}/interfaceblocks")
public class SysMLInterfaceBlockService extends HttpServlet {

	@Context
	private HttpServletRequest httpServletRequest;
	@Context
	private HttpServletResponse httpServletResponse;
	@Context
	private UriInfo uriInfo;

	@OslcQueryCapability(title = "SysML Interface Block Query Capability", label = "SysML Interface Block Catalog Query", resourceShape = OslcConstants.PATH_RESOURCE_SHAPES
			+ "/" + Constants.PATH_SYSML_INTERFACEBLOCK, resourceTypes = { Constants.TYPE_SYSML_INTERFACEBLOCK }, usages = { OslcConstants.OSLC_USAGE_DEFAULT })
	@GET
	@Produces({ OslcMediaType.APPLICATION_RDF_XML,
			OslcMediaType.APPLICATION_XML, OslcMediaType.APPLICATION_JSON })
	public List<edu.gatech.mbsec.adapter.magicdraw.resources.SysMLInterfaceBlock> getInterfaceBlocks(
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
		return MagicDrawManager.getInterfaceBlocks(projectId);
	}

	@GET
	@Path("{blockQualifiedName}")
	@Produces({ OslcMediaType.APPLICATION_RDF_XML,
			OslcMediaType.APPLICATION_JSON })
	public edu.gatech.mbsec.adapter.magicdraw.resources.SysMLInterfaceBlock getInterfaceBlock(
			@PathParam("projectId") final String projectId,
			@PathParam("blockQualifiedName") final String blockQualifiedName)
			throws URISyntaxException {
		MagicDrawManager.loadSysMLProjects();
		edu.gatech.mbsec.adapter.magicdraw.resources.SysMLInterfaceBlock sysmlInterfaceBlock = MagicDrawManager
				.getInterfaceBlockByQualifiedName(projectId, blockQualifiedName);
		return sysmlInterfaceBlock;
	}

	@GET
	@Produces(MediaType.TEXT_HTML)
	public void getHtmlInterfaceBlocks(@PathParam("projectId") final String projectId) {
		MagicDrawManager.loadSysMLProjects();
		List<SysMLInterfaceBlock> sysmlInterfaceBlocks = MagicDrawManager.getInterfaceBlocks(projectId);
		String requestURL = httpServletRequest.getRequestURL().toString();
		if (sysmlInterfaceBlocks != null) {			
			httpServletRequest.setAttribute("elements", sysmlInterfaceBlocks);
			httpServletRequest.setAttribute("requestURL", requestURL);
			httpServletRequest.setAttribute("projectId", projectId);
			RequestDispatcher rd = httpServletRequest
					.getRequestDispatcher("/sysml/sysml_interfaceblocks_html.jsp");
			try {
				rd.forward(httpServletRequest, httpServletResponse);
			} catch (Exception e) {
				e.printStackTrace();
				throw new WebApplicationException(e);
			}
		}
	}

	@GET
	@Path("{blockQualifiedName}")
	@Produces(MediaType.TEXT_HTML)
	public void getHtmlInterfaceBlock(@PathParam("projectId") final String projectId,
			@PathParam("blockQualifiedName") final String blockQualifiedName,
			@QueryParam("oslc.properties") final String propertiesString,
			@QueryParam("oslc.prefix") final String prefix)
			throws URISyntaxException, IOException {
		MagicDrawManager.loadSysMLProjects();
		edu.gatech.mbsec.adapter.magicdraw.resources.SysMLInterfaceBlock sysmlBlock = MagicDrawManager
				.getInterfaceBlockByQualifiedName(projectId, blockQualifiedName);

		String requestURL = httpServletRequest.getRequestURL().toString();
		if (sysmlBlock != null) {
			
			httpServletRequest.setAttribute("interfaceblock", sysmlBlock);
			httpServletRequest.setAttribute("requestURL", requestURL);
			RequestDispatcher rd = httpServletRequest
					.getRequestDispatcher("/sysml/sysml_interfaceblock_html.jsp");
			try {
				rd.forward(httpServletRequest, httpServletResponse);
			} catch (Exception e) {
				e.printStackTrace();
				throw new WebApplicationException(e);
			}
		}
	}

	@OslcCreationFactory(title = "SysML Interface Block Creation Factory", label = "SysML Interface Block Creation", resourceShapes = { OslcConstants.PATH_RESOURCE_SHAPES
			+ "/" + Constants.PATH_SYSML_INTERFACEBLOCK }, resourceTypes = { Constants.TYPE_SYSML_INTERFACEBLOCK }, usages = { OslcConstants.OSLC_USAGE_DEFAULT })
	@POST
	@Consumes({ OslcMediaType.APPLICATION_RDF_XML,
			OslcMediaType.APPLICATION_XML, OslcMediaType.APPLICATION_JSON })
	@Produces({ OslcMediaType.APPLICATION_RDF_XML,
			OslcMediaType.APPLICATION_XML, OslcMediaType.APPLICATION_JSON })
	public Response addInterfaceBlock(@PathParam("projectId") final String projectId,
			final SysMLInterfaceBlock sysmlInterfaceBlock) throws IOException, ServletException {		
//		MagicDrawManager.loadSysMLProjects();
		MagicDrawManager.makeSysMLProjectActive(projectId);
		MagicDrawManager.createSysMLInterfaceBlock(sysmlInterfaceBlock, projectId);
		URI about = sysmlInterfaceBlock.getAbout();
		return Response.created(about).entity(sysmlInterfaceBlock).build();
	}
}
