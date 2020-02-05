/**
 * Copyright 2020 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.wfs3.crs;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import de.ii.ldproxy.ogcapi.domain.ConformanceClass;
import de.ii.ldproxy.ogcapi.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ldproxy.ogcapi.domain.OgcApiApiDataV2;
import de.ii.ldproxy.ogcapi.domain.OgcApiParameterExtension;
import de.ii.xtraplatform.crs.domain.EpsgCrs;
import de.ii.xtraplatform.feature.provider.api.ImmutableFeatureQuery;
import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;

import java.util.HashMap;
import java.util.Map;


@Component
@Provides
@Instantiate
public class OgcApiParameterCrs implements OgcApiParameterExtension, ConformanceClass {

    public static final String BBOX_CRS = "bbox-crs";
    public static final String BBOX = "bbox";
    public static final String CRS = "crs";

    @Override
    public String getConformanceClass() {
        return "http://www.opengis.net/spec/ogcapi-features-2/1.0/conf/crs";
    }

    @Override
    public boolean isEnabledForApi(OgcApiApiDataV2 apiData) {
        return isExtensionEnabled(apiData, CrsConfiguration.class);
    }

    @Override
    public ImmutableSet<String> getParameters(OgcApiApiDataV2 apiData, String subPath) {
        if (!isEnabledForApi(apiData))
            return ImmutableSet.of();

        if (subPath.matches("^/[\\w\\-]+/items/?$")) {
            // Features
            return ImmutableSet.of("crs", "bbox-crs");
        } else if (subPath.matches("^/[\\w\\-]+/items/[^/\\s]+/?$")) {
            // Feature
            return ImmutableSet.of("crs");
        }

        return ImmutableSet.of();
    }

    @Override
    public Map<String, String> transformParameters(FeatureTypeConfigurationOgcApi featureTypeConfiguration,
                                                   Map<String, String> parameters, OgcApiApiDataV2 datasetData) {

        if (!isEnabledForApi(datasetData)) {
            return parameters;
        }

        if (parameters.containsKey(BBOX) && parameters.containsKey(BBOX_CRS)) {
            Map<String, String> newParameters = new HashMap<>(parameters);
            newParameters.put(BBOX, parameters.get(BBOX) + "," + parameters.get(BBOX_CRS));
            return ImmutableMap.copyOf(newParameters);
        }

        return parameters;
    }

    @Override
    public ImmutableFeatureQuery.Builder transformQuery(FeatureTypeConfigurationOgcApi featureTypeConfiguration,
                                                        ImmutableFeatureQuery.Builder queryBuilder,
                                                        Map<String, String> parameters, OgcApiApiDataV2 datasetData) {

        if (isEnabledForApi(datasetData) && parameters.containsKey(CRS)) {
            EpsgCrs targetCrs = EpsgCrs.fromString(parameters.get(CRS));
            queryBuilder.crs(targetCrs);
        }

        return queryBuilder;
    }
}
