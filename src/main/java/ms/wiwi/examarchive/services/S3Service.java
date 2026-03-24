package ms.wiwi.examarchive.services;

import io.minio.*;
import io.minio.http.Method;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBufferedFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionJavaScript;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class S3Service {

    private static final Logger logger = LoggerFactory.getLogger(S3Service.class);

    private final MinioClient s3client;
    private final String bucketName;

    /**
     * Creates a new S3Service instance.
     *
     * @param endpoint   S3 Service endpoint
     * @param accessKey  S3 Access Key
     * @param secretKey  S3 Secret Key
     * @param bucketName Name of the S3 bucket
     */
    public S3Service(String endpoint, String accessKey, String secretKey, String bucketName) {
        s3client = MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
        this.bucketName = bucketName;
    }

    /**
     * Creates the S3 bucket if it doesn't exist.
     */
    public void createBucketIfNotExists(){
        try {
            if(!s3client.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build())){
                s3client.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Sanitizes a PDF and uploads it to the S3 bucket.
     *
     * @param file The PDF file to upload
     * @param objectName The destination key/name in the S3 bucket
     * @throws RuntimeException When upload fails or sanitization fails
     */
    public void uploadPDF(File file, String objectName) throws RuntimeException {
        File tempSanitizedFile = null;
        try {
            tempSanitizedFile = File.createTempFile("sanitized-upload-", ".pdf");
            boolean isSanitized = sanitizePdf(file, tempSanitizedFile);
            if (!isSanitized) {
                throw new IOException("Failed to sanitize the PDF. Upload aborted.");
            }
            try (InputStream is = new FileInputStream(tempSanitizedFile)) {
                s3client.putObject(PutObjectArgs.builder().bucket(bucketName).object(objectName).stream(is, tempSanitizedFile.length(), -1)
                        .contentType("application/pdf").build());

                logger.info("Successfully uploaded sanitized PDF to {}/{}", bucketName, objectName);
            }
        } catch (Exception e) {
            logger.error("Error uploading PDF: {}", objectName, e);
            throw new RuntimeException("Could not upload PDF to S3", e);
        } finally {
            if (tempSanitizedFile != null && tempSanitizedFile.exists()) {
                boolean deleted = tempSanitizedFile.delete();
                if (!deleted) {
                    logger.warn("Could not delete temporary sanitized file: {}", tempSanitizedFile.getAbsolutePath());
                }
            }
        }
    }

    /**
     * Tests the connection to the S3 bucket.
     * @return true if the connection was successful, false otherwise
     */
    public boolean testConnection() {
        try {
            s3client.listBuckets();
            return true;
        } catch (Exception e) {
            logger.error("Could not connect to S3", e);
            return false;
        }
    }

    /**
     * Deletes an object from the S3 bucket.
     */
    public void deleteFile(String objectName) {
        try {
            s3client.removeObject(RemoveObjectArgs.builder().bucket(bucketName).object(objectName).build());
        } catch (Exception e) {
            logger.error("Could not delete object {} in bucket {}", objectName, bucketName);
        }
    }

    /**
     * Creates a presigned URL for a file in the S3 bucket. The URL will expire after 10 minutes.
     *
     * @param objectName Name of the object in the bucket
     * @param filename   Name of the file to be downloaded
     * @return Presigned URL for the file, or null if the URL could not be created
     */
    public @Nullable String createPresignedUrl(String objectName, String filename) {
        try {
            Map<String, String> reqParams = new HashMap<>();
            reqParams.put("response-content-disposition", "attachment; filename=\"" + filename + "\"");
            return s3client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder().method(Method.GET).bucket(bucketName).object(objectName).expiry(10, TimeUnit.MINUTES).extraQueryParams(reqParams).build());
        } catch (Exception e) {
            logger.error("Could not create presigned URL for object {} in bucket {}", objectName, bucketName, e);
            return null;
        }
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