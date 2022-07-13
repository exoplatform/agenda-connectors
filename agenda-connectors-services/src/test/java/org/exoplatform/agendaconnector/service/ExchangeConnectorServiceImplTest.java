package org.exoplatform.agendaconnector.service;

import junit.framework.TestCase;
import org.exoplatform.agenda.exception.AgendaException;
import org.exoplatform.agenda.model.Calendar;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.RemoteProvider;
import org.exoplatform.agenda.rest.model.EventEntity;
import org.exoplatform.agenda.service.AgendaCalendarService;
import org.exoplatform.agenda.service.AgendaEventService;
import org.exoplatform.agenda.service.AgendaRemoteEventService;
import org.exoplatform.agenda.util.AgendaDateUtils;
import org.exoplatform.agendaconnector.model.ExchangeUserSetting;
import org.exoplatform.agendaconnector.storage.ExchangeConnectorStorage;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.hibernate.ObjectNotFoundException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.*;


public class ExchangeConnectorServiceImplTest  extends TestCase {


    private ExchangeConnectorService exchangeConnectorService;

    private IdentityManager identityManager;

    private AgendaCalendarService agendaCalendarService;

    private AgendaEventService agendaEventService;
    private PortalContainer          container;

    private AgendaRemoteEventService agendaRemoteEventService;


    @Before
    public void setUp() throws Exception {
        container = PortalContainer.getInstance();
        exchangeConnectorService = container.getComponentInstanceOfType(ExchangeConnectorService.class);
        agendaEventService = container.getComponentInstanceOfType(AgendaEventService.class);
        identityManager = container.getComponentInstanceOfType(IdentityManager.class);
        agendaCalendarService = container.getComponentInstanceOfType(AgendaCalendarService.class);
        agendaRemoteEventService = container.getComponentInstanceOfType(AgendaRemoteEventService.class);
        begin();
    }


    @Test
    public void testPushEventToExchange(){
      try {
        //Given
        System.setProperty("exo.exchange.server.url", "https://acc-ad.exoplatform.org");
        ExchangeUserSetting exchangeUserSetting = new ExchangeUserSetting();
        exchangeUserSetting.setUsername("azayati");
        exchangeUserSetting.setPassword("Root@1234");
        Identity testuser1Identity = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, "testuser1");
        long userIdentityId = Long.parseLong(testuser1Identity.getId());
        ZoneId dstTimeZone = ZoneId.of("Europe/Paris");
        ZonedDateTime startDate = ZonedDateTime.of(LocalDate.now(), LocalTime.of(10, 0), dstTimeZone).withZoneSameInstant(dstTimeZone);
        ZonedDateTime endDate = startDate.plusHours(1);
        RemoteProvider remoteProvider = new RemoteProvider();
        remoteProvider.setName("agenda.exchangeCalendar");
        //When

        RemoteProvider remoteProvider1 = agendaRemoteEventService.saveRemoteProvider(remoteProvider);
        Calendar calendar = agendaCalendarService.createCalendar(new Calendar(0,
                    userIdentityId,
                    false,
                    null,
                    "calendarDescription",
                    null,
                    null,
                    "calendarColor",
                    null));

        Event event = new Event();
        event.setSummary("push created event");
        event.setStart(startDate);
        event.setEnd(endDate);
        event.setCalendarId(calendar.getId());
        Event createdEvent = agendaEventService.createEvent(event,
                    null,
                    null,
                    null,
                    null,
                    null,
                    true,
                    userIdentityId);
        //Then
        assertNotNull(createdEvent);

        //When
        EventEntity eventEntity = new EventEntity();
        eventEntity.setId(createdEvent.getId());
        eventEntity.setSummary(createdEvent.getSummary());
        eventEntity.setStart(AgendaDateUtils.toRFC3339Date(createdEvent.getStart()));
        eventEntity.setEnd(AgendaDateUtils.toRFC3339Date(createdEvent.getEnd()));
        eventEntity.setRemoteProviderId(remoteProvider1.getId());
        eventEntity.setRemoteProviderName(remoteProvider1.getName());

        exchangeConnectorService.createExchangeSetting(exchangeUserSetting, userIdentityId);
        exchangeConnectorService.pushEventToExchange(userIdentityId, eventEntity, dstTimeZone);
        //Then
        EventEntity pushedEvent = exchangeConnectorService.getRemoteEventById(createdEvent.getId(), userIdentityId, dstTimeZone);
        assertNotNull(pushedEvent);
        assertEquals(pushedEvent.getSummary(),"push created event");
        assertEquals(pushedEvent.getStart(), AgendaDateUtils.toRFC3339Date(startDate));
        assertEquals(pushedEvent.getEnd(), AgendaDateUtils.toRFC3339Date(endDate));
        eventEntity.setSummary("push updated event");
        //When
        exchangeConnectorService.pushEventToExchange(userIdentityId, eventEntity, dstTimeZone);
        EventEntity updatedEvent = exchangeConnectorService.getRemoteEventById(createdEvent.getId(), userIdentityId, dstTimeZone);
        //Then
        assertNotNull(updatedEvent);
        assertEquals(updatedEvent.getSummary(),"push updated event");

      } catch (IllegalAccessException | AgendaException e) {
        throw new RuntimeException(e);
      }
    }

    private void begin() {
        ExoContainerContext.setCurrentContainer(container);
        RequestLifeCycle.begin(container);
    }

    private void end() {
        RequestLifeCycle.end();
    }

    @After
    public void tearDown() throws ObjectNotFoundException {
        end();
    }
}
