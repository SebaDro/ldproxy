/**
 * Copyright 2020 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.ogcapi.features.geojson.app;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;
import de.ii.ldproxy.ogcapi.domain.ApiExtension;
import de.ii.ldproxy.ogcapi.domain.ApiMediaType;
import de.ii.ldproxy.ogcapi.domain.ApiMediaTypeContent;
import de.ii.ldproxy.ogcapi.domain.ConformanceClass;
import de.ii.ldproxy.ogcapi.domain.ExtensionConfiguration;
import de.ii.ldproxy.ogcapi.domain.FormatExtension;
import de.ii.ldproxy.ogcapi.domain.HttpMethods;
import de.ii.ldproxy.ogcapi.domain.ImmutableApiMediaType;
import de.ii.ldproxy.ogcapi.domain.ImmutableApiMediaTypeContent;
import de.ii.ldproxy.ogcapi.domain.OgcApiDataV2;
import de.ii.ldproxy.ogcapi.features.core.domain.FeatureFormatExtension;
import de.ii.ldproxy.ogcapi.features.core.domain.FeatureTransformationContext;
import de.ii.ldproxy.ogcapi.features.core.domain.SchemaGeneratorFeature;
import de.ii.ldproxy.ogcapi.features.geojson.domain.FeatureTransformerGeoJson;
import de.ii.ldproxy.ogcapi.features.geojson.domain.GeoJsonConfiguration;
import de.ii.ldproxy.ogcapi.features.geojson.domain.GeoJsonWriter;
import de.ii.ldproxy.ogcapi.features.geojson.domain.ImmutableFeatureTransformationContextGeoJson;
import de.ii.ldproxy.ogcapi.features.core.domain.SchemaGeneratorFeatureCollectionOpenApi;
import de.ii.ldproxy.ogcapi.features.core.domain.SchemaGeneratorFeatureOpenApi;
import de.ii.xtraplatform.features.domain.FeatureTransformer2;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.apache.felix.ipojo.annotations.Requires;

import javax.ws.rs.core.MediaType;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * @author zahnen
 */
@Component
//TODO
@Provides(specifications = {FeaturesFormatGeoJson.class, ConformanceClass.class, FeatureFormatExtension.class, FormatExtension.class, ApiExtension.class})
@Instantiate
public class FeaturesFormatGeoJson implements ConformanceClass, FeatureFormatExtension {

    private static final String CONFORMANCE_CLASS = "http://www.opengis.net/spec/ogcapi-features-1/1.0/conf/geojson";
    public static final ApiMediaType MEDIA_TYPE = new ImmutableApiMediaType.Builder()
            .type(new MediaType("application", "geo+json"))
            .label("GeoJSON")
            .parameter("json")
            .build();
    public static final ApiMediaType COLLECTION_MEDIA_TYPE = new ImmutableApiMediaType.Builder()
            .type(new MediaType("application", "json"))
            .label("JSON")
            .parameter("json")
            .build();

    @Requires
    SchemaGeneratorFeatureOpenApi schemaGeneratorFeature;

    @Requires
    SchemaGeneratorFeatureCollectionOpenApi schemaGeneratorFeatureCollection;

    @Requires
    GeoJsonWriterRegistry geoJsonWriterRegistry;

    @Override
    public List<String> getConformanceClassUris() {
        return ImmutableList.of(CONFORMANCE_CLASS);
    }

    @Override
    public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
        return GeoJsonConfiguration.class;
    }

    @Override
    public boolean canSupportTransactions() {
        return true;
    }

    @Override
    public ApiMediaType getMediaType() {
        return MEDIA_TYPE;
    }

    @Override
    public ApiMediaTypeContent getContent(OgcApiDataV2 apiData, String path) {
        String schemaRef = "#/components/schemas/anyObject";
        Schema schema = new ObjectSchema();
        String collectionId = path.split("/", 4)[2];
        Optional<GeoJsonConfiguration> geoJsonConfiguration = apiData.getCollections()
                                                                     .get(collectionId)
                                                                     .getExtension(GeoJsonConfiguration.class);
        boolean flatten = geoJsonConfiguration.filter(config -> config.getNestedObjectStrategy() == FeatureTransformerGeoJson.NESTED_OBJECTS.FLATTEN && config.getMultiplicityStrategy() == FeatureTransformerGeoJson.MULTIPLICITY.SUFFIX)
                                              .isPresent();
        SchemaGeneratorFeature.SCHEMA_TYPE type = flatten ? SchemaGeneratorFeature.SCHEMA_TYPE.RETURNABLES_FLAT : SchemaGeneratorFeature.SCHEMA_TYPE.RETURNABLES;
        if (path.matches("/collections/[^//]+/items/?")) {
            schemaRef = schemaGeneratorFeatureCollection.getSchemaReferenceOpenApi(collectionId, type);
            schema = schemaGeneratorFeatureCollection.getSchemaOpenApi(apiData, collectionId, type);
        } else if (path.matches("/collections/[^//]+/items/[^//]+/?")) {
            schemaRef = schemaGeneratorFeature.getSchemaReferenceOpenApi(collectionId, type);
            schema = schemaGeneratorFeature.getSchemaOpenApi(apiData, collectionId, type);
        }
        // TODO example
        return new ImmutableApiMediaTypeContent.Builder()
                .schema(schema)
                .schemaRef(schemaRef)
                .ogcApiMediaType(MEDIA_TYPE)
                .build();
    }

    @Override
    public ApiMediaTypeContent getRequestContent(OgcApiDataV2 apiData, String path, HttpMethods method) {
        String collectionId = path.split("/", 4)[2];
        if ((path.matches("/collections/[^//]+/items/[^//]+/?") && method== HttpMethods.PUT) ||
            (path.matches("/collections/[^//]+/items/?") && method== HttpMethods.POST)) {

            // TODO change to MUTABLES
            Optional<GeoJsonConfiguration> geoJsonConfiguration = apiData.getCollections()
                                                                         .get(collectionId)
                                                                         .getExtension(GeoJsonConfiguration.class);
            boolean flatten = geoJsonConfiguration.filter(config -> config.getNestedObjectStrategy() == FeatureTransformerGeoJson.NESTED_OBJECTS.FLATTEN && config.getMultiplicityStrategy() == FeatureTransformerGeoJson.MULTIPLICITY.SUFFIX)
                                                  .isPresent();
            SchemaGeneratorFeature.SCHEMA_TYPE type = flatten ? SchemaGeneratorFeature.SCHEMA_TYPE.RETURNABLES_FLAT : SchemaGeneratorFeature.SCHEMA_TYPE.RETURNABLES;
            return new ImmutableApiMediaTypeContent.Builder()
                    .schema(schemaGeneratorFeature.getSchemaOpenApi(apiData, collectionId, type))
                    .schemaRef(schemaGeneratorFeature.getSchemaReferenceOpenApi(collectionId, type))
                    .ogcApiMediaType(MEDIA_TYPE)
                    .build();
        }

        return null;
    }

    @Override
    public ApiMediaType getCollectionMediaType() {
        return COLLECTION_MEDIA_TYPE;
    }

    @Override
    public boolean canTransformFeatures() {
        return true;
    }

    @Override
    public Optional<FeatureTransformer2> getFeatureTransformer(FeatureTransformationContext transformationContext,
                                                               Optional<Locale> language) {

        // TODO support language
        ImmutableSortedSet<GeoJsonWriter> geoJsonWriters = geoJsonWriterRegistry.getGeoJsonWriters()
                                                                                .stream()
                                                                                .map(GeoJsonWriter::create)
                                                                                .collect(ImmutableSortedSet.toImmutableSortedSet(Comparator.comparingInt(GeoJsonWriter::getSortPriority)));

        return Optional.of(new FeatureTransformerGeoJson(ImmutableFeatureTransformationContextGeoJson.builder()
                                                                                                     .from(transformationContext)
                                                                                                     .geoJsonConfig(transformationContext.getApiData().getCollections().get(transformationContext.getCollectionId()).getExtension(GeoJsonConfiguration.class).get())
                                                                                                     .prettify(Optional.ofNullable(transformationContext.getOgcApiRequest()
                                                                                                                                                        .getParameters()
                                                                                                                                                        .get("pretty"))
                                                                                                                       .filter(value -> Objects.equals(value, "true"))
                                                                                                                       .isPresent() ||
                                                                                                               (transformationContext.getApiData().getCollections().get(transformationContext.getCollectionId()).getExtension(GeoJsonConfiguration.class).get().getUseFormattedJsonOutput()))
                                                                                                     .debugJson(Optional.ofNullable(transformationContext.getOgcApiRequest()
                                                                                                                                                        .getParameters()
                                                                                                                                                        .get("debug"))
                                                                                                                       .filter(value -> Objects.equals(value, "true"))
                                                                                                                       .isPresent())
                                                                                                     .build(), geoJsonWriters));
    }

}
