package org.eclipse.bpmn2.modeler.ui.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// EcoreFS begin: publishes models and gives every model that imports them by CID a new version
/**
 * Publishes models and every model that imports them by CID, so that the
 * importers name the new versions.
 *
 * <p>
 * An import with an {@code ipfs://} location pins one version of the imported
 * model. When that model changes, it gets a new CID, and the importers keep
 * the old one until they get a new version too. Each of those new versions has
 * a new CID in turn, so the change travels up the chain of imports. This is
 * the cascade that {@code saveIPFSCascaded} performs in the core plug-in of
 * EcoreFS, here for BPMN files in a workspace.
 * </p>
 *
 * <p>
 * {@link #plan(IPFSWorkingCopy, List, IPFSModelStore)} plans the cascade for one
 * model, and {@link #planProject(List, IPFSModelStore)} for every model of a
 * folder, whose imports of each other then name the versions published
 * together. {@link #publish()} publishes each model after the models of the
 * publication that it imports. A model gets new locations in its imports and
 * no other change: a working copy gets them written into its file, and a
 * version that exists only on IPFS gets them in the new bytes that the
 * cascade publishes.
 * </p>
 */
public final class IPFSImportCascade {

	private static final String IPFS_SCHEME_PREFIX = "ipfs://"; //$NON-NLS-1$

	private final IPFSModelStore store;
	private final List<IPFSCascadeModel> startModels;
	private final List<IPFSCascadeModel> modelsToPublish;

	/** Creates a planned cascade; the two plan methods are the entry points. */
	private IPFSImportCascade(IPFSModelStore store, List<IPFSCascadeModel> startModels,
			List<IPFSCascadeModel> modelsToPublish) {
		this.store = store;
		this.startModels = Collections.unmodifiableList(new ArrayList<IPFSCascadeModel>(startModels));
		this.modelsToPublish = Collections.unmodifiableList(new ArrayList<IPFSCascadeModel>(modelsToPublish));
	}

	/**
	 * Plans the cascade for publishing one working copy. Nothing is published
	 * or written yet.
	 *
	 * @param publishedFile the working copy to publish
	 * @param projectFiles  all model files of its project, where the cascade looks for importers
	 * @param store         where pinned versions are read and new versions published
	 * @return the planned cascade
	 * @throws IOException          when a working copy or a pinned version cannot be read
	 * @throws IPFSCascadeException when the cascade would have to guess which model or version is meant
	 */
	public static IPFSImportCascade plan(IPFSWorkingCopy publishedFile, List<IPFSWorkingCopy> projectFiles,
			IPFSModelStore store) throws IOException, IPFSCascadeException {
		IPFSCascadePlanner planner = new IPFSCascadePlanner(Collections.singletonList(publishedFile), projectFiles,
				store);
		List<IPFSCascadeModel> order = planner.plan();
		return new IPFSImportCascade(store, planner.getPublishedModels(), order);
	}

	/**
	 * Plans the publication of every model of a folder as one consistent set:
	 * an import of a model of the folder names the version published with it,
	 * and versions on IPFS that the folder pins get new versions when they
	 * import a model of the folder. Nothing is published or written yet.
	 *
	 * @param folderFiles the model files of the folder
	 * @param store       where pinned versions are read and new versions published
	 * @return the planned publication
	 * @throws IOException          when a working copy or a pinned version cannot be read
	 * @throws IPFSCascadeException when the publication would have to guess which model or version is meant
	 */
	public static IPFSImportCascade planProject(List<IPFSWorkingCopy> folderFiles, IPFSModelStore store)
			throws IOException, IPFSCascadeException {
		IPFSCascadePlanner planner = new IPFSCascadePlanner(folderFiles, folderFiles, store);
		List<IPFSCascadeModel> order = planner.plan();
		return new IPFSImportCascade(store, planner.getPublishedModels(), order);
	}

	/** Returns the model that the user publishes, the first one for a folder. */
	public IPFSCascadeModel getPublishedModel() {
		return startModels.get(0);
	}

	/** Returns the models that the user publishes, in the order given. */
	public List<IPFSCascadeModel> getStartModels() {
		return startModels;
	}

	/**
	 * Returns the models that get a new version because they import a
	 * published model, directly or through other models, in publication order.
	 */
	public List<IPFSCascadeModel> getImporters() {
		List<IPFSCascadeModel> importers = new ArrayList<IPFSCascadeModel>();
		for (IPFSCascadeModel model : modelsToPublish) {
			if (!startModels.contains(model)) {
				importers.add(model);
			}
		}
		return importers;
	}

	/** Returns every model of the publication, in publication order. */
	public List<IPFSCascadeModel> getModelsToPublish() {
		return modelsToPublish;
	}

	/**
	 * Returns the working copies whose files may get new import locations:
	 * those with an import of another model of the publication.
	 */
	public List<IPFSWorkingCopy> getWorkingCopiesToRewrite() {
		List<IPFSWorkingCopy> files = new ArrayList<IPFSWorkingCopy>();
		for (IPFSCascadeModel model : modelsToPublish) {
			if (model.isWorkingCopy() && importsAModelOfThePublication(model)) {
				files.add(model.getWorkingCopy());
			}
		}
		return files;
	}

	/**
	 * Returns the models published so far, in order. After a failure, these
	 * are the models whose new versions exist; their import locations name
	 * versions that exist too.
	 */
	public List<IPFSCascadeModel> getPublishedSoFar() {
		List<IPFSCascadeModel> done = new ArrayList<IPFSCascadeModel>();
		for (IPFSCascadeModel model : modelsToPublish) {
			if (model.getNewCid() != null) {
				done.add(model);
			}
		}
		return done;
	}

	/**
	 * Publishes every model of the publication, each after the models of the
	 * publication that it imports. A working copy is written before it is
	 * published, so a failure leaves every written file naming versions that
	 * exist.
	 *
	 * @throws IOException when a file cannot be written or the node refuses a version;
	 *                     {@link #getPublishedSoFar()} tells how far the cascade got
	 */
	public void publish() throws IOException {
		for (IPFSCascadeModel model : modelsToPublish) {
			publishModel(model);
		}
	}

	/**
	 * Writes the new CIDs of the models published before into the imports of
	 * one model, then publishes the result.
	 */
	private void publishModel(IPFSCascadeModel model) throws IOException {
		Map<String, String> newLocationByOldLocation = new LinkedHashMap<String, String>();
		List<IPFSCascadeModel.ImportChange> changes = new ArrayList<IPFSCascadeModel.ImportChange>();
		for (IPFSCascadeModel.ImportTarget target : model.getImportTargets()) {
			recordNewLocation(target, newLocationByOldLocation, changes);
		}
		byte[] rewritten = model.getImports().withLocations(newLocationByOldLocation);
		boolean changed = !Arrays.equals(rewritten, model.getContents());
		if (model.isWorkingCopy() && changed) {
			model.getWorkingCopy().writeContents(rewritten);
		}
		String cid = store.publish(model.getPublicationFileName(), rewritten);
		model.recordPublication(cid, model.isWorkingCopy() && changed, changes);
	}

	/**
	 * Adds the new location of one import, when the version it refers to got a
	 * new version in this publication, and records the change once per old
	 * location.
	 */
	private static void recordNewLocation(IPFSCascadeModel.ImportTarget target,
			Map<String, String> newLocationByOldLocation, List<IPFSCascadeModel.ImportChange> changes) {
		String newCid = target.getModel().getNewCid();
		if (newCid == null) {
			return;
		}
		String oldLocation = target.getDeclaration().getLocation();
		String newLocation = IPFS_SCHEME_PREFIX + newCid;
		if (!newLocation.equals(oldLocation) && !newLocationByOldLocation.containsKey(oldLocation)) {
			changes.add(new IPFSCascadeModel.ImportChange(target.getDeclaration().getNamespace(), oldLocation,
					newLocation));
		}
		newLocationByOldLocation.put(oldLocation, newLocation);
	}

	/** Tells whether a model imports another model of the publication. */
	private boolean importsAModelOfThePublication(IPFSCascadeModel model) {
		for (IPFSCascadeModel.ImportTarget target : model.getImportTargets()) {
			if (modelsToPublish.contains(target.getModel())) {
				return true;
			}
		}
		return false;
	}
}
// EcoreFS end: publishes models and gives every model that imports them by CID a new version
