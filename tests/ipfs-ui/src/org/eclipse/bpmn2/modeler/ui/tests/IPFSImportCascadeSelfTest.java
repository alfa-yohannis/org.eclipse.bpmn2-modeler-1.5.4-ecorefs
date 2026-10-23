package org.eclipse.bpmn2.modeler.ui.tests;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.bpmn2.modeler.ui.util.BpmnImport;
import org.eclipse.bpmn2.modeler.ui.util.BpmnImports;
import org.eclipse.bpmn2.modeler.ui.util.BpmnNamespaceFolder;
import org.eclipse.bpmn2.modeler.ui.util.IPFSCascadeException;
import org.eclipse.bpmn2.modeler.ui.util.IPFSCascadeModel;
import org.eclipse.bpmn2.modeler.ui.util.IPFSImportCascade;
import org.eclipse.bpmn2.modeler.ui.util.IPFSModelStore;
import org.eclipse.bpmn2.modeler.ui.util.IPFSWorkingCopy;
import org.eclipse.bpmn2.modeler.ui.util.KuboModelStore;

// EcoreFS begin: standalone self-test of the namespace folders, the import rewrite and the cascade
/**
 * Checks the parts of Publish to IPFS with Cascade that need no workbench: the
 * folder of a working copy, the reading and rewriting of BPMN imports, and the
 * planning and publication of a cascade. The models have the shape of the
 * EcoreFS case study: a payment process, an order process that imports it, and
 * a collaboration that imports both. An in-memory store stands in for Kubo,
 * and {@code --integration} repeats the cascade on a real Kubo node.
 */
public final class IPFSImportCascadeSelfTest {

	private static final String BPMN = "http://www.omg.org/spec/BPMN/20100524/MODEL"; //$NON-NLS-1$
	private static final String XSD = "http://www.w3.org/2001/XMLSchema"; //$NON-NLS-1$
	private static final String PAYMENT = "http://ecorefs.org/case-study/payment-process"; //$NON-NLS-1$
	private static final String ORDER = "http://ecorefs.org/case-study/order-process"; //$NON-NLS-1$
	private static final String COLLABORATION = "http://ecorefs.org/case-study/collaboration"; //$NON-NLS-1$

	/** Prevents instances; the class is a program. */
	private IPFSImportCascadeSelfTest() {
	}

	/**
	 * Runs the checks.
	 *
	 * @param args {@code --integration} adds the cascade on the Kubo node of the default API address
	 * @throws Exception when a check fails
	 */
	public static void main(String[] args) throws Exception {
		testNamespaceFolders();
		testImportsAreReadFromBpmnImportsOnly();
		testRewriteChangesOnlyTheLocations();
		testCascadeRepublishesImportersInOrder();
		testWorkingCopyOfAPinnedVersionIsRewritten();
		testDifferentWorkingCopyStopsTheCascade();
		testCycleStopsTheCascade();
		testSharedNamespaceStopsTheCascade();
		testSharedNamespaceWithoutImportersIsPublished();
		testModelWithoutImportersIsPublishedAlone();
		testProjectNamesTheVersionsPublishedTogether();
		testProjectGivesPinnedVersionsNewVersions();
		testProjectRefusesACycle();
		System.out.println("IPFS cascade self-tests passed."); //$NON-NLS-1$
		if (Arrays.asList(args).contains("--integration")) { //$NON-NLS-1$
			testCascadeOnKubo();
			System.out.println("IPFS cascade integration on Kubo passed."); //$NON-NLS-1$
		}
	}

	/** Checks that the folder of a working copy follows its namespace and stays a safe path. */
	private static void testNamespaceFolders() {
		assertEquals("ecorefs.org/case-study/order-process/order-process.bpmn", //$NON-NLS-1$
				BpmnNamespaceFolder.modelPathFor(ORDER), "an http namespace should drop the scheme"); //$NON-NLS-1$
		assertEquals("urn/example/payments/payments.bpmn", //$NON-NLS-1$
				BpmnNamespaceFolder.modelPathFor("urn:example:payments"), "a urn should keep its scheme"); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("example.org/models/models.bpmn", //$NON-NLS-1$
				BpmnNamespaceFolder.modelPathFor("https://example.org/models/"), "a trailing slash should not count"); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("host/8080/a_b/_hidden/_hidden.bpmn", //$NON-NLS-1$
				BpmnNamespaceFolder.modelPathFor("http://host:8080/a b/.hidden"), //$NON-NLS-1$
				"unsafe characters and hidden names should be replaced"); //$NON-NLS-1$
		assertEquals(null, BpmnNamespaceFolder.modelPathFor(null), "no namespace should give no folder"); //$NON-NLS-1$
		assertEquals(null, BpmnNamespaceFolder.modelPathFor("http://"), "an empty namespace should give no folder"); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("order-process.bpmn", BpmnNamespaceFolder.fileNameFor(ORDER), //$NON-NLS-1$
				"the file should be named after the last part of the namespace"); //$NON-NLS-1$
	}

	/**
	 * Checks that only BPMN imports count: not an import of an XML schema, not
	 * one inside a comment, and not one without a location.
	 */
	private static void testImportsAreReadFromBpmnImportsOnly() throws Exception {
		String text = "<?xml version='1.0' encoding='UTF-8'?>\n" //$NON-NLS-1$
				+ "<!-- <bpmn2:import importType=\"" + BPMN + "\" location=\"ipfs://inComment\" namespace=\"x\"/> -->\n" //$NON-NLS-1$ //$NON-NLS-2$
				+ "<definitions xmlns='" + BPMN + "' targetNamespace='urn:a&amp;b'>\n" //$NON-NLS-1$ //$NON-NLS-2$
				+ "  <import importType='" + BPMN + "' location='ipfs://first' namespace='urn:x&amp;y'/>\n" //$NON-NLS-1$ //$NON-NLS-2$
				+ "  <import importType='" + XSD + "' location='types.xsd' namespace='urn:types'/>\n" //$NON-NLS-1$ //$NON-NLS-2$
				+ "  <import importType='" + BPMN + "' namespace='urn:nolocation'/>\n" //$NON-NLS-1$ //$NON-NLS-2$
				+ "  <import importType='" + BPMN + "' location='local.bpmn' namespace='urn:local'/>\n" //$NON-NLS-1$ //$NON-NLS-2$
				+ "  <process id='P' name='a > b'/>\n" //$NON-NLS-1$
				+ "</definitions>\n<!-- after the root -->\n"; //$NON-NLS-1$
		BpmnImports imports = BpmnImports.read(text.getBytes(StandardCharsets.UTF_8));
		assertEquals("urn:a&b", imports.getTargetNamespace(), "entity references should be resolved"); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("2", String.valueOf(imports.getImports().size()), "two BPMN imports have a location"); //$NON-NLS-1$ //$NON-NLS-2$
		List<BpmnImport> ipfsImports = imports.getIpfsImports();
		assertEquals("1", String.valueOf(ipfsImports.size()), "one import names a CID"); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("urn:x&y", ipfsImports.get(0).getNamespace(), "the import namespace should be decoded"); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals(null, BpmnImports.read("<root/>".getBytes(StandardCharsets.UTF_8)).getTargetNamespace(), //$NON-NLS-1$
				"a file that is not BPMN should have no namespace"); //$NON-NLS-1$
	}

	/**
	 * Checks that a rewrite changes the location values and every other byte
	 * stays, including comments and characters outside ASCII.
	 */
	private static void testRewriteChangesOnlyTheLocations() throws Exception {
		byte[] original = model(COLLABORATION, "Collaboration", ORDER, "ipfs://oldOrder", PAYMENT, "ipfs://oldPayment"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		Map<String, String> newLocations = new HashMap<String, String>();
		newLocations.put("ipfs://oldOrder", "ipfs://newOrder"); //$NON-NLS-1$ //$NON-NLS-2$
		newLocations.put("ipfs://commented", "ipfs://mustNotAppear"); //$NON-NLS-1$ //$NON-NLS-2$
		byte[] rewritten = BpmnImports.read(original).withLocations(newLocations);
		String expected = new String(original, StandardCharsets.UTF_8).replace("location=\"ipfs://oldOrder\"", //$NON-NLS-1$
				"location=\"ipfs://newOrder\""); //$NON-NLS-1$
		assertEquals(expected, new String(rewritten, StandardCharsets.UTF_8),
				"only the location of the order import should change"); //$NON-NLS-1$
	}

	/**
	 * Checks the case-study cascade with the order process only on IPFS: the
	 * order process gets a new version from its pinned bytes, and the
	 * collaboration working copy gets both new CIDs.
	 */
	private static void testCascadeRepublishesImportersInOrder() throws Exception {
		MemoryStore store = new MemoryStore();
		CaseStudy caseStudy = new CaseStudy(store);
		MemoryWorkingCopy payment = new MemoryWorkingCopy("p/payment-process.bpmn", caseStudy.changedPayment()); //$NON-NLS-1$
		MemoryWorkingCopy collaboration = new MemoryWorkingCopy("p/collaboration.bpmn", caseStudy.collaboration); //$NON-NLS-1$
		IPFSImportCascade cascade = IPFSImportCascade.plan(payment, workingCopies(payment, collaboration), store);
		List<IPFSCascadeModel> importers = cascade.getImporters();
		assertEquals("2", String.valueOf(importers.size()), "the order process and the collaboration are affected"); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals(caseStudy.orderLocation, importers.get(0).getPinnedLocation(),
				"the order process has no working copy and comes first"); //$NON-NLS-1$
		assertEquals("p/collaboration.bpmn", importers.get(1).getDisplayName(), "the collaboration comes last"); //$NON-NLS-1$ //$NON-NLS-2$

		cascade.publish();
		String newPayment = "ipfs://" + cascade.getPublishedModel().getNewCid(); //$NON-NLS-1$
		String newOrder = "ipfs://" + importers.get(0).getNewCid(); //$NON-NLS-1$
		assertContains(new String(store.read(newOrder), StandardCharsets.UTF_8), "location=\"" + newPayment + "\"", //$NON-NLS-1$ //$NON-NLS-2$
				"the new order process should import the new payment process"); //$NON-NLS-1$
		String collaborationText = new String(collaboration.contents, StandardCharsets.UTF_8);
		assertContains(collaborationText, "location=\"" + newOrder + "\"", "the collaboration should import the new order process"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertContains(collaborationText, "location=\"" + newPayment + "\"", //$NON-NLS-1$ //$NON-NLS-2$
				"the collaboration should import the new payment process"); //$NON-NLS-1$
		assertEquals("0", String.valueOf(payment.writes), "the published model is published as it is"); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("1", String.valueOf(collaboration.writes), "the collaboration file is written once"); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("2", String.valueOf(importers.get(1).getImportChanges().size()), "two imports of the collaboration changed"); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals(Arrays.toString(collaboration.contents), Arrays.toString(store.read("ipfs://" + importers.get(1).getNewCid())), //$NON-NLS-1$
				"the published collaboration should be the rewritten working copy"); //$NON-NLS-1$
	}

	/**
	 * Checks that a working copy with the same bytes as the version that an
	 * import pins stands for that version and gets the new locations written.
	 */
	private static void testWorkingCopyOfAPinnedVersionIsRewritten() throws Exception {
		MemoryStore store = new MemoryStore();
		CaseStudy caseStudy = new CaseStudy(store);
		MemoryWorkingCopy payment = new MemoryWorkingCopy("p/payment-process.bpmn", caseStudy.changedPayment()); //$NON-NLS-1$
		MemoryWorkingCopy order = new MemoryWorkingCopy("p/order-process.bpmn", caseStudy.order); //$NON-NLS-1$
		MemoryWorkingCopy collaboration = new MemoryWorkingCopy("p/collaboration.bpmn", caseStudy.collaboration); //$NON-NLS-1$
		IPFSImportCascade cascade = IPFSImportCascade.plan(payment, workingCopies(payment, order, collaboration), store);
		assertEquals("p/order-process.bpmn", cascade.getImporters().get(0).getDisplayName(), //$NON-NLS-1$
				"the working copy of the order process should stand for its pinned version"); //$NON-NLS-1$
		cascade.publish();
		assertEquals("1", String.valueOf(order.writes), "the order working copy should get the new location"); //$NON-NLS-1$ //$NON-NLS-2$
		String newOrder = "ipfs://" + cascade.getImporters().get(0).getNewCid(); //$NON-NLS-1$
		assertContains(new String(collaboration.contents, StandardCharsets.UTF_8), "location=\"" + newOrder + "\"", //$NON-NLS-1$ //$NON-NLS-2$
				"the collaboration should import the new version of the order working copy"); //$NON-NLS-1$
	}

	/**
	 * Checks that a pinned version that differs from the working copy of its
	 * model stops the cascade before anything is published or written.
	 */
	private static void testDifferentWorkingCopyStopsTheCascade() throws Exception {
		MemoryStore store = new MemoryStore();
		CaseStudy caseStudy = new CaseStudy(store);
		MemoryWorkingCopy payment = new MemoryWorkingCopy("p/payment-process.bpmn", caseStudy.changedPayment()); //$NON-NLS-1$
		MemoryWorkingCopy order = new MemoryWorkingCopy("p/order-process.bpmn", //$NON-NLS-1$
				model(ORDER, "OrderEdited", PAYMENT, caseStudy.paymentLocation)); //$NON-NLS-1$
		MemoryWorkingCopy collaboration = new MemoryWorkingCopy("p/collaboration.bpmn", caseStudy.collaboration); //$NON-NLS-1$
		assertCascadeRefused(payment, workingCopies(payment, order, collaboration), store, "holds a different version"); //$NON-NLS-1$
		assertEquals("0", String.valueOf(store.publications + order.writes + collaboration.writes), //$NON-NLS-1$
				"a refused cascade should change nothing"); //$NON-NLS-1$
	}

	/** Checks that two models that import each other by CID stop the cascade. */
	private static void testCycleStopsTheCascade() throws Exception {
		MemoryStore store = new MemoryStore();
		String oldA = store.add(model("urn:a", "A")); //$NON-NLS-1$ //$NON-NLS-2$
		byte[] b = model("urn:b", "B", "urn:a", oldA); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		String oldB = store.add(b);
		MemoryWorkingCopy a = new MemoryWorkingCopy("p/a.bpmn", model("urn:a", "A2", "urn:b", oldB)); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		MemoryWorkingCopy workingCopyOfB = new MemoryWorkingCopy("p/b.bpmn", b); //$NON-NLS-1$
		assertCascadeRefused(a, workingCopies(a, workingCopyOfB), store, "import each other by CID"); //$NON-NLS-1$
	}

	/**
	 * Checks that an import of a namespace that the published model shares with
	 * another working copy stops the cascade, because it could mean either file.
	 */
	private static void testSharedNamespaceStopsTheCascade() throws Exception {
		MemoryStore store = new MemoryStore();
		CaseStudy caseStudy = new CaseStudy(store);
		MemoryWorkingCopy payment = new MemoryWorkingCopy("p/payment-process.bpmn", caseStudy.changedPayment()); //$NON-NLS-1$
		MemoryWorkingCopy copy = new MemoryWorkingCopy("p/payment-process-1.bpmn", caseStudy.payment); //$NON-NLS-1$
		MemoryWorkingCopy collaboration = new MemoryWorkingCopy("p/collaboration.bpmn", caseStudy.collaboration); //$NON-NLS-1$
		assertCascadeRefused(payment, workingCopies(payment, copy, collaboration), store, "several files declare it"); //$NON-NLS-1$
	}

	/** Checks that a shared namespace that no import names does not stop the publication. */
	private static void testSharedNamespaceWithoutImportersIsPublished() throws Exception {
		MemoryStore store = new MemoryStore();
		MemoryWorkingCopy first = new MemoryWorkingCopy("p/first.bpmn", model(PAYMENT, "First")); //$NON-NLS-1$ //$NON-NLS-2$
		MemoryWorkingCopy second = new MemoryWorkingCopy("p/second.bpmn", model(PAYMENT, "Second")); //$NON-NLS-1$ //$NON-NLS-2$
		IPFSImportCascade cascade = IPFSImportCascade.plan(first, workingCopies(first, second), store);
		cascade.publish();
		assertEquals("1", String.valueOf(store.publications), "only the published model should be published"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	/** Checks that a model that nothing imports is published alone. */
	private static void testModelWithoutImportersIsPublishedAlone() throws Exception {
		MemoryStore store = new MemoryStore();
		MemoryWorkingCopy payment = new MemoryWorkingCopy("p/payment-process.bpmn", model(PAYMENT, "Payment")); //$NON-NLS-1$ //$NON-NLS-2$
		IPFSImportCascade cascade = IPFSImportCascade.plan(payment, workingCopies(payment), store);
		cascade.publish();
		assertEquals("0", String.valueOf(cascade.getImporters().size()), "nothing imports the payment process"); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("1", String.valueOf(store.publications), "only the payment process should be published"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	/**
	 * Checks the publication of a whole folder: the imports between its models
	 * name the versions published together, in dependency order, and every
	 * working copy that changes gets the new locations.
	 */
	private static void testProjectNamesTheVersionsPublishedTogether() throws Exception {
		MemoryStore store = new MemoryStore();
		CaseStudy caseStudy = new CaseStudy(store);
		MemoryWorkingCopy payment = new MemoryWorkingCopy("p/payment-process.bpmn", caseStudy.changedPayment()); //$NON-NLS-1$
		MemoryWorkingCopy order = new MemoryWorkingCopy("p/order-process.bpmn", caseStudy.order); //$NON-NLS-1$
		MemoryWorkingCopy collaboration = new MemoryWorkingCopy("p/collaboration.bpmn", caseStudy.collaboration); //$NON-NLS-1$
		IPFSImportCascade publication = IPFSImportCascade.planProject(workingCopies(collaboration, order, payment), store);
		List<IPFSCascadeModel> sequence = publication.getModelsToPublish();
		assertEquals("p/payment-process.bpmn p/order-process.bpmn p/collaboration.bpmn", //$NON-NLS-1$
				sequence.get(0).getDisplayName() + " " + sequence.get(1).getDisplayName() + " " + sequence.get(2).getDisplayName(), //$NON-NLS-1$ //$NON-NLS-2$
				"the models of the folder should be published in dependency order"); //$NON-NLS-1$
		assertEquals("0", String.valueOf(publication.getImporters().size()), "nothing outside the folder is affected"); //$NON-NLS-1$ //$NON-NLS-2$
		publication.publish();
		String newPayment = "ipfs://" + sequence.get(0).getNewCid(); //$NON-NLS-1$
		String newOrder = "ipfs://" + sequence.get(1).getNewCid(); //$NON-NLS-1$
		assertContains(new String(order.contents, StandardCharsets.UTF_8), "location=\"" + newPayment + "\"", //$NON-NLS-1$ //$NON-NLS-2$
				"the order working copy should import the payment process published with it"); //$NON-NLS-1$
		String collaborationText = new String(collaboration.contents, StandardCharsets.UTF_8);
		assertContains(collaborationText, "location=\"" + newOrder + "\"", "the collaboration should import the new order process"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertContains(collaborationText, "location=\"" + newPayment + "\"", "the collaboration should import the new payment process"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertEquals("0", String.valueOf(payment.writes), "the payment process imports nothing and stays as it is"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	/**
	 * Checks that a folder publication gives a new version to a version on
	 * IPFS that the folder pins and that imports a model of the folder.
	 */
	private static void testProjectGivesPinnedVersionsNewVersions() throws Exception {
		MemoryStore store = new MemoryStore();
		CaseStudy caseStudy = new CaseStudy(store);
		MemoryWorkingCopy payment = new MemoryWorkingCopy("p/payment-process.bpmn", caseStudy.changedPayment()); //$NON-NLS-1$
		MemoryWorkingCopy collaboration = new MemoryWorkingCopy("p/collaboration.bpmn", caseStudy.collaboration); //$NON-NLS-1$
		IPFSImportCascade publication = IPFSImportCascade.planProject(workingCopies(collaboration, payment), store);
		assertEquals("1", String.valueOf(publication.getImporters().size()), "the order process on IPFS is affected"); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals(caseStudy.orderLocation, publication.getImporters().get(0).getPinnedLocation(),
				"the affected version should be the pinned order process"); //$NON-NLS-1$
		publication.publish();
		String newOrder = "ipfs://" + publication.getImporters().get(0).getNewCid(); //$NON-NLS-1$
		assertContains(new String(collaboration.contents, StandardCharsets.UTF_8), "location=\"" + newOrder + "\"", //$NON-NLS-1$ //$NON-NLS-2$
				"the collaboration should import the new version of the order process"); //$NON-NLS-1$
	}

	/** Checks that two models of a folder that import each other by CID stop the publication. */
	private static void testProjectRefusesACycle() throws Exception {
		MemoryStore store = new MemoryStore();
		String oldA = store.add(model("urn:a", "A")); //$NON-NLS-1$ //$NON-NLS-2$
		String oldB = store.add(model("urn:b", "B")); //$NON-NLS-1$ //$NON-NLS-2$
		MemoryWorkingCopy a = new MemoryWorkingCopy("p/a.bpmn", model("urn:a", "A", "urn:b", oldB)); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		MemoryWorkingCopy b = new MemoryWorkingCopy("p/b.bpmn", model("urn:b", "B", "urn:a", oldA)); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		try {
			IPFSImportCascade.planProject(workingCopies(a, b), store);
		} catch (IPFSCascadeException e) {
			assertContains(e.getMessage(), "import each other by CID", "the refusal should name the cycle"); //$NON-NLS-1$ //$NON-NLS-2$
			return;
		}
		throw new AssertionError("Expected the folder publication to be refused for a cycle."); //$NON-NLS-1$
	}

	/**
	 * Runs the case-study cascade on the Kubo node of the default API address
	 * and reads the new versions back from the node.
	 */
	private static void testCascadeOnKubo() throws Exception {
		KuboModelStore store = new KuboModelStore(null);
		String payment = "ipfs://" + store.publish("payment-process.bpmn", model(PAYMENT, "Payment")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		String order = "ipfs://" + store.publish("order-process.bpmn", model(ORDER, "Order", PAYMENT, payment)); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		byte[] collaboration = model(COLLABORATION, "Collaboration", ORDER, order, PAYMENT, payment); //$NON-NLS-1$
		Path folder = Files.createTempDirectory("ipfs-cascade-"); //$NON-NLS-1$
		try {
			FileWorkingCopy changedPayment = new FileWorkingCopy(folder.resolve("payment-process.bpmn"), //$NON-NLS-1$
					model(PAYMENT, "Payment, second version")); //$NON-NLS-1$
			FileWorkingCopy collaborationFile = new FileWorkingCopy(folder.resolve("collaboration.bpmn"), collaboration); //$NON-NLS-1$
			List<IPFSWorkingCopy> files = new ArrayList<IPFSWorkingCopy>();
			files.add(collaborationFile);
			files.add(changedPayment);
			IPFSImportCascade cascade = IPFSImportCascade.plan(changedPayment, files, store);
			cascade.publish();
			String newPayment = "ipfs://" + cascade.getPublishedModel().getNewCid(); //$NON-NLS-1$
			String newOrder = "ipfs://" + cascade.getImporters().get(0).getNewCid(); //$NON-NLS-1$
			String newCollaboration = "ipfs://" + cascade.getImporters().get(1).getNewCid(); //$NON-NLS-1$
			assertContains(new String(store.read(newOrder), StandardCharsets.UTF_8), "location=\"" + newPayment + "\"", //$NON-NLS-1$ //$NON-NLS-2$
					"the node should hold the new order process with the new payment CID"); //$NON-NLS-1$
			String storedCollaboration = new String(store.read(newCollaboration), StandardCharsets.UTF_8);
			assertContains(storedCollaboration, "location=\"" + newOrder + "\"", "the stored collaboration should import the new order process"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			assertContains(storedCollaboration, "location=\"" + newPayment + "\"", //$NON-NLS-1$ //$NON-NLS-2$
					"the stored collaboration should import the new payment process"); //$NON-NLS-1$
			assertEquals(storedCollaboration, new String(Files.readAllBytes(folder.resolve("collaboration.bpmn")), //$NON-NLS-1$
					StandardCharsets.UTF_8), "the working copy should equal the stored collaboration"); //$NON-NLS-1$
		} finally {
			deleteFolder(folder);
		}
	}

	/** Checks that planning a cascade fails with a message that contains the expected words. */
	private static void assertCascadeRefused(IPFSWorkingCopy published, List<IPFSWorkingCopy> files, IPFSModelStore store,
			String expectedWords) throws IOException {
		try {
			IPFSImportCascade.plan(published, files, store);
		} catch (IPFSCascadeException e) {
			assertContains(e.getMessage(), expectedWords, "the refusal should say why"); //$NON-NLS-1$
			return;
		}
		throw new AssertionError("Expected the cascade to be refused: " + expectedWords); //$NON-NLS-1$
	}

	/**
	 * Builds a BPMN file with a comment that holds a character outside ASCII
	 * and a commented-out import, followed by the given imports as namespace
	 * and location pairs.
	 */
	private static byte[] model(String namespace, String name, String... namespacesAndLocations) {
		StringBuilder xml = new StringBuilder();
		xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"); //$NON-NLS-1$
		xml.append("<!-- Prüfung. A commented-out import must stay as it is:\n"); //$NON-NLS-1$
		xml.append("     <bpmn2:import importType=\"").append(BPMN) //$NON-NLS-1$
				.append("\" location=\"ipfs://commented\" namespace=\"").append(namespace).append("\"/> -->\n"); //$NON-NLS-1$ //$NON-NLS-2$
		xml.append("<bpmn2:definitions xmlns:bpmn2=\"").append(BPMN).append("\" id=\"Definitions_1\"\n"); //$NON-NLS-1$ //$NON-NLS-2$
		xml.append("    targetNamespace=\"").append(namespace).append("\">\n"); //$NON-NLS-1$ //$NON-NLS-2$
		for (int index = 0; index + 1 < namespacesAndLocations.length; index += 2) {
			xml.append("  <bpmn2:import importType=\"").append(BPMN).append("\"\n      location=\"") //$NON-NLS-1$ //$NON-NLS-2$
					.append(namespacesAndLocations[index + 1]).append("\" namespace=\"") //$NON-NLS-1$
					.append(namespacesAndLocations[index]).append("\"/>\n"); //$NON-NLS-1$
		}
		xml.append("  <bpmn2:process id=\"Process_1\" name=\"").append(name).append("\"/>\n"); //$NON-NLS-1$ //$NON-NLS-2$
		xml.append("</bpmn2:definitions>\n"); //$NON-NLS-1$
		return xml.toString().getBytes(StandardCharsets.UTF_8);
	}

	/** Lists working copies in the order given. */
	private static List<IPFSWorkingCopy> workingCopies(IPFSWorkingCopy... files) {
		return new ArrayList<IPFSWorkingCopy>(Arrays.asList(files));
	}

	/** Deletes a temporary folder with the files the integration check wrote. */
	private static void deleteFolder(Path folder) throws IOException {
		for (Path file : Files.newDirectoryStream(folder)) {
			Files.delete(file);
		}
		Files.delete(folder);
	}

	/** Fails when a text does not contain the expected part. */
	private static void assertContains(String text, String expected, String message) {
		if (!text.contains(expected)) {
			throw new AssertionError(message + " Missing: " + expected + "\nin:\n" + text); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}

	/** Fails when two values differ; null equals null. */
	private static void assertEquals(String expected, String actual, String message) {
		boolean same = expected == null ? actual == null : expected.equals(actual);
		if (!same) {
			throw new AssertionError(message + " Expected '" + expected + "' but was '" + actual + "'."); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		}
	}

	/**
	 * The three published case-study models in a store: the payment process,
	 * the order process that imports it and the collaboration that imports
	 * both, all by CID.
	 */
	private static final class CaseStudy {

		private final byte[] payment;
		private final byte[] order;
		private final byte[] collaboration;
		private final String paymentLocation;
		private final String orderLocation;

		/**
		 * Publishes the three models in dependency order.
		 *
		 * @param store the store that holds the published versions
		 */
		private CaseStudy(MemoryStore store) {
			payment = model(PAYMENT, "Payment"); //$NON-NLS-1$
			paymentLocation = store.add(payment);
			order = model(ORDER, "Order", PAYMENT, paymentLocation); //$NON-NLS-1$
			orderLocation = store.add(order);
			collaboration = model(COLLABORATION, "Collaboration", ORDER, orderLocation, PAYMENT, paymentLocation); //$NON-NLS-1$
			store.add(collaboration);
		}

		/** Returns the payment process after a local edit. */
		private byte[] changedPayment() {
			return model(PAYMENT, "Payment, second version"); //$NON-NLS-1$
		}
	}

	/** A store in memory whose CIDs are derived from a SHA-256 digest of the bytes. */
	private static final class MemoryStore implements IPFSModelStore {

		private final Map<String, byte[]> contentByLocation = new LinkedHashMap<String, byte[]>();
		private int publications;

		/** Returns the bytes behind a location, or fails as a node would. */
		@Override
		public byte[] read(String location) throws IOException {
			byte[] contents = contentByLocation.get(location);
			if (contents == null) {
				throw new IOException("No content for " + location); //$NON-NLS-1$
			}
			return contents;
		}

		/** Stores a new version and counts the publication. */
		@Override
		public String publish(String fileName, byte[] contents) {
			publications++;
			String cid = cidOf(contents);
			contentByLocation.put("ipfs://" + cid, contents); //$NON-NLS-1$
			return cid;
		}

		/** Stores a version that exists before the test starts and returns its location. */
		private String add(byte[] contents) {
			String location = "ipfs://" + cidOf(contents); //$NON-NLS-1$
			contentByLocation.put(location, contents);
			return location;
		}

		/** Derives a stable stand-in for a CID from the bytes. */
		private static String cidOf(byte[] contents) {
			try {
				byte[] digest = MessageDigest.getInstance("SHA-256").digest(contents); //$NON-NLS-1$
				StringBuilder hex = new StringBuilder("bafk"); //$NON-NLS-1$
				for (int index = 0; index < 20; index++) {
					hex.append(String.format("%02x", Integer.valueOf(digest[index] & 0xff))); //$NON-NLS-1$
				}
				return hex.toString();
			} catch (NoSuchAlgorithmException e) {
				throw new IllegalStateException(e);
			}
		}
	}

	/** A working copy in memory that counts its writes. */
	private static final class MemoryWorkingCopy implements IPFSWorkingCopy {

		private final String path;
		private byte[] contents;
		private int writes;

		/**
		 * Creates the working copy.
		 *
		 * @param path     the path for messages
		 * @param contents the saved bytes
		 */
		private MemoryWorkingCopy(String path, byte[] contents) {
			this.path = path;
			this.contents = contents;
		}

		/** Returns the path given at creation. */
		@Override
		public String getPath() {
			return path;
		}

		/** Returns the last segment of the path. */
		@Override
		public String getFileName() {
			return path.substring(path.lastIndexOf('/') + 1);
		}

		/** Returns the saved bytes. */
		@Override
		public byte[] readContents() {
			return contents;
		}

		/** Replaces the bytes and counts the write. */
		@Override
		public void writeContents(byte[] newContents) {
			contents = newContents;
			writes++;
		}
	}

	/** A working copy in a temporary file, for the check on Kubo. */
	private static final class FileWorkingCopy implements IPFSWorkingCopy {

		private final Path file;

		/**
		 * Writes the file.
		 *
		 * @param file     the path of the file
		 * @param contents the bytes to write
		 * @throws IOException when the file cannot be written
		 */
		private FileWorkingCopy(Path file, byte[] contents) throws IOException {
			this.file = file;
			Files.write(file, contents);
		}

		/** Returns the absolute path. */
		@Override
		public String getPath() {
			return file.toString();
		}

		/** Returns the file name. */
		@Override
		public String getFileName() {
			return file.getFileName().toString();
		}

		/** Reads the file. */
		@Override
		public byte[] readContents() throws IOException {
			return Files.readAllBytes(file);
		}

		/** Writes the file. */
		@Override
		public void writeContents(byte[] contents) throws IOException {
			Files.write(file, contents);
		}
	}
}
// EcoreFS end: standalone self-test of the namespace folders, the import rewrite and the cascade
