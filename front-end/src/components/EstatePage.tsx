import React, {useState} from "react";
import {getUUID} from "../services/BankApi";
import {EstateData, getEstate} from "../services/EstateApi";
const EstatePage : React.FC = () => {

    const [estate,setEstate] = useState<EstateData | null>(null)

    const showResult = () => {
        if (estate === null)return '存在しないユーザーです'
        return `
        更新日:${estate.date.toLocaleString()}\n
        ユーザー名:${estate.player}\n
        電子マネー:${estate.vault.toLocaleString()}円\n
        現金:${estate.cash.toLocaleString()}円\n
        銀行:${estate.bank.toLocaleString()}円\n
        リボ:${estate.loan.toLocaleString()}円\n
        総額${estate.total.toLocaleString()}円\n
        `
    }

    return (
        <div>
            <h1>資産状況の確認</h1>
            <div>
                <label htmlFor="input"><span className='text-box-label'>UUIDかMCIDを入力</span></label>
                <input
                    className='text-box'
                    type="text"
                    id="input"
                    onChange={async e => {
                        const value = e.target.value

                        if (value.length <= 3) {
                            setEstate(null)
                            return
                        }

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
                <pre>{showResult()}</pre>
            </div>
        </div>
    );

}

export default EstatePage