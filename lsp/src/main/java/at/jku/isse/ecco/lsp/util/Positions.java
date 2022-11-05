package at.jku.isse.ecco.lsp.util;

import at.jku.isse.ecco.tree.Node;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.util.Ranges;

import java.util.*;
import java.util.stream.Collectors;

public class Positions {

    public static final int LINE_START_CHARACTER_NUM = 0;
    public static final int LINE_END_CHARACTER_NUM = Integer.MAX_VALUE;

    public static Optional<Position> extractNodeStart(final Node node) {
        return node
                .<Integer>getProperty(Properties.LINE_START)
                .map(line -> new Position(line, LINE_START_CHARACTER_NUM))
                .map(Positions::mapPosition);
    }

    public static Optional<Range> extractNodeRange(final Node node) {
        final Map<String, Object> properties = node.getProperties();
        if (!properties.containsKey(Properties.LINE_START) || !properties.containsKey(Properties.LINE_END)) {
            return Optional.empty();
        }

        final Position lineStart = node
                .<Integer>getProperty(Properties.LINE_START)
                .map(line -> new Position(line, LINE_START_CHARACTER_NUM))
                .map(Positions::mapPosition)
                .get();
        final Position lineEnd = node
                .<Integer>getProperty(Properties.LINE_END)
                .map(line -> new Position(line, LINE_END_CHARACTER_NUM))
                .map(Positions::mapPosition)
                .get();
        return Optional.of(new Range(lineStart, lineEnd));
    }

    private static Position mapPosition(final Position position) {
        return new Position(position.getLine() - 1, position.getCharacter());
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

    public static List<Range> extractNodeRanges(final Collection<? extends Node> nodes) {
        return nodes.stream()
                .map(Positions::extractNodeRange)
                .filter(Optional<Range>::isPresent)
                .map(Optional<Range>::get)
                .collect(Collectors.toList());
    }

    public static class PositionComparator implements Comparator<Position> {
        @Override
        public int compare(final Position p1, final Position p2) {
            if (p1.getLine() < p2.getLine() ||
                    (p1.getLine() == p2.getLine() && p1.getCharacter() < p2.getCharacter())) {
                return -1;
            } else if (p1.equals(p2)) {
                return 0;
            } else {
                return 1;
            }
        }
    }
}
