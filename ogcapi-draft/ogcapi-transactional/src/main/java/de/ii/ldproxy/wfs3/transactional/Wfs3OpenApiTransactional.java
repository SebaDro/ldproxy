/**
 * Copyright 2020 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.wfs3.transactional;

import de.ii.ldproxy.ogcapi.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ldproxy.ogcapi.domain.OgcApiApiDataV2;
import de.ii.ldproxy.ogcapi.features.core.api.OgcApiFeatureCoreProviders;
import de.ii.ldproxy.wfs3.oas30.OpenApiExtension;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.apache.felix.ipojo.annotations.Requires;

import java.util.Comparator;
import java.util.Objects;

/**
 * @author zahnen
 */
@Component
@Provides
@Instantiate
public class Wfs3OpenApiTransactional implements OpenApiExtension {

    private final OgcApiFeatureCoreProviders providers;

    public Wfs3OpenApiTransactional(@Requires OgcApiFeatureCoreProviders providers) {
        this.providers = providers;
    }

    @Override
    public int getSortPriority() {
        return 10;
    }

    @Override
    public boolean isEnabledForApi(OgcApiApiDataV2 apiData) {
        return isExtensionEnabled(apiData, TransactionalConfiguration.class);
    }

    @Override
    public OpenAPI process(OpenAPI openAPI, OgcApiApiDataV2 datasetData) {
        if (providers.getFeatureProvider(datasetData).supportsTransactions() && isEnabledForApi(datasetData)) {

            datasetData.getCollections()
                       .values()
                       .stream()
                       .sorted(Comparator.comparing(FeatureTypeConfigurationOgcApi::getId))
                       .filter(ft -> datasetData.isCollectionEnabled(ft.getId()))
                       .forEach(ft -> {

                           PathItem pathItem = openAPI.getPaths()
                                                      .get(String.format("/collections/%s/items", ft.getId()));

                           RequestBody requestBody = new RequestBody().description("A single feature.")
                                                                      .content(new Content().addMediaType("application/geo+json", new MediaType().schema(new Schema().$ref("#/components/schemas/featureGeoJSON"))));
                           ApiResponse exception = new ApiResponse().description("An error occured.")
                                                                    .content(new Content().addMediaType("application/geo+json", new MediaType().schema(new Schema().$ref("#/components/schemas/exception"))));

                           if (Objects.nonNull(pathItem)) {
                               pathItem
                                       .post(new Operation()
                                               .summary("add features to the " + ft.getLabel() + " feature collection")
                                               //.description("")
                                               .operationId("addFeatures" + ft.getId())
                                               .tags(pathItem.getGet()
                                                             .getTags())
                                               .requestBody(requestBody)
                                               .responses(new ApiResponses().addApiResponse("201", new ApiResponse().description("Features were created.")
                                                                                                                    .addHeaderObject("location", new Header().description("The URL of the first created feature")
                                                                                                                                                             .schema(new StringSchema())))
                                                                            .addApiResponse("default", exception))
                                       );
                           }

                           PathItem pathItem2 = openAPI.getPaths()
                                                       .get(String.format("/collections/%s/items/{featureId}", ft.getId()));

                           if (Objects.nonNull(pathItem2)) {
                               pathItem2
                                       .put(new Operation()
                                               .summary("replace a " + ft.getLabel())
                                               //.description("")
                                               .operationId("replaceFeature" + ft.getId())
                                               .tags(pathItem2.getGet()
                                                              .getTags())
                                               .addParametersItem(new Parameter().$ref("#/components/parameters/featureId"))
                                               .requestBody(requestBody)
                                               .responses(new ApiResponses().addApiResponse("204", new ApiResponse().description("Feature was replaced."))
                                                                            .addApiResponse("default", exception))
                                       )
                                       .delete(new Operation()
                                               .summary("delete a " + ft.getLabel())
                                               //.description("")
                                               .operationId("deleteFeature" + ft.getId())
                                               .tags(pathItem2.getGet()
                                                              .getTags())
                                               .addParametersItem(new Parameter().$ref("#/components/parameters/featureId"))
                                               .responses(new ApiResponses().addApiResponse("204", new ApiResponse().description("Feature was deleted."))
                                                                            .addApiResponse("default", exception))
                                       );
                           }

                       });
        }

        return openAPI;
    }
}
