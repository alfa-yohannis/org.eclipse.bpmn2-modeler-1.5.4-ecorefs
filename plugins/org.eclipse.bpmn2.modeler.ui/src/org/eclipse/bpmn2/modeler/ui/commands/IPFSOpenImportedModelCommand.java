package org.eclipse.bpmn2.modeler.ui.commands;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.bpmn2.Definitions;
import org.eclipse.bpmn2.Import;
import org.eclipse.bpmn2.modeler.core.preferences.Bpmn2Preferences;
import org.eclipse.bpmn2.modeler.core.utils.ModelUtil;
import org.eclipse.bpmn2.modeler.ui.Activator;
import org.eclipse.bpmn2.modeler.ui.editor.BPMN2Editor;
import org.eclipse.bpmn2.modeler.ui.util.BpmnNamespaceFolder;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.dialogs.ElementListSelectionDialog;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.ide.IDE;

// EcoreFS begin: command that downloads a model that the current model imports from IPFS, for editing
/**
 * Downloads one of the models that the model of the active editor imports
 * from IPFS and opens it for editing.
 *
 * <p>
 * An import with an {@code ipfs://} location always loads the version it pins
 * from IPFS, so changing an imported model starts with a working copy of it.
 * The command lists the imports with an {@code ipfs://} or {@code ipns://}
 * location, downloads the chosen one into the folder of its namespace in the
 * project of the current model, as Open Model does, and opens it. The
 * imports come from the model in the editor, so an import that is not saved
 * yet is listed too.
 * </p>
 */
public class IPFSOpenImportedModelCommand extends AbstractHandler {

	public static final String ID = "org.eclipse.bpmn2.modeler.command.openImportedModelFromIpfs"; //$NON-NLS-1$

	/** Lists the imports, downloads the chosen model and opens it. */
	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		BPMN2Editor editor = BPMN2Editor.getActiveEditor();
		if (editor == null) {
			return null;
		}
		Shell shell = HandlerUtil.getActiveShell(event);
		IFile modelFile = editor.getModelFile();
		if (modelFile == null || !modelFile.exists()) {
			MessageDialog.openError(shell, Messages.IPFS_Open_Imported_Error_Title,
					Messages.IPFS_Open_Imported_No_Workspace_File);
			return null;
		}
		List<Import> imports = importsFromIpfs(editor);
		if (imports.isEmpty()) {
			MessageDialog.openInformation(shell, Messages.IPFS_Open_Imported_Title, Messages.IPFS_Open_Imported_None);
			return null;
		}
		Import chosen = choose(shell, imports);
		if (chosen == null) {
			return null;
		}
		try {
			String apiUrl = Bpmn2Preferences.getInstance(modelFile.getProject()).getIpfsApiUrl();
			IFile workingCopy = new IPFSNamespaceDownload(shell, apiUrl).download(chosen.getLocation().trim(),
					modelFile.getParent());
			if (workingCopy != null) {
				IDE.openEditor(HandlerUtil.getActiveWorkbenchWindow(event).getActivePage(), workingCopy);
			}
			return null;
		} catch (Exception e) {
			Activator.logError(e);
			MessageDialog.openError(shell, Messages.IPFS_Open_Imported_Error_Title, e.getMessage());
			throw new ExecutionException("Could not open the imported BPMN2 model from IPFS.", e); //$NON-NLS-1$
		}
	}

	/**
	 * Returns the imports of the model in the editor whose location names a
	 * version on IPFS or an IPNS name.
	 */
	private static List<Import> importsFromIpfs(BPMN2Editor editor) {
		List<Import> imports = new ArrayList<Import>();
		Definitions definitions = ModelUtil.getDefinitions(editor.getResource());
		if (definitions == null) {
			return imports;
		}
		for (Import declaration : definitions.getImports()) {
			if (isIpfsLocation(declaration.getLocation())) {
				imports.add(declaration);
			}
		}
		return imports;
	}

	/** Tells whether an import location names a version on IPFS or an IPNS name. */
	private static boolean isIpfsLocation(String location) {
		if (location == null) {
			return false;
		}
		String trimmed = location.trim();
		return trimmed.startsWith("ipfs://") || trimmed.startsWith("ipns://"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	/** Lets the user pick one import, and returns null when the user cancels. */
	private static Import choose(Shell shell, List<Import> imports) {
		ElementListSelectionDialog dialog = new ElementListSelectionDialog(shell, new ImportLabelProvider());
		dialog.setTitle(Messages.IPFS_Open_Imported_Title);
		dialog.setMessage(Messages.IPFS_Open_Imported_Message);
		dialog.setElements(imports.toArray());
		dialog.setMultipleSelection(false);
		dialog.setSize(130, 14);
		if (dialog.open() != Window.OK) {
			return null;
		}
		return (Import) dialog.getFirstResult();
	}

	/**
	 * Shows an import as the name of the imported model, from the last part of
	 * its namespace, followed by the namespace and the location, so that typing
	 * the start of the name filters the list.
	 */
	private static final class ImportLabelProvider extends LabelProvider {

		/** Describes one import. */
		@Override
		public String getText(Object element) {
			Import declaration = (Import) element;
			String fileName = BpmnNamespaceFolder.fileNameFor(declaration.getNamespace());
			String name = fileName == null ? "" : fileName.substring(0, fileName.lastIndexOf('.')) + "  "; //$NON-NLS-1$ //$NON-NLS-2$
			String namespace = declaration.getNamespace() == null ? "" : declaration.getNamespace() + "  "; //$NON-NLS-1$ //$NON-NLS-2$
			return name + namespace + declaration.getLocation().trim();
		}
	}
}
// EcoreFS end: command that downloads a model that the current model imports from IPFS, for editing
