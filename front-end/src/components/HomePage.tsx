import React from "react";
import {Link} from "react-router-dom";

const HomePage : React.FC = () =>{

    const fontStyle = {
        justifyContent: 'center',
        alignItems: 'center',
        fontSize: '20px',
    }

    return(
        <div>
            <h1>Man10Bank</h1>
            <ul style={{fontSize: '30px',listStyle: 'none'}}>
                <li><Link style={fontStyle} to="/bank">Home</Link></li>
                <li><Link style={fontStyle} to="/bank/balance">最新の銀行の残高をみる</Link></li>
                <li><Link style={fontStyle} to="/bank/uuid">mcidからuuidを取得する</Link></li>
                <li><Link style={fontStyle} to="/bank/estate">資産情報を見る</Link></li>
                <li><Link style={fontStyle} to="/bank/serverestate">サーバーの資産情報を見る</Link></li>
            </ul>
        </div>
    )

}

export default HomePage