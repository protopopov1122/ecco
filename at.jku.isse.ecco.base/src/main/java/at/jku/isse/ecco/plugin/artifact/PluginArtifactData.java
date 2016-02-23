package at.jku.isse.ecco.plugin.artifact;

import java.nio.file.Path;
import java.nio.file.Paths;

public class PluginArtifactData implements ArtifactData {

	private String pluginId;
	private transient Path path = null;
	private String pathString = null;

	protected PluginArtifactData() {
		this.pluginId = null;
		this.path = null;
		this.pathString = null;
	}

	public PluginArtifactData(String pluginId, Path path) {
		this.pluginId = pluginId;
		this.path = path;
		this.pathString = path.toString();
	}

	public String getPluginId() {
		return this.pluginId;
	}

	public Path getFileName() {
		return this.getPath().getFileName();
	}

	public Path getPath() {
		if (this.path == null && this.pathString != null) {
			this.path = Paths.get(this.pathString);
		}
		return this.path;
	}

	@Override
	public String toString() {
		return this.pluginId + "(" + this.getPath().toString() + ")";
	}

	@Override
	public int hashCode() {
		return this.getPath().hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		PluginArtifactData that = (PluginArtifactData) o;

		return this.getPath().equals(that.getPath());

	}

}
