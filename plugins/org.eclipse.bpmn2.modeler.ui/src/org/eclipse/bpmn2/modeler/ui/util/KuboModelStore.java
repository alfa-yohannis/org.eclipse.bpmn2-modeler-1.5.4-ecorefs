package org.eclipse.bpmn2.modeler.ui.util;

import java.io.IOException;

// EcoreFS begin: the model store of the cascade on a Kubo node
/**
 * Reads and publishes model versions on a Kubo node through
 * {@link IPFSModelTransfer}, which holds the HTTP calls of the modeler. New
 * versions are added and pinned as CIDv1, like the Publish to IPFS command
 * does.
 */
public final class KuboModelStore implements IPFSModelStore {

	private final String apiUrl;

	/**
	 * Creates a store for one node.
	 *
	 * @param apiUrl the Kubo API address, or null for the address that
	 *               {@link IPFSModelTransfer} configures by default
	 */
	public KuboModelStore(String apiUrl) {
		this.apiUrl = apiUrl;
	}

	/** Reads a version with the {@code cat} command of the node. */
	@Override
	public byte[] read(String location) throws IOException {
		return IPFSModelTransfer.downloadBytes(location, apiUrl);
	}

	/** Adds and pins a version with the {@code add} command of the node. */
	@Override
	public String publish(String fileName, byte[] contents) throws IOException {
		return IPFSModelTransfer.publish(fileName, contents, apiUrl).getCid();
	}
}
// EcoreFS end: the model store of the cascade on a Kubo node
