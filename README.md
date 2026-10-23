# BPMN2 Modeler IPFS UI Integration

This checkout keeps the normal BPMN2 Modeler workflow unchanged:

- local/workspace files still load and save normally
- the editor still works on local `.bpmn` files
- Graphiti `.diagram` sidecars remain local

The UI bundle version remains:

`1.5.4.qualifier`

<!-- EcoreFS begin: short menu labels, a command for imported models and a separator -->
IPFS is exposed through explicit UI actions in the `BPMN2 IPFS` menu instead:

- `BPMN2 IPFS > Open Model...`
- `BPMN2 IPFS > Open Imported Model...`
- `BPMN2 IPFS > Publish Model...`
- `BPMN2 IPFS > Publish Model with Cascade...`
- `BPMN2 IPFS > Open Project...`
- `BPMN2 IPFS > Publish Project...`

A separator keeps the two project actions apart from the model actions. `Open Imported Model...` and the two `Publish Model` actions need an open model, so the menu shows them only while a BPMN editor is active. The commands keep longer names, such as `Publish Model to IPFS with Cascade`, for key bindings and Quick Access.
<!-- EcoreFS end: short menu labels, a command for imported models and a separator -->

## What The Actions Do

`Open Model...`

- accepts a `CID`, `/ipfs/...` path, `ipfs://...` URI, or `IPNS` reference
- fetches the BPMN XML through Kubo
<!-- EcoreFS begin: downloads go to the folder of their namespace -->
<!--
- writes it into the selected workspace folder, the active model folder, or the active project when one is available
-->
- writes it into the folder of its target namespace in the project of the selected folder or of the active model, so every download of a model lands in the same file: `http://ecorefs.org/case-study/order-process` gives `ecorefs.org/case-study/order-process/order-process.bpmn`
- opens that file as it is when it already holds the same bytes; otherwise asks whether to replace it, which keeps the old contents in the local history, or to open the file as it is
- writes a model without a usable target namespace into the selected workspace folder, the active model folder, or the active project, named as described below
<!-- EcoreFS end: downloads go to the folder of their namespace -->
- falls back to a temporary local `.bpmn` file only when there is no usable active workspace location
<!-- EcoreFS begin: readable names for downloaded models -->
- names the file after the model, because a CID does not record the name it was published under: the definitions name, the collaboration name, the first process name, or the first process id, in that order; editor placeholders such as `Default Process` or `Process_1` are used only when nothing better exists, and the CID only when the model has no names
- keeps the file name of a reference that ends in one, such as `/ipfs/<cid>/order.bpmn`, and appends `-1`, `-2`, ... when the name is taken
<!-- EcoreFS end: readable names for downloaded models -->
- opens the persisted file in the existing BPMN2 editor flow

<!-- EcoreFS begin: a command for imported models -->
`Open Imported Model...`

- lists the imports of the model in the active editor whose location is an `ipfs://` URI or an `ipns://` name, including imports that are not saved yet
- downloads the chosen model into the folder of its target namespace in the project of the active model, like `Open Model...`, and opens it for editing
- makes a working copy of a model that the current model loads from IPFS, the first step before changing it and publishing it with cascade
<!-- EcoreFS end: a command for imported models -->

`Publish Model...`

- saves the current BPMN model locally first if the editor is dirty
- uploads the current `.bpmn` model file to Kubo
<!-- EcoreFS begin: per-project IPFS/IPNS publishing behavior documentation -->
- when the project publish mode is `CID`, shows the resulting `CID`, `ipfs://...` URI, `/ipfs/...` path, and local gateway URL
- when the project publish mode is `IPNS`, publishes the new `CID` and then updates the configured IPNS key name to point to it
- the publish success popup is resizable and keeps the full references in a multi-line text area so they can be selected or copied
<!-- EcoreFS end: per-project IPFS/IPNS publishing behavior documentation -->

<!-- EcoreFS begin: publication with cascade -->
`Publish Model with Cascade...`

- publishes the current model and gives every model that imports it by CID a new version that names the new CID, as `saveIPFSCascaded` does in the EcoreFS core plug-in
- saves the open models of the project first
- looks for the importers among the `.bpmn` and `.bpmn2` files of the project and among the versions that those files pin with `ipfs://` import locations; an import refers to the published model when it names the target namespace of that model
- lists the affected models and asks before it publishes anything
- publishes each model after the models it imports: a working copy gets the new CIDs written into the `location` of its imports and keeps every other byte, and a version that exists only on IPFS is republished from its pinned bytes
- closes and reopens the editors of the rewritten files, because the editor does not reload a file that changes on disk
- publishes by CID even when the project publish mode is `IPNS`, because import locations name versions by CID
- stops before it changes anything when two files of the project declare the namespace of the published model, when an affected import pins a version that differs from the working copy of that model, or when models import each other by CID
- shows each published model with its new CID and the import locations that changed

`Save` still writes only the local file, and `Publish Model...` still publishes one model. The models that import it keep the old CID until the cascade runs.
<!-- EcoreFS end: publication with cascade -->

`Publish Project...`

- saves dirty editors before collecting files
- scans the active project or selected folder recursively for `.bpmn` and `.bpmn2` files
<!-- EcoreFS begin: the files of a folder are published as one consistent set -->
<!--
- publishes each BPMN file by CID
-->
- publishes the BPMN files by CID as one consistent set: an `ipfs://` import of another model of the folder gets the CID that this publication gives that model, the files are published in dependency order, and a working copy gets the new locations written into its imports
- gives new versions to versions on IPFS that the folder pins and that import a model of the folder, after asking
- offers to publish the files as they are when the folder cannot be published consistently, for example because of a cycle of imports
<!-- EcoreFS end: the files of a folder are published as one consistent set -->
- generates a small JSON manifest that records the root model and each relative path to CID mapping
- publishes that manifest as the project entry point, optionally through IPNS

`Open Project...`

- accepts a BPMN project manifest reference
- downloads the manifest JSON
- restores all listed `.bpmn` files into a new workspace folder under the active project or selected folder
<!-- EcoreFS begin: project import into a new workspace project -->
- creates a new workspace project named after the published project when nothing is selected and no BPMN editor is open, for example when the Project Explorer is empty; the project gets the BPMN2 nature, and `-1`, `-2`, ... is appended when the name is taken
<!-- EcoreFS end: project import into a new workspace project -->
- opens the manifest root model after the import completes and selects it in the Project Explorer

## Kubo Configuration

<!-- EcoreFS begin: per-project BPMN2 property page documentation for IPFS integration -->
Per-project settings live in:

`Project Properties > BPMN2`

The property page now stores:

- `Kubo API URL`
- `Default load reference`
- `Publish mode` (`CID` or `IPNS`)
- `Default IPNS publish key name`

By default, projects talk to:

`http://127.0.0.1:5001`

If the project property is left at the default value, you can still override that process-wide with either:

- JVM system property: `-Dorg.eclipse.bpmn2.modeler.ipfs.apiUrl=http://HOST:5001`
- environment variable: `BPMN2_MODELER_IPFS_API_URL=http://HOST:5001`
<!-- EcoreFS end: per-project BPMN2 property page documentation for IPFS integration -->

## Supported Input Formats

The open action accepts:

- `Qm...`
- `bafy...`
- `/ipfs/<cid>`
- `ipfs://<cid>`
- `k51...`
- `/ipns/<name-or-key>`
- `ipns://<name-or-key>`

## Caveats

- This integration publishes and reloads the BPMN model file, not the local Graphiti `.diagram` file.
- When a workspace folder or project is active, models opened from IPFS become normal persisted workspace files.
- The temp-file fallback is still used when there is no active workspace location to persist into.
- Project imports always restore into a real workspace folder; they do not use temp files.
- IPNS publishing expects a Kubo key name that already exists in the local node.
<!-- EcoreFS begin: publication with cascade -->
- The cascade finds importers only in the project of the published model and in the versions those files pin. IPFS cannot list the other models that import a CID.
<!-- EcoreFS end: publication with cascade -->

## Build

From the repository root:

```bash
mvn -f pom.xml -DskipTests package
```

If you only want to build the UI plugin reactor slice:

```bash
mvn -f pom.xml -pl plugins/org.eclipse.bpmn2.modeler.ui -am -DskipTests package
```

## Self-Tests

Fast structural checks:

```bash
tests/ipfs-ui/run-ipfs-ui-tests.sh
```

Structural checks plus a real Kubo publish/download round-trip:

```bash
tests/ipfs-ui/run-ipfs-ui-tests.sh --integration
```

These self-tests cover:

- reference normalization for `CID`, `ipfs://`, `/ipfs/...`, and `IPNS`
- plugin command and handler registration
- bundle dependency/version metadata
- workspace-persist behavior for the IPFS open action
- project-manifest command wiring and documentation
- a real BPMN file round-trip through Kubo when `--integration` is enabled
<!-- EcoreFS begin: self-tests of the cascade -->
- the namespace folders, the reading and rewriting of BPMN imports, and the planning and publication of a cascade with an in-memory store, plus the same cascade on Kubo when `--integration` is enabled
<!-- EcoreFS end: self-tests of the cascade -->
