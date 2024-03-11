using Man10BankServer.Common;
using Man10BankServer.Data;
using Man10BankServer.Model;
using Microsoft.AspNetCore.Mvc;

namespace Man10BankServer.Controllers;

[ApiController]
[Route("[controller]")]
public class BankController : ControllerBase
{
    // [HttpGet("try-connect")]
    // public int TryConnect()
    // {
    //     Console.WriteLine("クライアントからの接続を確認しました");
    //
    //     var connectionCheck = Bank.SyncCheckConnect().Result;
    //
    //     if (connectionCheck)
    //     {
    //         Console.WriteLine("MySQLの接続を確認");
    //     }
    //     else
    //     {
    //         Console.WriteLine("MySQLの接続に失敗");
    //         return 1;
    //     }
    //     
    //     return 0;
    // }
    
    [HttpGet("get")]
    public async Task<IActionResult> GetBalance(string uuid)
    {
        var p = await Player.GetFromUuid(uuid);
        if (p.IsEmpty())
        {
            return NotFound();
        }
        var bank = await Bank.GetBank(p);
        var result = await bank.GetBalance();
        return Ok(result.Amount);
    }

    [HttpPost("add")]
    public async Task<IActionResult> Add([FromBody] TransactionData data)
    {
        var p = await Player.GetFromUuid(data.UUID);
        if (p.IsEmpty())
        {
            return NotFound();
        }
        var bank = await Bank.GetBank(p);
        var result = await bank.Add(new Money(data.Amount), data.Plugin, data.Note, data.DisplayNote);
        return result ? Ok() : StatusCode(500,"Server Error");
    }

    [HttpPost("take")]
    public async Task<IActionResult> Take([FromBody] TransactionData data)
    {
        var p = await Player.GetFromUuid(data.UUID);
        if (p.IsEmpty())
        {
            return NotFound();
        }
        var bank = await Bank.GetBank(p);
        var result = await bank.Take(new Money(data.Amount), data.Plugin, data.Note, data.DisplayNote);
        return result ? Ok() : BadRequest("Lack Of Money");
    }
    
    [HttpPost("set")]
    public async Task<IActionResult> Set([FromBody] TransactionData data)
    {
        var p = await Player.GetFromUuid(data.UUID);
        if (p.IsEmpty())
        {
            return NotFound();
        }
        var bank = await Bank.GetBank(p);
        bank.Set(new Money(data.Amount), data.Plugin, data.Note, data.DisplayNote);
        return Ok();
    }

    [HttpGet("log")]
    public async Task<IActionResult> Log([FromBody] string uuid, int count, int skip)
    {
        var p = await Player.GetFromUuid(uuid);
        if (p.IsEmpty())
        {
            return NotFound();
        }
        var bank = await Bank.GetBank(p);
        var result = await bank.GetLog(count, skip);
        return Ok(result);
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