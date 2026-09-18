using EventFlow.Application;
using EventFlow.Domain;
using EventFlow.Infrastructure;

namespace EventFlow.Tests;
public sealed class DomainRulesTests
{
    [Theory]
    [InlineData(EventStatus.Draft,EventStatus.Published,true)]
    [InlineData(EventStatus.Draft,EventStatus.Finished,false)]
    [InlineData(EventStatus.Published,EventStatus.Running,true)]
    [InlineData(EventStatus.Finished,EventStatus.Draft,false)]
    public void Event_transitions_are_enforced(EventStatus from,EventStatus to,bool expected)=>Assert.Equal(expected,EventTransitions.CanTransition(from,to));
    [Theory][InlineData("Password1!",true)][InlineData("short",false)][InlineData("alllowercase1!",false)][InlineData("NoNumber!!",false)]
    public void Password_policy_is_enforced(string password,bool expected)=>Assert.Equal(expected,InputValidation.ValidPassword(password));
    [Fact] public void Passwords_are_salted_and_verifiable(){var service=new PasswordService();var a=service.Hash("ValidPassword1!");var b=service.Hash("ValidPassword1!");Assert.NotEqual(a,b);Assert.True(service.Verify(a,"ValidPassword1!"));Assert.False(service.Verify(a,"WrongPassword1!"));}
    [Fact] public void Disabled_module_preserves_configuration(){var module=new EventModule{ConfigurationJson="{\"limit\":20}",IsEnabled=true};module.IsEnabled=false;Assert.Equal("{\"limit\":20}",module.ConfigurationJson);Assert.True(module.HasValidConfiguration());}
}
