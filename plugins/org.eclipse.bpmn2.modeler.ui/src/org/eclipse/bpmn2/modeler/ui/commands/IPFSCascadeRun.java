package org.eclipse.bpmn2.modeler.ui.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.bpmn2.modeler.ui.Activator;
import org.eclipse.bpmn2.modeler.ui.util.IPFSImportCascade;
import org.eclipse.bpmn2.modeler.ui.util.IPFSWorkingCopy;
import org.eclipse.core.resources.IFile;

// EcoreFS begin: runs a planned cascade in the workbench, around the open editors
/**
 * Runs a planned cascade in the workbench. The editors of the working copies
 * that may get new import locations are closed first and opened again
 * afterwards, because the BPMN2 editor keeps the model it loaded and would
 * write the old locations back on its next save. Publish Model with Cascade
 * and Publish Project both use it.
 */
final class IPFSCascadeRun {

	/** Prevents instances; the class only has static helpers. */
	private IPFSCascadeRun() {
	}

	/**
	 * Publishes a planned cascade around the open editors.
	 *
	 * @param cascade the planned cascade
	 * @return the failure that stopped the publication, or null when every model was published;
	 *         {@link IPFSImportCascade#getPublishedSoFar()} tells how far it got
	 */
	static IOException publish(IPFSImportCascade cascade) {
		List<IPFSModelEditors.ClosedEditor> closedEditors = IPFSModelEditors
				.closeEditorsOf(filesOf(cascade.getWorkingCopiesToRewrite()));
		try {
			cascade.publish();
			return null;
		} catch (IOException e) {
			Activator.logError(e);
			return e;
		} finally {
			IPFSModelEditors.reopen(closedEditors);
		}
	}

	/** Returns the workspace files of working copies. */
	private static List<IFile> filesOf(List<IPFSWorkingCopy> workingCopies) {
		List<IFile> files = new ArrayList<IFile>();
		for (IPFSWorkingCopy workingCopy : workingCopies) {
			files.add(((WorkspaceWorkingCopy) workingCopy).getFile());
		}
		return files;
	}
}
// EcoreFS end: runs a planned cascade in the workbench, around the open editors
