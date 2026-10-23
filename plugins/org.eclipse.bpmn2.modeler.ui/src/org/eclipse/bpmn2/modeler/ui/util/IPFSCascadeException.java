package org.eclipse.bpmn2.modeler.ui.util;

// EcoreFS begin: a cascade that cannot be carried out without guessing
/**
 * Says why {@link IPFSImportCascade} cannot plan a cascade: two files declare
 * the same namespace, an import pins a version that differs from the working
 * copy of that model, or models import each other by CID. The cascade checks
 * all of this before it publishes or writes anything, so the message can tell
 * the user what to fix while the project is still unchanged.
 */
public class IPFSCascadeException extends Exception {

	private static final long serialVersionUID = 1L;

	/**
	 * Creates the exception.
	 *
	 * @param message what stops the cascade and how to resolve it
	 */
	public IPFSCascadeException(String message) {
		super(message);
	}
}
// EcoreFS end: a cascade that cannot be carried out without guessing
