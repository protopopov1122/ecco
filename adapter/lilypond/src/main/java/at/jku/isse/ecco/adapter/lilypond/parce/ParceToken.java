package at.jku.isse.ecco.adapter.lilypond.parce;

import java.util.Objects;

/**
 * Represents a wrapper for a Parce-Token (<a href="https://parce.info/tree.html">https://parce.info</a>).
 */
public class ParceToken {
    private final int pos;
    private String text;
    private final String action;
    private Object transformationData;

    /**
     * Returns position of token in original text.
     * @return Position of token in original text.
     */
    public int getPos() { return pos; }

    /**
     * Returns text of token.
     * @return Text of token.
     */
    public String getText() { return text; }

    public void setText(String s) {
        text = s;
    }

    /**
     * Returns action of the token (e.g. Text.Lyrics).
     * @return Action of token.
     */
    public String getAction() { return action; }

    public Object getTransformationData() { return transformationData; }

    public ParceToken(int pos, String text, String action) {
        this.pos = pos;
        this.text = text;
        this.action = action;
    }

    public ParceToken(int pos, String text, String action, Object transformationData) {
        this(pos, text, action);
        this.transformationData = transformationData;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ParceToken that = (ParceToken) o;
        return text.equals(that.text) && action.equals(that.action);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, action);
    }

    @Override
    public String toString() {
        return  "ParceToken{" +
                "pos=" + pos +
                ", action='" + action + "'" +
                ", text='" + text + "'}";
    }
}
