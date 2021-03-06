import Immutable from 'immutable';
import store from 'store';

// branches, not repos, are starred, but the app
// current saves the ids as 'starredRepos'
const migrateStarredRepos = () => {
  const starredRepos = store.get('starredRepos');
  if (starredRepos) {
    store.set('starredBranches', starredRepos);
    store.remove('starredRepos');
  }
};

export const loadStarredBranches = () => {
  migrateStarredRepos();
  return Immutable.Set(store.get('starredBranches') || []);
};

export const saveStarredBranches = (starredBranches) => {
  store.set('starredBranches', starredBranches.toJS());
};

// used to sync local storage across tabs and windows
export const onStarredBranchesUpdate = (callback) => {
  window.addEventListener('storage', (event) => {
    if (event.key === 'starredBranches') {
      callback(Immutable.Set(store.get('starredBranches') || []));
    }
  });
};

export const loadDismissedBetaNotifications = () => {
  return Immutable.Map(store.get('dismissedBetaNotifications') || {});
};

export const saveDismissedBetaNotifications = (dismissedBetaNotifications) => {
  // only allow toggling off notifications
  const savedDismissedBetaNotifications = loadDismissedBetaNotifications();
  store.set('dismissedBetaNotifications', savedDismissedBetaNotifications
    .mergeWith((saved, toSave) => saved || toSave, dismissedBetaNotifications)
    .toJS());
};
