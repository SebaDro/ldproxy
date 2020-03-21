/**
 * Copyright 2020 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.ogcapi.features.core.application;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import de.ii.ldproxy.ogcapi.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ldproxy.ogcapi.domain.ImmutableOgcApiContext;
import de.ii.ldproxy.ogcapi.domain.OgcApiApi;
import de.ii.ldproxy.ogcapi.domain.OgcApiApiDataV2;
import de.ii.ldproxy.ogcapi.domain.OgcApiCommonConfiguration;
import de.ii.ldproxy.ogcapi.domain.OgcApiContext;
import de.ii.ldproxy.ogcapi.domain.OgcApiEndpointExtension;
import de.ii.ldproxy.ogcapi.domain.OgcApiExtensionRegistry;
import de.ii.ldproxy.ogcapi.domain.OgcApiMediaType;
import de.ii.ldproxy.ogcapi.domain.OgcApiParameterExtension;
import de.ii.ldproxy.ogcapi.domain.OgcApiRequestContext;
import de.ii.ldproxy.ogcapi.features.core.api.ImmutableOgcApiQueryInputFeature;
import de.ii.ldproxy.ogcapi.features.core.api.ImmutableOgcApiQueryInputFeatures;
import de.ii.ldproxy.ogcapi.features.core.api.OgcApiFeatureCoreProviders;
import de.ii.ldproxy.ogcapi.features.core.api.OgcApiFeatureFormatExtension;
import de.ii.ldproxy.ogcapi.features.core.api.OgcApiFeaturesCoreQueriesHandler;
import de.ii.xtraplatform.auth.api.User;
import de.ii.xtraplatform.features.domain.FeatureQuery;
import io.dropwizard.auth.Auth;
import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.apache.felix.ipojo.annotations.Requires;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.ServerErrorException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@Provides
@Instantiate
public class OgcApiFeaturesEndpoint implements OgcApiEndpointExtension {

    private static final Logger LOGGER = LoggerFactory.getLogger(OgcApiFeaturesEndpoint.class);
    private static final OgcApiContext API_CONTEXT = new ImmutableOgcApiContext.Builder()
            .apiEntrypoint("collections")
            .addMethods(OgcApiContext.HttpMethods.GET, OgcApiContext.HttpMethods.HEAD)
            .subPathPattern("^/[\\w\\-]+/items(?:/[^/\\s]+)?/?$")
            .build();

    private final OgcApiExtensionRegistry extensionRegistry;
    private final OgcApiFeatureCoreProviders providers;
    private final OgcApiFeaturesQuery ogcApiFeaturesQuery;
    private final OgcApiFeaturesCoreQueriesHandler queryHandler;

    public OgcApiFeaturesEndpoint(@Requires OgcApiExtensionRegistry extensionRegistry,
                                  @Requires OgcApiFeatureCoreProviders providers,
                                  @Requires OgcApiFeaturesQuery ogcApiFeaturesQuery,
                                  @Requires OgcApiFeaturesCoreQueriesHandler queryHandler) {
        this.extensionRegistry = extensionRegistry;
        this.providers = providers;
        this.ogcApiFeaturesQuery = ogcApiFeaturesQuery;
        this.queryHandler = queryHandler;
    }

    @Override
    public OgcApiContext getApiContext() {
        return API_CONTEXT;
    }

    @Override
    public ImmutableSet<OgcApiMediaType> getMediaTypes(OgcApiApiDataV2 dataset, String subPath) {
        if (subPath.matches("^/[\\w\\-]+/items(?:/[^/\\s]+)?/?$"))
            return extensionRegistry.getExtensionsForType(OgcApiFeatureFormatExtension.class)
                                    .stream()
                                    .filter(outputFormatExtension -> outputFormatExtension.isEnabledForApi(dataset))
                                    .map(OgcApiFeatureFormatExtension::getMediaType)
                                    .collect(ImmutableSet.toImmutableSet());

        throw new ServerErrorException("Invalid sub path: "+subPath, 500);
    }

    @Override
    public ImmutableSet<String> getParameters(OgcApiApiDataV2 apiData, String subPath) {
        if (!isEnabledForApi(apiData))
            return ImmutableSet.of();

        ImmutableSet<String> parametersFromExtensions = new ImmutableSet.Builder<String>()
            .addAll(extensionRegistry.getExtensionsForType(OgcApiParameterExtension.class)
                .stream()
                .map(ext -> ext.getParameters(apiData, subPath))
                .flatMap(Collection::stream)
                .collect(Collectors.toSet()))
            .build();

        if (subPath.matches("^/[\\w\\-]+/items/?$")) {
            // Features
            return new ImmutableSet.Builder<String>()
                    .addAll(OgcApiEndpointExtension.super.getParameters(apiData, subPath))
                    .add("datetime", "bbox", "limit", "offset")
                    .addAll(parametersFromExtensions)
                    .build();
        } else if (subPath.matches("^/[\\w\\-]+/items/[^/\\s]+/?$")) {
            // Feature
            return new ImmutableSet.Builder<String>()
                    .addAll(OgcApiEndpointExtension.super.getParameters(apiData, subPath))
                    .addAll(parametersFromExtensions)
                    .build();
        }

        throw new ServerErrorException("Invalid sub path: "+subPath, 500);
    }

    @GET
    @Path("/{collectionId}/items")
    public Response getItems(@Auth Optional<User> optionalUser,
                             @Context OgcApiApi api,
                             @Context OgcApiRequestContext requestContext,
                             @Context UriInfo uriInfo,
                             @PathParam("collectionId") String collectionId) {
        checkAuthorization(api.getData(), optionalUser);
        checkCollectionExists(api.getData(), collectionId);

        FeatureTypeConfigurationOgcApi collectionData = api.getData()
                                                       .getCollections()
                                                       .get(collectionId);

        OgcApiFeaturesCoreConfiguration coreConfiguration = getExtensionConfiguration(api.getData(), collectionData, OgcApiFeaturesCoreConfiguration.class).orElseThrow(NotFoundException::new);

        int minimumPageSize = coreConfiguration.getMinimumPageSize();
        int defaultPageSize = coreConfiguration.getDefaultPageSize();
        int maxPageSize = coreConfiguration.getMaxPageSize();
        boolean showsFeatureSelfLink = coreConfiguration.getShowsFeatureSelfLink();
        boolean includeHomeLink = getExtensionConfiguration(api.getData(), OgcApiCommonConfiguration.class)
                .map(OgcApiCommonConfiguration::getIncludeHomeLink)
                .orElse(false);
        boolean includeLinkHeader = getExtensionConfiguration(api.getData(), OgcApiCommonConfiguration.class)
                .map(OgcApiCommonConfiguration::getIncludeLinkHeader)
                .orElse(false);

        FeatureQuery query = ogcApiFeaturesQuery.requestToFeatureQuery(api.getData(), collectionData, coreConfiguration, minimumPageSize, defaultPageSize, maxPageSize, toFlatMap(uriInfo.getQueryParameters()));

        OgcApiFeaturesCoreQueriesHandlerImpl.OgcApiQueryInputFeatures queryInput = new ImmutableOgcApiQueryInputFeatures.Builder()
                .collectionId(collectionId)
                .query(query)
                .featureProvider(providers.getFeatureProvider(api.getData(), collectionData))
                .defaultCrs(coreConfiguration.getDefaultEpsgCrs())
                .defaultPageSize(Optional.of(defaultPageSize))
                .showsFeatureSelfLink(showsFeatureSelfLink)
                .includeHomeLink(includeHomeLink)
                .includeLinkHeader(includeLinkHeader)
                .build();

        return queryHandler.handle(OgcApiFeaturesCoreQueriesHandlerImpl.Query.FEATURES, queryInput, requestContext);
    }

    @GET
    @Path("/{collectionId}/items/{featureId}")
    public Response getItem(@Auth Optional<User> optionalUser,
                            @Context OgcApiApi api,
                            @Context OgcApiRequestContext requestContext,
                            @Context UriInfo uriInfo,
                            @PathParam("collectionId") String collectionId,
                            @PathParam("featureId") String featureId) {
        checkAuthorization(api.getData(), optionalUser);
        checkCollectionExists(api.getData(), collectionId);

        FeatureTypeConfigurationOgcApi collectionData = api.getData()
                                                           .getCollections()
                                                           .get(collectionId);

        OgcApiFeaturesCoreConfiguration coreConfiguration = getExtensionConfiguration(api.getData(), collectionData, OgcApiFeaturesCoreConfiguration.class).orElseThrow(NotFoundException::new);

        boolean includeHomeLink = getExtensionConfiguration(api.getData(), OgcApiCommonConfiguration.class)
                .map(OgcApiCommonConfiguration::getIncludeHomeLink)
                .orElse(false);
        boolean includeLinkHeader = getExtensionConfiguration(api.getData(), OgcApiCommonConfiguration.class)
                .map(OgcApiCommonConfiguration::getIncludeLinkHeader)
                .orElse(false);

        FeatureQuery query = ogcApiFeaturesQuery.requestToFeatureQuery(api.getData(), collectionData, coreConfiguration, toFlatMap(uriInfo.getQueryParameters()), featureId);

        OgcApiFeaturesCoreQueriesHandlerImpl.OgcApiQueryInputFeature queryInput = new ImmutableOgcApiQueryInputFeature.Builder()
                .collectionId(collectionId)
                .featureId(featureId)
                .query(query)
                .featureProvider(providers.getFeatureProvider(api.getData(), collectionData))
                .defaultCrs(coreConfiguration.getDefaultEpsgCrs())
                .includeHomeLink(includeHomeLink)
                .includeLinkHeader(includeLinkHeader)
                .build();

        return queryHandler.handle(OgcApiFeaturesCoreQueriesHandlerImpl.Query.FEATURE, queryInput, requestContext);
    }

    private void checkCollectionExists(@Context OgcApiApiDataV2 apiData,
                                       @PathParam("collectionId") String collectionId) {
        if (!apiData.isCollectionEnabled(collectionId)) {
            throw new NotFoundException();
        }
    }

    public static Map<String, String> toFlatMap(MultivaluedMap<String, String> queryParameters) {
        return toFlatMap(queryParameters, false);
    }

    public static Map<String, String> toFlatMap(MultivaluedMap<String, String> queryParameters,
                                                boolean keysToLowerCase) {
        return queryParameters.entrySet()
                .stream()
                .map(entry -> {
                    String key = keysToLowerCase ? entry.getKey()
                            .toLowerCase() : entry.getKey();
                    return new AbstractMap.SimpleImmutableEntry<>(key, entry.getValue()
                            .isEmpty() ? "" : entry.getValue()
                            .get(0));
                })
                .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
