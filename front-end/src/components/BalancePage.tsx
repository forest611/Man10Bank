import React, {useState} from "react";
import {getBalance, getUUID} from "../services/BankApi";

const BalancePage : React.FC = () => {

    const [balance, setBalance] = useState(0)

    const showResult = () =>{
        if (balance === -1){
            return '存在しないユーザーです'
        }
        return `銀行の残高:${balance.toLocaleString()}円`
    }

    return (
        <div>
            <h1>銀行の残高を確認</h1>
            <div>
                <label htmlFor="input">UUIDかMCIDを入力</label>
                <input
                    type="text"
                    id="input"
                    onChange={async e => {
                        const value = e.target.value

                        if (value.length <= 3) {
                            setBalance(-1)
                            return
                        }

                        //uuid
                        if (value.length === 36) {
                            setBalance(await getBalance(value))
                            return
                        }
                        //mcid
                        const uuid = await getUUID(value)
                        if (uuid.length === 36)setBalance(await getBalance(uuid))

                    }}
                    placeholder="UUIDかMCIDを入力してください"
                />
                <p>{showResult()}</p>
            </div>
        </div>
    );
}

export default BalancePage