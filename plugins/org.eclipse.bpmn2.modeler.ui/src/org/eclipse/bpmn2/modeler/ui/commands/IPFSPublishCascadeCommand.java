package org.eclipse.bpmn2.modeler.ui.commands;

import java.io.IOException;
import java.util.List;

import org.eclipse.bpmn2.modeler.core.preferences.Bpmn2Preferences;
import org.eclipse.bpmn2.modeler.ui.Activator;
import org.eclipse.bpmn2.modeler.ui.editor.BPMN2Editor;
import org.eclipse.bpmn2.modeler.ui.util.IPFSCascadeException;
import org.eclipse.bpmn2.modeler.ui.util.IPFSImportCascade;
import org.eclipse.bpmn2.modeler.ui.util.IPFSWorkingCopy;
import org.eclipse.bpmn2.modeler.ui.util.KuboModelStore;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.handlers.HandlerUtil;

// EcoreFS begin: command that publishes the current model and the models that import it by CID
/**
 * Publishes the model of the active editor and gives every model that imports
 * it by CID a new version that names the new CID, as {@code saveIPFSCascaded}
 * does in the core plug-in.
 *
 * <p>
 * Save writes the working copy only, and Publish Model publishes one model.
 * The models that import it keep the CID of the old version until this
 * command runs. It looks for them among the BPMN files of the project of the
 * model and among the versions those files pin on IPFS, asks the user to
 * confirm the list, and publishes each model after the models it imports. A
 * working copy gets the new CIDs written into the locations of its imports and
 * no other change; its editor is closed for the rewrite and opened again, so
 * it shows the new versions.
 * </p>
 */
public class IPFSPublishCascadeCommand extends AbstractHandler {

	public static final String ID = "org.eclipse.bpmn2.modeler.command.publishModelToIpfsWithCascade"; //$NON-NLS-1$

	/**
	 * Saves the open models of the project, plans the cascade, asks for
	 * confirmation and publishes. A cascade that cannot be planned without
	 * guessing is reported before anything changes.
	 */
	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		BPMN2Editor editor = BPMN2Editor.getActiveEditor();
		if (editor == null) {
			return null;
		}
		Shell shell = HandlerUtil.getActiveShell(event);
		IFile modelFile = editor.getModelFile();
		if (modelFile == null || !modelFile.exists()) {
			MessageDialog.openError(shell, Messages.IPFS_Cascade_Error_Title, Messages.IPFS_Cascade_No_Workspace_File);
			return null;
		}
		IProject project = modelFile.getProject();
		if (!IPFSModelEditors.saveDirtyEditors(project)) {
			MessageDialog.openError(shell, Messages.IPFS_Cascade_Error_Title,
					NLS.bind(Messages.IPFS_Cascade_Unsaved_Editors, project.getName()));
			return null;
		}
		try {
			IPFSImportCascade cascade = planCascade(modelFile, project);
			if (confirm(shell, cascade)) {
				publishCascade(shell, cascade, project);
			}
			return null;
		} catch (IPFSCascadeException e) {
			MessageDialog.openError(shell, Messages.IPFS_Cascade_Error_Title, e.getMessage());
			return null;
		} catch (Exception e) {
			Activator.logError(e);
			MessageDialog.openError(shell, Messages.IPFS_Cascade_Error_Title, e.getMessage());
			throw new ExecutionException("Could not publish the BPMN2 model to IPFS with cascade.", e); //$NON-NLS-1$
		}
	}

	/**
	 * Plans the cascade over the BPMN files of the project, with the Kubo
	 * node that the project settings name.
	 */
	private IPFSImportCascade planCascade(IFile modelFile, IProject project) throws Exception {
		List<IPFSWorkingCopy> projectFiles = WorkspaceWorkingCopy.listProject(project);
		String apiUrl = Bpmn2Preferences.getInstance(project).getIpfsApiUrl();
		return IPFSImportCascade.plan(new WorkspaceWorkingCopy(modelFile), projectFiles, new KuboModelStore(apiUrl));
	}

	/**
	 * Asks the user to confirm the models that get new versions. A model that
	 * no other model imports is published without asking, like Publish Model.
	 */
	private boolean confirm(Shell shell, IPFSImportCascade cascade) {
		if (cascade.getImporters().isEmpty()) {
			return true;
		}
		return MessageDialog.openConfirm(shell, Messages.IPFS_Cascade_Confirm_Title,
				NLS.bind(Messages.IPFS_Cascade_Confirm_Message, cascade.getPublishedModel().getDisplayName(),
						IPFSCascadeReport.listImporters(cascade.getImporters())));
	}

	/**
	 * Publishes the cascade around the open editors and reports the outcome.
	 * After a failure, the report lists what was published before it.
	 */
	private void publishCascade(Shell shell, IPFSImportCascade cascade, IProject project) {
		IOException failure = IPFSCascadeRun.publish(cascade);
		String report = IPFSCascadeReport.describe(cascade.getPublishedSoFar());
		if (failure != null) {
			MessageDialog.openError(shell, Messages.IPFS_Cascade_Error_Title,
					NLS.bind(Messages.IPFS_Cascade_Partial_Failure, failure.getMessage(), report));
			return;
		}
		if (cascade.getImporters().isEmpty()) {
			report = NLS.bind(Messages.IPFS_Cascade_No_Importers, project.getName()) + "\n\n" + report; //$NON-NLS-1$
		}
		new IPFSPublicationResultDialog(shell, Messages.IPFS_Cascade_Success_Title, Messages.IPFS_Cascade_Result_Message,
				report).open();
	}
}
// EcoreFS end: command that publishes the current model and the models that import it by CID
