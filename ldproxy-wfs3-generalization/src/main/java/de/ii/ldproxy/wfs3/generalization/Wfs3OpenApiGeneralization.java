/**
 * Copyright 2018 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.wfs3.generalization;

import de.ii.ldproxy.wfs3.api.FeatureTypeConfigurationWfs3;
import de.ii.ldproxy.wfs3.api.Wfs3ServiceData;
import de.ii.ldproxy.wfs3.oas30.Wfs3OpenApiExtension;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Objects;

import static de.ii.ldproxy.wfs3.generalization.GeneralizationConfiguration.EXTENSION_KEY;

/**
 * @author zahnen
 */
@Component
@Provides
@Instantiate
public class Wfs3OpenApiGeneralization implements Wfs3OpenApiExtension {
    @Override
    public int getSortPriority() {
        return 600;
    }

    @Override
    public OpenAPI process(OpenAPI openAPI, Wfs3ServiceData serviceData) {
        if(isExtensionEnabled(serviceData, EXTENSION_KEY)) {

            openAPI.getComponents()
                    .addParameters("maxAllowableOffset", new Parameter()
                            .name("maxAllowableOffset")
                            .in("query")
                            .description("This option can be used to specify the maxAllowableOffset to be used for generalizing the response geometries.\n \nThe maxAllowableOffset is in the units of the response coordinate reference system.")
                            .required(false)
                            .schema(new NumberSchema()._default(BigDecimal.valueOf(0)))
                            .style(Parameter.StyleEnum.FORM)
                            .explode(false)
                            .example(0.05)
                    );

            serviceData.getFeatureTypes()
                    .values()
                    .stream()
                    .sorted(Comparator.comparing(FeatureTypeConfigurationWfs3::getId))
                    .filter(ft -> serviceData.isFeatureTypeEnabled(ft.getId()))
                    .forEach(ft -> {

                        PathItem pathItem = openAPI.getPaths()
                                .get(String.format("/collections/%s/items", ft.getId()));

                        if (Objects.nonNull(pathItem)) {
                            pathItem.getGet()
                                    .addParametersItem(new Parameter().$ref("#/components/parameters/maxAllowableOffset"));
                        }

                        PathItem pathItem2 = openAPI.getPaths()
                                .get(String.format("/collections/%s/items/{featureId}", ft.getId()));

                        if (Objects.nonNull(pathItem2)) {
                            pathItem2.getGet()
                                    .addParametersItem(new Parameter().$ref("#/components/parameters/maxAllowableOffset"));
                        }


                    });
        }
        return openAPI;
    }
}
