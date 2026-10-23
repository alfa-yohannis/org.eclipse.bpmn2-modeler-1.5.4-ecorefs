/*******************************************************************************
 * Copyright (c) 2011, 2012, 2013, 2014 Red Hat, Inc.
 * All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package org.eclipse.bpmn2.modeler.ui.commands;

import org.eclipse.osgi.util.NLS;

public class Messages extends NLS {
	private static final String BUNDLE_NAME = "org.eclipse.bpmn2.modeler.ui.commands.messages"; //$NON-NLS-1$
	public static String CreateDiagramCommand_Title;
	public static String CreateDiagramCommand_Message;
	public static String CreateDiagramCommand_Choreography;
	public static String CreateDiagramCommand_Collaboration;
	public static String CreateDiagramCommand_Invalid_Duplicate;
	public static String CreateDiagramCommand_Invalid_Empty;
	public static String CreateDiagramCommand_Process;
	// EcoreFS begin: message keys for the IPFS UI actions
	public static String IPFS_Open_Title;
	public static String IPFS_Open_Message;
	public static String IPFS_Open_Invalid_Empty;
	public static String IPFS_Open_Invalid_Format;
	public static String IPFS_Open_Error_Title;
	public static String IPFS_Project_Open_Title;
	public static String IPFS_Project_Open_Message;
	public static String IPFS_Project_Open_Error_Title;
	public static String IPFS_Publish_Success_Title;
	public static String IPFS_Publish_Success_Message;
	public static String IPFS_Publish_Success_Message_With_IPNS;
	public static String IPFS_Publish_Result_Details_Message;
	public static String IPFS_Publish_Result_Details_Label;
	public static String IPFS_Publish_Result_Copy_Button;
	public static String IPFS_Publish_Error_Title;
	public static String IPFS_Publish_No_Model;
	public static String IPFS_Publish_Missing_IPNS_Key;
	public static String IPFS_Project_Publish_Success_Title;
	public static String IPFS_Project_Publish_Result_Message;
	public static String IPFS_Project_Publish_Error_Title;
	public static String IPFS_Project_Publish_No_Target;
	public static String IPFS_Project_Publish_No_Models;
	public static String IPFS_Project_Publish_Details_Header;
	public static String IPFS_Project_Publish_Manifest_Details;
	public static String IPFS_Project_Publish_Manifest_Details_With_IPNS;
	// EcoreFS end: message keys for the IPFS UI actions
	// EcoreFS begin: message keys for publishing with cascade and for downloads into namespace folders
	public static String IPFS_Cascade_Error_Title;
	public static String IPFS_Cascade_No_Workspace_File;
	public static String IPFS_Cascade_Unsaved_Editors;
	public static String IPFS_Cascade_Confirm_Title;
	public static String IPFS_Cascade_Confirm_Message;
	public static String IPFS_Cascade_Pinned_Version;
	public static String IPFS_Cascade_Success_Title;
	public static String IPFS_Cascade_Result_Message;
	public static String IPFS_Cascade_No_Importers;
	public static String IPFS_Cascade_Result_Cid;
	public static String IPFS_Cascade_Result_Import_Change;
	public static String IPFS_Cascade_Result_File_Updated;
	public static String IPFS_Cascade_Partial_Failure;
	public static String IPFS_Download_Existing_Title;
	public static String IPFS_Download_Existing_Message;
	public static String IPFS_Download_Replace_Button;
	public static String IPFS_Download_Open_Existing_Button;
	// EcoreFS end: message keys for publishing with cascade and for downloads into namespace folders
	static {
		// initialize resource bundle
		NLS.initializeMessages(BUNDLE_NAME, Messages.class);
	}

	private Messages() {
	}
}
