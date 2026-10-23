package org.eclipse.bpmn2.modeler.ui.commands;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.bpmn2.modeler.ui.util.IPFSWorkingCopy;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;

// EcoreFS begin: a BPMN file of the workspace as a working copy of the cascade
/**
 * A BPMN file of the workspace as a working copy of the cascade. Reads and
 * writes go through the Eclipse resource API, so a file that the cascade
 * rewrites keeps its previous contents in the local history.
 */
final class WorkspaceWorkingCopy implements IPFSWorkingCopy {

	private final IFile file;

	/**
	 * Wraps a workspace file.
	 *
	 * @param file a BPMN file of the workspace
	 */
	WorkspaceWorkingCopy(IFile file) {
		this.file = file;
	}

	/**
	 * Lists the BPMN files of a project as working copies, after a refresh
	 * that picks up files changed outside Eclipse.
	 *
	 * @param project the project of the published model
	 * @return the working copies, sorted by path
	 * @throws Exception when the project cannot be refreshed or walked
	 */
	static List<IPFSWorkingCopy> listProject(IProject project) throws Exception {
		project.refreshLocal(IResource.DEPTH_INFINITE, new NullProgressMonitor());
		List<IPFSWorkingCopy> workingCopies = new ArrayList<IPFSWorkingCopy>();
		for (IFile modelFile : IPFSWorkspaceResourceSupport.collectBpmnFiles(project)) {
			workingCopies.add(new WorkspaceWorkingCopy(modelFile));
		}
		return workingCopies;
	}

	/** Returns the wrapped workspace file. */
	IFile getFile() {
		return file;
	}

	/** Returns the workspace path without the leading slash, such as {@code case-study/collaboration.bpmn}. */
	@Override
	public String getPath() {
		return file.getFullPath().makeRelative().toPortableString();
	}

	/** Returns the name of the file. */
	@Override
	public String getFileName() {
		return file.getName();
	}

	/** Reads the saved contents of the file. */
	@Override
	public byte[] readContents() throws IOException {
		try (InputStream input = file.getContents(true)) {
			return input.readAllBytes();
		} catch (CoreException e) {
			throw new IOException(e.getMessage(), e);
		}
	}

	/** Replaces the contents of the file and keeps the old contents in the local history. */
	@Override
	public void writeContents(byte[] contents) throws IOException {
		try {
			file.setContents(new ByteArrayInputStream(contents), IResource.FORCE | IResource.KEEP_HISTORY,
					new NullProgressMonitor());
		} catch (CoreException e) {
			throw new IOException(e.getMessage(), e);
		}
	}
}
// EcoreFS end: a BPMN file of the workspace as a working copy of the cascade
