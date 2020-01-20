/**
 * Copyright 2020 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.ogcapi.tiles;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import de.ii.xtraplatform.crs.api.EpsgCrs;
import org.immutables.value.Value;

import java.util.Optional;

@Value.Immutable
@Value.Style(deepImmutablesDetection = true)
@JsonDeserialize(builder = ImmutableTileMatrixSetBoundingBox.Builder.class)
public abstract class TileMatrixSetBoundingBox {

    @Value.Default
    public String getType() { return "BoundingBoxType"; }

    public abstract double[] getLowerCorner();
    public abstract double[] getUpperCorner();

    /**
     * the coordinate reference system that is the basis of this tiling scheme
     */
    public abstract Optional<EpsgCrs> getCrsEpsg();

    @Value.Derived
    public Optional<String> getCrs() {
        if (getCrsEpsg().isPresent())
            return Optional.of(getCrsEpsg().get().getAsUri());

        return Optional.empty();
    }
}