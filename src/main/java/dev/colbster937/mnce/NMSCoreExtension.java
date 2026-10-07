package dev.colbster937.mnce;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginManagement;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.version.Version;

@Named
@Singleton
public final class NMSCoreExtension extends AbstractMavenLifecycleParticipant {
  private static final String NMS_PLUGIN_AFCT = "ca.bkaw:paper-nms-maven-plugin";

  private final BuildPluginManager pluginManager;
  private final RepositorySystem repositorySystem;

  @Inject
  public NMSCoreExtension(BuildPluginManager pluginManager, RepositorySystem repositorySystem) {
    this.pluginManager = pluginManager;
    this.repositorySystem = repositorySystem;
  }

  @Override
  public void afterProjectsRead(MavenSession session) throws MavenExecutionException {
    session.getProjects().forEach(project -> {
      final boolean[] init = { false };

      project.getDependencies().forEach(dependency -> {
        if (
          dependency.getGroupId().equals("ca.bkaw") &&
          dependency.getArtifactId().equals("paper-nms")
        ) {
          try {
            final Artifact artifact = new DefaultArtifact(
              dependency.getGroupId(),
              dependency.getArtifactId(),
              dependency.getClassifier(),
              dependency.getType(),
              dependency.getVersion()
            );

            final Version version = repositorySystem.resolveVersionRange(
              session.getRepositorySession(),
              new VersionRangeRequest(artifact, project.getRemoteProjectRepositories(), null)
            ).getHighestVersion();

            if (version != null) {
              repositorySystem.resolveArtifact(
                session.getRepositorySession(),
                new ArtifactRequest(
                  artifact.setVersion(version.toString()),
                  project.getRemoteProjectRepositories(),
                  null
                )
              );

              return;
            }
          } catch (Exception ex) {}

          init[0] = true;
        }
      });

      if (init[0]) {
        Plugin plugin = project.getPlugin(NMS_PLUGIN_AFCT);

        if (plugin == null) {
          final PluginManagement pluginManagement = project.getBuild().getPluginManagement();

          if (pluginManagement != null) {
            plugin = pluginManagement.getPluginsAsMap().get(NMS_PLUGIN_AFCT);
          }
        }

        if (plugin != null) {
          try {
            final MojoDescriptor mojo = pluginManager.loadPlugin(
              plugin,
              project.getRemotePluginRepositories(),
              session.getRepositorySession()
            ).getMojo("init");

            if (mojo != null) {
              final MojoExecution execution = new MojoExecution(mojo);
              execution.setMojoDescriptor(mojo);

              final Xpp3Dom configuration = new Xpp3Dom("configuration");

              final Xpp3Dom projectNode = new Xpp3Dom("project");
              projectNode.setValue("${project}");
              configuration.addChild(projectNode);

              final Xpp3Dom localRepositoryNode = new Xpp3Dom("localRepository");
              localRepositoryNode.setValue("${localRepository}");
              configuration.addChild(localRepositoryNode);

              execution.setConfiguration(configuration);

              final MavenProject currentProject = session.getCurrentProject();
              try {
                session.setCurrentProject(project);
                pluginManager.executeMojo(session, execution);
              } finally {
                session.setCurrentProject(currentProject);
              }
            } else {
              throw new IllegalStateException();
            }
          } catch (Exception ex) {
            throw new RuntimeException(ex);
          }
        }
      }
    });
  }
}