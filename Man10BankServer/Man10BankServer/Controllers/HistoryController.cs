using Man10BankServer.Common;
using Man10BankServer.Model;
using Microsoft.AspNetCore.Mvc;

namespace Man10BankServer.Controllers;

[ApiController]
[Route("[controller]")]
public class HistoryController : ControllerBase
{

    [HttpGet("get-balance-top")]
    public EstateTable[] GetBalanceTop(int size)
    {
        return History.GetBalanceTop(size).Result;
    }

    [HttpGet("get-user-estate")]
    public EstateTable GetUserEstate(string uuid)
    {
        return History.GetUserEstate(uuid).Result;
    }

    [HttpGet("get-server-estate")]
    public ServerEstateHistory GetServerEstate()
    {
        return History.GetServerEstate().Result;
    }

    [HttpPost("add-user-estate")]
    public void AddUserEstate([FromBody] EstateTable data)
    {
        History.AddUserEstateHistory(data);
    }
}