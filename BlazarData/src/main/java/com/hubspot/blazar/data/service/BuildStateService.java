package com.hubspot.blazar.data.service;

import com.google.inject.Inject;
import com.hubspot.blazar.base.BuildState;
import com.hubspot.blazar.data.dao.BuildStateDao;

import java.util.Set;

public class BuildStateService {
  private final BuildStateDao buildStateDao;

  @Inject
  public BuildStateService(BuildStateDao buildStateDao) {
    this.buildStateDao = buildStateDao;
  }

  public Set<BuildState> getAllBuildStates(long since) {
    return buildStateDao.getAllBuildStates(since);
  }

  public BuildState getByModule(int moduleId) {
    return buildStateDao.getByModule(moduleId);
  }
}
