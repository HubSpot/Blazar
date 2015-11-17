import Reflux from 'reflux';
import BuildActions from '../actions/buildActions';

const BuildStore = Reflux.createStore({

  listenables: BuildActions,

  init() {
    this.build = {};
    this.buildTriggeringDone = true;
    this.buildTriggeringError = '';
  },

  loadBuildSuccess(build) {
    this.build = build;

    this.trigger({
      build: this.build,
      loading: false
    });
  },

  loadBuildError(detail) {
    this.build = detail.build;
    
    this.trigger({
      error: detail.responseText,
      loading: false,
      build: this.build
    });
  },

  loadBuildCancelled() {
    this.build.build.build.state = 'CANCELLED';
    this.trigger({
      build: this.build
    })
  },

  triggerBuildStart() {
    this.buildTriggeringDone = false;

    this.trigger({
      buildTriggeringDone: false
    });
  },

  triggerBuildSuccess() {
    this.buildTriggeringDone = true;

    this.trigger({
      buildTriggeringDone: true
    });
  },
  
  loadGenericErrorMessage(error) {    
    this.trigger({
      error: error
    });
  },

  triggerBuildError(error) {
    this.buildTriggeringDone = true;
    this.buildTriggeringError = error;

    this.trigger({
      buildTriggeringDone: true,
      buildTriggeringError: error
    });
  }

});

export default BuildStore;
