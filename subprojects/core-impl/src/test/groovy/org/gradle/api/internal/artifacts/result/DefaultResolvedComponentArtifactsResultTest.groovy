/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.artifacts.result
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.result.Artifact
import org.gradle.api.artifacts.result.ArtifactResult
import org.gradle.api.artifacts.result.jvm.JavadocArtifact
import org.gradle.api.artifacts.result.jvm.SourcesArtifact
import spock.lang.Specification

class DefaultResolvedComponentArtifactsResultTest extends Specification {
    def id = Mock(ComponentIdentifier)
    def result = new DefaultResolvedComponentArtifactsResult(id)

    def "returns artifacts matching type"() {
        given:
        def artifact = Mock(ArtifactResult)
        result.addArtifact(artifact)

        when:
        artifact.type >> SourcesArtifact

        then:
        result.getArtifacts(Artifact) == [artifact] as Set
        result.getArtifacts(SourcesArtifact) == [artifact] as Set
        result.getArtifacts(JavadocArtifact) == [] as Set
    }
}
