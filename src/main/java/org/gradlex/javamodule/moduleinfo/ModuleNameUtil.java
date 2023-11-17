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

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implementation based on 'jdk.internal.module.ModulePath#deriveModuleDescriptor' and related methods.
 */
class ModuleNameUtil {

    private static final Pattern DASH_VERSION = Pattern.compile("-(\\d+(\\.|$))");
    private static final Pattern NON_ALPHANUM = Pattern.compile("[^A-Za-z0-9]");
    private static final Pattern REPEATING_DOTS = Pattern.compile("(\\.)(\\1)+");
    private static final Pattern LEADING_DOTS = Pattern.compile("^\\.");
    private static final Pattern TRAILING_DOTS = Pattern.compile("\\.$");

    static String automaticModulNameFromFileName(File jarFile) {
        // Derive the version, and the module name if needed, from JAR file name
        String fn = jarFile.getName();
        int i = fn.lastIndexOf(File.separator);
        if (i != -1)
            fn = fn.substring(i + 1);

        // drop ".jar"
        String name = fn.substring(0, fn.length() - 4);

        // find first occurrence of -${NUMBER}. or -${NUMBER}$
        Matcher matcher = DASH_VERSION.matcher(name);
        if (matcher.find()) {
            name = name.substring(0, matcher.start());
        }
        return validateModuleName(cleanModuleName(name));
    }

    static String validateModuleName(String name) {
        int next;
        int off = 0;
        while ((next = name.indexOf('.', off)) != -1) {
            String id = name.substring(off, next);
            if (!isJavaIdentifier(id)) {
                throw new IllegalArgumentException(name + ": Invalid module name"
                        + ": '" + id + "' is not a Java identifier");
            }
            off = next+1;
        }
        String last = name.substring(off);
        if (!isJavaIdentifier(last)) {
            throw new IllegalArgumentException(name + ": Invalid module name"
                    + ": '" + last + "' is not a Java identifier");
        }
        return name;
    }

    private static String cleanModuleName(String mn) {
        // replace non-alphanumeric
        mn = NON_ALPHANUM.matcher(mn).replaceAll(".");

        // collapse repeating dots
        mn = REPEATING_DOTS.matcher(mn).replaceAll(".");

        // drop leading dots
        if (!mn.isEmpty() && mn.charAt(0) == '.')
            mn = LEADING_DOTS.matcher(mn).replaceAll("");

        // drop trailing dots
        int len = mn.length();
        if (len > 0 && mn.charAt(len-1) == '.')
            mn = TRAILING_DOTS.matcher(mn).replaceAll("");

        return mn;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static boolean isJavaIdentifier(String str) {
        if (str.isEmpty() || RESERVED.contains(str))
            return false;

        int first = Character.codePointAt(str, 0);
        if (!Character.isJavaIdentifierStart(first))
            return false;

        int i = Character.charCount(first);
        while (i < str.length()) {
            int cp = Character.codePointAt(str, i);
            if (!Character.isJavaIdentifierPart(cp))
                return false;
            i += Character.charCount(cp);
        }

        return true;
    }

    // keywords, boolean and null literals, not allowed in identifiers
    private static final List<String> RESERVED = Arrays.asList(
            "abstract",
            "assert",
            "boolean",
            "break",
            "byte",
            "case",
            "catch",
            "char",
            "class",
            "const",
            "continue",
            "default",
            "do",
            "double",
            "else",
            "enum",
            "extends",
            "final",
            "finally",
            "float",
            "for",
            "goto",
            "if",
            "implements",
            "import",
            "instanceof",
            "int",
            "interface",
            "long",
            "native",
            "new",
            "package",
            "private",
            "protected",
            "public",
            "return",
            "short",
            "static",
            "strictfp",
            "super",
            "switch",
            "synchronized",
            "this",
            "throw",
            "throws",
            "transient",
            "try",
            "void",
            "volatile",
            "while",
            "true",
            "false",
            "null",
            "_"
    );
}
