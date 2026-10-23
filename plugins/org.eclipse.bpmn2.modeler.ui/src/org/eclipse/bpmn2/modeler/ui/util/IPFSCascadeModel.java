package org.eclipse.bpmn2.modeler.ui.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// EcoreFS begin: one model version that takes part in a cascade
/**
 * One model version that takes part in a cascade: either a working copy in
 * the project, or a version that an import pins and that exists only on IPFS.
 *
 * <p>
 * {@link IPFSImportCascade} gives each model that imports the published model,
 * directly or through other models, a new version whose imports name the new
 * CIDs. A working copy gets the new import locations written into its file. A
 * version without a working copy gets them only in the new bytes that the
 * cascade publishes. After the publication, the model reports its new CID and
 * the import locations that changed.
 * </p>
 */
public final class IPFSCascadeModel {

	private static final String DEFAULT_FILE_NAME = "model.bpmn"; //$NON-NLS-1$

	private final IPFSWorkingCopy workingCopy;
	private final String pinnedLocation;
	private final byte[] contents;
	private final BpmnImports imports;
	private final String firstImporter;
	private final List<IPFSCascadeModel> sameNamespaceWorkingCopies;
	private final List<ImportTarget> importTargets = new ArrayList<ImportTarget>();
	private String newCid;
	private boolean workingCopyRewritten;
	private List<ImportChange> importChanges = Collections.emptyList();

	/** Creates a model version; the factory methods name the two kinds. */
	private IPFSCascadeModel(IPFSWorkingCopy workingCopy, String pinnedLocation, byte[] contents, BpmnImports imports,
			String firstImporter, List<IPFSCascadeModel> sameNamespaceWorkingCopies) {
		this.workingCopy = workingCopy;
		this.pinnedLocation = pinnedLocation;
		this.contents = contents;
		this.imports = imports;
		this.firstImporter = firstImporter;
		this.sameNamespaceWorkingCopies = sameNamespaceWorkingCopies;
	}

	/**
	 * Reads a working copy of the project.
	 *
	 * @param workingCopy the file
	 * @return the model version of the file as it is saved
	 * @throws IOException when the file cannot be read or is not well-formed XML;
	 *                     the message names the file
	 */
	static IPFSCascadeModel forWorkingCopy(IPFSWorkingCopy workingCopy) throws IOException {
		try {
			byte[] contents = workingCopy.readContents();
			return new IPFSCascadeModel(workingCopy, null, contents, BpmnImports.read(contents), null,
					Collections.<IPFSCascadeModel>emptyList());
		} catch (IOException e) {
			throw new IOException(workingCopy.getPath() + ": " + e.getMessage(), e); //$NON-NLS-1$
		}
	}

	/**
	 * Creates the model version that an import pins and that no working copy
	 * of the project holds.
	 *
	 * @param location                   the {@code ipfs://} location of the import
	 * @param contents                   the bytes of the version
	 * @param imports                    the imports of the version
	 * @param firstImporter              the model whose import led to this version, for messages
	 * @param sameNamespaceWorkingCopies the working copies that declare the same namespace
	 *                                   but hold other bytes
	 * @return the model version
	 */
	static IPFSCascadeModel forPinnedVersion(String location, byte[] contents, BpmnImports imports,
			String firstImporter, List<IPFSCascadeModel> sameNamespaceWorkingCopies) {
		return new IPFSCascadeModel(null, location, contents, imports, firstImporter,
				Collections.unmodifiableList(new ArrayList<IPFSCascadeModel>(sameNamespaceWorkingCopies)));
	}

	/**
	 * Returns a name for messages: the path of the working copy, or the pinned
	 * location with the namespace of the model.
	 */
	public String getDisplayName() {
		if (workingCopy != null) {
			return workingCopy.getPath();
		}
		String namespace = getNamespace();
		return namespace == null ? pinnedLocation : pinnedLocation + " (" + namespace + ")"; //$NON-NLS-1$ //$NON-NLS-2$
	}

	/** Tells whether the version is a working copy in the project. */
	public boolean isWorkingCopy() {
		return workingCopy != null;
	}

	/** Returns the working copy, or null for a version that exists only on IPFS. */
	public IPFSWorkingCopy getWorkingCopy() {
		return workingCopy;
	}

	/** Returns the location that pins this version, or null for a working copy. */
	public String getPinnedLocation() {
		return pinnedLocation;
	}

	/** Returns the target namespace of the model, or null when it declares none. */
	public String getNamespace() {
		return imports.getTargetNamespace();
	}

	/** Returns the CID of the new version, or null before the cascade published it. */
	public String getNewCid() {
		return newCid;
	}

	/** Tells whether the cascade wrote new import locations into the working copy. */
	public boolean isWorkingCopyRewritten() {
		return workingCopyRewritten;
	}

	/** Returns the import locations that the new version changed. */
	public List<ImportChange> getImportChanges() {
		return importChanges;
	}

	/** Returns the bytes of the version as the cascade found it. */
	byte[] getContents() {
		return contents;
	}

	/** Returns the imports of the version as the cascade found it. */
	BpmnImports getImports() {
		return imports;
	}

	/** Returns the model whose import led the cascade to this version. */
	String getFirstImporter() {
		return firstImporter;
	}

	/**
	 * Returns the working copies that declare the namespace of this pinned
	 * version but hold other bytes, which makes the version ambiguous.
	 */
	List<IPFSCascadeModel> getSameNamespaceWorkingCopies() {
		return sameNamespaceWorkingCopies;
	}

	/** Returns the {@code ipfs://} imports of the version with the versions they pin. */
	List<ImportTarget> getImportTargets() {
		return importTargets;
	}

	/**
	 * Records the version that one {@code ipfs://} import pins.
	 *
	 * @param declaration the import
	 * @param target      the pinned version, or null when the import names the published model
	 */
	void addImportTarget(BpmnImport declaration, IPFSCascadeModel target) {
		importTargets.add(new ImportTarget(declaration, target));
	}

	/**
	 * Returns the file name sent with the publication: the name of the working
	 * copy, or one derived from the namespace.
	 */
	String getPublicationFileName() {
		if (workingCopy != null) {
			return workingCopy.getFileName();
		}
		String fileName = BpmnNamespaceFolder.fileNameFor(getNamespace());
		return fileName == null ? DEFAULT_FILE_NAME : fileName;
	}

	/**
	 * Records the outcome of the publication of the new version.
	 *
	 * @param cid       the CID of the new version
	 * @param rewritten whether the working copy got new import locations
	 * @param changes   the import locations that changed
	 */
	void recordPublication(String cid, boolean rewritten, List<ImportChange> changes) {
		this.newCid = cid;
		this.workingCopyRewritten = rewritten;
		this.importChanges = Collections.unmodifiableList(new ArrayList<ImportChange>(changes));
	}

	/**
	 * One {@code ipfs://} import of a model version and the version it pins;
	 * a null target stands for the published model.
	 */
	static final class ImportTarget {

		private final BpmnImport declaration;
		private final IPFSCascadeModel model;

		/**
		 * Creates the pair.
		 *
		 * @param declaration the import
		 * @param model       the pinned version, or null for the published model
		 */
		ImportTarget(BpmnImport declaration, IPFSCascadeModel model) {
			this.declaration = declaration;
			this.model = model;
		}

		/** Returns the import. */
		BpmnImport getDeclaration() {
			return declaration;
		}

		/** Returns the pinned version, or null when the import names the published model. */
		IPFSCascadeModel getModel() {
			return model;
		}
	}

	/** One import location that a new version changed. */
	public static final class ImportChange {

		private final String namespace;
		private final String oldLocation;
		private final String newLocation;

		/**
		 * Creates the change.
		 *
		 * @param namespace   the namespace of the import
		 * @param oldLocation the location before the cascade
		 * @param newLocation the location in the new version
		 */
		ImportChange(String namespace, String oldLocation, String newLocation) {
			this.namespace = namespace;
			this.oldLocation = oldLocation;
			this.newLocation = newLocation;
		}

		/** Returns the namespace of the import. */
		public String getNamespace() {
			return namespace;
		}

		/** Returns the location before the cascade. */
		public String getOldLocation() {
			return oldLocation;
		}

		/** Returns the location in the new version. */
		public String getNewLocation() {
			return newLocation;
		}
	}
}
// EcoreFS end: one model version that takes part in a cascade
