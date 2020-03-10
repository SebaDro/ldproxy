/**
 * Copyright 2020 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.resources;

import com.google.common.collect.ImmutableSet;
import com.google.common.io.ByteSource;
import de.ii.ldproxy.ogcapi.application.DefaultLinksGenerator;
import de.ii.ldproxy.ogcapi.application.I18n;
import de.ii.ldproxy.ogcapi.domain.*;
import de.ii.ldproxy.wfs3.styles.StylesConfiguration;
import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.apache.felix.ipojo.annotations.Requires;
import org.osgi.framework.BundleContext;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.IOException;
import java.net.URLConnection;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

import static de.ii.xtraplatform.runtime.FelixRuntime.DATA_DIR_KEY;

/**
 * fetch list of styles or a style for the service
 */
@Component
@Provides
@Instantiate
public class EndpointResources implements OgcApiEndpointExtension, ConformanceClass {

    @Requires
    I18n i18n;

    private static final OgcApiContext API_CONTEXT = new ImmutableOgcApiContext.Builder()
            .apiEntrypoint("resources")
            .addMethods(OgcApiContext.HttpMethods.GET, OgcApiContext.HttpMethods.HEAD)
            .subPathPattern("^/?[^/]*$")
            .build();

    private final File resourcesStore; // TODO: change to Store
    private final OgcApiExtensionRegistry extensionRegistry;

    public EndpointResources(@org.apache.felix.ipojo.annotations.Context BundleContext bundleContext, @Requires OgcApiExtensionRegistry extensionRegistry) {
        this.extensionRegistry = extensionRegistry;
        this.resourcesStore = new File(bundleContext.getProperty(DATA_DIR_KEY) + File.separator + "resources");
        if (!resourcesStore.exists()) {
            resourcesStore.mkdirs();
        }
    }

    @Override
    public String getConformanceClass() {
        return "http://www.opengis.net/t15/opf-styles-1/1.0/conf/resources";
    }

    @Override
    public OgcApiContext getApiContext() {
        return API_CONTEXT;
    }

    @Override
    public ImmutableSet<OgcApiMediaType> getMediaTypes(OgcApiApiDataV2 dataset, String subPath) {
        if (subPath.matches("^/?$"))
            return ImmutableSet.of(
                    new ImmutableOgcApiMediaType.Builder()
                            .type(MediaType.APPLICATION_JSON_TYPE)
                            .build(),
                    new ImmutableOgcApiMediaType.Builder()
                            .type(MediaType.TEXT_HTML_TYPE)
                            .build());
        else if (subPath.matches("^/?[^/]+$"))
            return ImmutableSet.of(
                    new ImmutableOgcApiMediaType.Builder()
                            .type(MediaType.WILDCARD_TYPE)
                            .build());

        throw new ServerErrorException("Invalid sub path: "+subPath, 500);
    }

    @Override
    public ImmutableSet<String> getParameters(OgcApiApiDataV2 apiData, String subPath) {
        if (!isEnabledForApi(apiData))
            return ImmutableSet.of();

        if (subPath.matches("^/?$")) {
            return OgcApiEndpointExtension.super.getParameters(apiData, subPath);
        } else if (subPath.matches("^/?[^/]+$")) {
            return ImmutableSet.of();
        }

        throw new ServerErrorException("Invalid sub path: "+subPath, 500);
    }

    @Override
    public boolean isEnabledForApi(OgcApiApiDataV2 apiData) {
        Optional<StylesConfiguration> stylesExtension = getExtensionConfiguration(apiData, StylesConfiguration.class);

        if (stylesExtension.isPresent() &&
                stylesExtension.get()
                        .getResourcesEnabled()) {
            return true;
        }
        return false;
    }

    /**
     * fetch all available resources
     *
     * @return all resources in a JSON resources object
     */
    @Path("/")
    @GET
    @Produces({MediaType.APPLICATION_JSON,MediaType.TEXT_HTML})
    public Response getResources(@Context OgcApiApi api, @Context OgcApiRequestContext requestContext) {
        final ResourcesLinkGenerator resourcesLinkGenerator = new ResourcesLinkGenerator();

        final String apiId = api.getId();
        File apiDir = new File(resourcesStore + File.separator + apiId);
        if (!apiDir.exists()) {
            apiDir.mkdirs();
        }

        Resources resources = ImmutableResources.builder()
            .resources(
                Arrays.stream(apiDir.listFiles())
                .filter(file -> !file.isHidden())
                .map(File::getName)
                .sorted()
                .map(filename -> ImmutableResource.builder()
                        .id(filename)
                        .link(resourcesLinkGenerator.generateResourceLink(requestContext.getUriCustomizer(), filename))
                        .build())
                .collect(Collectors.toList()))
            .links(new DefaultLinksGenerator()
                    .generateLinks(requestContext.getUriCustomizer(),
                            requestContext.getMediaType(),
                            requestContext.getAlternateMediaTypes(),
                            i18n,
                            requestContext.getLanguage()))
            .build();

        if (requestContext.getMediaType().matches(MediaType.TEXT_HTML_TYPE)) {
            Optional<ResourcesFormatExtension> outputFormatHtml = api.getOutputFormat(ResourcesFormatExtension.class, requestContext.getMediaType(), "/resources");
            if (outputFormatHtml.isPresent())
                return outputFormatHtml.get().getResourcesResponse(resources, api, requestContext);

            throw new NotAcceptableException();
        }

        return Response.ok(resources)
                       .type(MediaType.APPLICATION_JSON_TYPE)
                       .build();
    }

    /**
     * Fetch a resource by id
     *
     * @param resourceId the local identifier of a specific style
     * @return the style in a json file
     */
    @Path("/{resourceId}")
    @GET
    @Produces(MediaType.WILDCARD)
    public Response getResource(@PathParam("resourceId") String resourceId, @Context OgcApiApi dataset,
                             @Context OgcApiRequestContext ogcApiRequest) {

        final String datasetId = dataset.getId();
        final File apiDir = new File(resourcesStore + File.separator + datasetId);
        if (!apiDir.exists()) {
            apiDir.mkdirs();
        }

        File resourceFile = new File(apiDir + File.separator + resourceId);

        if (!resourceFile.exists()) {
            throw new NotFoundException();
        }

        try {
            final byte[] resource = Files.readAllBytes(resourceFile.toPath());

            // TODO: URLConnection content-type guessing doesn't seem to work well, maybe try Apache Tika
            String contentType = URLConnection.guessContentTypeFromName(resourceId);
            if (contentType==null) {
                try {
                    contentType = URLConnection.guessContentTypeFromStream(ByteSource.wrap(resource).openStream());
                } catch (IOException e) {
                    // nothing we can do here, just take the default
                }
            }
            if (contentType==null || contentType.isEmpty())
                contentType = "application/octet-stream";

            return Response.ok()
                    .entity(resource)
                    .type(contentType)
                    .header("Content-Disposition", "inline; filename=\""+resourceId+"\"")
                    .build();
        } catch (IOException e) {
            throw new ServerErrorException("resource could not be read: "+resourceId, 500);
        }
    }
}
