package org.eclipse.bpmn2.modeler.ui.util;

// EcoreFS begin: one BPMN import of a model file, with the position of its location in the text
/**
 * One BPMN import of a model file: the namespace it binds and the location
 * that names the imported model.
 *
 * <p>
 * An {@code ipfs://} location pins one published version of the imported
 * model by its CID. {@link BpmnImports#withLocations(java.util.Map)} uses the
 * position of the location value to replace it without touching the rest of
 * the file.
 * </p>
 */
public final class BpmnImport {

	private static final String IPFS_SCHEME_PREFIX = "ipfs://"; //$NON-NLS-1$

	private final String namespace;
	private final String location;
	private final int locationStart;
	private final int locationEnd;
	private final char quote;

	/**
	 * Creates an import.
	 *
	 * @param namespace     the namespace the import binds, may be null
	 * @param location      the location as the parser reports it
	 * @param locationStart the offset of the raw location value in the text
	 * @param locationEnd   the offset just after the raw location value
	 * @param quote         the quote character around the location value
	 */
	BpmnImport(String namespace, String location, int locationStart, int locationEnd, char quote) {
		this.namespace = namespace;
		this.location = location;
		this.locationStart = locationStart;
		this.locationEnd = locationEnd;
		this.quote = quote;
	}

	/** Returns the namespace that the import binds, or null when the import names none. */
	public String getNamespace() {
		return namespace;
	}

	/** Returns the location of the imported model, for example {@code ipfs://bafk...}. */
	public String getLocation() {
		return location;
	}

	/**
	 * Tells whether the location names a published version by CID, so that a
	 * model loaded with this import always gets that version from IPFS.
	 */
	public boolean isIpfsLocation() {
		return location.startsWith(IPFS_SCHEME_PREFIX);
	}

	/** Returns the offset of the raw location value in the text of the file. */
	int getLocationStart() {
		return locationStart;
	}

	/** Returns the offset just after the raw location value. */
	int getLocationEnd() {
		return locationEnd;
	}

	/** Returns the quote character around the location value. */
	char getQuote() {
		return quote;
	}
}
// EcoreFS end: one BPMN import of a model file, with the position of its location in the text
