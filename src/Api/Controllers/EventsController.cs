using EventFlow.Application;
using EventFlow.Domain;
using EventFlow.Infrastructure;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace EventFlow.Api.Controllers;
[Authorize,ApiController,Route("api/events")]
public sealed class EventsController(EventFlowDbContext db,ICurrentUser current) : ControllerBase
{
    [HttpGet] public async Task<IReadOnlyList<EventDto>> List(CancellationToken ct)=>await Accessible().OrderByDescending(x=>x.StartsAt).Select(x=>Map(x)).ToListAsync(ct);
    [HttpGet("{id:guid}")] public async Task<EventDto> Get(Guid id,CancellationToken ct)=>Map(await GetAccessible(id,ct));
    [HttpPost,Authorize(Policy="Organizer")] public async Task<EventDto> Create(CreateEventRequest r,CancellationToken ct){Validate(r.Name,r.StartsAt,r.EndsAt,r.Location,r.EstimatedCapacity);var e=new Event{OrganizerId=current.Id!.Value,Name=r.Name.Trim(),Type=r.Type,Description=r.Description,StartsAt=r.StartsAt,EndsAt=r.EndsAt,Location=r.Location.Trim(),EstimatedCapacity=r.EstimatedCapacity,Budget=r.Budget};db.Events.Add(e);await db.SaveChangesAsync(ct);if(!string.IsNullOrWhiteSpace(r.TemplateCode))await ApplyTemplate(e,r.TemplateCode,ct);return Map(e);}
    [HttpPut("{id:guid}")] public async Task<EventDto> Update(Guid id,UpdateEventRequest r,CancellationToken ct){var e=await Owned(id,ct);if(e.Status is EventStatus.Finished or EventStatus.Cancelled)throw new AppException(409,"event_locked","El evento ya no puede editarse.");Validate(r.Name,r.StartsAt,r.EndsAt,r.Location,r.EstimatedCapacity);e.Name=r.Name.Trim();e.Description=r.Description;e.StartsAt=r.StartsAt;e.EndsAt=r.EndsAt;e.Location=r.Location.Trim();e.EstimatedCapacity=r.EstimatedCapacity;e.Budget=r.Budget;await db.SaveChangesAsync(ct);return Map(e);}
    [HttpPut("{id:guid}/state")] public async Task<EventDto> State(Guid id,[FromBody]ChangeStateRequest r,CancellationToken ct){var e=await Owned(id,ct);if(!Enum.TryParse<EventStatus>(r.Status,true,out var next)||!EventTransitions.CanTransition(e.Status,next))throw new AppException(409,"invalid_transition","La transición de estado no está permitida.");if(next==EventStatus.Published){var issues=await ValidateModules(id,ct);if(issues.Count>0)throw new AppException(409,"invalid_module_configuration",string.Join(" ",issues.Select(x=>x.Message)));if(!await db.EventModules.AnyAsync(x=>x.EventId==id&&x.IsEnabled,ct))throw new AppException(409,"modules_required","Habilita al menos un módulo antes de publicar.");}e.Status=next;await db.SaveChangesAsync(ct);return Map(e);}
    [HttpGet("{id:guid}/dashboard")] public async Task<object> Dashboard(Guid id,CancellationToken ct){var e=await GetAccessible(id,ct);var active=await db.EventModules.CountAsync(x=>x.EventId==id&&x.IsEnabled,ct);return new{Event=Map(e),ActiveModules=active,TotalModules=await db.EventModules.CountAsync(x=>x.EventId==id,ct),CanManage=e.OrganizerId==current.Id};}
    internal IQueryable<Event> Accessible()=>db.Events.Where(x=>x.OrganizerId==current.Id||x.Members.Any(m=>m.UserId==current.Id));
    internal async Task<Event> GetAccessible(Guid id,CancellationToken ct)=>await Accessible().SingleOrDefaultAsync(x=>x.Id==id,ct)??throw new AppException(404,"event_not_found","Evento no encontrado.");
    internal async Task<Event> Owned(Guid id,CancellationToken ct)=>await db.Events.SingleOrDefaultAsync(x=>x.Id==id&&x.OrganizerId==current.Id,ct)??throw new AppException(404,"event_not_found","Evento no encontrado.");
    internal async Task ApplyTemplate(Event e,string code,CancellationToken ct){var template=await db.ModuleTemplates.Include(x=>x.Items).SingleOrDefaultAsync(x=>x.Code==code.ToUpper(),ct)??throw new AppException(404,"template_not_found","Plantilla no encontrada.");db.EventModules.RemoveRange(db.EventModules.Where(x=>x.EventId==e.Id));db.EventModules.AddRange(template.Items.Select(x=>new EventModule{EventId=e.Id,ModuleId=x.ModuleId,DisplayOrder=x.DisplayOrder,ConfigurationJson=x.ConfigurationJson}));await db.SaveChangesAsync(ct);}
    internal async Task<List<ValidationIssue>> ValidateModules(Guid id,CancellationToken ct){var active=await db.EventModules.Where(x=>x.EventId==id&&x.IsEnabled).Select(x=>x.ModuleId).ToListAsync(ct);var deps=await db.ModuleDependencies.Include(x=>x.Module).Include(x=>x.RequiredModule).Where(x=>active.Contains(x.ModuleId)&&!active.Contains(x.RequiredModuleId)).ToListAsync(ct);return deps.Select(x=>new ValidationIssue("missing_dependency",$"{x.Module.Code} requiere {x.RequiredModule.Code}.",[x.Module.Code,x.RequiredModule.Code])).ToList();}
    private static void Validate(string name,DateTimeOffset starts,DateTimeOffset? ends,string location,int capacity){if(string.IsNullOrWhiteSpace(name)||string.IsNullOrWhiteSpace(location)||capacity<1||ends<=starts)throw new AppException(400,"invalid_event","Revisa nombre, ubicación, capacidad y fechas.");}
    private static EventDto Map(Event x)=>new(x.Id,x.Name,x.Type,x.Description,x.StartsAt,x.EndsAt,x.Location,x.EstimatedCapacity,x.Budget,x.Status.ToString(),EventTransitions.Next(x.Status).Select(s=>s.ToString()).ToList());
}
public record ChangeStateRequest(string Status);
