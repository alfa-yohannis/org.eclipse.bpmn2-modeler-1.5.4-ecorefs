package org.eclipse.bpmn2.modeler.ui.commands;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.eclipse.bpmn2.modeler.core.builder.BPMN2Nature;
import org.eclipse.bpmn2.modeler.core.preferences.Bpmn2Preferences;
import org.eclipse.bpmn2.modeler.ui.editor.BPMN2Editor;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.handlers.HandlerUtil;

import org.eclipse.bpmn2.modeler.ui.util.IPFSModelFileName;
import org.eclipse.bpmn2.modeler.ui.util.IPFSModelTransfer;

// EcoreFS begin: shared workspace helpers keep the single-file and project-level IPFS commands consistent
final class IPFSWorkspaceResourceSupport {

	private IPFSWorkspaceResourceSupport() {
	}

	static Bpmn2Preferences resolvePreferences() {
		BPMN2Editor editor = BPMN2Editor.getActiveEditor();
		if (editor != null && editor.getProject() != null) {
			return Bpmn2Preferences.getInstance(editor.getProject());
		}

		IProject project = Bpmn2Preferences.getActiveProject();
		if (project != null) {
			return Bpmn2Preferences.getInstance(project);
		}
		return Bpmn2Preferences.getInstance();
	}

	static IContainer resolveTargetContainer(ExecutionEvent event) {
		ISelection selection = HandlerUtil.getCurrentSelection(event);
		if (selection instanceof IStructuredSelection) {
			Object firstElement = ((IStructuredSelection) selection).getFirstElement();
			IResource selectedResource = adaptToResource(firstElement);
			if (selectedResource instanceof IContainer && selectedResource.isAccessible()) {
				return (IContainer) selectedResource;
			}
			if (selectedResource instanceof IFile && selectedResource.getParent() != null
					&& selectedResource.getParent().isAccessible()) {
				return selectedResource.getParent();
			}
		}

		BPMN2Editor editor = BPMN2Editor.getActiveEditor();
		if (editor != null) {
			IFile modelFile = editor.getModelFile();
			if (modelFile != null && modelFile.getParent() != null && modelFile.getParent().isAccessible()) {
				return modelFile.getParent();
			}
			if (editor.getProject() != null && editor.getProject().isAccessible()) {
				return editor.getProject();
			}
		}

		IProject project = Bpmn2Preferences.getActiveProject();
		if (project != null && project.isAccessible()) {
			return project;
		}
		return null;
	}

	static IFile resolveSelectedBpmnFile(ExecutionEvent event, IContainer container) {
		ISelection selection = HandlerUtil.getCurrentSelection(event);
		if (selection instanceof IStructuredSelection) {
			Object firstElement = ((IStructuredSelection) selection).getFirstElement();
			IResource selectedResource = adaptToResource(firstElement);
			if (selectedResource instanceof IFile && isBpmnFile((IFile) selectedResource)
					&& container != null && container.getFullPath().isPrefixOf(selectedResource.getFullPath())) {
				return (IFile) selectedResource;
			}
		}

		BPMN2Editor editor = BPMN2Editor.getActiveEditor();
		if (editor != null) {
			IFile modelFile = editor.getModelFile();
			if (modelFile != null && isBpmnFile(modelFile) && container != null
					&& container.getFullPath().isPrefixOf(modelFile.getFullPath())) {
				return modelFile;
			}
		}
		return null;
	}

	/**
	 * Downloads a model into a workspace container. The file is named after the
	 * model, because a CID does not record the name it was published under, and
	 * gets a numbered suffix when that name is taken.
	 */
	static IFile downloadToWorkspaceFile(String reference, String apiUrl, IContainer container) throws Exception {
		byte[] contents = IPFSModelTransfer.downloadBytes(reference, apiUrl);
		// EcoreFS begin: the naming of a new model file is shared with the download into a namespace folder
		// IFile targetFile = createUniqueTargetFile(container, IPFSModelFileName.derive(reference, contents), "bpmn"); //$NON-NLS-1$
		// return writeWorkspaceFile(container, targetFile.getName(), contents);
		return writeNewModelFile(container, reference, contents);
		// EcoreFS end: the naming of a new model file is shared with the download into a namespace folder
	}

	// EcoreFS begin: the naming of a new model file is shared with the download into a namespace folder
	/**
	 * Writes downloaded bytes to a new file in a container. The file is named
	 * after the model and gets a numbered suffix when that name is taken. The
	 * download into a namespace folder falls back to this for a model without
	 * a usable namespace.
	 */
	static IFile writeNewModelFile(IContainer container, String reference, byte[] contents) throws Exception {
		IFile targetFile = createUniqueTargetFile(container, IPFSModelFileName.derive(reference, contents), "bpmn"); //$NON-NLS-1$
		return writeWorkspaceFile(container, targetFile.getName(), contents);
	}
	// EcoreFS end: the naming of a new model file is shared with the download into a namespace folder

	static IFile writeWorkspaceFile(IContainer container, String relativePath, byte[] contents) throws Exception {
		IPath path = new Path(relativePath);
		for (String segment : path.segments()) {
			if ("..".equals(segment) || ".".equals(segment)) { //$NON-NLS-1$ //$NON-NLS-2$
				throw new IllegalArgumentException(
						"Manifest path contains illegal segment '" + segment + "': " + relativePath); //$NON-NLS-1$ //$NON-NLS-2$
			}
		}
		if (path.segmentCount() > 1) {
			ensureFolder(container, path.removeLastSegments(1));
		}

		IFile file = container.getFile(path);
		try (InputStream input = new ByteArrayInputStream(contents)) {
			if (file.exists()) {
				// EcoreFS begin: a replaced file keeps its previous contents in the local history
				// file.setContents(input, IResource.FORCE, new NullProgressMonitor());
				file.setContents(input, IResource.FORCE | IResource.KEEP_HISTORY, new NullProgressMonitor());
				// EcoreFS end: a replaced file keeps its previous contents in the local history
			} else {
				file.create(input, IResource.FORCE, new NullProgressMonitor());
			}
		}
		file.refreshLocal(IResource.DEPTH_ZERO, new NullProgressMonitor());
		return file;
	}

	static IFolder createUniqueFolder(IContainer parent, String baseName) throws Exception {
		String normalized = normalizeFolderBaseName(baseName);
		IFolder folder = parent.getFolder(new Path(normalized));
		int suffix = 1;
		while (folder.exists()) {
			folder = parent.getFolder(new Path(normalized + "-" + suffix)); //$NON-NLS-1$
			suffix++;
		}
		folder.create(true, true, new NullProgressMonitor());
		return folder;
	}

	// EcoreFS begin: new workspace project for imports that have no target container
	/**
	 * Creates and opens a project for an import that has nowhere else to go,
	 * for example in an empty workspace. The project is named after
	 * {@code baseName}, with a numbered suffix when a project or a leftover
	 * directory already uses that name, and gets the BPMN2 nature so the user
	 * is not asked to add it on the first save.
	 */
	static IProject createUniqueProject(String baseName) throws CoreException {
		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		String name = baseName == null ? "" : baseName.trim(); //$NON-NLS-1$
		if (name.isEmpty() || !root.getWorkspace().validateName(name, IResource.PROJECT).isOK()) {
			name = normalizeFolderBaseName(baseName);
		}
		IProject project = root.getProject(name);
		int suffix = 1;
		// Skip names that belong to a project or to a leftover directory in the workspace location
		while (project.exists() || root.getLocation().append(project.getName()).toFile().exists()) {
			project = root.getProject(name + "-" + suffix); //$NON-NLS-1$
			suffix++;
		}
		project.create(new NullProgressMonitor());
		project.open(new NullProgressMonitor());

		// The BPMN2 nature adds the model validation builder and spares the user the
		// "add the BPMN2 nature?" question on the first save
		IProjectDescription description = project.getDescription();
		description.setNatureIds(new String[] { BPMN2Nature.NATURE_ID });
		project.setDescription(description, new NullProgressMonitor());
		return project;
	}
	// EcoreFS end: new workspace project for imports that have no target container

	static List<IFile> collectBpmnFiles(IContainer root) throws Exception {
		final List<IFile> files = new ArrayList<IFile>();
		root.accept(new IResourceVisitor() {
			@Override
			public boolean visit(IResource resource) {
				if (resource instanceof IFile && isBpmnFile((IFile) resource)) {
					files.add((IFile) resource);
				}
				return true;
			}
		});
		Collections.sort(files, new Comparator<IFile>() {
			@Override
			public int compare(IFile left, IFile right) {
				return left.getFullPath().toPortableString().compareTo(right.getFullPath().toPortableString());
			}
		});
		return files;
	}

	static String toRelativePath(IContainer root, IFile file) {
		return file.getFullPath().makeRelativeTo(root.getFullPath()).toPortableString();
	}

	private static IResource adaptToResource(Object element) {
		if (element instanceof IResource) {
			return (IResource) element;
		}
		if (element instanceof IAdaptable) {
			return (IResource) ((IAdaptable) element).getAdapter(IResource.class);
		}
		return null;
	}

	private static boolean isBpmnFile(IFile file) {
		String extension = file.getFileExtension();
		return "bpmn".equalsIgnoreCase(extension) || "bpmn2".equalsIgnoreCase(extension); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static IFolder ensureFolder(IContainer container, IPath relativeFolder) throws Exception {
		IContainer current = container;
		for (String segment : relativeFolder.segments()) {
			IFolder folder = current.getFolder(new Path(segment));
			if (!folder.exists()) {
				folder.create(true, true, new NullProgressMonitor());
			}
			current = folder;
		}
		return (IFolder) current;
	}

	private static IFile createUniqueTargetFile(IContainer container, String baseName, String extension) {
		IFile file = container.getFile(new Path(baseName + "." + extension)); //$NON-NLS-1$
		int suffix = 1;
		while (file.exists()) {
			file = container.getFile(new Path(baseName + "-" + suffix + "." + extension)); //$NON-NLS-1$ //$NON-NLS-2$
			suffix++;
		}
		return file;
	}

	private static String normalizeFolderBaseName(String baseName) {
		String normalized = baseName == null ? "ipfs-project" : baseName.trim(); //$NON-NLS-1$
		normalized = normalized.replaceAll("\\.bpmn2?$", ""); //$NON-NLS-1$ //$NON-NLS-2$
		normalized = normalized.replaceAll("^[^a-zA-Z0-9]+", "ipfs-project-"); //$NON-NLS-1$ //$NON-NLS-2$
		normalized = normalized.replaceAll("[^a-zA-Z0-9._-]", "_"); //$NON-NLS-1$ //$NON-NLS-2$
		if (normalized.isEmpty()) {
			normalized = "ipfs-project"; //$NON-NLS-1$
		}
		if (normalized.length() > 80) {
			normalized = normalized.substring(0, 80);
		}
		return normalized;
	}
}
// EcoreFS end: shared workspace helpers keep the single-file and project-level IPFS commands consistent
