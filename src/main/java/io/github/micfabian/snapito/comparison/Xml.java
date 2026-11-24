package io.github.micfabian.snapito.comparison;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public final class Xml {
  private static final ThreadLocal<DocumentBuilder> BUILDERS = new ThreadLocal<>();
  private static final ThreadLocal<Transformer> TRANSFORMERS = new ThreadLocal<>();

  private Xml() {
  }

  public static Document parse(String text) {
    try {
      return builder().parse(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
    } catch (SAXException | IOException | ParserConfigurationException e) {
      throw new IllegalArgumentException("Unable to parse XML: " + e.getMessage(), e);
    }
  }

  public static boolean isXml(String text) {
    if (text == null || text.isBlank() || !text.trim().startsWith("<")) {
      return false;
    }
    try {
      builder().parse(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
      return true;
    } catch (SAXException | IOException | ParserConfigurationException e) {
      return false;
    }
  }

  public static String canonical(String text) {
    Document document = parse(text);
    document.normalizeDocument();
    stripBlankText(document);
    return serialize(document);
  }

  public static String serialize(Node node) {
    try {
      Transformer transformer = transformer();
      StringWriter writer = new StringWriter();
      transformer.transform(new DOMSource(node), new StreamResult(writer));
      return writer.toString().trim();
    } catch (TransformerException e) {
      throw new IllegalArgumentException("Unable to serialize XML", e);
    }
  }

  private static Transformer transformer() throws TransformerException {
    Transformer cached = TRANSFORMERS.get();
    if (cached != null) {
      return cached;
    }
    TransformerFactory factory = TransformerFactory.newInstance();
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
    Transformer transformer = factory.newTransformer();
    transformer.setOutputProperty(OutputKeys.INDENT, "yes");
    transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
    transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
    TRANSFORMERS.set(transformer);
    return transformer;
  }

  private static DocumentBuilder builder() throws ParserConfigurationException {
    DocumentBuilder cached = BUILDERS.get();
    if (cached != null) {
      return cached;
    }
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    factory.setNamespaceAware(true);
    factory.setIgnoringComments(true);
    factory.setCoalescing(true);
    DocumentBuilder builder = factory.newDocumentBuilder();
    builder.setErrorHandler(null);
    BUILDERS.set(builder);
    return builder;
  }

  private static void stripBlankText(Node node) {
    NodeList children = node.getChildNodes();
    for (int index = children.getLength() - 1; index >= 0; index--) {
      Node child = children.item(index);
      if (child.getNodeType() == Node.TEXT_NODE && child.getTextContent().isBlank()) {
        node.removeChild(child);
      } else {
        stripBlankText(child);
      }
    }
  }
}
