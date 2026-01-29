// SPDX-License-Identifier: Apache-2.0
package org.gradlex.javamodule.moduleinfo;

import org.jspecify.annotations.NullMarked;

@NullMarked
public class AutomaticModuleName extends ModuleSpec {

    AutomaticModuleName(String identifier, String moduleName) {
        super(identifier, moduleName);
    }
}
