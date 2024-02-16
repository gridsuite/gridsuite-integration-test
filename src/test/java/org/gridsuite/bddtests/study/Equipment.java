/*
  Copyright (c) 2022, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.bddtests.study;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Equipment {

    @JsonProperty("uniqueId")
    private String uniqueId;

    @JsonProperty("id")
    private String id;

    @JsonProperty("networkUuid")
    private String networkUuid;

    @JsonProperty("variantId")
    private String variantId;

    @JsonProperty("name")
    private String name;

    public String getUniqueId() {
        return uniqueId;
    }

    public String getId() {
        return id;
    }

    public String getNetworkUuid() {
        return networkUuid;
    }

    public String getVariantId() {
        return variantId;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    @JsonProperty("type")
    private String type;

    // could add voltageLevels

    @Override
    public String toString() {
        return "Equipment " + type + " : [" + id + "] (variant:" + variantId + ") networkUuid = "  + networkUuid;
    }

    public String getIdentifier(String fieldSelector) {
        if (fieldSelector.equalsIgnoreCase("NAME")) {
            return name;
        } else if (fieldSelector.equalsIgnoreCase("ID")) {
            return id;
        } else {
            return null;
        }
    }
}
