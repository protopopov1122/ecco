package at.jku.isse.ecco.lsp.services;

import at.jku.isse.ecco.lsp.server.EccoLspServer;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.WorkspaceService;

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
    public CompletableFuture<Either<List<? extends SymbolInformation>, List<? extends WorkspaceSymbol>>> symbol(final WorkspaceSymbolParams params) {
        logger.fine("Requested workspace symbols");

        List<WorkspaceSymbol> symbols = List.of();
        return  CompletableFuture.completedFuture(Either.forRight(symbols));
    }

    @Override
    public CompletableFuture<WorkspaceSymbol> resolveWorkspaceSymbol(final WorkspaceSymbol workspaceSymbol) {
        logger.fine("Resolve workspace symbol " + workspaceSymbol.toString());
        return  CompletableFuture.completedFuture(workspaceSymbol);
    }
}
