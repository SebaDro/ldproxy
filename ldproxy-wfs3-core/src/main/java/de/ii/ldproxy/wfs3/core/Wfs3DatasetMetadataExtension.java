package de.ii.ldproxy.wfs3.core;

import de.ii.ldproxy.wfs3.api.*;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface Wfs3DatasetMetadataExtension extends Wfs3Extension {
    ImmutableWfs3Collections.Builder process(ImmutableWfs3Collections.Builder collections, URICustomizer uriCustomizer, Collection<FeatureTypeConfigurationWfs3> featureTypeConfigurationsWfs3);

}
