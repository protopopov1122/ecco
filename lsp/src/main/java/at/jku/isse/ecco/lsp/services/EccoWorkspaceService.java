package at.jku.isse.ecco.lsp.services;

import at.jku.isse.ecco.lsp.server.EccoLspServer;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.WorkspaceService;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public class EccoWorkspaceService implements WorkspaceService {
    private final EccoLspServer eccoLspServer;
    private final Logger logger;

    public EccoWorkspaceService(final EccoLspServer eccoLspServer) {
        this.eccoLspServer = eccoLspServer;
        this.logger = this.eccoLspServer.getLogger();
    }

    @Override
    public void didChangeConfiguration(final DidChangeConfigurationParams params) {
    }

    @Override
    public void didChangeWatchedFiles(final DidChangeWatchedFilesParams params) {
    }

    @Override
    public void didChangeWorkspaceFolders(DidChangeWorkspaceFoldersParams params) {
        logger.info("Workspace folders changed: added=" + params.getEvent().getAdded() + ", removed=" + params.getEvent().getRemoved());
        params.getEvent().getRemoved().forEach(workspaceFolder -> {
            final URI uri = URI.create(workspaceFolder.getUri());
            final Path path = Paths.get(uri);
            this.eccoLspServer.removeEccoServiceFor(path);
        });
        params.getEvent().getAdded().forEach(workspaceFolder -> {
            final URI uri = URI.create(workspaceFolder.getUri());
            final Path path = Paths.get(uri);
            this.eccoLspServer.addEccoServiceFor(path);
        });
    }
}
