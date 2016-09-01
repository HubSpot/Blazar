import Immutable from 'immutable';
import ActionTypes from '../redux-actions/ActionTypes';

const initialState = Immutable.Map({
  moduleStates: Immutable.List(),
  loading: false
});

export default function branchState(state = initialState, action) {
  switch (action.type) {
    case ActionTypes.REQUEST_MODULE_STATES:
      return initialState.set('loading', true);
    case ActionTypes.RECEIVE_MODULE_STATES:
      return state.merge({
        moduleStates: action.payload,
        loading: false
      });
    default:
      return state;
  }
}
