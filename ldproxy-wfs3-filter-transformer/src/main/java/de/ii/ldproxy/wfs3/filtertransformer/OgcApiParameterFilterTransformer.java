/**
 * Copyright 2019 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.wfs3.filtertransformer;

import de.ii.ldproxy.ogcapi.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ldproxy.ogcapi.domain.OgcApiDatasetData;
import de.ii.ldproxy.ogcapi.domain.OgcApiParameterExtension;
import de.ii.xtraplatform.akka.http.Http;
import de.ii.xtraplatform.akka.http.HttpClient;
import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.apache.felix.ipojo.annotations.Requires;

import java.util.Map;
import java.util.Optional;

/**
 * @author zahnen
 */
@Component
@Provides
@Instantiate
public class OgcApiParameterFilterTransformer implements OgcApiParameterExtension {


    private final HttpClient httpClient;

    public OgcApiParameterFilterTransformer(@Requires Http http) {
        this.httpClient = http.getDefaultClient();
    }

    @Override
    public boolean isEnabledForApi(OgcApiDatasetData apiData) {
        return isExtensionEnabled(apiData, FilterTransformersConfiguration.class);
    }

    @Override
    public Map<String, String> transformParameters(FeatureTypeConfigurationOgcApi featureTypeConfiguration,
                                                   Map<String, String> parameters, OgcApiDatasetData datasetData) {
        final Optional<FilterTransformersConfiguration> filterTransformersConfiguration = featureTypeConfiguration.getExtension(FilterTransformersConfiguration.class);

        if (filterTransformersConfiguration.isPresent()) {

            Map<String, String> nextParameters = parameters;

            for (FilterTransformerConfiguration filterTransformerConfiguration : filterTransformersConfiguration.get()
                                                                                                                .getTransformers()) {
                //TODO
                if (filterTransformerConfiguration instanceof RequestGeoJsonBboxConfiguration) {
                    RequestGeoJsonBboxTransformer requestGeoJsonBboxTransformer = new RequestGeoJsonBboxTransformer((RequestGeoJsonBboxConfiguration) filterTransformerConfiguration, httpClient);
                    nextParameters = requestGeoJsonBboxTransformer.resolveParameters(nextParameters);

                }
            }

            return nextParameters;
        }
        return parameters;
    }
}