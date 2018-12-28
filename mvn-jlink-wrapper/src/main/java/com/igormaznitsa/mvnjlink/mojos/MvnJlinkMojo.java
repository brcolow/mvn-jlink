package com.igormaznitsa.mvnjlink.mojos;

import com.igormaznitsa.meta.annotation.MustNotContainNull;
import com.igormaznitsa.meta.common.utils.GetUtils;
import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.igormaznitsa.mvnjlink.utils.StringUtils.extractJdepsModuleNames;
import static java.nio.file.Files.isDirectory;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.joining;
import static org.apache.commons.io.FileUtils.deleteDirectory;

@Mojo(name = "jlink", defaultPhase = LifecyclePhase.PACKAGE)
public class MvnJlinkMojo extends AbstractJlinkMojo {

  @Parameter(name = "jdepsReportPath")
  private String jdepsReportPath;

  @Parameter(name = "options")
  private List<String> options = new ArrayList<>();

  @Parameter(name = "addModules")
  private List<String> addModules = new ArrayList<>();

  @Parameter(name = "output", required = true)
  private String output;

  @Nonnull
  @MustNotContainNull
  private static List<String> extractModuleNamesFromJdepsReport(@Nonnull final Optional<Path> jdepsReportPath) throws MojoExecutionException {
    if (jdepsReportPath.isPresent()) {
      final Path jdepsFile = jdepsReportPath.get();

      try {
        return extractJdepsModuleNames(FileUtils.readFileToString(jdepsFile.toFile(), Charset.defaultCharset()));
      } catch (IOException ex) {
        throw new MojoExecutionException("Can't read jdeps out file:" + jdepsFile, ex);
      }
    } else {
      return Collections.emptyList();
    }
  }

  @Nonnull
  @MustNotContainNull
  public List<String> getOptions() {
    return this.options;
  }

  public void setOptions(@Nullable @MustNotContainNull final List<String> value) {
    this.options = GetUtils.ensureNonNull(value, new ArrayList<>());
  }

  @Override
  public void onExecute() throws MojoExecutionException, MojoFailureException {
    final Log log = this.getLog();

    final Path providerJdk = this.getSourceJdkFolderFromProvider();

    final Path outputPath = Paths.get(this.output);

    final String pathToJlink = this.findJdkTool("jlink");
    if (pathToJlink == null) {
      throw new MojoExecutionException("Can't find jlink in JDK");
    }
    final Path execJlinkPath = Paths.get(pathToJlink);

    final List<String> modulesFromJdeps = extractModuleNamesFromJdepsReport(ofNullable(this.jdepsReportPath == null ? null : Paths.get(this.jdepsReportPath)));
    final List<String> totalModules = new ArrayList<>(modulesFromJdeps);
    totalModules.addAll(this.addModules);

    final String joinedAddModules = totalModules.stream().map(String::trim).collect(joining(","));

    log.info("List of modules : " + joinedAddModules);

    final List<String> commandLineOptions = new ArrayList<>(this.getOptions());

    commandLineOptions.add("--module-path");
    commandLineOptions.add(providerJdk.resolve("jmods").toString());

    final int indexOptions = commandLineOptions.indexOf("--add-modules");
    if (indexOptions < 0) {
      if (joinedAddModules.isEmpty()) {
        throw new MojoExecutionException("There are not provided modules to be added.");
      }
      commandLineOptions.add("--add-modules");
      commandLineOptions.add(joinedAddModules);
    } else {
      if (!joinedAddModules.isEmpty()) {
        commandLineOptions.set(indexOptions + 1, commandLineOptions.get(indexOptions + 1) + ',' + joinedAddModules);
      }
    }

    if (isDirectory(outputPath)) {
      log.warn("Deleting existing output folder: " + outputPath);
      try {
        deleteDirectory(outputPath.toFile());
      } catch (IOException ex) {
        throw new MojoExecutionException("Can't delete output folder: " + outputPath, ex);
      }
    }

    final List<String> commandLine = new ArrayList<>();
    commandLine.add(execJlinkPath.toString());
    commandLine.add("--output");
    commandLine.add(outputPath.toString());
    commandLine.addAll(commandLineOptions);

    this.getLog().info("CLI arguments: " + commandLine.stream().skip(1).collect(Collectors.joining(" ")));

    log.debug("Command line: " + commandLine);

    final ByteArrayOutputStream consoleOut = new ByteArrayOutputStream();
    final ByteArrayOutputStream consoleErr = new ByteArrayOutputStream();

    final ProcessResult executor;
    try {
      executor = new ProcessExecutor(commandLine)
          .redirectOutput(consoleOut)
          .redirectError(consoleErr)
          .executeNoTimeout();
    } catch (IOException ex) {
      throw new MojoExecutionException("Error during execution", ex);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new MojoFailureException("Execution interrupted", ex);
    }

    if (executor.getExitValue() == 0) {
      log.debug(new String(consoleOut.toByteArray(), Charset.defaultCharset()));
      log.info("Execution completed successfully, the result folder is " + outputPath);
    } else {
      final String textOut = new String(consoleOut.toByteArray(), Charset.defaultCharset());
      final String textErr = new String(consoleErr.toByteArray(), Charset.defaultCharset());

      if (executor.getExitValue() == 1 && textOut.contains("Error: java.lang.IllegalArgumentException")) {
        log.error("It looks like that working JDK is incompatible with the source module JDK!");
      }

      if (textErr.isEmpty()) {
        log.error(textOut);
      } else {
        log.info(textOut);
        log.error(textErr);
      }

      throw new MojoFailureException("jlink returns error status code: " + executor.getExitValue());
    }
  }
}