using EventFlow.Application;
using EventFlow.Domain;
using EventFlow.Infrastructure;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace EventFlow.Api.Controllers;
[Authorize,ApiController,Route("api/users/me")]
public sealed class UsersController(EventFlowDbContext db,ICurrentUser current,IFileStorage storage) : ControllerBase
{
    [HttpGet] public async Task<ProfileDto> Get(CancellationToken ct)=>Map(await Query().SingleAsync(x=>x.Id==current.Id,ct));
    [HttpPut] public async Task<ProfileDto> Update(UpdateProfileRequest request,CancellationToken ct){ if(string.IsNullOrWhiteSpace(request.Name)||string.IsNullOrWhiteSpace(request.Phone)) throw new AppException(400,"invalid_input","Nombre y teléfono son obligatorios."); var phone=new string(request.Phone.Where(char.IsDigit).ToArray()); if(await db.Users.AnyAsync(x=>x.Id!=current.Id&&x.NormalizedPhone==phone,ct)) throw new AppException(409,"phone_exists","El teléfono ya está registrado."); var user=await Query().SingleAsync(x=>x.Id==current.Id,ct); user.Name=request.Name.Trim();user.Phone=request.Phone.Trim();user.NormalizedPhone=phone;await db.SaveChangesAsync(ct);return Map(user);}
    [HttpPut("preferences")] public async Task<IActionResult> Preferences(NotificationPreferences request,CancellationToken ct){var pref=await db.Set<UserPreference>().SingleAsync(x=>x.UserId==current.Id,ct);pref.EmailNotifications=request.Email;pref.PushNotifications=request.Push;pref.InAppNotifications=request.InApp;await db.SaveChangesAsync(ct);return NoContent();}
    [HttpPost("photo"),RequestSizeLimit(5_000_000)] public async Task<object> Photo(IFormFile file,CancellationToken ct){var user=await db.Users.SingleAsync(x=>x.Id==current.Id,ct);await using var stream=file.OpenReadStream();user.PhotoUrl=await storage.SaveAsync(stream,file.FileName,file.ContentType,ct);await db.SaveChangesAsync(ct);return new{user.PhotoUrl};}
    [HttpPost("deactivate")] public async Task<IActionResult> Deactivate(CancellationToken ct){var user=await db.Users.SingleAsync(x=>x.Id==current.Id,ct);user.Status=AccountStatus.Disabled;await db.RefreshTokens.Where(x=>x.UserId==user.Id&&x.RevokedAt==null).ExecuteUpdateAsync(x=>x.SetProperty(t=>t.RevokedAt,DateTimeOffset.UtcNow),ct);await db.SaveChangesAsync(ct);return NoContent();}
    [HttpDelete] public async Task<IActionResult> Delete(CancellationToken ct){var user=await db.Users.SingleAsync(x=>x.Id==current.Id,ct);user.Status=AccountStatus.DeletionScheduled;user.DeletionDueAt=DateTimeOffset.UtcNow.AddDays(30);await db.RefreshTokens.Where(x=>x.UserId==user.Id&&x.RevokedAt==null).ExecuteUpdateAsync(x=>x.SetProperty(t=>t.RevokedAt,DateTimeOffset.UtcNow),ct);await db.SaveChangesAsync(ct);return Accepted(new{user.DeletionDueAt});}
    private IQueryable<User> Query()=>db.Users.Include(x=>x.Preference).Include(x=>x.UserRoles).ThenInclude(x=>x.Role);
    private static ProfileDto Map(User x)=>new(x.Id,x.Name,x.Email,x.Phone,x.PhotoUrl,x.Status.ToString(),x.UserRoles.Select(r=>r.Role.Name).ToList(),new(x.Preference.EmailNotifications,x.Preference.PushNotifications,x.Preference.InAppNotifications));
}
