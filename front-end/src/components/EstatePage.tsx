import React, {useState} from "react";
import {getIdSuggest, getUUID} from "../services/BankApi";
import {EstateData, getEstate} from "../services/EstateApi";
import {formatDate} from "../App";
const EstatePage : React.FC = () => {

    const [estate,setEstate] = useState<EstateData | null>(null)
    const [suggest,setSuggest] = useState<string[]>([])

    const showResult = () => {
        if (estate === null)return '存在しないユーザーです'
        const date = new Date(estate.date)
        return `更新日:${formatDate(date)}
        ユーザー名:${estate.player}
        電子マネー:${estate.vault.toLocaleString()}円
        現金:${estate.cash.toLocaleString()}円
        銀行:${estate.bank.toLocaleString()}円
        リボ:${estate.loan.toLocaleString()}円
        総額${estate.total.toLocaleString()}円`
    }

    const ulStyle = {
        padding: '20px',
        fontSize: '20px',
        justifyContent: 'center',
        alignItems: 'center',
    }

    const suggestStyle = {
        padding: '10px',
        listStyle: 'none',
        borderRadius: '5px',
        backgroundColor: 'antiquewhite',
        fontSize: '20px',
    }

    return (
        <div>
            <h1>資産状況の確認</h1>
            <div>
                <label htmlFor="input">UUIDかMCIDを入力</label>
                <input
                    type="text"
                    id="input"
                    onChange={async e => {
                        const value = e.target.value

                        if (value.length < 3) {
                            setEstate(null)
                            setSuggest([])
                            return
                        }

                        setSuggest(await getIdSuggest(value))

                        //uuid
                        if (value.length === 36) {
                            setEstate(await getEstate(value))
                            return
                        }
                        //mcid
                        const uuid = await getUUID(value)
                        if (uuid.length === 36)setEstate(await getEstate(uuid))

                    }}
                    placeholder="UUIDかMCIDを入力してください"
                />
                {suggest.length　> 0 && (
                    <ul style={suggestStyle}>
                        {suggest.map((s,index) => <li style={{backgroundColor:'antiquewhite'}} key={index}>{s}</li>)}
                    </ul>
                )}
                <ul style={ulStyle}>
                    {showResult().split("\n")
                        .map((line,index) => <li style={{color: 'antiquewhite'}} key={index}>{line}</li>)}
                </ul>
            </div>
        </div>
    );

}

export default EstatePage