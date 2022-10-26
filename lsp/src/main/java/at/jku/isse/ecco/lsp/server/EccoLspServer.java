package at.jku.isse.ecco.lsp.server;

import at.jku.isse.ecco.lsp.services.EccoTextDocumentService;
import at.jku.isse.ecco.lsp.services.EccoWorkspaceService;
import at.jku.isse.ecco.service.EccoService;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.eclipse.lsp4j.services.*;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class EccoLspServer implements LanguageServer, LanguageClientAware {
    private TextDocumentService textDocumentService;
    private WorkspaceService workspaceService;
    private LanguageClient languageClient;
    private int exitCode;
    private EccoService eccoService;

    public EccoLspServer() {
        this.textDocumentService = null;
        this.workspaceService = null;
        this.languageClient = null;
        this.exitCode = -1;
        this.eccoService = null;
    }

    public EccoService getEccoService() {
        return this.eccoService;
    }

    public LanguageClient getLanguageClient() {
        return  this.languageClient;
    }

    @Override
    public void connect(LanguageClient client) {
        this.languageClient = client;
    }

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        final List<WorkspaceFolder> workspaceFolders = params.getWorkspaceFolders();
        if (workspaceFolders != null && !workspaceFolders.isEmpty()) {
            final String workspaceFolderUri = workspaceFolders.get(0).getUri(); // TODO Deal with other workspace folders
            final Path workspaceFolderPath;
            try {
                workspaceFolderPath = Paths.get(new URI(workspaceFolderUri));
            } catch (Throwable e) {
                return CompletableFuture.failedFuture(e);
            }

            this.eccoService = new EccoService(workspaceFolderPath);
        } else {
            ResponseError error = new ResponseError(ResponseErrorCode.InvalidParams, "Ecco LSP server initialization expects non-empty workspace", null);
            return CompletableFuture.failedFuture(new ResponseErrorException(error));
        }

        this.textDocumentService = new EccoTextDocumentService(this);
        this.workspaceService = new EccoWorkspaceService(this);

        InitializeResult result = new InitializeResult(new ServerCapabilities());
        return CompletableFuture.completedFuture(result);
    }

    @Override
    public CompletableFuture<Object> shutdown() {
        this.exitCode = 0;
        return CompletableFuture.supplyAsync(Object::new);
    }

    @Override
    public void exit() {
        System.exit(this.exitCode);
    }

    @Override
    public TextDocumentService getTextDocumentService() {
        return this.textDocumentService;
    }

    @Override
    public WorkspaceService getWorkspaceService() {
        return this.workspaceService;
    }
}
