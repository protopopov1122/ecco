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
import java.util.logging.Level;
import java.util.logging.Logger;

public class EccoLspServer implements LanguageServer, LanguageClientAware {
    private TextDocumentService textDocumentService;
    private WorkspaceService workspaceService;
    private LanguageClient languageClient;
    private int exitCode;
    private EccoService eccoService;
    private Logger logger;

    public EccoLspServer(Logger logger) {
        this.languageClient = null;
        this.exitCode = -1;
        this.eccoService = null;
        this.logger = logger;

        logger.fine("Instantiating LSP services");
        this.textDocumentService = new EccoTextDocumentService(this);
        this.workspaceService = new EccoWorkspaceService(this);
    }

    public EccoService getEccoService() {
        return this.eccoService;
    }

    public LanguageClient getLanguageClient() {
        return  this.languageClient;
    }

    public Logger getLogger() {
        return this.logger;
    }

    @Override
    public void connect(LanguageClient client) {
        this.languageClient = client;
    }

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        logger.info("Starting ECCO LSP server initialization");

        final List<WorkspaceFolder> workspaceFolders = params.getWorkspaceFolders();
        if (workspaceFolders != null && !workspaceFolders.isEmpty()) {
            final String workspaceFolderUri = workspaceFolders.get(0).getUri(); // TODO Deal with other workspace folders
            final Path workspaceFolderPath;
            try {
                workspaceFolderPath = Paths.get(new URI(workspaceFolderUri));
            } catch (Throwable e) {
                return CompletableFuture.failedFuture(e);
            }
            this.getLogger().info("Instantiating ECCO service in " + workspaceFolderPath.toString());

            this.eccoService = new EccoService(workspaceFolderPath);
        } else {
            logger.severe("Unable to detect workspace folder in initialization parameters");
            ResponseError error = new ResponseError(ResponseErrorCode.InvalidParams, "Ecco LSP server initialization expects non-empty workspace", null);
            return CompletableFuture.failedFuture(new ResponseErrorException(error));
        }

        try {
            logger.fine("Opening ECCO repository");
            this.eccoService.open();
        } catch (Throwable ex) {
            logger.log(Level.SEVERE, "Opening ECCO repository failed with an exception", ex);
            ResponseError error = new ResponseError(ResponseErrorCode.InternalError, "Opening ECCO repository failed", null);
            return CompletableFuture.failedFuture(new ResponseErrorException(error));
        }

        logger.fine("Instantiating LSP initialize result");
        InitializeResult result = new InitializeResult(new ServerCapabilities());
        result.getCapabilities().setTextDocumentSync(TextDocumentSyncKind.Full);
        result.getCapabilities().setDocumentSymbolProvider(new DocumentSymbolOptions());
        result.getCapabilities().setDocumentHighlightProvider(new DocumentHighlightOptions());
        result.getCapabilities().setHoverProvider(new HoverOptions());
        result.getCapabilities().setWorkspaceSymbolProvider(new WorkspaceSymbolOptions(true));
        return CompletableFuture.completedFuture(result);
    }

    @Override
    public CompletableFuture<Object> shutdown() {
        this.getLogger().fine("Shutdown requested");
        this.eccoService.close();
        this.exitCode = 0;
        return CompletableFuture.supplyAsync(Object::new);
    }

    @Override
    public void exit() {
        this.getLogger().fine("Exiting with code " + this.exitCode);
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
