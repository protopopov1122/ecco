package at.jku.isse.ecco.lsp.services;

import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.lsp.extensions.*;
import at.jku.isse.ecco.lsp.server.EccoLspServer;
import at.jku.isse.ecco.service.EccoService;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import java.util.stream.Collectors;

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
        final String configuration = request.getConfiguration();
        final String message = request.getMessage();
        logger.fine("Requested ECCO commit: message=\"\"" + message + "\"; configuration=\"" + configuration + "\"");

        try {
            final EccoService eccoService = this.eccoLspServer.getEccoService();
            final Commit commit = configuration.length() > 0
                    ? eccoService.commit(message, configuration)
                    : eccoService.commit(message);

            return CompletableFuture.completedFuture(new CommitResponse(
                    commit.getId(), commit.getDate(), commit.getCommitMassage(), commit.getConfiguration().getConfigurationString()));
        } catch (Throwable ex) {
            logger.severe(ex.getMessage() + "\t" + ex.getStackTrace());
            final ResponseError error = new ResponseError(ResponseErrorCode.InvalidRequest, ex.getMessage(), null);
            return CompletableFuture.failedFuture(new ResponseErrorException(error));
        }
    }

    @Override
    public CompletableFuture<InfoResponse> info(InfoRequest request) {
        logger.fine("Requested current ECCO repository configuration");
        try {
            final EccoService eccoService = this.eccoLspServer.getEccoService();
            final String configuration = eccoService.getConfigStringFromFile(eccoService.getBaseDir());

            final List<InfoResponse.CommitInfo> commits = eccoService.getCommits()
                    .stream()
                    .map(commit -> new InfoResponse.CommitInfo(
                            commit.getId(), commit.getCommitMassage(), commit.getConfiguration().getConfigurationString(), commit.getDate()))
                    .collect(Collectors.toList());

            final List<InfoResponse.FeatureInfo> features = eccoService.getRepository().getFeatures()
                    .stream()
                    .map(feature -> new InfoResponse.FeatureInfo(
                            feature.getId(), feature.getName(), feature.getDescription(),
                            feature.getRevisions().stream().map(featureRevision -> featureRevision.getFeatureRevisionString()).collect(Collectors.toList())))
                    .collect(Collectors.toList());

            return CompletableFuture.completedFuture(new InfoResponse(eccoService.getBaseDir().toString(), configuration, commits, features));
        } catch (Throwable ex) {
            logger.severe(ex.getMessage() + "\t" + ex.getStackTrace());
            final ResponseError error = new ResponseError(ResponseErrorCode.InvalidRequest, ex.getMessage(), null);
            return CompletableFuture.failedFuture(new ResponseErrorException(error));
        }
    }
}
