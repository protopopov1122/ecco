package at.jku.isse.ecco.lsp.util;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.tree.Node;

import java.util.Optional;

public class Nodes {

    public static Optional<Association> getMappedNodeAssociation(final Node node) {
        final Artifact<?> artifact = node.getArtifact();
        if (artifact == null || !artifact.getProperties().containsKey(Properties.MAPPED)) {
            return Optional.empty();
        }

        final Artifact<?> mappedArtifact = (Artifact<?>) artifact.getProperties().get(Properties.MAPPED);
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
