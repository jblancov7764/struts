/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.struts2.views.util;

import com.opensymphony.xwork2.inject.Inject;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.StrutsConstants;
import org.apache.struts2.url.ParametersStringBuilder;
import org.apache.struts2.url.UrlDecoder;
import org.apache.struts2.url.UrlEncoder;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Default implementation of UrlHelper
 */
public class DefaultUrlHelper implements UrlHelper {

    private static final Logger LOG = LogManager.getLogger(DefaultUrlHelper.class);

    public static final String HTTP_PROTOCOL = "http";
    public static final String HTTPS_PROTOCOL = "https";

    private int httpPort = DEFAULT_HTTP_PORT;
    private int httpsPort = DEFAULT_HTTPS_PORT;

    private ParametersStringBuilder parametersStringBuilder;
    private UrlEncoder encoder;
    private UrlDecoder decoder;

    @Inject(StrutsConstants.STRUTS_URL_HTTP_PORT)
    public void setHttpPort(String httpPort) {
        this.httpPort = Integer.parseInt(httpPort);
    }

    @Inject(StrutsConstants.STRUTS_URL_HTTPS_PORT)
    public void setHttpsPort(String httpsPort) {
        this.httpsPort = Integer.parseInt(httpsPort);
    }

    @Inject
    public void setEncoder(UrlEncoder encoder) {
        this.encoder = encoder;
    }

    @Inject
    public void setDecoder(UrlDecoder decoder) {
        this.decoder = decoder;
    }

    @Inject
    public void setParametersStringBuilder(ParametersStringBuilder builder) {
        this.parametersStringBuilder = builder;
    }

    public String buildUrl(String action, HttpServletRequest request, HttpServletResponse response, Map<String, Object> params) {
        return buildUrl(action, request, response, params, null, true, true);
    }

    public String buildUrl(String action, HttpServletRequest request, HttpServletResponse response, Map<String, Object> params, String scheme,
                           boolean includeContext, boolean encodeResult) {
        return buildUrl(action, request, response, params, scheme, includeContext, encodeResult, false);
    }

    public String buildUrl(String action, HttpServletRequest request, HttpServletResponse response, Map<String, Object> params, String scheme,
                           boolean includeContext, boolean encodeResult, boolean forceAddSchemeHostAndPort) {
        return buildUrl(action, request, response, params, scheme, includeContext, encodeResult, forceAddSchemeHostAndPort, true);
    }

    public String buildUrl(String action, HttpServletRequest request, HttpServletResponse response, Map<String, Object> params, String urlScheme,
                           boolean includeContext, boolean encodeResult, boolean forceAddSchemeHostAndPort, boolean escapeAmp) {

        StringBuilder link = new StringBuilder();
        boolean changedScheme = false;

        String scheme = null;
        if (isValidScheme(urlScheme)) {
            scheme = urlScheme;
        }

        // only append scheme if it is different to the current scheme *OR*
        // if we explicity want it to be appended by having forceAddSchemeHostAndPort = true
        if (forceAddSchemeHostAndPort) {
            String reqScheme = request.getScheme();
            changedScheme = true;
            link.append(scheme != null ? scheme : reqScheme);
            link.append("://");
            link.append(request.getServerName());

            if (scheme != null) {
                // If switching schemes, use the configured port for the particular scheme.
                if (!scheme.equals(reqScheme)) {
                    appendPort(link, scheme, HTTP_PROTOCOL.equals(scheme) ? httpPort : httpsPort);
                    // Else use the port from the current request.
                } else {
                    appendPort(link, scheme, request.getServerPort());
                }
            } else {
                appendPort(link, reqScheme, request.getServerPort());
            }
        } else if ((scheme != null) && !scheme.equals(request.getScheme())) {
            changedScheme = true;
            link.append(scheme);
            link.append("://");
            link.append(request.getServerName());

            appendPort(link, scheme, HTTP_PROTOCOL.equals(scheme) ? httpPort : httpsPort);
        }

        if (action != null) {
            // Check if context path needs to be added
            // Add path to absolute links
            if (action.startsWith("/") && includeContext) {
                String contextPath = request.getContextPath();
                if (!contextPath.equals("/")) {
                    link.append(contextPath);
                }
            } else if (changedScheme) {

                // (Applicable to Servlet 2.4 containers)
                // If the request was forwarded, the attribute below will be set with the original URL
                String uri = (String) request.getAttribute("javax.servlet.forward.request_uri");

                // If the attribute wasn't found, default to the value in the request object
                if (uri == null) {
                    uri = request.getRequestURI();
                }

                link.append(uri, 0, uri.lastIndexOf('/') + 1);
            }

            // Add page
            link.append(action);
        } else {
            // Go to "same page"
            String requestURI = (String) request.getAttribute("struts.request_uri");

            // (Applicable to Servlet 2.4 containers)
            // If the request was forwarded, the attribute below will be set with the original URL
            if (requestURI == null) {
                requestURI = (String) request.getAttribute("javax.servlet.forward.request_uri");
            }

            // If neither request attributes were found, default to the value in the request object
            if (requestURI == null) {
                requestURI = request.getRequestURI();
            }

            link.append(requestURI);
        }

        //if the action was not explicitly set grab the params from the request
        if (escapeAmp) {
            parametersStringBuilder.buildParametersString(params, link, AMP);
        } else {
            parametersStringBuilder.buildParametersString(params, link, "&");
        }

        String result = link.toString();

        if (StringUtils.containsIgnoreCase(result, "<script")) {
            result = StringEscapeUtils.escapeEcmaScript(result);
        }
        try {
            result = encodeResult ? response.encodeURL(result) : result;
        } catch (Exception ex) {
            LOG.debug("Could not encode the URL for some reason, use it unchanged", ex);
            result = link.toString();
        }

        return result;
    }

    private void appendPort(StringBuilder link, String scheme, int port) {
        if ((HTTP_PROTOCOL.equals(scheme) && port != DEFAULT_HTTP_PORT) || (HTTPS_PROTOCOL.equals(scheme) && port != DEFAULT_HTTPS_PORT)) {
            link.append(":");
            link.append(port);
        }
    }

    /**
     * Builds parameters assigned to url - a query string
     * @param params a set of params to assign
     * @param link a based url
     * @param paramSeparator separator used
     * @deprecated since Struts 6.1.0, use {@link ParametersStringBuilder} instead
     */
    @Deprecated
    public void buildParametersString(Map<String, Object> params, StringBuilder link, String paramSeparator) {
        parametersStringBuilder.buildParametersString(params, link, paramSeparator);
    }

    /**
     * Builds parameters assigned to url - a query string
     * @param params a set of params to assign
     * @param link a based url
     * @param paramSeparator separator used
     * @param encode if true, parameters will be encoded - ignored
     * @deprecated since Struts 6.1.0, use {@link #buildParametersString(Map, StringBuilder, String)}
     */
    @Deprecated
    public void buildParametersString(Map<String, Object> params, StringBuilder link, String paramSeparator, boolean encode) {
        buildParametersString(params, link, paramSeparator);
    }

    protected boolean isValidScheme(String scheme) {
        return HTTP_PROTOCOL.equals(scheme) || HTTPS_PROTOCOL.equals(scheme);
    }

    /**
     * Encodes the URL using {@link UrlEncoder#encode} with the encoding specified in the configuration.
     *
     * @param input the input to encode
     * @return the encoded string
     * @deprecated since 6.1.0, use {@link UrlEncoder} directly, use {@link Inject} to inject a proper instance
     */
    @Deprecated
    public String encode(String input) {
        return encoder.encode(input);
    }

    /**
     * Decodes the URL using {@link UrlDecoder#decode(String, boolean)} with the encoding specified in the configuration.
     *
     * @param input the input to decode
     * @return the encoded string
     * @deprecated since 6.1.0, use {@link UrlDecoder} directly, use {@link Inject} to inject a proper instance
     */
    @Deprecated
    public String decode(String input) {
        return decoder.decode(input, false);
    }

    /**
     * Decodes the URL using {@link UrlDecoder#decode(String, boolean)} with the encoding specified in the configuration.
     *
     * @param input         the input to decode
     * @param isQueryString whether input is a query string. If <code>true</code> other decoding rules apply.
     * @return the encoded string
     * @deprecated since 6.1.0, use {@link UrlDecoder} directly, use {@link Inject} to inject a proper instance
     */
    @Deprecated
    public String decode(String input, boolean isQueryString) {
        return decoder.decode(input, isQueryString);
    }

    public Map<String, Object> parseQueryString(String queryString, boolean forceValueArray) {
        Map<String, Object> queryParams = new LinkedHashMap<>();
        if (queryString != null) {
            String[] params = queryString.split("&");
            for (String param : params) {
                if (param.trim().length() > 0) {
                    String[] tmpParams = param.split("=");
                    String paramName = null;
                    String paramValue = "";
                    if (tmpParams.length > 0) {
                        paramName = tmpParams[0];
                    }
                    if (tmpParams.length > 1) {
                        paramValue = tmpParams[1];
                    }
                    if (paramName != null) {
                        paramName = decoder.decode(paramName, true);
                        String translatedParamValue = decoder.decode(paramValue, true);

                        if (queryParams.containsKey(paramName) || forceValueArray) {
                            // WW-1619 append new param value to existing value(s)
                            Object currentParam = queryParams.get(paramName);
                            if (currentParam instanceof String) {
                                queryParams.put(paramName, new String[]{(String) currentParam, translatedParamValue});
                            } else {
                                String[] currentParamValues = (String[]) currentParam;
                                if (currentParamValues != null) {
                                    List<String> paramList = new ArrayList<>(Arrays.asList(currentParamValues));
                                    paramList.add(translatedParamValue);
                                    queryParams.put(paramName, paramList.toArray(new String[0]));
                                } else {
                                    queryParams.put(paramName, new String[]{translatedParamValue});
                                }
                            }
                        } else {
                            queryParams.put(paramName, translatedParamValue);
                        }
                    }
                }
            }
        }
        return queryParams;
    }
}
