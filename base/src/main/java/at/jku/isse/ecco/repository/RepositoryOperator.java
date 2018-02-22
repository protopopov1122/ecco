package at.jku.isse.ecco.repository;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.EccoUtil;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.composition.LazyCompositionRootNode;
import at.jku.isse.ecco.core.*;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureInstance;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.module.ModuleRevision;
import at.jku.isse.ecco.module.PresenceCondition;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.tree.RootNode;
import at.jku.isse.ecco.util.Associations;
import at.jku.isse.ecco.util.Trees;

import java.util.*;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class RepositoryOperator {

	private Repository.Op repository;
	private EntityFactory entityFactory;

	public RepositoryOperator(Repository.Op repository) {
		this.repository = repository;
		this.entityFactory = repository.getEntityFactory();
	}


	/**
	 * Returns a collection of all features in this repository with the given name.
	 *
	 * @param name Name of the features.
	 * @return Collection of features with given name.
	 */
	public Collection<Feature> getFeaturesByName(String name) {
		Collection<Feature> features = new ArrayList<>();
		for (Feature feature : this.repository.getFeatures()) {
			if (feature.getName().equals(name))
				features.add(feature);
		}
		return features;
	}


	/**
	 * Copies every existing module and adds every new feature negatively.
	 *
	 * @param newFeatures
	 * @return
	 */
	private Collection<ModuleRevision> addFeatures(Collection<Feature> newFeatures) {
		Collection<ModuleRevision> allNewRevisionModules = new ArrayList<>();
		for (Feature feature : newFeatures) {
			allNewRevisionModules.addAll(this.addFeature(feature));
		}
		return allNewRevisionModules;
	}

	/**
	 * Copies every existing module and adds the new feature negatively.
	 *
	 * @param feature
	 * @return
	 */
	private Collection<ModuleRevision> addFeature(Feature feature) {
		// TODO: also add feature to repository here? move code from "extract" method to here?

		Collection<ModuleRevision> newModuleRevisions = new ArrayList<>();
		for (Module module : this.repository.getModules()) {
			// create array of negative features. to be reused also by every revision module.
			Feature[] negFeatures = Arrays.copyOf(module.getNeg(), module.getNeg().length + 1);
			negFeatures[negFeatures.length - 1] = feature;
			// create copy of feature module with new feature negative
			Module newModule = this.entityFactory.createModule(module.getPos(), negFeatures);
			newModule.setCount(module.getCount());
			// make sure it does not already exist. TODO: remove this later. simply check if the feature already exists.
			if (this.repository.getModules().contains(newModule))
				throw new EccoException("ERROR: feature module already exists.");
			// do the same for the revision modules
			for (ModuleRevision revisionModule : module.getRevisions()) {
				// create copy of module revision with new feature negative
//				ModuleRevision newModuleRevision = newModule.createModuleRevision(revisionModule.getPos(), negFeatures);
//				newModuleRevision.setCount(revisionModule.getCount());
//				newModule.addRevision(newModuleRevision);
				ModuleRevision newModuleRevision = newModule.addRevision(revisionModule.getPos(), negFeatures);
				newModuleRevision.setCount(revisionModule.getCount());
				newModuleRevisions.add(newModuleRevision);
			}
		}
		return newModuleRevisions;
	}

	/**
	 * Uses all positive feature revisions of the configuration.
	 * Ignores all negative features and revisions (there should be none in the configuration). TODO: change configuration and remove feature instance type such that this is not possible anymore.
	 * <p>
	 * Expects all features and feature revisions to already exist in the repository.
	 * Uses feature and feature revision instances contained in the repository and discards the instances in the configuration.
	 * <p>
	 * Uses module and module revision instances contained in the repository.
	 * If a module or module revision does not yet exist in the repository it is created and added to the repository.
	 *
	 * @param configuration The configuration to be added to the repository.
	 * @return All module revisions that are contained in the configuration.
	 */
	private Collection<ModuleRevision> addConfiguration(Configuration configuration) {
		checkNotNull(configuration);

		Collection<FeatureInstance> featureInstances = configuration.getFeatureInstances();

		// collect positive feature revisions
		Collection<FeatureRevision> pos = new ArrayList();
		for (FeatureInstance featureInstance : featureInstances) {
			if (featureInstance.getSign()) {
				// get feature from repository
				Feature repoFeature = this.repository.getFeature(featureInstance.getFeature().getId());
				if (repoFeature == null)
					throw new EccoException("ERROR: feature does not exist in repository: " + featureInstance.getFeature());
				// get feature revision from repository
				FeatureRevision repoFeatureRevision = repoFeature.getRevision(featureInstance.getFeatureVersion().getId());
				if (repoFeatureRevision == null)
					throw new EccoException("ERROR: feature revision does not exist inr epository: " + featureInstance.getFeatureVersion());
				pos.add(featureInstance.getFeatureVersion());
			} else {
				//neg.add(featureInstance.getFeature());
			}
		}

		// collect negative features
		Collection<Feature> neg = new ArrayList();
		for (Feature feature : this.repository.getFeatures()) {
			if (!pos.stream().anyMatch(featureRevision -> featureRevision.getFeature().equals(feature))) {
				neg.add(feature);
			}
		}

		// add empty module initially
		Collection<ModuleRevision> modules = new ArrayList();
		ModuleRevision emptyModule = this.entityFactory.createModuleRevision(new FeatureRevision[]{}, new Feature[]{});
		modules.add(emptyModule); // add empty module to power set

		// compute powerset
		for (final FeatureRevision featureRevision : pos) {
			final Collection<ModuleRevision> toAdd = new ArrayList<>();

			for (final ModuleRevision module : modules) {
				if (module.getOrder() < this.repository.getMaxOrder()) {
					FeatureRevision[] posFeatureRevisions = Arrays.copyOf(module.getPos(), module.getPos().length + 1);
					posFeatureRevisions[posFeatureRevisions.length - 1] = featureRevision;

					// get module revision from repository if it already exists, otherwise a new module revision is created and if necessary also a new module
					ModuleRevision newModule = this.repository.getModuleRevision(posFeatureRevisions, module.getNeg());
					newModule.incCount();

					toAdd.add(newModule);
				}
			}

			modules.addAll(toAdd);
		}

		// remove the empty module again
		modules.remove(emptyModule);

		for (final Feature feature : neg) {
			final Collection<ModuleRevision> toAdd = new ArrayList<>();

			for (final ModuleRevision module : modules) {
				if (module.getOrder() < this.repository.getMaxOrder() && module.getPos().length > 0) {
					Feature[] negFeatures = Arrays.copyOf(module.getNeg(), module.getNeg().length + 1);
					negFeatures[negFeatures.length - 1] = feature;

					// get module revision from repository if it already exists, otherwise a new module revision is created and if necessary also a new module
					ModuleRevision newModule = this.repository.getModuleRevision(module.getPos(), negFeatures);
					newModule.incCount();

					toAdd.add(newModule);
				}
			}

			modules.addAll(toAdd);
		}

		return modules;
	}


	/**
	 * Commits a set of artifact nodes as a given configuration to the repository and returns the resulting commit object, or null in case of an error.
	 *
	 * @param configuration The configuration that is committed.
	 * @param nodes         The artifact nodes that implement the given configuration.
	 * @return The resulting commit object or null in case of an error.
	 */
	public Commit extract(Configuration configuration, Set<Node.Op> nodes) {
		checkNotNull(configuration);
		checkNotNull(nodes);

		// TODO: restructure the following code together with this.addFeatures and this.addConfiguration!

		// add new features and feature revisions from configuration to this repository
		Collection<Feature> newFeatures = new ArrayList<>();
		Collection<FeatureRevision> newFeatureRevisions = new ArrayList<>();
		Configuration newConfiguration = this.entityFactory.createConfiguration();
		for (FeatureInstance featureInstance : configuration.getFeatureInstances()) {
			Feature feature = featureInstance.getFeature();
			Feature repoFeature = this.repository.getFeature(featureInstance.getFeature().getId());
			if (repoFeature == null) {
				repoFeature = this.repository.addFeature(feature.getId(), feature.getName(), feature.getDescription());
				newFeatures.add(repoFeature);
			}
			FeatureRevision featureRevision = featureInstance.getFeatureVersion();
			FeatureRevision repoFeatureRevision = repoFeature.getRevision(featureRevision.getId());
			if (repoFeatureRevision == null) {
				repoFeatureRevision = repoFeature.addRevision(featureRevision.getId());
				repoFeatureRevision.setDescription(featureRevision.getDescription());
				newFeatureRevisions.add(repoFeatureRevision);
			}
			FeatureInstance newFeatureInstance = repoFeatureRevision.getInstance(featureInstance.getSign());
			newConfiguration.addFeatureInstance(newFeatureInstance);
		}

		// add new modules to the repository that contain the new features negatively
		Collection<ModuleRevision> newModuleRevisions = this.addFeatures(newFeatures);

		// update existing associations that have matching old modules with the new modules
		for (Association.Op association : this.repository.getAssociations()) {
			association.updateWithNewModules(newModuleRevisions);
		}

		// -------------------------------------------------------------------------------------

		// compute modules for configuration. new modules are added to the repository. old ones have their counter incremented.
		Collection<ModuleRevision> moduleRevisions = this.addConfiguration(newConfiguration);

		// create association
		Association.Op association = this.entityFactory.createAssociation(nodes);
		association.setId(UUID.randomUUID().toString());

		// initialize new association
		association.setCount(1);
		for (ModuleRevision moduleRevision : moduleRevisions) {
			association.addObservation(moduleRevision);
		}

		// do actual extraction
		this.extract(association);

		// -------------------------------------------------------------------------------------

		// create commit object
		Commit commit = this.entityFactory.createCommit();
		commit.setConfiguration(newConfiguration);

		return commit;
	}

	/**
	 * When associations are committed directly then the corresponding configuration must be added manually first!
	 *
	 * @param inputAs The collection of associations to be committed.
	 */
	protected void extract(Collection<? extends Association.Op> inputAs) {
		checkNotNull(inputAs);

		for (Association.Op inputA : inputAs) {
			this.extract(inputA);
		}
	}

	/**
	 * When an association is committed directly then the corresponding configuration must be added manually first!
	 *
	 * @param association The association to be committed.
	 */
	protected void extract(Association.Op association) {
		checkNotNull(association);

		Collection<? extends Association.Op> originalAssociations = this.repository.getAssociations();

		Collection<Association.Op> toAdd = new ArrayList<>();
		Collection<Association.Op> toRemove = new ArrayList<>();

		// slice new association with every original association
		for (Association.Op origA : originalAssociations) {
			// ASSOCIATION
			// slice the associations. the order matters here! the "left" association's featuers and artifacts are maintained. the "right" association's features and artifacts are replaced by the "left" association's.
			Association.Op intA = this.entityFactory.createAssociation();
			intA.setId(UUID.randomUUID().toString());

			// ARTIFACT TREE
			//intA.setRootNode((origA.getRootNode().slice(inputA.getRootNode())));
			intA.setRootNode((RootNode.Op) Trees.slice(origA.getRootNode(), association.getRootNode()));

			// INTERSECTION
			if (!intA.getRootNode().getChildren().isEmpty()) { // if the intersection association has artifacts store it
				toAdd.add(intA);

				Trees.checkConsistency(intA.getRootNode());

				intA.add(origA);
				intA.add(association);
			}

			// ORIGINAL
			if (!origA.getRootNode().getChildren().isEmpty()) { // if the original association has artifacts left
				Trees.checkConsistency(origA.getRootNode());
			} else {
				toRemove.add(origA);
			}
		}

		// REMAINDER
		if (!association.getRootNode().getChildren().isEmpty()) { // if the remainder is not empty store it
			toAdd.add(association);

			Trees.sequence(association.getRootNode());
			Trees.updateArtifactReferences(association.getRootNode());
			Trees.checkConsistency(association.getRootNode());
		}

		// remove associations from repository
		for (Association.Op origA : toRemove) {
			this.repository.removeAssociation(origA);
		}

		// add associations to repository
		for (Association.Op newA : toAdd) {
			this.repository.addAssociation(newA);
		}
	}


	public Checkout compose(Configuration configuration) {
		return this.compose(configuration, true);
	}

	public Checkout compose(Configuration configuration, boolean lazy) {
		checkNotNull(configuration);

		Set<Association> selectedAssociations = new HashSet<>();
		for (Association association : this.repository.getAssociations()) {
			if (association.getPresenceCondition().holds(configuration)) {
				selectedAssociations.add(association);
			}
		}

		Checkout checkout = this.compose(selectedAssociations, lazy);
		checkout.setConfiguration(configuration);


		Set<at.jku.isse.ecco.module.Module> desiredModules = configuration.computeModules(this.repository.getMaxOrder());
		Set<at.jku.isse.ecco.module.Module> missingModules = new HashSet<>();
		Set<at.jku.isse.ecco.module.Module> surplusModules = new HashSet<>();

		for (Association association : selectedAssociations) {
			// compute missing
			for (at.jku.isse.ecco.module.Module desiredModule : desiredModules) {
				if (!association.getPresenceCondition().getMinModules().contains(desiredModule)) {
					missingModules.add(desiredModule);
				}
			}
			// compute surplus
			for (at.jku.isse.ecco.module.Module existingModule : association.getPresenceCondition().getMinModules()) {
				if (!desiredModules.contains(existingModule)) {
					surplusModules.add(existingModule);
				}
			}
		}

		checkout.getSurplus().addAll(surplusModules);
		checkout.getMissing().addAll(missingModules);

		return checkout;
	}

	public Checkout compose(Collection<Association> selectedAssociations, boolean lazy) {
		Node compRootNode;
		Collection<Artifact<?>> orderWarnings;
		if (lazy) {
			LazyCompositionRootNode lazyCompRootNode = new LazyCompositionRootNode();

			for (Association association : selectedAssociations) {
				lazyCompRootNode.addOrigNode(association.getRootNode());
			}

			orderWarnings = lazyCompRootNode.getOrderSelector().getUncertainOrders();

			compRootNode = lazyCompRootNode;
		} else {
			// TODO: non-lazy composition and computation of order warnings!
			throw new EccoException("Non-lazy composition not yet implemented!");
		}

		// compute unresolved dependencies
		DependencyGraph dg = new DependencyGraph(selectedAssociations, DependencyGraph.ReferencesResolveMode.INCLUDE_ALL_REFERENCED_ASSOCIATIONS);
		Set<Association> unresolvedAssociations = new HashSet<>(dg.getAssociations());
		unresolvedAssociations.removeAll(selectedAssociations);

		// put together result
		Checkout checkout = new Checkout();
		checkout.setNode(compRootNode);
		checkout.getOrderWarnings().addAll(orderWarnings);
		checkout.getUnresolvedAssociations().addAll(unresolvedAssociations);
		checkout.getSelectedAssociations().addAll(selectedAssociations);

		return checkout;
	}


	/**
	 * Maps the given tree (e.g. result from a reader) to the repository without modifying the repository by replacing the artifacts in the given tree.
	 * With this way a reader could keep reading a file after it was changed, map it to the repository, and have the trace information again.
	 * The nodes contain the updated line/col information from the reader, and the marking can still be done on the artifacts in the repository.
	 * This also enables highlighting of selected associations in changed files.
	 *
	 * @param nodes The tree to be mapped.
	 */
	public void map(Collection<RootNode> nodes) {
		Collection<? extends Association> associations = this.repository.getAssociations();

		for (Node.Op node : nodes) {
			for (Association association : associations) {
				Trees.map(association.getRootNode(), node);
			}
		}
	}


	/**
	 * Diffs the current working copy against the repository and returns a diff object containing all affected associations (and thus all affected features and artifacts).
	 *
	 * @return The diff object.
	 */
	public Diff diff() {
		// TODO
		return null;
	}


	/**
	 * Creates a copy of this repository using the same entity factory and maximum order of modules. This repository is not changed.
	 *
	 * @return The copy of the repository.
	 */
	public Repository.Op copy(EntityFactory entityFactory) {
		return this.subset(new ArrayList<>(), this.repository.getMaxOrder(), entityFactory);
	}


	/**
	 * Creates a subset repository of this repository using the given entity factory. This repository is not changed.
	 *
	 * @param deselected The deselected feature versions (i.e. feature versions that are set to false).
	 * @param maxOrder   The maximum order of modules.
	 * @return The subset repository.
	 */
	public Repository.Op subset(Collection<FeatureRevision> deselected, int maxOrder, EntityFactory entityFactory) {
		checkNotNull(deselected);
		checkArgument(maxOrder <= this.repository.getMaxOrder());


		// create empty repository using the given entity factory
		Repository.Op newRepository = entityFactory.createRepository();
		newRepository.setMaxOrder(maxOrder);


		// add all features and versions in this repository to new repository, excluding the deselected feature versions.
		Map<Feature, Feature> featureReplacementMap = new HashMap<>();
		Map<FeatureRevision, FeatureRevision> featureVersionReplacementMap = new HashMap<>();
		Collection<FeatureRevision> newFeatureVersions = new ArrayList<>();
		for (Feature feature : this.repository.getFeatures()) {
			Feature newFeature = newRepository.addFeature(feature.getId(), feature.getName(), feature.getDescription());

			for (FeatureRevision featureVersion : feature.getRevisions()) {
				if (!deselected.contains(featureVersion)) {
					FeatureRevision newFeatureVersion = newFeature.addRevision(featureVersion.getId());
					newFeatureVersion.setDescription(featureVersion.getDescription());
					newFeatureVersions.add(newFeatureVersion);
					featureVersionReplacementMap.put(featureVersion, newFeatureVersion);
				}
			}

			if (!newFeature.getRevisions().isEmpty()) {
				featureReplacementMap.put(feature, newFeature);
			}
		}
		for (Association newAssociation : newRepository.getAssociations()) {
			for (FeatureRevision newFeatureVersion : newFeatureVersions) {
				newAssociation.getPresenceCondition().addFeatureVersion(newFeatureVersion);
				newAssociation.getPresenceCondition().addFeatureInstance(newFeatureVersion, false, newRepository.getMaxOrder());
			}
		}


		// copy associations in this repository and add them to new repository, but exclude modules or module features that evaluate to false given the deselected feature versions
		Collection<Association.Op> copiedAssociations = new ArrayList<>();
		for (Association association : this.repository.getAssociations()) {
			Association.Op copiedAssociation = entityFactory.createAssociation();
			copiedAssociation.setId(UUID.randomUUID().toString());

			PresenceCondition thisPresenceCondition = association.getPresenceCondition();


			// copy presence condition
			PresenceCondition copiedPresenceCondition = entityFactory.createPresenceCondition();
			copiedAssociation.setPresenceCondition(copiedPresenceCondition);

			Set<at.jku.isse.ecco.module.Module>[][] moduleSetPairs = new Set[][]{{thisPresenceCondition.getMinModules(), copiedPresenceCondition.getMinModules()}, {thisPresenceCondition.getMaxModules(), copiedPresenceCondition.getMaxModules()}, {thisPresenceCondition.getNotModules(), copiedPresenceCondition.getNotModules()}, {thisPresenceCondition.getAllModules(), copiedPresenceCondition.getAllModules()}};

			for (Set<at.jku.isse.ecco.module.Module>[] moduleSetPair : moduleSetPairs) {
				Set<at.jku.isse.ecco.module.Module> fromModuleSet = moduleSetPair[0];
				Set<at.jku.isse.ecco.module.Module> toModuleSet = moduleSetPair[1];

				for (at.jku.isse.ecco.module.Module fromModule : fromModuleSet) {
					at.jku.isse.ecco.module.Module toModule = entityFactory.createModule();
					for (ModuleFeature fromModuleFeature : fromModule) {

						// feature
						Feature fromFeature = fromModuleFeature.getFeature();
						Feature toFeature;
						if (featureReplacementMap.containsKey(fromFeature)) {
							toFeature = featureReplacementMap.get(fromFeature);

							// if a deselected feature version is contained in module feature:
							//  if module feature is positive: remove / do not add feature version from module feature
							//   if module feature is empty: remove it / do not add it
							//  else if module feature is negative: remove module feature from module
							//   if module is empty (should not happen?) then leave it! module is always TRUE (again: should not happen, because at least one positive module feature should be in every module, but that might currently not be the case)

							ModuleFeature toModuleFeature = entityFactory.createModuleFeature(toFeature, fromModuleFeature.getSign());
							boolean addToModule = true;
							for (FeatureRevision fromFeatureVersion : fromModuleFeature) {
								if (deselected.contains(fromFeatureVersion)) { // if a deselected feature version is contained in module feature

									if (fromModuleFeature.getSign()) {  // if module feature is positive
										// do not add feature version to module feature
									} else {
										// do not add module feature to module because it is always true
										addToModule = false;
										break;
									}

								} else { // ordinary copy
									FeatureRevision toFeatureVersion;
									if (featureVersionReplacementMap.containsKey(fromFeatureVersion)) {
										toFeatureVersion = featureVersionReplacementMap.get(fromFeatureVersion);
									} else {
										toFeatureVersion = fromFeatureVersion;

										throw new EccoException("This should not happen!");
									}
									toModuleFeature.add(toFeatureVersion);
								}
							}
							if (!toModuleFeature.isEmpty() && addToModule) { // if module feature is empty: do not add it
								toModule.add(toModuleFeature);
							}
							if (fromModuleFeature.getSign() && toModuleFeature.isEmpty()) { // don't add module because it is false
								toModule.clear();
								break;
							}
						} else {
							//toFeature = fromFeature;
							//throw new EccoException("This should not happen!");
							if (fromModuleFeature.getSign()) {
								toModule.clear();
								break;
							}
						}

					}
					if (!toModule.isEmpty())
						toModuleSet.add(toModule);
				}
			}


			// copy artifact tree
			RootNode.Op copiedRootNode = entityFactory.createRootNode();
			copiedAssociation.setRootNode(copiedRootNode);
			// clone tree
			for (Node.Op parentChildNode : association.getRootNode().getChildren()) {
				Node.Op copiedChildNode = EccoUtil.deepCopyTree(parentChildNode, entityFactory);
				copiedRootNode.addChild(copiedChildNode);
				copiedChildNode.setParent(copiedRootNode);
			}
			//Trees.checkConsistency(copiedRootNode);


			copiedAssociations.add(copiedAssociation);
		}

		for (Association a : copiedAssociations) {
			Trees.checkConsistency(a.getRootNode());
		}


		// remove (fixate) all provided (selected) feature instances in the presence conditions of the copied associations.
		// this is already done in the previous step

		// remove cloned associations with empty PCs.
		Iterator<Association.Op> associationIterator = copiedAssociations.iterator();
		while (associationIterator.hasNext()) {
			Association.Op association = associationIterator.next();
			if (association.getPresenceCondition().isEmpty())
				associationIterator.remove();
		}

		// compute dependency graph for selected associations and check if there are any unresolved dependencies.
		DependencyGraph dg = new DependencyGraph(copiedAssociations, DependencyGraph.ReferencesResolveMode.LEAVE_REFERENCES_UNRESOLVED); // we do not trim unresolved references. instead we abort.
		if (!dg.getUnresolvedDependencies().isEmpty()) {
			throw new EccoException("Unresolved dependencies in selection.");
		}

		// merge cloned associations with equal PCs.
		Associations.consolidate(copiedAssociations);

		// trim sequence graphs to only contain artifacts from the selected associations.
		EccoUtil.trimSequenceGraph(copiedAssociations);

		for (Association.Op copiedAssociation : copiedAssociations) {
			newRepository.addAssociation(copiedAssociation);
		}

		return newRepository;
	}


	/**
	 * Merges other repository into this repository. The other repository is destroyed in the process.
	 *
	 * @param other The other repository.
	 */
	public void merge(Repository.Op other) {
		checkNotNull(other);
		checkArgument(other.getClass().equals(this.repository.getClass()));

		// step 1: add new features and versions in other repository to associations in this repository,
		Map<Feature, Feature> featureReplacementMap = new HashMap<>();
		Map<FeatureRevision, FeatureRevision> featureVersionReplacementMap = new HashMap<>();
		Collection<FeatureRevision> newThisFeatureVersions = new ArrayList<>();
		for (Feature otherFeature : other.getFeatures()) {
			Feature thisFeature = this.repository.getFeature(otherFeature.getId()); // TODO: what to do when parent and child feature have different description? e.g. because it was changed on one of the two before the pull.
			if (thisFeature == null) {
				thisFeature = this.repository.addFeature(otherFeature.getId(), otherFeature.getName(), otherFeature.getDescription());
			}

			for (FeatureRevision otherFeatureVersion : otherFeature.getRevisions()) {
				FeatureRevision thisFeatureVersion = thisFeature.getRevision(otherFeatureVersion.getId());
				if (thisFeatureVersion == null) {
					thisFeatureVersion = thisFeature.addRevision(otherFeatureVersion.getId());
					thisFeatureVersion.setDescription(otherFeatureVersion.getDescription());
					newThisFeatureVersions.add(thisFeatureVersion);
				}
				featureVersionReplacementMap.put(otherFeatureVersion, thisFeatureVersion);
			}

			if (!thisFeature.getRevisions().isEmpty()) {
				featureReplacementMap.put(otherFeature, thisFeature);
			}
		}
		for (Association thisAssociation : this.repository.getAssociations()) {
			for (FeatureRevision newThisFeatureVersion : newThisFeatureVersions) {
				thisAssociation.getPresenceCondition().addFeatureVersion(newThisFeatureVersion);
				thisAssociation.getPresenceCondition().addFeatureInstance(newThisFeatureVersion, false, this.repository.getMaxOrder());
			}
		}

		// step 2: add new features in this repository to associations in other repository.
		Collection<FeatureRevision> newOtherFeatureVersions = new ArrayList<>();
		for (Feature thisFeature : this.repository.getFeatures()) {
			Feature otherFeature = other.getFeature(thisFeature.getId());
			if (otherFeature == null) {
				// add all its versions to list
				for (FeatureRevision thisFeatureVersion : thisFeature.getRevisions()) {
					newOtherFeatureVersions.add(thisFeatureVersion);
				}
			} else {
				// compare versions and add new ones to list
				for (FeatureRevision thisFeatureVersion : thisFeature.getRevisions()) {
					FeatureRevision otherFeatureVersion = otherFeature.getRevision(thisFeatureVersion.getId());
					if (otherFeatureVersion == null) {
						newOtherFeatureVersions.add(thisFeatureVersion);
					}
				}
			}
		}
		for (Association otherAssociation : other.getAssociations()) {
			for (FeatureRevision newOtherFeatureVersion : newOtherFeatureVersions) {
				otherAssociation.getPresenceCondition().addFeatureVersion(newOtherFeatureVersion);
				otherAssociation.getPresenceCondition().addFeatureInstance(newOtherFeatureVersion, false, other.getMaxOrder());
			}
		}

		// step 3: commit associations in other repository to this repository.
		this.extract(other.getAssociations());
	}


	/**
	 * Splits all marked artifacts in the repository from their previous association into a new one.
	 *
	 * @return The commit object containing the affected associations.
	 */
	public Commit split() { // TODO: the presence condition must also somehow be marked and extracted! otherwise the repo becomes inconsistent.
		Commit commit = this.entityFactory.createCommit();

		Collection<? extends Association.Op> originalAssociations = this.repository.getAssociations();
		Collection<Association.Op> newAssociations = new ArrayList<>();

		// extract from every  original association
		for (Association.Op origA : originalAssociations) {

			// ASSOCIATION
			Association.Op extractedA = this.entityFactory.createAssociation();
			extractedA.setId(UUID.randomUUID().toString());


			// PRESENCE CONDITION
			//extractedA.setPresenceCondition(this.entityFactory.createPresenceCondition(origA.getPresenceCondition())); // copy presence condition
			extractedA.setPresenceCondition(this.entityFactory.createPresenceCondition()); // new empty presence condition


			// ARTIFACT TREE
			RootNode.Op extractedTree = (RootNode.Op) Trees.extractMarked(origA.getRootNode());
			if (extractedTree != null)
				extractedA.setRootNode(extractedTree);


			// if the intersection association has artifacts or a not empty presence condition store it
			if (extractedA.getRootNode() != null && (extractedA.getRootNode().getChildren().size() > 0 || !extractedA.getPresenceCondition().isEmpty())) {
				// set parents for intersection association (and child for parents)
				extractedA.setName("EXTRACTED " + origA.getId());

				// store association
				newAssociations.add(extractedA);
			}

			Trees.checkConsistency(origA.getRootNode());
			if (extractedA.getRootNode() != null)
				Trees.checkConsistency(extractedA.getRootNode());
		}

		for (Association.Op newA : newAssociations) {
			this.repository.addAssociation(newA);

//			commit.addAssociation(newA);
		}

		return commit;
	}


	/**
	 * Merges all associations that have the same presence condition.
	 */
	protected void consolidateAssociations() {
		Collection<Association.Op> toRemove = new ArrayList<>();

		Map<PresenceCondition, Association.Op> pcToAssocMap = new HashMap<>();

		Collection<? extends Association.Op> associations = this.repository.getAssociations();
		Iterator<? extends Association.Op> it = associations.iterator();
		while (it.hasNext()) {
			Association.Op association = it.next();
			Association.Op equalAssoc = pcToAssocMap.get(association.getPresenceCondition());
			if (equalAssoc == null) {
				pcToAssocMap.put(association.getPresenceCondition(), association);
			} else {
				Trees.merge(equalAssoc.getRootNode(), association.getRootNode());
				toRemove.add(association);
				it.remove();
			}
		}

		// delete removed associations
		for (Association.Op a : toRemove) {
			repository.removeAssociation(a);
		}
	}

	protected void mergeEmptyAssociations() {
		Collection<? extends Association.Op> originalAssociations = this.repository.getAssociations();
		Collection<Association.Op> toRemove = new ArrayList<>();
		Association emptyAssociation = null;

		// look for empty association
		for (Association originalAssociation : originalAssociations) {
			if (originalAssociation.getRootNode().getChildren().isEmpty()) {
				emptyAssociation = originalAssociation;
				break;
			}
		}
		if (emptyAssociation == null) { // if no empty association was found we are done
			return;
		}


		// TODO


		// delete removed associations
		for (Association.Op a : toRemove) {
			this.repository.removeAssociation(a);
		}
	}

}
