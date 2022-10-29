package at.jku.isse.ecco.lsp.services;

import at.jku.isse.ecco.lsp.server.EccoLspServer;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.TextDocumentService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public class EccoTextDocumentService implements TextDocumentService {
    private EccoLspServer eccoLspServer;
    private Logger logger;

    public EccoTextDocumentService(EccoLspServer eccoLspServer) {
        this.eccoLspServer = eccoLspServer;
        this.logger = this.eccoLspServer.getLogger();
    }

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        logger.finer("Opened document " + params.getTextDocument().getUri());
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        logger.finest("Changed document " + params.getTextDocument().getUri());
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        logger.finer("Closed document " + params.getTextDocument().getUri());
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {
        logger.finer("Saved document " + params.getTextDocument().getUri());
    }

    @Override
    public CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbol(DocumentSymbolParams params) {
        logger.fine("Requested document symbols of " + params.getTextDocument().getUri());

        List<Either<SymbolInformation, DocumentSymbol>> symbols = new ArrayList<>();
        return CompletableFuture.completedFuture(symbols);
    }
}
