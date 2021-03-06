package de.ii.ldproxy.ogcapi.domain;

import java.io.IOException;
import java.util.Optional;

public interface SchemaValidator {
    Optional<String> validate(String schemaContent, String jsonContent) throws IOException;
}
