# Kamel Maven Plugin README

The `kamel-maven-plugin` is designed to simplify the deployment and management of Camel integrations within a Kubernetes environment using the `kamel` CLI. This plugin distinguishes itself by auto-scanning and auto-configuring various resources, properties, and dependencies to streamline the deployment process.

## Features

- **Auto-scanning**: The plugin automatically scans your project for configuration files, resources, and properties to include in the kamel command.

- **Auto-configuring**: It automatically detects and sets up configuration maps in Kubernetes for the discovered resources.

- **Dependencies Management**: It fetches non-Apache Camel dependencies from the project and appends them to the kamel command.

## Goals

### `run`

Executes the `kamel run` command to deploy the integration to Kubernetes. The goal is designed to discover:

- Configuration files and treats them to create configMaps in Kubernetes.
- Non-Apache Camel Maven dependencies and appends them to the `kamel run` command.

### `dev`

Executes the `kamel run` command in development mode by appending the `--dev` flag. It allows for quicker iterative development as it facilitates hot-reloading of Camel routes during runtime.

The `dev` goal leverages all the capabilities of the `run` goal but provides a more developer-friendly approach to deploying integrations to Kubernetes.

## Configuration

### Common Parameters:

- **project**: Maven project reference. *(Required)*

- **configs**: List of configurations to be passed to the kamel CLI.

- **traits**: Map of traits to be used by the kamel CLI.

- **parentScans**: List of paths relative to the parent directory to be scanned for files.

- **excludedDependencies**: List of Maven artifactIds to be excluded when fetching the list of non-Apache Camel dependencies.

- **resourceFileTypes**: List of file extensions considered as resource files. Default is `.yaml`.

- **connectorNamePattern**: Pattern to match filenames considered as connector files. Default is `*Connector.java`.

- **apiDirectoryPattern**: Pattern to identify directories containing API documentation. Default is `**/api/doc/`.

### Advanced parameters:

- **PARENT_DIR**: Absolute path of the parent directory.

- **MODULE_DIR**: Absolute path of the module directory.

## Usage

Add the plugin to your `pom.xml`:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>com.backbase.oss</groupId>
            <artifactId>kamel-maven-plugin</artifactId>
            <version>YOUR_PLUGIN_VERSION</version>
            <configuration>
                <!-- Your configurations here -->
            </configuration>
        </plugin>
    </plugins>
</build>
