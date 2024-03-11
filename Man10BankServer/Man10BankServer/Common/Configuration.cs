using Man10BankServer.Model;

namespace Man10BankServer.Common;

public static class Configuration
{
    public static string Man10SystemUrl { get; private set; } = "";

    public static string HttpUsername { get; private set; } = "";
    public static string HttpPassword { get; private set; } = "";


    public static void LoadConfiguration(IConfiguration config)
    {
        Man10SystemUrl = config["Man10SystemURL"] ?? "";
        HttpUsername = config["Http:UserName"] ?? "";
        HttpPassword = config["Http:Password"] ?? "";
        
        BankContext.LoadConfig(config);
        LocalLoan.LoadConfig(config);
        ServerLoan.LoadConfig(config);
    }
}