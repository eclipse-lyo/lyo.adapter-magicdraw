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

package org.eclipse.lyo.adapter.magicdraw.authentication;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.ws.commons.util.Base64;

/**
 * CredentialsFilter is responsible for authentication at each request
 * received by the OSLC MagicDraw adapter.  
 * 
 * @author Axel Reichwein (axel.reichwein@koneksys.com)
 */
public class CredentialsFilter implements Filter {

	public void doFilter(ServletRequest servletRequest,
			ServletResponse servletResponse, FilterChain chain)
			throws IOException, ServletException {

		// uncomment to enforce authentication
//		if (servletRequest instanceof HttpServletRequest
//				&& servletResponse instanceof HttpServletResponse) {
//			HttpServletRequest request = (HttpServletRequest) servletRequest;			
//			if (!request.getPathInfo().startsWith("/oauth")) {
//
//				// Check for basic access authentication.
//				try {					
//					String authorizationHeader = request
//							.getHeader("Authorization");
//					if (authorizationHeader == null
//							|| "".equals(authorizationHeader)) {						
//						return;
//					}
//					String encodedString = authorizationHeader
//							.substring("Basic ".length());
//
//					String unencodedString = new String(
//							Base64.decode(encodedString), "UTF-8");
//					int seperator = unencodedString.indexOf(':');
//					if (seperator == -1) {
//						throw new Exception(
//								"Invalid Authorization header value.");
//					}
//					String username = unencodedString.substring(0, seperator);
//					String password = unencodedString.substring(seperator + 1);
//					System.out.println(username);
//					System.out.println(password);
//				} 
//				catch (Exception e) {
//					e.printStackTrace();
//				}
//			}
//		}

		chain.doFilter(servletRequest, servletResponse);
	}

	public void destroy() {
		// TODO Auto-generated method stub

	}

	public void init(FilterConfig arg0) throws ServletException {
		// TODO Auto-generated method stub

	}

}
