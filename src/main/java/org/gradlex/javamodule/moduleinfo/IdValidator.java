package org.gradlex.javamodule.moduleinfo;

class IdValidator {
    static private final String COORDINATES_PATTERN = "^[a-zA-Z0-9._-]+:[a-zA-Z0-9._-]+$";
    static private final String FILE_NAME_PATTERN = "^[a-zA-Z0-9._-]+\\.(jar|zip)$";
    static private final String MODULE_NAME_PATTERN = "^[a-z][a-z0-9_]*(\\.[a-z0-9_]+)*$";

    static void validateIdentifier(String identifier) {
        if (!identifier.matches(COORDINATES_PATTERN) && !identifier.matches(FILE_NAME_PATTERN)) {
            throw new RuntimeException("'" + identifier + "' are not valid coordinates (group:name) / is not a valid file name (name-1.0.jar)");
        }
    }

    static void validateModuleName(String moduleName) {
        if (!moduleName.matches(MODULE_NAME_PATTERN)) {
            throw new RuntimeException("'" + moduleName + "' is not a valid Java Module name");
        }
    }
}
