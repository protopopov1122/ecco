package at.jku.isse.ecco.lsp.domain;

import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.tree.RootNode;

import java.nio.file.Path;
import java.util.List;

public class EccoDocument implements Document {

    private final Path documentPath;

    private final RootNode rootNode;

    public EccoDocument(Path documentPath, RootNode rootNode) {
        this.documentPath = documentPath;
        this.rootNode = rootNode;
    }

    @Override
    public Path getDocumentPath() {
        return this.documentPath;
    }

    @Override
    public RootNode getRootNode() {
        return this.rootNode;
    }

    @Override
    public String toString() {
        return this.getDocumentPath().toString();
    }

    public static EccoDocument load(final EccoService eccoService, final Path documentPath) {
        final RootNode documentRootNode = eccoService.map(List.of(documentPath));
        return new EccoDocument(documentPath, documentRootNode);
    }
}
