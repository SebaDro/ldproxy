/**
 * Copyright 2019 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.wfs3.crs;

import com.google.common.collect.ImmutableMap;
import de.ii.ldproxy.ogcapi.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ldproxy.ogcapi.domain.OgcApiDatasetData;
import de.ii.ldproxy.wfs3.api.Wfs3ParameterExtension;
import de.ii.xtraplatform.crs.api.EpsgCrs;
import de.ii.xtraplatform.feature.provider.api.ImmutableFeatureQuery;
import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static de.ii.ldproxy.ogcapi.domain.OgcApiDatasetData.DEFAULT_CRS;
import static de.ii.ldproxy.ogcapi.domain.OgcApiDatasetData.DEFAULT_CRS_URI;

/**
 * @author zahnen
 */
@Component
@Provides
@Instantiate
public class Wfs3ParameterCrs implements Wfs3ParameterExtension {

    public static final String BBOX_CRS = "bbox-crs";
    public static final String BBOX = "bbox";
    public static final String CRS = "crs";

    @Override
    public boolean isEnabledForDataset(OgcApiDatasetData dataset) {
        return isExtensionEnabled(dataset, CrsConfiguration.class);
    }

    @Override
    public Map<String, String> transformParameters(FeatureTypeConfigurationOgcApi featureTypeConfiguration,
                                                   Map<String, String> parameters, OgcApiDatasetData datasetData) {

        if (!isEnabledForDataset(datasetData)) {
            return parameters;
        }

        if (parameters.containsKey(BBOX) && parameters.containsKey(BBOX_CRS) && !isDefaultCrs(parameters.get(BBOX_CRS))) {
            Map<String, String> newParameters = new HashMap<>(parameters);
            newParameters.put(BBOX, parameters.get(BBOX) + "," + parameters.get(BBOX_CRS));
            return ImmutableMap.copyOf(newParameters);
        }

        return parameters;
    }

    @Override
    public ImmutableFeatureQuery.Builder transformQuery(FeatureTypeConfigurationOgcApi featureTypeConfiguration,
                                                        ImmutableFeatureQuery.Builder queryBuilder,
                                                        Map<String, String> parameters, OgcApiDatasetData datasetData) {

        if (!isEnabledForDataset(datasetData)) {
            return queryBuilder;
        }

        if (parameters.containsKey(CRS) && !isDefaultCrs(parameters.get(CRS))) {
            EpsgCrs targetCrs = new EpsgCrs(parameters.get(CRS));
            queryBuilder.crs(targetCrs);
        } else {
            queryBuilder.crs(DEFAULT_CRS);
        }

        return queryBuilder;
    }

    private boolean isDefaultCrs(String crs) {
        return Objects.equals(crs, DEFAULT_CRS_URI);
    }
}
