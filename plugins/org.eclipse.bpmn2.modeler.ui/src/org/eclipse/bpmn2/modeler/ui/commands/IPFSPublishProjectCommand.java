package org.eclipse.bpmn2.modeler.ui.commands;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.bpmn2.modeler.core.preferences.Bpmn2Preferences;
import org.eclipse.bpmn2.modeler.ui.Activator;
import org.eclipse.bpmn2.modeler.ui.util.IPFSCascadeException;
import org.eclipse.bpmn2.modeler.ui.util.IPFSCascadeModel;
import org.eclipse.bpmn2.modeler.ui.util.IPFSImportCascade;
import org.eclipse.bpmn2.modeler.ui.util.IPFSModelTransfer;
import org.eclipse.bpmn2.modeler.ui.util.IPFSModelTransfer.PublicationResult;
import org.eclipse.bpmn2.modeler.ui.util.IPFSProjectManifest;
import org.eclipse.bpmn2.modeler.ui.util.IPFSProjectManifest.ModelEntry;
import org.eclipse.bpmn2.modeler.ui.util.IPFSWorkingCopy;
import org.eclipse.bpmn2.modeler.ui.util.KuboModelStore;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.HandlerUtil;

// EcoreFS begin: command that publishes all BPMN models in the active project or folder through a manifest
/**
 * Publishes every BPMN model of the selected project or folder and a JSON
 * manifest that maps each relative path to its CID.
 *
 * <p>
 * The models are published as one consistent set: an {@code ipfs://} import of
 * another model of the folder gets the CID that this publication gives that
 * model, and the models are published in dependency order, as the cascade
 * does. A working copy gets the new locations written into its imports. When
 * the folder pins versions on IPFS that import a model of the folder, those
 * versions get new versions too, after the user agrees. When the folder
 * cannot be published consistently, for example because of a cycle of
 * imports, the user may publish the files as they are.
 * </p>
 */
public class IPFSPublishProjectCommand extends AbstractHandler {

	public static final String ID = "org.eclipse.bpmn2.modeler.command.publishProjectToIpfs"; //$NON-NLS-1$

	/** Publishes the models of the folder and the manifest, and shows the references. */
	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		File manifestFile = null;
		Shell shell = HandlerUtil.getActiveShell(event);
		try {
			IContainer container = IPFSWorkspaceResourceSupport.resolveTargetContainer(event);
			if (container == null || !container.isAccessible()) {
				MessageDialog.openError(shell, Messages.IPFS_Project_Publish_Error_Title,
						Messages.IPFS_Project_Publish_No_Target);
				return null;
			}

			Bpmn2Preferences preferences = IPFSWorkspaceResourceSupport.resolvePreferences();
			if (Bpmn2Preferences.PREF_IPFS_PUBLISH_MODE_IPNS.equals(preferences.getIpfsPublishMode())
					&& preferences.getIpfsDefaultIpnsKey().isEmpty()) {
				MessageDialog.openError(shell, Messages.IPFS_Project_Publish_Error_Title,
						Messages.IPFS_Publish_Missing_IPNS_Key);
				return null;
			}

			PlatformUI.getWorkbench().saveAllEditors(false);

			List<IFile> modelFiles = IPFSWorkspaceResourceSupport.collectBpmnFiles(container);
			if (modelFiles.isEmpty()) {
				MessageDialog.openError(shell, Messages.IPFS_Project_Publish_Error_Title,
						Messages.IPFS_Project_Publish_No_Models);
				return null;
			}

			IFile selectedRoot = IPFSWorkspaceResourceSupport.resolveSelectedBpmnFile(event, container);
			if (selectedRoot == null) {
				selectedRoot = modelFiles.get(0);
			}

			List<ModelEntry> entries = new ArrayList<ModelEntry>();
			StringBuilder details = new StringBuilder();
			details.append(NLS.bind(Messages.IPFS_Project_Publish_Details_Header,
					new Object[] { container.getFullPath().toPortableString(), Integer.valueOf(modelFiles.size()),
							IPFSWorkspaceResourceSupport.toRelativePath(container, selectedRoot) }));
			details.append("\n\n"); //$NON-NLS-1$

			// EcoreFS begin: the files of the folder are published as one consistent set
			// for (IFile modelFile : modelFiles) {
			// 	...publish each file as it is with IPFSModelTransfer.publish(...)
			// }
			if (!publishModels(shell, container, modelFiles, preferences.getIpfsApiUrl(), entries, details)) {
				return null;
			}
			// EcoreFS end: the files of the folder are published as one consistent set

			if (entries.isEmpty()) {
				MessageDialog.openError(shell, Messages.IPFS_Project_Publish_Error_Title,
						Messages.IPFS_Project_Publish_No_Models);
				return null;
			}

			IPFSProjectManifest manifest = new IPFSProjectManifest(container.getName(),
					IPFSWorkspaceResourceSupport.toRelativePath(container, selectedRoot), entries);
			manifestFile = File.createTempFile("bpmn-project-manifest", ".json"); //$NON-NLS-1$ //$NON-NLS-2$
			Files.writeString(manifestFile.toPath(), manifest.toJson(), StandardCharsets.UTF_8);

			PublicationResult manifestPublication = IPFSModelTransfer.publish(manifestFile, preferences.getIpfsApiUrl(),
					preferences.getIpfsPublishMode(), preferences.getIpfsDefaultIpnsKey());
			new IPFSPublicationResultDialog(shell, Messages.IPFS_Project_Publish_Success_Title,
					Messages.IPFS_Project_Publish_Result_Message,
					buildPublicationDetails(manifestPublication, details.toString())).open();
			return null;
		} catch (Exception e) {
			Activator.logError(e);
			MessageDialog.openError(shell, Messages.IPFS_Project_Publish_Error_Title, e.getMessage());
			throw new ExecutionException("Could not publish the BPMN project to IPFS.", e); //$NON-NLS-1$
		} finally {
			if (manifestFile != null && manifestFile.exists()) {
				manifestFile.delete();
			}
		}
	}

	// EcoreFS begin: the files of the folder are published as one consistent set
	/**
	 * Publishes the models of the folder and fills the manifest entries and
	 * the details. The models are published as one consistent set when the
	 * plan allows it, and as they are when the user chooses so.
	 *
	 * @return false when the user cancelled or the publication failed, which
	 *         the method has reported already
	 */
	private boolean publishModels(Shell shell, IContainer container, List<IFile> modelFiles, String apiUrl,
			List<ModelEntry> entries, StringBuilder details) throws Exception {
		IPFSImportCascade publication;
		try {
			publication = IPFSImportCascade.planProject(workingCopiesOf(modelFiles), new KuboModelStore(apiUrl));
		} catch (IPFSCascadeException | IOException e) {
			// A refused plan or a file that cannot be read still allows the old way
			if (!askToPublishAsIs(shell, e.getMessage())) {
				return false;
			}
			publishAsIs(container, modelFiles, apiUrl, entries, details);
			return true;
		}
		if (!confirmVersionsOutsideTheFolder(shell, container, publication)) {
			return false;
		}
		IOException failure = IPFSCascadeRun.publish(publication);
		if (failure != null) {
			MessageDialog.openError(shell, Messages.IPFS_Project_Publish_Error_Title, NLS.bind(
					Messages.IPFS_Cascade_Partial_Failure, failure.getMessage(),
					IPFSCascadeReport.describe(publication.getPublishedSoFar())));
			return false;
		}
		describePublication(container, modelFiles, publication, entries, details);
		return true;
	}

	/**
	 * Fills the manifest entries and the details from a consistent
	 * publication: each file of the folder with its new CID and changed
	 * imports, then the versions outside the folder that got new versions.
	 */
	private static void describePublication(IContainer container, List<IFile> modelFiles,
			IPFSImportCascade publication, List<ModelEntry> entries, StringBuilder details) {
		Map<IFile, IPFSCascadeModel> modelByFile = new HashMap<IFile, IPFSCascadeModel>();
		for (IPFSCascadeModel model : publication.getStartModels()) {
			modelByFile.put(((WorkspaceWorkingCopy) model.getWorkingCopy()).getFile(), model);
		}
		for (IFile modelFile : modelFiles) {
			IPFSCascadeModel model = modelByFile.get(modelFile);
			String relativePath = IPFSWorkspaceResourceSupport.toRelativePath(container, modelFile);
			entries.add(new ModelEntry(relativePath, model.getNewCid()));
			details.append(relativePath).append(" -> ").append(model.getNewCid()) //$NON-NLS-1$
					.append(IPFSCascadeReport.describeChanges(model)).append('\n');
		}
		for (IPFSCascadeModel importer : publication.getImporters()) {
			details.append(IPFSCascadeReport.nameOf(importer)).append(" -> ").append(importer.getNewCid()) //$NON-NLS-1$
					.append(IPFSCascadeReport.describeChanges(importer)).append('\n');
		}
	}

	/**
	 * Asks before the publication gives new versions to models outside the
	 * folder: versions on IPFS that the folder pins and that import a model of
	 * the folder.
	 */
	private static boolean confirmVersionsOutsideTheFolder(Shell shell, IContainer container,
			IPFSImportCascade publication) {
		if (publication.getImporters().isEmpty()) {
			return true;
		}
		return MessageDialog.openConfirm(shell, Messages.IPFS_Project_Publish_Outside_Title,
				NLS.bind(Messages.IPFS_Project_Publish_Outside_Message, container.getFullPath().makeRelative(),
						IPFSCascadeReport.listImporters(publication.getImporters())));
	}

	/**
	 * Tells why the folder cannot be published as a consistent set and asks
	 * whether to publish the files as they are.
	 */
	private static boolean askToPublishAsIs(Shell shell, String reason) {
		MessageDialog dialog = new MessageDialog(shell, Messages.IPFS_Project_Publish_Error_Title, null,
				NLS.bind(Messages.IPFS_Project_Publish_As_Is_Message, reason), MessageDialog.WARNING, 1,
				Messages.IPFS_Project_Publish_As_Is_Button, IDialogConstants.CANCEL_LABEL);
		return dialog.open() == 0;
	}

	/**
	 * Publishes every file as it is, which is how the command worked before it
	 * published the folder as one consistent set.
	 */
	private static void publishAsIs(IContainer container, List<IFile> modelFiles, String apiUrl,
			List<ModelEntry> entries, StringBuilder details) throws IOException {
		for (IFile modelFile : modelFiles) {
			if (modelFile.getLocation() == null) {
				Activator.logError(new IllegalStateException(
						"Skipping model file with no filesystem location: " + modelFile.getFullPath())); //$NON-NLS-1$
				continue;
			}
			String relativePath = IPFSWorkspaceResourceSupport.toRelativePath(container, modelFile);
			PublicationResult publication = IPFSModelTransfer.publish(modelFile.getLocation().toFile(), apiUrl);
			entries.add(new ModelEntry(relativePath, publication.getCid()));
			details.append(relativePath).append(" -> ").append(publication.getCid()).append('\n'); //$NON-NLS-1$
		}
	}

	/** Wraps the files of the folder as working copies of the publication. */
	private static List<IPFSWorkingCopy> workingCopiesOf(List<IFile> modelFiles) {
		List<IPFSWorkingCopy> workingCopies = new ArrayList<IPFSWorkingCopy>();
		for (IFile modelFile : modelFiles) {
			workingCopies.add(new WorkspaceWorkingCopy(modelFile));
		}
		return workingCopies;
	}
	// EcoreFS end: the files of the folder are published as one consistent set

	/** Puts the manifest references before the list of models. */
	private String buildPublicationDetails(PublicationResult manifestPublication, String modelDetails) {
		StringBuilder details = new StringBuilder();
		details.append(NLS.bind(Messages.IPFS_Project_Publish_Manifest_Details,
				new Object[] { manifestPublication.getCid(), manifestPublication.getIpfsUri(), manifestPublication.getIpfsPath(),
						manifestPublication.getGatewayUrl() }));
		if (manifestPublication.hasIpnsPublication()) {
			details.append('\n').append('\n');
			details.append(NLS.bind(Messages.IPFS_Project_Publish_Manifest_Details_With_IPNS,
					new Object[] { manifestPublication.getIpnsName(), manifestPublication.getIpnsUri(),
							manifestPublication.getIpnsPath(), manifestPublication.getIpnsGatewayUrl() }));
		}
		details.append('\n').append('\n').append(modelDetails);
		return details.toString();
	}
}
// EcoreFS end: command that publishes all BPMN models in the active project or folder through a manifest
