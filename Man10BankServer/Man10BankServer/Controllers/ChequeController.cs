using Man10BankServer.Common;
using Microsoft.AspNetCore.Mvc;

namespace Man10BankServer.Controllers;

[ApiController]
[Route("[controller]")]
public class ChequeController : ControllerBase
{
    
    
    [HttpGet("create")]
    public int Create(string uuid,double amount,string note,bool isOp)
    {
        return Cheque.Create(uuid,amount,note,isOp).Result;
    }

    [HttpGet("try-use")]
    public double TryUse(int id,string player)
    {
        return Cheque.Use(id,player).Result;
    }

    [HttpGet("amount")]
    public double Amount(int id)
    {
        return Cheque.Amount(id).Result;
    }
}