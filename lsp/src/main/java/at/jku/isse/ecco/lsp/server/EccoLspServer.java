package at.jku.isse.ecco.lsp.server;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.lsp.extensions.CheckoutRequest;
import at.jku.isse.ecco.lsp.extensions.EccoLspExtensions;
import at.jku.isse.ecco.lsp.services.EccoExtensionService;
import at.jku.isse.ecco.lsp.services.EccoServiceCommonState;
import at.jku.isse.ecco.lsp.services.EccoTextDocumentService;
import at.jku.isse.ecco.lsp.services.EccoWorkspaceService;
import at.jku.isse.ecco.service.EccoService;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.eclipse.lsp4j.jsonrpc.services.JsonDelegate;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.eclipse.lsp4j.services.*;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

public class EccoLspServer implements LanguageServer, LanguageClientAware {
    private final TextDocumentService textDocumentService;
    private final WorkspaceService workspaceService;
    private final EccoLspExtensions eccoLspExtensions;
    private LanguageClient languageClient;
    private int exitCode;
    private final Map<Path, EccoService> eccoServiceWorkspaces;
    private final Logger logger;

    public EccoLspServer(final Logger logger) {
        this.languageClient = null;
        this.exitCode = -1;
        this.eccoServiceWorkspaces = new HashMap<>();
        this.logger = logger;

        logger.fine("Instantiating LSP services");
        final EccoServiceCommonState eccoServiceCommonState = new EccoServiceCommonState(this);
        this.textDocumentService = new EccoTextDocumentService(this, eccoServiceCommonState);
        this.workspaceService = new EccoWorkspaceService(this);
        this.eccoLspExtensions = new EccoExtensionService(this, eccoServiceCommonState);
    }

    public EccoService getEccoServiceFor(final Path documentPath) {
        for (final var serviceForWorkspace : this.eccoServiceWorkspaces.entrySet()) {
            if (documentPath.startsWith(serviceForWorkspace.getKey())) {
                return serviceForWorkspace.getValue();
            }
        }
        throw new EccoException("Cannot find ECCO service for workspace that contains " + documentPath);
    }

    public LanguageClient getLanguageClient() {
        return  this.languageClient;
    }

    public Logger getLogger() {
        return this.logger;
    }

    public void removeEccoServiceFor(final Path workspacePath) {
        if (this.eccoServiceWorkspaces.containsKey(workspacePath)) {
            this.getLogger().info("Stopping ECCO service for " + workspacePath);
            final EccoService eccoService = this.eccoServiceWorkspaces.get(workspacePath);
            eccoService.close();
            this.eccoServiceWorkspaces.remove(workspacePath);
        }
    }

    public void addEccoServiceFor(final Path workspacePath) {
        if (workspacePath.resolve(".ecco").toFile().exists()) {
            if (!this.eccoServiceWorkspaces.containsKey(workspacePath)) {
                this.getLogger().info("Instantiating ECCO service in " + workspacePath);
                final EccoService eccoService = new EccoService(workspacePath);
                eccoService.open();
                this.eccoServiceWorkspaces.put(workspacePath, eccoService);
            }
        }
    }

    @Override
    public void connect(final LanguageClient client) {
        this.languageClient = client;
    }

    @Override
    public CompletableFuture<InitializeResult> initialize(final InitializeParams params) {
        logger.info("Starting ECCO LSP server initialization");

        final List<WorkspaceFolder> workspaceFolders = params.getWorkspaceFolders();
        if (workspaceFolders != null && !workspaceFolders.isEmpty()) {
            try {
                for (final WorkspaceFolder workspaceFolder : workspaceFolders) {
                    final String workspaceFolderUri = workspaceFolder.getUri();
                    final Path workspaceFolderPath = Paths.get(new URI(workspaceFolderUri));
                    if (workspaceFolderPath.resolve(".ecco").toFile().exists()) {
                        this.getLogger().info("Instantiating ECCO service in " + workspaceFolderPath);
                        this.eccoServiceWorkspaces.put(workspaceFolderPath, new EccoService(workspaceFolderPath));
                    }
                }
            } catch (Throwable e) {
                return CompletableFuture.failedFuture(e);
            }
        } else {
            logger.severe("Unable to detect workspace folder in initialization parameters");
            ResponseError error = new ResponseError(ResponseErrorCode.InvalidParams, "Ecco LSP server initialization expects non-empty workspace", null);
            return CompletableFuture.failedFuture(new ResponseErrorException(error));
        }

        try {
            logger.fine("Opening ECCO repositories");
            for (final EccoService eccoService : this.eccoServiceWorkspaces.values()) {
                eccoService.open();
            }
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
        result.getCapabilities().setColorProvider(new ColorProviderOptions());

        final WorkspaceServerCapabilities workspaceServerCapabilities = new WorkspaceServerCapabilities();
        final WorkspaceFoldersOptions workspaceFoldersOptions = new WorkspaceFoldersOptions();
        workspaceFoldersOptions.setChangeNotifications(true);
        workspaceServerCapabilities.setWorkspaceFolders(workspaceFoldersOptions);
        result.getCapabilities().setWorkspace(workspaceServerCapabilities);
        return CompletableFuture.completedFuture(result);
    }

    @Override
    public CompletableFuture<Object> shutdown() {
        this.getLogger().fine("Shutdown requested");
        for (final EccoService eccoService : this.eccoServiceWorkspaces.values()) {
            eccoService.close();
        }
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

    @JsonDelegate
    public EccoLspExtensions getEccoLspExtensions() {
        return this.eccoLspExtensions;
    }
}
