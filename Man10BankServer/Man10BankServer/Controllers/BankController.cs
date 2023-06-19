using Man10BankServer.Common;
using Man10BankServer.Model;
using Microsoft.AspNetCore.Mvc;

namespace Man10BankServer.Controllers;

[ApiController]
[Route("[controller]")]
public class BankController : ControllerBase
{
    [HttpGet("try-connect")]
    public int TryConnect()
    {
        Console.WriteLine("クライアントからの接続を確認しました");

        var connectionCheck = Bank.SyncCheckConnect().Result;

        if (connectionCheck)
        {
            Console.WriteLine("MySQLの接続を確認");
        }
        else
        {
            Console.WriteLine("MySQLの接続に失敗");
            return 1;
        }
        
        return 0;
    }

    [HttpGet("uuid")]
    public string GetUUID(string mcid)
    {
        return Utility.GetUUID(mcid).Result;
    }

    [HttpGet("score")]
    public int? GetScore(string uuid)
    {
        return Utility.GetScore(uuid).Result;
    }
    
    //9c4161a9-0f5f-4317-835c-0bb196a7defa
    [HttpGet("balance")]
    public double GetBalance(string uuid)
    {
        return Bank.SyncGetBalance(uuid).Result;
    }
    
    [HttpGet("log")]
    public MoneyLog[] GetLog(string uuid,int record,int skip)
    {
        return Bank.GetLog(uuid,record,skip).Result;
    }

    [HttpGet("add")]
    public string AddBalance([FromBody]TransactionData data)
    {
        var ret = Bank.SyncAddBalance(data.UUID,data.Amount,data.Plugin,data.Note,data.DisplayNote);
        return ret.Result;
    }

    [HttpGet("take")]
    public string TakeBalance([FromBody]TransactionData data)
    {
        return Bank.SyncTakeBalance(data.UUID, data.Amount, data.Plugin, data.Note, data.DisplayNote).Result;
    }
    
    [HttpGet("set")]
    public bool SetBalance([FromBody]TransactionData data)
    {
        Bank.SetBalance(data.UUID,data.Amount,data.Plugin,data.Note,data.DisplayNote);
        return false;
    }

    [HttpGet("create")]
    public void CreateBank(string uuid,string mcid)
    {
        Bank.CreateBank(uuid,mcid);
    }

}

public class TransactionData
{
    public string UUID { get; set; }
    public double Amount { get; set; }
    public string Plugin { get; set; }
    public string Note { get; set; }
    public string DisplayNote { get; set; }
}