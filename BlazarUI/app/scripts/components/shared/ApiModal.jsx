import React, {Component, PropTypes} from 'react';
import Modal from 'react-bootstrap/lib/Modal';
import Button from 'react-bootstrap/lib/Button';
import FormGroup from 'react-bootstrap/lib/FormGroup';
import FormControl from 'react-bootstrap/lib/FormControl';

class ApiModal extends Component {

  constructor() {
    super();
    this.state = {apiRootUrl: ''};
    this.onChange = this.onChange.bind(this);
    this.setUrl = this.setUrl.bind(this);
  }

  onChange(e) {
    this.setState({apiRootUrl: e.target.value});
  }

  setUrl() {
    localStorage.apiRootOverride = this.state.apiRootUrl;
    this.props.onHide();
    location.reload();
  }

  render() {
    const {show, onHide} = this.props;
    return (
      <Modal show={show} onHide={onHide}>
        <Modal.Header closeButton={true}>
          <Modal.Title>API Root Override</Modal.Title>
        </Modal.Header>
        <Modal.Body>
          <FormGroup>
            <FormControl
              type="text"
              placeholder="https://path.to-api.com/v1/api"
              label="Enter API root URL to use:"
              value={this.state.apiRootUrl}
              onChange={this.onChange}
            />
          </FormGroup>
        </Modal.Body>
        <Modal.Footer>
          <Button onClick={onHide}>Close</Button>
          <Button bsStyle="primary" onClick={this.setUrl}>Set</Button>
        </Modal.Footer>
      </Modal>
    );
  }
}

ApiModal.propTypes = {
  show: PropTypes.bool.isRequired,
  onHide: PropTypes.func.isRequired
};

export default ApiModal;
