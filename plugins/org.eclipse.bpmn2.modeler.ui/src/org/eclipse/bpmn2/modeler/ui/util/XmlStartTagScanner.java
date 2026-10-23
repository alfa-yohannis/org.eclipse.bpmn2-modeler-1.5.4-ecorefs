package org.eclipse.bpmn2.modeler.ui.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// EcoreFS begin: finds the root element and its children in XML text, with the position of every attribute value
/**
 * Finds the start tags of the root element of an XML document and of the
 * root's children, together with the position of every attribute value in the
 * text.
 *
 * <p>
 * {@link BpmnImports} needs those positions to change the location of a BPMN
 * import and keep every other character of the file. A parser that builds a
 * model would write the whole file anew and lose its comments and layout. The
 * scanner skips comments, CDATA sections, processing instructions and the
 * document type declaration, and stops at the end tag of the root element.
 * </p>
 */
final class XmlStartTagScanner {

	/** Depth of the root element. */
	static final int ROOT_DEPTH = 1;
	/** Depth of the children of the root element. */
	static final int CHILD_DEPTH = 2;

	private final String text;
	private final List<StartTag> startTags = new ArrayList<StartTag>();
	private int position;
	private int depth;

	/**
	 * Creates a scanner for one document; {@link #scan(String)} is the entry
	 * point.
	 */
	private XmlStartTagScanner(String text) {
		this.text = text;
	}

	/**
	 * Scans a document and returns the start tags of its root element and of
	 * the root's children, in document order.
	 *
	 * @param text the whole document
	 * @return the start tags at {@link #ROOT_DEPTH} and {@link #CHILD_DEPTH}
	 * @throws IOException when the markup is not well formed
	 */
	static List<StartTag> scan(String text) throws IOException {
		XmlStartTagScanner scanner = new XmlStartTagScanner(text);
		scanner.scanDocument();
		return Collections.unmodifiableList(scanner.startTags);
	}

	/**
	 * Reads one piece of markup after the other until the root element ends or
	 * the text runs out.
	 */
	private void scanDocument() throws IOException {
		boolean rootClosed = false;
		while (!rootClosed) {
			int markup = text.indexOf('<', position);
			if (markup < 0) {
				return;
			}
			rootClosed = scanMarkup(markup);
		}
	}

	/**
	 * Reads the piece of markup that starts at the given offset and moves past
	 * it. Character data between two pieces of markup cannot hold a '<', so the
	 * next '<' always starts markup.
	 *
	 * @return true when the markup closed the root element
	 */
	private boolean scanMarkup(int markup) throws IOException {
		if (text.startsWith("<!--", markup)) { //$NON-NLS-1$
			position = endOf("<!--", "-->", markup); //$NON-NLS-1$ //$NON-NLS-2$
			return false;
		}
		if (text.startsWith("<![CDATA[", markup)) { //$NON-NLS-1$
			position = endOf("<![CDATA[", "]]>", markup); //$NON-NLS-1$ //$NON-NLS-2$
			return false;
		}
		if (text.startsWith("<?", markup)) { //$NON-NLS-1$
			position = endOf("<?", "?>", markup); //$NON-NLS-1$ //$NON-NLS-2$
			return false;
		}
		if (text.startsWith("<!", markup)) { //$NON-NLS-1$
			position = endOfDeclaration(markup);
			return false;
		}
		if (text.startsWith("</", markup)) { //$NON-NLS-1$
			position = endOf("</", ">", markup); //$NON-NLS-1$ //$NON-NLS-2$
			depth--;
			return depth <= 0;
		}
		return scanStartTag(markup);
	}

	/**
	 * Reads a start tag, records it when it belongs to the root element or to
	 * one of its children, and tracks the depth of the element.
	 *
	 * @return true when the tag was an empty root element, which ends the document
	 */
	private boolean scanStartTag(int markup) throws IOException {
		StartTagReader reader = new StartTagReader(text, markup, depth + 1);
		StartTag tag = reader.read();
		position = reader.getEnd();
		if (tag.getDepth() <= CHILD_DEPTH) {
			startTags.add(tag);
		}
		if (reader.isEmptyElement()) {
			return depth == 0;
		}
		depth++;
		return false;
	}

	/**
	 * Returns the offset just after the terminator of a comment, CDATA
	 * section, processing instruction or end tag.
	 */
	private int endOf(String opener, String terminator, int markup) throws IOException {
		int end = text.indexOf(terminator, markup + opener.length());
		if (end < 0) {
			throw notWellFormed(markup);
		}
		return end + terminator.length();
	}

	/**
	 * Returns the offset just after a declaration such as the document type
	 * declaration, whose internal subset in square brackets may hold further
	 * '>' characters, as may its quoted literals.
	 */
	private int endOfDeclaration(int markup) throws IOException {
		int bracketDepth = 0;
		int index = markup + 2;
		while (index < text.length()) {
			char current = text.charAt(index);
			if (current == '"' || current == '\'') {
				index = endOfLiteral(index);
				continue;
			}
			if (current == '[') {
				bracketDepth++;
			} else if (current == ']') {
				bracketDepth--;
			} else if (current == '>' && bracketDepth <= 0) {
				return index + 1;
			}
			index++;
		}
		throw notWellFormed(markup);
	}

	/** Returns the offset just after the quoted literal that starts at the given offset. */
	private int endOfLiteral(int quoteOffset) throws IOException {
		int closing = text.indexOf(text.charAt(quoteOffset), quoteOffset + 1);
		if (closing < 0) {
			throw notWellFormed(quoteOffset);
		}
		return closing + 1;
	}

	/** Creates the exception for markup that is not well formed near the given offset. */
	static IOException notWellFormed(int offset) {
		return new IOException("The XML is not well formed near offset " + offset + "."); //$NON-NLS-1$ //$NON-NLS-2$
	}

	/**
	 * Reads one start tag: the element name, then each attribute with the
	 * position of its value, up to the closing '>' or '/>'. A quoted value may
	 * hold a '>' and is skipped as a whole.
	 */
	private static final class StartTagReader {

		private final String text;
		private final int markup;
		private final int depth;
		private int index;
		private boolean emptyElement;

		/**
		 * Prepares to read the start tag at the given offset.
		 *
		 * @param text   the document
		 * @param markup the offset of the '<' that opens the tag
		 * @param depth  the depth the element will have
		 */
		private StartTagReader(String text, int markup, int depth) {
			this.text = text;
			this.markup = markup;
			this.depth = depth;
			this.index = markup + 1;
		}

		/** Reads the tag and leaves the reader just after it. */
		private StartTag read() throws IOException {
			String name = readName();
			if (name.isEmpty()) {
				throw notWellFormed(markup);
			}
			List<StartTag.Attribute> attributes = new ArrayList<StartTag.Attribute>();
			while (!atTagEnd()) {
				attributes.add(readAttribute());
			}
			return new StartTag(name, depth, attributes);
		}

		/** Returns the offset just after the tag. */
		private int getEnd() {
			return index;
		}

		/** Tells whether the tag closed itself with '/>'. */
		private boolean isEmptyElement() {
			return emptyElement;
		}

		/**
		 * Skips white space and consumes the end of the tag when it comes next.
		 *
		 * @return true when the tag ended
		 */
		private boolean atTagEnd() throws IOException {
			skipWhiteSpace();
			if (index >= text.length()) {
				throw notWellFormed(markup);
			}
			if (text.charAt(index) == '>') {
				index++;
				return true;
			}
			if (text.startsWith("/>", index)) { //$NON-NLS-1$
				index += 2;
				emptyElement = true;
				return true;
			}
			return false;
		}

		/** Reads one attribute: its name, the equals sign and the quoted value. */
		private StartTag.Attribute readAttribute() throws IOException {
			String name = readName();
			skipWhiteSpace();
			if (name.isEmpty() || index >= text.length() || text.charAt(index) != '=') {
				throw notWellFormed(markup);
			}
			index++;
			skipWhiteSpace();
			if (index >= text.length()) {
				throw notWellFormed(markup);
			}
			char quote = text.charAt(index);
			if (quote != '"' && quote != '\'') {
				throw notWellFormed(markup);
			}
			int valueStart = index + 1;
			int valueEnd = text.indexOf(quote, valueStart);
			if (valueEnd < 0) {
				throw notWellFormed(markup);
			}
			index = valueEnd + 1;
			return new StartTag.Attribute(name, text.substring(valueStart, valueEnd), valueStart, valueEnd, quote);
		}

		/** Reads an element or attribute name, which ends at white space, '=', '/' or '>'. */
		private String readName() {
			int start = index;
			while (index < text.length() && !endsName(text.charAt(index))) {
				index++;
			}
			return text.substring(start, index);
		}

		/** Tells whether a character ends a name. */
		private static boolean endsName(char character) {
			return isWhiteSpace(character) || character == '=' || character == '/' || character == '>';
		}

		/** Moves past white space. */
		private void skipWhiteSpace() {
			while (index < text.length() && isWhiteSpace(text.charAt(index))) {
				index++;
			}
		}

		/** Tells whether a character is XML white space. */
		private static boolean isWhiteSpace(char character) {
			return character == ' ' || character == '\t' || character == '\r' || character == '\n';
		}
	}

	/**
	 * A start tag found by the scanner: the qualified element name, the depth
	 * of the element and its attributes.
	 */
	static final class StartTag {

		private final String name;
		private final int depth;
		private final List<Attribute> attributes;

		/**
		 * Creates a start tag.
		 *
		 * @param name       the qualified name, such as {@code bpmn2:import}
		 * @param depth      1 for the root element, 2 for its children
		 * @param attributes the attributes in document order
		 */
		StartTag(String name, int depth, List<Attribute> attributes) {
			this.name = name;
			this.depth = depth;
			this.attributes = Collections.unmodifiableList(new ArrayList<Attribute>(attributes));
		}

		/** Returns the depth of the element: 1 for the root, 2 for its children. */
		int getDepth() {
			return depth;
		}

		/** Returns the prefix of the element name, or null when the name has none. */
		String getPrefix() {
			int colon = name.indexOf(':');
			return colon < 0 ? null : name.substring(0, colon);
		}

		/** Returns the element name without its prefix. */
		String getLocalName() {
			int colon = name.indexOf(':');
			return colon < 0 ? name : name.substring(colon + 1);
		}

		/**
		 * Returns the attribute with the given qualified name, or null when the
		 * tag has none.
		 */
		Attribute getAttribute(String qualifiedName) {
			for (Attribute attribute : attributes) {
				if (attribute.getName().equals(qualifiedName)) {
					return attribute;
				}
			}
			return null;
		}

		/**
		 * One attribute of a start tag, with the offsets of its raw value
		 * between the quotes.
		 */
		static final class Attribute {

			private final String name;
			private final String rawValue;
			private final int valueStart;
			private final int valueEnd;
			private final char quote;

			/**
			 * Creates an attribute.
			 *
			 * @param name       the qualified attribute name
			 * @param rawValue   the value as written, with entity references
			 * @param valueStart the offset of the first character of the value
			 * @param valueEnd   the offset just after the value, at the closing quote
			 * @param quote      the quote character around the value
			 */
			Attribute(String name, String rawValue, int valueStart, int valueEnd, char quote) {
				this.name = name;
				this.rawValue = rawValue;
				this.valueStart = valueStart;
				this.valueEnd = valueEnd;
				this.quote = quote;
			}

			/** Returns the qualified attribute name. */
			String getName() {
				return name;
			}

			/** Returns the value with its entity and character references resolved. */
			String getValue() {
				return XmlAttributeValues.decode(rawValue);
			}

			/** Returns the offset of the first character of the raw value. */
			int getValueStart() {
				return valueStart;
			}

			/** Returns the offset just after the raw value. */
			int getValueEnd() {
				return valueEnd;
			}

			/** Returns the quote character around the value. */
			char getQuote() {
				return quote;
			}
		}
	}
}
// EcoreFS end: finds the root element and its children in XML text, with the position of every attribute value
