package at.jku.isse.ecco.lsp.services;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.lsp.server.EccoLspServer;
import at.jku.isse.ecco.lsp.util.Pair;
import at.jku.isse.ecco.module.Condition;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.module.ModuleRevision;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.tree.RootNode;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.eclipse.lsp4j.services.TextDocumentService;

import java.net.URI;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BinaryOperator;
import java.util.logging.Logger;
import java.util.stream.Collector;
import java.util.stream.Collectors;

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

        final URI documentUri = URI.create(params.getTextDocument().getUri());
        final Path documentPath = Path.of(documentUri.getPath());

        final EccoService eccoService = this.eccoLspServer.getEccoService();
        final Path repoBasePath = eccoService.getBaseDir();
        final Path documentInRepoPath = repoBasePath.relativize(documentPath);
        logger.finer("Document path in repo " + documentInRepoPath.toString());

        List<Either<SymbolInformation, DocumentSymbol>> symbols = null;
        try {
            final RootNode documentRootNode = eccoService.map(List.of(documentInRepoPath));
            final Map<FeatureRevision, Set<Node>> featureRevisionSetMap = this.extractFeatureRevisions(documentRootNode);
            logger.finer("Identified feature revisions in document " + params.getTextDocument().getUri() + ": " + featureRevisionSetMap.keySet());

            final Map<FeatureRevision, Range> featureRevisionRangeMap = featureRevisionSetMap.entrySet()
                    .stream()
                    .map(featureRevisionSetEntry ->
                            new Pair<FeatureRevision, Optional<Range>>(
                                    featureRevisionSetEntry.getKey(),
                                    this.extractRangeFromNodes(featureRevisionSetEntry.getValue())
                            )
                    )
                    .filter(featureRevisionOptionalPair -> !featureRevisionOptionalPair.getSecond().isEmpty())
                    .collect(Collectors.toMap(
                            Pair::getFirst,
                            featureRevisionOptionalPair -> featureRevisionOptionalPair.getSecond().get()));

            logger.finest("Identified feature revision source ranges in document " + params.getTextDocument().getUri() + ": " + featureRevisionRangeMap);

            symbols = featureRevisionRangeMap.entrySet()
                        .stream()
                        .map(featureRevisionRangeEntry -> {
                            final DocumentSymbol documentSymbol = new DocumentSymbol(
                                    featureRevisionRangeEntry.getKey().getFeatureRevisionString(),
                                    SymbolKind.Object,
                                    featureRevisionRangeEntry.getValue(),
                                    featureRevisionRangeEntry.getValue());
                            return Either.<SymbolInformation, DocumentSymbol>forRight(documentSymbol);
                        })
                        .collect(Collectors.toList());
        }  catch (Throwable ex) {
            logger.severe(ex.getMessage() + "\t" + ex.getStackTrace());
            ResponseError error = new ResponseError(ResponseErrorCode.InternalError, ex.getMessage(), null);
            return CompletableFuture.failedFuture(new ResponseErrorException(error));
        }

        return CompletableFuture.completedFuture(symbols);
    }

    private Map<FeatureRevision, Set<Node>> extractFeatureRevisions(Node root) {
        final Map<FeatureRevision, Set<Node>> featureRevisionSetMap = new HashMap<>();

        root.traverse(node -> {
            final Artifact<?> artifact = node.getArtifact();
            if (artifact == null || !artifact.getProperties().containsKey("mapped")) {
                return;
            }

            final Artifact<?> mappedArtifact = (Artifact<?>) artifact.getProperties().get("mapped");
            if (mappedArtifact == null) {
                return;
            }

            final Association association = mappedArtifact.getContainingNode().getContainingAssociation();
            if (association == null) {
                return;
            }
            if (!(association instanceof Association.Op)) {
                throw new EccoException("Expected association " + association.getId() + " to be an instance of " + Association.Op.class);
            }

            final Association.Op associationOp = (Association.Op) association;
            final Condition condition = associationOp.computeCondition();
            final Map<Module, Collection<ModuleRevision>> conditionModules = condition.getModules();

            conditionModules.values()
                    .stream()
                    .flatMap(moduleRevisions -> moduleRevisions.stream())
                    .flatMap(moduleRevision -> Arrays.stream(moduleRevision.getPos()))
                    .forEach(featureRevision -> {
                        Set<Node> nodes = null;
                        if (featureRevisionSetMap.containsKey(featureRevision)) {
                            nodes = featureRevisionSetMap.get(featureRevision);
                        } else {
                            nodes = new HashSet<>();
                            featureRevisionSetMap.put(featureRevision, nodes);
                        }

                        nodes.add(node);
                    });
        });

        return featureRevisionSetMap;
    }

    private Optional<Range> extractRangeFromNodes(Collection<? extends Node> nodes) {
        final Comparator<Position> positionComparator = new Comparator<Position>() {
            @Override
            public int compare(Position p1, Position p2) {
                if (p1.getLine() < p2.getLine() ||
                        (p1.getLine() == p2.getLine() && p1.getCharacter() < p2.getCharacter())) {
                    return -1;
                } else if (p1.getLine() == p2.getLine() &&
                    p1.getCharacter() == p2.getCharacter()) {
                    return 0;
                } else {
                    return 1;
                }
            }
        };

        final BinaryOperator<Optional<Position>> startPositionCombinator = new BinaryOperator<Optional<Position>>() {
            @Override
            public Optional<Position> apply(Optional<Position> position1, Optional<Position> position2) {
                if ((position1.isEmpty() && !position2.isEmpty()) ||
                        (!position1.isEmpty() && !position2.isEmpty() &&
                                positionComparator.compare(position1.get(), position2.get()) > 0)) {
                    return position2;
                } else {
                    return position1;
                }
            }
        };

        final BinaryOperator<Optional<Position>> endPositionCombinator = new BinaryOperator<Optional<Position>>() {
            @Override
            public Optional<Position> apply(Optional<Position> position1, Optional<Position> position2) {
                if ((position1.isEmpty() && !position2.isEmpty()) ||
                        (!position1.isEmpty() && !position2.isEmpty() &&
                                positionComparator.compare(position1.get(), position2.get()) < 0)) {
                    return position2;
                } else {
                    return position1;
                }
            }
        };

        final String LINE_START = "LINE_START";
        final String LINE_END = "LINE_END";
        Pair<Optional<Position>, Optional<Position>> rangePositions = nodes.stream()
                .filter(node -> node.getProperties().containsKey(LINE_START) || node.getProperties().containsKey(LINE_END))
                .map(node -> {
                    Optional<Position> lineStart = node
                            .<Integer>getProperty("LINE_START")
                            .map(line -> new Position(line - 1, 0));
                    Optional<Position> lineEnd = node
                            .<Integer>getProperty("LINE_END")
                            .map(line -> new Position(line - 1, Integer.MAX_VALUE));
                    return new Pair<>(lineStart, lineEnd);
                })
                .reduce(new Pair<Optional<Position>, Optional<Position>>(Optional.empty(), Optional.empty()), (result, element) -> {
                    final Optional<Position> elementLineStart = element.getFirst();
                    final Optional<Position> elementLineEnd = element.getSecond();

                    final Optional<Position> resultLineStart = result.getFirst();
                    final Optional<Position> resultLineEnd = result.getSecond();


                    return new Pair<>(
                            startPositionCombinator.apply(resultLineStart, elementLineStart),
                            endPositionCombinator.apply(resultLineEnd, elementLineEnd));
                });

        if (rangePositions.getFirst().isEmpty()) {
            return Optional.empty();
        } else if (rangePositions.getSecond().isEmpty()) {
            return Optional.of(new Range(rangePositions.getFirst().get(), rangePositions.getFirst().get()));
        } else {
            return Optional.of(new Range(rangePositions.getFirst().get(), rangePositions.getSecond().get()));
        }
    }
}
