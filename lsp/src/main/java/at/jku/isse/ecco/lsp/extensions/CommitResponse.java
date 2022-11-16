package at.jku.isse.ecco.lsp.extensions;

import java.util.Date;

public class CommitResponse {

    private final String id;

    private final long timestamp;

    private final String message;

    private final String configuration;

    public CommitResponse(String id, Date date, String message, String configuration) {
        this.id = id;
        this.timestamp = date.getTime();
        this.message = message;
        this.configuration = configuration;
    }
}
