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

package de.jjohannes.gradle.javamodules;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

public abstract class ExtraModuleInfoPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getLogger().lifecycle("Plugin ID changed - please update your build:\n" +
                "  Plugin ID: de.jjohannes.extra-java-module-info -> org.gradlex.extra-java-module-info\n" +
                "  GA Coordinates: de.jjohannes.gradle:extra-java-module-info -> org.gradlex:extra-java-module-info");
        project.getPlugins().apply("org.gradlex.extra-java-module-info");
    }
}
