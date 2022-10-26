package at.jku.isse.ecco.lsp.services;

import at.jku.isse.ecco.lsp.server.EccoLspServer;
import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.services.WorkspaceService;

public class EccoWorkspaceService implements WorkspaceService {
    final EccoLspServer eccoLspServer;

    public EccoWorkspaceService(EccoLspServer eccoLspServer) {
        this.eccoLspServer = eccoLspServer;
    }

    @Override
    public void didChangeConfiguration(DidChangeConfigurationParams params) {
    }

    @Override
    public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
    }
}
