import {extend, findWhere, contains} from 'underscore';
import BuildStates from '../constants/BuildStates';
import {buildIsOnDeck} from '../components/Helpers';

import Build from '../models/Build';
import Log from '../models/Log';
import LogSize from '../models/LogSize';
import Resource from '../services/ResourceProvider';

class BuildApi {

  constructor(params) {
    this.params = params;
    this.build = {};
  }

  _buildError(error) {
    this.cb(error);
  }

  loadBuild(cb) {
    this.cb = cb;
    this._getBuild()
      .then(this._getLog.bind(this))
      .then(this._assignBuildProcessing.bind(this));
  }

  navigationChange(position, cb) {
    this.cb = cb;
    const startOfLogLoaded = this.build.logCollection.minOffsetLoaded === 0;
    const endOfLogLoaded = this.build.logCollection.maxOffsetLoaded === this.build.logCollection.options.size;
    const buildInProgress = this.build.model.data.state === BuildStates.IN_PROGRESS;

    // navigated to the top
    if (position === 'top') {
      if (buildInProgress) {
        this.setLogPollingState(false);
      }

      if (startOfLogLoaded) {
        this._triggerUpdate({positionChange: position});
      } else {
        this._resetLogViaNavigation(position);
      }
    } else if (position === 'bottom') {
      // navigated to the bottom

      if (buildInProgress) {
        this.setLogPollingState(true);
      }

      if (endOfLogLoaded) {
        this._triggerUpdate({positionChange: position});
      } else {
        this._resetLogViaNavigation(position);
      }
    }
  }

  fetchPrevious(cb) {
    if (cb) {
      this.cb = cb;
    }

    const log = this.build.logCollection;

    if (log.minOffsetLoaded === 0) {
      this._triggerUpdate();
    } else {
      log.fetchPrevious().done(this._triggerUpdate.bind(this));
    }
  }

  fetchNext(cb) {
    if (cb) {
      this.cb = cb;
    }

    if (this.build.logCollection.requestOffset === -1) {
      return;
    }

    this.build.logCollection
      .fetchNext()
      .done(this._triggerUpdate.bind(this));
  }

  setLogPollingState(state, cb) {
    if (cb) {
      cb();
    }

    if (this.build.logCollection) {
      this.build.logCollection.shouldPoll = state;
      // if we stopped polling and are starting up again,
      // fetch the latest log size, last offset,
      // and start polling again
      if (state) {
        this._getLog().done(this._pollLog.bind(this));
      }
    }
  }

  _resetLogViaNavigation(position) {
    const logSize = this.build.logCollection.options.size;

    const logPromise = this.build.logCollection.updateLogForNavigationChange({
      size: logSize,
      position
    }).fetch();

    logPromise.done(() => {
      this._triggerUpdate({positionChange: position});
    });
  }

  _fetchBuild() {
    const buildPromise = this.build.model.fetch();

    buildPromise.fail((jqXHR) => {
      this._buildError(`Error retrieving build #${this.params.buildNumber}. See your console for more detail.`);
      console.warn(jqXHR);
    });

    return buildPromise;
  }

  _fetchLog() {
    if (!this.build.logCollection) {
      return null;
    }

    const logPromise = this.build.logCollection.fetch();

    logPromise
      .fail((jqXHR) => {
        this._buildError(`Error retrieving log for build #${this.params.buildNumber}. See your console for more detail.`);
        console.warn(jqXHR);
      });

    return logPromise;
  }

  _pollLog() {
    if (!this.build.logCollection) {
      return;
    }

    if (this.build.logCollection.shouldPoll && this.build.model.data.state === BuildStates.IN_PROGRESS) {
      this._fetchLog().done((data) => {
        this._triggerUpdate();
        this.build.logCollection.requestOffset = data.nextOffset;

        setTimeout(() => {
          this._pollLog();
        }, window.config.activeBuildLogRefresh);
      });
    }
  }

  _pollBuild() {
    if (!this.build.model) {
      return;
    }

    if (this.build.model.data.state === BuildStates.IN_PROGRESS) {
      this._fetchBuild().done(() => {
        // build has finished, get most up to date info
        if (this.build.model.data.state !== BuildStates.IN_PROGRESS) {
          this._updateLogSize()
            .then(this._fetchLog.bind(this))
            .then(this._triggerUpdate.bind(this));
        }
        setTimeout(() => {
          this._pollBuild();
        }, window.config.activeBuildRefresh);
      });
    }
  }

  // If cancelled build, fetch twice to see if the build
  // is still processing or if it is actually complete.
  _processCancelledBuild() {
    this._processFinishedBuild(() => {
      this.fetchNext();
    });
  }

  _processFinishedBuild(cb) {
    this._getLog().then(this._fetchLog.bind(this)).done(() => {
      this._triggerUpdate();
      if (cb) {
        cb();
      }
    });
  }

  _processBuildOnDeck() {
    this._triggerUpdate();
  }

  _processActiveBuild() {
    this._pollLog();
    this._pollBuild();
  }

  _getBuild() {
    const branchHistoryPromise = new Resource({
      url: `${window.config.apiRoot}/builds/history/branch/${this.params.branchId}`,
      type: 'GET'
    }).send();

    return branchHistoryPromise.then((resp) => {
      // now get modules so we can get the moduleId by the module name
      const repoBuildModules = new Resource({
        url: `${window.config.apiRoot}/branches/${this.params.branchId}/modules`,
        type: 'GET'
      }).send();

      return repoBuildModules.then((modules) => {
        const repoBuildModule = findWhere(modules, {name: this.params.moduleName, active: true});

        let buildModulesPromise;

        if (this.params.buildNumber !== 'latest') {
          const repoBuildId = findWhere(resp, {buildNumber: parseInt(this.params.buildNumber, 10)}).id;
          // last get module build based on module id
          buildModulesPromise = new Resource({
            url: `${window.config.apiRoot}/branches/builds/${repoBuildId}/modules`,
            type: 'GET'
          }).send();

          return buildModulesPromise.then((buildModules) => {
            const moduleBuild = findWhere(buildModules, {moduleId: repoBuildModule.id});

            this.build.model = new Build({
              id: moduleBuild.id,
              repoBuildId: moduleBuild.repoBuildId
            });

            return this._fetchBuild();
          });
        }

        buildModulesPromise = new Resource({
          url: `${window.config.apiRoot}/branches/state/${this.params.branchId}/modules`,
          type: 'GET'
        }).send();

        return buildModulesPromise.then((buildModules) => {
          const moduleBuild = buildModules.filter((module) => {
            return module.module.id === repoBuildModule.id;
          })[0];

          const buildToUse = this._getBuildToUse(moduleBuild);
          const repoBuildId = findWhere(resp, {buildNumber: parseInt(buildToUse.buildNumber, 10)}).id;

          this.build.model = new Build({
            id: buildToUse.id,
            repoBuildId
          });

          return this._fetchBuild();
        });
      });
    }, (error) => {
      this.cb(error);
    });
  }

  _getBuildToUse(moduleBuild) {
    const inProgressBuild = moduleBuild.inProgressBuild;

    if (inProgressBuild && contains(['QUEUED', 'LAUNCHING', 'IN_PROGRESS', 'SUCCEEDED', 'CANCELLED', 'FAILED', 'UNSTABLE'], inProgressBuild.state)) {
      return inProgressBuild;
    }

    return moduleBuild.lastBuild;
  }

  _getLog() {
    const logSizePromise = this._getLogSize();

    if (!logSizePromise || !this.build.model) {
      return null;
    }

    logSizePromise.done((resp) => {
      this.build.logCollection = new Log({
        buildId: this.build.model.data.id,
        size: resp.size,
        buildState: this.build.model.data.state
      });
    });

    return logSizePromise;
  }

  _getLogSize() {
    if (!this.build.model || buildIsOnDeck(this.build.model.data.state)) {
      return null;
    }

    const logSize = new LogSize({
      buildId: this.build.model.data.id
    });

    const sizePromise = logSize.fetch();

    sizePromise.fail((err) => {
      console.warn('Error requesting log size. ', err);
      this._buildError('The build log was not found. Most likely, the build failed to start. Try a manual rebuild; if you see this error again, message us in #platform-support.');
    });

    return sizePromise;
  }

  _updateLogSize() {
    const logSizePromise = this._getLogSize();

    if (!logSizePromise) {
      return null;
    }

    logSizePromise.done((resp) => {
      this.build.logCollection.options.size = resp.size;
    });

    return logSizePromise;
  }

  _assignBuildProcessing() {
    const buildState = this.build.model.data.state;

    switch (buildState) {
      case BuildStates.WAITING_FOR_UPSTREAM_BUILD:
      case BuildStates.WAITING_FOR_BUILD_SLOT:
      case BuildStates.QUEUED:
      case BuildStates.LAUNCHING:
        this._processBuildOnDeck();
        break;

      case BuildStates.IN_PROGRESS:
        this._processActiveBuild();
        break;

      case BuildStates.SUCCEEDED:
      case BuildStates.FAILED:
      case BuildStates.UNSTABLE:
        this._processFinishedBuild();
        break;

      case BuildStates.CANCELLED:
        this._processCancelledBuild();
        break;
      default:
    }
  }

  _triggerUpdate(additional = {}) {
    const buildInfo = {
      build: this.build.model.data,
      log: this.build.logCollection || {},
      loading: false,
      positionChange: false
    };

    const buildUpdate = extend(buildInfo, additional);

    this.cb(false, buildUpdate);
  }

}

export default BuildApi;
