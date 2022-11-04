package at.jku.isse.ecco.lsp.util;

public class Pair<F, S> {
    private F first;
    private S second;

    public Pair(F first, S second) {
        this.first = first;
        this.second = second;
    }

    public F getFirst() {
        return this.first;
    }

    public S getSecond() {
        return this.second;
    }

    @Override
    public int hashCode() {
        return this.first.hashCode() ^ this.second.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }

        if (!(obj instanceof Pair)) {
            return false;
        }

        Pair p = (Pair) obj;
        return p.getFirst().equals(this.first) && p.getSecond().equals(this.second);
    }

    @Override
    public String toString() {
        return "Pair(" + this.first.toString() + ", " + this.second.toString() + ")";
    }
}
