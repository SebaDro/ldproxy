/**
 * Copyright 2020 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.ogcapi.domain;

import de.ii.xtraplatform.crs.domain.EpsgCrs;

import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.core.Response;
import java.text.MessageFormat;
import java.util.*;

public interface QueriesHandler<T extends QueryIdentifier> {

    Map<T, QueryHandler<? extends QueryInput>> getQueryHandlers();

    default Response handle(T queryIdentifier, QueryInput queryInput,
                            ApiRequestContext requestContext) {

        QueryHandler<? extends QueryInput> queryHandler = getQueryHandlers().get(queryIdentifier);

       if (Objects.isNull(queryHandler)) {
           throw new IllegalStateException("No query handler found for " + queryIdentifier +".");
       }

        if (!queryHandler.isValidInput(queryInput)) {
            throw new RuntimeException(MessageFormat.format("Invalid query handler {0} for query input of class {1}.", queryHandler.getClass().getSimpleName(), queryInput.getClass().getSimpleName()));
        }

        return queryHandler.handle(queryInput, requestContext);
    }

    default Response.ResponseBuilder prepareSuccessResponse(OgcApi api,
                                                            ApiRequestContext requestContext) {
        return prepareSuccessResponse(api, requestContext, null);
    }

    default Response.ResponseBuilder prepareSuccessResponse(OgcApi api,
                                                            ApiRequestContext requestContext,
                                                            List<Link> links) {
        return prepareSuccessResponse(api, requestContext, links, null);
    }

    default Response.ResponseBuilder prepareSuccessResponse(OgcApi api,
                                                            ApiRequestContext requestContext,
                                                            List<Link> links,
                                                            EpsgCrs crs) {
        Response.ResponseBuilder response = Response.ok()
                                                    .type(requestContext
                                                        .getMediaType()
                                                        .type());

        Optional<Locale> language = requestContext.getLanguage();
        if (language.isPresent())
            response.language(language.get());

        if (links != null)
            // skip URI templates in the header as these are not RFC 8288 links
            links.stream()
                    .filter(link -> link.getTemplated()==null || !link.getTemplated())
                    .forEach(link -> response.links(link.getLink()));

        if (crs != null)
            response.header("Content-Crs", "<" + crs.toUriString() + ">");

        return response;
    }

    /**
     * Analyse the error reported by a feature stream. If it looks like a server-side error, re-throw
     * the exception, otherwise continue
     * @param error the exception reported by xtraplatform
     */
    default void processStreamError(Throwable error) {
        String errorMessage = error.getMessage();
        while (Objects.nonNull(error) && !Objects.equals(error,error.getCause())) {
            if (error instanceof org.eclipse.jetty.io.EofException) {
                // the connection has been lost, typically the client has cancelled the request, log on debug level
                return;
            } else if (error instanceof RuntimeException) {
                // Runtime exception is generated by xtraplatform, look at the cause
                error = error.getCause();
            } else {
                // some other exception occured, log as an error
                break;
            }
        }

        throw new InternalServerErrorException(errorMessage);
    }

}
