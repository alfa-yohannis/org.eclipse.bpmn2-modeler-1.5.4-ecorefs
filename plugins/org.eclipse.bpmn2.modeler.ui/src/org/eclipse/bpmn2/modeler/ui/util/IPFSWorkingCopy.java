package org.eclipse.bpmn2.modeler.ui.util;

import java.io.IOException;

// EcoreFS begin: a model file that the cascade may read and rewrite
/**
 * A model file in the user's project: the working copy that the user edits
 * and saves locally.
 *
 * <p>
 * {@link IPFSImportCascade} reads every working copy of a project to find the
 * models that import a published model, and writes the new import locations
 * into the working copies it republishes. The workspace implementation goes
 * through the Eclipse resource API, so the change shows in the local history;
 * tests use plain files.
 * </p>
 */
public interface IPFSWorkingCopy {

	/**
	 * Returns the path that identifies the file to the user, for example
	 * {@code case-study/ecorefs.org/case-study/collaboration/collaboration.bpmn}.
	 */
	String getPath();

	/**
	 * Returns the file name, which the publication passes on to the node.
	 */
	String getFileName();

	/**
	 * Reads the saved contents of the file.
	 *
	 * @return the bytes of the file
	 * @throws IOException when the file cannot be read
	 */
	byte[] readContents() throws IOException;

	/**
	 * Replaces the contents of the file.
	 *
	 * @param contents the new bytes
	 * @throws IOException when the file cannot be written
	 */
	void writeContents(byte[] contents) throws IOException;
}
// EcoreFS end: a model file that the cascade may read and rewrite
