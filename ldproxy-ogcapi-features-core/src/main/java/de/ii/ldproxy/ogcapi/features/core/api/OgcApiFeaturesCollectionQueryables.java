/**
 * Copyright 2020 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.ogcapi.features.core.api;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.immutables.value.Value;

import java.util.List;

@Value.Immutable
@Value.Style(builder = "new")
@JsonDeserialize(builder = ImmutableOgcApiFeaturesCollectionQueryables.Builder.class)
public interface OgcApiFeaturesCollectionQueryables {

    List<String> getSpatial();

    List<String> getTemporal();

    List<String> getOther();
}
