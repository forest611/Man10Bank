import React from 'react';
import { Link, Route, BrowserRouter as Router, Routes } from 'react-router-dom';
import BalancePage from './components/BalancePage';
import UuidPage from "./components/UuidPage";
import EstatePage from "./components/EstatePage";
import config from './config.json'
import HomePage from "./components/HomePage";
import './css/App.css'
import './css/Header.css'
import ServerEstatePage from "./components/ServerEstatePage";

export let apiUrl : string = ""

const App: React.FC = () => {

  loadConfig()

  return (
      <div>
        <Router>
            <header>
              <ul className='header_link'>
                <li><Link to="/bank/">Home</Link></li>
                <li><Link to="/bank/balance">最新の銀行の残高をみる</Link></li>
                <li><Link to="/bank/uuid">mcidからuuidを取得する</Link></li>
                <li><Link to="/bank/estate">資産情報を見る</Link></li>
              </ul>
            </header>
            <body>
              <Routes>
                <Route path="/bank/" element={<HomePage />} />
                <Route path="/bank/balance" element={<BalancePage />} />
                <Route path="/bank/uuid" element={<UuidPage />} />
                <Route path="/bank/estate" element={<EstatePage/>} />
                <Route path="/bank/serverestate" element={<ServerEstatePage/>} />
              </Routes>
            </body>
        </Router>

      </div>
  );
};

function loadConfig() {
  apiUrl = config.apiUrl
}

export function formatDate(date: Date) {
  return `${date.getFullYear()}-${date.getMonth() + 1}-${date.getDate()} ${date.getHours()}:${date.getMinutes()}:${date.getSeconds()}`
}

export default App;
