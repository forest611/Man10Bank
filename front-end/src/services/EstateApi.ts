import {apiUrl} from "../App";

const apiRoute = '/history/'

//9c4161a9-0f5f-4317-835c-0bb196a7defa

export interface EstateData {
    id: number;
    player: string;
    uuid: string;
    date: Date;
    vault: number;
    bank: number;
    cash: number;
    estate: number;
    loan: number;
    crypto: number;
    total: number;
}

export async function  getEstate(uuid : string) {

    try {
        const response = await fetch(`${apiUrl+apiRoute}get-user-estate?uuid=${uuid}`);
        if (!response.ok) {
            throw new Error('Network response was not ok');
        }
        const estate : EstateData = await response.json()
        return estate
    } catch (error) {
        console.error('Error fetching balance:', error);
    }

    return null
}