package org.eclipse.bpmn2.modeler.ui.util;

import java.util.regex.Pattern;

// EcoreFS begin: readable file names for models fetched from IPFS or IPNS
/**
 * Chooses the file name under which a model fetched from IPFS or IPNS is saved.
 *
 * <p>
 * A single-file CID does not record the name the model was published under, so
 * without this class every download would be named after its CID. The name
 * comes from the file name in the reference when there is one, otherwise from
 * the model itself (see {@link BpmnModelNames}), and only as a last resort from
 * the CID or IPNS name.
 * </p>
 */
public final class IPFSModelFileName {

	private static final String DEFAULT_FILE_NAME = "ipfs-model"; //$NON-NLS-1$
	private static final int MAX_FILE_NAME_LENGTH = 80;
	private static final Pattern MODEL_FILE_EXTENSION = Pattern.compile("(?i)\\.(bpmn2?|xml)$"); //$NON-NLS-1$
	private static final Pattern WHITESPACE_RUN = Pattern.compile("\\s+"); //$NON-NLS-1$
	private static final Pattern ILLEGAL_FILE_NAME_CHARACTERS = Pattern.compile("[\\\\/:*?\"<>|\\p{Cntrl}]"); //$NON-NLS-1$
	private static final Pattern LEADING_OR_TRAILING_DOTS_AND_SPACES = Pattern.compile("^[. ]+|[. ]+$"); //$NON-NLS-1$

	private IPFSModelFileName() {
	}

	/**
	 * Returns a file name without extension for the model behind a reference.
	 *
	 * <p>
	 * A reference such as {@code /ipfs/<cid>/order.bpmn} keeps its file name.
	 * A bare CID or IPNS name takes the most descriptive name in the model, and
	 * falls back to the CID or IPNS name when the model has no names or cannot
	 * be read.
	 * </p>
	 *
	 * @param reference the CID, path or URI the model was fetched with
	 * @param contents the downloaded model bytes
	 * @return a name that is safe to use as a file name, never empty
	 */
	public static String derive(String reference, byte[] contents) {
		String[] segments = referenceSegments(reference);
		if (segments.length > 2) {
			return toFileName(segments[segments.length - 1]);
		}
		String modelName = BpmnModelNames.read(contents).mostDescriptive();
		if (modelName == null && segments.length == 2) {
			modelName = segments[1];
		}
		return toFileName(modelName);
	}

	/**
	 * Splits a reference into its path segments, for example
	 * {@code {"ipfs", cid, "order.bpmn"}}, so the caller can tell a bare CID from
	 * a path that ends in a file name.
	 */
	private static String[] referenceSegments(String reference) {
		try {
			return IPFSModelTransfer.normalizeReference(reference).substring(1).split("/"); //$NON-NLS-1$
		} catch (IllegalArgumentException e) {
			return new String[0];
		}
	}

	/**
	 * Turns a model name into a file name that every common file system
	 * accepts: it drops a model extension, replaces reserved characters, and
	 * removes leading dots, which would hide the file, and trailing dots or
	 * spaces, which Windows rejects.
	 */
	private static String toFileName(String name) {
		String fileName = name == null ? "" : name.trim(); //$NON-NLS-1$
		fileName = MODEL_FILE_EXTENSION.matcher(fileName).replaceFirst(""); //$NON-NLS-1$
		fileName = WHITESPACE_RUN.matcher(fileName).replaceAll(" "); //$NON-NLS-1$
		fileName = ILLEGAL_FILE_NAME_CHARACTERS.matcher(fileName).replaceAll("_"); //$NON-NLS-1$
		if (fileName.length() > MAX_FILE_NAME_LENGTH) {
			fileName = fileName.substring(0, MAX_FILE_NAME_LENGTH);
		}
		fileName = LEADING_OR_TRAILING_DOTS_AND_SPACES.matcher(fileName).replaceAll(""); //$NON-NLS-1$
		return fileName.isEmpty() ? DEFAULT_FILE_NAME : fileName;
	}
}
// EcoreFS end: readable file names for models fetched from IPFS or IPNS
