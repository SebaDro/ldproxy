/**
 * Copyright 2020 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.ogcapi.tiles;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import de.ii.ldproxy.ogcapi.domain.*;
import de.ii.ldproxy.ogcapi.features.core.api.OgcApiFeatureCoreProviders;
import de.ii.ldproxy.ogcapi.features.core.application.OgcApiFeaturesCoreConfiguration;
import de.ii.ldproxy.ogcapi.tiles.tileMatrixSet.TileMatrixSet;
import de.ii.ldproxy.ogcapi.tiles.tileMatrixSet.TileMatrixSetLimits;
import de.ii.ldproxy.ogcapi.tiles.tileMatrixSet.TileMatrixSetLimitsGenerator;
import de.ii.xtraplatform.auth.api.User;
import de.ii.xtraplatform.crs.domain.CrsTransformationException;
import de.ii.xtraplatform.crs.domain.CrsTransformerFactory;
import de.ii.xtraplatform.features.domain.FeatureProvider2;
import de.ii.xtraplatform.features.domain.FeatureQuery;
import de.ii.xtraplatform.features.domain.ImmutableFeatureQuery;
import io.dropwizard.auth.Auth;
import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.apache.felix.ipojo.annotations.Requires;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Handle responses under '/tiles/{tileMatrixSetId}/{tileMatrix}/{tileRow}/{tileCol}'.
 */
@Component
@Provides
@Instantiate
public class EndpointTileMultiCollection extends OgcApiEndpoint {

    private static final Logger LOGGER = LoggerFactory.getLogger(EndpointTileMultiCollection.class);

    private static final List<String> TAGS = ImmutableList.of("Access multi-layer tiles");

    private final OgcApiFeatureCoreProviders providers;
    private final TilesQueriesHandler queryHandler;
    private final CrsTransformerFactory crsTransformerFactory;
    private final TileMatrixSetLimitsGenerator limitsGenerator;
    private final TilesCache cache;

    EndpointTileMultiCollection(@Requires OgcApiFeatureCoreProviders providers,
                                @Requires OgcApiExtensionRegistry extensionRegistry,
                                @Requires TilesQueriesHandler queryHandler,
                                @Requires CrsTransformerFactory crsTransformerFactory,
                                @Requires TileMatrixSetLimitsGenerator limitsGenerator,
                                @Requires TilesCache cache) {
        super(extensionRegistry);
        this.providers = providers;
        this.queryHandler = queryHandler;
        this.crsTransformerFactory = crsTransformerFactory;
        this.limitsGenerator = limitsGenerator;
        this.cache = cache;
    }

    @Override
    public boolean isEnabledForApi(OgcApiApiDataV2 apiData) {
        Optional<TilesConfiguration> extension = getExtensionConfiguration(apiData, TilesConfiguration.class);

        return extension
                .filter(TilesConfiguration::getEnabled)
                .filter(TilesConfiguration::getMultiCollectionEnabled)
                .isPresent();
    }

    @Override
    public List<? extends FormatExtension> getFormats() {
        if (formats==null)
            formats = extensionRegistry.getExtensionsForType(TileFormatExtension.class);
        return formats;
    }

    @Override
    public OgcApiEndpointDefinition getDefinition(OgcApiApiDataV2 apiData) {
        if (!isEnabledForApi(apiData))
            return super.getDefinition(apiData);

        String apiId = apiData.getId();
        if (!apiDefinitions.containsKey(apiId)) {
            ImmutableOgcApiEndpointDefinition.Builder definitionBuilder = new ImmutableOgcApiEndpointDefinition.Builder()
                    .apiEntrypoint("tiles")
                    .sortPriority(OgcApiEndpointDefinition.SORT_PRIORITY_TILE);
            final String path = "/tiles/{tileMatrixSetId}/{tileMatrix}/{tileRow}/{tileCol}";
            final OgcApiContext.HttpMethods method = OgcApiContext.HttpMethods.GET;
            final List<OgcApiPathParameter> pathParameters = getPathParameters(extensionRegistry, apiData, path);
            final List<OgcApiQueryParameter> queryParameters = getQueryParameters(extensionRegistry, apiData, path);
            String operationSummary = "fetch a tile with multiple layers, one per collection";
            Optional<String> operationDescription = Optional.of("The tile in the requested tiling scheme ('{tileMatrixSetId}'), " +
                    "on the requested zoom level ('{tileMatrix}'), with the requested grid coordinates ('{tileRow}', '{tileCol}') is returned. " +
                    "The tile has one layer per collection with all selected features in the bounding box of the tile with the requested properties.");
            ImmutableOgcApiResourceData.Builder resourceBuilder = new ImmutableOgcApiResourceData.Builder()
                    .path(path)
                    .pathParameters(pathParameters);
            OgcApiOperation operation = addOperation(apiData, queryParameters, path, operationSummary, operationDescription, TAGS);
            if (operation != null)
                resourceBuilder.putOperations(method.name(), operation);
            definitionBuilder.putResources(path, resourceBuilder.build());

            apiDefinitions.put(apiId, definitionBuilder.build());
        }

        return apiDefinitions.get(apiId);
    }

    @Path("/{tileMatrixSetId}/{tileMatrix}/{tileRow}/{tileCol}")
    @GET
    public Response getTile(@Auth Optional<User> optionalUser, @Context OgcApiApi api,
                            @PathParam("tileMatrixSetId") String tileMatrixSetId, @PathParam("tileMatrix") String tileMatrix,
                            @PathParam("tileRow") String tileRow, @PathParam("tileCol") String tileCol,
                            @Context UriInfo uriInfo, @Context OgcApiRequestContext requestContext)
        throws CrsTransformationException, FileNotFoundException, NotFoundException {

        OgcApiApiDataV2 apiData = api.getData();
        checkAuthorization(apiData, optionalUser);
        FeatureProvider2 featureProvider = providers.getFeatureProvider(apiData);
        ensureFeatureProviderSupportsQueries(featureProvider);

        String definitionPath = "/tiles/{tileMatrixSetId}/{tileMatrix}/{tileRow}/{tileCol}";
        checkPathParameter(extensionRegistry, apiData, definitionPath, "tileMatrixSetId", tileMatrixSetId);
        checkPathParameter(extensionRegistry, apiData, definitionPath, "tileMatrix", tileMatrix);
        checkPathParameter(extensionRegistry, apiData, definitionPath, "tileRow", tileRow);
        checkPathParameter(extensionRegistry, apiData, definitionPath, "tileCol", tileCol);
        final List<OgcApiQueryParameter> allowedParameters = getQueryParameters(extensionRegistry, api.getData(), definitionPath);

        // check, if the cache can be used (no query parameters except f)
        Map<String, String> queryParams = toFlatMap(uriInfo.getQueryParameters());
        boolean useCache = queryParams.isEmpty() || (queryParams.size()==1 && queryParams.containsKey("f"));

        int row;
        int col;
        int level;
        try {
            level = Integer.parseInt(tileMatrix);
            row = Integer.parseInt(tileRow);
            col = Integer.parseInt(tileCol);
        } catch (NumberFormatException e) {
            throw new ServerErrorException("Could not convert tile coordinates that have been validated to integers", 500);
        }

        TilesConfiguration tilesConfiguration = getExtensionConfiguration(apiData, TilesConfiguration.class).get();

        MinMax zoomLevels = tilesConfiguration.getZoomLevels().get(tileMatrixSetId);
        if (zoomLevels.getMax() < level || zoomLevels.getMin() > level)
            throw new NotFoundException();

        TileMatrixSet tileMatrixSet = extensionRegistry.getExtensionsForType(TileMatrixSet.class).stream()
                .filter(tms -> tms.getId().equals(tileMatrixSetId))
                .findAny()
                .orElseThrow(() -> new NotFoundException("Unknown tile matrix set: " + tileMatrixSetId));

        TileMatrixSetLimits tileLimits = limitsGenerator.getTileMatrixSetLimits(apiData, tileMatrixSet, zoomLevels, crsTransformerFactory)
                .stream()
                .filter(limits -> limits.getTileMatrix().equals(tileMatrix))
                .findAny()
                .orElse(null);

        if (tileLimits!=null) {
            if (tileLimits.getMaxTileCol()<col || tileLimits.getMinTileCol()>col ||
                    tileLimits.getMaxTileRow()<row || tileLimits.getMinTileRow()>row)
                // return 404, if outside the range
                throw new NotFoundException();
        }

        String path = definitionPath.replace("{tileMatrixSetId}", tileMatrixSetId)
                .replace("{tileMatrix}", tileMatrix)
                .replace("{tileRow}", tileRow)
                .replace("{tileCol}", tileCol);

        TileFormatExtension outputFormat = api.getOutputFormat(TileFormatExtension.class, requestContext.getMediaType(), path)
                .orElseThrow(NotAcceptableException::new);

        List<String> collections = queryParams.containsKey("collections") ?
                Splitter.on(",")
                        .splitToList(queryParams.get("collections")) :
                apiData.getCollections()
                        .values()
                        .stream()
                        .filter(collection -> apiData.isCollectionEnabled(collection.getId()))
                        .filter(collection -> {
                            Optional<TilesConfiguration> config = getExtensionConfiguration(apiData, collection, TilesConfiguration.class);
                            return config.isPresent() && config.get().getEnabled();
                        })
                        .map(collection -> collection.getId())
                        .collect(Collectors.toList());

        if (!outputFormat.canMultiLayer() && collections.size() > 1)
            throw new NotAcceptableException("The requested tile format supports only a single layer. Please select only a single collection.");

        Tile multiLayerTile = new ImmutableTile.Builder()
                .collectionIds(collections)
                .tileMatrixSet(tileMatrixSet)
                .tileLevel(level)
                .tileRow(row)
                .tileCol(col)
                .api(api)
                .temporary(!useCache)
                .featureProvider(featureProvider)
                .outputFormat(outputFormat)
                .build();

        if (collections.isEmpty()) {
            TilesQueriesHandler.OgcApiQueryInputTileEmpty queryInput = new ImmutableOgcApiQueryInputTileEmpty.Builder()
                    .from(getGenericQueryInput(api.getData()))
                    .tile(multiLayerTile)
                    .build();

            return queryHandler.handle(TilesQueriesHandler.Query.EMPTY_TILE, queryInput, requestContext);
        }

        // if cache can be used and the tile is cached for the requested format, return the cache
        if (useCache) {
            // get the tile from the cache and return it
            File tileFile = cache.getFile(multiLayerTile);
            if (tileFile.exists()) {
                TilesQueriesHandler.OgcApiQueryInputTileFile queryInput = new ImmutableOgcApiQueryInputTileFile.Builder()
                        .from(getGenericQueryInput(api.getData()))
                        .tile(multiLayerTile)
                        .tileFile(tileFile)
                        .build();

                return queryHandler.handle(TilesQueriesHandler.Query.TILE_FILE, queryInput, requestContext);
            }
        }

        Map<String, Tile> singleLayerTileMap = collections.stream()
                .collect(ImmutableMap.toImmutableMap(collectionId -> collectionId, collectionId -> new ImmutableTile.Builder()
                        .from(multiLayerTile)
                        .collectionIds(ImmutableList.of(collectionId))
                        .build()));

        // first execute the information that is passed as processing parameters (e.g., "properties")
        Map<String, Object> processingParameters = new HashMap<>();
        for (OgcApiQueryParameter parameter : allowedParameters) {
            processingParameters = parameter.transformContext(null, processingParameters, queryParams, api.getData());
        }

        // generate a query template for an arbitrary collection
        FeatureQuery query = outputFormat.getQuery(singleLayerTileMap.get(collections.get(0)), allowedParameters, queryParams, tilesConfiguration, requestContext.getUriCustomizer());

        Map<String, FeatureQuery> queryMap = collections.stream()
                .collect(ImmutableMap.toImmutableMap(collectionId -> collectionId, collectionId -> ImmutableFeatureQuery.builder()
                        .from(query)
                        .type(collectionId)
                        .build()));

        OgcApiFeaturesCoreConfiguration coreConfiguration = getExtensionConfiguration(apiData, OgcApiFeaturesCoreConfiguration.class).get();

        TilesQueriesHandler.OgcApiQueryInputTileMultiLayer queryInput = new ImmutableOgcApiQueryInputTileMultiLayer.Builder()
                .from(getGenericQueryInput(api.getData()))
                .tile(multiLayerTile)
                .singleLayerTileMap(singleLayerTileMap)
                .queryMap(queryMap)
                .processingParameters(processingParameters)
                .defaultCrs(coreConfiguration.getDefaultEpsgCrs())
                .build();

        return queryHandler.handle(TilesQueriesHandler.Query.MULTI_LAYER_TILE, queryInput, requestContext);
    }

    private void ensureFeatureProviderSupportsQueries(FeatureProvider2 featureProvider) {
        if (!featureProvider.supportsQueries()) {
            throw new IllegalStateException("feature provider does not support queries");
        }
    }
}