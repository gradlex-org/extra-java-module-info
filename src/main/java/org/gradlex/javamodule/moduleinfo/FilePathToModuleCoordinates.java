// SPDX-License-Identifier: Apache-2.0
package org.gradlex.javamodule.moduleinfo;

import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Attempts to parse 'group', 'name', 'version' coordinates from a paths like:
 *   .gradle/caches/modules-2/files-2.1/org.slf4j/slf4j-api/1.7.36/6c62681a2f655b49963a5983b8b0950a6120ae14/slf4j-api-1.7.36.jar
 *   .m2/repository/com/google/code/findbugs/jsr305/3.0.2/jsr305-3.0.2.jar
 */
@NullMarked
final class FilePathToModuleCoordinates {

    @Nullable
    static String versionFromFilePath(Path path) {
        if (null != isInGradleCache(path)) {
            return getVersionFromGradleCachePath(path);
        }
        if (null != isInM2Cache(path)) {
            return getVersionFromM2CachePath(path);
        }
        return null;
    }

    static boolean gaCoordinatesFromFilePathMatch(Path path, String gaAndClassifier) {
        if (gaAndClassifier.contains("|")) {
            String[] split = gaAndClassifier.split("\\|");
            return gaCoordinatesFromFilePathMatch(path, split[0], split[1]);
        }
        return gaCoordinatesFromFilePathMatch(path, gaAndClassifier, null);
    }

    static boolean gaCoordinatesFromFilePathMatch(Path path, String ga, @Nullable String classifier) {
        String name = nameCoordinateFromFilePath(path);
        String group = groupCoordinateFromFilePath(path);
        if (name == null || group == null) {
            return false;
        }

        String coordinatesFromPath = group + ":" + name;

        String classifierFromGradleCache = isInGradleCache(path);
        if (classifierFromGradleCache != null) {
            if (classifierFromGradleCache.isEmpty()) {
                return coordinatesFromPath.equals(ga);
            }
            return coordinatesFromPath.equals(ga) && classifierFromGradleCache.equals(classifier);
        }

        String classifierFromM2Cache = isInM2Cache(path);
        if (classifierFromM2Cache != null) {
            if (classifierFromM2Cache.isEmpty()) {
                return coordinatesFromPath.endsWith(ga);
            }
            return coordinatesFromPath.endsWith(ga) && classifierFromM2Cache.equals(classifier);
        }

        return false;
    }

    @Nullable
    private static String groupCoordinateFromFilePath(Path path) {
        if (null != isInGradleCache(path)) {
            return path.getName(path.getNameCount() - 5).toString();
        }
        if (null != isInM2Cache(path)) {
            return StreamSupport.stream(path.subpath(0, path.getNameCount() - 3).spliterator(), false)
                    .map(Path::toString)
                    .collect(Collectors.joining("."));
        }
        return null;
    }

    @Nullable
    static String isInGradleCache(Path path) {
        String name = nameCoordinateFromFilePath(path);
        if (name == null) {
            return null;
        }
        String version = getVersionFromGradleCachePath(path);
        return matchesPath(path, name, version);
    }

    @Nullable
    static String isInM2Cache(Path path) {
        String name = nameCoordinateFromFilePath(path);
        if (name == null) {
            return null;
        }
        String version = getVersionFromM2CachePath(path);
        return matchesPath(path, name, version);
    }

    @Nullable
    private static String nameCoordinateFromFilePath(Path path) {
        if (path.getNameCount() < 5) {
            return null;
        }

        String nameFromGradleCachePath = path.getName(path.getNameCount() - 4).toString();
        String versionFromGradleCachePath = getVersionFromGradleCachePath(path);
        if (null != matchesPath(path, nameFromGradleCachePath, versionFromGradleCachePath)) {
            return nameFromGradleCachePath;
        }
        String nameFromM2CachePath = path.getName(path.getNameCount() - 3).toString();
        String versionFromM2CachePath = getVersionFromM2CachePath(path);
        if (null != matchesPath(path, nameFromM2CachePath, versionFromM2CachePath)) {
            return nameFromM2CachePath;
        }

        return null;
    }

    private static String getVersionFromGradleCachePath(Path path) {
        return path.getName(path.getNameCount() - 3).toString();
    }

    private static String getVersionFromM2CachePath(Path path) {
        return path.getName(path.getNameCount() - 2).toString();
    }

    /**
     * @return the classifier or 'null' if there is no match.
     */
    @Nullable
    private static String matchesPath(Path path, String potentialName, String potentialVersion) {
        String jarFileName = path.getFileName().toString();
        boolean fuzzyMatchVersion = potentialVersion.contains("-");
        String potentialVersionNormalized =
                fuzzyMatchVersion ? potentialVersion.substring(0, potentialVersion.indexOf("-")) : potentialVersion;

        if (jarFileName.startsWith(potentialVersionNormalized)) {
            return null;
        }

        String baseName = potentialName + "-" + potentialVersionNormalized;
        if (jarFileName.startsWith(baseName)) {
            if (fuzzyMatchVersion) {
                // Potential classifiers are ignored as we do not know what is version and what is classifier.
                // For example in '33.2.1-android' is '-android' part of the version or a classifier?
                return "";
            } else {
                if (jarFileName.startsWith(baseName + ".")) {
                    return "";
                }
                return jarFileName.substring(baseName.length() + 1, jarFileName.length() - 4);
            }
        }

        return null;
    }
}
