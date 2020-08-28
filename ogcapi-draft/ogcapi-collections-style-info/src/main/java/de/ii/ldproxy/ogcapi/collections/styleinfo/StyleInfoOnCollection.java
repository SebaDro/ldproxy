/**
 * Copyright 2020 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.ogcapi.collections.styleinfo;


import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import de.ii.ldproxy.ogcapi.domain.I18n;
import de.ii.ldproxy.ogcapi.collections.domain.CollectionExtension;
import de.ii.ldproxy.ogcapi.collections.domain.ImmutableOgcApiCollection;
import de.ii.ldproxy.ogcapi.domain.*;
import de.ii.ldproxy.ogcapi.styles.domain.StylesConfiguration;
import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.apache.felix.ipojo.annotations.Requires;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

import static de.ii.xtraplatform.runtime.domain.Constants.DATA_DIR_KEY;

/**
 * add style links to the collection information
 */
@Component
@Provides
@Instantiate
public class StyleInfoOnCollection implements CollectionExtension {

    @Requires
    I18n i18n;

    private static final Logger LOGGER = LoggerFactory.getLogger(StyleInfoOnCollection.class);

    private final File styleInfosStore;

    public StyleInfoOnCollection(@org.apache.felix.ipojo.annotations.Context BundleContext bundleContext) {
        this.styleInfosStore = new File(bundleContext.getProperty(DATA_DIR_KEY) + File.separator + "styleInfos");
        if (!styleInfosStore.exists()) {
            styleInfosStore.mkdirs();
        }
    }

    @Override
    public boolean isEnabledForApi(OgcApiDataV2 apiData) {
        Optional<StylesConfiguration> stylesExtension = apiData.getExtension(StylesConfiguration.class);

        if (stylesExtension.isPresent() && stylesExtension.get()
                                                          .getStyleInfosOnCollection()) {
            return true;
        }
        return false;
    }

    @Override
    public boolean isEnabledForApi(OgcApiDataV2 apiData, String collectionId) {
        Optional<StylesConfiguration> stylesExtension = apiData.isCollectionEnabled(collectionId) ?
                apiData.getCollections()
                       .get(collectionId)
                       .getExtension(StylesConfiguration.class) :
                Optional.empty();

        if (stylesExtension.isPresent() && stylesExtension.get()
                                                          .getStyleInfosOnCollection()) {
            return true;
        }
        return false;
    }

    @Override
    public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
        return StylesConfiguration.class;
    }

    @Override
    public ImmutableOgcApiCollection.Builder process(ImmutableOgcApiCollection.Builder collection,
                                                     FeatureTypeConfigurationOgcApi featureTypeConfiguration,
                                                     OgcApiDataV2 apiData,
                                                     URICustomizer uriCustomizer,
                                                     boolean isNested,
                                                     ApiMediaType mediaType,
                                                     List<ApiMediaType> alternateMediaTypes,
                                                     Optional<Locale> language) {
        if (!isNested && isEnabledForApi(apiData, featureTypeConfiguration.getId())) {
            final String collectionId = featureTypeConfiguration.getId();

            final String apiId = apiData.getId();
            File apiDir = new File(styleInfosStore + File.separator + apiId);
            if (!apiDir.exists()) {
                apiDir.mkdirs();
            }

            File collectionFile = new File(styleInfosStore + File.separator + apiId + File.separator + featureTypeConfiguration.getId());
            if (collectionFile.exists()) {
                Optional<StyleInfo> styleInfos = getStyleInfos(collectionFile);
                if (styleInfos.isPresent() && styleInfos.get().getStyles().isPresent()) {
                    collection.putExtensions("styles",
                            styleInfos.get()
                                    .getStyles()
                                    .get()
                                    .stream()
                                    .map(styleInfo -> ImmutableStyleEntry.builder()
                                            .id(styleInfo.getId())
                                            .title(styleInfo.getTitle())
                                            .links(styleInfo.getLinks())
                                            .build())
                                    .collect(Collectors.toList()));
                }
                if (styleInfos.isPresent() && styleInfos.get().getDefaultStyle().isPresent()) {
                    collection.putExtensions("defaultStyle",styleInfos.get().getDefaultStyle());
                }
            }
        }

        return collection;
    }

    private Optional<StyleInfo> getStyleInfos(File styleInfosFile) {

        try {
            final byte[] content = java.nio.file.Files.readAllBytes(styleInfosFile.toPath());

            // prepare Jackson mapper for deserialization
            final ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new Jdk8Module());
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            try {
                // parse input
                StyleInfo styleInfo = mapper.readValue(content, StyleInfo.class);

                return Optional.of(styleInfo);
            } catch (IOException e) {
                LOGGER.error("File in styleInfo store is invalid and is skipped: "+styleInfosFile.getAbsolutePath());
            }
        } catch (IOException e) {
            LOGGER.error("Style info could not be read: "+styleInfosFile.getAbsolutePath());
        }
        return Optional.empty();
    };
}
