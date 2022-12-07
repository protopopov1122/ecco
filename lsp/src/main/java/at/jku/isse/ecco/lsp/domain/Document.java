package at.jku.isse.ecco.lsp.domain;

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

    default Set<Node> getNodesAt(final Position position) {
        final Set<Node> nodes = new HashSet<>();
        this.getRootNode().traverse(node -> {
            final Optional<Range> nodeRange = Positions.extractNodeRange(node);
            if (nodeRange.isEmpty()) {
                return;
            }

            if (Positions.rangeContains(nodeRange.get(), position)) {
                nodes.add(node);
            }
        });

        return nodes;
    }

    default Set<Association> getAssociationsOf(final Collection<? extends Node> nodes) {
        final Set<Association> associations = new HashSet<>();
        this.getRootNode().traverse(node -> {
            if (!nodes.contains(node)) {
                return;
            }

            final Optional<Association> association = Nodes.getMappedNodeAssociation(node);
            association.ifPresent(associations::add);
        });

        return associations;
    }

    default  Map<Association, Set<Node>> getNodesFor(final Collection<? extends Association> associations) {
        final Map<Association, Set<Node>> nodeMap = new HashMap<>();
        final Set<String> associationIds = associations.stream()
                .map(Association::getId)
                .collect(Collectors.toSet());

        this.getRootNode().traverse(node -> {
            final Optional<Association> association = Nodes.getMappedNodeAssociation(node);
            if (association.isPresent() && associationIds.contains(association.get().getId())) {
                final Set<Node> nodes = nodeMap.computeIfAbsent(association.get(), assoc -> new HashSet<>());
                nodes.add(node);
            }
        });

        return nodeMap;
    }
}
