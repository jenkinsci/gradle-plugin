package hudson.plugins.gradle.injection;

import hudson.FilePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.io.InputStream;

/**
 * Represents a Maven extensions XML file, typically present at {@code .mvn/extensions.xml}.
 */
final class MavenExtensions {

    private static final Logger LOGGER = LoggerFactory.getLogger(MavenExtensions.class);

    private static final XPath XPATH = XPathFactory.newInstance().newXPath();

    private static final String EXTENSION_XPATH_EXPR = "/extensions/extension[groupId = '%s' and artifactId = '%s']";

    private final Document document;

    private MavenExtensions(Document document) {
        this.document = document;
    }

    static MavenExtensions empty() {
        return new MavenExtensions(null);
    }

    static MavenExtensions fromFilePath(FilePath extensionsFile) {
        try(InputStream inputStream = extensionsFile.read()) {
            Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(inputStream);
            document.normalizeDocument();

            return new MavenExtensions(document);
        } catch (ParserConfigurationException | IOException | InterruptedException| SAXException e) {
            LOGGER.warn("Failed to parse extensions file: {}", extensionsFile, e);
            return MavenExtensions.empty();
        }
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    boolean hasExtension(MavenCoordinates coordinates) {
        if (document == null || coordinates == null) {
            return false;
        }

        String expr = String.format(EXTENSION_XPATH_EXPR, coordinates.groupId(), coordinates.artifactId());
        try {
            XPathExpression exprCompiled = XPATH.compile(expr);
            NodeList extension = (NodeList) exprCompiled.evaluate(document, XPathConstants.NODESET);

            return extension != null && extension.getLength() > 0;
        } catch (XPathExpressionException e) {
            LOGGER.warn("Could not apply XPath expression: {}", expr, e);
            return false;
        }
    }

}
