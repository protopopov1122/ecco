package at.jku.isse.ecco.lsp.extensions;

public class CommitRequest {
    private String workspaceUri;
    private String configuration;
    private String message;

    public CommitRequest() {}
    public String getWorkspaceUri() {
        return this.workspaceUri;
    }

    public String getConfiguration() {
        return this.configuration;
    }

    public String getMessage() {
        return this.message;
    }
}
