package com.igormaznitsa.mvnjlink.mojos;

import com.igormaznitsa.mvnjlink.exceptions.FailureException;
import com.igormaznitsa.mvnjlink.jdkproviders.JdkProviderId;
import com.igormaznitsa.mvnjlink.utils.ProxySettings;
import com.igormaznitsa.mvnjlink.utils.SystemUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.toolchain.Toolchain;
import org.apache.maven.toolchain.ToolchainManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// TODO investigate https://github.com/apache/maven-jdeps-plugin/blob/maven-jdeps-plugin-3.1.1

public abstract class AbstractJlinkMojo extends AbstractMojo {

  @Parameter(defaultValue = "${project}", readonly = true, required = true)
  private MavenProject project;

  @Parameter(defaultValue = "false", name = "useOnlyCache")
  private boolean useOnlyCache;

  @Parameter(defaultValue = "${user.home}${file.separator}.mvnJlinkCache", name = "jdkCachePath")
  private String jdkCachePath = System.getProperty("user.home") + File.separator + ".mvnJlinkJdkCache";

  @Parameter(defaultValue = "${session}", readonly = true, required = true)
  private MavenSession session;

  @Parameter(name = "skip", defaultValue = "false")
  private boolean skip;

  @Parameter(name = "disableSSLcheck", defaultValue = "false")
  private boolean disableSSLcheck;

  @Parameter(name = "proxy")
  private ProxySettings proxy;

  @Parameter(name = "provider", defaultValue = "LOCAL")
  private JdkProviderId provider = JdkProviderId.LOCAL;

  @Parameter(name = "providerConfig")
  private Map<String, String> providerConfig = new HashMap<>();

  @Parameter(name = "toolJdk", defaultValue = "null")
  private String toolJdk = null;

  @Component
  private ToolchainManager toolchainManager;

  public boolean isUseOnlyCache() {
    return this.useOnlyCache;
  }

  @Nonnull
  public Map<String, String> getProviderConfig() {
    return this.providerConfig;
  }

  @Nonnull
  public String getJdkCachePath() {
    return this.jdkCachePath;
  }

  @Nonnull
  public JdkProviderId getProvider() {
    return this.provider;
  }

  @Nullable
  public ProxySettings getProxy() {
    return this.proxy;
  }

  public boolean isDisableSSLcheck() {
    return this.disableSSLcheck;
  }

  public boolean isSkip() {
    return this.skip;
  }

  @Nonnull
  public MavenProject getProject() {
    return this.project;
  }

  public final void execute() throws MojoExecutionException, MojoFailureException {
    if (isSkip()) {
      this.getLog().debug("Skip flag is active");
    } else {
      onExecute();
    }
  }

  public boolean isOfflineModeActive() {
    return this.isUseOnlyCache() || this.getSession().isOffline();
  }

  @Nonnull
  protected MavenSession getSession(){
    return this.session;
  }

  @Nonnull
  public Path findJdkCacheFolder() throws IOException {
    final String storeFolder = this.getJdkCachePath();

    if (storeFolder.trim().isEmpty()) {
      throw new IOException("Path to the cache folder is not provided");
    }

    final Path result = Paths.get(storeFolder);

    if (!Files.isDirectory(result)) {
      Files.createDirectories(result);
    }

    if (!Files.isReadable(result)) {
      throw new IOException("Can't read from the cache folder, check rights: " + result);
    }

    if (!Files.isWritable(result)) {
      throw new IOException("Can't write to the cache folder, check rights: " + result);
    }
    return result;
  }

  protected void processJdkProvider() throws MojoExecutionException, MojoFailureException {
    try {
      this.getProvider().makeInstance(this).prepareJdkFolder(this.getProviderConfig());
    } catch (IOException ex) {
      throw new MojoExecutionException("Provider can't prepare JDK folder, see log for errors!", ex);
    } catch (FailureException ex) {
      throw new MojoFailureException(ex.getMessage());
    }
  }

  @Nullable
  protected String findJdkTool(@Nonnull final String toolName) {
    String toolPath = null;
    if (this.toolJdk == null) {
      final Toolchain toolcahin = getToolchain();
      if (toolcahin == null) {

      } else {
        toolPath = SystemUtils.ensureOsExtension(toolcahin.findTool(toolName));
      }
    } else {
      final Path jdkHome = Path.of(this.toolJdk);
      if (Files.isDirectory(jdkHome)) {
        Path foundPath = SystemUtils.findJdkExecutable(this.getLog(), jdkHome, toolName);
        toolPath = foundPath == null ? null : foundPath.toString();
      } else {
        this.getLog().error("Can't find directory: "+jdkHome);
      }
    }
    return toolPath;
  }

  @Nullable
  protected Toolchain getToolchain()
  {
    Toolchain result = null;
    if (this.toolchainManager != null ) {
      result = this.toolchainManager.getToolchainFromBuildContext( "jdk", this.session);

      if ( result == null ) {
        try {
          final Method getToolchainsMethod = this.toolchainManager.getClass().getMethod( "getToolchains", MavenSession.class, String.class, Map.class);

          @SuppressWarnings( "unchecked" )
          final List<Toolchain> toolchainList = (List<Toolchain>) getToolchainsMethod.invoke(this.toolchainManager, this.session, "jdk", Collections.singletonMap( "version", "[1.8,)" ) );

          if ( toolchainList != null && !toolchainList.isEmpty()) {
            result = toolchainList.get( toolchainList.size() - 1 );
          }
        }
        catch (Exception ex) {
          this.getLog().debug("Exception during getToolchain()", ex);
        }
      }
    }

    return result;
  }



  public abstract void onExecute() throws MojoExecutionException, MojoFailureException;
}