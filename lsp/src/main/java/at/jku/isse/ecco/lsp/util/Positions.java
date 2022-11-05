package at.jku.isse.ecco.lsp.util;

import at.jku.isse.ecco.tree.Node;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import java.util.*;
import java.util.stream.Collectors;

public class Positions {
    public static final String LINE_START = "LINE_START";
    public static final String LINE_END = "LINE_END";

    public static Optional<Position> extractNodeStart(final Node node) {
        return node
                .<Integer>getProperty(LINE_START)
                .map(line -> new Position(line - 1, 0));
    }

    public static Optional<Range> extractNodeRange(final Node node) {
        final Map<String, Object> properties = node.getProperties();
        if (!properties.containsKey(LINE_START) || !properties.containsKey(LINE_END)) {
            return Optional.empty();
        }

        final Position lineStart = node
                .<Integer>getProperty(LINE_START)
                .map(line -> new Position(line - 1, 0))
                .get();
        final Position lineEnd = node
                .<Integer>getProperty(LINE_END)
                .map(line -> new Position(line - 1, Integer.MAX_VALUE))
                .get();
        return Optional.of(new Range(lineStart, lineEnd));
    }

    public static boolean rangeContains(final Range range, final Position position) {
        final int positionLine = position.getLine();
        final int positionCharacter = position.getCharacter();

        final int startLine = range.getStart().getLine();
        final int startCharacter = range.getStart().getCharacter();

        final int endLine = range.getEnd().getLine();
        final int endCharacter = range.getEnd().getCharacter();

        return (positionLine == startLine && positionLine == endLine && positionCharacter >= startCharacter && positionCharacter <= endCharacter) ||
                (positionLine == startLine && positionLine < endLine && positionCharacter >= startCharacter) ||
                (positionLine > startLine && positionLine < endLine) ||
                (positionLine > startLine && positionLine == endLine && positionCharacter <= endCharacter);
    }

    public static List<Range> extractNodeRanges(Collection<? extends Node> nodes) {
        return nodes.stream()
                .map(Positions::extractNodeRange)
                .filter(range -> range.isPresent())
                .map(range -> range.get())
                .collect(Collectors.toList());
    }
}
