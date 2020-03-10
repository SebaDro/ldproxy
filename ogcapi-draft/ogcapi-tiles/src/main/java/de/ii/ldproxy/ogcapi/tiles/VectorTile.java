/**
 * Copyright 2020 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.ogcapi.tiles;


import de.ii.ldproxy.ogcapi.application.I18n;
import de.ii.ldproxy.ogcapi.domain.OgcApiApi;
import de.ii.ldproxy.ogcapi.domain.OgcApiApiDataV2;
import de.ii.ldproxy.ogcapi.domain.OgcApiRequestContext;
import de.ii.ldproxy.ogcapi.features.core.api.OgcApiFeatureFormatExtension;
import de.ii.xtraplatform.crs.domain.BoundingBox;
import de.ii.xtraplatform.crs.domain.CrsTransformationException;
import de.ii.xtraplatform.crs.domain.CrsTransformer;
import de.ii.xtraplatform.crs.domain.CrsTransformerFactory;
import de.ii.xtraplatform.crs.domain.EpsgCrs;
import de.ii.xtraplatform.crs.domain.OgcCrs;
import de.ii.xtraplatform.features.domain.FeatureProvider2;
import org.locationtech.jts.geom.util.AffineTransformation;
import org.slf4j.LoggerFactory;

import javax.ws.rs.NotAcceptableException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.MultivaluedMap;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;


/**
 * This class represents a vector tile
 */
class VectorTile {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(Wfs3EndpointTiles.class);

    private final int level;
    private final int row;
    private final int col;
    private final String collectionId;
    private final TileMatrixSet tileMatrixSet;
    private final OgcApiApiDataV2 apiData;
    private final OgcApiApi api;
    private final FeatureProvider2 featureProvider;
    private final boolean temporary;
    private final String fileName;
    private final OgcApiFeatureFormatExtension wfs3OutputFormatGeoJson;


    /**
     * specify the vector tile
     *
     * @param collectionId            the id of the collection in which the tile belongs
     * @param tileMatrixSetId         the local identifier of a specific tile matrix set
     * @param level                   the zoom level as a string
     * @param row                     the row number as a string
     * @param col                     the column number as a string
     * @param api                     the Features API
     * @param temporary               the info, if this is a temporary or permanent vector tile
     * @param cache                   the tile cache
     * @param featureProvider         the wfs3 service feature provider
     * @param wfs3OutputFormatGeoJson
     */

    VectorTile(String collectionId, String tileMatrixSetId, String level, String row, String col,
               OgcApiApi api, boolean temporary, VectorTilesCache cache,
               FeatureProvider2 featureProvider,
               OgcApiFeatureFormatExtension wfs3OutputFormatGeoJson) {
        this.wfs3OutputFormatGeoJson = wfs3OutputFormatGeoJson;
        this.api = api;
        this.apiData = api.getData();

        // check and process parameters

        // check, if collectionId is valid
        if (collectionId != null) {
            Set<String> collectionIds = apiData.getCollections()
                                               .keySet();
            if (collectionId.isEmpty() || !collectionIds.contains(collectionId)) {
                throw new NotFoundException();
            }
        }
        this.collectionId = collectionId;

        // get the Tile Matrix Set
        tileMatrixSet = TileMatrixSetCache.getTileMatrixSet(tileMatrixSetId);

        this.level = checkLevel(tileMatrixSet, level);
        if (this.level == -1)
            throw new NotFoundException();

        this.row = checkRow(tileMatrixSet, this.level, row);
        if (this.row == -1)
            throw new NotFoundException();

        this.col = checkColumn(tileMatrixSet, this.level, col);
        if (this.col == -1)
            throw new NotFoundException();

        this.featureProvider = featureProvider;
        this.temporary = temporary;

        if (this.temporary) {
            fileName = UUID.randomUUID()
                           .toString();
        } else {
            fileName = String.format("%s_%s_%s", Integer.toString(this.level), Integer.toString(this.row), Integer.toString(this.col));
        }
    }

    public int getLevel() {
        return level;
    }

    public int getRow() {
        return row;
    }

    public int getCol() {
        return col;
    }

    public String getCollectionId() {
        return collectionId;
    }


    public OgcApiFeatureFormatExtension getWfs3OutputFormatGeoJson() {
        return wfs3OutputFormatGeoJson;
    }

    /**
     * @return the tile matrix set object
     */
    public TileMatrixSet getTileMatrixSet() {
        return tileMatrixSet;
    }

    public OgcApiApi getApi() {
        return api;
    }

    public OgcApiApiDataV2 getApiData() {
        return apiData;
    }

    public FeatureProvider2 getFeatureProvider() {
        return featureProvider;
    }

    public boolean getTemporary() {
        return temporary;
    }


    /**
     * Verify that the zoom level is an integer value in the valid range for the tile matrix set
     *
     * @param tileMatrixSet the tile matrix set used in the request
     * @param level         the zoom level as a string
     * @return the zoom level as an integer, or -1 in case of an invalid zoom level
     */
    private int checkLevel(TileMatrixSet tileMatrixSet, String level) {
        int l;
        try {
            l = Integer.parseInt(level);
            if (l > tileMatrixSet.getMaxLevel() || l < tileMatrixSet.getMinLevel()) {
                l = -1;
            }
        } catch (NumberFormatException e) {
            l = -1;
        }
        return l;
    }

    /**
     * Verify that the row number is an integer value
     *
     * @param tileMatrixSet the tile matrix set used in the request
     * @param level         the zoom level
     * @param row           the row number as a string
     * @return the row number as an integer, or -1 in case of an invalid value
     */
    private int checkRow(TileMatrixSet tileMatrixSet, int level, String row) {
        int r;
        try {
            r = Integer.parseInt(row);
            if (!tileMatrixSet.validateRow(level, r))
                r = -1;
        } catch (NumberFormatException e) {
            r = -1;
        }
        return r;
    }

    /**
     * Verify that the column number is an integer value
     *
     * @param tileMatrixSet the tile matrix set used in the request
     * @param level         the zoom level
     * @param col           the column number as a string
     * @return the column number as an integer, or -1 in case of an invalid value
     */
    private int checkColumn(TileMatrixSet tileMatrixSet, int level, String col) {
        int c;
        try {
            c = Integer.parseInt(col);
            if (!tileMatrixSet.validateCol(level, c))
                c = -1;
        } catch (NumberFormatException e) {
            c = -1;
        }
        return c;
    }

    /**
     * fetch the file object of a tile in the cache
     *
     * @param cache     the tile cache
     * @param extension the file extension of the cached file
     * @return the file object of the tile in the cache
     */
    File getFile(VectorTilesCache cache, String extension) {
        return new File(this.getTileDirectory(cache), String.format("%s.%s", fileName, extension));
    }

    /**
     * retrieve the subdirectory in the tiles directory for the selected service, tile matrix set, etc.
     *
     * @param cache the tile cache
     * @return the directory with the cached tile files
     */
    private File getTileDirectory(VectorTilesCache cache) {
        File subDir;
        if (temporary)
            subDir = cache.getTmpDirectory();
        else
            subDir = new File(new File(new File(cache.getTilesDirectory(), apiData.getId()), (collectionId == null ? "__all__" : collectionId)), tileMatrixSet.getId());

        if (!subDir.exists()) {
            subDir.mkdirs();
        }
        return subDir;
    }

    /**
     * Creates an affine transformation for converting geometries in lon/lat to tile coordinates.
     *
     * @param crsTransformerFactory the coordinate reference system transformation object to transform coordinates
     * @return the transform
     * @throws CrsTransformationException an error occurred when transforming the coordinates
     */
    public AffineTransformation createTransformLonLatToTile(
            CrsTransformerFactory crsTransformerFactory) throws CrsTransformationException {

        BoundingBox bbox = getBoundingBox(OgcCrs.CRS84, crsTransformerFactory);

        double lonMin = bbox.getXmin();
        double lonMax = bbox.getXmax();
        double latMin = bbox.getYmin();
        double latMax = bbox.getYmax();

        double tileSize = tileMatrixSet.getTileSize();
        double xScale = tileSize / (lonMax - lonMin);
        double yScale = tileSize / (latMax - latMin);

        double xOffset = -lonMin * xScale;
        double yOffset = latMin * yScale + tileSize;

        return new AffineTransformation(xScale, 0.0d, xOffset, 0.0d, -yScale, yOffset);
    }


    /**
     * @return the bounding box of the tile in the native CRS of the tile
     */
    BoundingBox getBoundingBox() {
        return tileMatrixSet.getTileBoundingBox(level, col, row);
    }

    /**
     * @param crs               the target coordinate references system
     * @param crsTransformerFactory the coordinate reference system transformation object to transform coordinates
     * @return the bounding box of the tile matrix set in the form of the target crs
     * @throws CrsTransformationException an error occurred when transforming the coordinates
     */
    private BoundingBox getBoundingBox(EpsgCrs crs,
                                       CrsTransformerFactory crsTransformerFactory) throws CrsTransformationException {
        BoundingBox bboxTileMatrixSetCrs = getBoundingBox();
        Optional<CrsTransformer> transformer = crsTransformerFactory.getTransformer(tileMatrixSet.getCrs(), crs);

        if (!transformer.isPresent()) {
            return bboxTileMatrixSetCrs;
        }

        return transformer.get().transformBoundingBox(bboxTileMatrixSetCrs);
    }

    /**
     * checks if the requested format for the tile is specified in the Config and therefore valid. If it is not valid then throw a NotAcceptableException.
     *
     * @param formatsMap        a map with all collections and the supported formats
     * @param collectionId      the id of the collection you want to check the formats for
     * @param mediaType         the requested format
     * @param forLinksOrDataset boolean, false if it is requested from a collection, true if requested from Collections or Link.
     * @return false if forLinksOrDataset is true and the format is not supported.
     * NotAcceptableException if forLinksOrDataset is false and the format is not supported
     * true if the format is supported
     */
    public static boolean checkFormat(Map<String, List<String>> formatsMap, String collectionId, String mediaType,
                                      boolean forLinksOrDataset) {

        if (!Objects.isNull(formatsMap) && formatsMap.containsKey(collectionId)) {
            List<String> formats = formatsMap.get(collectionId);

            if (!formats.contains(mediaType)) {
                if (forLinksOrDataset)
                    return false;
                else
                    throw new NotAcceptableException();
            }

            return true;
        }
        throw new NotFoundException();
    }

    /**
     * checks if the zoom level is valid for the tileMatrixSet and the specified min and max values in the config. If no Value is specified in the config
     * the whole zoom level range of the TileMatrixSet is supported. If the zoom level is not valid, generate empty tile
     *
     * @param zoomLevel               the zoom level of the tile, which should be checked
     * @param zoomLevelsMap           a map with all collections that have the tiles extension and their zoomLevels
     * @param wfsService              the wfs3Service
     * @param wfs3OutputFormatGeoJson the wfs3OutputFormat Extension
     * @param collectionId            the id of the collection of the tile
     * @param tileMatrixSetId         the id of the tileMatrixSet of the tile
     * @param mediaType               the media type of the tile, either application/json or application/vnd.mapbox-vector-tile
     * @param row                     the row of the tile
     * @param col                     the column of the Tile
     * @param doNotCache              boolean value if temporary tile or not
     * @param cache                   the tile cache
     * @param isCollection            boolean collection or dataset Tile
     * @param wfs3Request             the request
     * @param crsTransformerFactory       the coordinate reference system transformation object to transform coordinates
     * @throws FileNotFoundException
     */
    public static MinMax checkZoomLevel(int zoomLevel,
                                        Map<String, Map<String, MinMax>> zoomLevelsMap,
                                        OgcApiApi wfsService,
                                        OgcApiFeatureFormatExtension wfs3OutputFormatGeoJson,
                                        String collectionId, String tileMatrixSetId, String mediaType,
                                        String row, String col, boolean doNotCache, VectorTilesCache cache,
                                        boolean isCollection, OgcApiRequestContext wfs3Request,
                                        CrsTransformerFactory crsTransformerFactory, I18n i18n) throws FileNotFoundException {
        // first check, if the zoom level is valid for the tile matrix set
        TileMatrixSet tileMatrixSet = TileMatrixSetCache.getTileMatrixSet(tileMatrixSetId);
        if (zoomLevel > tileMatrixSet.getMaxLevel() || zoomLevel < tileMatrixSet.getMinLevel()) {
            throw new NotFoundException();
        }

        // first check, if we have a zoom level range in the configuration;
        // if not, the fallback is the tiling scheme range
        MinMax zoomLevels = Objects.isNull(zoomLevelsMap) ?
                new ImmutableMinMax.Builder()
                        .min(tileMatrixSet.getMinLevel())
                        .max(tileMatrixSet.getMaxLevel())
                        .build() :
                zoomLevelsMap.entrySet()
                             .stream()
                             .filter(entry -> collectionId == null || entry.getKey()
                                                                           .equals(collectionId))
                             .map(entry -> entry.getValue())
                             .map(entry -> entry == null ? null : entry.get(tileMatrixSetId))
                             .filter(entry -> entry != null)
                             .reduce((minmax1, minmax2) -> new ImmutableMinMax.Builder()
                                     .min(Math.min(minmax1.getMin(), minmax2.getMin()))
                                     .max(Math.max(minmax1.getMax(), minmax2.getMax()))
                                     .build())
                             .map(minmax -> new ImmutableMinMax.Builder()
                                     .min(Math.max(minmax.getMin(), tileMatrixSet.getMinLevel()))
                                     .max(Math.min(minmax.getMax(), tileMatrixSet.getMaxLevel()))
                                     .build())
                             .orElse(new ImmutableMinMax.Builder()
                                     .min(tileMatrixSet.getMinLevel())
                                     .max(tileMatrixSet.getMaxLevel())
                                     .build());

        // if requested zoom Level is not in range return 404
        if (zoomLevel < zoomLevels.getMin() || zoomLevel > zoomLevels.getMax()) {
            throw new NotFoundException();
        }

        return zoomLevels;
    }

    /**
     * If the zoom Level is not valid generate empty JSON Tile or empty MVT.
     *
     * @param collectionId            the id of the collection of the tile
     * @param tileMatrixSetId         the id of the tileMatrixSet of the tile
     * @param zoomLevel               the zoom level of the tile, which should be checked
     * @param wfsService              the wfs3Service
     * @param featureProvider
     * @param wfs3OutputFormatGeoJson the wfs3OutputFormat Extension
     * @param row                     the row of the tile
     * @param col                     the column of the Tile
     * @param doNotCache              boolean value if temporary tile or not
     * @param cache                   the tile cache
     * @param isCollection            boolean collection or dataset Tile
     * @param wfs3Request             the request
     * @param crsTransformerFactory       the coordinate reference system transformation object to transform coordinates
     * @throws FileNotFoundException
     */
    public static void generateEmptyTile(String collectionId, String tileMatrixSetId, int zoomLevel,
                                         OgcApiApi wfsService, FeatureProvider2 featureProvider,
                                         OgcApiFeatureFormatExtension wfs3OutputFormatGeoJson,
                                         String mediaType, String row, String col, boolean doNotCache,
                                         VectorTilesCache cache, boolean isCollection, OgcApiRequestContext wfs3Request,
                                         CrsTransformerFactory crsTransformerFactory, I18n i18n) throws FileNotFoundException {
        TileMatrixSet tileMatrixSet = TileMatrixSetCache.getTileMatrixSet(tileMatrixSetId);
        try {
            if (mediaType.equals("application/json")) {
                VectorTile tile = new VectorTile(collectionId, tileMatrixSetId, Integer.toString(zoomLevel), row, col, wfsService, doNotCache, cache, featureProvider, wfs3OutputFormatGeoJson);
                File tileFileJSON = tile.getFile(cache, "json");
                if (!tileFileJSON.exists()) {
                    TileGeneratorJson.generateEmptyJSON(tileFileJSON, tileMatrixSet, wfsService.getData(), wfs3OutputFormatGeoJson, collectionId, isCollection, wfs3Request, zoomLevel, Integer.parseInt(row), Integer.parseInt(col), crsTransformerFactory, wfsService, i18n, wfs3Request.getLanguage());
                }
            }
            //generate empty MVT
            if (mediaType.equals("application/vnd.mapbox-vector-tile")) {
                VectorTile tile = new VectorTile(collectionId, tileMatrixSetId, Integer.toString(zoomLevel), row, col, wfsService, doNotCache, cache, featureProvider, wfs3OutputFormatGeoJson);
                File tileFileMvt = tile.getFile(cache, "pbf");

                if (!tileFileMvt.exists()) {
                    VectorTile jsonTile = new VectorTile(collectionId, tileMatrixSetId, Integer.toString(zoomLevel), row, col, wfsService, doNotCache, cache, featureProvider, wfs3OutputFormatGeoJson);
                    File tileFileJSON = jsonTile.getFile(cache, "json");
                    if (!tileFileJSON.exists()) {
                        TileGeneratorJson.generateEmptyJSON(tileFileJSON, tileMatrixSet, wfsService.getData(), wfs3OutputFormatGeoJson, collectionId, isCollection, wfs3Request, zoomLevel, Integer.parseInt(row), Integer.parseInt(col), crsTransformerFactory, wfsService, i18n, wfs3Request.getLanguage());
                    }
                    TileGeneratorMvt.generateEmptyMVT(tileFileMvt, tileMatrixSet);
                }
            }
        } catch (NullPointerException ignored) {
        }
    }

    public static List<String> getPropertiesList(MultivaluedMap<String, String> queryParameters) {
        List propertiesList = new ArrayList();
        if (queryParameters.containsKey("properties")) {
            String propertiesString = queryParameters.get("properties")
                                                     .toString();
            propertiesString = propertiesString.substring(1, propertiesString.length() - 1);
            String[] parts = propertiesString.split(",");
            for (String part : parts) {
                propertiesList.add(part);
            }
        } else {
            propertiesList.add("*");
        }
        return propertiesList;

    }
}

