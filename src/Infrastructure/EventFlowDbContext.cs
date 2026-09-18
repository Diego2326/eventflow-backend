using EventFlow.Domain;
using Microsoft.EntityFrameworkCore;

namespace EventFlow.Infrastructure;

public sealed class EventFlowDbContext(DbContextOptions<EventFlowDbContext> options) : DbContext(options)
{
    public DbSet<User> Users => Set<User>(); public DbSet<Role> Roles => Set<Role>(); public DbSet<Permission> Permissions => Set<Permission>();
    public DbSet<RefreshToken> RefreshTokens => Set<RefreshToken>(); public DbSet<OneTimeToken> OneTimeTokens => Set<OneTimeToken>();
    public DbSet<Event> Events => Set<Event>(); public DbSet<Module> Modules => Set<Module>(); public DbSet<ModuleTemplate> ModuleTemplates => Set<ModuleTemplate>();
    public DbSet<EventModule> EventModules => Set<EventModule>(); public DbSet<ModuleDependency> ModuleDependencies => Set<ModuleDependency>();

    protected override void OnModelCreating(ModelBuilder b)
    {
        b.HasPostgresEnum<AccountStatus>(); b.HasPostgresEnum<EventStatus>(); b.HasPostgresEnum<ModuleAudience>();
        b.Entity<User>().HasIndex(x => x.NormalizedEmail).IsUnique(); b.Entity<User>().HasIndex(x => x.NormalizedPhone).IsUnique();
        b.Entity<UserPreference>().HasKey(x => x.UserId); b.Entity<User>().HasOne(x => x.Preference).WithOne(x => x.User).HasForeignKey<UserPreference>(x => x.UserId).OnDelete(DeleteBehavior.Cascade);
        b.Entity<UserRole>().HasKey(x => new { x.UserId, x.RoleId }); b.Entity<RolePermission>().HasKey(x => new { x.RoleId, x.PermissionId });
        b.Entity<Role>().HasIndex(x => x.Name).IsUnique(); b.Entity<Permission>().HasIndex(x => x.Code).IsUnique();
        b.Entity<RefreshToken>().HasIndex(x => x.TokenHash).IsUnique(); b.Entity<OneTimeToken>().HasIndex(x => x.TokenHash).IsUnique();
        b.Entity<Event>().HasIndex(x => new { x.OrganizerId, x.StartsAt }); b.Entity<Event>().Property(x => x.Budget).HasPrecision(14, 2);
        b.Entity<EventMember>().HasKey(x => new { x.EventId, x.UserId });
        b.Entity<Module>().HasIndex(x => x.Code).IsUnique(); b.Entity<ModuleTemplate>().HasIndex(x => x.Code).IsUnique();
        b.Entity<ModuleTemplateItem>().HasKey(x => new { x.TemplateId, x.ModuleId });
        b.Entity<EventModule>().HasKey(x => new { x.EventId, x.ModuleId }); b.Entity<EventModule>().HasIndex(x => new { x.EventId, x.DisplayOrder }).IsUnique();
        b.Entity<ModuleDependency>().HasKey(x => new { x.ModuleId, x.RequiredModuleId });
        b.Entity<ModuleDependency>().HasOne(x => x.RequiredModule).WithMany().HasForeignKey(x => x.RequiredModuleId).OnDelete(DeleteBehavior.Restrict);
    }
}
