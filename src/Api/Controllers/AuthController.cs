using EventFlow.Application;
using EventFlow.Domain;
using EventFlow.Infrastructure;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace EventFlow.Api.Controllers;

[ApiController, Route("api/auth")]
public sealed class AuthController(EventFlowDbContext db, IPasswordService passwords, ITokenService tokens, INotificationSender notifications, IConfiguration config, ICurrentUser current) : ControllerBase
{
    [HttpPost("register")]
    public async Task<IActionResult> Register(RegisterRequest request, CancellationToken ct)
    {
        var email=request.Email.Trim().ToUpperInvariant(); var phone=NormalizePhone(request.Phone);
        if (string.IsNullOrWhiteSpace(request.Name)||!InputValidation.ValidEmail(request.Email)||string.IsNullOrWhiteSpace(phone)) throw new AppException(400,"invalid_input","Nombre, correo y teléfono son obligatorios.");
        if (!InputValidation.ValidPassword(request.Password)) throw new AppException(400,"weak_password","La contraseña requiere 10 caracteres, mayúscula, minúscula, número y símbolo.");
        if(await db.Users.AnyAsync(x=>x.NormalizedEmail==email||x.NormalizedPhone==phone,ct)) throw new AppException(409,"account_exists","El correo o teléfono ya está registrado.");
        var role=await db.Roles.SingleAsync(x=>x.Name=="Organizer",ct); var user=new User{Name=request.Name.Trim(),Email=request.Email.Trim().ToLowerInvariant(),NormalizedEmail=email,Phone=request.Phone.Trim(),NormalizedPhone=phone,PasswordHash=passwords.Hash(request.Password)};
        user.UserRoles.Add(new UserRole{User=user,Role=role}); user.Preference.User=user; db.Users.Add(user); var raw=tokens.CreateOpaqueToken(); db.OneTimeTokens.Add(new OneTimeToken{User=user,TokenHash=tokens.HashToken(raw),Purpose="verify",ExpiresAt=DateTimeOffset.UtcNow.AddHours(24)}); await db.SaveChangesAsync(ct); await notifications.SendVerificationAsync(user.Email,raw,ct);
        return Accepted(new{message="Cuenta creada. Revisa tu correo para activarla.", developmentToken=Environment.GetEnvironmentVariable("ASPNETCORE_ENVIRONMENT")=="Development"?raw:null});
    }
    [HttpPost("verify")]
    public async Task<IActionResult> Verify([FromBody] TokenRequest request,CancellationToken ct) { var hash=tokens.HashToken(request.Token); var token=await db.OneTimeTokens.Include(x=>x.User).SingleOrDefaultAsync(x=>x.TokenHash==hash&&x.Purpose=="verify",ct)??throw new AppException(400,"invalid_token","Token inválido."); if(token.UsedAt is not null||token.ExpiresAt<=DateTimeOffset.UtcNow) throw new AppException(400,"expired_token","El token expiró o ya fue utilizado."); token.UsedAt=DateTimeOffset.UtcNow; token.User.Status=AccountStatus.Active; await db.SaveChangesAsync(ct); return Ok(new{message="Cuenta activada."}); }
    [HttpPost("login")]
    public async Task<TokenPair> Login(LoginRequest request,CancellationToken ct) { var id=request.Identifier.Trim().ToUpperInvariant(); var user=await UserQuery().SingleOrDefaultAsync(x=>x.NormalizedEmail==id||x.NormalizedPhone==NormalizePhone(request.Identifier),ct); if(user is null||!passwords.Verify(user.PasswordHash,request.Password)) throw new AppException(401,"invalid_credentials","Credenciales inválidas."); if(user.Status!=AccountStatus.Active) throw new AppException(403,"account_inactive","La cuenta no está activa."); return await Issue(user,ct); }
    [HttpPost("refresh")]
    public async Task<TokenPair> Refresh(RefreshRequest request,CancellationToken ct) { var hash=tokens.HashToken(request.RefreshToken); var stored=await db.RefreshTokens.Include(x=>x.User).ThenInclude(x=>x.UserRoles).ThenInclude(x=>x.Role).ThenInclude(x=>x.RolePermissions).ThenInclude(x=>x.Permission).SingleOrDefaultAsync(x=>x.TokenHash==hash,ct)??throw new AppException(401,"invalid_refresh_token","Sesión inválida."); if(!stored.IsActive||stored.User.Status!=AccountStatus.Active) throw new AppException(401,"invalid_refresh_token","Sesión expirada o revocada."); stored.RevokedAt=DateTimeOffset.UtcNow; var pair=await Issue(stored.User,ct); stored.ReplacedByHash=tokens.HashToken(pair.RefreshToken); await db.SaveChangesAsync(ct); return pair; }
    [Authorize,HttpPost("logout")]
    public async Task<IActionResult> Logout(RefreshRequest request,CancellationToken ct) { var hash=tokens.HashToken(request.RefreshToken); var stored=await db.RefreshTokens.SingleOrDefaultAsync(x=>x.UserId==current.Id&&x.TokenHash==hash,ct); if(stored is not null) stored.RevokedAt=DateTimeOffset.UtcNow; await db.SaveChangesAsync(ct); return NoContent(); }
    [Authorize,HttpPost("logout-all")]
    public async Task<IActionResult> LogoutAll(CancellationToken ct) { await db.RefreshTokens.Where(x=>x.UserId==current.Id&&x.RevokedAt==null).ExecuteUpdateAsync(x=>x.SetProperty(t=>t.RevokedAt,DateTimeOffset.UtcNow),ct); return NoContent(); }
    [HttpPost("forgot-password")]
    public async Task<IActionResult> Forgot(ForgotPasswordRequest request,CancellationToken ct) { var user=await db.Users.SingleOrDefaultAsync(x=>x.NormalizedEmail==request.Email.Trim().ToUpperInvariant(),ct); if(user is not null){ await db.OneTimeTokens.Where(x=>x.UserId==user.Id&&x.Purpose=="reset"&&x.UsedAt==null).ExecuteUpdateAsync(x=>x.SetProperty(t=>t.UsedAt,DateTimeOffset.UtcNow),ct); var raw=tokens.CreateOpaqueToken(); db.OneTimeTokens.Add(new OneTimeToken{User=user,TokenHash=tokens.HashToken(raw),Purpose="reset",ExpiresAt=DateTimeOffset.UtcNow.AddHours(1)}); await db.SaveChangesAsync(ct); await notifications.SendPasswordResetAsync(user.Email,raw,ct); } return Accepted(new{message="Si la cuenta existe, recibirás instrucciones."}); }
    [HttpPost("reset-password")]
    public async Task<IActionResult> Reset(ResetPasswordRequest request,CancellationToken ct) { if(!InputValidation.ValidPassword(request.NewPassword)) throw new AppException(400,"weak_password","La contraseña no cumple los requisitos."); var hash=tokens.HashToken(request.Token); var token=await db.OneTimeTokens.Include(x=>x.User).SingleOrDefaultAsync(x=>x.TokenHash==hash&&x.Purpose=="reset",ct)??throw new AppException(400,"invalid_token","Token inválido."); if(token.UsedAt is not null||token.ExpiresAt<=DateTimeOffset.UtcNow) throw new AppException(400,"expired_token","El token expiró o ya fue utilizado."); token.UsedAt=DateTimeOffset.UtcNow; token.User.PasswordHash=passwords.Hash(request.NewPassword); await RevokeAll(token.UserId,ct); await db.SaveChangesAsync(ct); return Ok(new{message="Contraseña actualizada."}); }
    [Authorize,HttpPost("change-password")]
    public async Task<IActionResult> Change(ChangePasswordRequest request,CancellationToken ct) { var user=await db.Users.FindAsync([current.Id!.Value],ct)??throw new AppException(404,"not_found","Usuario no encontrado."); if(!passwords.Verify(user.PasswordHash,request.CurrentPassword)) throw new AppException(400,"invalid_password","La contraseña actual es incorrecta."); if(!InputValidation.ValidPassword(request.NewPassword)) throw new AppException(400,"weak_password","La contraseña no cumple los requisitos."); user.PasswordHash=passwords.Hash(request.NewPassword); if(request.LogoutAll) await RevokeAll(user.Id,ct); await db.SaveChangesAsync(ct); return NoContent(); }
    [HttpGet("google")]
    public IActionResult Google() => string.IsNullOrWhiteSpace(config["Google:ClientId"]) ? StatusCode(503,new{error=new{code="oauth_not_configured",message="Google OAuth requiere credenciales de configuración."}}) : Redirect("/api/auth/google/challenge");
    private IQueryable<User> UserQuery()=>db.Users.Include(x=>x.UserRoles).ThenInclude(x=>x.Role).ThenInclude(x=>x.RolePermissions).ThenInclude(x=>x.Permission);
    private async Task<TokenPair> Issue(User user,CancellationToken ct){ var roles=user.UserRoles.Select(x=>x.Role.Name).Distinct(); var permissions=user.UserRoles.SelectMany(x=>x.Role.RolePermissions).Select(x=>x.Permission.Code).Distinct(); var access=tokens.CreateAccessToken(user,roles,permissions,out var expires); var refresh=tokens.CreateOpaqueToken(); db.RefreshTokens.Add(new RefreshToken{UserId=user.Id,TokenHash=tokens.HashToken(refresh),ExpiresAt=DateTimeOffset.UtcNow.AddDays(config.GetValue("Jwt:RefreshDays",30))}); await db.SaveChangesAsync(ct); return new(access,refresh,expires); }
    private async Task RevokeAll(Guid id,CancellationToken ct)=>await db.RefreshTokens.Where(x=>x.UserId==id&&x.RevokedAt==null).ExecuteUpdateAsync(x=>x.SetProperty(t=>t.RevokedAt,DateTimeOffset.UtcNow),ct);
    private static string NormalizePhone(string value)=>new(value.Where(char.IsDigit).ToArray());
}
public record TokenRequest(string Token);
