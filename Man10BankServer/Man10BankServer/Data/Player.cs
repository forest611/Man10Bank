namespace Man10BankServer.Data;

public class Player
{
    public string Name { get; }
    public string Uuid { get; }

    public Player(string name, string uuid)
    {
        Name = name;
        Uuid = uuid;
    }
}