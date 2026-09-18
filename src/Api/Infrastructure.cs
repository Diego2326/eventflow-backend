using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using System.Security.Cryptography;
using System.Text;
using EventFlow.Application;
using EventFlow.Domain;
using Microsoft.IdentityModel.Tokens;

namespace EventFlow.Api;
public sealed class CurrentUser(IHttpContextAccessor context) : ICurrentUser
{
    public Guid? Id => Guid.TryParse(context.HttpContext?.User.FindFirstValue(ClaimTypes.NameIdentifier), out var id) ? id : null;
    public bool IsAuthenticated => context.HttpContext?.User.Identity?.IsAuthenticated == true;
}
public sealed class JwtTokenService(IConfiguration config) : ITokenService
{
    public string CreateAccessToken(User user, IEnumerable<string> roles, IEnumerable<string> permissions, out DateTimeOffset expiresAt)
    {
        expiresAt=DateTimeOffset.UtcNow.AddMinutes(config.GetValue("Jwt:AccessMinutes",15)); var claims=new List<Claim>{new(ClaimTypes.NameIdentifier,user.Id.ToString()),new(ClaimTypes.Name,user.Name),new(ClaimTypes.Email,user.Email)};
        claims.AddRange(roles.Select(x=>new Claim(ClaimTypes.Role,x))); claims.AddRange(permissions.Select(x=>new Claim("permission",x)));
        var key=new SymmetricSecurityKey(Encoding.UTF8.GetBytes(config["Jwt:Key"]??"development-only-key-change-me-32-chars"));
        return new JwtSecurityTokenHandler().WriteToken(new JwtSecurityToken(config["Jwt:Issuer"]??"EventFlow",config["Jwt:Audience"]??"EventFlowClients",claims,expires:expiresAt.UtcDateTime,signingCredentials:new SigningCredentials(key,SecurityAlgorithms.HmacSha256)));
    }
    public string CreateOpaqueToken()=>Convert.ToBase64String(RandomNumberGenerator.GetBytes(48)).Replace('+','-').Replace('/','_').TrimEnd('=');
    public string HashToken(string token)=>Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(token)));
}
public sealed class ErrorHandlingMiddleware(RequestDelegate next, ILogger<ErrorHandlingMiddleware> log)
{
    public async Task Invoke(HttpContext context) { try { await next(context); } catch(AppException ex) { context.Response.StatusCode=ex.StatusCode; await context.Response.WriteAsJsonAsync(new{error=new{code=ex.Code,message=ex.Message}}); } catch(Exception ex) { log.LogError(ex,"Unhandled error for {Path}",context.Request.Path); context.Response.StatusCode=500; await context.Response.WriteAsJsonAsync(new{error=new{code="internal_error",message="Ocurrió un error inesperado."}}); } }
}
