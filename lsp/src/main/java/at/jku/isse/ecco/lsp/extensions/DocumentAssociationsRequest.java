package at.jku.isse.ecco.lsp.extensions;

public class DocumentAssociationsRequest {
    private String documentUri;
    private String documentText;
    private boolean collapse;

    public DocumentAssociationsRequest() {}

    public String getDocumentUri() {
        return this.documentUri;
    }

    public String getDocumentText() {
        return this.documentText;
    }

    public boolean isCollapse() {
        return this.collapse;
    }
}
