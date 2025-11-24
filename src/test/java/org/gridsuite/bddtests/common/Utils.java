/*
  Copyright (c) 2022, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.bddtests.common;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

public final class Utils {
    private Utils() {
        throw new java.lang.UnsupportedOperationException("BddUtils Utility class and cannot be instantiated");
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
        assertTrue(Files.exists(resourceFile) && Files.isRegularFile(resourceFile),
                "Cannot find resource file named " + resourceFile.toFile().getAbsolutePath());
        String fileContent = Utils.readFileContent(resourceFile, -1);
        assertTrue(fileContent != null && !fileContent.isEmpty(), "Cannot read content from resource file named " + resourceFile.toFile().getAbsolutePath());
        return fileContent;
    }
}
