package at.jku.isse.ecco.lsp.extensions;

public class CheckoutRequest {
    private String workspaceUri;
    private String configuration;

    public CheckoutRequest() {}

    public String getWorkspaceUri() {
        return this.workspaceUri;
    }

    public String getConfiguration() {
        return this.configuration;
    }
}
