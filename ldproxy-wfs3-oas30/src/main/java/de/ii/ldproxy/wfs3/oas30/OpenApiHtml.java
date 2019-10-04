/**
 * Copyright 2019 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.wfs3.oas30;

import de.ii.ldproxy.ogcapi.domain.*;
import de.ii.xtraplatform.openapi.OpenApiViewerResource;
import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.apache.felix.ipojo.annotations.Requires;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.NotFoundException;
import javax.ws.rs.ServerErrorException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URISyntaxException;

@Component
@Provides
@Instantiate
public class OpenApiHtml implements ApiDefinitionFormatExtension {

    private static Logger LOGGER = LoggerFactory.getLogger(OpenApiHtml.class);
    private static OgcApiMediaType MEDIA_TYPE = new ImmutableOgcApiMediaType.Builder()
            .type(MediaType.TEXT_HTML_TYPE)
            .label("HTML")
            .build();

    @Requires
    private ExtendableOpenApiDefinition openApiDefinition;

    @Requires(optional = true)
    private OpenApiViewerResource openApiViewerResource;

    @Override
    public OgcApiMediaType getMediaType() {
        return MEDIA_TYPE;
    }

    @Override
    public boolean isEnabledForApi(OgcApiDatasetData apiData) {
        return isExtensionEnabled(apiData, Oas30Configuration.class);
    }

    @Override
    public Response getApiDefinitionResponse(OgcApiDatasetData apiData,
                                             OgcApiRequestContext wfs3Request) {
        if (!wfs3Request.getUriCustomizer()
                        .getPath()
                        .endsWith("/")) {
            try {
                return Response
                        .status(Response.Status.MOVED_PERMANENTLY)
                        .location(wfs3Request.getUriCustomizer()
                                .copy()
                                .ensureTrailingSlash()
                                .build())
                        .build();
            } catch (URISyntaxException ex) {
                throw new ServerErrorException("Invalid URI: "+ex.getMessage(), 500);
            }
        }

        if (openApiViewerResource == null) {
            throw new NotFoundException();
        }

        LOGGER.debug("MIME {}", "HTML");
        return openApiViewerResource.getFile("index.html");
    }
}