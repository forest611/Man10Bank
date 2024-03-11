namespace Man10BankServer.Common;

public class Status
{

    public static Status NowStatus { get; set; } = new();

    public bool EnableDealBank { get; set; } = true;
    public bool EnableATM { get; set; } = true;
    public bool EnableCheque { get; set; } = true;
    public bool EnableLocalLoan { get; set; } = true;
    public bool EnableServerLoan { get; set; } = true;
    public bool EnableAccessUserServer { get; set; } = true;

}