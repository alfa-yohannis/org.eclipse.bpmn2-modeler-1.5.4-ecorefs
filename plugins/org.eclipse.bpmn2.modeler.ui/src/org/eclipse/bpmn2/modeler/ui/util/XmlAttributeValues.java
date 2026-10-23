package org.eclipse.bpmn2.modeler.ui.util;

// EcoreFS begin: entity references in XML attribute values, read and written
/**
 * Reads and writes the entity and character references of XML attribute
 * values, so that a namespace written as {@code a&amp;b} compares equal to the
 * namespace {@code a&b}, and a new value never breaks the quotes around it.
 */
final class XmlAttributeValues {

	/** Prevents instances; the class only has static helpers. */
	private XmlAttributeValues() {
	}

	/**
	 * Resolves the five predefined entities and the numeric character
	 * references of a raw attribute value. A reference to any other entity
	 * stays as written, because the scanner reads no document type definition.
	 *
	 * @param rawValue the value as written between the quotes
	 * @return the value the XML parser would report
	 */
	static String decode(String rawValue) {
		if (rawValue.indexOf('&') < 0) {
			return rawValue;
		}
		StringBuilder decoded = new StringBuilder(rawValue.length());
		int index = 0;
		while (index < rawValue.length()) {
			int semicolon = rawValue.indexOf(';', index);
			if (rawValue.charAt(index) != '&' || semicolon < 0) {
				decoded.append(rawValue.charAt(index));
				index++;
				continue;
			}
			decoded.append(resolveReference(rawValue.substring(index, semicolon + 1)));
			index = semicolon + 1;
		}
		return decoded.toString();
	}

	/**
	 * Escapes a value for an attribute delimited by the given quote character.
	 *
	 * @param value the value to write
	 * @param quote the quote character around the value
	 * @return the value with '&', '<' and the quote character escaped
	 */
	static String escape(String value, char quote) {
		StringBuilder escaped = new StringBuilder(value.length() + 8);
		for (int index = 0; index < value.length(); index++) {
			escaped.append(escapeCharacter(value.charAt(index), quote));
		}
		return escaped.toString();
	}

	/** Returns the escaped form of one character of an attribute value. */
	private static String escapeCharacter(char character, char quote) {
		if (character == '&') {
			return "&amp;"; //$NON-NLS-1$
		}
		if (character == '<') {
			return "&lt;"; //$NON-NLS-1$
		}
		if (character == quote) {
			return quote == '"' ? "&quot;" : "&apos;"; //$NON-NLS-1$ //$NON-NLS-2$
		}
		return String.valueOf(character);
	}

	/**
	 * Resolves one reference such as {@code &amp;} or {@code &#x41;}, and
	 * keeps a reference that it cannot resolve as written.
	 */
	private static String resolveReference(String reference) {
		String name = reference.substring(1, reference.length() - 1);
		if ("lt".equals(name)) { //$NON-NLS-1$
			return "<"; //$NON-NLS-1$
		}
		if ("gt".equals(name)) { //$NON-NLS-1$
			return ">"; //$NON-NLS-1$
		}
		if ("amp".equals(name)) { //$NON-NLS-1$
			return "&"; //$NON-NLS-1$
		}
		if ("quot".equals(name)) { //$NON-NLS-1$
			return "\""; //$NON-NLS-1$
		}
		if ("apos".equals(name)) { //$NON-NLS-1$
			return "'"; //$NON-NLS-1$
		}
		return resolveCharacterReference(reference, name);
	}

	/**
	 * Resolves a decimal or hexadecimal character reference, and keeps the
	 * reference as written when it is neither.
	 */
	private static String resolveCharacterReference(String reference, String name) {
		try {
			if (name.startsWith("#x") || name.startsWith("#X")) { //$NON-NLS-1$ //$NON-NLS-2$
				return new String(Character.toChars(Integer.parseInt(name.substring(2), 16)));
			}
			if (name.startsWith("#")) { //$NON-NLS-1$
				return new String(Character.toChars(Integer.parseInt(name.substring(1))));
			}
		} catch (IllegalArgumentException e) {
			// Not a valid character reference: keep it as written
		}
		return reference;
	}
}
// EcoreFS end: entity references in XML attribute values, read and written
