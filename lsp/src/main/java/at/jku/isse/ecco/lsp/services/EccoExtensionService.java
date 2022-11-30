package at.jku.isse.ecco.lsp.services;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.lsp.domain.Document;
import at.jku.isse.ecco.lsp.domain.DocumentFeature;
import at.jku.isse.ecco.lsp.domain.EccoDocument;
import at.jku.isse.ecco.lsp.domain.EccoDocumentFeature;
import at.jku.isse.ecco.lsp.extensions.*;
import at.jku.isse.ecco.lsp.server.EccoLspServer;
import at.jku.isse.ecco.lsp.util.Nodes;
import at.jku.isse.ecco.lsp.util.Pair;
import at.jku.isse.ecco.lsp.util.Positions;
import at.jku.isse.ecco.module.Condition;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.module.ModuleRevision;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.tree.Node;
import org.eclipse.lsp4j.Color;
import org.eclipse.lsp4j.ColorInformation;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
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
            final URI workspaceUri = URI.create(request.getWorkspaceUri());
            final Path workspacePath = Paths.get(workspaceUri);
            final EccoService eccoService = this.eccoLspServer.getEccoServiceFor(workspacePath);
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
            final URI workspaceUri = URI.create(request.getWorkspaceUri());
            final Path workspacePath = Paths.get(workspaceUri);

            final EccoService eccoService = this.eccoLspServer.getEccoServiceFor(workspacePath);
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
    public CompletableFuture<InfoResponse> info(final InfoRequest request) {
        logger.fine("Requested current ECCO repository configuration");
        try {
            final URI workspaceUri = URI.create(request.getWorkspaceUri());
            final Path workspacePath = Paths.get(workspaceUri);

            final EccoService eccoService = this.eccoLspServer.getEccoServiceFor(workspacePath);
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
    public CompletableFuture<DocumentAssociationsResponse> getDocumentAssociations(final DocumentAssociationsRequest request) {
        try {
            final String documentUri = request.getDocumentUri();
            logger.fine("Requested document associations of " + documentUri);

            final Path documentInRepoPath = this.eccoServiceCommonState.getDocumentPathInRepo(documentUri);
            logger.finer("Document path in repo " + documentInRepoPath);

            final Document document = this.eccoServiceCommonState.documentLoader(request.getDocumentUri()).get();

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

             final Comparator<Position> positionComparator = Positions.PositionComparator.Instance;
             final List<DocumentAssociationsResponse.FragmentAssociation> fragmentAssociations = associationRangeList
                    .stream()
                    .sorted((el1, el2) ->
                            positionComparator.compare(el1.getSecond().getStart(), el2.getSecond().getStart()))
                    .flatMap(pair -> Positions.rangeSplitLines(pair.getSecond()).stream()
                            .map(range -> new Pair<>(pair.getFirst(),range)))
                    .map(associationRangePair ->
                            new DocumentAssociationsResponse.FragmentAssociation(associationRangePair.getSecond(),
                                    new AssociationInfo(associationRangePair.getFirst().getAssociationString(),
                                                        associationRangePair.getFirst().computeCondition().toString())))
                    .toList();

            final DocumentAssociationsResponse response = new DocumentAssociationsResponse(fragmentAssociations);

            return CompletableFuture.completedFuture(response);
        }  catch (Throwable ex) {
            logger.severe(ex.getMessage() + "\t" + Arrays.toString(ex.getStackTrace()));
            final ResponseError error = new ResponseError(ResponseErrorCode.InternalError, ex.getMessage(), null);
            return CompletableFuture.failedFuture(new ResponseErrorException(error));
        }
    }

    @Override
    public CompletableFuture<DocumentFeaturesResponse> getDocumentFeatures(final DocumentFeaturesRequest request) {
        try {
            final String documentUri = request.getDocumentUri();
            logger.fine("Requested document features " + request.getRequestedFeatures() + " of " + documentUri);

            final Path documentInRepoPath = this.eccoServiceCommonState.getDocumentPathInRepo(documentUri);
            logger.finer("Document path in repo " + documentInRepoPath);

            final Document document = this.eccoServiceCommonState.documentLoader(request.getDocumentUri()).get();

            final Map<FeatureRevision, DocumentFeature> documentFeatures = EccoDocumentFeature.from(document);

            final Comparator<Position> positionComparator = Positions.PositionComparator.Instance;

            final List<Pair<Range, Set<FeatureRevision>>> documentFeatureFragments = new ArrayList<>();
            documentFeatures.entrySet().stream()
                    .filter(featureRevisionDocumentFeatureEntry -> request.getRequestedFeatures() == null ||
                            request.getRequestedFeatures().contains(featureRevisionDocumentFeatureEntry.getKey().getFeature().toString()) ||
                            request.getRequestedFeatures().contains(featureRevisionDocumentFeatureEntry.getKey().getFeatureRevisionString()))
                    .map(entry -> new Pair<>(entry.getKey(), entry.getValue().getRanges()))
                    .flatMap(pair ->
                        pair.getSecond().stream()
                                .map(range -> new Pair<>(range, (Set<FeatureRevision>) new HashSet<>(Arrays.asList(pair.getFirst())))))
                    .sorted((p1, p2) -> positionComparator.compare(p1.getFirst().getStart(), p2.getFirst().getStart()))
                    .forEach(pair -> {
                        if (documentFeatureFragments.isEmpty()) {
                            documentFeatureFragments.add(pair);
                            return;
                        }

                        final Range range = pair.getFirst();
                        final Set<FeatureRevision> featureRevisions = pair.getSecond();

                        final var prevElement = documentFeatureFragments.get(documentFeatureFragments.size() - 1);
                        final Range prevRange = prevElement.getFirst();

                        final int startComparison = positionComparator.compare(prevRange.getStart(), range.getStart());

                        if (startComparison < 0) {
                            final int overlapComparison = positionComparator.compare(prevRange.getEnd(), range.getStart());
                            if (overlapComparison > 0) {
                                final Set<FeatureRevision> mergedFeatures = new HashSet<>();
                                mergedFeatures.addAll(prevElement.getSecond());
                                mergedFeatures.addAll(featureRevisions);

                                documentFeatureFragments.remove(documentFeatureFragments.size() - 1);
                                final int endComparison = positionComparator.compare(prevRange.getEnd(), range.getEnd());

                                documentFeatureFragments.add(new Pair<>(
                                        new Range(prevRange.getStart(), range.getStart()),
                                        prevElement.getSecond()));
                                if (endComparison > 0) {
                                    documentFeatureFragments.add(new Pair<>(
                                            new Range(range.getStart(), range.getEnd()),
                                            mergedFeatures));
                                    documentFeatureFragments.add(new Pair<>(
                                            new Range(range.getEnd(), prevRange.getEnd()),
                                            prevElement.getSecond()));
                                } else if (endComparison < 0) {
                                    documentFeatureFragments.add(new Pair<>(
                                            new Range(range.getStart(), prevRange.getEnd()),
                                            mergedFeatures));
                                    documentFeatureFragments.add(new Pair<>(
                                            new Range(prevRange.getEnd(), range.getEnd()),
                                            featureRevisions));
                                } else {
                                    documentFeatureFragments.add(new Pair<>(
                                            new Range(range.getStart(), range.getEnd()),
                                            mergedFeatures));
                                }
                            } else {
                                documentFeatureFragments.add(pair);
                            }
                        } else if (startComparison == 0) {
                            final Set<FeatureRevision> mergedFeatures = new HashSet<>();
                            mergedFeatures.addAll(prevElement.getSecond());
                            mergedFeatures.addAll(featureRevisions);

                            documentFeatureFragments.remove(documentFeatureFragments.size() - 1);

                            final int endComparison = positionComparator.compare(prevRange.getEnd(), range.getEnd());
                            if (endComparison < 0) {
                                documentFeatureFragments.add(new Pair<>(
                                        prevRange,
                                        mergedFeatures));
                                documentFeatureFragments.add(new Pair<>(
                                        new Range(prevRange.getEnd(), range.getEnd()),
                                        featureRevisions));
                            } else if (endComparison > 0) {
                                documentFeatureFragments.add(new Pair<>(
                                        range,
                                        mergedFeatures));
                                documentFeatureFragments.add(new Pair<>(
                                        new Range(range.getEnd(), prevRange.getEnd()),
                                        prevElement.getSecond()));
                            } else {
                                documentFeatureFragments.add(new Pair<>(
                                        range,
                                        mergedFeatures));
                            }
                        } else {
                            final Set<FeatureRevision> mergedFeatures = new HashSet<>();
                            mergedFeatures.addAll(prevElement.getSecond());
                            mergedFeatures.addAll(featureRevisions);

                            documentFeatureFragments.remove(documentFeatureFragments.size() - 1);

                            documentFeatureFragments.add(new Pair<>(
                                    new Range(range.getStart(), prevRange.getStart()),
                                    featureRevisions));

                            final int endComparison = positionComparator.compare(prevRange.getEnd(), range.getEnd());
                            if (endComparison < 0) {
                                documentFeatureFragments.add(new Pair<>(
                                        prevRange,
                                        mergedFeatures));
                                documentFeatureFragments.add(new Pair<>(
                                        new Range(prevRange.getEnd(), range.getEnd()),
                                        featureRevisions));
                            } else if (endComparison > 0) {
                                documentFeatureFragments.add(new Pair<>(
                                        new Range(prevRange.getStart(), range.getEnd()),
                                        mergedFeatures));
                                documentFeatureFragments.add(new Pair<>(
                                        new Range(range.getEnd(), prevRange.getEnd()),
                                        prevElement.getSecond()));
                            } else {
                                documentFeatureFragments.add(new Pair<>(
                                        prevRange,
                                        mergedFeatures));
                            }
                        }

                        while (documentFeatureFragments.size() > 1) {
                            final var lastFragment = documentFeatureFragments.get(documentFeatureFragments.size() - 1);
                            final var prevLastFragment = documentFeatureFragments.get(documentFeatureFragments.size() - 2);
                            final var fragmentRangeMerge = Positions.rangesMerge(lastFragment.getFirst(), prevLastFragment.getFirst());
                            if (lastFragment.getSecond().equals(prevLastFragment.getSecond()) &&
                                    fragmentRangeMerge.isPresent()) {
                                documentFeatureFragments.remove(documentFeatureFragments.size() - 1);
                                documentFeatureFragments.remove(documentFeatureFragments.size() - 1);
                                documentFeatureFragments.add(new Pair<>(
                                        fragmentRangeMerge.get(),
                                        lastFragment.getSecond()));
                            } else {
                                break;
                            }
                        }
                    });

            final List<DocumentFeaturesResponse.FragmentFeatures> fragmentFeatures = documentFeatureFragments.stream()
                    .flatMap(pair ->
                            Positions.rangeSplitLines(pair.getFirst()).stream()
                                    .map(range -> new Pair<>(range, pair.getSecond())))
                    .map(pair -> new DocumentFeaturesResponse.FragmentFeatures(pair.getFirst(),
                            pair.getSecond().stream()
                                    .map(FeatureRevision::toString)
                                    .collect(Collectors.toList())))
                    .collect(Collectors.toList());

            return CompletableFuture.completedFuture(new DocumentFeaturesResponse(fragmentFeatures));
        }  catch (Throwable ex) {
            logger.severe(ex.getMessage() + "\t" + Arrays.toString(ex.getStackTrace()));
            final ResponseError error = new ResponseError(ResponseErrorCode.InternalError, ex.getMessage(), null);
            return CompletableFuture.failedFuture(new ResponseErrorException(error));
        }
    }

    @Override
    public CompletableFuture<SettingsState> getSettings(final SettingsRequest request) {
        logger.fine("Requested server settings");
        return CompletableFuture.completedFuture(new SettingsState(
                this.eccoServiceCommonState.getSettings().getIgnoreColumnsForColoring()));
    }

    @Override
    public CompletableFuture<SettingsState> updateSettings(final SettingsState request) {
        final Settings settings = this.eccoServiceCommonState.getSettings();
        settings.setIgnoreColumnsForColoring(request.getIgnoreColumnsForColoring());

        logger.fine("Requested server setting update: " + settings.toString());

        return CompletableFuture.completedFuture(new SettingsState(
                settings.getIgnoreColumnsForColoring()));
    }
}
