package com.eventflow.eventflow_api.sport

import com.eventflow.eventflow_api.auth.domain.User
import com.eventflow.eventflow_api.auth.infrastructure.persistence.UserRepository
import com.eventflow.eventflow_api.event.application.ConfigureModuleRequest
import com.eventflow.eventflow_api.event.application.CreateEventRequest
import com.eventflow.eventflow_api.event.application.EventService
import com.eventflow.eventflow_api.module.application.ModuleDataService
import com.eventflow.eventflow_api.module.application.ModuleRecordRequest
import com.eventflow.eventflow_api.module.application.ModuleRecordUpdate
import com.eventflow.eventflow_api.module.domain.ModuleCatalog
import com.eventflow.eventflow_api.module.infrastructure.persistence.ModuleCatalogRepository
import com.eventflow.eventflow_api.shared.application.error.ConflictException
import com.eventflow.eventflow_api.shared.application.error.BadRequestException
import com.eventflow.eventflow_api.sport.application.MatchResultRequest
import com.eventflow.eventflow_api.sport.application.MatchResultService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Instant
import java.util.UUID

@SpringBootTest class MatchResultServiceIntegrationTest {
    @Autowired lateinit var events:EventService
    @Autowired lateinit var modules:ModuleDataService
    @Autowired lateinit var results:MatchResultService
    @Autowired lateinit var users:UserRepository
    @Autowired lateinit var catalog:ModuleCatalogRepository

    @Test fun `winners advance into configured bracket slots`() {
        val owner=requireNotNull(users.save(User(name="Owner",email="sport-${UUID.randomUUID()}@example.com",passwordHash="unused")).id)
        val eventId=events.create(owner,CreateEventRequest("Tournament","CUSTOM",Instant.now().plusSeconds(3600))).id
        catalog.save(ModuleCatalog("SPT","Sports","Guest","Matches"))
        events.configureModule(owner,eventId,"SPT",ConfigureModuleRequest())
        val teams=List(4){modules.create(owner,eventId,"SPT","TEAM",ModuleRecordRequest(title="Team $it")).id}
        val final=modules.create(owner,eventId,"SPT","MATCH",ModuleRecordRequest(title="Final",payload=mapOf("round" to 2)))
        val semiA=modules.create(owner,eventId,"SPT","MATCH",ModuleRecordRequest(title="Semi A",payload=mapOf(
            "round" to 1,"participantAId" to teams[0].toString(),"participantBId" to teams[1].toString(),
            "nextMatchId" to final.id.toString(),"nextSlot" to "A")))
        val semiB=modules.create(owner,eventId,"SPT","MATCH",ModuleRecordRequest(title="Semi B",payload=mapOf(
            "round" to 1,"participantAId" to teams[2].toString(),"participantBId" to teams[3].toString(),
            "nextMatchId" to final.id.toString(),"nextSlot" to "B")))
        assertThrows(BadRequestException::class.java){modules.update(owner,eventId,semiA.id,ModuleRecordUpdate(payload=mapOf("scoreA" to 99)))}
        assertEquals(teams[0],results.report(owner,eventId,semiA.id,MatchResultRequest(2,1)).winnerId)
        assertEquals(teams[3],results.report(owner,eventId,semiB.id,MatchResultRequest(0,3)).winnerId)
        assertEquals(teams[0].toString(),modules.get(owner,eventId,final.id).payload["participantAId"])
        assertEquals(teams[3].toString(),modules.get(owner,eventId,final.id).payload["participantBId"])
        assertThrows(ConflictException::class.java){results.report(owner,eventId,semiA.id,MatchResultRequest(1,0))}
    }
}
