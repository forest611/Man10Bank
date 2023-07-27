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
                <li><Link style={fontStyle} to="/">Home</Link></li>
                <li><Link style={fontStyle} to="/balance">最新の銀行の残高をみる</Link></li>
                <li><Link style={fontStyle} to="/uuid">mcidからuuidを取得する</Link></li>
                <li><Link style={fontStyle} to="/estate">資産情報を見る</Link></li>
            </ul>
        </div>
    )

}

export default HomePage