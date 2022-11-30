package at.jku.isse.ecco.lsp.extensions;

import org.eclipse.lsp4j.Range;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class DocumentFeaturesResponse {

    private final List<FragmentFeatures> fragments;

    public static class FragmentFeatures {
        private final Range range;
        private final List<String> features;

        public FragmentFeatures(final Range range, final Collection<String> features) {
            this.range = range;
            this.features = new ArrayList<>(features);
        }
    }

    public DocumentFeaturesResponse(final Collection<FragmentFeatures> fragmentFeatures) {
        this.fragments = new ArrayList<>(fragmentFeatures);
    }
}
