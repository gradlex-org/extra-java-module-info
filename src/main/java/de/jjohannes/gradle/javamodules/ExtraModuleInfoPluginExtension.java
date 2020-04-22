/*
 * Copyright 2020 the original author or authors.
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

import org.gradle.api.Action;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * A data class to collect all the module information we want to add.
 * Here the class is used as extension that can be configured in the build script
 * and as input to the ExtraModuleInfoTransform that add the information to Jars.
 */
public class ExtraModuleInfoPluginExtension {

    private final Map<String, ModuleInfo> moduleInfo = new HashMap<>();
    private final Map<String, String> automaticModules = new HashMap<>();

    /**
     * Add full module information for a given Jar file.
     */
    public void module(String jarName, String moduleName, String moduleVersion) {
        module(jarName, moduleName, moduleVersion, null);
    }

    /**
     * Add full module information, including exported packages and dependencies, for a given Jar file.
     */
    public void module(String jarName, String moduleName, String moduleVersion, @Nullable Action<? super ModuleInfo> conf) {
        ModuleInfo moduleInfo = new ModuleInfo(moduleName, moduleVersion);
        if (conf != null) {
            conf.execute(moduleInfo);
        }
        this.moduleInfo.put(jarName, moduleInfo);
    }

    /**
     * Add only an automatic module name to a given jar file.
     */
    public void automaticModule(String jarName, String moduleName) {
        automaticModules.put(jarName, moduleName);
    }

    protected Map<String, ModuleInfo> getModuleInfo() {
        return moduleInfo;
    }

    protected Map<String, String> getAutomaticModules() {
        return automaticModules;
    }
}
