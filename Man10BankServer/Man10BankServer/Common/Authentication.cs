using System.Text;

namespace Man10BankServer.Common;

public static class Authentication
{
    public static bool CheckAuthentication(HttpContext context)
    {
        var authHeader = context.Request.Headers["Authorization"].ToString();
        
        //Basic <Base64>
        if (authHeader.StartsWith("Basic "))
        {
            // ヘッダーからクレデンシャルを取得
            var encodedUsernamePassword = authHeader.Split(' ', 2, StringSplitOptions.RemoveEmptyEntries)[1]?.Trim();

            if (encodedUsernamePassword == null)
            {
                return false;
            }
            var decodedUsernamePassword = Encoding.UTF8.GetString(Convert.FromBase64String(encodedUsernamePassword));
            
            var username = decodedUsernamePassword.Split(':', 2)[0];
            var password = decodedUsernamePassword.Split(':', 2)[1];

            // ユーザー名とパスワードの検証
            if (IsAuthorized(username, password))
            {
                // 認証成功
                var claims = new[] { new System.Security.Claims.Claim("name", username) };
                var identity = new System.Security.Claims.ClaimsIdentity(claims, "Basic");
                context.User = new System.Security.Claims.ClaimsPrincipal(identity);

                return true;
            }

            // 認証失敗
            context.Response.Headers["WWW-Authenticate"] = "Basic";
            context.Response.StatusCode = 401; // Unauthorized
            return false;
        }

        // ヘッダーがない場合
        context.Response.Headers["WWW-Authenticate"] = "Basic";
        context.Response.StatusCode = 401; // Unauthorized
        return false;
    }

    private static bool IsAuthorized(string username, string password) =>
        username == Configuration.HttpUsername && password == Configuration.HttpPassword;
}