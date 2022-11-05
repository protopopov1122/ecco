package at.jku.isse.ecco.lsp.util;

import at.jku.isse.ecco.tree.Node;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class Positions {
    public static final String LINE_START = "LINE_START";
    public static final String LINE_END = "LINE_END";

    public static Range extractNodeRange(final Node node) {
        final Position lineStart = node
                .<Integer>getProperty(LINE_START)
                .map(line -> new Position(line, 0))
                .get();
        final Position lineEnd = node
                .<Integer>getProperty(LINE_END)
                .map(line -> new Position(line, Integer.MAX_VALUE))
                .get();
        return new Range(lineStart, lineEnd);
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
                .filter(node -> node.getProperties().containsKey(Positions.LINE_START) && node.getProperties().containsKey(Positions.LINE_END))
                .map(Positions::extractNodeRange)
                .collect(Collectors.toList());
    }
}
