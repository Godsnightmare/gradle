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

package org.gradle.internal.resolve.resolver;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactSetFactory;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.FileDependencyArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.simple.DefaultExcludeFactory;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec;
import org.gradle.api.internal.artifacts.type.ArtifactTypeRegistry;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.local.model.LocalFileDependencyMetadata;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.ModuleSources;
import org.gradle.internal.component.model.ResolvedVariantCache;
import org.gradle.internal.component.model.VariantResolveMetadata;
import org.gradle.internal.model.CalculatedValueContainerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public class DefaultArtifactSelector implements ArtifactSelector {
    private static final ExcludeSpec EXCLUDE_NONE = new DefaultExcludeFactory().nothing();

    private final List<OriginArtifactSelector> selectors;
    private final ArtifactTypeRegistry artifactTypeRegistry;
    private final ArtifactResolver artifactResolver;
    private final CalculatedValueContainerFactory calculatedValueContainerFactory;
    private final ResolvedVariantCache variantCache;

    public DefaultArtifactSelector(List<OriginArtifactSelector> selectors, ArtifactResolver artifactResolver, ArtifactTypeRegistry artifactTypeRegistry, ResolvedVariantCache variantCache, CalculatedValueContainerFactory calculatedValueContainerFactory) {
        this.selectors = selectors;
        this.artifactTypeRegistry = artifactTypeRegistry;
        this.artifactResolver = artifactResolver;
        this.variantCache = variantCache;
        this.calculatedValueContainerFactory = calculatedValueContainerFactory;
    }

    @Override
    public ArtifactSet resolveArtifacts(LocalFileDependencyMetadata fileDependencyMetadata) {
        return new FileDependencyArtifactSet(fileDependencyMetadata, artifactTypeRegistry, calculatedValueContainerFactory);
    }

    @Override
    public ArtifactSet resolveArtifacts(ComponentResolveMetadata component, ConfigurationMetadata configurationMetadata, ExcludeSpec exclusions, ImmutableAttributes overriddenAttributes) {
        ArtifactSet artifacts = null;
        for (OriginArtifactSelector selector : selectors) {
            artifacts = selector.resolveArtifacts(new DefaultComponentResolveMetadataForArtifactSelection(component, exclusions, configurationMetadata.getVariants()), artifactTypeRegistry, exclusions, overriddenAttributes);
            if (artifacts != null) {
                break;
            }
        }
        if (artifacts == null) {
            throw new IllegalStateException("No artifacts selected.");
        }
        return artifacts;
    }

    @Override
    public ArtifactSet resolveArtifacts(ComponentResolveMetadata component, Collection<? extends ComponentArtifactMetadata> artifacts, ImmutableAttributes overriddenAttributes) {
        return ArtifactSetFactory.adHocVariant(component.getId(), component.getModuleVersionId(), artifacts, component.getSources(), EXCLUDE_NONE, component.getAttributesSchema(), artifactResolver, artifactTypeRegistry, component.getAttributes(), overriddenAttributes, calculatedValueContainerFactory);
    }

    class DefaultComponentResolveMetadataForArtifactSelection implements OriginArtifactSelector.ComponentResolveMetadataForArtifactSelection {
        private final ComponentResolveMetadata delegate;
        private final Set<? extends VariantResolveMetadata> variants;
        private final ExcludeSpec exclusions;

        public DefaultComponentResolveMetadataForArtifactSelection(ComponentResolveMetadata delegate, ExcludeSpec exclusions, Set<? extends VariantResolveMetadata> variants) {
            this.delegate = delegate;
            this.exclusions = exclusions;
            this.variants = variants;
        }

        @Override
        public ComponentIdentifier getId() {
            return delegate.getId();
        }

        @Override
        public ModuleSources getSources() {
            return delegate.getSources();
        }

        @Override
        public AttributesSchemaInternal getAttributesSchema() {
            return delegate.getAttributesSchema();
        }

        @Override
        public Set<ResolvedVariant> getResolvedVariants() {
            ImmutableSet.Builder<ResolvedVariant> result = ImmutableSet.builder();
            for (VariantResolveMetadata variant : variants) {
                assert variant.getIdentifier()!=null;
                result.add(variantCache.getOrCompute(variant, delegate.getModuleVersionId(), getSources(), EXCLUDE_NONE, artifactResolver));
            }
            return result.build();
        }
    }
}
