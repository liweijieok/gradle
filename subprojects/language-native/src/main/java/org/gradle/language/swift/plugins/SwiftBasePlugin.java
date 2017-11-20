/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.language.swift.plugins;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.TaskContainerInternal;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.language.nativeplatform.internal.Names;
import org.gradle.language.swift.SwiftBinary;
import org.gradle.language.swift.SwiftExecutable;
import org.gradle.language.swift.SwiftSharedLibrary;
import org.gradle.language.swift.internal.DefaultSwiftBinary;
import org.gradle.language.swift.internal.DefaultSwiftExecutable;
import org.gradle.language.swift.internal.DefaultSwiftLibrary;
import org.gradle.language.swift.internal.DefaultSwiftSharedLibrary;
import org.gradle.language.swift.tasks.SwiftCompile;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform;
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal;
import org.gradle.nativeplatform.tasks.AbstractLinkTask;
import org.gradle.nativeplatform.tasks.ExtractSymbols;
import org.gradle.nativeplatform.tasks.InstallExecutable;
import org.gradle.nativeplatform.tasks.LinkExecutable;
import org.gradle.nativeplatform.tasks.LinkSharedLibrary;
import org.gradle.nativeplatform.tasks.StripSymbols;
import org.gradle.nativeplatform.toolchain.NativeToolChain;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainRegistryInternal;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;
import org.gradle.nativeplatform.toolchain.internal.SymbolExtractorOsConfig;
import org.gradle.nativeplatform.toolchain.internal.msvcpp.VisualCppToolChain;
import org.gradle.nativeplatform.toolchain.plugins.SwiftCompilerPlugin;
import org.gradle.platform.base.ToolChain;
import org.gradle.platform.base.internal.toolchain.ToolChainInternal;

import java.util.concurrent.Callable;

/**
 * A common base plugin for the Swift executable and library plugins
 *
 * @since 4.1
 */
@Incubating
public class SwiftBasePlugin implements Plugin<ProjectInternal> {
    @Override
    public void apply(ProjectInternal project) {
        project.getPluginManager().apply(LifecycleBasePlugin.class);
        project.getPluginManager().apply(SwiftCompilerPlugin.class);

        // TODO - Merge with CppBasePlugin to remove code duplication

        final TaskContainerInternal tasks = project.getTasks();
        final DirectoryProperty buildDirectory = project.getLayout().getBuildDirectory();
        final ModelRegistry modelRegistry = project.getModelRegistry();
        final ProviderFactory providers = project.getProviders();

        project.getComponents().withType(SwiftBinary.class, new Action<SwiftBinary>() {
            @Override
            public void execute(final SwiftBinary binary) {
                final Names names = Names.of(binary.getName());
                SwiftCompile compile = tasks.create(names.getCompileTaskName("swift"), SwiftCompile.class);
                compile.getModules().from(binary.getCompileModules());
                compile.getSource().from(binary.getSwiftSource());
                if (binary.isDebuggable()) {
                    compile.setDebuggable(true);
                }
                if (binary.isOptimized()) {
                    compile.setOptimized(true);
                }
                if (binary.isTestable()) {
                    compile.getCompilerArgs().add("-enable-testing");
                }
                compile.getModuleName().set(binary.getModule());
                compile.getObjectFileDir().set(buildDirectory.dir("obj/" + names.getDirName()));
                compile.getModuleFile().set(buildDirectory.file(providers.provider(new Callable<String>() {
                    @Override
                    public String call() throws Exception {
                        return "modules/" + names.getDirName() + binary.getModule().get() + ".swiftmodule";
                    }
                })));
                ((DefaultSwiftBinary)binary).getModuleFile().set(compile.getModuleFile());

                DefaultNativePlatform currentPlatform = new DefaultNativePlatform("current");
                compile.setTargetPlatform(currentPlatform);

                // TODO - make this lazy
                NativeToolChainInternal toolChain = (NativeToolChainInternal) modelRegistry.realize("toolChains", NativeToolChainRegistryInternal.class).getForPlatform(currentPlatform);
                compile.setToolChain(toolChain);

                ((DefaultSwiftBinary)binary).getObjectsDir().set(compile.getObjectFileDir());

                if (binary instanceof SwiftExecutable) {
                    DefaultSwiftExecutable executable = (DefaultSwiftExecutable) binary;
                    // Add a link task
                    LinkExecutable link = tasks.create(names.getTaskName("link"), LinkExecutable.class);
                    link.source(binary.getObjects());
                    link.lib(binary.getLinkLibraries());
                    final PlatformToolProvider toolProvider = toolChain.select(currentPlatform);
                    Provider<RegularFile> exeLocation = buildDirectory.file(providers.provider(new Callable<String>() {
                        @Override
                        public String call() {
                            return toolProvider.getExecutableName("exe/" + names.getDirName() + binary.getModule().get());
                        }
                    }));
                    link.setOutputFile(exeLocation);
                    link.setTargetPlatform(currentPlatform);
                    link.setToolChain(toolChain);
                    link.setDebuggable(binary.isDebuggable());

                    if (binary.isDebuggable() && binary.isOptimized()) {
                        Provider<RegularFile> symbolLocation = buildDirectory.file(providers.provider(new Callable<String>() {
                            @Override
                            public String call() {
                                return toolProvider.getExecutableName("exe/" + names.getDirName() + "stripped/" + binary.getModule().get()) + SymbolExtractorOsConfig.getExtension();
                            }
                        }));
                        Provider<RegularFile> strippedLocation = buildDirectory.file(providers.provider(new Callable<String>() {
                            @Override
                            public String call() {
                                return toolProvider.getExecutableName("exe/" + names.getDirName() + "stripped/"+ binary.getModule().get());
                            }
                        }));
                        StripSymbols stripSymbols = stripSymbols(link, names, tasks, toolChain, currentPlatform, symbolLocation, strippedLocation);
                        executable.getExecutableFile().set(stripSymbols.getOutputFile());
                    } else {
                        executable.getExecutableFile().set(link.getBinaryFile());
                    }

                    // Add an install task
                    // TODO - maybe not for all executables
                    // TODO - add stripped symbols to the installation
                    final InstallExecutable install = tasks.create(names.getTaskName("install"), InstallExecutable.class);
                    install.setPlatform(link.getTargetPlatform());
                    install.setToolChain(link.getToolChain());
                    install.getInstallDirectory().set(buildDirectory.dir("install/" + names.getDirName()));
                    install.getSourceFile().set(executable.getExecutableFile());
                    install.lib(binary.getRuntimeLibraries());
                    executable.getInstallDirectory().set(install.getInstallDirectory());
                    executable.getRunScriptFile().set(install.getRunScriptFile());
                } else if (binary instanceof SwiftSharedLibrary) {
                    DefaultSwiftSharedLibrary library = (DefaultSwiftSharedLibrary) binary;

                    // Specific compiler arguments
                    compile.getCompilerArgs().add("-parse-as-library");

                    // Add a link task
                    final LinkSharedLibrary link = tasks.create(names.getTaskName("link"), LinkSharedLibrary.class);
                    link.source(binary.getObjects());
                    link.lib(binary.getLinkLibraries());
                    // TODO - need to set soname
                    final PlatformToolProvider toolProvider = toolChain.select(currentPlatform);
                    Provider<RegularFile> runtimeFile = buildDirectory.file(providers.provider(new Callable<String>() {
                        @Override
                        public String call() {
                            return toolProvider.getSharedLibraryName("lib/" + names.getDirName() + binary.getModule().get());
                        }
                    }));
                    link.setOutputFile(runtimeFile);
                    link.setTargetPlatform(currentPlatform);
                    link.setToolChain(toolChain);
                    link.setDebuggable(binary.isDebuggable());

                    if (binary.isDebuggable() && binary.isOptimized()) {
                        Provider<RegularFile> symbolLocation = buildDirectory.file(providers.provider(new Callable<String>() {
                            @Override
                            public String call() {
                                return toolProvider.getExecutableName("lib/" + names.getDirName() + "stripped/" + binary.getModule().get()) + SymbolExtractorOsConfig.getExtension();
                            }
                        }));
                        Provider<RegularFile> strippedLocation = buildDirectory.file(providers.provider(new Callable<String>() {
                            @Override
                            public String call() {
                                return toolProvider.getExecutableName("lib/" + names.getDirName() + "stripped/"+ binary.getModule().get());
                            }
                        }));
                        StripSymbols stripSymbols = stripSymbols(link, names, tasks, toolChain, currentPlatform, symbolLocation, strippedLocation);
                        library.getRuntimeFile().set(stripSymbols.getOutputFile());
                    } else {
                        library.getRuntimeFile().set(link.getBinaryFile());
                    }

                }
            }
        });
    }

    private StripSymbols stripSymbols(AbstractLinkTask link, Names names, TaskContainer tasks, NativeToolChainInternal toolChain, NativePlatformInternal currentPlatform, Provider<RegularFile> symbolLocation, Provider<RegularFile> strippedLocation) {
        ExtractSymbols extractSymbols = tasks.create(names.getTaskName("extractSymbols"), ExtractSymbols.class);
        extractSymbols.getBinaryFile().set(link.getBinaryFile());
        extractSymbols.getSymbolFile().set(symbolLocation);
        extractSymbols.setTargetPlatform(currentPlatform);
        extractSymbols.setToolChain(toolChain);

        StripSymbols stripSymbols = tasks.create(names.getTaskName("stripSymbols"), StripSymbols.class);
        // TODO This is not technically correct - these two tasks don't actually have a dependency relationship - we do this because we don't have a lifecycle task to hook into
        stripSymbols.dependsOn(extractSymbols);
        stripSymbols.getBinaryFile().set(link.getBinaryFile());
        stripSymbols.getOutputFile().set(strippedLocation);
        stripSymbols.setTargetPlatform(currentPlatform);
        stripSymbols.setToolChain(toolChain);

        return stripSymbols;
    }
}
