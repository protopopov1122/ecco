package at.jku.isse.ecco.lsp.services;

import at.jku.isse.ecco.lsp.extensions.CheckoutRequest;
import at.jku.isse.ecco.lsp.extensions.CheckoutResponse;
import at.jku.isse.ecco.lsp.extensions.EccoLspExtensions;
import at.jku.isse.ecco.lsp.server.EccoLspServer;
import at.jku.isse.ecco.service.EccoService;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;

import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public class EccoExtensionService implements EccoLspExtensions {

    private final EccoLspServer eccoLspServer;
    private final Logger logger;

    public EccoExtensionService(final EccoLspServer eccoLspServer) {
        this.eccoLspServer = eccoLspServer;
        this.logger = this.eccoLspServer.getLogger();
    }

    @Override
    public CompletableFuture<CheckoutResponse> checkout(final CheckoutRequest request) {
        logger.fine("Requested ECCO configuration checkout: " + request.getConfiguration());

        try {
            final EccoService eccoService = this.eccoLspServer.getEccoService();
            eccoService.checkout(request.getConfiguration());
            return CompletableFuture.supplyAsync(CheckoutResponse::new);
        } catch (Throwable ex) {
            logger.severe(ex.getMessage() + "\t" + ex.getStackTrace());
            final ResponseError error = new ResponseError(ResponseErrorCode.InvalidRequest, ex.getMessage(), null);
            return CompletableFuture.failedFuture(new ResponseErrorException(error));
        }
    }
}
