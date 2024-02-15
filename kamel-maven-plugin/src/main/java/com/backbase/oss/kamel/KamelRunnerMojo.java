package com.backbase.oss.kamel;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mojo(name = "run")
public class KamelRunnerMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;
    @Parameter(property = "apiFile", required = true)
    private String apiFile;

    @Parameter(property = "connectorNamePattern", defaultValue = "*Integration.java")
    private String connectorNamePattern;

    @Parameter(property = "configs")
    private List<String> configs;

    @Parameter(property = "traits")
    private Map<String, String> traits;

    @Parameter(property = "excludedDependencies")
    private List<String> excludedDependencies;

    @Parameter(property = "resourceFileTypes", defaultValue = ".yaml")
    private List<String> resourceFileTypes;

    @Parameter(property = "buildPropertiesFile")
    private String buildPropertiesFile;
    @Parameter(property = "sourcePaths", defaultValue = "${project.basedir}/src/main/resources,${project.basedir}/src/main/java")
    private List<String> sourcePaths;

    public void execute() throws MojoExecutionException {
        try {
            String command = buildCommand();
            getLog().info(command);
            executeCommand(command);
        } catch (Exception e) {
            throw new MojoExecutionException("Execution failed", e);
        }
    }

    protected String buildCommand() throws IOException, InterruptedException, MojoExecutionException {
        List<String> allFiles = new ArrayList<>();
        List<String> connectorFiles = new ArrayList<>();
        for (String dir : sourcePaths) {
            allFiles.addAll(scanFiles(new File(dir)));
        }

        StringBuilder command = new StringBuilder("kamel run \\");

        for (String config : configs) {
            if (!config.startsWith("--")) {
                config = "--" + config;
            }
            command.append("\n").append(config).append(" \\");
        }

        for (Map.Entry<String, String> traitEntry : traits.entrySet()) {
            command.append("\n--trait ").append(traitEntry.getKey()).append("=").append(traitEntry.getValue()).append(" \\");
        }

        for (String file : allFiles) {
            handleFileInCommand(file, command, connectorFiles);
        }

        Set<String> thirdPartyDependencies = getNonApacheCamelDependencies();
        for (String dep : thirdPartyDependencies) {
            command.append("\n--dependency ").append(dep).append(" \\");
        }

        handleApiYamlFile(apiFile, command);

        for (String connector : connectorFiles) {
            command.append("\n").append(connector);
        }

        appendAdditionalArgs(command);

        return command.toString();
    }

    private void executeCommand(String command) throws IOException, InterruptedException, MojoExecutionException {
        String[] cmdArray = {"/bin/sh", "-c", command};
        Process process = Runtime.getRuntime().exec(cmdArray);
        printStream(process.getInputStream());
        printStream(process.getErrorStream());
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Command execution failed with exit code: " + exitCode);
        }
    }

    private void handleFileInCommand(String file, StringBuilder command, List<String> connectorFiles) throws MojoExecutionException, IOException, InterruptedException {
            if (getFileExtension(file).equals(".properties")) {

                String configMapName = createConfigMapForFile(file);
                if (file.equals(buildPropertiesFile)) {
                    command.append("\n--build-property configmap:").append(configMapName).append(" \\");
                } else {
                    command.append("\n--config configmap:").append(configMapName).append(" \\");
                }
            } else if (resourceFileTypes.contains(getFileExtension(file))) {
                String configMapName = createConfigMapForFile(file);
                command.append("\n--config configmap:").append(configMapName).append(" \\");
            } else if (matchesPattern(file, connectorNamePattern)) {
                Path basePath = Paths.get(project.getBasedir().getPath());
                Path filePath = Paths.get(file);
                Path relativePath = basePath.relativize(filePath);
                String relativePathStr = "./" + relativePath;
                if (!connectorFiles.isEmpty()) {
                    throw new MojoExecutionException("More than one connector file detected. Only one connector file is allowed.");
                }
                connectorFiles.add(relativePathStr);
            }

    }


    private String createConfigMapForFile(String filePath) throws IOException, MojoExecutionException, InterruptedException {
        File file = new File(filePath);
        String prefix = project.getName();
        String configMapName = prefix + "-" + file.getName().replace(".", "-");
        configMapName = configMapName.toLowerCase();
        if (configMapName.length() > 62) {
            configMapName = configMapName.substring(0, 60) + "-l";
        }

        Map<String, String> data = new HashMap<>();
        data.put(file.getName(), new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));

        String configMapYaml = generateConfigMapYaml(configMapName, data);
        applyConfigMapToCluster(configMapYaml);

        return configMapName;
    }

    private void applyConfigMapToCluster(String configMapYaml) throws IOException, InterruptedException, MojoExecutionException {
        File tempFile = File.createTempFile("configmap-", ".yaml");
        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write(configMapYaml);
        }

        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command("kubectl", "apply", "-f", tempFile.getAbsolutePath());

        Process process = processBuilder.start();
        printStream(process.getInputStream());
        printStream(process.getErrorStream());
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new IOException("Failed to apply configMap to Kubernetes cluster.");
        }

        // Cleanup
        tempFile.delete();
    }

    private void handleApiYamlFile(String filePath, StringBuilder command) throws MojoExecutionException {
        try {
            String configMapName = createConfigMapForFile(filePath);
            command.append("\n--open-api configmap:").append(configMapName).append(" \\");
        } catch (IOException | InterruptedException e) {
            throw new MojoExecutionException("Failed to process API YAML file: " + filePath, e);
        }
    }
    private String generateConfigMapYaml(String name, Map<String, String> data) {
        StringBuilder yaml = new StringBuilder();
        yaml.append("apiVersion: v1\n");
        yaml.append("kind: ConfigMap\n");
        yaml.append("metadata:\n");
        yaml.append("  name: ").append(name).append("\n");
        yaml.append("data:\n");
        for (Map.Entry<String, String> entry : data.entrySet()) {
            yaml.append("  ").append(entry.getKey()).append(": |\n");
            for (String line : entry.getValue().split("\n")) {
                yaml.append("    ").append(line).append("\n");
            }
        }
        return yaml.toString();
    }

    private boolean matchesPattern(String fileName, String regex) {
        Pattern p = Pattern.compile(regex);
        Matcher m = p.matcher(fileName);
        return m.matches();
    }

    private String getFileExtension(String filePath) {
        int lastDotPosition = filePath.lastIndexOf('.');
        if (lastDotPosition == -1) {
            return "";  // No extension found
        }
        return filePath.substring(lastDotPosition); // This includes the dot
    }


    private Set<String> getNonApacheCamelDependencies() {
        Set<String> dependencies = new HashSet<>();
        Set<String> excludedDeps = new HashSet<>(excludedDependencies);

        project.getDependencies().stream().forEach(dependency -> {
            String groupId = dependency.getGroupId().toLowerCase();
            String artifactId = dependency.getArtifactId().toLowerCase();

            if (groupId.contains("quarkus") || groupId.contains("apache")) {
                if (groupId.contains("apache.camel.quarkus") || groupId.contains("apache.camel")) {
                    String transformedArtifact = artifactId.replace("quarkus-", "").replace("camel-", "");
                    dependencies.add("camel:" + transformedArtifact);
                }
                return;
            }

            if (!excludedDeps.contains(artifactId)) {
                dependencies.add("mvn:" + dependency.getGroupId() + ":" + artifactId + ":" + dependency.getVersion());
            }
        });

        return dependencies;
    }

    private void printStream(InputStream inputStream) throws MojoExecutionException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = br.readLine()) != null) {
                getLog().info(line);
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to print command output", e);
        }
    }

    private List<String> scanFiles(File directory) {
        List<String> files = new ArrayList<>();
        File[] fList = directory.listFiles();
        if (fList != null) {
            for (File file : fList) {
                if (file.isFile()) {
                    files.add(file.getAbsolutePath());
                } else if (file.isDirectory()) {
                    files.addAll(scanFiles(file));
                }
            }
        }
        return files;
    }

    protected void appendAdditionalArgs(StringBuilder command) {
        // to add other goals
    }
}
