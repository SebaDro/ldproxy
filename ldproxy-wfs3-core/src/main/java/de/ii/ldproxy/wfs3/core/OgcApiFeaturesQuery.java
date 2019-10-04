/**
 * Copyright 2019 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.wfs3.core;

import de.ii.ldproxy.ogcapi.domain.OgcApiDataset;
import de.ii.ldproxy.ogcapi.domain.OgcApiDatasetData;
import de.ii.ldproxy.ogcapi.domain.OgcApiExtensionRegistry;
import de.ii.ldproxy.ogcapi.domain.OgcApiParameterExtension;
import de.ii.xtraplatform.crs.api.BoundingBox;
import de.ii.xtraplatform.crs.api.CrsTransformationException;
import de.ii.xtraplatform.crs.api.EpsgCrs;
import de.ii.xtraplatform.feature.provider.api.FeatureQuery;
import de.ii.xtraplatform.feature.provider.api.ImmutableFeatureQuery;
import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.apache.felix.ipojo.annotations.Requires;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.threeten.extra.Interval;

import javax.ws.rs.BadRequestException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;


/**
 * @author zahnen
 */
@Component
@Provides(specifications = {OgcApiFeaturesQuery.class})
@Instantiate
public class OgcApiFeaturesQuery {

    // TODO review

    private static final String TIMESTAMP_REGEX = "([0-9]+)-(0[1-9]|1[012])-(0[1-9]|[12][0-9]|3[01])[Tt]([01][0-9]|2[0-3]):([0-5][0-9]):([0-5][0-9]|60)(\\.[0-9]+)?(([Zz])|([\\+|\\-]([01][0-9]|2[0-3]):[0-5][0-9]))";
    private static final String OPEN_REGEX = "(\\.\\.)?";

    private static final Logger LOGGER = LoggerFactory.getLogger(OgcApiFeaturesQuery.class);

    private final OgcApiExtensionRegistry wfs3ExtensionRegistry;

    public OgcApiFeaturesQuery(@Requires OgcApiExtensionRegistry wfs3ExtensionRegistry) {
        this.wfs3ExtensionRegistry = wfs3ExtensionRegistry;
    }

    public FeatureQuery requestToFeatureQuery(OgcApiDataset api, String collectionId, Map<String, String> parameters, String featureId) {

        for (OgcApiParameterExtension parameterExtension : wfs3ExtensionRegistry.getExtensionsForType(OgcApiParameterExtension.class)) {
            parameters = parameterExtension.transformParameters(api.getData()
                                                                       .getFeatureTypes()
                                                                       .get(collectionId), parameters,api.getData());
        }

        final String filter = String.format("IN ('%s')", featureId);

        final ImmutableFeatureQuery.Builder queryBuilder = ImmutableFeatureQuery.builder()
                                                                                .type(collectionId)
                                                                                .filter(filter);

        for (OgcApiParameterExtension parameterExtension : wfs3ExtensionRegistry.getExtensionsForType(OgcApiParameterExtension.class)) {
            parameterExtension.transformQuery(api.getData()
                                                     .getFeatureTypes()
                                                     .get(collectionId), queryBuilder, parameters,api.getData());
        }

        return queryBuilder.build();
    }

    public FeatureQuery requestToFeatureQuery(OgcApiDataset api, String collectionId, int defaultPageSize, int maxPageSize, Map<String, String> parameters) {

        final Map<String, String> filterableFields = api.getData()
                                                            .getFilterableFieldsForFeatureType(collectionId);

        for (OgcApiParameterExtension parameterExtension : wfs3ExtensionRegistry.getExtensionsForType(OgcApiParameterExtension.class)) {
            parameters = parameterExtension.transformParameters(api.getData()
                                                                       .getFeatureTypes()
                                                                       .get(collectionId), parameters,api.getData());
        }


        final Map<String, String> filters = getFiltersFromQuery(parameters, filterableFields);

        boolean hitsOnly = parameters.containsKey("resultType") && parameters.get("resultType")
                                                                             .toLowerCase()
                                                                             .equals("hits");

        /**
         * NOTE: OGC API and ldproxy do not use the HTTP "Range" header for limit/offset for the following reasons:
         * - We need to support some non-header mechanism anyhow to be able to mint URIs (links) to pages / partial responses.
         * - A request without a range header cannot return 206, so there is no way that a server could have a default limit.
         *   I.e. any request to a collection without a range header would have to return all features and it is important to
         *   enable servers to have a default page limit.
         * - There is no real need for multipart responses, but servers would have to support requests that lead to
         *   206 multipart responses.
         * - Developers do not seem to expect such an approach and since it uses a custom range unit anyhow (i.e. not bytes),
         *   it is unclear how much value it brings. Probably consistent with this: I have not seen much of range headers
         *   in Web APIs for paging.
         */
        final int limit = parseLimit(defaultPageSize, maxPageSize, parameters.get("limit"));
        final int offset = parseOffset(parameters.get("offset"));

        final ImmutableFeatureQuery.Builder queryBuilder = ImmutableFeatureQuery.builder()
                                                                                .type(collectionId)
                                                                                .limit(limit)
                                                                                .offset(offset)
                                                                                .hitsOnly(hitsOnly);

        for (OgcApiParameterExtension parameterExtension : wfs3ExtensionRegistry.getExtensionsForType(OgcApiParameterExtension.class)) {
            parameterExtension.transformQuery(api.getData()
                                                     .getFeatureTypes()
                                                     .get(collectionId), queryBuilder, parameters,api.getData());
        }


        if (!filters.isEmpty()) {
            String cql = getCQLFromFilters(api, filters, filterableFields);
            LOGGER.debug("CQL {}", cql);
            queryBuilder.filter(cql);
        }

        return queryBuilder.build();

    }

    private Map<String, String> getFiltersFromQuery(Map<String, String> query, Map<String, String> filterableFields) {

        Map<String, String> filters = new LinkedHashMap<>();

        for (String filterKey : query.keySet()) {
            if (filterableFields.containsKey(filterKey.toLowerCase())) {
                String filterValue = query.get(filterKey);
                filters.put(filterKey.toLowerCase(), filterValue);
            }
        }

        return filters;
    }

    private String getCQLFromFilters(OgcApiDataset service, Map<String, String> filters, Map<String, String> filterableFields) {
        return filters.entrySet()
                      .stream()
                      .map(f -> {
                          if (f.getKey()
                               .equals("bbox")) {
                              return bboxToCql(service, filterableFields.get(f.getKey()), f.getValue());
                          }
                          if (f.getKey()
                               .equals("datetime")) {
                              return timeToCql(filterableFields.get(f.getKey()), f.getValue());
                          }
                          if (f.getValue()
                               .contains("*")) {
                              return String.format("%s LIKE '%s'", filterableFields.get(f.getKey()), f.getValue());
                          }

                          return String.format("%s = '%s'", filterableFields.get(f.getKey()), f.getValue());
                      })
                      .filter(pred -> pred!=null)
                      .collect(Collectors.joining(" AND "));
    }

    private String bboxToCql(OgcApiDataset service, String geometryField, String bboxValue) {
        String[] bboxArray = bboxValue.split(",");

        String bboxCrs = bboxArray.length > 4 ? bboxArray[4] : null;
        EpsgCrs crs = Optional.ofNullable(bboxCrs)
                              .map(EpsgCrs::new)
                              .orElse(OgcApiDatasetData.DEFAULT_CRS);

        BoundingBox bbox = new BoundingBox(Double.valueOf(bboxArray[0]), Double.valueOf(bboxArray[1]), Double.valueOf(bboxArray[2]), Double.valueOf(bboxArray[3]), crs);
        BoundingBox transformedBbox = null;
        try {
            transformedBbox = service.transformBoundingBox(bbox);
        } catch (CrsTransformationException e) {
            LOGGER.error("Error transforming bbox");
            transformedBbox = bbox;
        }

        return String.format(Locale.US, "BBOX(%s, %.3f, %.3f, %.3f, %.3f, '%s')", geometryField, transformedBbox.getXmin(), transformedBbox.getYmin(), transformedBbox.getXmax(), transformedBbox.getYmax(), transformedBbox.getEpsgCrs()
                                                                                                                                                                          .getAsSimple());
    }

    private String timeToCql(String timeField, String timeValue) {
        // valid values: timestamp or time interval;
        // this includes open intervals indicated by ".." (see ISO 8601-2);
        // accept also unknown ("") with the same interpretation
        try {
            if (timeValue.matches("^"+TIMESTAMP_REGEX+"\\/"+TIMESTAMP_REGEX+"$")) {
                // the following parse accepts fully specified time intervals
                Interval fromIso8601Period = Interval.parse(timeValue);
                return String.format("%s DURING %s", timeField, fromIso8601Period);
            } else if (timeValue.matches("^"+TIMESTAMP_REGEX+"$")) {
                // a time instant
                Instant fromIso8601 = Instant.parse(timeValue);
                return String.format("%s TEQUALS %s", timeField, fromIso8601);
            } else if (timeValue.matches("^"+OPEN_REGEX+"\\/"+OPEN_REGEX+"$")) {
                // open start and end, nothing to do, all values match
                return null;
            } else if (timeValue.matches("^"+TIMESTAMP_REGEX+"\\/"+OPEN_REGEX+"$")) {
                // open end
                Instant fromIso8601 = Instant.parse(timeValue.substring(0,timeValue.indexOf("/")));
                return String.format("%s AFTER %s", timeField, fromIso8601.minusSeconds(1));
            } else if (timeValue.matches("^"+OPEN_REGEX+"\\/"+TIMESTAMP_REGEX+"$")) {
                // open start
                Instant fromIso8601 = Instant.parse(timeValue.substring(timeValue.indexOf("/")+1));
                return String.format("%s BEFORE %s", timeField, fromIso8601.plusSeconds(1));
            } else {
                LOGGER.error("TIME PARSER ERROR " + timeValue);
                throw new BadRequestException("Invalid value for query parameter '"+timeField+"'. Found: "+timeValue);
            }
        } catch (DateTimeParseException e) {
            LOGGER.debug("TIME PARSER ERROR", e);
            throw new BadRequestException("Invalid value for query parameter '"+timeField+"'. Found: "+timeValue);
        }
    }

    private int parseLimit(int defaultPageSize, int maxPageSize, String paramLimit) {
        int limit = defaultPageSize;
        if (paramLimit != null && !paramLimit.isEmpty()) {
            try {
                limit = Integer.parseInt(paramLimit);
            } catch (NumberFormatException ex) {
                throw new BadRequestException("Invalid value for query parameter 'limit'. The value must be a positive integer. Found: "+paramLimit);
            }
            if (limit < 1) {
                throw new BadRequestException("Invalid value for query parameter 'limit'. The value must be a positive integer. Found: "+paramLimit);
            }
            if (limit > maxPageSize) {
                throw new BadRequestException("Invalid value for query parameter 'limit'. The value must be less than " + maxPageSize + ". Found: "+paramLimit);
            }
        }
        return limit;
    }

    private int parseOffset(String paramOffset) {
        int offset = 0;
        if (paramOffset != null && !paramOffset.isEmpty()) {
            try {
                offset = Integer.parseInt(paramOffset);
            } catch (NumberFormatException ex) {
                throw new BadRequestException("Invalid value for query parameter 'offset'. The value must be a non-negative integer. Found: "+paramOffset);
            }
            if (offset < 0) {
                throw new BadRequestException("Invalid value for query parameter 'offset'. The value must be a non-negative integer. Found: "+paramOffset);
            }
        }
        return offset;
    }
}