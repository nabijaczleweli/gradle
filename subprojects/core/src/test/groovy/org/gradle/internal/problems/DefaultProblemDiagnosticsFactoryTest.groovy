/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.problems

import com.google.common.base.Supplier
import org.gradle.internal.code.UserCodeApplicationContext
import org.gradle.internal.code.UserCodeSource
import org.gradle.internal.problems.failure.DefaultFailureFactory
import org.gradle.internal.problems.failure.Failure
import org.gradle.internal.problems.failure.StackTraceClassifier
import org.gradle.problems.Location
import spock.lang.Specification

class DefaultProblemDiagnosticsFactoryTest extends Specification {
    def failureFactory = new DefaultFailureFactory(StackTraceClassifier.USER_CODE)
    def locationAnalyzer = Mock(ProblemLocationAnalyzer)
    def userCodeContext = Mock(UserCodeApplicationContext)
    def factory = new DefaultProblemDiagnosticsFactory(failureFactory, locationAnalyzer, userCodeContext, 2)

    def "uses caller's stack trace to calculate problem location"() {
        given:
        def location = Stub(Location)
        def stream = factory.newStream()

        when:
        def diagnostics = stream.forCurrentCaller()

        then:
        diagnostics.stackTracing == null
        assertIsCallerStackTrace(diagnostics.minimizedStackTrace)
        diagnostics.location == location

        1 * locationAnalyzer.locationForUsage(_, false) >> { Failure failure, b ->
            assertIsCallerStackTrace(failure.stackTrace)
            location
        }
    }

    def "uses caller provided exception factory to calculate problem location"() {
        given:
        def exception = new Exception()
        def supplier = Stub(Supplier) {
            get() >> exception
        }
        def location = Stub(Location)
        def stream = factory.newStream()

        when:
        def diagnostics = stream.forCurrentCaller(supplier)

        then:
        diagnostics.stackTracing.header == exception.toString()
        diagnostics.stackTracing.stackTrace == exception.stackTrace.toList()
        diagnostics.location == location

        1 * locationAnalyzer.locationForUsage(_, false) >> { Failure failure, b ->
            assert failure.stackTrace == exception.stackTrace.toList()
            location
        }
    }

    def "does not populate stack traces after limit has been reached"() {
        def supplier = Stub(Supplier) {
            get() >> { throw new Exception() }
        }
        def stream = factory.newStream()

        expect:
        def diagnostics1 = stream.forCurrentCaller()
        diagnostics1.stackTracing == null
        !diagnostics1.minimizedStackTrace.empty

        def diagnostics2 = stream.forCurrentCaller()
        diagnostics2.stackTracing == null
        !diagnostics2.minimizedStackTrace.empty

        def diagnostics3 = stream.forCurrentCaller()
        diagnostics3.stackTracing == null
        diagnostics3.minimizedStackTrace.empty

        def diagnostics4 = stream.forCurrentCaller()
        diagnostics4.stackTracing == null
        diagnostics4.minimizedStackTrace.empty

        def diagnostics5 = stream.forCurrentCaller(supplier)
        diagnostics5.stackTracing == null
        diagnostics5.minimizedStackTrace.empty
    }

    def "each stream has an independent stack trace limit"() {
        def stream1 = factory.newStream()
        def stream2 = factory.newStream()

        given:
        stream1.forCurrentCaller()

        expect:
        !stream1.forCurrentCaller().minimizedStackTrace.empty

        !stream2.forCurrentCaller().minimizedStackTrace.empty
        !stream2.forCurrentCaller().minimizedStackTrace.empty
        stream2.forCurrentCaller().minimizedStackTrace.empty

        stream1.forCurrentCaller().minimizedStackTrace.empty
    }

    def "keeps stack trace after limit has been reached when diagnostics constructed from exception"() {
        given:
        def stream = factory.newStream()
        stream.forCurrentCaller()
        stream.forCurrentCaller()

        expect:
        stream.forCurrentCaller().minimizedStackTrace.empty

        def failure1 = new Exception("broken")
        def diagnostics1 = stream.forCurrentCaller(failure1)
        diagnostics1.stackTracing.header == failure1.toString()
        diagnostics1.stackTracing.stackTrace == failure1.stackTrace.toList()
        !diagnostics1.minimizedStackTrace.empty

        def failure2 = new Exception("broken")
        def diagnostics2 = factory.forException(failure2)
        diagnostics2.stackTracing.header == failure2.toString()
        diagnostics2.stackTracing.stackTrace == failure2.stackTrace.toList()
        !diagnostics2.minimizedStackTrace.empty
    }

    def "tracks user code source"() {
        given:
        def currentSource = Stub(UserCodeSource)
        def stream = factory.newStream()

        when:
        def diagnostics = stream.forCurrentCaller()

        then:
        diagnostics.source == currentSource

        1 * userCodeContext.current() >> Mock(UserCodeApplicationContext.Application) {
            getSource() >> currentSource
        }
    }

    void assertIsCallerStackTrace(List<StackTraceElement> trace) {
        assert trace.any { it.className == DefaultProblemDiagnosticsFactoryTest.name }
    }
}
