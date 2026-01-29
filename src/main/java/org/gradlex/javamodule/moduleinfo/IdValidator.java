// SPDX-License-Identifier: Apache-2.0
package org.gradlex.javamodule.moduleinfo;

import org.jspecify.annotations.NullMarked;

@NullMarked
class IdValidator {
    private static final String COORDINATES_PATTERN = "^[a-zA-Z0-9._-]+:[a-zA-Z0-9._-]+(\\|[a-zA-Z0-9._-]+)?$";
    private static final String FILE_NAME_PATTERN = "^[a-zA-Z0-9._-]+\\.(jar|zip)$";

    static void validateIdentifier(String identifier) {
        if (!identifier.matches(COORDINATES_PATTERN) && !identifier.matches(FILE_NAME_PATTERN)) {
            throw new RuntimeException("'" + identifier
                    + "' are not valid coordinates (group:name) / is not a valid file name (name-1.0.jar)");
        }
    }

    static boolean isCoordinates(String identifier) {
        return identifier.matches(COORDINATES_PATTERN);
    }
}
