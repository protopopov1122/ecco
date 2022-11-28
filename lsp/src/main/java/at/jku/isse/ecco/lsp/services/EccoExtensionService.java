package at.jku.isse.ecco.lsp.services;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.lsp.domain.Document;
import at.jku.isse.ecco.lsp.domain.EccoDocument;
import at.jku.isse.ecco.lsp.extensions.*;
import at.jku.isse.ecco.lsp.server.EccoLspServer;
import at.jku.isse.ecco.lsp.util.Nodes;
import at.jku.isse.ecco.lsp.util.Pair;
import at.jku.isse.ecco.lsp.util.Positions;
import at.jku.isse.ecco.service.EccoService;
import org.eclipse.lsp4j.Color;
import org.eclipse.lsp4j.ColorInformation;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class EccoExtensionService implements EccoLspExtensions {

    private final EccoLspServer eccoLspServer;
    private final Logger logger;
    private final EccoServiceCommonState eccoServiceCommonState;

    public EccoExtensionService(final EccoLspServer eccoLspServer, final EccoServiceCommonState eccoServiceCommonState) {
        this.eccoLspServer = eccoLspServer;
        this.logger = this.eccoLspServer.getLogger();
        this.eccoServiceCommonState = eccoServiceCommonState;
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

    @Override
    public CompletableFuture<DocumentAssociationsResponse> documentAssociations(DocumentAssociationsRequest request) {
        try {
            final String documentUri = request.getDocumentUri();
            logger.fine("Requested document associations of " + documentUri);

            final Path documentInRepoPath = this.eccoServiceCommonState.getDocumentPathInRepo(documentUri);
            logger.finer("Document path in repo " + documentInRepoPath);

            InputStream is = new ByteArrayInputStream(request.getDocumentText().getBytes());
            final Document document = EccoDocument.load(this.eccoLspServer.getEccoService(), documentInRepoPath, is);

            final Map<Association, List<Range>> associationRanges = new HashMap<>();
            document.getRootNode().traverse(node -> {
                final Optional<Range> range = Positions.extractNodeRange(node);
                if (range.isEmpty()) {
                    return;
                }

                final Optional<Association> association = Nodes.getMappedNodeAssociation(node);
                if (association.isEmpty()) {
                    return;
                }

                List<Range> ranges;
                if (associationRanges.containsKey(association.get())) {
                    ranges = associationRanges.get(association.get());
                } else {
                    ranges = new ArrayList<>();
                    associationRanges.put(association.get(), ranges);
                }
                ranges.add(range.get());
            });

             final List<Pair<Association, Range>> associationRangeList = associationRanges
                     .entrySet().stream()
                     .flatMap(associationListEntry ->
                             Positions.rangesMerge(associationListEntry.getValue()).stream()
                                     .map(range -> new Pair<>(associationListEntry.getKey(), range)))
                     .toList();

             final List<Pair<Association, Range>> linearizedAssociationRanges = request.isCollapse()
                     ? Positions.linearizeRanges(associationRangeList,
                         Pair::getSecond,
                        (pair, newRange) -> new Pair<>(pair.getFirst(), newRange))
                     : associationRangeList;

            final List<DocumentAssociationsResponse.FragmentAssociation> fragmentAssociations = linearizedAssociationRanges.stream()
                    .flatMap(pair -> Positions.rangeSplitLines(pair.getSecond()).stream()
                            .map(range -> new Pair<>(pair.getFirst(),range)))
                    .map(associationRangePair ->
                            new DocumentAssociationsResponse.FragmentAssociation(associationRangePair.getSecond(),
                                    associationRangePair.getFirst().getAssociationString(),
                                    associationRangePair.getFirst().computeCondition().toString()))
                    .toList();

            final DocumentAssociationsResponse response = new DocumentAssociationsResponse(fragmentAssociations);

            return CompletableFuture.completedFuture(response);
        }  catch (Throwable ex) {
            logger.severe(ex.getMessage() + "\t" + Arrays.toString(ex.getStackTrace()));
            final ResponseError error = new ResponseError(ResponseErrorCode.InternalError, ex.getMessage(), null);
            return CompletableFuture.failedFuture(new ResponseErrorException(error));
        }
    }
}
