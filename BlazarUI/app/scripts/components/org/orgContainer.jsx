import React, {Component, PropTypes} from 'react';
import Immutable from 'immutable';
import PageContainer from '../shared/PageContainer.jsx';
import UIGrid from '../shared/grid/UIGrid.jsx';
import UIGridItem from '../shared/grid/UIGridItem.jsx';
import Headline from '../shared/headline/Headline.jsx';
import HeadlineDetail from '../shared/headline/HeadlineDetail.jsx';
import Icon from '../shared/Icon.jsx';
import Filter from '../shared/Filter.jsx';
import RepoListing from './RepoListing.jsx';

import OrgActions from '../../actions/orgActions';
import OrgStore from '../../stores/orgStore';

class OrgContainer extends Component {

  constructor() {
    this.state = {
      repos: Immutable.List.of(),
      loading: true,
      filterValue: ''
    };
    
    this.handleFilterChange = this.handleFilterChange.bind(this);
  }

  componentDidMount() {
    this.unsubscribeFromOrg = OrgStore.listen(this.onStatusChange.bind(this));
    OrgActions.loadRepos(this.props.params);
  }

  componentWillReceiveProps(nextprops) {
    this.setState({ loading: true });
    OrgActions.loadRepos(nextprops.params);
  }

  componentWillUnmount() {
    OrgActions.stopPolling();
    this.unsubscribeFromOrg();
  }

  onStatusChange(state) {
    this.setState(state);
  }
  
  handleFilterChange(newValue) {
    this.setState({
      filterValue: newValue
    });
  }
  
  getFilterOptions() {    
    return this.state.repos.toJS().map((item) => {
      return {
        value: item.repository,
        label: item.repository
      }
    });
  }
  
  getFilteredRepos() {
    const {repos, filterValue} = this.state;

    if (filterValue.length === 0) {
      return repos;
    }

    return repos.filter((item) => {
      return item.get('repository') === filterValue;
    });
    
  }

  render() {
    return (
      <PageContainer>
        <UIGrid>
          <UIGridItem size={12}>
            <Headline>
              <Icon type="octicon" name="organization" classNames="headline-icon" />
              <span>{this.props.params.org}</span>
              <HeadlineDetail>
                Repositories
              </HeadlineDetail>
            </Headline>
          </UIGridItem>
          <UIGridItem size={8}>
              <Filter
                placeholder='Filter Repositories'
                options={this.getFilterOptions()}
                value={this.state.filterValue}
                handleFilterChange={this.handleFilterChange}
                {...this.state}
                {...this.props}
              />
              <RepoListing 
                {...this.state}
                {...this.props}
                filteredRepos={this.getFilteredRepos()}
              />
          </UIGridItem>
        </UIGrid>
      </PageContainer>
    );
  }
}

OrgContainer.propTypes = {
  params: PropTypes.object.isRequired
};

export default OrgContainer;