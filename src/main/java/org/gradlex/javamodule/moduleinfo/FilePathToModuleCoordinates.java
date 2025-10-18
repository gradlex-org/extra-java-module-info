// SPDX-License-Identifier: Apache-2.0
package org.gradlex.javamodule.moduleinfo;

import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;

/**
 * Attempts to parse 'group', 'name', 'version' coordinates from a paths like:
 *   .gradle/caches/modules-2/files-2.1/org.slf4j/slf4j-api/1.7.36/6c62681a2f655b49963a5983b8b0950a6120ae14/slf4j-api-1.7.36.jar
 *   .m2/repository/com/google/code/findbugs/jsr305/3.0.2/jsr305-3.0.2.jar
 */
final class FilePathToModuleCoordinates {

    @Nullable
    static String versionFromFilePath(Path path) {
        if (isInGradleCache(path)) {
            return getVersionFromGradleCachePath(path);
        }
        if (isInM2Cache(path)) {
            return getVersionFromM2CachePath(path);
        }
        return null;
    }

    static boolean gaCoordinatesFromFilePathMatch(Path path, String ga) {
        String name = nameCoordinateFromFilePath(path);
        String group = groupCoordinateFromFilePath(path);
        if (name == null || group == null) {
            return false;
        }
        return (isInGradleCache(path) && ga.equals(group + ":" + name))
                || (isInM2Cache(path) && (group + ":" + name).endsWith("." + ga));
    }

    @Nullable
    private static String groupCoordinateFromFilePath(Path path) {
        if (isInGradleCache(path)) {
            return path.getName(path.getNameCount() - 5).toString();
        }
        if (isInM2Cache(path)) {
            return StreamSupport.stream(path.subpath(0, path.getNameCount() - 3).spliterator(), false)
                    .map(Path::toString)
                    .collect(Collectors.joining("."));
        }
        return null;
    }

    static boolean isInGradleCache(Path path) {
        String name = nameCoordinateFromFilePath(path);
        if (name == null) {
            return false;
        }
        String version = getVersionFromGradleCachePath(path);
        return matchesPath(path, name, version);
    }

    static boolean isInM2Cache(Path path) {
        String name = nameCoordinateFromFilePath(path);
        if (name == null) {
            return false;
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
        if (matchesPath(path, nameFromGradleCachePath, versionFromGradleCachePath)) {
            return nameFromGradleCachePath;
        }
        String nameFromM2CachePath = path.getName(path.getNameCount() - 3).toString();
        String versionFromM2CachePath = getVersionFromM2CachePath(path);
        if (matchesPath(path, nameFromM2CachePath, versionFromM2CachePath)) {
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

    private static boolean matchesPath(Path path, String name, String version) {
        String jarFileName = path.getFileName().toString();
        return jarFileName.startsWith(name + "-") && !jarFileName.startsWith(version);
    }
}
