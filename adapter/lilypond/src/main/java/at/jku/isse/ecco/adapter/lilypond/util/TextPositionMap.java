package at.jku.isse.ecco.adapter.lilypond.util;

import org.w3c.dom.Text;

import java.io.IOException;
import java.io.LineNumberReader;
import java.io.Reader;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public class TextPositionMap {
    private final TreeMap<Integer, Integer> map;

    public static class TextPosition {
        private final int line;
        private final int column;

        public TextPosition(int line, int column) {
            this.line = line;
            this.column = column;
        }

        public int getLine() {
            return this.line;
        }

        public int getColumn() {
            return this.column;
        }
    }

    public TextPositionMap() {
        this.map = new TreeMap<>();
    }

    public Optional<TextPosition> get(final int offset) {
        final Map.Entry<Integer, Integer> entry = this.map.floorEntry(offset);
        if (entry != null) {
            final int lineNumber = entry.getValue();
            final int column = offset - entry.getKey() + 1;
            return Optional.of(new TextPosition(lineNumber, column));
        } else {
            return Optional.empty();
        }
    }

    public void buildMap(final Reader reader) throws IOException {
        int lastLineNumber = -1;
        try (final LineNumberReader lineNumberReader = new LineNumberReader(reader)) {
            lineNumberReader.setLineNumber(1);
            for (int offset = 0; lineNumberReader.ready(); lineNumberReader.read(), offset++) {
                int lineNumber = lineNumberReader.getLineNumber();
                if (lineNumber > lastLineNumber) {
                    lastLineNumber = lineNumber;
                    this.map.put(offset, lineNumber);
                }
            }
        }
    }
}
