package at.jku.isse.ecco.lsp.launcher;

import at.jku.isse.ecco.lsp.server.EccoLspServer;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.services.LanguageClient;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.logging.*;

public class StdioLauncher {
    public static void main(String[] args) throws ExecutionException, InterruptedException, IOException {
        Logger log = Logger.getLogger(EccoLspServer.class.getName());
        String logFilepath = System.getenv("ECCO_LSP_SERVER_LOG");
        if (logFilepath != null && !logFilepath.isEmpty()) {
            FileHandler handler = new FileHandler(logFilepath);
            log.addHandler(handler);
            handler.setFormatter(new SimpleFormatter());
        }
        log.setLevel(Level.ALL);

        EccoLspServer eccoLspServer = new EccoLspServer(log);
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
