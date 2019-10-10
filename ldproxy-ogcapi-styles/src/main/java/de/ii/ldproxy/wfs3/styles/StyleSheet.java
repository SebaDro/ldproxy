/**
 * Copyright 2019 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.wfs3.styles;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import de.ii.ldproxy.ogcapi.domain.OgcApiLink;
import org.immutables.value.Value;

import java.util.Optional;

@Value.Immutable
@Value.Style(deepImmutablesDetection = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonDeserialize(as = ImmutableStyleSheet.class)
public interface StyleSheet {

    Optional<String> getTitle();

    Optional<String> getVersion();

    Optional<String> getSpecification();

    @JsonProperty("native")
    Optional<Boolean> native_();

    Optional<String> getTileMatrixSet();

    Optional<OgcApiLink> getLink();
}