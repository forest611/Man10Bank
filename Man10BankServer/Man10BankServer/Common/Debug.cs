using System.Diagnostics;

namespace Man10BankServer.Common;

public static class Debug
{
    public static void DebugTask(IConfiguration config)
    {

        if ((bool.Parse(config["Debug"] ?? "false")) == false)
        {
            return;
        }

        Task.Run(() =>
        {
            var currentProcess = Process.GetCurrentProcess();

            while (true)
            {
                var cpuUsage = currentProcess.TotalProcessorTime.Ticks / (float)Stopwatch.Frequency;

                Console.WriteLine("CPU使用率: " + cpuUsage.ToString("0.00") + "%");

                Thread.Sleep(1000); // 1秒待機
            }
        });
    }

}