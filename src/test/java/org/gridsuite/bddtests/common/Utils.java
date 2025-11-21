/*
  Copyright (c) 2022, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.bddtests.common;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

import static org.junit.Assert.*;

public final class Utils {
    private Utils() {
        throw new java.lang.UnsupportedOperationException("BddUtils Utility class and cannot be instantiated");
    }

    public static Path getBddFile(String subDir, String fileName) {
        // look into local filesystem in $BDD_DATA_DIR/<subDir> directory
        String bddDataDir = System.getenv("BDD_DATA_DIR");
        assertNotNull("Cannot find system variable BDD_DATA_DIR", bddDataDir);
        assertFalse("System variable BDD_DATA_DIR is empty", bddDataDir.isEmpty());

        Path bddDir = Paths.get(bddDataDir, subDir);
        assertTrue("Cannot find subdir named " + bddDir.toFile().getAbsolutePath(),
                Files.exists(bddDir) && Files.isDirectory(bddDir));
        Path bddFile = Paths.get(bddDir.toString(), fileName);
        assertTrue("Cannot find file named " + bddFile.toFile().getAbsolutePath(),
                Files.exists(bddFile) && Files.isRegularFile(bddFile));

        return bddFile;
    }

    public static String readFileContent(Path filePath, int nbLines) {
        String ret;
        try {
            InputStream inputStream = Files.newInputStream(filePath);
            StringBuilder resultStringBuilder = new StringBuilder();
            try (BufferedReader br
                         = new BufferedReader(new InputStreamReader(inputStream))) {
                String line;
                int lineCount = 0;
                while ((line = br.readLine()) != null && (nbLines == -1 || lineCount < nbLines)) {
                    resultStringBuilder.append(line).append("\n");
                    lineCount++;
                }
            }
            ret = resultStringBuilder.toString();
        } catch (IOException ex) {
            ret = null;
        }
        return ret;
    }

    public static String getResourceFileContent(String resourceFileName) {
        Path resourceFile = Paths.get("src", "test", "resources", resourceFileName);
        assertTrue("Cannot find resource file named " + resourceFile.toFile().getAbsolutePath(),
                Files.exists(resourceFile) && Files.isRegularFile(resourceFile));
        String fileContent = Utils.readFileContent(resourceFile, -1);
        assertTrue("Cannot read content from resource file named " + resourceFile.toFile().getAbsolutePath(), fileContent != null && !fileContent.isEmpty());
        return fileContent;
    }

    public static String getValueFromContingenciesList(JsonNode jsonArray, String subjectId, String side, String limitType, String attributeName) {
        String result = null;
        for (JsonNode violation : jsonArray) {
            if (violation.has("subjectId") && violation.get("subjectId").asText().equalsIgnoreCase(subjectId) &&
                    (Objects.isNull(side) || violation.has("side") && violation.get("side").asText().equalsIgnoreCase(side)) &&
                    violation.has("limitType") && violation.get("limitType").asText().equalsIgnoreCase(limitType) &&
                    violation.has(attributeName)) {
                result = violation.get(attributeName).asText();
            }
        }
        return result;
    }

    public static String getValueFromContingencyViolationsList(JsonNode jsonArray, String subjectId, String side, String limitType, String attributeName) {
        String result = null;
        for (JsonNode violation : jsonArray) {
            if (violation.has("limitViolation")) {
                JsonNode limitViolation = violation.get("limitViolation");
                if (violation.has("subjectId") && violation.get("subjectId").asText().equalsIgnoreCase(subjectId) &&
                        (Objects.isNull(side) || limitViolation.has("side") && limitViolation.get("side").asText().equalsIgnoreCase(side)) &&
                        limitViolation.has("limitType") && limitViolation.get("limitType").asText().equalsIgnoreCase(limitType) &&
                        limitViolation.has(attributeName)) {
                    result = limitViolation.get(attributeName).asText();
                }
            }
        }
        return result;
    }

    public static String getValueFromLimitViolationContingenciesList(JsonNode jsonArray, String contingencyId, String side, String limitType, String attributeName) {
        String result = null;
        for (JsonNode violation : jsonArray) {
            if (violation.has("limitViolation") && violation.has("contingency")) {
                JsonNode limitViolation = violation.get("limitViolation");
                JsonNode contingency = violation.get("contingency");
                if (contingency.has("contingencyId") && contingency.get("contingencyId").asText().equalsIgnoreCase(contingencyId) &&
                        (Objects.isNull(side) || limitViolation.has("side") && limitViolation.get("side").asText().equalsIgnoreCase(side)) &&
                        limitViolation.has("limitType") && limitViolation.get("limitType").asText().equalsIgnoreCase(limitType) &&
                        limitViolation.has(attributeName)) {
                    result = limitViolation.get(attributeName).asText();
                }
            }
        }
        return result;
    }

    public static boolean existFromViolationsList(JsonNode jsonArray, String subjectId, String side, String limitType) {
        for (JsonNode violation : jsonArray) {
            if (violation.has("subjectId") && violation.get("subjectId").asText().equalsIgnoreCase(subjectId) &&
                    violation.has("side") && violation.get("side").asText().equalsIgnoreCase(side) &&
                    violation.has("limitType") && violation.get("limitType").asText().equalsIgnoreCase(limitType)) {
                return true;
            }
        }
        return false;
    }

    public static JsonNode getValueFromContingenciesList(JsonNode jsonNkArray, String contingencyId) {
        for (JsonNode child : jsonNkArray) {
            if (child.has("contingency")) {
                JsonNode contingency = child.get("contingency");
                if (contingency.has("contingencyId") && contingency.get("contingencyId").asText().equalsIgnoreCase(contingencyId) && child.has("subjectLimitViolations") && child.get("subjectLimitViolations").isArray()) {
                    return child.get("subjectLimitViolations");
                }
            }
        }
        return null;
    }

    public static JsonNode getValueFromLimitViolationsList(JsonNode jsonNkArray, String subjectId) {
        for (JsonNode child : jsonNkArray) {
            if (child.has("subjectId") && child.get("subjectId").asText().equalsIgnoreCase(subjectId) && child.has("contingencies") && child.get("contingencies").isArray()) {
                return child.get("contingencies");
            }
        }
        return null;
    }
}
