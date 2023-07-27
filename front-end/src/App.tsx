import React from 'react';
import { Link, Route, BrowserRouter as Router, Routes } from 'react-router-dom';
import BalancePage from './components/BalancePage';
import UuidPage from "./components/UuidPage";
import EstatePage from "./components/EstatePage";
import config from './config.json'

export let apiUrl : string = ""

const App: React.FC = () => {

  loadConfig()

  return (
    <div className="App">
      <header className="App-header">
        <img src={logo} className="App-logo" alt="logo" />
        <p>
          Edit <code>src/App.tsx</code> and save to reload.
        </p>
        <a
          className="App-link"
          href="https://reactjs.org"
          target="_blank"
          rel="noopener noreferrer"
        >
          Learn React
        </a>
      </header>
    </div>
  );
}

function loadConfig() {
  apiUrl = config.apiUrl
}

export default App;
