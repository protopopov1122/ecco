package at.jku.isse.ecco.lsp.domain;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.lsp.util.Nodes;
import at.jku.isse.ecco.lsp.util.Positions;
import at.jku.isse.ecco.module.Condition;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.module.ModuleRevision;
import at.jku.isse.ecco.tree.Node;
import org.eclipse.lsp4j.Range;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class EccoDocumentFeature implements DocumentFeature {

    private final Document document;
    private final FeatureRevision featureRevision;
    private final List<Node> nodes;
    private final List<Range> ranges;

    public EccoDocumentFeature(final Document document, final FeatureRevision featureRevision, final Collection<? extends Node> nodes) {
        this.document = document;
        this.featureRevision = featureRevision;
        this.nodes = new ArrayList<>(nodes);

        this.ranges = Positions.extractNodeRanges(nodes);
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

    public static Map<FeatureRevision, DocumentFeature> from(final Document document) {
        final Map<FeatureRevision, List<Node>> featureRevisionSetMap = new HashMap<>();

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
                    .flatMap(Collection<ModuleRevision>::stream)
                    .flatMap(moduleRevision -> Arrays.stream(moduleRevision.getPos()))
                    .forEach(featureRevision -> {
                        final List<Node> nodes = featureRevisionSetMap.computeIfAbsent(featureRevision, rev -> new ArrayList<>());
                        nodes.add(node);
                    });
        });

        return featureRevisionSetMap.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> new EccoDocumentFeature(document, entry.getKey(), entry.getValue())));
    }

}
