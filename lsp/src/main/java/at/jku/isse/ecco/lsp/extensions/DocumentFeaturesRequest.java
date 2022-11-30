package at.jku.isse.ecco.lsp.extensions;

import java.util.Collections;
import java.util.List;

public class DocumentFeaturesRequest {
    private String documentUri;
    private List<String> requestedFeatures;

    public DocumentFeaturesRequest() {}

    public String getDocumentUri() {
        return this.documentUri;
    }

    public List<String> getRequestedFeatures() {
        return this.requestedFeatures != null
                ? Collections.unmodifiableList(this.requestedFeatures)
                : null;
    }
}
