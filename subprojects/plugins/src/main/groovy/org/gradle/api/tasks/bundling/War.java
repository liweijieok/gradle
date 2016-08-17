/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.api.tasks.bundling;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.Transformer;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.copy.DefaultCopySpec;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OrderSensitive;
import org.gradle.util.ConfigureUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.Callable;

/**
 * Assembles a WAR archive.
 */
public class War extends Jar {
    public static final String WAR_EXTENSION = "war";
    private static final Spec<File> IS_DIRECTORY = new Spec<File>() {
        @Override
        public boolean isSatisfiedBy(File element) {
            return element.isDirectory();
        }
    };
    private static final Spec<File> IS_FILE = new Spec<File>() {
        @Override
        public boolean isSatisfiedBy(File element) {
            return element.isFile();
        }
    };

    private File webXml;
    private FileCollection classpath;
    private final DefaultCopySpec webInf;


    public War() {
        setExtension(WAR_EXTENSION);
        setMetadataCharset("UTF-8");
        // Add these as separate specs, so they are not affected by the changes to the main spec

        webInf = (DefaultCopySpec) getRootSpec().addChildBeforeSpec(getMainSpec()).into("WEB-INF");
        webInf.into("classes", new Action<CopySpec>() {
            @Override
            public void execute(CopySpec copySpec) {
                copySpec.from(new Callable<Object>() {
                    public Object call() {
                        FileCollection classpath = getClasspath();
                        return classpath != null ? classpath.filter(IS_DIRECTORY) : Collections.emptyList();
                    }
                });
            }
        });
        webInf.into("lib", new Action<CopySpec>() {
            public void execute(CopySpec it) {
                it.from(new Callable<Object>() {
                    public Object call() {
                        FileCollection classpath = getClasspath();
                        return classpath != null ? classpath.filter(IS_FILE) : Collections.emptyList();
                    }
                });
            }

        });
        webInf.into("", new Action<CopySpec>() {
            public void execute(CopySpec it) {
                it.from(new Callable<File>() {
                    public File call() {
                        return getWebXml();
                    }
                });
                it.rename(new Transformer<String, String>() {
                    public String transform(String it) {
                        return "web.xml";
                    }
                });
            }
        });
    }

    @Internal
    public CopySpec getWebInf() {
        return webInf.addChild();
    }

    /**
     * Adds some content to the {@code WEB-INF} directory for this WAR archive.
     *
     * <p>The given closure is executed to configure a {@link CopySpec}. The {@code CopySpec} is passed to the closure as its delegate.
     *
     * @param configureClosure The closure to execute
     * @return The newly created {@code CopySpec}.
     */
    public CopySpec webInf(Closure configureClosure) {
        return ConfigureUtil.configure(configureClosure, getWebInf());
    }

    /**
     * Returns the classpath to include in the WAR archive. Any JAR or ZIP files in this classpath are included in the {@code WEB-INF/lib} directory. Any directories in this classpath are included in
     * the {@code WEB-INF/classes} directory.
     *
     * @return The classpath. Returns an empty collection when there is no classpath to include in the WAR.
     */
    @OrderSensitive
    @InputFiles
    @Optional
    public FileCollection getClasspath() {
        return classpath;
    }

    /**
     * Sets the classpath to include in the WAR archive.
     *
     * @param classpath The classpath. Must not be null.
     */
    public void setClasspath(Object classpath) {
        this.classpath = getProject().files(classpath);
    }

    /**
     * Adds files to the classpath to include in the WAR archive.
     *
     * @param classpath The files to add. These are evaluated as per {@link org.gradle.api.Project#files(Object...)}
     */
    public void classpath(Object... classpath) {
        FileCollection oldClasspath = getClasspath();
        this.classpath = getProject().files(oldClasspath != null ? oldClasspath : new ArrayList(), classpath);
    }

    /**
     * Returns the {@code web.xml} file to include in the WAR archive. When {@code null}, no {@code web.xml} file is included in the WAR.
     *
     * @return The {@code web.xml} file.
     */
    @InputFile
    @Optional
    public File getWebXml() {
        return webXml;
    }

    /**
     * Sets the {@code web.xml} file to include in the WAR archive. When {@code null}, no {@code web.xml} file is included in the WAR.
     *
     * @param webXml The {@code web.xml} file. Maybe null.
     */
    public void setWebXml(File webXml) {
        this.webXml = webXml;
    }

}
