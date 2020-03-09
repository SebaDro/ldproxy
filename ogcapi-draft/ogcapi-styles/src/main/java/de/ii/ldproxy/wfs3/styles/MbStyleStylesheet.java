/**
 * Copyright 2020 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.wfs3.styles;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.collect.ImmutableList;
import org.immutables.value.Value;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Value.Immutable
@Value.Style(jdkOnly = true, deepImmutablesDetection = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonDeserialize(as = ImmutableMbStyleStylesheet.class)
public abstract class MbStyleStylesheet {

    public enum Anchor { map, viewport }
    public enum Scheme { xyz, tms }
    public enum Encoding { terrarium, mapbox }
    public enum LayerType { background, fill, line, symbol, raster, circle, fillExtrusion, heatmap, hillshade }
    public enum Visibility { visible, none }

    public final String getVersion() { return "8"; }
    public abstract Optional<String> getName();
    public abstract Optional<Object> getMetadata();
    public abstract Optional<List<Double>> getCenter();
    public abstract Optional<Double> getZoom();
    public Optional<Double> getBearing() { return Optional.of(0.0); }
    public Optional<Double> getPitch() { return Optional.of(0.0); }
    public abstract Optional<Light> getLight();
    public abstract Map<String, Object> getSources(); // TODO Source
    public abstract Optional<String> getSprite();
    public abstract Optional<String> getGlyphs();
    public abstract Optional<Transition> getTransition();
    public abstract List<Object> getLayers(); // TODO Layer

    public abstract class Light {
        public Anchor getAnchor() { return Anchor.viewport; }
        public Optional<List<Double>> getPosition() { return Optional.of(ImmutableList.of(1.15,210.0,30.0)); }
        public Optional<String> getColor() { return Optional.of("#ffffff"); }
        public Optional<String> setColor(Optional<String> color) { return color; }
        public Optional<Double> getIntensity() { return Optional.of(0.5); }
    }

    public abstract class Source {
    }

    public abstract class VectorSource extends Source {
        public final String getType() { return "vector"; }
        public abstract Optional<String> getUrl();
        public abstract Optional<List<String>> getTiles();
        public Optional<List<Double>> getBounds() { return Optional.of(ImmutableList.of(-180.0,-85.051129,180.0,85.051129)); }
        public Scheme getScheme() { return Scheme.xyz; }
        public Optional<Integer> getMinzoon() { return Optional.of(0); }
        public Optional<Integer> getMaxzoon() { return Optional.of(22); }
        public abstract Optional<String> getAttribution();
    }

    public abstract class RasterSource extends Source {
        public final String getType() { return "raster"; }
        public abstract Optional<String> getUrl();
        public abstract Optional<List<String>> getTiles();
        public Optional<List<Double>> getBounds() { return Optional.of(ImmutableList.of(-180.0,-85.051129,180.0,85.051129)); }
        public Scheme getScheme() { return Scheme.xyz; }
        public Optional<Integer> getTilesize() { return Optional.of(512); }
        public Optional<Integer> getMinzoon() { return Optional.of(0); }
        public Optional<Integer> getMaxzoon() { return Optional.of(22); }
        public abstract Optional<String> getAttribution();
    }

    public abstract class RasterDemSource extends Source {
        public final String getType() { return "raster-dem"; }
        public abstract Optional<String> getUrl();
        public abstract Optional<List<String>> getTiles();
        public Optional<List<Double>> getBounds() { return Optional.of(ImmutableList.of(-180.0,-85.051129,180.0,85.051129)); }
        public Optional<Integer> getTilesize() { return Optional.of(512); }
        public Optional<Integer> getMinzoon() { return Optional.of(0); }
        public Optional<Integer> getMaxzoon() { return Optional.of(22); }
        public abstract Optional<String> getAttribution();
        public Encoding getEncoding() { return Encoding.mapbox; }
    }

    public abstract class GeojsonSource extends Source {
        public final String getType() { return "geojson"; }
        public abstract Optional<Object> getData();
        public Integer getMaxzoon() { return 18; }
        public abstract Optional<String> getAttribution();
        public Optional<Integer> getBuffer() { return Optional.of(128); }
        public Optional<Double> getTolerance() { return Optional.of(0.375); }
        public Optional<Boolean> getCluster() { return Optional.of(false); }
        public Optional<Integer> getClusterRadius() { return Optional.of(50); }
        public Optional<Integer> getClusterMaxZoom() { return Optional.of(Integer.valueOf(getMaxzoon()-1)); }
        public abstract Optional<Object> getClusterProperties();
        public Optional<Boolean> getLineMetrics() { return Optional.of(false); }
        public Optional<Boolean> getGenerateId() { return Optional.of(false); }
    }

    public abstract class ImageSource extends Source {
        public final String getType() { return "image"; }
        public abstract String getUrl();
        public abstract List<List<Double>> getCoordinates();
    }

    public abstract class VideoSource extends Source {
        public final String getType() { return "video"; }
        public abstract String getUrl();
        public abstract List<List<Double>> getCoordinates();
    }

    public abstract class Transition {
        public Optional<Integer> getDuration() { return Optional.of(300); }
        public Optional<Integer> getDelay() { return Optional.of(0); }
    }

    public abstract class Layer {
        public abstract String getId();
        public abstract LayerType getType();
        public abstract Optional<Object> getMetadata();
        public abstract Optional<String> getSource();
        @JsonProperty("source-layer")
        public abstract Optional<String> getSourceLayer();
        public abstract Optional<Integer> getMinzoon();
        public abstract Optional<Integer> getMaxzoon();
        public abstract Optional<Object> getFilter(); // TODO proper handling of expressions
        public abstract Optional<Map<String, Object>> getLayout(); // TODO proper handling of layout properties
        public abstract Optional<Map<String, Object>> getPaint(); // TODO proper handling of paint properties
    }
}
