package org.eclipse.bpmn2.modeler.ui.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// EcoreFS begin: publishes a model and gives every model that imports it by CID a new version
/**
 * Publishes a model and every model that imports it by CID, so that the
 * importers name the new version.
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
 * {@link #plan(IPFSWorkingCopy, List, IPFSModelStore)} finds the affected
 * models in the project and on IPFS and orders them, and {@link #publish()}
 * publishes the model, then each importer after the models it imports. An
 * importer gets new locations in its imports and no other change: a working
 * copy gets them written into its file, and a version that exists only on IPFS
 * gets them in the new bytes that the cascade publishes.
 * </p>
 */
public final class IPFSImportCascade {

	private static final String IPFS_SCHEME_PREFIX = "ipfs://"; //$NON-NLS-1$

	private final IPFSModelStore store;
	private final IPFSCascadeModel published;
	private final List<IPFSCascadeModel> importers;

	/** Creates a planned cascade; {@link #plan(IPFSWorkingCopy, List, IPFSModelStore)} is the entry point. */
	private IPFSImportCascade(IPFSModelStore store, IPFSCascadeModel published, List<IPFSCascadeModel> importers) {
		this.store = store;
		this.published = published;
		this.importers = Collections.unmodifiableList(new ArrayList<IPFSCascadeModel>(importers));
	}

	/**
	 * Plans the cascade for publishing a working copy. Nothing is published or
	 * written yet.
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
		IPFSCascadePlanner planner = new IPFSCascadePlanner(publishedFile, projectFiles, store);
		List<IPFSCascadeModel> plannedImporters = planner.planImporters();
		return new IPFSImportCascade(store, planner.getPublished(), plannedImporters);
	}

	/** Returns the model that the user publishes. */
	public IPFSCascadeModel getPublishedModel() {
		return published;
	}

	/** Returns the models that get a new version, in publication order. */
	public List<IPFSCascadeModel> getImporters() {
		return importers;
	}

	/** Returns the working copies among the importers, whose files may get new import locations. */
	public List<IPFSWorkingCopy> getWorkingCopiesToRewrite() {
		List<IPFSWorkingCopy> files = new ArrayList<IPFSWorkingCopy>();
		for (IPFSCascadeModel importer : importers) {
			if (importer.isWorkingCopy()) {
				files.add(importer.getWorkingCopy());
			}
		}
		return files;
	}

	/**
	 * Returns the models published so far, in order. After a failure, these
	 * are the models whose new versions exist; their import locations name
	 * versions that exist too.
	 */
	public List<IPFSCascadeModel> getPublishedModels() {
		List<IPFSCascadeModel> done = new ArrayList<IPFSCascadeModel>();
		if (published.getNewCid() != null) {
			done.add(published);
		}
		for (IPFSCascadeModel importer : importers) {
			if (importer.getNewCid() != null) {
				done.add(importer);
			}
		}
		return done;
	}

	/**
	 * Publishes the model and then each importer, after the models it imports.
	 * A working copy is written before it is published, so a failure leaves
	 * every written file naming versions that exist.
	 *
	 * @throws IOException when a file cannot be written or the node refuses a version;
	 *                     {@link #getPublishedModels()} tells how far the cascade got
	 */
	public void publish() throws IOException {
		String cid = store.publish(published.getPublicationFileName(), published.getContents());
		published.recordPublication(cid, false, Collections.<IPFSCascadeModel.ImportChange>emptyList());
		for (IPFSCascadeModel importer : importers) {
			publishImporter(importer);
		}
	}

	/**
	 * Writes the new CIDs of the published model and of the affected models
	 * into the imports of one importer, then publishes the result.
	 */
	private void publishImporter(IPFSCascadeModel importer) throws IOException {
		Map<String, String> newLocationByOldLocation = new LinkedHashMap<String, String>();
		List<IPFSCascadeModel.ImportChange> changes = new ArrayList<IPFSCascadeModel.ImportChange>();
		for (IPFSCascadeModel.ImportTarget target : importer.getImportTargets()) {
			recordNewLocation(target, newLocationByOldLocation, changes);
		}
		byte[] rewritten = importer.getImports().withLocations(newLocationByOldLocation);
		boolean changed = !Arrays.equals(rewritten, importer.getContents());
		if (importer.isWorkingCopy() && changed) {
			importer.getWorkingCopy().writeContents(rewritten);
		}
		String cid = store.publish(importer.getPublicationFileName(), rewritten);
		importer.recordPublication(cid, importer.isWorkingCopy() && changed, changes);
	}

	/**
	 * Adds the new location of one import, when the version it pins got a new
	 * version in this cascade, and records the change once per old location.
	 */
	private void recordNewLocation(IPFSCascadeModel.ImportTarget target, Map<String, String> newLocationByOldLocation,
			List<IPFSCascadeModel.ImportChange> changes) {
		IPFSCascadeModel pinned = target.getModel() == null ? published : target.getModel();
		if (pinned.getNewCid() == null) {
			return;
		}
		String oldLocation = target.getDeclaration().getLocation();
		String newLocation = IPFS_SCHEME_PREFIX + pinned.getNewCid();
		if (!newLocation.equals(oldLocation) && !newLocationByOldLocation.containsKey(oldLocation)) {
			changes.add(new IPFSCascadeModel.ImportChange(target.getDeclaration().getNamespace(), oldLocation,
					newLocation));
		}
		newLocationByOldLocation.put(oldLocation, newLocation);
	}
}
// EcoreFS end: publishes a model and gives every model that imports it by CID a new version
