/*
  Copyright (c) 2022, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.bddtests.directory;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DirectoryElement {

    @JsonProperty("elementUuid")
    private String elementUuid;
    @JsonProperty("elementName")
    private String elementName;
    @JsonProperty("type")
    private String type;

    public String getElementUuid() {
        return elementUuid;
    }

    public String getElementName() {
        return elementName;
    }

    public String getType() {
        return type;
    }

    @Override
    public String toString() {
        return "Element " + type + ": [" + elementName + "] (uuid:" + elementUuid + ")";
    }
}

