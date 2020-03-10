/**
 * Copyright 2020 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.ogcapi.domain;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public interface OgcApiConformanceDeclarationExtension extends OgcApiContentExtension {

    ImmutableConformanceDeclaration.Builder process(ImmutableConformanceDeclaration.Builder conformanceDeclarationBuilder,
                                                    OgcApiApiDataV2 apiData,
                                                    URICustomizer uriCustomizer,
                                                    OgcApiMediaType mediaType,
                                                    List<OgcApiMediaType> alternateMediaTypes,
                                                    Optional<Locale> language);

    default String getResourceName() { return "Conformance Declaration"; };
}
