package de.ii.ldproxy.wfs3.crs;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import de.ii.ldproxy.ogcapi.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ldproxy.ogcapi.domain.OgcApiApiDataV2;
import de.ii.ldproxy.ogcapi.domain.OgcApiContext;
import de.ii.ldproxy.ogcapi.domain.OgcApiQueryParameter;
import de.ii.xtraplatform.crs.domain.EpsgCrs;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.apache.felix.ipojo.annotations.Requires;

import javax.ws.rs.BadRequestException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Provides
@Instantiate
public class QueryParameterBboxCrsFeatures implements OgcApiQueryParameter {

    public static final String BBOX = "bbox";
    public static final String BBOX_CRS = "bbox-crs";
    public static final String CRS84 = "http://www.opengis.net/def/crs/OGC/1.3/CRS84";
    public static final String CRS84H = "http://www.opengis.net/def/crs/OGC/0/CRS84h";

    private final CrsSupport crsSupport;

    public QueryParameterBboxCrsFeatures(@Requires CrsSupport crsSupport) {
        this.crsSupport = crsSupport;
    }

    @Override
    public String getId(String collectionId) {
        return BBOX_CRS+"Features_"+collectionId;
    }

    @Override
    public String getName() {
        return BBOX_CRS;
    }

    @Override
    public String getDescription() {
        return "The coordinate reference system of the `bbox` parameter. Default is WGS84 longitude/latitude (http://www.opengis.net/def/crs/OGC/1.3/CRS84).";
    }

    @Override
    public boolean isApplicable(OgcApiApiDataV2 apiData, String definitionPath, OgcApiContext.HttpMethods method) {
        return isEnabledForApi(apiData) &&
                method==OgcApiContext.HttpMethods.GET &&
                definitionPath.equals("/collections/{collectionId}/items");
    }

    private Map<String,Schema> schemaMap = new ConcurrentHashMap<>();

    @Override
    public Schema getSchema(OgcApiApiDataV2 apiData, String collectionId) {
        String key = apiData.getId()+"__"+collectionId;
        if (!schemaMap.containsKey(key)) {
            List<String> crsList = crsSupport.getSupportedCrsList(apiData, apiData.getCollections().get(collectionId))
                                             .stream()
                                             .map(EpsgCrs::toUriString)
                                             .collect(ImmutableList.toImmutableList());
            schemaMap.put(key, new StringSchema()._enum(crsList)._default(CRS84));
        }
        return schemaMap.get(key);
    }

    @Override
    public boolean isEnabledForApi(OgcApiApiDataV2 apiData) {
        return isExtensionEnabled(apiData, CrsConfiguration.class);
    }

    @Override
    public boolean isEnabledForApi(OgcApiApiDataV2 apiData, String collectionId) {
        return isExtensionEnabled(apiData.getCollections().get(collectionId), CrsConfiguration.class);
    }

    @Override
    public Map<String, String> transformParameters(FeatureTypeConfigurationOgcApi featureTypeConfiguration,
                                                   Map<String, String> parameters, OgcApiApiDataV2 datasetData) {

        if (!isEnabledForApi(datasetData)) {
            return parameters;
        }

        if (parameters.containsKey(BBOX) && parameters.containsKey(BBOX_CRS)) {
            EpsgCrs bboxCrs;
            try {
                bboxCrs = EpsgCrs.fromString(parameters.get(BBOX_CRS));
            } catch (Throwable e) {
                throw new BadRequestException(String.format("The parameter '%s' is invalid: %s", BBOX_CRS, e.getMessage()));
            }
            if (!crsSupport.isSupported(datasetData, featureTypeConfiguration, bboxCrs)) {
                throw new BadRequestException(String.format("The parameter '%s' is invalid: the crs '%s' is not supported", BBOX_CRS, bboxCrs.toUriString()));
            }

            Map<String, String> newParameters = new HashMap<>(parameters);
            newParameters.put(BBOX, String.format("%s,%s", parameters.get(BBOX), bboxCrs.toUriString()));
            return ImmutableMap.copyOf(newParameters);
        }

        return parameters;
    }
}
