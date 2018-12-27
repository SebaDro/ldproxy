package de.ii.ldproxy.wfs3.styles.representation;

import com.google.common.collect.ImmutableList;
import de.ii.ldproxy.wfs3.Wfs3Service;
import de.ii.ldproxy.wfs3.api.Wfs3EndpointExtension;
import de.ii.ldproxy.wfs3.api.Wfs3RequestContext;
import de.ii.ldproxy.wfs3.styles.Wfs3EndpointStyles;
import de.ii.xsf.configstore.api.KeyValueStore;
import de.ii.xsf.core.server.CoreServerConfig;
import de.ii.xtraplatform.service.api.Service;
import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.apache.felix.ipojo.annotations.Requires;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@Provides
@Instantiate
public class Wfs3EndpointStylesRepresentation implements Wfs3EndpointExtension {
    @Requires
    private KeyValueStore keyValueStore;
    @Requires
    private CoreServerConfig coreServerConfig;

    @Override
    public String getPath() {
        return "maps";
    }

    @Override
    public String getSubPathRegex() {
        return "^\\/(?:\\w+)";
    }

    @Override
    public List<String> getMethods() {
        return ImmutableList.of("GET");
    }

    @Override
    public boolean matches(String firstPathSegment, String method, String subPath) {
        return Wfs3EndpointExtension.super.matches(firstPathSegment, method, subPath);
    }


    @Path("/{styleId}")
    @GET
    @Produces(MediaType.TEXT_HTML)
    public Response getStyles(@Context Service service, @PathParam("styleId") String styleId,@Context Wfs3RequestContext wfs3Request) {
        KeyValueStore stylesStore = keyValueStore.getChildStore("styles").getChildStore(service.getId());
        List<String> styles = stylesStore.getKeys();
        Wfs3EndpointStyles.getStyleToDisplay(stylesStore,styles,styleId);

        String prefix = coreServerConfig.getExternalUrl();

        String styleUri = prefix + "/" + service.getData().getId() + "/" + "styles" + "/" + styleId ;
        String tileUri = prefix + "/" + service.getData().getId() + "/tiles/default/{z}/{y}/{x}?f\\=mvt";

        StyleView styleView = new StyleView(styleUri,tileUri,service.getData().getId());

        return Response.ok().entity(styleView).build();
    }
}