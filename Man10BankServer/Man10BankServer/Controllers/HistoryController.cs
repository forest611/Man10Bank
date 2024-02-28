using Man10BankServer.Common;
using Man10BankServer.Model;
using Microsoft.AspNetCore.Mvc;

namespace Man10BankServer.Controllers;

[ApiController]
[Route("[controller]")]
public class HistoryController : ControllerBase
{

    [HttpGet("get-balance-top")]
    public async Task<IActionResult> GetBalanceTop(int record,int skip)
    {
        var result = await History.GetBalanceTop(record, skip);
        return Ok(result);
    }

    [HttpGet("get-loan-top")]
    public async Task<IActionResult> GetLoanTop(int record,int skip)
    {
        var result = await History.GetLoanTop(record,skip);
        return Ok(result);
    }

    [HttpGet("get-user-estate")]
    public async Task<IActionResult> GetUserEstate(string uuid)
    {
        var result = await History.GetUserEstate(uuid);
        return Ok(result);
    }

    [HttpGet("get-user-estate-history")]
    public async Task<IActionResult> GetUserEstateHistory(string uuid,int day)
    {
        var result = await History.GetUserEstateHistory(uuid, day);
        return Ok(result);
    }

    [HttpGet("get-server-estate")]
    public async Task<IActionResult> GetServerEstate()
    {
        var result = await History.GetServerEstate();
        return Ok(result);
    }

    [HttpGet("get-server-estate-history")]
    public async Task<IActionResult> GetServerEstateHistory(int day)
    {
        var result = await History.GetServerEstateHistory(day);
        return Ok(result);
    }
    
    [HttpPost("add-user-estate")]
    public IActionResult AddUserEstate([FromBody] EstateTable data)
    {
        History.AddUserEstateHistory(data);
        return Ok();
    }

    [HttpPost("add-vault-log")]
    public IActionResult AddVaultLog([FromBody] VaultLog data)
    {
        History.AddVaultLog(data);
        return Ok();
    }

    [HttpPost("add-atm-log")]
    public IActionResult AddATMLog([FromBody] ATMLog log)
    {
        History.AddAtmLog(log);
        return Ok();
    }
}