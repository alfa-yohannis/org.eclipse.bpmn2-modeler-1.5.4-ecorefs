package org.eclipse.bpmn2.modeler.ui.commands;

import java.util.List;

import org.eclipse.bpmn2.modeler.ui.util.IPFSCascadeModel;
import org.eclipse.osgi.util.NLS;

// EcoreFS begin: the text that the cascade command shows before and after a publication
/**
 * Writes the text that {@link IPFSPublishCascadeCommand} shows: the list of
 * models that a cascade will republish, and the new CID and changed import
 * locations of each model it published.
 */
final class IPFSCascadeReport {

	private static final String INDENT = "\n  "; //$NON-NLS-1$

	/** Prevents instances; the class only has static helpers. */
	private IPFSCascadeReport() {
	}

	/**
	 * Lists the models that a cascade will republish, one per line.
	 *
	 * @param importers the affected models in publication order
	 * @return the list for the confirmation dialog
	 */
	static String listImporters(List<IPFSCascadeModel> importers) {
		StringBuilder list = new StringBuilder();
		for (IPFSCascadeModel importer : importers) {
			if (list.length() > 0) {
				list.append('\n');
			}
			list.append("- ").append(nameOf(importer)); //$NON-NLS-1$
		}
		return list.toString();
	}

	/**
	 * Describes the models that a cascade published, in order, each with its
	 * new CID, the import locations that changed and whether its working copy
	 * was updated.
	 *
	 * @param publishedModels the published models
	 * @return the text for the result dialog
	 */
	static String describe(List<IPFSCascadeModel> publishedModels) {
		StringBuilder report = new StringBuilder();
		for (IPFSCascadeModel model : publishedModels) {
			if (report.length() > 0) {
				report.append("\n\n"); //$NON-NLS-1$
			}
			appendModel(report, model);
		}
		return report.toString();
	}

	/** Appends the lines of one published model. */
	private static void appendModel(StringBuilder report, IPFSCascadeModel model) {
		report.append(nameOf(model));
		report.append(INDENT).append(NLS.bind(Messages.IPFS_Cascade_Result_Cid, model.getNewCid()));
		report.append(describeChanges(model));
	}

	/**
	 * Returns the lines under one published model: the import locations that
	 * changed and whether its working copy was updated. Each line starts with a
	 * line break and an indent, so the text can follow the line of the model.
	 *
	 * @param model a published model
	 * @return the lines, or an empty text when nothing changed
	 */
	static String describeChanges(IPFSCascadeModel model) {
		StringBuilder lines = new StringBuilder();
		for (IPFSCascadeModel.ImportChange change : model.getImportChanges()) {
			lines.append(INDENT).append(NLS.bind(Messages.IPFS_Cascade_Result_Import_Change,
					new Object[] { change.getNamespace(), change.getOldLocation(), change.getNewLocation() }));
		}
		if (model.isWorkingCopyRewritten()) {
			lines.append(INDENT).append(Messages.IPFS_Cascade_Result_File_Updated);
		}
		return lines.toString();
	}

	/** Names a model, and says so when it has no working copy in the project. */
	static String nameOf(IPFSCascadeModel model) {
		if (model.isWorkingCopy()) {
			return model.getDisplayName();
		}
		return NLS.bind(Messages.IPFS_Cascade_Pinned_Version, model.getDisplayName());
	}
}
// EcoreFS end: the text that the cascade command shows before and after a publication
