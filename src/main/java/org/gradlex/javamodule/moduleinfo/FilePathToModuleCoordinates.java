/*
 * Copyright 2022 the GradleX team.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradlex.javamodule.moduleinfo;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Attempts to parse 'group', 'name', 'version' coordinates from a paths like:
 *   .gradle/caches/modules-2/files-2.1/org.slf4j/slf4j-api/1.7.36/6c62681a2f655b49963a5983b8b0950a6120ae14/slf4j-api-1.7.36.jar
 *   .m2/repository/com/google/code/findbugs/jsr305/3.0.2/jsr305-3.0.2.jar
 */
final class FilePathToModuleCoordinates {

    @Nullable
    static String versionFromFilePath(Path path) {
        if (isInGradleCache(path)) {
            return path.getName(path.getNameCount() - 3).toString();
        }
        if (isInM2Cache(path)) {
            return path.getName(path.getNameCount() - 2).toString();
        }
        return null;
    }

    static boolean gaCoordinatesFromFilePathMatch(Path path, String ga) {
        String name = nameCoordinateFromFilePath(path);
        String group = groupCoordinateFromFilePath(path);
        if (name == null || group == null) {
            return false;
        }
        return (isInGradleCache(path) && ga.equals(group + ":" + name)) || (isInM2Cache(path) && (group + ":" + name).endsWith("." + ga));
    }

    @Nullable
    private static String groupCoordinateFromFilePath(Path path) {
        if (isInGradleCache(path)) {
            return path.getName(path.getNameCount() - 5).toString();
        }
        if (isInM2Cache(path)) {
            return StreamSupport.stream(path.subpath(0, path.getNameCount() - 3).spliterator(), false).map(Path::toString).collect(Collectors.joining("."));
        }
        return null;
    }

    static boolean isInGradleCache(Path path) {
        String name = nameCoordinateFromFilePath(path);
        if (name == null) {
            return false;
        }
        String version = path.getName(path.getNameCount() - 3).toString();
        return path.getFileName().toString().startsWith(name + "-" + version);
    }

    static boolean isInM2Cache(Path path) {
        String name = nameCoordinateFromFilePath(path);
        if (name == null) {
            return false;
        }
        String version = path.getName(path.getNameCount() - 2).toString();
        return path.getFileName().toString().startsWith(name + "-" + version);
    }

    @Nullable
    private static String nameCoordinateFromFilePath(Path path) {
        if (path.getNameCount() < 5) {
            return null;
        }

        String nameFromGradleCachePath = path.getName(path.getNameCount() - 4).toString();
        if (path.getFileName().toString().startsWith(nameFromGradleCachePath + "-")) {
            return nameFromGradleCachePath;
        }
        String nameFromM2CachePath = path.getName(path.getNameCount() - 3).toString();
        if (path.getFileName().toString().startsWith(nameFromM2CachePath + "-")) {
            return nameFromM2CachePath;
        }

        return null;
    }
}
