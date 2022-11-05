package at.jku.isse.ecco.lsp.domain;

import org.eclipse.lsp4j.Position;

import java.util.Comparator;

public class PositionComparator implements Comparator<Position> {
    @Override
    public int compare(Position p1, Position p2) {
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
