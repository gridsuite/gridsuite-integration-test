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
}
