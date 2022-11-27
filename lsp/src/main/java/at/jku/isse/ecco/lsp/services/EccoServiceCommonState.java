package at.jku.isse.ecco.lsp.services;

import at.jku.isse.ecco.lsp.domain.Document;
import at.jku.isse.ecco.lsp.domain.EccoDocument;
import at.jku.isse.ecco.lsp.server.EccoLspServer;
import at.jku.isse.ecco.service.EccoService;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

public class EccoServiceCommonState {
    private final EccoLspServer eccoLspServer;
    private final Map<String, String> unsavedDocumentContents;

    public EccoServiceCommonState(final EccoLspServer eccoLspServer) {
        this.eccoLspServer = eccoLspServer;
        this.unsavedDocumentContents = new HashMap<>();
    }

    public void removeUnsaved(final String uri) {
        this.unsavedDocumentContents.remove(uri);
    }

    public void updateUnsaved(final String uri, final String content) {
        this.unsavedDocumentContents.put(uri, content);
    }

    public Optional<String> getUnsaved(final String uri) {
        if (this.unsavedDocumentContents.containsKey(uri)) {
            return Optional.of(this.unsavedDocumentContents.get(uri));
        } else {
            return Optional.empty();
        }
    }

    public Path getDocumentPathInRepo(final String uri) {
        final EccoService eccoService = this.eccoLspServer.getEccoService();
        final Path repoBasePath = eccoService.getBaseDir();
        final URI documentUri = URI.create(uri);
        final Path documentPath = Path.of(documentUri.getPath());
        return repoBasePath.relativize(documentPath);
    }

    public Supplier<Document> documentLoader(final String uri) {
        final Path documentInRepoPath = this.getDocumentPathInRepo(uri);
        final EccoService eccoService = this.eccoLspServer.getEccoService();
        return this.getUnsaved(uri)
                .map(content -> {
                    final Supplier<Document> documentSupplier = () -> {
                        InputStream is = new ByteArrayInputStream(content.getBytes());
                        return EccoDocument.load(eccoService, documentInRepoPath, is);
                    };
                    return documentSupplier;
                })
                .orElseGet(() -> {
                    final Supplier<Document> documentSupplier = () ->
                        EccoDocument.load(eccoService, documentInRepoPath);
                    return documentSupplier;
                });
    }
}
