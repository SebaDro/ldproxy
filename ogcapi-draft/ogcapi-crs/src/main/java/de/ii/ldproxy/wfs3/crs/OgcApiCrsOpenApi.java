/**
 * Copyright 2020 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.wfs3.crs;

import com.google.common.collect.ImmutableSet;
import de.ii.ldproxy.ogcapi.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ldproxy.ogcapi.domain.OgcApiApiDataV2;
import de.ii.ldproxy.ogcapi.features.core.application.OgcApiFeaturesCoreConfiguration;
import de.ii.ldproxy.wfs3.oas30.OpenApiExtension;
import de.ii.xtraplatform.crs.domain.EpsgCrs;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.apache.felix.ipojo.annotations.Requires;

import java.util.Comparator;
import java.util.Objects;

import static de.ii.ldproxy.wfs3.crs.OgcApiParameterCrs.BBOX_CRS;
import static de.ii.ldproxy.wfs3.crs.OgcApiParameterCrs.CRS;
import static de.ii.xtraplatform.crs.domain.OgcCrs.CRS84_URI;


@Component
@Provides
@Instantiate
public class OgcApiCrsOpenApi implements OpenApiExtension {

    private final CrsSupport crsSupport;

    public OgcApiCrsOpenApi(@Requires CrsSupport crsSupport) {
        this.crsSupport = crsSupport;
    }

    @Override
    public int getSortPriority() {
        return 700;
    }

    @Override
    public boolean isEnabledForApi(OgcApiApiDataV2 apiData) {
        return isExtensionEnabled(apiData, CrsConfiguration.class);
    }

    @Override
    public OpenAPI process(OpenAPI openAPI, OgcApiApiDataV2 datasetData) {
        if (isEnabledForApi(datasetData)) {

            String defaultCrsUri = getExtensionConfiguration(datasetData, OgcApiFeaturesCoreConfiguration.class).get()
                                                                                                                .getDefaultEpsgCrs()
                                                                                                                .toUriString();

            ImmutableSet<String> crsSet = crsSupport.getSupportedCrsList(datasetData)
                                                    .stream()
                                                    .map(EpsgCrs::toUriString)
                                                    .collect(ImmutableSet.toImmutableSet());

            openAPI.getComponents()
                   .addParameters(CRS, new Parameter()
                           .name(CRS)
                           .in("query")
                           .description("The coordinate reference system of the response geometries. Default is WGS84 longitude/latitude (http://www.opengis.net/def/crs/OGC/1.3/CRS84).")
                           .required(false)
                           .style(Parameter.StyleEnum.FORM)
                           .schema(new StringSchema()
                                   ._enum(crsSet.asList())
                                   ._default(defaultCrsUri))
                           .explode(false));

            openAPI.getComponents()
                   .addParameters(BBOX_CRS, new Parameter()
                           .name(BBOX_CRS)
                           .in("query")
                           .description("The coordinate reference system of the bbox parameter. Default is WGS84 longitude/latitude (http://www.opengis.net/def/crs/OGC/1.3/CRS84).")
                           .required(false)
                           .schema(new StringSchema()
                                   ._enum(crsSet.asList())
                                   ._default(CRS84_URI))
                           .style(Parameter.StyleEnum.FORM)
                           .explode(false)
                   );

            datasetData.getCollections()
                       .values()
                       .stream()
                       .sorted(Comparator.comparing(FeatureTypeConfigurationOgcApi::getId))
                       .filter(ft -> datasetData.isCollectionEnabled(ft.getId()))
                       .forEach(ft -> {

                           PathItem pathItem = openAPI.getPaths()
                                                      .get(String.format("/collections/%s/items", ft.getId()));

                           if (Objects.nonNull(pathItem)) {
                               pathItem.getGet()
                                       .addParametersItem(new Parameter().$ref("#/components/parameters/crs"))
                                       .addParametersItem(new Parameter().$ref("#/components/parameters/bbox-crs"));
                           }

                           PathItem pathItem2 = openAPI.getPaths()
                                                       .get(String.format("/collections/%s/items/{featureId}", ft.getId()));

                           if (Objects.nonNull(pathItem2)) {
                               pathItem2.getGet()
                                        .addParametersItem(new Parameter().$ref("#/components/parameters/crs"));
                           }


                       });
        }
        return openAPI;
    }
}
