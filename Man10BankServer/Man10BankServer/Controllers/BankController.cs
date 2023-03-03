using Man10BankServer.Common;
using Man10BankServer.Model;
using Microsoft.AspNetCore.Mvc;

namespace Man10BankServer.Controllers;

[ApiController]
[Route("[controller]")]
public class BankController : ControllerBase
{

    //9c4161a9-0f5f-4317-835c-0bb196a7defa
    [HttpGet("balance")]
    public double GetBalance(string uuid)
    {
        return Bank.AsyncGetBalance(uuid).Result;
    }
    
    [HttpGet("log")]
    public MoneyLog[] GetLog(string uuid)
    {
        return Bank.GetLog(uuid).Result;
    }

    [HttpGet("add")]
    public string AddBalance([FromBody]TransactionData data)
    {
        var ret = Bank.AsyncAddBalance(data.UUID,data.Amount,data.Plugin,data.Note,data.DisplayNote);
        return ret.Result;
    }

    [HttpGet("take")]
    public string TakeBalance([FromBody]TransactionData data)
    {
        return Bank.AsyncTakeBalance(data.UUID, data.Amount, data.Plugin, data.Note, data.DisplayNote).Result;
    }
    
    [HttpGet("set")]
    public bool SetBalance([FromBody]TransactionData data)
    {
        Bank.SetBalance(data.UUID,data.Amount,data.Plugin,data.Note,data.DisplayNote);
        return false;
    }

    [HttpPost("create")]
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