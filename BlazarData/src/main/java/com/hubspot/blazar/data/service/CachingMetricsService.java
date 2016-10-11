package com.hubspot.blazar.data.service;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.hubspot.blazar.base.InterProjectBuild;
import com.hubspot.blazar.base.ModuleBuild;
import com.hubspot.blazar.base.RepositoryBuild;

public class CachingMetricsService {
  private static final Logger LOG = LoggerFactory.getLogger(CachingMetricsService.class);
  private static final long MODULE_BUILD_COUNT_MAX_AGE_MILLIS = 500;
  private static final long BRANCH_BUILD_COUNT_MAX_AGE_MILLIS = 500;
  private static final long INTER_PROJECT_BUILD_COUNT_MAX_AGE_MILLIS = 500;
  private final MetricsService metricsService;
  private volatile Map<ModuleBuild.State, Integer> moduleBuildCountMap = ImmutableMap.of();
  private volatile Map<RepositoryBuild.State, Integer> repoBuildCountMap = ImmutableMap.of();
  private volatile Map<InterProjectBuild.State, Integer> interProjectCountMap = ImmutableMap.of();
  private volatile long moduleBuildCountMapLastWrite = 0;
  private volatile long repoBuildCountMapLastWrite = 0;
  private volatile long interProjectBuildCountMapLastWrite = 0;

  @Inject
  public CachingMetricsService(MetricsService metricsService) {
    this.metricsService = metricsService;
    this.moduleBuildCountMap = new HashMap<>();
    this.moduleBuildCountMapLastWrite = 0;
    this.repoBuildCountMapLastWrite = 0;
  }

  public synchronized int getCachedActiveModuleBuildCountByState(ModuleBuild.State state) {
    if (isTooOld(moduleBuildCountMapLastWrite, MODULE_BUILD_COUNT_MAX_AGE_MILLIS)) {
      LOG.info("Refreshing moduleBuildCountMap cache");
      moduleBuildCountMap = metricsService.countActiveModuleBuildsByState();
      moduleBuildCountMapLastWrite = System.currentTimeMillis();
    }

    if (!moduleBuildCountMap.containsKey(state)) {
      LOG.info("No such state {} in count results returning 0", state);
      return 0;
    }

    return moduleBuildCountMap.get(state);
  }

  public synchronized int getCachedActiveBranchBuildCountByState(RepositoryBuild.State state) {
    if (isTooOld(repoBuildCountMapLastWrite, BRANCH_BUILD_COUNT_MAX_AGE_MILLIS)) {
      LOG.info("Refreshing branchBuildCountMap cache");
      repoBuildCountMap = metricsService.countActiveBranchBuildsByState();
      repoBuildCountMapLastWrite = System.currentTimeMillis();
    }

    if (!repoBuildCountMap.containsKey(state)) {
      LOG.info("No such state {} in count results returning 0", state);
      return 0;
    }

    return repoBuildCountMap.get(state);
  }

  public synchronized int getCachedActiveInterProjectBuildCountByState(InterProjectBuild.State state) {
    if (isTooOld(interProjectBuildCountMapLastWrite, INTER_PROJECT_BUILD_COUNT_MAX_AGE_MILLIS)) {
      LOG.info("Refreshing interProjectBuildCountMap cache");
      interProjectCountMap = metricsService.countActiveInterProjectBuildsByState();
      interProjectBuildCountMapLastWrite = System.currentTimeMillis();
    }

    if (!interProjectCountMap.containsKey(state)) {
      LOG.info("No such state {} in count results returning 0", state);
      return 0;
    }

    return interProjectCountMap.get(state);
  }

  private boolean isTooOld(long lastWrite, long maxAge) {
    return System.currentTimeMillis() - lastWrite > maxAge;
  }
}
