package ms.wiwi.examarchive.services;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBufferedFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionJavaScript;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PdfService {

    private static final Logger logger = LoggerFactory.getLogger(PdfService.class);

    /**
     * Convert PDF to JPEG images with specified DPI.
     * @param pdf All bytes of the PDF file
     * @param dpi DPI value for the JPEG images
     * @return List of JPEG images as byte arrays
     */
    public List<byte[]> convertPdfToImages(byte[] pdf , int dpi) throws RuntimeException{
        List<byte[]> images = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDFRenderer renderer = new PDFRenderer(document);
            int pageCount = document.getNumberOfPages();
            for (int page = 0; page < pageCount; page++) {
                BufferedImage image = renderer.renderImageWithDPI(page, dpi, ImageType.RGB);
                try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                    ImageIO.write(image, "JPEG", baos);
                    images.add(baos.toByteArray());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        } catch (RuntimeException | IOException e) {
            logger.error("Error while converting PDF to JPEG", e);
            throw new RuntimeException(e);
        }
        return images;
    }

    /**
     * Sanitizes a PDF file by removing all JavaScript, embedded files, and open actions.
     *
     * @param inputFile PDF file to sanitize
     * @param sanitizedOutputFile Output file for the sanitized PDF
     * @return True if the PDF was sanitized successfully, false otherwise. When returning false, the sanitizedOutputFile file will not exist
     * and the input should be rejected by the caller.
     */
    public boolean sanitizePdf(File inputFile, File sanitizedOutputFile) {
        try (RandomAccessReadBufferedFile buffer = new RandomAccessReadBufferedFile(inputFile); PDDocument document = Loader.loadPDF(buffer)) {
            PDDocumentCatalog catalog = document.getDocumentCatalog();
            if (catalog.getNames() != null && catalog.getNames().getJavaScript() != null) {
                catalog.getNames().setJavascript(null);
            }
            if (catalog.getNames() != null && catalog.getNames().getEmbeddedFiles() != null) {
                catalog.getNames().setEmbeddedFiles(null);
            }
            if (catalog.getOpenAction() != null) {
                catalog.setOpenAction(null);
            }
            for (PDPage page : document.getPages()) {
                for (PDAnnotation annotation : page.getAnnotations()) {
                    if (annotation instanceof PDAnnotationLink link) {
                        if (link.getAction() instanceof PDActionJavaScript) {
                            link.setAction(null);
                        }
                    }
                    if (annotation instanceof PDAnnotationWidget widget) {
                        if (widget.getActions() != null) {
                            widget.setActions(null);
                        }
                    }
                }
            }
            document.save(sanitizedOutputFile);
            return true;
        } catch (IOException e) {
            logger.error("Failed to sanitize PDF {}", inputFile.getName());
            if (sanitizedOutputFile.exists()) {
                sanitizedOutputFile.delete();
            }
            return false;
        }
    }
}
