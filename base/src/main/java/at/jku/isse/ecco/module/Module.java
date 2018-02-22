package at.jku.isse.ecco.module;

import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;

import java.util.Collection;

/**
 *
 */
public interface Module {

	/**
	 * The minimum length of this is 1.
	 *
	 * @return The list of positive feature revisions.
	 */
	public Feature[] getPos();

	public default int getOrder() {
		return this.getPos().length + this.getNeg().length - 1;
	}

	public boolean holds(Configuration configuration);

	public Feature[] getNeg();

	public Collection<ModuleRevision> getRevisions();

	public ModuleRevision addRevision(FeatureRevision[] pos, Feature[] neg);

	public ModuleRevision getRevision(FeatureRevision[] pos, Feature[] neg);

	public int getCount();

	public void setCount(int value);

	public void incCount();

}
