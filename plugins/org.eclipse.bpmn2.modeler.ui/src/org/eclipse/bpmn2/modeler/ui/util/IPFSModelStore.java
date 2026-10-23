package org.eclipse.bpmn2.modeler.ui.util;

import java.io.IOException;

// EcoreFS begin: where the cascade reads pinned versions and publishes new ones
/**
 * The IPFS side of {@link IPFSImportCascade}: it reads the versions that
 * imports pin and adds the new versions that the cascade creates.
 *
 * <p>
 * {@link KuboModelStore} talks to a Kubo node. The interface keeps the cascade
 * free of HTTP code, so it can be tested with an in-memory store.
 * </p>
 */
public interface IPFSModelStore {

	/**
	 * Reads the model that an {@code ipfs://} location names.
	 *
	 * @param location the location of an import, such as {@code ipfs://bafk...}
	 * @return the bytes of that version
	 * @throws IOException when the node cannot provide them
	 */
	byte[] read(String location) throws IOException;

	/**
	 * Adds a new version of a model and returns its CID.
	 *
	 * @param fileName the file name to send along, for the logs of the node
	 * @param contents the bytes of the new version
	 * @return the CID of the new version
	 * @throws IOException when the node does not accept the content
	 */
	String publish(String fileName, byte[] contents) throws IOException;
}
// EcoreFS end: where the cascade reads pinned versions and publishes new ones
