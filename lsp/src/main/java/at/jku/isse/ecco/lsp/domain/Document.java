package at.jku.isse.ecco.lsp.domain;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.lsp.util.Nodes;
import at.jku.isse.ecco.lsp.util.Positions;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.tree.RootNode;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public interface Document {

    Path getDocumentPath();
    RootNode getRootNode();

    default Set<Association> getAssociationsAt(Position position) {
        final Set<Association> associations = new HashSet<>();
        this.getRootNode().traverse(node -> {
            final Map<String, Object> nodeProperties = node.getProperties();
            if (!nodeProperties.containsKey(Positions.LINE_START) ||
                    !nodeProperties.containsKey(Positions.LINE_END)) {
                return;
            }

            final Range nodeRange = Positions.extractNodeRange(node);
            if (!Positions.rangeContains(nodeRange, position)) {
                return;
            }

            final Optional<Association> association = Nodes.getMappedNodeAssociation(node);
            if (!association.isEmpty()) {
                associations.add(association.get());
            }
        });

        return associations;
    }

    default Set<Node> getNodesFrom(Collection<? extends Association> associations) {
        final Set<String> associationIds = associations.stream()
                .map(association -> association.getId())
                .collect(Collectors.toSet());
        final Set<Node> nodes = new HashSet<>();
        this.getRootNode().traverse(node -> {
            final Optional<Association> association = Nodes.getMappedNodeAssociation(node);
            if (!association.isEmpty() && associationIds.contains(association.get().getId())) {
                nodes.add(node);
            }
        });

        return nodes;
    }
}
