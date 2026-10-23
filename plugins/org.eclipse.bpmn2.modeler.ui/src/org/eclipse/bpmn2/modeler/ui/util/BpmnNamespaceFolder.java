package org.eclipse.bpmn2.modeler.ui.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

// EcoreFS begin: the folder of a working copy follows the target namespace of its model
/**
 * Places the working copy of a model downloaded from IPFS in a folder named
 * after its target namespace.
 *
 * <p>
 * BPMN imports name the model they import by its namespace, so the namespace
 * is the identity that every version of a model shares. Deriving the folder
 * from it puts each model in one fixed place in a project, whatever is
 * selected when it is downloaded: a second download of the same model finds
 * the first copy instead of creating a second one. The namespace
 * {@code http://ecorefs.org/case-study/order-process} gives
 * {@code ecorefs.org/case-study/order-process/order-process.bpmn}. The file is
 * named after the last part of the namespace, which stays the same from one
 * version to the next, unlike the name of a process.
 * </p>
 */
public final class BpmnNamespaceFolder {

	private static final Pattern SCHEME_WITH_AUTHORITY = Pattern.compile("^[A-Za-z][A-Za-z0-9+.-]*://"); //$NON-NLS-1$
	private static final Pattern SEPARATORS = Pattern.compile("[/:#?\\\\]+"); //$NON-NLS-1$
	private static final Pattern UNSAFE_CHARACTERS = Pattern.compile("[^A-Za-z0-9._-]"); //$NON-NLS-1$
	private static final int MAX_SEGMENT_LENGTH = 64;
	private static final String MODEL_EXTENSION = ".bpmn"; //$NON-NLS-1$

	/** Prevents instances; the class only has static helpers. */
	private BpmnNamespaceFolder() {
	}

	/**
	 * Returns the path, relative to a project, of the working copy of a model
	 * with the given target namespace.
	 *
	 * @param targetNamespace the target namespace of the model, may be null
	 * @return a path such as {@code ecorefs.org/case-study/order-process/order-process.bpmn},
	 *         or null when the namespace yields no folder name
	 */
	public static String modelPathFor(String targetNamespace) {
		List<String> segments = folderSegments(targetNamespace);
		if (segments.isEmpty()) {
			return null;
		}
		return String.join("/", segments) + "/" + fileNameFor(segments); //$NON-NLS-1$ //$NON-NLS-2$
	}

	/**
	 * Returns the file name for a model with the given target namespace,
	 * without the folders.
	 *
	 * @param targetNamespace the target namespace of the model, may be null
	 * @return a name such as {@code order-process.bpmn}, or null when the
	 *         namespace yields no name
	 */
	public static String fileNameFor(String targetNamespace) {
		List<String> segments = folderSegments(targetNamespace);
		return segments.isEmpty() ? null : fileNameFor(segments);
	}

	/** Names the file after the last folder segment. */
	private static String fileNameFor(List<String> segments) {
		return segments.get(segments.size() - 1) + MODEL_EXTENSION;
	}

	/**
	 * Splits a namespace into folder names. A scheme followed by {@code //},
	 * such as {@code http://}, is dropped; the scheme of a namespace without
	 * an authority, such as {@code urn:}, stays as the first folder. Slashes,
	 * colons, '#' and '?' separate the folders.
	 */
	private static List<String> folderSegments(String targetNamespace) {
		List<String> segments = new ArrayList<String>();
		if (targetNamespace == null) {
			return segments;
		}
		String withoutScheme = SCHEME_WITH_AUTHORITY.matcher(targetNamespace.trim()).replaceFirst(""); //$NON-NLS-1$
		for (String segment : SEPARATORS.split(withoutScheme)) {
			if (!segment.isEmpty()) {
				segments.add(safeSegment(segment));
			}
		}
		return segments;
	}

	/**
	 * Makes one segment safe as a folder name on every platform: characters
	 * other than letters, digits, '.', '_' and '-' become '_', a leading '.'
	 * becomes '_' so the folder is not hidden, and long names are cut.
	 */
	private static String safeSegment(String segment) {
		String safe = UNSAFE_CHARACTERS.matcher(segment).replaceAll("_"); //$NON-NLS-1$
		if (safe.startsWith(".")) { //$NON-NLS-1$
			safe = "_" + safe.substring(1); //$NON-NLS-1$
		}
		return safe.length() > MAX_SEGMENT_LENGTH ? safe.substring(0, MAX_SEGMENT_LENGTH) : safe;
	}
}
// EcoreFS end: the folder of a working copy follows the target namespace of its model
