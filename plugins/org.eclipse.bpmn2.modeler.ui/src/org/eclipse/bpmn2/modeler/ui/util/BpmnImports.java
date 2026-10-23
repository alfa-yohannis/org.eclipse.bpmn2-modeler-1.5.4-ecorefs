package org.eclipse.bpmn2.modeler.ui.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.bpmn2.modeler.ui.util.XmlStartTagScanner.StartTag;

// EcoreFS begin: the target namespace and the BPMN imports of a model file, and a rewrite of import locations
/**
 * The target namespace of a BPMN 2.0 model file and the BPMN imports it
 * declares, read straight from the bytes of the file.
 *
 * <p>
 * An import binds a namespace to a location. When the location is an
 * {@code ipfs://} URI, the model pins one published version of the imported
 * model. {@link #withLocations(Map)} writes new locations into such imports and
 * keeps every other byte of the file, so a new version of a model differs from
 * the old one only in those locations, and its comments and layout stay.
 * </p>
 *
 * <p>
 * Only BPMN imports count: {@code import} children of the {@code definitions}
 * root whose {@code importType} is the BPMN model namespace. Imports of XML
 * schemas or WSDL files, imports without a location and anything inside a
 * comment are left out. The file is decoded strictly in the encoding that its
 * XML declaration names, UTF-8 by default, so that encoding it again gives
 * back the same bytes.
 * </p>
 */
public final class BpmnImports {

	/** The namespace of BPMN 2.0 model elements, which is also the import type of BPMN imports. */
	static final String BPMN_MODEL_NAMESPACE = "http://www.omg.org/spec/BPMN/20100524/MODEL"; //$NON-NLS-1$

	private static final Pattern ENCODING_DECLARATION = Pattern
			.compile("^\\s*<\\?xml[^>]*?encoding\\s*=\\s*[\"']([A-Za-z0-9._:-]+)[\"']"); //$NON-NLS-1$
	private static final int DECLARATION_LENGTH_LIMIT = 256;
	private static final byte[] UTF8_BYTE_ORDER_MARK = { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };

	private final String text;
	private final Charset charset;
	private final String targetNamespace;
	private final List<BpmnImport> imports;

	/**
	 * Creates the result of a read; {@link #read(byte[])} is the entry point.
	 */
	private BpmnImports(String text, Charset charset, String targetNamespace, List<BpmnImport> imports) {
		this.text = text;
		this.charset = charset;
		this.targetNamespace = targetNamespace;
		this.imports = Collections.unmodifiableList(imports);
	}

	/**
	 * Reads the target namespace and the BPMN imports of a model file. A file
	 * whose root is not a BPMN {@code definitions} element has neither.
	 *
	 * @param contents the bytes of the file
	 * @return the namespace and imports, never null
	 * @throws IOException when the bytes do not decode in the declared encoding
	 *                     or the markup is not well formed
	 */
	public static BpmnImports read(byte[] contents) throws IOException {
		Charset charset = detectCharset(contents);
		String text = decode(contents, charset);
		List<StartTag> tags = XmlStartTagScanner.scan(text);
		StartTag root = tags.isEmpty() ? null : tags.get(0);
		if (root == null || !isBpmnDefinitions(root)) {
			return new BpmnImports(text, charset, null, new ArrayList<BpmnImport>());
		}
		return new BpmnImports(text, charset, attributeValue(root, "targetNamespace"), collectImports(tags, root)); //$NON-NLS-1$
	}

	/** Returns the target namespace of the model, or null when it declares none. */
	public String getTargetNamespace() {
		return targetNamespace;
	}

	/** Returns the BPMN imports of the model in document order. */
	public List<BpmnImport> getImports() {
		return imports;
	}

	/** Returns the BPMN imports whose location names a published version by CID. */
	public List<BpmnImport> getIpfsImports() {
		List<BpmnImport> ipfsImports = new ArrayList<BpmnImport>();
		for (BpmnImport declaration : imports) {
			if (declaration.isIpfsLocation()) {
				ipfsImports.add(declaration);
			}
		}
		return ipfsImports;
	}

	/**
	 * Returns the bytes of the file with new locations in its {@code ipfs://}
	 * imports. Every other byte stays as it was.
	 *
	 * @param newLocationByOldLocation the new location for each old location to replace
	 * @return the bytes of the new version of the file
	 * @throws IOException when a new location cannot be written in the encoding of the file
	 */
	public byte[] withLocations(Map<String, String> newLocationByOldLocation) throws IOException {
		StringBuilder rewritten = new StringBuilder(text.length() + 64);
		int copied = 0;
		for (BpmnImport declaration : imports) {
			String newLocation = newLocationByOldLocation.get(declaration.getLocation());
			if (newLocation != null && declaration.isIpfsLocation()) {
				rewritten.append(text, copied, declaration.getLocationStart());
				rewritten.append(XmlAttributeValues.escape(newLocation, declaration.getQuote()));
				copied = declaration.getLocationEnd();
			}
		}
		rewritten.append(text, copied, text.length());
		return encode(rewritten.toString());
	}

	/**
	 * Collects the BPMN imports among the children of the root. The namespace
	 * of an element prefix is looked up on the element itself and then on the
	 * root, which is where BPMN files declare their namespaces.
	 */
	private static List<BpmnImport> collectImports(List<StartTag> tags, StartTag root) {
		List<BpmnImport> imports = new ArrayList<BpmnImport>();
		for (StartTag tag : tags) {
			if (isBpmnImport(tag, root)) {
				StartTag.Attribute location = tag.getAttribute("location"); //$NON-NLS-1$
				imports.add(new BpmnImport(attributeValue(tag, "namespace"), location.getValue().trim(), //$NON-NLS-1$
						location.getValueStart(), location.getValueEnd(), location.getQuote()));
			}
		}
		return imports;
	}

	/**
	 * Tells whether a child of the root is a BPMN import with a location: a
	 * BPMN {@code import} element whose import type is the BPMN model namespace.
	 */
	private static boolean isBpmnImport(StartTag tag, StartTag root) {
		return tag.getDepth() == XmlStartTagScanner.CHILD_DEPTH && "import".equals(tag.getLocalName()) //$NON-NLS-1$
				&& BPMN_MODEL_NAMESPACE.equals(namespaceOf(tag.getPrefix(), tag, root))
				&& BPMN_MODEL_NAMESPACE.equals(attributeValue(tag, "importType")) //$NON-NLS-1$
				&& tag.getAttribute("location") != null; //$NON-NLS-1$
	}

	/** Tells whether the root element is a BPMN {@code definitions} element. */
	private static boolean isBpmnDefinitions(StartTag root) {
		return "definitions".equals(root.getLocalName()) //$NON-NLS-1$
				&& BPMN_MODEL_NAMESPACE.equals(namespaceOf(root.getPrefix(), root, root));
	}

	/**
	 * Returns the namespace bound to an element prefix, or to the default
	 * namespace when the prefix is null, as declared on the element or on the
	 * root.
	 */
	private static String namespaceOf(String prefix, StartTag element, StartTag root) {
		String declaration = prefix == null ? "xmlns" : "xmlns:" + prefix; //$NON-NLS-1$ //$NON-NLS-2$
		StartTag.Attribute own = element.getAttribute(declaration);
		if (own != null) {
			return own.getValue();
		}
		StartTag.Attribute inherited = root.getAttribute(declaration);
		return inherited == null ? null : inherited.getValue();
	}

	/** Returns the trimmed value of an attribute, or null when the attribute is missing or blank. */
	private static String attributeValue(StartTag tag, String name) {
		StartTag.Attribute attribute = tag.getAttribute(name);
		if (attribute == null || attribute.getValue().trim().isEmpty()) {
			return null;
		}
		return attribute.getValue().trim();
	}

	/**
	 * Returns the encoding that the XML declaration names, or UTF-8. Files in
	 * UTF-16 are refused, because their byte order mark would not survive a
	 * decode and encode unchanged.
	 */
	private static Charset detectCharset(byte[] contents) throws IOException {
		if (contents.length >= 2 && ((contents[0] == (byte) 0xFE && contents[1] == (byte) 0xFF)
				|| (contents[0] == (byte) 0xFF && contents[1] == (byte) 0xFE))) {
			throw new IOException("Model files in UTF-16 are not supported."); //$NON-NLS-1$
		}
		int offset = startsWithUtf8ByteOrderMark(contents) ? UTF8_BYTE_ORDER_MARK.length : 0;
		int length = Math.min(contents.length - offset, DECLARATION_LENGTH_LIMIT);
		String head = new String(contents, offset, Math.max(length, 0), StandardCharsets.ISO_8859_1);
		Matcher declaration = ENCODING_DECLARATION.matcher(head);
		if (!declaration.find()) {
			return StandardCharsets.UTF_8;
		}
		try {
			return Charset.forName(declaration.group(1));
		} catch (IllegalArgumentException e) {
			throw new IOException("The model file declares an unsupported encoding: " + declaration.group(1), e); //$NON-NLS-1$
		}
	}

	/** Tells whether the bytes start with the UTF-8 byte order mark. */
	private static boolean startsWithUtf8ByteOrderMark(byte[] contents) {
		return contents.length >= UTF8_BYTE_ORDER_MARK.length && contents[0] == UTF8_BYTE_ORDER_MARK[0]
				&& contents[1] == UTF8_BYTE_ORDER_MARK[1] && contents[2] == UTF8_BYTE_ORDER_MARK[2];
	}

	/** Decodes the bytes and fails on any byte sequence that the encoding does not define. */
	private static String decode(byte[] contents, Charset charset) throws IOException {
		return charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(contents)).toString();
	}

	/** Encodes the text in the encoding of the file and fails on characters that it cannot hold. */
	private byte[] encode(String rewritten) throws IOException {
		ByteBuffer buffer = charset.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(rewritten));
		byte[] bytes = new byte[buffer.remaining()];
		buffer.get(bytes);
		return bytes;
	}
}
// EcoreFS end: the target namespace and the BPMN imports of a model file, and a rewrite of import locations
