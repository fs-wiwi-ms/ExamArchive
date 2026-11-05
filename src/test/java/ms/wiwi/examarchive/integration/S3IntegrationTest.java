package ms.wiwi.examarchive.integration;

import com.adobe.testing.s3mock.testcontainers.S3MockContainer;
import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import ms.wiwi.examarchive.services.S3Service;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
public class S3IntegrationTest {

    @Container
    static final S3MockContainer s3mock = new S3MockContainer("5.2");
    static S3Service s3Service;
    static MinioClient minioClient;

    @BeforeAll
    static void setUp() {
        String endpoint = s3mock.getHttpEndpoint();
        s3Service = new S3Service(endpoint, "foo", "bar");
        minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials("foo", "bar")
                .build();
        S3Service.Bucket.EXAMS.setName("exams");
        S3Service.Bucket.USER_EXAMS.setName("user-exams");
        s3Service.createBucketIfNotExists(S3Service.Bucket.USER_EXAMS);
        s3Service.createBucketIfNotExists(S3Service.Bucket.EXAMS);
    }

    @Test
    void testConnection() {
        assertTrue(s3Service.testConnection());
    }

    @Test
    void testBucketCreation() throws Exception {
        assertTrue(minioClient.bucketExists(BucketExistsArgs.builder().bucket(S3Service.Bucket.EXAMS.name()).build()));
        assertTrue(minioClient.bucketExists(BucketExistsArgs.builder().bucket(S3Service.Bucket.USER_EXAMS.name()).build()));
    }

    @Test
    void testUploadAndDownload(@TempDir Path tempDir) throws Exception {
        File dummyPdf = tempDir.resolve("test-exam.pdf").toFile();
        byte[] originalContent = "dummy pdf content".getBytes();
        Files.write(dummyPdf.toPath(), originalContent);
        String objectName = "uploaded-exam.pdf";
        s3Service.uploadPDF(dummyPdf, objectName, S3Service.Bucket.EXAMS);
        StatObjectResponse stat = minioClient.statObject(StatObjectArgs.builder()
                .bucket(S3Service.Bucket.EXAMS.name())
                .object(objectName)
                .build());
        assertEquals(dummyPdf.length(), stat.size());
        assertEquals("application/pdf", stat.contentType());
        byte[] downloadedBytes = s3Service.downloadFile(objectName, S3Service.Bucket.EXAMS);
        assertArrayEquals(originalContent, downloadedBytes, "Downloaded bytes should match the uploaded file");
    }

    @Test
    void testGetNonExistingFile() {
        assertThrows(RuntimeException.class, () -> s3Service.downloadFile("non-existing-file.pdf", S3Service.Bucket.EXAMS));
    }

    @Test
    void testDeleteFile(@TempDir Path tempDir) throws Exception {
        File fileToDelete = tempDir.resolve("delete-me.pdf").toFile();
        Files.writeString(fileToDelete.toPath(), "to be deleted");
        String objectName = "delete-test.pdf";
        s3Service.uploadPDF(fileToDelete, objectName, S3Service.Bucket.EXAMS);
        s3Service.deleteFile(objectName, S3Service.Bucket.EXAMS);
        ErrorResponseException exception = assertThrows(ErrorResponseException.class, () -> minioClient.statObject(StatObjectArgs.builder()
                .bucket(S3Service.Bucket.EXAMS.name())
                .object(objectName)
                .build()));

        assertEquals("NoSuchKey", exception.errorResponse().code());
    }

    @Test
    void testCreatePresignedUrl() {
        String objectName = "some-exam.pdf";
        String downloadFilename = "downloaded-exam.pdf";
        String url = s3Service.createPresignedUrl(objectName, downloadFilename, S3Service.Bucket.EXAMS);
        assertNotNull(url);
        assertTrue(url.contains(S3Service.Bucket.EXAMS.name()), "URL should contain bucket name");
        assertTrue(url.contains(objectName), "URL should contain object name");
        assertTrue(url.contains("response-content-disposition"), "URL should contain the content-disposition override");
        assertTrue(url.contains(downloadFilename), "URL should contain the specified download filename");
    }
}