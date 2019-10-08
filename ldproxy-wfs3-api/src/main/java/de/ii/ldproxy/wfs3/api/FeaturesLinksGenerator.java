/**
 * Copyright 2019 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.wfs3.api;

import com.google.common.collect.ImmutableList;
import de.ii.ldproxy.ogcapi.application.DefaultLinksGenerator;
import de.ii.ldproxy.ogcapi.application.I18n;
import de.ii.ldproxy.ogcapi.domain.ImmutableOgcApiLink;
import de.ii.ldproxy.ogcapi.domain.OgcApiLink;
import de.ii.ldproxy.ogcapi.domain.OgcApiMediaType;
import de.ii.ldproxy.ogcapi.domain.URICustomizer;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class FeaturesLinksGenerator extends DefaultLinksGenerator {

    public List<OgcApiLink> generateLinks(URICustomizer uriBuilder,
                                          int offset,
                                          int limit,
                                          int defaultLimit,
                                          OgcApiMediaType mediaType,
                                          List<OgcApiMediaType> alternateMediaTypes,
                                          boolean homeLink,
                                          I18n i18n,
                                          Optional<Locale> language)
    {
        final ImmutableList.Builder<OgcApiLink> builder = new ImmutableList.Builder<OgcApiLink>()
                .addAll(super.generateLinks(uriBuilder, mediaType, alternateMediaTypes, i18n, language));

        // we have to create a next link here as we do not know numberMatched yet, but it will
        // be removed again in the feature transformer, if we are on the last page
        builder.add(new ImmutableOgcApiLink.Builder()
                .href(getUrlWithPageAndCount(uriBuilder.copy(), offset + limit, limit, defaultLimit))
                .rel("next")
                .type(mediaType.type()
                        .toString())
                .description(i18n.get("nextLink",language))
                .build());
        if (offset > 0) {
            builder.add(new ImmutableOgcApiLink.Builder()
                    .href(getUrlWithPageAndCount(uriBuilder.copy(), offset - limit, limit, defaultLimit))
                    .rel("prev")
                    .type(mediaType.type()
                            .toString())
                    .description(i18n.get("prevLink",language))
                    .build());
            builder.add(new ImmutableOgcApiLink.Builder()
                    .href(getUrlWithPageAndCount(uriBuilder.copy(), 0, limit, defaultLimit))
                    .rel("first")
                    .type(mediaType.type()
                            .toString())
                    .description(i18n.get("firstLink",language))
                    .build());
        }

        if (homeLink)
            builder.add(new ImmutableOgcApiLink.Builder()
                    .href(uriBuilder
                            .copy()
                            .removeLastPathSegments(3)
                            .ensureNoTrailingSlash()
                            .clearParameters()
                            .toString())
                    .rel("home")
                    .description(i18n.get("homeLink",language))
                    .build());

        return builder.build();
    }

    private String getUrlWithPageAndCount(final URICustomizer uriBuilder, final int offset, final int limit, final int defaultLimit) {
        if (offset==0 && limit==defaultLimit) {
            return uriBuilder
                    .ensureNoTrailingSlash()
                    .removeParameters("offset", "limit")
                    .toString();
        } else if (limit==defaultLimit) {
            return uriBuilder
                    .ensureNoTrailingSlash()
                    .removeParameters("offset", "limit")
                    .setParameter("offset", String.valueOf(Integer.max(0, offset)))
                    .toString();
        } else if (offset==0) {
            return uriBuilder
                    .ensureNoTrailingSlash()
                    .removeParameters("offset", "limit")
                    .setParameter("limit", String.valueOf(limit))
                    .toString();
        }

        return uriBuilder
                .ensureNoTrailingSlash()
                .removeParameters("offset", "limit")
                .setParameter("limit", String.valueOf(limit))
                .setParameter("offset", String.valueOf(Integer.max(0, offset)))
                .toString();
    }
}
