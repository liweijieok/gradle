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

package org.gradle.nativeplatform.test.xctest.internal

import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.file.ProjectLayout
import org.gradle.api.internal.file.FileOperations
import org.gradle.language.swift.SwiftComponent
import org.gradle.nativeplatform.test.xctest.SwiftXCTestSuite
import org.gradle.util.TestUtil
import spock.lang.Specification

class DefaultSwiftXCTestSuiteTest extends Specification {
    def "has only a single executables"() {
        def componentUnderTest = Mock(SwiftComponent)
        SwiftXCTestSuite testSuite = new DefaultSwiftXCTestSuite("test", Mock(ProjectLayout), Stub(FileOperations), TestUtil.objectFactory(), Stub(ConfigurationContainer))
        testSuite.testedComponent.set(componentUnderTest)
        expect:
        testSuite.developmentBinary.name == "testExecutable"
        testSuite.developmentBinary.debuggable
        testSuite.testedComponent.get() == componentUnderTest
    }
}
