package at.jku.isse.ecco.lsp.util;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.tree.Node;

import java.util.Optional;

public class Nodes {
    public static final String MAPPED = "mapped";

    public static Optional<Association> getMappedNodeAssociation(Node node) {
        final Artifact<?> artifact = node.getArtifact();
        if (artifact == null || !artifact.getProperties().containsKey(MAPPED)) {
            return Optional.empty();
        }

        final Artifact<?> mappedArtifact = (Artifact<?>) artifact.getProperties().get(MAPPED);
        if (mappedArtifact == null) {
            return Optional.empty();
        }

        final Association association = mappedArtifact.getContainingNode().getContainingAssociation();
        if (association == null) {
            return Optional.empty();
        }

        return Optional.of(association);
    }
}
