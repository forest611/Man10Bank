import React from 'react';
import { Link, Route, BrowserRouter as Router, Routes } from 'react-router-dom';
import BalancePage from './components/BalancePage';
import UuidPage from "./components/UuidPage";
import EstatePage from "./components/EstatePage";
import config from './config.json'
import HomePage from "./components/HomePage";
import './css/App.css'

export let apiUrl : string = ""

const App: React.FC = () => {

  loadConfig()

  return (
      <div>
        <Router>
          <div>
            <header>
              <ul className='header_link'>
                <li>
                  <Link to="/">Home</Link>
                </li>
                <li>
                  <Link to="/balance">最新の銀行の残高をみる</Link>
                </li>
                <li>
                  <Link to="/uuid">mcidからuuidを取得する</Link>
                </li>
                <li>
                  <Link to="/estate">資産情報を見る</Link>
                </li>
              </ul>
            </header>
            <Routes>
              <Route path="/" element={<HomePage />} />
              <Route path="/balance" element={<BalancePage />} />
              <Route path="/uuid" element={<UuidPage />} />
              <Route path="/estate" element={<EstatePage/>} />
            </Routes>

          </div>
        </Router>

      </div>
  );
};

function loadConfig() {
  apiUrl = config.apiUrl
}

export default App;
