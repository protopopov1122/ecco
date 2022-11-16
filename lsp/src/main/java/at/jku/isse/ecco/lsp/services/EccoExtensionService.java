package at.jku.isse.ecco.lsp.services;

import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.lsp.extensions.*;
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

    @Override
    public CompletableFuture<CommitResponse> commit(final CommitRequest request) {
        logger.fine("Requested ECCO commit: message=\"\"" + request.getMessage() + "\"; configuration=\"" + request.getConfiguration() + "\"");

        try {
            final EccoService eccoService = this.eccoLspServer.getEccoService();
            Commit commit = null;
            if (request.getConfiguration().length() > 0) {
                commit = eccoService.commit(request.getMessage(), request.getConfiguration());
            } else {
                commit = eccoService.commit(request.getMessage());
            }

            return CompletableFuture.completedFuture(new CommitResponse(
                    commit.getId(), commit.getDate(), commit.getCommitMassage(), commit.getConfiguration().getConfigurationString()));
        } catch (Throwable ex) {
            logger.severe(ex.getMessage() + "\t" + ex.getStackTrace());
            final ResponseError error = new ResponseError(ResponseErrorCode.InvalidRequest, ex.getMessage(), null);
            return CompletableFuture.failedFuture(new ResponseErrorException(error));
        }
    }
}
