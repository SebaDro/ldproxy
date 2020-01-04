/**
 * Copyright 2020 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.wfs3.vt;

import com.google.common.collect.ImmutableList;
import de.ii.ldproxy.ogcapi.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ldproxy.ogcapi.domain.OgcApiDatasetData;
import de.ii.ldproxy.wfs3.vt.TilesConfiguration.MinMax;
import de.ii.xtraplatform.crs.api.BoundingBox;
import de.ii.xtraplatform.crs.api.CrsTransformation;
import de.ii.xtraplatform.crs.api.CrsTransformationException;
import de.ii.xtraplatform.crs.api.CrsTransformer;
import de.ii.xtraplatform.crs.api.EpsgCrs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * This class is responsible for generating a list of tileMatrixSetLimits in json responses for /tiles and /collections/{collectionsId}/tiles requests.
 */
public class TileMatrixSetLimitsGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(TileMatrixSetLimitsGenerator.class);

    /**
     * Return a list of tileMatrixSetLimits for a single collection.
     * @param data service dataset
     * @param collectionId name of the collection
     * @param tileMatrixSetId identifier of a specific tile matrix set
     * @param crsTransformation crs transfromation
     * @return list of TileMatrixSetLimits
     */
    public static List<TileMatrixSetLimits> getCollectionTileMatrixSetLimits(OgcApiDatasetData data, String collectionId,
                                                                             String tileMatrixSetId, MinMax tileMatrixRange,
                                                                             CrsTransformation crsTransformation) {

        TileMatrixSet tileMatrixSet = TileMatrixSetCache.getTileMatrixSet(tileMatrixSetId);

        List<FeatureTypeConfigurationOgcApi> collectionData = data.getFeatureTypes()
                .values()
                .stream()
                .filter(featureType -> collectionId.equals(featureType.getId()))
                .collect(Collectors.toList());
        BoundingBox bbox = collectionData.get(0).getExtent().getSpatial();

        if (!bbox.getEpsgCrs().equals(tileMatrixSet.getCrs())) {
            CrsTransformer transformer = crsTransformation.getTransformer(bbox.getEpsgCrs(), tileMatrixSet.getCrs());
            try {
                bbox = transformer.transformBoundingBox(bbox);
            } catch (CrsTransformationException e) {
                LOGGER.error(String.format(Locale.US, "Cannot generate tile matrix set limits. Error converting bounding box (%f, %f, %f, %f) to %s.", bbox.getXmin(), bbox.getYmin(), bbox.getXmax(), bbox.getYmax(), bbox.getEpsgCrs().getAsSimple()));
                return ImmutableList.of();
            }
        }
        return tileMatrixSet.getLimitsList(tileMatrixRange, bbox);
    }

    /**
     * Return a list of tileMatrixSetLimits for all collections in the dataset.
     * @param data service dataset
     * @param tileMatrixSetId identifier of a specific tile matrix set
     * @param crsTransformation crs transfromation
     * @return list of TileMatrixSetLimits
     */
    public static List<TileMatrixSetLimits> getTileMatrixSetLimits(OgcApiDatasetData data, String tileMatrixSetId,
                                                            MinMax tileMatrixRange, CrsTransformation crsTransformation) {

        TileMatrixSet tileMatrixSet = TileMatrixSetCache.getTileMatrixSet(tileMatrixSetId);

        double[] spatialExtent = data.getSpatialExtent();
        BoundingBox bbox = new BoundingBox(spatialExtent[0], spatialExtent[1], spatialExtent[2], spatialExtent[3], new EpsgCrs(4326, true));
        if (!bbox.getEpsgCrs().equals(tileMatrixSet.getCrs())) {
            CrsTransformer transformer = crsTransformation.getTransformer(bbox.getEpsgCrs(), tileMatrixSet.getCrs());
            try {
                bbox = transformer.transformBoundingBox(bbox);
            } catch (CrsTransformationException e) {
                LOGGER.error(String.format(Locale.US, "Cannot generate tile matrix set limits. Error converting bounding box (%f, %f, %f, %f) to %s.", bbox.getXmin(), bbox.getYmin(), bbox.getXmax(), bbox.getYmax(), bbox.getEpsgCrs().getAsSimple()));
                return ImmutableList.of();
            }
        }

        return tileMatrixSet.getLimitsList(tileMatrixRange, bbox);
    }
}
