package at.jku.isse.ecco.lsp.domain;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.lsp.util.Nodes;
import at.jku.isse.ecco.lsp.util.Positions;
import at.jku.isse.ecco.module.Condition;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.module.ModuleRevision;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.tree.RootNode;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class EccoDocumentFeature implements DocumentFeature {

    private final Document document;
    private final FeatureRevision featureRevision;
    private final Set<Node> nodes;
    private List<Range> ranges;

    public EccoDocumentFeature(Document document, FeatureRevision featureRevision, Collection<? extends Node> nodes) {
        this.document = document;
        this.featureRevision = featureRevision;
        this.nodes = Set.copyOf(nodes);

        this.extractNodeRanges();
    }

    @Override
    public Document getDocument() {
        return this.document;
    }

    @Override
    public FeatureRevision getFeatureRevision() {
        return this.featureRevision;
    }

    @Override
    public List<Range> getRanges() {
        return Collections.unmodifiableList(this.ranges);
    }

    @Override
    public String toString() {
        return this.getDocument() + "::" +
                this.getFeatureRevision().getFeatureRevisionString();
    }

    private void extractNodeRanges() {
        this.ranges = Positions.extractNodeRanges(this.nodes);
    }

    public static Map<FeatureRevision, DocumentFeature> from(Document document) {
        final Map<FeatureRevision, Set<Node>> featureRevisionSetMap = new HashMap<>();

        document.getRootNode().traverse(node -> {
            final Optional<Association> associationOptional = Nodes.getMappedNodeAssociation(node);
            if (associationOptional.isEmpty()) {
                return;
            }

            final Association association = associationOptional.get();

            if (!(association instanceof Association.Op)) {
                throw new EccoException("Expected association " + association.getId() + " to be an instance of " + Association.Op.class);
            }

            final Association.Op associationOp = (Association.Op) association;
            final Condition condition = associationOp.computeCondition();
            final Map<Module, Collection<ModuleRevision>> conditionModules = condition.getModules();

            conditionModules.values()
                    .stream()
                    .flatMap(moduleRevisions -> moduleRevisions.stream())
                    .flatMap(moduleRevision -> Arrays.stream(moduleRevision.getPos()))
                    .forEach(featureRevision -> {
                        Set<Node> nodes = null;
                        if (featureRevisionSetMap.containsKey(featureRevision)) {
                            nodes = featureRevisionSetMap.get(featureRevision);
                        } else {
                            nodes = new HashSet<>();
                            featureRevisionSetMap.put(featureRevision, nodes);
                        }

                        nodes.add(node);
                    });
        });

        return featureRevisionSetMap.entrySet().stream()
                .map(featureRevisionSetEntry -> new EccoDocumentFeature(document, featureRevisionSetEntry.getKey(), featureRevisionSetEntry.getValue()))
                .collect(Collectors.toMap(EccoDocumentFeature::getFeatureRevision, eccoDocumentFeature -> eccoDocumentFeature));
    }

}
