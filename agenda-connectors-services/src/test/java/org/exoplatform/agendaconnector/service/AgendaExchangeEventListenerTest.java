package org.exoplatform.agendaconnector.service;

import microsoft.exchange.webservices.data.core.ExchangeService;
import microsoft.exchange.webservices.data.core.service.item.Appointment;
import org.exoplatform.agenda.model.EventAttendee;
import org.exoplatform.agenda.model.EventAttendeeList;
import org.exoplatform.agenda.model.RemoteEvent;
import org.exoplatform.agenda.rest.model.EventEntity;
import org.exoplatform.agenda.service.AgendaEventAttendeeService;
import org.exoplatform.agenda.service.AgendaRemoteEventService;
import org.exoplatform.agenda.util.AgendaDateUtils;
import org.exoplatform.agendaconnector.model.ExchangeUserSetting;
import org.exoplatform.agendaconnector.storage.ExchangeConnectorStorage;
import org.exoplatform.agendaconnector.utils.ExchangeConnectorUtils;
import org.exoplatform.services.listener.Event;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ ExchangeConnectorUtils.class, ExchangeService.class })
public class AgendaExchangeEventListenerTest {

  private ExchangeConnectorService exchangeConnectorService;

  private AgendaRemoteEventService agendaRemoteEventService;

  private ExchangeConnectorStorage exchangeConnectorStorage;

  private AgendaEventAttendeeService agendaEventAttendeeService;

  private ExchangeService exchangeService;

    @Before
    public void setUp() throws Exception {
      agendaEventAttendeeService = mock(AgendaEventAttendeeService.class);
      agendaRemoteEventService = mock(AgendaRemoteEventService.class);
      exchangeConnectorStorage = mock(ExchangeConnectorStorage.class);
      exchangeService = PowerMockito.mock(ExchangeService.class);
      PowerMockito.whenNew(ExchangeService.class).withArguments(any()).thenReturn(exchangeService);
      exchangeConnectorService = new ExchangeConnectorServiceImpl(exchangeConnectorStorage, agendaRemoteEventService);

    }
    @Test
    public void testDeleteExchangeEventForAllParticipants() throws Exception {
      //Given
      ExchangeUserSetting exchangeUserSetting = new ExchangeUserSetting();
      exchangeUserSetting.setUsername("username");
      exchangeUserSetting.setPassword("password");
      when(exchangeConnectorStorage.getExchangeSetting(2)).thenReturn(exchangeUserSetting);
      System.setProperty("exo.exchange.server.url", "server.url");

      RemoteEvent remoteEvent = new RemoteEvent();
      remoteEvent.setEventId(1);
      remoteEvent.setRemoteId("remoteId");
      remoteEvent.setRemoteProviderId(1);
      remoteEvent.setRemoteProviderName("agenda.exchangeCalendar");
      when(agendaRemoteEventService.findRemoteEvent(1, 2)).thenReturn(remoteEvent);
      Appointment appointment = mock(Appointment.class);
      when(exchangeService.bindToItem(any(), any(), any())).thenReturn(appointment);

      AgendaExchangeEventListener agendaExchangeEventListener = new AgendaExchangeEventListener(exchangeConnectorService, agendaEventAttendeeService);
      Event<Long, Long> event = mock(Event.class);

      // When
      EventAttendee eventAttendee1 = new EventAttendee();
      eventAttendee1.setId(1);
      eventAttendee1.setEventId(1);
      eventAttendee1.setIdentityId(1);

      EventAttendee eventAttendee2 = new EventAttendee();
      eventAttendee2.setId(2);
      eventAttendee2.setEventId(1);
      eventAttendee2.setIdentityId(2);

      List<EventAttendee> attendees = new ArrayList<>();
      attendees.add(eventAttendee1);
      attendees.add(eventAttendee2);
      EventAttendeeList eventAttendeeList = new EventAttendeeList(attendees);
      when(agendaEventAttendeeService.getEventAttendees(1)).thenReturn(eventAttendeeList);
      when(event.getSource()).thenReturn(1L);
      when(event.getData()).thenReturn(1L);

      EventEntity eventEntity = new EventEntity();
      eventEntity.setId(1);
      eventEntity.setSummary("deleted event");
      ZoneId dstTimeZone = ZoneId.of("Europe/Paris");
      ZonedDateTime startDate =
              ZonedDateTime.of(LocalDate.now(), LocalTime.of(10, 0), dstTimeZone).withZoneSameInstant(dstTimeZone);
      ZonedDateTime endDate = startDate.plusHours(1);
      eventEntity.setStart(AgendaDateUtils.toRFC3339Date(startDate));
      eventEntity.setEnd(AgendaDateUtils.toRFC3339Date(endDate));
      eventEntity.setRemoteProviderId(1);
      eventEntity.setRemoteProviderName("agenda.exchangeCalendar");

      // When
      agendaExchangeEventListener.onEvent(event);

      // Then
      verify(appointment, times(1)).delete(any());
    }
}
