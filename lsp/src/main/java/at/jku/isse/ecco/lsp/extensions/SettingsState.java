package at.jku.isse.ecco.lsp.extensions;

public class SettingsState {
    private boolean ignoreColumnsForColoring;

    public SettingsState(boolean ignoreColumnsForColoring) {
        this.ignoreColumnsForColoring = ignoreColumnsForColoring;
    }

    public boolean getIgnoreColumnsForColoring() {
        return this.ignoreColumnsForColoring;
    }
}
