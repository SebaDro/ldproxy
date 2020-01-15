/**
 * Copyright 2020 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.target.geojson;

import de.ii.ldproxy.target.geojson.GeoJsonMapping.GEO_JSON_TYPE;
import de.ii.ldproxy.wfs3.templates.StringTemplateFilters;
import de.ii.xtraplatform.feature.provider.api.FeatureProperty;
import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * @author zahnen
 */
@Component
@Provides
@Instantiate
public class GeoJsonWriterId implements GeoJsonWriter {

    @Override
    public GeoJsonWriterId create() {
        return new GeoJsonWriterId();
    }

    private String currentId;
    private String currentUriId;
    private boolean writeAtFeatureEnd = false;

    @Override
    public int getSortPriority() {
        return 10;
    }

    private void reset() {
        this.currentId = null;
        this.writeAtFeatureEnd = false;
    }

    @Override
    public void onStart(FeatureTransformationContextGeoJson transformationContext,
                        Consumer<FeatureTransformationContextGeoJson> next) throws IOException {
        reset();

        next.accept(transformationContext);
    }

    @Override
    public void onFeatureEnd(FeatureTransformationContextGeoJson transformationContext,
                             Consumer<FeatureTransformationContextGeoJson> next) throws IOException {

        if (writeAtFeatureEnd) {
            this.writeAtFeatureEnd = false;

            if (Objects.nonNull(currentId)) {
                transformationContext.getJson()
                                     .writeStringField("id", currentId);
                writeLink(transformationContext, currentId);
                this.currentId = null;
            }
        }

        // next chain for extensions
        next.accept(transformationContext);

    }

    @Override
    public void onProperty(FeatureTransformationContextGeoJson transformationContext,
                           Consumer<FeatureTransformationContextGeoJson> next) throws IOException {
        if (transformationContext.getState()
                                 .getCurrentFeatureProperty()
                                 .isPresent()
                || transformationContext.getState()
                                        .getCurrentValue()
                                        .isPresent()) {

            final FeatureProperty currentFeatureProperty = transformationContext.getState()
                                                                        .getCurrentFeatureProperty()
                                                                        .get();
            String currentValue = transformationContext.getState()
                                                       .getCurrentValue()
                                                       .get();

            if (currentFeatureProperty.isId()) {
                //TODO: new transformations handling
                //String idTemplate = currentFeatureProperty.getIdTemplate();
                String currentUri = null;
                /*if (Objects.nonNull(idTemplate)) {
                    currentUri = StringTemplateFilters.applyTemplate(idTemplate, currentValue, isHtml -> {}, "featureId");
                    currentUri = StringTemplateFilters.applyTemplate(currentUri, transformationContext.getServiceUrl(), isHtml -> {}, "serviceUrl");
                    currentUri = StringTemplateFilters.applyTemplate(currentUri, transformationContext.getCollectionId(), isHtml -> {}, "collectionId");
                }*/

                if (writeAtFeatureEnd) {
                    currentId = currentValue;
                    currentUriId = currentUri;
                } else {
                    transformationContext.getJson()
                                         .writeStringField("id", currentValue);
                    if (currentUri!=null)
                        transformationContext.getJson()
                                .writeStringField("@id", currentUri);

                    writeLink(transformationContext, currentValue);
                }
                // don't pass through
                //return;

            } else {

                this.writeAtFeatureEnd = true;

            }
        }

        next.accept(transformationContext);
    }

    @Override
    public void onCoordinates(FeatureTransformationContextGeoJson transformationContext,
                              Consumer<FeatureTransformationContextGeoJson> next) throws IOException {
        this.writeAtFeatureEnd = true;

        // next chain for extensions
        next.accept(transformationContext);
    }

    private void writeLink(FeatureTransformationContextGeoJson transformationContext,
                           String featureId) throws IOException {
        if (transformationContext.isFeatureCollection() &&
                transformationContext.getShowsFeatureSelfLink() &&
                Objects.nonNull(featureId) &&
                !featureId.isEmpty()) {
            transformationContext.getJson()
                                 .writeFieldName("links");
            transformationContext.getJson()
                                 .writeStartArray(1);
            transformationContext.getJson()
                                 .writeStartObject();
            transformationContext.getJson()
                                 .writeStringField("rel", "self");
            transformationContext.getJson()
                                 .writeStringField("href", transformationContext.getServiceUrl() + "/collections/" + transformationContext.getCollectionId() + "/items/" + featureId);
            transformationContext.getJson()
                                 .writeEndObject();
            transformationContext.getJson()
                                 .writeEndArray();
        }
    }
}
