namespace Man10BankServer.Common;

public class Status
{

    public static Status NowStatus { get; set; } = new();
    
    public bool EnableDealBank { get; set; }
    public bool EnableATM { get; set; }
    public bool EnableCheque { get; set; }
    public bool EnableLocalLoan { get; set; }
    public bool EnableServerLoan { get; set; }
    public bool EnableAccessUserServer { get; set; }

}