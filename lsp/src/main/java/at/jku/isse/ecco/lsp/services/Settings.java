package at.jku.isse.ecco.lsp.services;

public class Settings {
    private boolean ignoreColumnsForColoring;

    public Settings() {
        this.ignoreColumnsForColoring = false;
    }

    public boolean getIgnoreColumnsForColoring() {
        return this.ignoreColumnsForColoring;
    }

    public void setIgnoreColumnsForColoring(boolean value) {
        this.ignoreColumnsForColoring = value;
    }

    @Override
    public String toString() {
        return "{ignoreColumnsForColoring=" + this.ignoreColumnsForColoring + "}";
    }
}
