package at.jku.isse.ecco.lsp.services;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.lsp.domain.*;
import at.jku.isse.ecco.lsp.server.EccoLspServer;
import at.jku.isse.ecco.lsp.util.Nodes;
import at.jku.isse.ecco.lsp.util.Positions;
import at.jku.isse.ecco.module.Condition;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.tree.Node;
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
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class EccoTextDocumentService implements TextDocumentService {
    private final EccoLspServer eccoLspServer;
    private final Logger logger;

    public EccoTextDocumentService(final EccoLspServer eccoLspServer) {
        this.eccoLspServer = eccoLspServer;
        this.logger = this.eccoLspServer.getLogger();
    }

    @Override
    public void didOpen(final DidOpenTextDocumentParams params) {
        logger.finer("Opened document " + params.getTextDocument().getUri());
    }

    @Override
    public void didChange(final DidChangeTextDocumentParams params) {
        logger.finest("Changed document " + params.getTextDocument().getUri());
    }

    @Override
    public void didClose(final DidCloseTextDocumentParams params) {
        logger.finer("Closed document " + params.getTextDocument().getUri());
    }

    @Override
    public void didSave(final DidSaveTextDocumentParams params) {
        logger.finer("Saved document " + params.getTextDocument().getUri());
    }

    @Override
    public CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbol(final DocumentSymbolParams params) {
        try {
            logger.fine("Requested document symbols of " + params.getTextDocument().getUri());

            final EccoService eccoService = this.eccoLspServer.getEccoService();
            final Path documentInRepoPath = this.getDocumentPathInRepo(params.getTextDocument().getUri());
            logger.finer("Document path in repo " + documentInRepoPath);

            final Document document = EccoDocument.load(eccoService, documentInRepoPath);
            final Map<FeatureRevision, DocumentFeature> documentFeatureMap = EccoDocumentFeature.from(document);

            logger.finest("Identified document features in " + params.getTextDocument().getUri() + ": " + documentFeatureMap);

            final List<Either<SymbolInformation, DocumentSymbol>> symbols = documentFeatureMap.values()
                        .stream()
                        .map(documentFeature -> {
                            final Range totalRange = documentFeature.getTotalRange().orElse(new Range());
                            final DocumentSymbol documentSymbol = new DocumentSymbol(
                                    documentFeature.getFeatureRevision().getFeatureRevisionString(),
                                    SymbolKind.Object,
                                    totalRange,
                                    totalRange);
                            return Either.<SymbolInformation, DocumentSymbol>forRight(documentSymbol);
                        })
                        .collect(Collectors.toList());

            return CompletableFuture.completedFuture(symbols);
        }  catch (Throwable ex) {
            logger.severe(ex.getMessage() + "\t" + Arrays.toString(ex.getStackTrace()));
            final ResponseError error = new ResponseError(ResponseErrorCode.InternalError, ex.getMessage(), null);
            return CompletableFuture.failedFuture(new ResponseErrorException(error));
        }
    }

    @Override
    public CompletableFuture<List<? extends DocumentHighlight>> documentHighlight(final DocumentHighlightParams params) {
        try {
            logger.fine("Requested document highlights of " + params.getTextDocument().getUri());

            final EccoService eccoService = this.eccoLspServer.getEccoService();
            final Path documentInRepoPath = this.getDocumentPathInRepo(params.getTextDocument().getUri());
            logger.finer("Document path in repo " + documentInRepoPath);

            final Position highlightPosition = params.getPosition();
            logger.finer("Highlight position is " + highlightPosition);

            final Document document = EccoDocument.load(eccoService, documentInRepoPath);

            final Set<Node> nodesAtPosition = document.getNodesAt(highlightPosition);
            final Set<Association> associations = document.getAssociationsOf(nodesAtPosition);
            final Set<Node> nodes = document.getNodesFrom(associations);
            final List<Range> nodeRanges = Positions.rangesMerge(Positions.extractNodeRanges(nodes));
            logger.finer("Highlight ranges " + nodeRanges);

            final List<? extends DocumentHighlight> highlights = nodeRanges.stream()
                    .map(nodeRange -> new DocumentHighlight(nodeRange, DocumentHighlightKind.Read))
                    .collect(Collectors.toList());
            return CompletableFuture.completedFuture(highlights);
        }  catch (Throwable ex) {
            logger.severe(ex.getMessage() + "\t" + Arrays.toString(ex.getStackTrace()));
            final ResponseError error = new ResponseError(ResponseErrorCode.InternalError, ex.getMessage(), null);
            return CompletableFuture.failedFuture(new ResponseErrorException(error));
        }
    }

    @Override
    public CompletableFuture<Hover> hover(final HoverParams params) {

        try {
            logger.fine("Requested document hover of " + params.getTextDocument().getUri());

            final EccoService eccoService = this.eccoLspServer.getEccoService();
            final Path documentInRepoPath = this.getDocumentPathInRepo(params.getTextDocument().getUri());
            logger.finer("Document path in repo " + documentInRepoPath);

            final Position hoverPosition = params.getPosition();
            logger.finer("Hover position is " + hoverPosition);

            final Document document = EccoDocument.load(eccoService, documentInRepoPath);

            final Set<Node> nodesAtPosition = document.getNodesAt(hoverPosition);
            final Set<Association> associations = document.getAssociationsOf(nodesAtPosition);
            final String hoverText = associations.stream()
                    .map(Association::computeCondition)
                    .map(Condition::toString)
                    .collect(Collectors.joining("\n"));
            final Comparator<Position> positionComparator = Positions.PositionComparator.Instance;
            final Range hoverRange = nodesAtPosition.stream()
                    .map(node -> Positions.extractNodeRange(node).get())
                    .reduce(new Range(hoverPosition, hoverPosition), (accumulator, range) -> {
                        final Position resultStart = positionComparator.compare(accumulator.getStart(), range.getStart()) <= 0
                                ? accumulator.getStart()
                                : range.getStart();
                        final Position resultEnd = positionComparator.compare(accumulator.getEnd(), range.getEnd()) >= 0
                                ? accumulator.getEnd()
                                : range.getEnd();
                        return new Range(resultStart, resultEnd);
                    });

            final Hover hover = new Hover(List.of(Either.forLeft(hoverText)), hoverRange);
            return CompletableFuture.completedFuture(hover);
        }  catch (Throwable ex) {
            logger.severe(ex.getMessage() + "\t" + Arrays.toString(ex.getStackTrace()));
            final ResponseError error = new ResponseError(ResponseErrorCode.InternalError, ex.getMessage(), null);
            return CompletableFuture.failedFuture(new ResponseErrorException(error));
        }
    }

    @Override
    public CompletableFuture<List<ColorInformation>> documentColor(final DocumentColorParams params) {
        try {
            logger.fine("Requested document colors of " + params.getTextDocument().getUri());

            final EccoService eccoService = this.eccoLspServer.getEccoService();
            final Path documentInRepoPath = this.getDocumentPathInRepo(params.getTextDocument().getUri());
            logger.finer("Document path in repo " + documentInRepoPath);

            final Document document = EccoDocument.load(eccoService, documentInRepoPath);


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

            final List<ColorInformation> colorInformation = associationRanges.entrySet().stream()
                    .flatMap(associationListEntry -> {
                        final int associationHash = associationListEntry.getKey().getAssociationString().hashCode();
                        final Color associationColor = new Color(
                                ((associationHash >> 8) & 0xff) / 255.0,
                                ((associationHash >> 16) & 0xff) / 255.0,
                                ((associationHash >> 24) & 0xff) / 255.0,
                                1.0);

                        return Positions.rangesMerge(associationListEntry.getValue()).stream()
                                .flatMap(range -> Positions.rangeSplitLines(range).stream())
                                .map(range -> new Range(
                                        range.getStart(),
                                        new Position(range.getStart().getLine(), range.getStart().getCharacter() + 1)
                                ))
                                .map(range -> new ColorInformation(range, associationColor));
                    })
                    .collect(Collectors.toList());

            return CompletableFuture.completedFuture(colorInformation);
        }  catch (Throwable ex) {
            logger.severe(ex.getMessage() + "\t" + Arrays.toString(ex.getStackTrace()));
            final ResponseError error = new ResponseError(ResponseErrorCode.InternalError, ex.getMessage(), null);
            return CompletableFuture.failedFuture(new ResponseErrorException(error));
        }
    }

    @Override
    public CompletableFuture<List<ColorPresentation>> colorPresentation(final ColorPresentationParams params) {
        return CompletableFuture.completedFuture(List.of());
    }

    private Path getDocumentPathInRepo(final String uri) {
        final EccoService eccoService = this.eccoLspServer.getEccoService();
        final Path repoBasePath = eccoService.getBaseDir();
        final URI documentUri = URI.create(uri);
        final Path documentPath = Path.of(documentUri.getPath());
        return repoBasePath.relativize(documentPath);
    }
}
