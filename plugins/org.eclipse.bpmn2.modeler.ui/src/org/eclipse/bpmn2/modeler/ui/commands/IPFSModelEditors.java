package org.eclipse.bpmn2.modeler.ui.commands;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.bpmn2.modeler.ui.Activator;
import org.eclipse.bpmn2.modeler.ui.editor.BPMN2Editor;
import org.eclipse.bpmn2.modeler.ui.editor.BPMN2MultiPageEditor;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;

// EcoreFS begin: the open BPMN editors of the files that IPFS commands rewrite
/**
 * Finds the open BPMN editors of model files, so that the IPFS commands can
 * save them before they read the files and close and reopen them around a
 * rewrite.
 *
 * <p>
 * The BPMN2 editor ignores changes that others make to its file: it keeps the
 * model it loaded and writes it back on its next save. A file that a command
 * rewrites therefore has its editor closed first and opened again afterwards,
 * which loads the new contents and the imports they name. Editors that the
 * workbench has not restored since its start hold no model yet and load the
 * file when they are first shown, so they stay as they are.
 * </p>
 */
final class IPFSModelEditors {

	/** Prevents instances; the class only has static helpers. */
	private IPFSModelEditors() {
	}

	/**
	 * Saves every BPMN editor with unsaved changes whose model belongs to the
	 * project.
	 *
	 * @param project the project whose models a command is about to read
	 * @return false when an editor still has unsaved changes after the save
	 */
	static boolean saveDirtyEditors(IProject project) {
		boolean allSaved = true;
		for (IEditorReference reference : allEditorReferences()) {
			IEditorPart part = reference.getEditor(false);
			IFile file = modelFileOf(part);
			if (part != null && part.isDirty() && file != null && project.equals(file.getProject())) {
				part.doSave(new NullProgressMonitor());
				allSaved = allSaved && !part.isDirty();
			}
		}
		return allSaved;
	}

	/**
	 * Closes the open editors of the given files without saving them, because
	 * {@link #saveDirtyEditors(IProject)} saved them before.
	 *
	 * @param files the files that are about to be rewritten
	 * @return the closed editors, to pass to {@link #reopen(List)}
	 */
	static List<ClosedEditor> closeEditorsOf(Collection<IFile> files) {
		List<ClosedEditor> closed = new ArrayList<ClosedEditor>();
		for (IEditorReference reference : allEditorReferences()) {
			IFile file = modelFileOf(reference.getEditor(false));
			if (file != null && files.contains(file)) {
				IWorkbenchPage page = reference.getPage();
				closed.add(new ClosedEditor(page, file, reference.getId()));
				page.closeEditors(new IEditorReference[] { reference }, false);
			}
		}
		return closed;
	}

	/**
	 * Closes the open editors of a file and lets the user save unsaved
	 * changes first.
	 *
	 * @param file the file that is about to be replaced
	 * @return false when the user cancelled the closing of an editor
	 */
	static boolean closeEditorsAskingToSave(IFile file) {
		for (IEditorReference reference : allEditorReferences()) {
			IEditorPart part = reference.getEditor(false);
			if (file.equals(modelFileOf(part)) && !reference.getPage().closeEditor(part, true)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Opens closed editors again, without taking the focus from the editor
	 * that has it. An editor that fails to open is logged and skipped, since
	 * the file itself is in place.
	 *
	 * @param editors the editors that {@link #closeEditorsOf(Collection)} closed
	 */
	static void reopen(List<ClosedEditor> editors) {
		for (ClosedEditor editor : editors) {
			try {
				IDE.openEditor(editor.page, editor.file, editor.editorId, false);
			} catch (PartInitException e) {
				Activator.logError(e);
			}
		}
	}

	/**
	 * Returns the model file of a BPMN editor part, or null when the part is
	 * not a BPMN editor or shows a model that is not a workspace file.
	 */
	static IFile modelFileOf(IEditorPart part) {
		if (part instanceof BPMN2MultiPageEditor) {
			BPMN2Editor designEditor = ((BPMN2MultiPageEditor) part).getDesignEditor();
			return designEditor == null ? null : designEditor.getModelFile();
		}
		if (part instanceof BPMN2Editor) {
			return ((BPMN2Editor) part).getModelFile();
		}
		return null;
	}

	/** Returns the editor references of every page of every workbench window. */
	private static List<IEditorReference> allEditorReferences() {
		List<IEditorReference> references = new ArrayList<IEditorReference>();
		for (IWorkbenchWindow window : PlatformUI.getWorkbench().getWorkbenchWindows()) {
			for (IWorkbenchPage page : window.getPages()) {
				for (IEditorReference reference : page.getEditorReferences()) {
					references.add(reference);
				}
			}
		}
		return references;
	}

	/** An editor that a command closed and opens again after the rewrite. */
	static final class ClosedEditor {

		private final IWorkbenchPage page;
		private final IFile file;
		private final String editorId;

		/**
		 * Remembers where and how to open the editor again.
		 *
		 * @param page     the workbench page of the editor
		 * @param file     the file the editor showed
		 * @param editorId the id of the editor, so the same editor opens again
		 */
		private ClosedEditor(IWorkbenchPage page, IFile file, String editorId) {
			this.page = page;
			this.file = file;
			this.editorId = editorId;
		}
	}
}
// EcoreFS end: the open BPMN editors of the files that IPFS commands rewrite
