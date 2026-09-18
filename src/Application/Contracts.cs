using EventFlow.Domain;

namespace EventFlow.Application;

public record RegisterRequest(string Name, string Email, string Phone, string Password);
public record LoginRequest(string Identifier, string Password);
public record RefreshRequest(string RefreshToken);
public record ForgotPasswordRequest(string Email);
public record ResetPasswordRequest(string Token, string NewPassword);
public record ChangePasswordRequest(string CurrentPassword, string NewPassword, bool LogoutAll = false);
public record TokenPair(string AccessToken, string RefreshToken, DateTimeOffset AccessExpiresAt);
public record ProfileDto(Guid Id, string Name, string Email, string Phone, string? PhotoUrl, string Status, IReadOnlyList<string> Roles, NotificationPreferences Preferences);
public record UpdateProfileRequest(string Name, string Phone);
public record NotificationPreferences(bool Email, bool Push, bool InApp);
public record CreateEventRequest(string Name, string Type, string? Description, DateTimeOffset StartsAt, DateTimeOffset? EndsAt, string Location, int EstimatedCapacity, decimal? Budget, string? TemplateCode);
public record UpdateEventRequest(string Name, string? Description, DateTimeOffset StartsAt, DateTimeOffset? EndsAt, string Location, int EstimatedCapacity, decimal? Budget);
public record EventDto(Guid Id, string Name, string Type, string? Description, DateTimeOffset StartsAt, DateTimeOffset? EndsAt, string Location, int EstimatedCapacity, decimal? Budget, string Status, IReadOnlyList<string> ValidTransitions);
public record ModuleDto(string Code, string Name, string Description, string Category, string Icon, bool Available, string ConfigurationSchema);
public record EventModuleDto(string Code, string Name, string Category, string Icon, bool Enabled, int Order, bool Featured, string Audience, string Configuration);
public record ConfigureModuleRequest(bool Enabled, bool Featured, string Audience, string Configuration);
public record ReorderModulesRequest(IReadOnlyList<string> Codes);
public record ApplyTemplateRequest(string TemplateCode);
public record TemplateDto(string Code, string Name, IReadOnlyList<string> Modules);
public record ValidationIssue(string Code, string Message, IReadOnlyList<string> RelatedModules);

public interface ICurrentUser { Guid? Id { get; } bool IsAuthenticated { get; } }
public interface ITokenService { string CreateAccessToken(User user, IEnumerable<string> roles, IEnumerable<string> permissions, out DateTimeOffset expiresAt); string CreateOpaqueToken(); string HashToken(string token); }
public interface IPasswordService { string Hash(string password); bool Verify(string hash, string password); }
public interface INotificationSender { Task SendVerificationAsync(string email, string token, CancellationToken ct); Task SendPasswordResetAsync(string email, string token, CancellationToken ct); }
public interface IFileStorage { Task<string> SaveAsync(Stream stream, string fileName, string contentType, CancellationToken ct); }
