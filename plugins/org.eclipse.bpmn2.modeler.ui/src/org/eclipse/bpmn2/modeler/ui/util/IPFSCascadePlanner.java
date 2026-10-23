package org.eclipse.bpmn2.modeler.ui.util;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// EcoreFS begin: finds the models a publication affects and the order in which to republish them
/**
 * Plans a cascade: which model versions import the published model, directly
 * or through other models, and in which order to republish them.
 *
 * <p>
 * The planner reads every working copy of the project and follows their
 * {@code ipfs://} imports to the versions they pin, reading those from IPFS.
 * Models are identified by their target namespace, which is also what a BPMN
 * import names. Every import of the published model's namespace refers to the
 * published model. A pinned version whose bytes equal the working copy of its
 * namespace is that working copy, so its file gets the new locations too.
 * </p>
 *
 * <p>
 * The planner refuses three situations instead of guessing: two working
 * copies declare the namespace of the published model; an affected import
 * pins a version that differs from the working copy of that model; models
 * import each other by CID. Nothing has been published or written when it
 * refuses.
 * </p>
 */
final class IPFSCascadePlanner {

	private final IPFSModelStore store;
	private final IPFSCascadeModel published;
	private final List<IPFSCascadeModel> workingCopies = new ArrayList<IPFSCascadeModel>();
	private final Map<String, List<IPFSCascadeModel>> workingCopiesByNamespace = new HashMap<String, List<IPFSCascadeModel>>();
	private final Map<String, IPFSCascadeModel> versionsByLocation = new HashMap<String, IPFSCascadeModel>();
	private final Set<String> publishedModelLocations = new HashSet<String>();
	private final List<IPFSCascadeModel> pinnedVersions = new ArrayList<IPFSCascadeModel>();
	private final Map<IPFSCascadeModel, List<IPFSCascadeModel>> importersByModel = new HashMap<IPFSCascadeModel, List<IPFSCascadeModel>>();
	private final List<IPFSCascadeModel> importersOfPublished = new ArrayList<IPFSCascadeModel>();

	/**
	 * Reads the published model and every other working copy of the project.
	 *
	 * @param publishedFile the working copy to publish
	 * @param projectFiles  all model files of the project; the published file may be among them
	 * @param store         where pinned versions are read
	 * @throws IOException when a working copy cannot be read; the message names the file
	 */
	IPFSCascadePlanner(IPFSWorkingCopy publishedFile, List<IPFSWorkingCopy> projectFiles, IPFSModelStore store)
			throws IOException {
		this.store = store;
		this.published = IPFSCascadeModel.forWorkingCopy(publishedFile);
		addToNamespaceIndex(published);
		for (IPFSWorkingCopy file : projectFiles) {
			if (!file.getPath().equals(publishedFile.getPath())) {
				IPFSCascadeModel model = IPFSCascadeModel.forWorkingCopy(file);
				workingCopies.add(model);
				addToNamespaceIndex(model);
			}
		}
	}

	/** Returns the version of the published model as the project holds it. */
	IPFSCascadeModel getPublished() {
		return published;
	}

	/**
	 * Finds the model versions that the publication affects and orders them
	 * so that every model comes after the affected models it imports.
	 *
	 * @return the affected versions in publication order, without the published model
	 * @throws IOException          when a pinned version cannot be read from IPFS
	 * @throws IPFSCascadeException when the cascade would have to guess
	 */
	List<IPFSCascadeModel> planImporters() throws IOException, IPFSCascadeException {
		checkPublishedNamespaceIsUnique();
		resolveImportsOf(published);
		for (IPFSCascadeModel model : workingCopies) {
			resolveImportsOf(model);
		}
		// Resolving a pinned version can pin further versions, which the loop then reaches
		for (int index = 0; index < pinnedVersions.size(); index++) {
			resolveImportsOf(pinnedVersions.get(index));
		}
		Set<IPFSCascadeModel> affected = findAffected();
		checkPublishedImportsNoAffectedModel(affected);
		checkPinnedVersionsAreUnambiguous(affected);
		return new PublicationOrder(affected).list();
	}

	/**
	 * Refuses a second working copy with the namespace of the published model,
	 * because the imports of that namespace could mean either file.
	 */
	private void checkPublishedNamespaceIsUnique() throws IPFSCascadeException {
		List<IPFSCascadeModel> declaring = workingCopiesDeclaring(published.getNamespace());
		if (declaring.size() > 1) {
			throw new IPFSCascadeException("Several files in the project declare the target namespace " //$NON-NLS-1$
					+ published.getNamespace() + ": " + displayNames(declaring) //$NON-NLS-1$
					+ ". The imports of that namespace could mean any of them, so the cascade publishes nothing." //$NON-NLS-1$
					+ " Give each model its own namespace, or remove the extra files."); //$NON-NLS-1$
		}
	}

	/**
	 * Resolves the {@code ipfs://} imports of a model version to the versions
	 * they pin and records the reverse links.
	 */
	private void resolveImportsOf(IPFSCascadeModel model) throws IOException {
		for (BpmnImport declaration : model.getImports().getIpfsImports()) {
			IPFSCascadeModel target = resolveTarget(model, declaration);
			model.addImportTarget(declaration, target);
			recordImporter(target, model);
		}
	}

	/**
	 * Returns the version that an import pins, reading it from IPFS the first
	 * time a location comes up.
	 *
	 * @return the pinned version, or null when the import refers to the published model
	 */
	private IPFSCascadeModel resolveTarget(IPFSCascadeModel importer, BpmnImport declaration) throws IOException {
		String location = declaration.getLocation();
		if (isPublishedNamespace(declaration.getNamespace()) || publishedModelLocations.contains(location)) {
			return null;
		}
		IPFSCascadeModel known = versionsByLocation.get(location);
		if (known != null) {
			return known;
		}
		byte[] contents = readPinnedVersion(importer, location);
		BpmnImports imports = readPinnedImports(importer, location, contents);
		if (isPublishedNamespace(imports.getTargetNamespace())) {
			publishedModelLocations.add(location);
			return null;
		}
		IPFSCascadeModel version = matchingWorkingCopy(imports.getTargetNamespace(), contents);
		if (version == null) {
			version = IPFSCascadeModel.forPinnedVersion(location, contents, imports, importer.getDisplayName(),
					workingCopiesDeclaring(imports.getTargetNamespace()));
			pinnedVersions.add(version);
		}
		versionsByLocation.put(location, version);
		return version;
	}

	/** Reads a pinned version and names the import in the error when the node cannot provide it. */
	private byte[] readPinnedVersion(IPFSCascadeModel importer, String location) throws IOException {
		try {
			return store.read(location);
		} catch (IOException e) {
			throw new IOException("Cannot read " + location + ", which " + importer.getDisplayName() //$NON-NLS-1$ //$NON-NLS-2$
					+ " imports: " + e.getMessage(), e); //$NON-NLS-1$
		}
	}

	/** Reads the imports of a pinned version and names the import in the error when it is not XML. */
	private static BpmnImports readPinnedImports(IPFSCascadeModel importer, String location, byte[] contents)
			throws IOException {
		try {
			return BpmnImports.read(contents);
		} catch (IOException e) {
			throw new IOException(location + ", which " + importer.getDisplayName() //$NON-NLS-1$
					+ " imports, is not a model the cascade can read: " + e.getMessage(), e); //$NON-NLS-1$
		}
	}

	/**
	 * Returns the working copy that a pinned version stands for: the only
	 * working copy of its namespace, when it holds the same bytes.
	 */
	private IPFSCascadeModel matchingWorkingCopy(String namespace, byte[] contents) {
		List<IPFSCascadeModel> declaring = workingCopiesDeclaring(namespace);
		if (declaring.size() == 1 && Arrays.equals(declaring.get(0).getContents(), contents)) {
			return declaring.get(0);
		}
		return null;
	}

	/**
	 * Collects every model version that imports the published model, directly
	 * or through other affected versions. The published model itself is left
	 * out even when it imports one of them, which
	 * {@link #checkPublishedImportsNoAffectedModel(Set)} reports.
	 */
	private Set<IPFSCascadeModel> findAffected() {
		Set<IPFSCascadeModel> affected = new LinkedHashSet<IPFSCascadeModel>();
		Deque<IPFSCascadeModel> pending = new ArrayDeque<IPFSCascadeModel>(importersOfPublished);
		while (!pending.isEmpty()) {
			IPFSCascadeModel model = pending.removeFirst();
			if (model != published && affected.add(model)) {
				pending.addAll(importersOf(model));
			}
		}
		return affected;
	}

	/**
	 * Refuses a cascade in which the published model imports one of the models
	 * that import it, because no version of either can then name the newest
	 * version of the other.
	 */
	private void checkPublishedImportsNoAffectedModel(Set<IPFSCascadeModel> affected) throws IPFSCascadeException {
		for (IPFSCascadeModel.ImportTarget target : published.getImportTargets()) {
			if (target.getModel() != null && affected.contains(target.getModel())) {
				throw cycleBetween(Arrays.asList(published, target.getModel(), published));
			}
		}
	}

	/**
	 * Refuses an affected pinned version whose namespace has a working copy
	 * with other bytes, or several working copies, because the cascade cannot
	 * tell which version of that model the user wants to carry the change.
	 */
	private static void checkPinnedVersionsAreUnambiguous(Set<IPFSCascadeModel> affected) throws IPFSCascadeException {
		for (IPFSCascadeModel model : affected) {
			List<IPFSCascadeModel> sameNamespace = model.getSameNamespaceWorkingCopies();
			if (model.isWorkingCopy() || sameNamespace.isEmpty()) {
				continue;
			}
			if (sameNamespace.size() == 1) {
				throw new IPFSCascadeException(model.getFirstImporter() + " imports " + model.getPinnedLocation() //$NON-NLS-1$
						+ " for the namespace " + model.getNamespace() + ", but the working copy " //$NON-NLS-1$ //$NON-NLS-2$
						+ sameNamespace.get(0).getDisplayName() + " holds a different version of that model." //$NON-NLS-1$
						+ " Publish that working copy first, or remove it, so that the cascade knows which version" //$NON-NLS-1$
						+ " to update. Nothing was published."); //$NON-NLS-1$
			}
			throw new IPFSCascadeException(model.getFirstImporter() + " imports " + model.getPinnedLocation() //$NON-NLS-1$
					+ " for the namespace " + model.getNamespace() //$NON-NLS-1$
					+ ", and several files in the project declare that namespace: " + displayNames(sameNamespace) //$NON-NLS-1$
					+ ". Remove all but one of them. Nothing was published."); //$NON-NLS-1$
		}
	}

	/** Creates the exception for models that import each other, listing the cycle. */
	static IPFSCascadeException cycleBetween(List<IPFSCascadeModel> cycle) {
		StringBuilder path = new StringBuilder();
		for (IPFSCascadeModel model : cycle) {
			if (path.length() > 0) {
				path.append(" -> "); //$NON-NLS-1$
			}
			path.append(model.getDisplayName());
		}
		return new IPFSCascadeException("These models import each other by CID: " + path //$NON-NLS-1$
				+ ". Each new version would have to contain the new CID of the other, which no CID can do," //$NON-NLS-1$
				+ " so the cascade publishes nothing. Replace one of these imports with an ipns:// name."); //$NON-NLS-1$
	}

	/** Records that a model imports a version, or the published model when the target is null. */
	private void recordImporter(IPFSCascadeModel target, IPFSCascadeModel importer) {
		if (target == null) {
			importersOfPublished.add(importer);
			return;
		}
		List<IPFSCascadeModel> importers = importersByModel.get(target);
		if (importers == null) {
			importers = new ArrayList<IPFSCascadeModel>();
			importersByModel.put(target, importers);
		}
		importers.add(importer);
	}

	/** Returns the models that import a version. */
	private List<IPFSCascadeModel> importersOf(IPFSCascadeModel model) {
		List<IPFSCascadeModel> importers = importersByModel.get(model);
		return importers == null ? Collections.<IPFSCascadeModel>emptyList() : importers;
	}

	/** Adds a working copy to the index of working copies by namespace. */
	private void addToNamespaceIndex(IPFSCascadeModel model) {
		String namespace = model.getNamespace();
		if (namespace == null) {
			return;
		}
		List<IPFSCascadeModel> declaring = workingCopiesByNamespace.get(namespace);
		if (declaring == null) {
			declaring = new ArrayList<IPFSCascadeModel>();
			workingCopiesByNamespace.put(namespace, declaring);
		}
		declaring.add(model);
	}

	/** Returns the working copies, the published one included, that declare a namespace. */
	private List<IPFSCascadeModel> workingCopiesDeclaring(String namespace) {
		List<IPFSCascadeModel> declaring = namespace == null ? null : workingCopiesByNamespace.get(namespace);
		return declaring == null ? Collections.<IPFSCascadeModel>emptyList() : declaring;
	}

	/** Tells whether a namespace is the target namespace of the published model. */
	private boolean isPublishedNamespace(String namespace) {
		return namespace != null && namespace.equals(published.getNamespace());
	}

	/** Joins the display names of models for a message. */
	private static String displayNames(List<IPFSCascadeModel> models) {
		StringBuilder names = new StringBuilder();
		for (IPFSCascadeModel model : models) {
			if (names.length() > 0) {
				names.append(", "); //$NON-NLS-1$
			}
			names.append(model.getDisplayName());
		}
		return names.toString();
	}

	/**
	 * Orders the affected versions depth first, so that each version follows
	 * the affected versions it imports, and stops at the first cycle.
	 */
	private static final class PublicationOrder {

		private final Set<IPFSCascadeModel> affected;
		private final Set<IPFSCascadeModel> placed = new HashSet<IPFSCascadeModel>();
		private final List<IPFSCascadeModel> path = new ArrayList<IPFSCascadeModel>();
		private final List<IPFSCascadeModel> order = new ArrayList<IPFSCascadeModel>();

		/**
		 * Prepares the order of a set of affected versions.
		 *
		 * @param affected the versions to order, in the order the planner found them
		 */
		private PublicationOrder(Set<IPFSCascadeModel> affected) {
			this.affected = affected;
		}

		/** Returns the versions in publication order. */
		private List<IPFSCascadeModel> list() throws IPFSCascadeException {
			for (IPFSCascadeModel model : affected) {
				place(model);
			}
			return order;
		}

		/**
		 * Places a version after the affected versions it imports. A version
		 * that is already on the current path closes a cycle.
		 */
		private void place(IPFSCascadeModel model) throws IPFSCascadeException {
			if (placed.contains(model)) {
				return;
			}
			int onPath = path.indexOf(model);
			if (onPath >= 0) {
				List<IPFSCascadeModel> cycle = new ArrayList<IPFSCascadeModel>(path.subList(onPath, path.size()));
				cycle.add(model);
				throw cycleBetween(cycle);
			}
			path.add(model);
			for (IPFSCascadeModel.ImportTarget target : model.getImportTargets()) {
				if (target.getModel() != null && affected.contains(target.getModel())) {
					place(target.getModel());
				}
			}
			path.remove(path.size() - 1);
			placed.add(model);
			order.add(model);
		}
	}
}
// EcoreFS end: finds the models a publication affects and the order in which to republish them
