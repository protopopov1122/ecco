package at.jku.isse.ecco.lsp.extensions;

import org.eclipse.lsp4j.Range;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class DocumentAssociationsResponse {

    private final List<FragmentAssociation> fragments;

    public static class FragmentAssociation {
        private final Range range;
        private final String association;
        private final String associationCondition;

        public FragmentAssociation(final Range range, final String association, final String associationCondition) {
            this.range = range;
            this.association = association;
            this.associationCondition = associationCondition;
        }
    }

    public DocumentAssociationsResponse(Collection<FragmentAssociation> fragmentAssociations) {
        this.fragments = new ArrayList<>(fragmentAssociations);
    }
}
