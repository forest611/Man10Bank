import React, {useEffect, useRef, useState} from "react";
import {getServerEstateHistory, ServerEstateData} from "../services/EstateApi";
import {formatDate} from "../App";
import {Chart, Colors, registerables} from "chart.js";
import {Console} from "inspector";

Chart.register(...registerables)
let chart : Chart

const ServerEstatePage : React.FC = () =>{

    const [estate,setEstate] = useState<ServerEstateData[]>()
    const [day,setDay] = useState(30)
    const chartRef = useRef<HTMLCanvasElement>(null)

    const fetch = async () => {
        const estate = await getServerEstateHistory(day)
        setEstate(estate)
    }
    const showEstate = () =>{
        if (estate == null || estate.length == 0)return '情報なし'
        const data = estate[estate.length-1]
        return `更新日:${formatDate(new Date(data.date))}
        電子マネー:${data.vault.toLocaleString()}
        現金:${data.cash.toLocaleString()}円
        銀行:${data.bank.toLocaleString()}円
        リボ:${data.loan.toLocaleString()}円
        総額:${data.total.toLocaleString()}円`
    }

    const drawChart = () => {
        if (chart !== undefined)chart.destroy()
        if (estate == null || estate.length <= 2)return
        if (chartRef.current === null)return;

        const ctx = chartRef.current.getContext('2d');

        if (ctx === null){return}

        // チャート用のデータを整形する
        const chartData = {
            labels: estate.map(item => formatDate(new Date(item.date))), // 横軸のラベル
            datasets: [
                {
                    label: '現金',
                    data: estate.map(item => item.cash),
                    borderColor: 'rgb(255,255,0)',
                    fill: false
                },

                {
                    label: '電子マネー',
                    data: estate.map(item => item.vault),
                    borderColor: 'rgb(0,255,0)',
                    fill: false
                },

                {
                    label: '銀行',
                    data: estate.map(item => item.bank),
                    borderColor: 'rgb(0,255,255)',
                    fill: false
                },

                {
                    label: '借金',
                    data: estate.map(item => item.loan),
                    borderColor: 'rgb(255,0,0)',
                    fill: false
                },

                {
                    label: '総額',
                    data: estate.map(item => item.total),
                    borderColor: 'rgb(255,255,255)',
                    fill: false
                },

            ]
        };
        // チャートを描画する
        chart = new Chart(ctx, {
            type: 'line',
            data: chartData,
        });

    }

    const ulStyle = {
        padding: '20px',
        fontSize: '20px',
        justifyContent: 'center',
        alignItems: 'center',
    }

    useEffect(()=> {fetch().then()},[])

    useEffect(()=>{
        drawChart()
    },[estate])

    return (

        <div>
            <h1>サーバー経済状況</h1>
            <div>
                <ul style={ulStyle}>
                    {showEstate().split("\n")
                        .map((line,index) => <li style={{color: 'antiquewhite'}} key={index}>{line}</li>)}
                </ul>
                <canvas id='Chart' ref={chartRef} width="400" height="400"></canvas>
            </div>
        </div>
    )

}

export default ServerEstatePage