package ms.wiwi.examarchive.services;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.StreamType;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class QuartoSandboxService {

    private final static String IMAGE_NAME = "ghcr.io/fs-wiwi-ms/quartorender:latest";
    private final DockerClient dockerClient;
    private final String runtime;

    public QuartoSandboxService(String dockerHostUrl, String runtime) {
        this.runtime = runtime;
        DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().withDockerHost(dockerHostUrl).build();
        ApacheDockerHttpClient httpClient = new ApacheDockerHttpClient.Builder().dockerHost(config.getDockerHost()).sslConfig(config.getSSLConfig()).maxConnections(50).connectionTimeout(Duration.ofSeconds(10)).responseTimeout(Duration.ofSeconds(140)).build();
        this.dockerClient = DockerClientImpl.getInstance(config, httpClient);
        try {
            dockerClient.pullImageCmd(IMAGE_NAME).exec(new PullImageResultCallback()).awaitCompletion(5, TimeUnit.MINUTES);
            dockerClient.inspectImageCmd(IMAGE_NAME).exec();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public byte[] renderQmd(String qmdContent) throws Exception {
        HostConfig hostConfig = HostConfig.newHostConfig().withRuntime(runtime).withNetworkMode("none").withMemory(2L * 1024 * 1024 * 1024).withNanoCPUs(2_000_000_000L).withPidsLimit(150L).withCapDrop(Capability.ALL);
        CreateContainerResponse container = dockerClient.createContainerCmd(IMAGE_NAME).withHostConfig(hostConfig).withWorkingDir("/tmp").withCmd("/tmp/input.qmd").withAttachStdout(true).withAttachStderr(true).withTty(false).exec();

        String containerId = container.getId();
        ByteArrayOutputStream stdoutStream = new ByteArrayOutputStream();
        ByteArrayOutputStream stderrStream = new ByteArrayOutputStream();

        ResultCallback.Adapter<Frame> attachCallback = new ResultCallback.Adapter<>() {
            @Override
            public void onNext(Frame frame) {
                try {
                    if (frame.getStreamType() == StreamType.STDOUT) {
                        stdoutStream.write(frame.getPayload());
                    } else if (frame.getStreamType() == StreamType.STDERR) {
                        stderrStream.write(frame.getPayload());
                    }
                } catch (IOException e) {
                    throw new RuntimeException("Stream write error", e);
                }
            }
        };

        try {
            ByteArrayOutputStream tarBaos = new ByteArrayOutputStream();
            try (TarArchiveOutputStream tos = new TarArchiveOutputStream(tarBaos)) {
                byte[] contentBytes = qmdContent.getBytes(StandardCharsets.UTF_8);
                TarArchiveEntry entry = new TarArchiveEntry("input.qmd");
                entry.setSize(contentBytes.length);
                entry.setMode(0644);
                tos.putArchiveEntry(entry);
                tos.write(contentBytes);
                tos.closeArchiveEntry();
            }
            dockerClient.copyArchiveToContainerCmd(containerId).withTarInputStream(new ByteArrayInputStream(tarBaos.toByteArray())).withRemotePath("/tmp").exec();
            dockerClient.attachContainerCmd(containerId).withStdOut(true).withStdErr(true).withFollowStream(true).exec(attachCallback);
            dockerClient.startContainerCmd(containerId).exec();

            boolean completed = attachCallback.awaitCompletion(125, TimeUnit.SECONDS);
            if (!completed) {
                throw new RuntimeException("Rendering timed out after 125 seconds");
            }

            var inspect = dockerClient.inspectContainerCmd(containerId).exec();
            Long exitCode = inspect.getState().getExitCodeLong();
            if (exitCode == null || exitCode != 0) {
                String errorLog = stderrStream.toString(StandardCharsets.UTF_8);
                throw new RuntimeException("Quarto render failed (Exit code " + exitCode + "): " + errorLog);
            }
            return stdoutStream.toByteArray();

        } finally {
            try {
                attachCallback.close();
            } catch (Exception _) {
            }
            try {
                dockerClient.removeContainerCmd(containerId).withForce(true).exec();
            } catch (Exception _) {
            }
        }
    }
}