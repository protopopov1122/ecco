package at.jku.isse.ecco.lsp.extensions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

public class InfoResponse {

    public static class CommitInfo {
        private final String id;
        private final String message;
        private final String configuration;
        private final long timestamp;

        public CommitInfo(final String id, final String message, final String configuration, final Date date) {
            this.id = id;
            this.message = message;
            this.configuration = configuration;
            this.timestamp = date.getTime();
        }
    }

    public static class FeatureInfo {
        private final String id;
        private final String name;
        private final String description;
        private final List<String> revisions;

        public FeatureInfo(final String id, final String name, final String description, final Collection<String> revisions) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.revisions = new ArrayList<>(revisions);
        }
    }

    private final String baseDir;
    private final String configuration;

    private final List<CommitInfo> commits;
    private final List<FeatureInfo> features;

    public InfoResponse(final String baseDir, final String configuration, Collection<CommitInfo> commits, Collection<FeatureInfo> features) {
        this.baseDir = baseDir;
        this.configuration = configuration;
        this.commits = new ArrayList<>(commits);
        this.features = new ArrayList<>(features);
    }
}
