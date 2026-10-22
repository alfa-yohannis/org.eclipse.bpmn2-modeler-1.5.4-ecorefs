package org.eclipse.bpmn2.modeler.ui.util;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

// EcoreFS begin: names that describe a BPMN model, for readable file names
/**
 * The names that describe a BPMN 2.0 model: the name of its definitions, of its
 * first named collaboration and of its first named process, and the id of its
 * first process.
 *
 * <p>
 * {@link IPFSModelFileName} uses them to name models downloaded from IPFS. The
 * model is read with a streaming parser that never loads a DTD or an external
 * entity, because the bytes come from the network.
 * </p>
 */
final class BpmnModelNames {

	private static final String BPMN_MODEL_NAMESPACE = "http://www.omg.org/spec/BPMN/20100524/MODEL"; //$NON-NLS-1$
	private static final String DEFINITIONS = "definitions"; //$NON-NLS-1$
	private static final String COLLABORATION = "collaboration"; //$NON-NLS-1$
	private static final String PROCESS = "process"; //$NON-NLS-1$
	/** Depth of the definitions root element. */
	private static final int ROOT_DEPTH = 1;
	/** Depth of processes and collaborations, the direct children of the definitions. */
	private static final int ROOT_ELEMENT_DEPTH = 2;
	/** Editor placeholders and generated ids, such as "Default Process", "Process_1" or "sid-6f1c2a9e-...". */
	private static final Pattern PLACEHOLDER = Pattern.compile(
			"(?i)default\\s.*|(process|collaboration|choreography|definitions)[ _-]?\\w*\\d\\w*|.*[0-9a-f]{8}-[0-9a-f]{4}-.*"); //$NON-NLS-1$

	private String definitionsName;
	private String collaborationName;
	private String processName;
	private String processId;

	private BpmnModelNames() {
	}

	/**
	 * Reads the names of a BPMN model. Content that is not well-formed XML keeps
	 * the names read before the error, and content that is not BPMN has none.
	 *
	 * @param contents the bytes of a BPMN 2.0 XML document, may be null
	 * @return the names found, never null
	 */
	static BpmnModelNames read(byte[] contents) {
		BpmnModelNames names = new BpmnModelNames();
		if (contents == null || contents.length == 0) {
			return names;
		}
		try {
			XMLStreamReader reader = createReader(contents);
			try {
				names.collectFrom(reader);
			} finally {
				reader.close();
			}
		} catch (XMLStreamException e) {
			// Not well-formed XML: keep the names read so far
		}
		return names;
	}

	/**
	 * Returns the name that best describes the model: the first of the
	 * definitions name, collaboration name, process name and process id that
	 * is not an editor placeholder, else the first placeholder, because even
	 * "Default Process" reads better than a CID.
	 *
	 * @return the chosen name, or null when the model has none
	 */
	String mostDescriptive() {
		List<String> candidates = presentNames();
		for (String candidate : candidates) {
			if (!PLACEHOLDER.matcher(candidate).matches()) {
				return candidate;
			}
		}
		return candidates.isEmpty() ? null : candidates.get(0);
	}

	/**
	 * Creates a reader for the JDK's own StAX implementation, whatever provider
	 * another bundle may expose, with DTDs and external entities switched off.
	 */
	private static XMLStreamReader createReader(byte[] contents) throws XMLStreamException {
		XMLInputFactory factory = XMLInputFactory.newDefaultFactory();
		factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
		factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
		return factory.createXMLStreamReader(new ByteArrayInputStream(contents));
	}

	/**
	 * Walks the whole document and records the names of the definitions root
	 * and of its process and collaboration children.
	 */
	private void collectFrom(XMLStreamReader reader) throws XMLStreamException {
		int depth = 0;
		while (reader.hasNext()) {
			int event = reader.next();
			if (event == XMLStreamConstants.START_ELEMENT) {
				depth++;
				recordElement(reader, depth);
			} else if (event == XMLStreamConstants.END_ELEMENT) {
				depth--;
			}
		}
	}

	/**
	 * Records the name, and for a process the id, of the element the reader is
	 * on, when that element is the BPMN definitions root or one of its process
	 * or collaboration children. The first named collaboration or process wins.
	 */
	private void recordElement(XMLStreamReader reader, int depth) {
		if (!BPMN_MODEL_NAMESPACE.equals(reader.getNamespaceURI())) {
			return;
		}
		String element = reader.getLocalName();
		if (depth == ROOT_DEPTH && DEFINITIONS.equals(element)) {
			definitionsName = attribute(reader, "name"); //$NON-NLS-1$
		} else if (depth == ROOT_ELEMENT_DEPTH && COLLABORATION.equals(element)) {
			collaborationName = firstPresent(collaborationName, attribute(reader, "name")); //$NON-NLS-1$
		} else if (depth == ROOT_ELEMENT_DEPTH && PROCESS.equals(element)) {
			processName = firstPresent(processName, attribute(reader, "name")); //$NON-NLS-1$
			processId = firstPresent(processId, attribute(reader, "id")); //$NON-NLS-1$
		}
	}

	/** Lists the names that were found, in order of preference. */
	private List<String> presentNames() {
		List<String> names = new ArrayList<String>();
		for (String name : new String[] { definitionsName, collaborationName, processName, processId }) {
			if (name != null) {
				names.add(name);
			}
		}
		return names;
	}

	/** Returns the trimmed value of an attribute without namespace, or null when it is missing or blank. */
	private static String attribute(XMLStreamReader reader, String name) {
		String value = reader.getAttributeValue(null, name);
		return value == null || value.trim().isEmpty() ? null : value.trim();
	}

	/** Keeps a value that was already recorded, so the first named element wins. */
	private static String firstPresent(String recorded, String candidate) {
		return recorded != null ? recorded : candidate;
	}
}
// EcoreFS end: names that describe a BPMN model, for readable file names
