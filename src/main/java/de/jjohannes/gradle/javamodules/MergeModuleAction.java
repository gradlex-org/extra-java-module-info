package de.jjohannes.gradle.javamodules;

import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.compile.JavaCompile;

import java.io.File;
import java.util.*;
import java.util.Optional;
import java.util.stream.Collectors;

public class MergeModuleAction implements Action<Task> {

    private final List<ModuleSpec> mergedModules;
    private final File mergedJarFolder;
    private final ObjectFactory objects;

    public MergeModuleAction(List<ModuleSpec> mergedModules, File mergedJarFolder, ObjectFactory objects) {
        this.mergedModules = mergedModules;
        this.mergedJarFolder = mergedJarFolder;
        this.objects = objects;
    }

    @Override
    public void execute(Task task) {
        JavaCompile javaCompile = (JavaCompile) task;
        Set<File> jars = javaCompile.getClasspath().getFiles();
        Set<File> mergedCp = new HashSet<>(jars);

        for (ModuleSpec mergedModule : mergedModules) {
            String jarBaseName = mergedModule.getJarName().substring(0, mergedModule.getJarName().lastIndexOf('.'));
            String jarModuleName = jarBaseName + "-module.jar";
            String jarMergedName = jarBaseName + "-merged.jar";
            Optional<File> mainJar = jars.stream().filter(j -> j.getName().equals(jarModuleName)).findFirst();
            if (!mainJar.isPresent()) {
                continue;
            }
            List<File> jarsToMerge = jars.stream().filter(j -> mergedModule.getMergedJars().contains(j.getName())).collect(Collectors.toList());
            if (!jarsToMerge.isEmpty()) {
                File outJar = new File(mergedJarFolder, jarMergedName);

                JarRewriteUtil.mergeJars(mainJar.get(), jarsToMerge, outJar);

                mergedCp.remove(mainJar.get());
                mergedCp.removeAll(jarsToMerge);
                mergedCp.add(outJar);
            }
        }

        javaCompile.setClasspath(objects.fileCollection().from(mergedCp));
    }
}
