using Man10BankServer.Common;
using Microsoft.AspNetCore.Mvc;

namespace Man10BankServer.Controllers;

[ApiController]
[Route("[controller]")]
public class BankController : ControllerBase
{

    [HttpGet("get")]
    public double GetBalance(string uuid)
    {
        return Bank.AsyncGetBalance(uuid).Result;
    }

    [HttpPost("add")]
    public bool AddBalance([FromBody]TransactionData data)
    {
        Bank.AddBalance(data.UUID,data.Amount,data.Plugin,data.Note,data.DisplayNote);
        return false;
    }

    [HttpPost("take")]
    public bool TakeBalance([FromBody]TransactionData data)
    {
        Bank.TakeBalance(data.UUID,data.Amount,data.Plugin,data.Note,data.DisplayNote);
        return false;
    }
    
    [HttpPost("set")]
    public bool SetBalance([FromBody]TransactionData data)
    {
        Bank.SetBalance(data.UUID,data.Amount,data.Plugin,data.Note,data.DisplayNote);
        return false;
    }

    [HttpPost("create")]
    public bool CreateBank(string uuid,string mcid)
    {
        Bank.CreateBank(uuid,mcid);
        return false;
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