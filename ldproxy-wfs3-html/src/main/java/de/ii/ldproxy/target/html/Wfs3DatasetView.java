/**
 * Copyright 2018 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.target.html;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import de.ii.ldproxy.wfs3.api.Wfs3Collection;
import de.ii.ldproxy.wfs3.api.Wfs3Collections;
import de.ii.ldproxy.wfs3.api.Wfs3Extent;
import de.ii.ldproxy.wfs3.api.Wfs3Link;
import de.ii.ldproxy.wfs3.api.Wfs3ServiceData;
import io.dropwizard.views.View;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author zahnen
 */
public class Wfs3DatasetView extends View {
    private final Wfs3Collections wfs3Dataset;
    private final List<NavigationDTO> breadCrumbs;
    private final String urlPrefix;
    public final HtmlConfig htmlConfig;
    private List<Style> styles;
    public String title;
    public String description;
    public String dataSourceUrl;
    public String keywords;

    public Wfs3DatasetView(Wfs3ServiceData serviceData, Wfs3Collections wfs3Dataset, final List<NavigationDTO> breadCrumbs, String urlPrefix, HtmlConfig htmlConfig, List<String> stylesList) {
        super("service.mustache", Charsets.UTF_8);
        this.wfs3Dataset = wfs3Dataset;
        this.breadCrumbs = breadCrumbs;
        this.urlPrefix = urlPrefix;
        this.htmlConfig = htmlConfig;


        this.title = serviceData.getLabel();
        this.description = serviceData.getDescription().orElse("");

        if (serviceData.getMetadata().isPresent() && !serviceData.getMetadata().get().getKeywords().isEmpty()) {
            this.keywords = Joiner.on(',').skipNulls().join(serviceData.getMetadata().get().getKeywords());
        }

        if (serviceData.getFeatureProvider().getDataSourceUrl().isPresent()) {
            this.dataSourceUrl = serviceData.getFeatureProvider().getDataSourceUrl().get();
        }
        styles=new ArrayList<>();

        for(String style : stylesList){
            String styleId=style.split("\\.")[0];
            styles.add(new Style(styleId,/*urlPrefix*/breadCrumbs.get(0).url + "/" + serviceData.getId()+"/maps/" + styleId));
        }



    }

    public Wfs3Collections getWfs3Dataset() {
        return wfs3Dataset;
    }

    public List<NavigationDTO> getBreadCrumbs() {
        return breadCrumbs;
    }

    public String getApiUrl() {
        return wfs3Dataset.getLinks()
                          .stream()
                          .filter(wfs3Link -> Objects.equals(wfs3Link.getRel(), "service") && Objects.equals(wfs3Link.getType(), Wfs3OutputFormatHtml.MEDIA_TYPE.main()
                                                                                                                                                                .toString()))
                          .map(Wfs3Link::getHref)
                          .findFirst()
                          .orElse("");
    }
    public List<Style> getStyles() {
        return styles;
    }

    public List<FeatureType> getFeatureTypes() {
        return wfs3Dataset.getCollections()
                          .stream()
                          .map(FeatureType::new)
                          .collect(Collectors.toList());
    }

    public List<NavigationDTO> getFormats() {
        return wfs3Dataset.getLinks()
                          .stream()
                          .filter(wfs3Link -> Objects.equals(wfs3Link.getRel(), "alternate"))
                          .sorted(Comparator.comparing(link -> link.getTypeLabel().toUpperCase()))
                          .map(wfs3Link -> new NavigationDTO(wfs3Link.getTypeLabel(), wfs3Link.getHref()))
                          .collect(Collectors.toList());
    }


    static class FeatureType extends Wfs3Collection {
        private final Wfs3Collection collection;
        public FeatureType(Wfs3Collection collection) {
            this.collection = collection;
        }

        public String getUrl() {
            return this.getLinks()
                       .stream()
                       .filter(wfs3Link -> Objects.equals(wfs3Link.getRel(), "item") && Objects.equals(wfs3Link.getType(), Wfs3OutputFormatHtml.MEDIA_TYPE.main()
                                                                                                                                                          .toString()))
                       .map(Wfs3Link::getHref)
                       .findFirst()
                       .orElse("");
        }

        @Override
        public String getName() {
            return collection.getName();
        }

        @Override
        public String getTitle() {
            return collection.getTitle();
        }

        @Override
        public Optional<String> getDescription() {
            return collection.getDescription();
        }

        @Override
        public Wfs3Extent getExtent() {
            return collection.getExtent();
        }

        @Override
        public List<Wfs3Link> getLinks() {
            return collection.getLinks();
        }

        @Override
        public List<String> getCrs() {
            return collection.getCrs();
        }

        @Override
        public String getPrefixedName() {
            return collection.getPrefixedName();
        }

        @Override
        public Map<String, Object> getExtensions() {
            return collection.getExtensions();
        }
    }

    static class Style{
        private final String styleId;
        private final String styleUrl;

        public Style(String styleId, String styleUrl){
            this.styleId = styleId;
            this.styleUrl = styleUrl;
        }

        public String getStyleId(){
            return styleId;
        }

        public String getStyleUrl(){
            return styleUrl;
        }
    }

    public String getUrlPrefix() {
        return urlPrefix;
    }
}
