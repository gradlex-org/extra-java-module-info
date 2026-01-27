// SPDX-License-Identifier: Apache-2.0
package org.gradlex.javamodule.moduleinfo;

import org.jspecify.annotations.NullMarked;

@NullMarked
public class KnownModule extends ModuleSpec {

    KnownModule(String identifier, String moduleName) {
        super(identifier, moduleName);
    }
}
