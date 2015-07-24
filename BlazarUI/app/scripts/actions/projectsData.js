import _ from 'underscore';


var projectsData = {

  // Ready data for the sidebar
  manageResponse: function(data, cb) {
    // list of module names, used for sidebar search
    let modules = [];
    _.filter(data, function(item){
      let module = { value: item.module.name, label: `${item.gitInfo.repository} » ${item.module.name}` };
      modules.push(module);
    });
    // jobs grouped by repo
    let grouped = _(data).groupBy(function(o) {
      return o.gitInfo.repository;
    });

    // determine if any of the repos have a module that is building
    _.each(grouped, function(repo){
      repo.moduleIsBuilding = false;
      for (var value of repo) {
        repo.repository = value.gitInfo.repository;
        if(value.buildState.result === 'IN_PROGRESS'){
          repo.moduleIsBuilding = true;
          break;
        }
      }
      return repo;
    })

    // To do: sort them by descending order of time being built
    // To do: if the job is dead, sort by order of last built

    data = {
      grouped: grouped,
      modules: modules
    };

    cb(data);
  }

}

export default projectsData;
