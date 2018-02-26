/**
 * Copyright 2018 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.codelists;

import de.ii.xsf.configstore.api.rest.ResourceStore;

import java.io.IOException;

/**
 * @author zahnen
 */
public interface CodelistStore extends ResourceStore<Codelist> {

    enum IMPORT_TYPE {
        GML_DICTIONARY
    }

    Codelist addCodelist(String id) throws IOException;
    Codelist addCodelist(String sourceUrl, IMPORT_TYPE sourceType) throws IOException;
}
