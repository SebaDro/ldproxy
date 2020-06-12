package de.ii.ldproxy.ogcapi.observation_processing.parameters;

import de.ii.ldproxy.ogcapi.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ldproxy.ogcapi.domain.OgcApiContext;
import de.ii.ldproxy.ogcapi.domain.OgcApiQueryParameter;
import de.ii.ldproxy.ogcapi.domain.OgcApiApiDataV2;
import de.ii.ldproxy.ogcapi.feature_processing.api.FeatureProcessInfo;
import de.ii.ldproxy.ogcapi.observation_processing.api.ObservationProcess;
import de.ii.ldproxy.ogcapi.observation_processing.data.GeometryPoint;
import de.ii.ldproxy.ogcapi.observation_processing.application.ObservationProcessingConfiguration;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.apache.felix.ipojo.annotations.Requires;

import javax.ws.rs.BadRequestException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@Provides
@Instantiate
public class QueryParameterCoordPosition extends GeometryHelper implements OgcApiQueryParameter {

    static final double BUFFER = 75.0; // buffer in km
    static final double R = 6378.1f; // earth radius in km

    private final Schema baseSchema;
    final FeatureProcessInfo featureProcessInfo;

    public QueryParameterCoordPosition(@Requires FeatureProcessInfo featureProcessInfo) {
        this.featureProcessInfo = featureProcessInfo;
        baseSchema = new StringSchema().pattern("^\\s*POINT\\s*" + POINT_REGEX + "\\s*$");
    }

    @Override
    public String getId() { return "coordPosition"; }

    @Override
    public boolean isApplicable(OgcApiApiDataV2 apiData, String definitionPath, OgcApiContext.HttpMethods method) {
        return isEnabledForApi(apiData) &&
                method==OgcApiContext.HttpMethods.GET &&
                featureProcessInfo.matches(apiData, ObservationProcess.class, definitionPath,"position");
    }

    @Override
    public String getName() {
        return "coord";
    }

    @Override
    public String getDescription() {
        return "A Well Known Text representation of a POINT or POINTZ geometry as defined in Simple Feature Access - Part 1: Common Architecture.";
    }

    @Override
    public Schema getSchema(OgcApiApiDataV2 apiData) {
        Optional<String> defValue = getDefault(apiData, Optional.empty());
        if (defValue.isPresent()) {
            Schema schema = baseSchema;
            schema.setDefault(defValue.get());
            return schema;
        }
        return baseSchema;
    }

    @Override
    public Schema getSchema(OgcApiApiDataV2 apiData, String collectionId) {
        Optional<String> defValue = getDefault(apiData, Optional.of(collectionId));
        if (defValue.isPresent()) {
            Schema schema = baseSchema;
            schema.setDefault(defValue.get());
            return schema;
        }
        return baseSchema;
    }

    @Override
    public boolean getRequired(OgcApiApiDataV2 apiData) {
        return !getDefault(apiData, Optional.empty()).isPresent();
    }

    @Override
    public boolean getRequired(OgcApiApiDataV2 apiData, String collectionId) {
        return !getDefault(apiData, Optional.of(collectionId)).isPresent();
    }

    @Override
    public boolean isEnabledForApi(OgcApiApiDataV2 apiData) {
        return isExtensionEnabled(apiData, ObservationProcessingConfiguration.class);
    }

    @Override
    public Map<String, String> transformParameters(FeatureTypeConfigurationOgcApi featureType,
                                                   Map<String, String> parameters,
                                                   OgcApiApiDataV2 apiData) {
        String coord = parameters.get(getName());
        if (coord==null) {
            coord = getDefault(apiData, Optional.of(featureType.getId())).orElse(null);
            if (coord == null)
                throw new BadRequestException(String.format("The required parameter '%s' has no value.", getName()));
        }

        // TODO support other CRS
        // add bbox and remove coord
        List<Float> point = extractPosition(coord);
        double lonBuffer = BUFFER / (R * Math.cos(point.get(1) / 180.0 * Math.PI) * Math.PI / 180.0);
        double latBuffer = BUFFER / (R * Math.PI / 180.0);
        String bbox = (point.get(0) - lonBuffer) + "," + (point.get(1) - latBuffer) + "," +
                      (point.get(0) + lonBuffer) + "," + (point.get(1) + latBuffer);
        parameters.put("bbox",bbox);
        parameters.remove(getName());
        return parameters;
    }

    @Override
    public Map<String, Object> transformContext(FeatureTypeConfigurationOgcApi featureType,
                                                Map<String, Object> context,
                                                Map<String, String> parameters,
                                                OgcApiApiDataV2 apiData) {
        String coord = parameters.get(getName());
        if (coord==null) {
            coord = getDefault(apiData, Optional.of(featureType.getId())).orElse(null);
            if (coord == null)
                throw new BadRequestException(String.format("The required parameter '%s' has no value.", getName()));
        }

        context.put("point",new GeometryPoint(extractPosition(coord)));
        return context;
    }

    private Optional<String> getDefault(OgcApiApiDataV2 apiData, Optional<String> collectionId) {
        FeatureTypeConfigurationOgcApi featureType = collectionId.isPresent() ? apiData.getCollections().get(collectionId.get()) : null;
        Optional<ObservationProcessingConfiguration> config = featureType!=null ?
                this.getExtensionConfiguration(apiData, featureType, ObservationProcessingConfiguration.class) :
                this.getExtensionConfiguration(apiData, ObservationProcessingConfiguration.class);
        if (config.isPresent()) {
            return config.get().getDefaultCoordPosition();
        }
        return Optional.empty();
    }
}