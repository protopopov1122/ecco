package at.jku.isse.ecco.lsp.launcher;

import at.jku.isse.ecco.lsp.server.EccoLspServer;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.services.LanguageClient;

import java.util.concurrent.ExecutionException;

public class StdioLauncher {
    public static void main(String[] args) throws ExecutionException, InterruptedException {
        EccoLspServer eccoLspServer = new EccoLspServer();
        Launcher<LanguageClient> launcher = new Launcher.Builder<LanguageClient>()
                .setLocalService(eccoLspServer)
                .setRemoteInterface(LanguageClient.class)
                .setInput(System.in)
                .setOutput(System.out)
                .create();
        LanguageClient languageClient = launcher.getRemoteProxy();
        eccoLspServer.connect(languageClient);
        launcher.startListening().get();
    }
}
