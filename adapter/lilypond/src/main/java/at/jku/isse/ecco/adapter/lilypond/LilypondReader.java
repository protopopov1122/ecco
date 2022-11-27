package at.jku.isse.ecco.adapter.lilypond;

import at.jku.isse.ecco.adapter.ArtifactReader;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.adapter.lilypond.data.ContextArtifactDataFactory;
import at.jku.isse.ecco.adapter.lilypond.data.TokenArtifactDataFactory;
import at.jku.isse.ecco.adapter.lilypond.parce.ParceToken;
import at.jku.isse.ecco.adapter.lilypond.util.TextPositionMap;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.service.listener.ReadListener;
import at.jku.isse.ecco.tree.Node;
import com.google.inject.Inject;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.google.common.base.Preconditions.checkNotNull;

public class LilypondReader implements ArtifactReader<Path, Set<Node.Op>> {
    private static final Logger LOGGER = Logger.getLogger(LilypondPlugin.class.getName());
    protected final EntityFactory entityFactory;
    private HashMap<String, Integer> tokenMetric;

    public static Logger getLogger() {
        return LOGGER;
    }
    public final static String PARSER_ACTION_LINEBREAK = "__LineBreak";

    @Inject
    public LilypondReader(EntityFactory entityFactory) {
        checkNotNull(entityFactory);

        this.entityFactory = entityFactory;
    }

    @Override
    public String getPluginId() {
        return LilypondPlugin.class.getName();
    }

    private static final Map<Integer, String[]> prioritizedPatterns;

    static {
        prioritizedPatterns = new HashMap<>();
        prioritizedPatterns.put(1, new String[]{"**.ly", "**.ily"});
    }

    @Override
    public Map<Integer, String[]> getPrioritizedPatterns() {
        return Collections.unmodifiableMap(prioritizedPatterns);
    }

    public void setGenerateTokenMetric(boolean generate) {
        if (generate) {
            tokenMetric = new HashMap<>();
        } else {
            tokenMetric = null;
        }
    }

    public Map<String, Integer> getTokenMetric() {
        return null == tokenMetric ? null : Collections.unmodifiableMap(tokenMetric);
    }

    @Override
    public Set<Node.Op> read(Path[] input) {
        return this.read(Paths.get("."), input);
    }

    @Override
    public Set<Node.Op> read(Path base, Path[] input) {
        Set<Node.Op> nodes = new HashSet<>();

        LilypondParser<ParceToken> parser = ParserFactory.getParser();
        try {
            if (parser == null) {
                throw new IOException("no parser found");
            }
            parser.init();

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "could not initialize parser", e);
            throw new RuntimeException("could not initialize parser", e);
        }

        for (Path path : input) {
            Path resolvedPath = base.resolve(path);
            final TextPositionMap textPositionMap = new TextPositionMap();
            try (BufferedReader bufferedReader = new BufferedReader(new FileReader(resolvedPath.toFile()))) {
                textPositionMap.buildMap(bufferedReader);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            Artifact.Op<PluginArtifactData> pluginArtifact = this.entityFactory.createArtifact(new PluginArtifactData(this.getPluginId(), path));
            Node.Op pluginNode = this.entityFactory.createOrderedNode(pluginArtifact);
            nodes.add(pluginNode);

            LilypondNode<ParceToken> head = parser.parse(resolvedPath, tokenMetric);
            if (head == null) {
                LOGGER.log(Level.SEVERE, "parser returned no node, file {0}", resolvedPath);
            } else {
                head = LilyEccoTransformer.transform(head);
                generateEccoTree(head, pluginNode, textPositionMap);
            }

            listeners.forEach(l -> l.fileReadEvent(resolvedPath, this));
        }

        parser.shutdown();

        return nodes;
    }

    @Override
    public Set<Node.Op> read(final Path path, final InputStream inputStream) {
        Set<Node.Op> nodes = new HashSet<>();

        LilypondParser<ParceToken> parser = ParserFactory.getParser();
        try {
            if (parser == null) {
                throw new IOException("no parser found");
            }
            parser.init();

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "could not initialize parser", e);
            throw new RuntimeException("could not initialize parser", e);
        }

        try {
            this.read(path, inputStream, parser, nodes);
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        parser.shutdown();
        return nodes;
    }

    private void read(final Path path, final InputStream inputStream, final LilypondParser<ParceToken> parser, final Set<Node.Op> nodes) throws IOException {
        final byte[] content = inputStream.readAllBytes();

        final TextPositionMap textPositionMap = new TextPositionMap();
        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(content)))) {
            textPositionMap.buildMap(bufferedReader);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        Artifact.Op<PluginArtifactData> pluginArtifact = this.entityFactory.createArtifact(new PluginArtifactData(this.getPluginId(), path));
        Node.Op pluginNode = this.entityFactory.createOrderedNode(pluginArtifact);
        nodes.add(pluginNode);

        final Path tmpFilePath = Files.createTempFile("unsavedLilypondContent", "");
        final File tmpFile = tmpFilePath.toFile();
        try (OutputStream os = new FileOutputStream(tmpFile)) {
            os.write(content);
            LilypondNode<ParceToken> head = parser.parse(tmpFilePath, tokenMetric);
            if (head == null) {
                LOGGER.log(Level.SEVERE, "parser returned no node, file {0}", path);
            } else {
                head = LilyEccoTransformer.transform(head);
                generateEccoTree(head, pluginNode, textPositionMap);
            }
        } finally {
            tmpFile.delete();
        }
    }

    public void generateEccoTree(LilypondNode<ParceToken> head, Node.Op node, final TextPositionMap textPositionMap) {
        Artifact.Op<ArtifactData> a;
        Node.Op nop;

        LilypondNode<ParceToken> n = head;
        int cntNodes = 0;
        while (n != null) {
            a = n.getData() == null ?
                this.entityFactory.createArtifact(ContextArtifactDataFactory.getContextArtifactData(n.getName())) :
                this.entityFactory.createArtifact(TokenArtifactDataFactory.getTokenArtifactData(n.getData()));

            if (n.getNext() != null && n.getNext().getLevel() > n.getLevel()) {
                nop = this.entityFactory.createOrderedNode(a);
                node.addChild(nop);
                node = nop;

            } else {
                nop = this.entityFactory.createNode(a);
                node.addChild(nop);
            }
            if (n.getData() != null) {
                final int position = n.getData().getPos();
                nop.putProperty("SOURCE_POSITION", Integer.toString(position));

                final Optional<TextPositionMap.TextPosition> textPosition = textPositionMap.get(position);
                if (textPosition.isPresent()) {
                    nop.putProperty("LINE_START", textPosition.get().getLine());
                    nop.putProperty("COLUMN_START", textPosition.get().getColumn());
                }

                final int endPosition = n.getData().getPos() + n.getData().getText().length();
                final Optional<TextPositionMap.TextPosition> endTextPosition = textPositionMap.get(endPosition);
                if (endTextPosition.isPresent()) {
                    nop.putProperty("LINE_END", endTextPosition.get().getLine());
                    nop.putProperty("COLUMN_END", endTextPosition.get().getColumn());
                }
            }
            cntNodes++;

            int prevLevel = n.getLevel();
            n = n.getNext();
            while (n != null && n.getLevel() < prevLevel) {
                prevLevel--;
                //LOG.trace("({}) ecco-node level ({}) == node level ({})", cntNodes, node.computeDepth(), n.getLevel());
                node = node.getParent();
            }
            if (node == null && n != null) {
                LOGGER.log(Level.SEVERE, "EccoNode is null after {0} nodes", cntNodes);
            }
        }
    }

	private final Collection<ReadListener> listeners = new ArrayList<>();

	@Override
	public void addListener(ReadListener listener) {
		this.listeners.add(listener);
	}

	@Override
	public void removeListener(ReadListener listener) {
		this.listeners.remove(listener);
	}

}
