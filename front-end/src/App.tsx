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
      <Router>
        <div>
          <h1>Man10BankWeb</h1>
          <nav>
            <ul>
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
          </nav>

          <Routes>
            <Route path="/balance" element={<BalancePage />} />
            <Route path="/uuid" element={<UuidPage />} />
            <Route path="/estate" element={<EstatePage/>} />
          </Routes>
        </div>
      </Router>
  );
};

function loadConfig() {
  apiUrl = config.apiUrl
}

export default App;
