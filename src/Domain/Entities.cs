using System.Text.Json;

namespace EventFlow.Domain;

public enum AccountStatus { Pending, Active, Disabled, DeletionScheduled, Deleted }
public enum EventStatus { Draft, Published, Running, Finished, Cancelled }
public enum ModuleAudience { Public, Authenticated, Staff, Organizer }

public sealed class User
{
    public Guid Id { get; set; } = Guid.NewGuid();
    public required string Name { get; set; }
    public required string Email { get; set; }
    public required string NormalizedEmail { get; set; }
    public required string Phone { get; set; }
    public required string NormalizedPhone { get; set; }
    public required string PasswordHash { get; set; }
    public AccountStatus Status { get; set; } = AccountStatus.Pending;
    public string? PhotoUrl { get; set; }
    public DateTimeOffset CreatedAt { get; set; } = DateTimeOffset.UtcNow;
    public DateTimeOffset? DeletionDueAt { get; set; }
    public UserPreference Preference { get; set; } = new();
    public List<UserRole> UserRoles { get; set; } = [];
}

public sealed class Role { public Guid Id { get; set; } = Guid.NewGuid(); public required string Name { get; set; } public List<RolePermission> RolePermissions { get; set; } = []; }
public sealed class Permission { public Guid Id { get; set; } = Guid.NewGuid(); public required string Code { get; set; } public string Description { get; set; } = ""; }
public sealed class UserRole { public Guid UserId { get; set; } public User User { get; set; } = null!; public Guid RoleId { get; set; } public Role Role { get; set; } = null!; }
public sealed class RolePermission { public Guid RoleId { get; set; } public Role Role { get; set; } = null!; public Guid PermissionId { get; set; } public Permission Permission { get; set; } = null!; }
public sealed class UserPreference { public Guid UserId { get; set; } public User User { get; set; } = null!; public bool EmailNotifications { get; set; } = true; public bool PushNotifications { get; set; } = true; public bool InAppNotifications { get; set; } = true; }

public sealed class RefreshToken
{
    public Guid Id { get; set; } = Guid.NewGuid(); public Guid UserId { get; set; } public User User { get; set; } = null!;
    public required string TokenHash { get; set; } public DateTimeOffset ExpiresAt { get; set; }
    public DateTimeOffset CreatedAt { get; set; } = DateTimeOffset.UtcNow; public DateTimeOffset? RevokedAt { get; set; }
    public string? ReplacedByHash { get; set; }
    public bool IsActive => RevokedAt is null && ExpiresAt > DateTimeOffset.UtcNow;
}
public sealed class OneTimeToken
{
    public Guid Id { get; set; } = Guid.NewGuid(); public Guid UserId { get; set; } public User User { get; set; } = null!;
    public required string TokenHash { get; set; } public required string Purpose { get; set; }
    public DateTimeOffset ExpiresAt { get; set; } public DateTimeOffset? UsedAt { get; set; }
}

public sealed class Event
{
    public Guid Id { get; set; } = Guid.NewGuid(); public Guid OrganizerId { get; set; } public User Organizer { get; set; } = null!;
    public required string Name { get; set; } public required string Type { get; set; }
    public string? Description { get; set; } public DateTimeOffset StartsAt { get; set; } public DateTimeOffset? EndsAt { get; set; }
    public required string Location { get; set; } public int EstimatedCapacity { get; set; } public decimal? Budget { get; set; }
    public EventStatus Status { get; set; } = EventStatus.Draft; public DateTimeOffset CreatedAt { get; set; } = DateTimeOffset.UtcNow;
    public List<EventMember> Members { get; set; } = []; public List<EventModule> Modules { get; set; } = [];
}
public sealed class EventMember { public Guid EventId { get; set; } public Event Event { get; set; } = null!; public Guid UserId { get; set; } public User User { get; set; } = null!; public required string Role { get; set; } }
public sealed class Module
{
    public Guid Id { get; set; } = Guid.NewGuid(); public required string Code { get; set; } public required string Name { get; set; }
    public required string Description { get; set; } public required string Category { get; set; } public bool IsAvailable { get; set; } = true;
    public string Icon { get; set; } = "grid"; public string ConfigurationSchemaJson { get; set; } = "{}";
}
public sealed class ModuleTemplate { public Guid Id { get; set; } = Guid.NewGuid(); public required string Code { get; set; } public required string Name { get; set; } public List<ModuleTemplateItem> Items { get; set; } = []; }
public sealed class ModuleTemplateItem { public Guid TemplateId { get; set; } public ModuleTemplate Template { get; set; } = null!; public Guid ModuleId { get; set; } public Module Module { get; set; } = null!; public int DisplayOrder { get; set; } public string ConfigurationJson { get; set; } = "{}"; }
public sealed class ModuleDependency { public Guid ModuleId { get; set; } public Module Module { get; set; } = null!; public Guid RequiredModuleId { get; set; } public Module RequiredModule { get; set; } = null!; }
public sealed class EventModule
{
    public Guid EventId { get; set; } public Event Event { get; set; } = null!; public Guid ModuleId { get; set; } public Module Module { get; set; } = null!;
    public bool IsEnabled { get; set; } = true; public int DisplayOrder { get; set; } public bool IsFeatured { get; set; }
    public ModuleAudience Audience { get; set; } = ModuleAudience.Public; public string ConfigurationJson { get; set; } = "{}";
    public bool HasValidConfiguration() { try { JsonDocument.Parse(ConfigurationJson); return true; } catch { return false; } }
}

public static class EventTransitions
{
    private static readonly Dictionary<EventStatus, EventStatus[]> Allowed = new()
    {
        [EventStatus.Draft] = [EventStatus.Published, EventStatus.Cancelled],
        [EventStatus.Published] = [EventStatus.Running, EventStatus.Cancelled],
        [EventStatus.Running] = [EventStatus.Finished, EventStatus.Cancelled],
        [EventStatus.Finished] = [], [EventStatus.Cancelled] = []
    };
    public static bool CanTransition(EventStatus from, EventStatus to) => Allowed[from].Contains(to);
    public static IReadOnlyList<EventStatus> Next(EventStatus from) => Allowed[from];
}
