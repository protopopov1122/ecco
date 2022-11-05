package at.jku.isse.ecco.lsp.domain;

import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.lsp.util.Positions;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public interface DocumentFeature {

    Document getDocument();
    FeatureRevision getFeatureRevision();
    List<Range> getRanges();

    default Optional<Range> getTotalRange() {
        final List<Range> ranges = this.getRanges();
        if (ranges.isEmpty()) {
            return Optional.empty();
        }

        final Comparator<Position> positionComparator = new Positions.PositionComparator();
        return ranges.stream().reduce((accumulator, range) -> {
            final Position accumulatorStart = accumulator.getStart();
            final Position accumulatorEnd = accumulator.getEnd();

            final Position rangeStart = range.getStart();
            final Position rangeEnd = range.getEnd();

            final Position resultStart = positionComparator.compare(accumulatorStart, rangeStart) <= 0
                    ? accumulatorStart
                    : rangeStart;
            final Position resultEnd = positionComparator.compare(accumulatorEnd, rangeEnd) >= 0
                    ? accumulatorEnd
                    : rangeEnd;
            return new Range(resultStart, resultEnd);
        });
    }
}
