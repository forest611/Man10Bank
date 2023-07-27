import React, {useEffect, useRef, useState} from "react";
import {getIdSuggest, getUUID} from "../services/BankApi";
import {EstateData, getEstate, getUserEstateHistory} from "../services/EstateApi";
import {formatDate} from "../App";
import '../css/SuggestStyle.css'
import {Chart,registerables } from "chart.js";

Chart.register(...registerables)
let chart : Chart

const EstatePage : React.FC = () => {

    const [estate,setEstate] = useState<EstateData | null>(null)
    const [input,setInput] = useState('')
    const [suggest,setSuggest] = useState<string[]>([])

    const chartRef = useRef<HTMLCanvasElement>(null);

    const drawChart = async (value:string) => {
        const data = await getUserEstateHistory(value,30)

        if (data.length <= 2){
            console.log("データ不足")
            return
        }
        if (chartRef.current === null){return}

        const ctx = chartRef.current.getContext('2d');

        if (ctx === null){return}
        if (chart !== undefined)chart.destroy()

        // チャート用のデータを整形する
        const chartData = {
            labels: data.map(item => formatDate(new Date())), // 横軸のラベル
            datasets: [
                {
                    label: '直近30日の資産推移', // データセットのラベル
                    data: data.map(item => item.total), // 縦軸のデータにtotalを使用
                    borderColor: 'rgba(75, 192, 192, 1)', // 折れ線の色
                    backgroundColor: 'rgba(75, 192, 192, 0.2)', // 折れ線の下の領域の色
                    fill: true, // 下の領域を塗りつぶすかどうか
                },
            ],
        };

        // チャートを描画する
        chart = new Chart(ctx, {
            type: 'line',
            data: chartData,
        });

        console.log(chart.id)
    }

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

    const onClick = async (value:string) => {
        setInput(value)

        if (value.length < 3) {
            setEstate(null)
            setSuggest([])
            return
        }

        setSuggest(await getIdSuggest(value))

        //uuid
        if (value.length === 36) {
            await drawChart(value)
            setEstate(await getEstate(value))
            return
        }
        //mcid
        const uuid = await getUUID(value)
        if (uuid.length === 36){
            await drawChart(uuid)
            setEstate(await getEstate(uuid))
        }
    }

    return (
        <div>
            <h1>資産状況の確認</h1>
            <div>
                <label htmlFor="input">UUIDかMCIDを入力</label>
                <input
                    type="text"
                    id="input"
                    value={input}
                    onChange={async e => await onClick(e.target.value)}
                    placeholder="UUIDかMCIDを入力してください"
                />
                {suggest.length　> 0 && (
                    <ul className='suggest'>
                        {suggest.map((s,index) => <li　key={index} onClick={async ()=>{
                            setInput(s)
                            await onClick(s)
                        }}>{s}</li>)}
                    </ul>
                )}
                <ul style={ulStyle}>
                    {showResult().split("\n")
                        .map((line,index) => <li style={{color: 'antiquewhite'}} key={index}>{line}</li>)}
                </ul>
                <canvas id='Chart' ref={chartRef} width="400" height="200"></canvas>
            </div>
        </div>
    );

}

export default EstatePage