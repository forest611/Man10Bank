using System.Collections.Concurrent;
using System.Text;

namespace Man10BankServer.Common;

public static class Authentication
{

    public const string Admin = "Admin";
    public const string User = "User";
    public const string Failed = "Failed";
    
    private static readonly ConcurrentDictionary<string, string?> UserAndPass  = new ();

    public static void LoadConfig(IConfiguration config)
    {
        UserAndPass.Clear();
        UserAndPass[User] = config["Http:UserPassword"] ?? "";
        UserAndPass[Admin] = config["Http:AdminPassword"] ?? "";
    }

    public static bool HasUserPermission(HttpContext context) => GetAuthorizedUser(context) != Failed;
    public static bool HasAdminPermission(HttpContext context) => GetAuthorizedUser(context) == Admin;
    
    public static string GetAuthorizedUser(HttpContext context)
    {
        var authHeader = context.Request.Headers["Authorization"].ToString();
        
        //Basic <Base64>
        if (authHeader.StartsWith("Basic "))
        {
            // ヘッダーからクレデンシャルを取得
            var encodedUsernamePassword = authHeader.Split(' ', 2, StringSplitOptions.RemoveEmptyEntries)[1]?.Trim();

            if (encodedUsernamePassword == null)
            {
                return Failed;
            }
            var decodedUsernamePassword = Encoding.UTF8.GetString(Convert.FromBase64String(encodedUsernamePassword));
            
            var username = decodedUsernamePassword.Split(':', 2)[0];
            var password = decodedUsernamePassword.Split(':', 2)[1];

            if (!UserAndPass.TryGetValue(username, out var correctPass))
            {
                return Failed;
            }

            // ユーザー名とパスワードの検証
            if (correctPass == password)
            {
                // 認証成功
                var claims = new[] { new System.Security.Claims.Claim("name", username) };
                var identity = new System.Security.Claims.ClaimsIdentity(claims, "Basic");
                context.User = new System.Security.Claims.ClaimsPrincipal(identity);

                return username;
            }

            // 認証失敗
            context.Response.Headers["WWW-Authenticate"] = "Basic";
            context.Response.StatusCode = 401; // Unauthorized
            return Failed;
        }

        // ヘッダーがない場合
        context.Response.Headers["WWW-Authenticate"] = "Basic";
        context.Response.StatusCode = 401; // Unauthorized
        return Failed;
    }
}