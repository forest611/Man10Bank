import React, {useState} from "react";
import {getUUID} from "../services/BankApi";

const UuidPage : React.FC = () =>{
    const [uuid,setUuid] = useState('')

    const showResult = () => {
        if (uuid === '')return "ログイン履歴のないユーザーです"
        return `UUID:${uuid}`
    }

    return(
        <div>
            <h1>UUIDを調べる</h1>
            <div>
                <label htmlFor="mcidInput">MCIDを入力</label>
                <input
                    type="text"
                    id="mcidInput"
                    onChange={async e => {
                        const value = e.target.value
                        if (value.length <=3) {
                            setUuid('')
                            return
                        }
                        setUuid(await getUUID(value))}
                    }
                    placeholder="MCIDを入力"
                />
                <p>{showResult()}</p>
            </div>


        </div>
    )

}

export default UuidPage