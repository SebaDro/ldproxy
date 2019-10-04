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
import de.ii.ldproxy.ogcapi.domain.ImmutableOgcApiLink;
import de.ii.ldproxy.ogcapi.domain.OgcApiLink;
import de.ii.ldproxy.ogcapi.domain.OgcApiMediaType;
import de.ii.ldproxy.ogcapi.domain.URICustomizer;

import java.util.List;

public class FeaturesLinksGenerator extends DefaultLinksGenerator {

    public List<OgcApiLink> generateLinks(URICustomizer uriBuilder,
                                          int offset,
                                          int limit,
                                          int defaultLimit,
                                          OgcApiMediaType mediaType,
                                          List<OgcApiMediaType> alternateMediaTypes)
    {
        final ImmutableList.Builder<OgcApiLink> builder = new ImmutableList.Builder<OgcApiLink>()
                .addAll(super.generateLinks(uriBuilder, mediaType, alternateMediaTypes));

        // TODO: make next links opaque
        // TODO: no next link, if there is no next page (but we don't know this yet)
        builder.add(new ImmutableOgcApiLink.Builder()
                .href(getUrlWithPageAndCount(uriBuilder.copy(), offset + limit, limit, defaultLimit))
                .rel("next")
                .type(mediaType.type()
                        .toString())
                .description("next page")
                .build());
        if (offset > 0) {
            builder.add(new ImmutableOgcApiLink.Builder()
                    .href(getUrlWithPageAndCount(uriBuilder.copy(), offset - limit, limit, defaultLimit))
                    .rel("prev")
                    .type(mediaType.type()
                            .toString())
                    .description("previous page")
                    .build());
            builder.add(new ImmutableOgcApiLink.Builder()
                    .href(getUrlWithPageAndCount(uriBuilder.copy(), 0, limit, defaultLimit))
                    .rel("first")
                    .type(mediaType.type()
                            .toString())
                    .description("first page")
                    .build());
        }

        builder.add(new ImmutableOgcApiLink.Builder()
                .href(uriBuilder
                        .copy()
                        .removeLastPathSegments(3)
                        .ensureNoTrailingSlash()
                        .clearParameters()
                        .toString())
                .rel("home")
                .description("API Landing Page")
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