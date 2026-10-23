package org.eclipse.bpmn2.modeler.ui.commands;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import org.eclipse.bpmn2.modeler.ui.util.BpmnImports;
import org.eclipse.bpmn2.modeler.ui.util.BpmnNamespaceFolder;
import org.eclipse.bpmn2.modeler.ui.util.IPFSModelTransfer;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.widgets.Shell;

// EcoreFS begin: a downloaded model goes to the folder of its namespace in the project
/**
 * Downloads a model from IPFS into the folder of its target namespace in the
 * project, where its working copy belongs.
 *
 * <p>
 * The namespace {@code http://ecorefs.org/case-study/order-process} puts the
 * model at {@code ecorefs.org/case-study/order-process/order-process.bpmn}, see
 * {@link BpmnNamespaceFolder}. Every download of a model therefore lands in
 * the same file. When that file already holds the same bytes, it is simply
 * opened; when it holds another version, the user chooses between replacing
 * it, which keeps the old contents in the local history, and opening the file
 * as it is. A model without a usable namespace goes to the selected folder
 * under a name derived from its content, as before.
 * </p>
 */
final class IPFSNamespaceDownload {

	private static final int REPLACE = 0;
	private static final int OPEN_EXISTING = 1;

	private final Shell shell;
	private final String apiUrl;

	/**
	 * Prepares downloads from one node.
	 *
	 * @param shell  the shell for the question about an existing file
	 * @param apiUrl the Kubo API address of the project settings
	 */
	IPFSNamespaceDownload(Shell shell, String apiUrl) {
		this.shell = shell;
		this.apiUrl = apiUrl;
	}

	/**
	 * Downloads a model and stores it as the working copy of its namespace.
	 *
	 * @param reference         the CID, IPFS path or IPNS name the user entered
	 * @param selectedContainer the selected folder; its project receives the namespace folder
	 * @return the file to open, or null when the user cancelled
	 * @throws Exception when the download or the workspace write fails
	 */
	IFile download(String reference, IContainer selectedContainer) throws Exception {
		byte[] contents = IPFSModelTransfer.downloadBytes(reference, apiUrl);
		String namespace = targetNamespaceOf(contents);
		String modelPath = BpmnNamespaceFolder.modelPathFor(namespace);
		IProject project = selectedContainer.getProject();
		if (modelPath == null || project == null) {
			return IPFSWorkspaceResourceSupport.writeNewModelFile(selectedContainer, reference, contents);
		}
		IFile target = project.getFile(new Path(modelPath));
		if (!target.exists()) {
			return IPFSWorkspaceResourceSupport.writeWorkspaceFile(project, modelPath, contents);
		}
		if (Arrays.equals(contentsOf(target), contents)) {
			return target;
		}
		return resolveExistingFile(target, namespace, contents);
	}

	/**
	 * Lets the user decide what happens to a working copy that holds another
	 * version of the downloaded model.
	 *
	 * @return the file to open, or null when the user cancelled
	 */
	private IFile resolveExistingFile(IFile target, String namespace, byte[] contents) throws Exception {
		int choice = askAboutExistingFile(target, namespace);
		if (choice == OPEN_EXISTING) {
			return target;
		}
		if (choice != REPLACE || !IPFSModelEditors.closeEditorsAskingToSave(target)) {
			return null;
		}
		target.setContents(new ByteArrayInputStream(contents), IResource.FORCE | IResource.KEEP_HISTORY,
				new NullProgressMonitor());
		return target;
	}

	/**
	 * Asks whether to replace the working copy, open it as it is, or cancel.
	 * Opening it is the default, so that pressing Enter never overwrites
	 * unpublished changes.
	 */
	private int askAboutExistingFile(IFile target, String namespace) {
		MessageDialog dialog = new MessageDialog(shell, Messages.IPFS_Download_Existing_Title, null,
				NLS.bind(Messages.IPFS_Download_Existing_Message, target.getFullPath().makeRelative().toPortableString(),
						namespace),
				MessageDialog.QUESTION, OPEN_EXISTING, Messages.IPFS_Download_Replace_Button,
				Messages.IPFS_Download_Open_Existing_Button, IDialogConstants.CANCEL_LABEL);
		return dialog.open();
	}

	/**
	 * Returns the target namespace of downloaded bytes, or null when they are
	 * not a BPMN model the scanner can read.
	 */
	private static String targetNamespaceOf(byte[] contents) {
		try {
			return BpmnImports.read(contents).getTargetNamespace();
		} catch (IOException e) {
			return null;
		}
	}

	/** Reads the saved contents of a workspace file. */
	private static byte[] contentsOf(IFile file) throws Exception {
		try (InputStream input = file.getContents(true)) {
			return input.readAllBytes();
		}
	}
}
// EcoreFS end: a downloaded model goes to the folder of its namespace in the project
