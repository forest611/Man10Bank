import {apiUrl} from "../App";

let apiRoute = '/bank/'

//9c4161a9-0f5f-4317-835c-0bb196a7defa
export async function getBalance(uuid : string) {

    let balance = 0

    try {
        const response = await fetch(`${apiUrl+apiRoute}balance?uuid=${uuid}`);
        if (!response.ok) {
            throw new Error('Network response was not ok');
        }
        balance =  parseFloat(await response.text());
        if (isNaN(balance))balance = 0
    } catch (error) {
        console.error('Error fetching balance:', error);
    }

    return balance
}

export async function getUUID(mcid : string) {

    let uuid = ''

    try {
        const response = await fetch(`${apiUrl+apiRoute}uuid?mcid=${mcid}`)
        if (!response.ok){
            throw new Error('Network response was not ok');
        }
        uuid = await response.text()

    }catch (e){}

    return uuid
}

export async function getIdSuggest(mcid : string) {

    if (mcid.length < 3) return []

    try {
        const response = await fetch(`${apiUrl+apiRoute}suggest?mcid=${mcid}`)
        if (!response.ok){
            throw new Error('Network response was not ok');
        }

        const array : string[] = await response.json()
        return array
    }catch (e) {
        return []
    }
}

