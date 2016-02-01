import React, {Component, PropTypes} from 'react';
import {map} from 'underscore';
import TableHeadRow from './TableHeadRow.jsx';
import classNames from 'classnames';


class TableHead extends Component {

  getClassNames() {
    return this.props.classNames;
  }

  getColumns() {
    return map(this.props.columnNames, (name, i) =>
      <TableHeadRow key={name.length > 0 ? (name).replace(/ /g, '') : i} label={name} />
    );
  }

  render() {

    return (
      <thead className={this.getClassNames()}>
        <tr>
          {this.getColumns()}
        </tr>
      </thead>
    );
  }

}

TableHead.propTypes = {
  columnNames: PropTypes.array.isRequired,
  classNames: PropTypes.string
};

export default TableHead;
