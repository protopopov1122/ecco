package at.jku.isse.ecco.lsp.services;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.lsp.domain.*;
import at.jku.isse.ecco.lsp.server.EccoLspServer;
import at.jku.isse.ecco.lsp.util.Nodes;
import at.jku.isse.ecco.lsp.util.Pair;
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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class EccoTextDocumentService implements TextDocumentService {
    private final EccoLspServer eccoLspServer;
    private final Logger logger;
    private final Map<String, String> unsavedDocumentContents;

    public EccoTextDocumentService(final EccoLspServer eccoLspServer) {
        this.eccoLspServer = eccoLspServer;
        this.logger = this.eccoLspServer.getLogger();
        this.unsavedDocumentContents = new HashMap<>();
    }

    @Override
    public void didOpen(final DidOpenTextDocumentParams params) {
        final String documentUri = params.getTextDocument().getUri();
        logger.finer("Opened document " + documentUri);
        this.unsavedDocumentContents.remove(documentUri);
    }

    @Override
    public void didChange(final DidChangeTextDocumentParams params) {
        final String documentUri = params.getTextDocument().getUri();
        logger.finest("Changed document " + documentUri);

        final List<TextDocumentContentChangeEvent> contentChangeEvents = params.getContentChanges();
        if (contentChangeEvents.size() != 1 || contentChangeEvents.get(0).getRange() != null) {
            logger.severe("Unexpected content change event format: " + contentChangeEvents);
            return;
        }

        this.unsavedDocumentContents.put(documentUri, contentChangeEvents.get(0).getText());
    }

    @Override
    public void didClose(final DidCloseTextDocumentParams params) {
        final String documentUri = params.getTextDocument().getUri();
        logger.finer("Closed document " + documentUri);
        this.unsavedDocumentContents.remove(documentUri);
    }

    @Override
    public void didSave(final DidSaveTextDocumentParams params) {
        final String documentUri = params.getTextDocument().getUri();
        logger.finer("Saved document " + documentUri);
        this.unsavedDocumentContents.remove(documentUri);
    }

    @Override
    public CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbol(final DocumentSymbolParams params) {
        try {
            final String documentUri = params.getTextDocument().getUri();
            logger.fine("Requested document symbols of " + documentUri);

            final Optional<String> unsavedContent = this.getUnsavedDocumentContent(documentUri);

            final Path documentInRepoPath = this.getDocumentPathInRepo(params.getTextDocument().getUri());
            logger.finer("Document path in repo " + documentInRepoPath);

            final Document document = unsavedContent
                    .map(this.newUnsavedDocumentLoader(documentInRepoPath))
                    .orElseGet(this.newDocumentLoader(documentInRepoPath));

            final Map<FeatureRevision, DocumentFeature> documentFeatureMap = EccoDocumentFeature.from(document);

            logger.finest("Identified document features in " + documentUri + ": " + documentFeatureMap);

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
            final String documentUri = params.getTextDocument().getUri();
            logger.fine("Requested document highlights of " + documentUri);

            final Optional<String> unsavedContent = this.getUnsavedDocumentContent(documentUri);

            final Path documentInRepoPath = this.getDocumentPathInRepo(params.getTextDocument().getUri());
            logger.finer("Document path in repo " + documentInRepoPath);

            final Position highlightPosition = params.getPosition();
            logger.finer("Highlight position is " + highlightPosition);

            final Document document = unsavedContent
                    .map(this.newUnsavedDocumentLoader(documentInRepoPath))
                    .orElseGet(this.newDocumentLoader(documentInRepoPath));

            final Set<Node> nodesAtPosition = document.getNodesAt(highlightPosition);
            final Set<Association> associations = document.getAssociationsOf(nodesAtPosition);
            final Map<Association, Set<Node>> associationNodes = document.getNodesFor(associations);

            final Comparator<Range> shortestRangeComparator = Positions.ShortestRangeComparator.Instance;
            final List<Range> nodeRanges = associationNodes.values()
                    .stream()
                    .map(Positions::extractNodeRanges)
                    .map(Positions::rangesMerge)
                    .min((ranges1, ranges2) -> {

                        Optional<Range> range1 = Positions.findShortestRangeContaining(ranges1.stream(), highlightPosition);
                        Optional<Range> range2 = Positions.findShortestRangeContaining(ranges2.stream(), highlightPosition);

                        if (range1.isEmpty()) {
                            return 1;
                        } else if (range2.isEmpty()) {
                            return -1;
                        } else {
                            return shortestRangeComparator.compare(range1.get(), range2.get());
                        }
                    })
                    .orElse(List.of());

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
            final String documentUri = params.getTextDocument().getUri();
            logger.fine("Requested document hover of " + documentUri);

            final Optional<String> unsavedContent = this.getUnsavedDocumentContent(documentUri);

            final Path documentInRepoPath = this.getDocumentPathInRepo(params.getTextDocument().getUri());
            logger.finer("Document path in repo " + documentInRepoPath);

            final Position hoverPosition = params.getPosition();
            logger.finer("Hover position is " + hoverPosition);

            final Document document = unsavedContent
                    .map(this.newUnsavedDocumentLoader(documentInRepoPath))
                    .orElseGet(this.newDocumentLoader(documentInRepoPath));

            final Set<Node> nodesAtPosition = document.getNodesAt(hoverPosition);
            final Set<Association> associations = document.getAssociationsOf(nodesAtPosition);
            final Map<Association, Set<Node>> associationNodes = document.getNodesFor(associations);


            final Comparator<Range> shortestRangeComparator = Positions.ShortestRangeComparator.Instance;
            final Optional<Pair<Association, Optional<Range>>> associationRange = associationNodes.entrySet()
                    .stream()
                    .map(entry -> new Pair<>(entry.getKey(), Positions.extractNodeRanges(entry.getValue())))
                    .map(entry -> new Pair<>(entry.getFirst(), Positions.rangesMerge(entry.getSecond())))
                    .map(entry -> new Pair<>(entry.getFirst(), Positions.findShortestRangeContaining(entry.getSecond().stream(), hoverPosition)))
                    .min((entry1, entry2) -> {
                        final Optional<Range> range1 = entry1.getSecond();
                        final Optional<Range> range2 = entry2.getSecond();

                        if (range1.isEmpty()) {
                            return 1;
                        } else if (range2.isEmpty()) {
                            return -1;
                        } else {
                            return shortestRangeComparator.compare(range1.get(), range2.get());
                        }
                    });

            final String hoverText = associationRange
                    .map(Pair::getFirst)
                    .map(Association::computeCondition)
                    .map(Condition::toString)
                    .orElse("");
            final Range hoverRange = associationRange
                    .map(Pair::getSecond)
                    .flatMap(Function.identity())
                    .orElseGet(() -> new Range(
                            new Position(hoverPosition.getLine(), Positions.LINE_START_CHARACTER_NUM),
                            new Position(hoverPosition.getLine(), Positions.LINE_END_CHARACTER_NUM)
                    ));

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
            final String documentUri = params.getTextDocument().getUri();
            logger.fine("Requested document colors of " + documentUri);

            final Optional<String> unsavedContent = this.getUnsavedDocumentContent(documentUri);

            final Path documentInRepoPath = this.getDocumentPathInRepo(params.getTextDocument().getUri());
            logger.finer("Document path in repo " + documentInRepoPath);

            final Document document = unsavedContent
                    .map(this.newUnsavedDocumentLoader(documentInRepoPath))
                    .orElseGet(this.newDocumentLoader(documentInRepoPath));

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

                        return  Positions.rangesMerge(associationListEntry.getValue())
                                .stream()
                                .flatMap(range -> Positions.rangeSplitLines(range).stream())
                                .map(Positions::collapseRange)
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

    private Optional<String> getUnsavedDocumentContent(final String uri) {
        if (this.unsavedDocumentContents.containsKey(uri)) {
            return Optional.of(this.unsavedDocumentContents.get(uri));
        } else {
            return Optional.empty();
        }
    }

    private Function<String, Document> newUnsavedDocumentLoader(final Path documentInRepoPath) {
        final EccoService eccoService = this.eccoLspServer.getEccoService();
        return content -> {
            final InputStream is = new ByteArrayInputStream(content.getBytes());
            return EccoDocument.load(eccoService, documentInRepoPath, is);
        };
    }

    private Supplier<Document> newDocumentLoader(final Path documentInRepoPath) {
        final EccoService eccoService = this.eccoLspServer.getEccoService();
        return () -> EccoDocument.load(eccoService, documentInRepoPath);
    }
}
