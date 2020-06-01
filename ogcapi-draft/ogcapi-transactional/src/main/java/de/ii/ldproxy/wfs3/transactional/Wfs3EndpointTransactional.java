/**
 * Copyright 2020 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.wfs3.transactional;

import com.google.common.collect.ImmutableSet;
import de.ii.ldproxy.ogcapi.domain.ImmutableOgcApiContext;
import de.ii.ldproxy.ogcapi.domain.ImmutableOgcApiMediaType;
import de.ii.ldproxy.ogcapi.domain.OgcApiApi;
import de.ii.ldproxy.ogcapi.domain.OgcApiContext;
import de.ii.ldproxy.ogcapi.domain.OgcApiContext.HttpMethods;
import de.ii.ldproxy.ogcapi.domain.OgcApiApiDataV2;
import de.ii.ldproxy.ogcapi.domain.OgcApiEndpointExtension;
import de.ii.ldproxy.ogcapi.domain.OgcApiMediaType;
import de.ii.ldproxy.ogcapi.domain.OgcApiRequestContext;
import de.ii.ldproxy.ogcapi.features.core.api.OgcApiFeatureCoreProviders;
import de.ii.xtraplatform.auth.api.User;
import de.ii.xtraplatform.features.domain.FeatureProvider2;
import de.ii.xtraplatform.features.domain.FeatureTransactions;
import io.dropwizard.auth.Auth;
import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.apache.felix.ipojo.annotations.Requires;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.NotAllowedException;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.ServerErrorException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Optional;


/**
 * @author zahnen
 */
@Component
@Provides
@Instantiate
public class Wfs3EndpointTransactional implements OgcApiEndpointExtension {

    private static final OgcApiContext API_CONTEXT = new ImmutableOgcApiContext.Builder()
            .apiEntrypoint("collections")
            .subPathPattern("^/(?:[\\w\\-]+)/items/?[^/\\s]*$")
            .addMethods(HttpMethods.POST, HttpMethods.PUT, HttpMethods.DELETE)
            .putSubPathsAndMethods("^/(?:[\\w\\-]+)/items/?", Arrays.asList(new HttpMethods[]{HttpMethods.POST}))
            .putSubPathsAndMethods("^/(?:[\\w\\-]+)/items/?(?:[^/\\s]+)$", Arrays.asList(new HttpMethods[]{HttpMethods.PUT, HttpMethods.DELETE}))
            .build();

    private final OgcApiFeatureCoreProviders providers;
    private final CommandHandlerTransactional commandHandler;

    public Wfs3EndpointTransactional(@Requires OgcApiFeatureCoreProviders providers) {
        this.providers = providers;
        this.commandHandler = new CommandHandlerTransactional();
    }

    @Override
    public OgcApiContext getApiContext() {
        return API_CONTEXT;
    }

    @Override
    public ImmutableSet<OgcApiMediaType> getMediaTypes(OgcApiApiDataV2 dataset, String subPath) {
        if (subPath.matches("^/(?:[\\w\\-]+)/items/?[^/\\s]*$"))
            return ImmutableSet.of(
                    new ImmutableOgcApiMediaType.Builder()
                            .type(new MediaType("application", "geo+json"))
                            .build()
            );

        throw new ServerErrorException("Invalid sub path: "+subPath, 500);
    }

    @Override
    public boolean isEnabledForApi(OgcApiApiDataV2 apiData) {
        return isExtensionEnabled(apiData, TransactionalConfiguration.class);
    }

    @Path("/{id}/items")
    @POST
    @Consumes("application/geo+json")
    public Response postItems(@Auth Optional<User> optionalUser, @PathParam("id") String id,
                              @Context OgcApiApi service, @Context OgcApiRequestContext wfs3Request,
                              @Context HttpServletRequest request, InputStream requestBody) {
        FeatureProvider2 featureProvider = providers.getFeatureProvider(service.getData(), service.getData().getCollections().get(id));

        checkTransactional(featureProvider);

        checkAuthorization(service.getData(), optionalUser);


        return commandHandler.postItemsResponse((FeatureTransactions) featureProvider, wfs3Request.getMediaType(), wfs3Request.getUriCustomizer()
                                                                                                                     .copy(), id, requestBody);
    }

    @Path("/{id}/items/{featureid}")
    @PUT
    @Consumes("application/geo+json")
    public Response putItem(@Auth Optional<User> optionalUser, @PathParam("id") String id,
                            @PathParam("featureid") final String featureId, @Context OgcApiApi service,
                            @Context OgcApiRequestContext wfs3Request, @Context HttpServletRequest request,
                            InputStream requestBody) {

        FeatureProvider2 featureProvider = providers.getFeatureProvider(service.getData(), service.getData().getCollections().get(id));

        checkTransactional(featureProvider);

        checkAuthorization(service.getData(), optionalUser);

        return commandHandler.putItemResponse((FeatureTransactions) featureProvider, wfs3Request.getMediaType(), id, featureId, requestBody);
    }

    @Path("/{id}/items/{featureid}")
    @DELETE
    public Response deleteItem(@Auth Optional<User> optionalUser, @Context OgcApiApi service,
                               @PathParam("id") String id, @PathParam("featureid") final String featureId) {

        FeatureProvider2 featureProvider = providers.getFeatureProvider(service.getData(), service.getData().getCollections().get(id));

        checkTransactional(featureProvider);

        checkAuthorization(service.getData(), optionalUser);

        return commandHandler.deleteItemResponse((FeatureTransactions) featureProvider, id, featureId);
    }

    private void checkTransactional(FeatureProvider2 featureProvider) {
        if (!(featureProvider instanceof FeatureTransactions)) {
            throw new NotAllowedException("GET");
        }
    }
}
