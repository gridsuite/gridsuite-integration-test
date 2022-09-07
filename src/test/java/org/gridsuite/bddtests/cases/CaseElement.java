/*
  Copyright (c) 2022, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.bddtests.cases;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CaseElement {

    @JsonProperty("uuid")
    private String caseUuid;
    @JsonProperty("name")
    private String caseName;

    @Override
    public String toString() {
        return "Case: [" + caseName + "] (uuid:" + caseUuid + ")";
    }

    public String getCaseUuid() {
        return caseUuid;
    }

    public String getCaseName() {
        return caseName;
    }
}
