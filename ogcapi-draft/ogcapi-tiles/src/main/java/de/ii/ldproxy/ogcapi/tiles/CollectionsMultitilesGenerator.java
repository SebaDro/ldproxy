/**
 * Copyright 2020 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.ogcapi.tiles;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import de.ii.ldproxy.ogcapi.application.I18n;
import de.ii.ldproxy.ogcapi.domain.OgcApiApi;
import de.ii.ldproxy.ogcapi.domain.OgcApiRequestContext;
import de.ii.ldproxy.ogcapi.domain.URICustomizer;
import de.ii.ldproxy.ogcapi.features.core.api.OgcApiFeatureCoreProviders;
import de.ii.ldproxy.ogcapi.features.core.api.OgcApiFeatureFormatExtension;
import de.ii.xtraplatform.crs.domain.CrsTransformerFactory;
import de.ii.xtraplatform.features.domain.FeatureProvider2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;


public class CollectionsMultitilesGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(CollectionsMultitilesGenerator.class);

    private final I18n i18n;
    //TODO: OgcApiTilesProviders (use features core featureProvider id as fallback)
    private final OgcApiFeatureCoreProviders providers;
    private final Wfs3EndpointTiles wfs3EndpointTiles;

    CollectionsMultitilesGenerator(I18n i18n,
                                   OgcApiFeatureCoreProviders providers,
                                   Wfs3EndpointTiles wfs3EndpointTiles) {
        this.i18n = i18n;
        this.providers = providers;
        this.wfs3EndpointTiles = wfs3EndpointTiles;
    }

    /**
     * Construct a response for a multitiles request for multiple collections
     *
     * @param tileMatrixSetId       identifier of tile matrix set
     * @param bboxParam             value of the bbox request parameter
     * @param scaleDenominatorParam value of the scaleDenominator request parameter
     * @param multiTileType         value of the multiTileType request parameter
     * @param uriCustomizer         uri customizer
     * @param collections           requested collections
     * @param crsTransformerFactory
     * @return nultiple tiles from multiple collections
     */
    Response getCollectionsMultitiles(String tileMatrixSetId, String bboxParam, String scaleDenominatorParam,
                                      String multiTileType, URICustomizer uriCustomizer, Set<String> collections,
                                      CrsTransformerFactory crsTransformerFactory, UriInfo uriInfo, OgcApiApi service,
                                      OgcApiRequestContext wfs3Request, VectorTilesCache cache,
                                      OgcApiFeatureFormatExtension wfs3OutputFormatGeoJson) throws UnsupportedEncodingException {

        TileMatrixSet tileMatrixSet = TileMatrixSetCache.getTileMatrixSet(tileMatrixSetId);
        List<Integer> tileMatrices = MultitilesUtils.parseScaleDenominator(scaleDenominatorParam, tileMatrixSet);
        double[] bbox = MultitilesUtils.parseBbox(bboxParam, tileMatrixSet);
        String collectoinsCsv = String.join(",", collections);
        LOGGER.debug("GET TILES COLLECTIONS MULTITILES {} {}-{} {} {}", bbox, tileMatrices.get(0), tileMatrices.get(tileMatrices.size() - 1), multiTileType, collectoinsCsv);
        List<TileSetEntry> tileSetEntries = generateCollectionsTileSetEntries(bbox, tileMatrices, uriCustomizer, collectoinsCsv, tileMatrixSet);

        if ("url".equals(multiTileType)) {
            return Response.ok(ImmutableMap.of("tileSet", tileSetEntries))
                           .type("application/geo+json")
                           .build();
        } else if (multiTileType == null || "tiles".equals(multiTileType) || "full".equals(multiTileType)) {
            File zip = generateZip(tileSetEntries, tileMatrixSetId, collections, "full".equals(multiTileType),
                    crsTransformerFactory, uriInfo, service, cache, wfs3OutputFormatGeoJson, wfs3Request);
            return Response.ok(zip)
                           .type("application/zip")
                           .build();
        }
        throw new NotFoundException("Unknown multiTileType");
    }

    /**
     * Generate a list of tiles that cover the bounding box for given tile matrices
     *
     * @param bbox          bounding box specified by two points and their longitude and latitude coordinates (WGS 84)
     * @param tileMatrices  all tile matrices to be retrieved
     * @param uriCustomizer uri customizer
     * @return list of TileSet objects
     */
    private List<TileSetEntry> generateCollectionsTileSetEntries(double[] bbox, List<Integer> tileMatrices,
                                                                 URICustomizer uriCustomizer, String collections,
                                                                 TileMatrixSet tileMatrixSet) throws UnsupportedEncodingException {
        List<TileSetEntry> tileSets = new ArrayList<>();
        for (int tileMatrix : tileMatrices) {
            List<Integer> bottomTile = MultitilesUtils.pointToTile(bbox[0], bbox[1], tileMatrix, tileMatrixSet);
            List<Integer> topTile = MultitilesUtils.pointToTile(bbox[2], bbox[3], tileMatrix, tileMatrixSet);
            for (int row = topTile.get(0); row <= bottomTile.get(0); row++) {
                for (int col = bottomTile.get(1); col <= topTile.get(1); col++) {
                    tileSets.add(new ImmutableTileSetEntry.Builder()
                            .tileURL(URLDecoder.decode(uriCustomizer.copy()
                                                                    .clearParameters()
                                                                    .ensureLastPathSegments(Integer.toString(tileMatrix), Integer.toString(row), Integer.toString(col))
                                                                    .ensureNoTrailingSlash()
                                                                    .addParameter("collections", collections)
                                                                    .toString(), "UTF-8"))
                            .tileMatrix(tileMatrix)
                            .tileRow(row)
                            .tileCol(col)
                            .build());
                }
            }
        }
        return tileSets;
    }

    private File generateZip(List<TileSetEntry> tileSetEntries, String tileMatrixSetId,
                             Set<String> requestedCollections,
                             boolean isFull, CrsTransformerFactory crsTransformation, UriInfo uriInfo,
                             OgcApiApi service,
                             VectorTilesCache cache, OgcApiFeatureFormatExtension wfs3OutputFormatGeoJson,
                             OgcApiRequestContext wfs3Request) {
        File zip = null;
        VectorTileMapGenerator vectorTileMapGenerator = new VectorTileMapGenerator();

        try {
            zip = File.createTempFile(tileMatrixSetId, ".zip");
            FileOutputStream fout = new FileOutputStream(zip);
            ZipOutputStream zout = new ZipOutputStream(fout);

            if (isFull) {
                // add tileSet response document in the archive
                ObjectMapper mapper = new ObjectMapper();
                String jsonString = mapper.writeValueAsString(ImmutableMap.of("tileSet", tileSetEntries));
                File tmpFile = File.createTempFile(tileMatrixSetId, ".json");
                FileWriter writer = new FileWriter(tmpFile);
                writer.write(jsonString);
                writer.close();

                zout.putNextEntry(new ZipEntry(tileMatrixSetId + ".json"));

                try (FileInputStream fis = new FileInputStream(tmpFile.getAbsolutePath());
                     BufferedInputStream bis = new BufferedInputStream(fis, 1024)) {
                    int count;
                    byte[] data = new byte[1024];
                    while ((count = bis.read(data, 0, 1024)) != -1) {
                        zout.write(data, 0, count);
                    }
                }
                zout.closeEntry();
                tmpFile.deleteOnExit();

            }

            FeatureProvider2 featureProvider = providers.getFeatureProvider(service.getData());

            for (TileSetEntry entry : tileSetEntries) {
                VectorTile tile = new VectorTile(null, tileMatrixSetId, String.valueOf(entry.getTileMatrix()),
                        String.valueOf(entry.getTileRow()), String.valueOf(entry.getTileCol()), service, false, cache,
                        featureProvider, wfs3OutputFormatGeoJson);
                File tileFileMvt = tile.getFile(cache, "pbf");
                Map<String, File> layers = new HashMap<>();
                Set<String> collectionIds = Wfs3EndpointTiles.getCollectionIdsDataset(vectorTileMapGenerator.getAllCollectionIdsWithTileExtension(service.getData()), vectorTileMapGenerator.getEnabledMap(service.getData()),
                        vectorTileMapGenerator.getFormatsMap(service.getData()), vectorTileMapGenerator.getMinMaxMap(service.getData(), true), false, false, false);

                if (!tileFileMvt.exists()) {
                    wfs3EndpointTiles.generateTileDataset(tile, tileFileMvt, layers, collectionIds, requestedCollections,
                            null, service, featureProvider, String.valueOf(entry.getTileMatrix()), String.valueOf(entry.getTileRow()),
                            String.valueOf(entry.getTileCol()), tileMatrixSetId, false, cache, wfs3Request, crsTransformation,
                            uriInfo, false, wfs3OutputFormatGeoJson, i18n, vectorTileMapGenerator, null);
                } else {
                    boolean invalid = false;

                    for (String collectionId : collectionIds) {
                        VectorTile layerTile = new VectorTile(collectionId, tileMatrixSetId, String.valueOf(entry.getTileMatrix()),
                                String.valueOf(entry.getTileRow()), String.valueOf(entry.getTileCol()), service, false,
                                cache, featureProvider, wfs3OutputFormatGeoJson);
                        File tileFileJson = layerTile.getFile(cache, "json");
                        if (tileFileJson.exists()) {
                            if (TileGeneratorJson.deleteJSON(tileFileJson)) {
                                tileFileJson.delete();
                                layerTile.getFile(cache, "pbf")
                                         .delete();
                                invalid = true;
                            }
                        } else {
                            invalid = true;
                        }
                    }

                    if (invalid) {
                        wfs3EndpointTiles.generateTileDataset(tile, tileFileMvt, layers, collectionIds, requestedCollections,
                                null, service, featureProvider, String.valueOf(entry.getTileMatrix()), String.valueOf(entry.getTileRow()),
                                String.valueOf(entry.getTileCol()), tileMatrixSetId, false, cache, wfs3Request, crsTransformation,
                                uriInfo, true, wfs3OutputFormatGeoJson, i18n, vectorTileMapGenerator, null);
                    }
                }

                // add the generated tile to the archive
                String path = new StringBuilder(tileMatrixSetId)
                        .append(File.separator)
                        .append(entry.getTileMatrix())
                        .append(File.separator)
                        .append(entry.getTileRow())
                        .append(File.separator)
                        .append(entry.getTileCol())
                        .append(".mvt")
                        .toString();
                zout.putNextEntry(new ZipEntry(path));

                try (FileInputStream fis = new FileInputStream(tileFileMvt.getAbsolutePath());
                     BufferedInputStream bis = new BufferedInputStream(fis, 1024)) {
                    int count;
                    byte[] data = new byte[1024];
                    while ((count = bis.read(data, 0, 1024)) != -1) {
                        zout.write(data, 0, count);
                    }
                }
                zout.closeEntry();
            }
            zout.close();
            fout.close();

        } catch (IOException e) {
            e.printStackTrace();
        }

        return zip;
    }

}
