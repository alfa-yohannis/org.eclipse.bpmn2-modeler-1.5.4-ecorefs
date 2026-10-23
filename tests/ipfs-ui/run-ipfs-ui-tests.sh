#!/usr/bin/env bash
set -euo pipefail

# EcoreFS begin: standalone runner for the BPMN2 Modeler IPFS UI self-tests
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
BUILD_DIR="$SCRIPT_DIR/build"

mkdir -p "$BUILD_DIR"

# EcoreFS begin: the cascade classes and their self-test join the standalone run
# javac --release 17 -d "$BUILD_DIR" \
#   "$REPO_ROOT/plugins/org.eclipse.bpmn2.modeler.ui/src/org/eclipse/bpmn2/modeler/ui/util/IPFSModelTransfer.java" \
#   "$REPO_ROOT/plugins/org.eclipse.bpmn2.modeler.ui/src/org/eclipse/bpmn2/modeler/ui/util/IPFSModelFileName.java" \
#   "$REPO_ROOT/plugins/org.eclipse.bpmn2.modeler.ui/src/org/eclipse/bpmn2/modeler/ui/util/BpmnModelNames.java" \
#   "$SCRIPT_DIR/src/org/eclipse/bpmn2/modeler/ui/tests/IPFSModelTransferSelfTest.java"
UTIL_DIR="$REPO_ROOT/plugins/org.eclipse.bpmn2.modeler.ui/src/org/eclipse/bpmn2/modeler/ui/util"
javac --release 17 -d "$BUILD_DIR" \
  "$UTIL_DIR/IPFSModelTransfer.java" \
  "$UTIL_DIR/IPFSModelFileName.java" \
  "$UTIL_DIR/BpmnModelNames.java" \
  "$UTIL_DIR/XmlStartTagScanner.java" \
  "$UTIL_DIR/XmlAttributeValues.java" \
  "$UTIL_DIR/BpmnImport.java" \
  "$UTIL_DIR/BpmnImports.java" \
  "$UTIL_DIR/BpmnNamespaceFolder.java" \
  "$UTIL_DIR/IPFSWorkingCopy.java" \
  "$UTIL_DIR/IPFSModelStore.java" \
  "$UTIL_DIR/KuboModelStore.java" \
  "$UTIL_DIR/IPFSCascadeException.java" \
  "$UTIL_DIR/IPFSCascadeModel.java" \
  "$UTIL_DIR/IPFSCascadePlanner.java" \
  "$UTIL_DIR/IPFSImportCascade.java" \
  "$SCRIPT_DIR/src/org/eclipse/bpmn2/modeler/ui/tests/IPFSModelTransferSelfTest.java" \
  "$SCRIPT_DIR/src/org/eclipse/bpmn2/modeler/ui/tests/IPFSImportCascadeSelfTest.java"

java -cp "$BUILD_DIR" org.eclipse.bpmn2.modeler.ui.tests.IPFSModelTransferSelfTest "$REPO_ROOT" "$@"
java -cp "$BUILD_DIR" org.eclipse.bpmn2.modeler.ui.tests.IPFSImportCascadeSelfTest "$@"
# EcoreFS end: the cascade classes and their self-test join the standalone run
# EcoreFS end: standalone runner for the BPMN2 Modeler IPFS UI self-tests
