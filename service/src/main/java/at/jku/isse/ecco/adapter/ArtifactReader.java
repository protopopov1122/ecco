package at.jku.isse.ecco.adapter;

import at.jku.isse.ecco.service.listener.ReadListener;

import java.io.InputStream;
import java.util.Map;

/**
 * An ArtifactReader is responsible for transforming a particular type of artifact into an Ecco tree.
 *
 * @param <I> The input to the reader.
 * @param <O> The output of the reader.
 */
public interface ArtifactReader<I, O> {

	public String getPluginId();


	public Map<Integer, String[]> getPrioritizedPatterns();


	public O read(I base, I[] input);

	public O read(I[] input);


	public void addListener(ReadListener listener);

	public void removeListener(ReadListener listener);

	default O read(I input, InputStream is) {
		throw new UnsupportedOperationException("Reading from InputStram is not supported in " + this.getPluginId() + " reader");
	}

}
