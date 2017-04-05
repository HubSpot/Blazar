package com.hubspot.blazar.listener;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hubspot.blazar.base.ModuleBuild;
import com.hubspot.blazar.base.visitor.AbstractModuleBuildVisitor;
import com.hubspot.blazar.exception.NonRetryableBuildException;
import com.hubspot.blazar.externalservice.BuildClusterService;

/**
 * This class handles the cancellation of builds by killing the build container.
 */
@Singleton
public class BuildContainerKiller extends AbstractModuleBuildVisitor {
  private static final Logger LOG = LoggerFactory.getLogger(BuildContainerKiller.class);

  private final BuildClusterService buildClusterService;

  @Inject
  public BuildContainerKiller(BuildClusterService buildClusterService) {
    this.buildClusterService = buildClusterService;
  }

  @Override
  protected void visitCancelled(ModuleBuild moduleBuild) throws NonRetryableBuildException {
    try {
      buildClusterService.killBuildContainer(moduleBuild);
    } catch (Exception e) {
      throw new NonRetryableBuildException(String.format("A problem encountered while trying to kill the container of cancelled module build %d", moduleBuild.getId().get()), e);
    }
  }
}
