/*
 * Copyright the GradleX team.
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

class IdValidator {
    static private final String COORDINATES_PATTERN = "^[a-zA-Z0-9._-]+:[a-zA-Z0-9._-]+(\\|[a-zA-Z0-9._-]+)?$";
    static private final String FILE_NAME_PATTERN = "^[a-zA-Z0-9._-]+\\.(jar|zip)$";

    static void validateIdentifier(String identifier) {
        if (!identifier.matches(COORDINATES_PATTERN) && !identifier.matches(FILE_NAME_PATTERN)) {
            throw new RuntimeException("'" + identifier + "' are not valid coordinates (group:name) / is not a valid file name (name-1.0.jar)");
        }
    }
}
