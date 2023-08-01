using Man10BankServer.Common;
using Man10BankServer.Model;
using Microsoft.AspNetCore.Mvc;

namespace Man10BankServer.Controllers;

[ApiController]
[Route("[controller]")]
public class HistoryController : ControllerBase
{

    [HttpGet("get-balance-top")]
    public EstateTable[] GetBalanceTop(int record,int skip)
    {
        return History.GetBalanceTop(record,skip).Result;
    }

    [HttpGet("get-loan-top")]
    public ServerLoanTable[] GetLoanTop(int record,int skip)
    {
        return History.GetLoanTop(record,skip).Result;
    }

    [HttpGet("get-user-estate")]
    public EstateTable GetUserEstate(string uuid)
    {
        return History.GetUserEstate(uuid).Result;
    }

    [HttpGet("get-user-estate-history")]
    public EstateHistoryTable[] GetUserEstateHistory(string uuid,int day)
    {
        return History.GetUserEstateHistory(uuid, day).Result;
    }

    [HttpGet("get-server-estate")]
    public ServerEstateHistory GetServerEstate()
    {
        return History.GetServerEstate().Result;
    }

    [HttpGet("get-server-estate-history")]
    public ServerEstateHistory[] GetServerEstateHistory(int day)
    {
        return History.GetServerEstateHistory(day).Result;
    }
    
    [HttpPost("add-user-estate")]
    public void AddUserEstate([FromBody] EstateTable data)
    {
        History.AddUserEstateHistory(data);
    }

    [HttpPost("add-vault-log")]
    public void AddVaultLog([FromBody] VaultLog data)
    {
        History.AddVaultLog(data);
    }

    [HttpPost("add-atm-log")]
    public void AddATMLog([FromBody] ATMLog log)
    {
        History.AddAtmLog(log);
    }
}