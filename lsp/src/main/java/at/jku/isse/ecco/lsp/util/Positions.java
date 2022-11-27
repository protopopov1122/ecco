package at.jku.isse.ecco.lsp.util;

import at.jku.isse.ecco.tree.Node;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.util.Ranges;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class Positions {

    public static final int LINE_START_CHARACTER_NUM = 0;
    public static final int LINE_END_CHARACTER_NUM = Integer.MAX_VALUE;

    public static Optional<Range> extractNodeRange(final Node node) {
        final Map<String, Object> properties = node.getProperties();
        if (!properties.containsKey(Properties.LINE_START) || !properties.containsKey(Properties.LINE_END)) {
            return Optional.empty();
        }

        final Position lineStart = node
                .<Integer>getProperty(Properties.LINE_START)
                .map(line -> new Position(line,
                        (Integer) node.getProperties().getOrDefault(Properties.COLUMN_START, LINE_START_CHARACTER_NUM)))
                .map(Positions::mapPosition)
                .get();
        final Position lineEnd = node
                .<Integer>getProperty(Properties.LINE_END)
                .map(line -> new Position(line,
                        (Integer) node.getProperties().getOrDefault(Properties.COLUMN_END, LINE_END_CHARACTER_NUM)))
                .map(Positions::mapPosition)
                .get();
        return Optional.of(new Range(lineStart, lineEnd));
    }

    private static Position mapPosition(final Position position) {
        return new Position(position.getLine() - 1, position.getCharacter() - 1);
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

    public static Optional<Range> rangesMerge(final Range r1, final Range r2) {
        final Comparator<Position> positionComparator = PositionComparator.Instance;

        if (positionComparator.compare(r1.getStart(), r2.getStart()) <= 0 &&
            positionComparator.compare(r1.getEnd(), r2.getEnd()) >= 0) {
            return Optional.of(r1);
        }

        if (positionComparator.compare(r1.getStart(), r2.getStart()) >= 0 &&
            positionComparator.compare(r1.getEnd(), r2.getEnd()) <= 0) {
            return Optional.of(r2);
        }

        if (positionComparator.compare(r1.getStart(), r2.getStart()) > 0) {
            return rangesMerge(r2, r1);
        }

        if (positionComparator.compare(r1.getEnd(), r2.getStart()) == 0) {
            return Optional.of(new Range(r1.getStart(), r2.getEnd()));
        }

        if (r1.getEnd().getLine() == r2.getStart().getLine() &&
            r1.getEnd().getCharacter() + 1 == r2.getStart().getCharacter()) {
            return Optional.of(new Range(r1.getStart(), r2.getEnd()));
        }

        if (r1.getEnd().getLine() + 1 == r2.getStart().getLine() &&
            r1.getEnd().getCharacter() == LINE_END_CHARACTER_NUM &&
            r2.getStart().getCharacter() == LINE_START_CHARACTER_NUM) {
            return Optional.of(new Range(r1.getStart(), r2.getEnd()));
        }

        if (positionComparator.compare(r1.getEnd(), r2.getStart()) >= 0) {
            return Optional.of(new Range(r1.getStart(), r2.getEnd()));
        }

        return Optional.empty();
    }

    public static List<Range> rangesMerge(final Collection<? extends Range> ranges) {
        List<Range> merged = new ArrayList<>();
        ranges.stream()
            .sorted((Comparator<Range>) (r1, r2) -> PositionComparator.Instance.compare(r1.getStart(), r2.getStart()))
            .forEach(range -> {
                if (merged.isEmpty()) {
                    merged.add(range);
                } else {
                    final int tailIndex = merged.size() - 1;
                    final Range tail = merged.get(tailIndex);
                    final Optional<Range> mergedTail = rangesMerge(tail, range);
                    if (mergedTail.isPresent()) {
                        merged.set(tailIndex, mergedTail.get());
                    } else {
                        merged.add(range);
                    }
                }
            });
        return merged;
    }

    public static List<Range> extractNodeRanges(final Collection<? extends Node> nodes) {
        return nodes.stream()
                .map(Positions::extractNodeRange)
                .filter(Optional<Range>::isPresent)
                .map(Optional<Range>::get)
                .collect(Collectors.toList());
    }

    public static List<Range> rangeSplitLines(final Range range) {
        return IntStream.range(range.getStart().getLine(), range.getEnd().getLine() + 1)
                .mapToObj(line -> {
                    if (line == range.getStart().getLine()) {
                        return new Range(
                                new Position(line, range.getStart().getCharacter()),
                                new Position(line, LINE_END_CHARACTER_NUM));
                    } else if (line == range.getEnd().getLine()) {
                        return new Range(
                                new Position(line, LINE_START_CHARACTER_NUM),
                                new Position(line, range.getEnd().getCharacter()));
                    } else {
                        return new Range(
                                new Position(line, LINE_START_CHARACTER_NUM),
                                new Position(line, LINE_END_CHARACTER_NUM));
                    }
                })
                .collect(Collectors.toList());
    }

    public static Range collapseRange(final Range range) {
        final Position rangeStart = range.getStart();

        return new Range(
                rangeStart,
                new Position(rangeStart.getLine(), rangeStart.getCharacter() + 1)
        );
    }

    public static Optional<Range> findShortestRangeContaining(final Stream<Range> rangeStream, final Position position) {
        return rangeStream
                .filter(range -> Positions.rangeContains(range, position))
                .min(ShortestRangeComparator.Instance);
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

        public static final Comparator<Position> Instance = new PositionComparator();
    }

    public static class ShortestRangeComparator implements Comparator<Range> {

        @Override
        public int compare(final Range r1, final Range r2) {
            final int range1Length = r1.getEnd().getLine() - r1.getStart().getLine();
            final int range2Length = r2.getEnd().getLine() - r2.getStart().getLine();
            if (range1Length < range2Length) {
                return -1;
            } else if (range1Length == range2Length) {
                return 0;
            } else {
                return 1;
            }
        }

        public static final Comparator<Range> Instance = new ShortestRangeComparator();
    }
}
