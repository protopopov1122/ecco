package at.jku.isse.ecco.lsp.launcher;

import at.jku.isse.ecco.lsp.server.EccoLspServer;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.services.LanguageClient;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class StdioLauncher {
    public static void main(String[] args) throws ExecutionException, InterruptedException, IOException {
        Logger log = Logger.getLogger("lsp-server");
        if (args.length > 0) {
            FileHandler handler = new FileHandler(args[0]);
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
