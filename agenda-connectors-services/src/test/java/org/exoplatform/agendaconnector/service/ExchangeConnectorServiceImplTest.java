package org.exoplatform.agendaconnector.service;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;

import org.exoplatform.agenda.constant.EventRecurrenceFrequency;
import org.exoplatform.agenda.constant.EventRecurrenceType;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventRecurrence;
import org.exoplatform.agenda.service.AgendaEventService;
import org.exoplatform.agenda.util.NotificationUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import org.exoplatform.agenda.model.RemoteEvent;
import org.exoplatform.agenda.rest.model.EventEntity;
import org.exoplatform.agenda.service.AgendaRemoteEventService;
import org.exoplatform.agenda.util.AgendaDateUtils;
import org.exoplatform.agendaconnector.model.ExchangeUserSetting;
import org.exoplatform.agendaconnector.storage.ExchangeConnectorStorage;
import org.exoplatform.agendaconnector.utils.ExchangeConnectorUtils;

import microsoft.exchange.webservices.data.core.ExchangeService;
import microsoft.exchange.webservices.data.core.enumeration.misc.ExchangeVersion;
import microsoft.exchange.webservices.data.core.enumeration.property.WellKnownFolderName;
import microsoft.exchange.webservices.data.core.service.item.Appointment;
import microsoft.exchange.webservices.data.core.service.item.Item;
import microsoft.exchange.webservices.data.search.FindItemsResults;
import microsoft.exchange.webservices.data.search.ItemView;
import microsoft.exchange.webservices.data.search.filter.SearchFilter;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ ExchangeConnectorUtils.class, ExchangeService.class, NotificationUtils.class })
public class ExchangeConnectorServiceImplTest {

  private ExchangeConnectorServiceImpl exchangeConnectorService;

  private AgendaRemoteEventService agendaRemoteEventService;

  private ExchangeService          exchangeService;
  
  private ExchangeConnectorStorage exchangeConnectorStorage;

  private AgendaEventService       agendaEventService;

  @Before
  public void setUp() throws Exception {
    agendaRemoteEventService = mock(AgendaRemoteEventService.class);
    exchangeConnectorStorage = mock(ExchangeConnectorStorage.class);
    agendaEventService = mock(AgendaEventService.class);
    exchangeService = PowerMockito.mock(ExchangeService.class);
    PowerMockito.whenNew(ExchangeService.class).withArguments(any()).thenReturn(exchangeService);
    exchangeConnectorService = new ExchangeConnectorServiceImpl(exchangeConnectorStorage,
                                                                agendaRemoteEventService,
                                                                agendaEventService);
    PowerMockito.mockStatic(NotificationUtils.class);
  }
  
  @Test
  public void testGetExchangeEvents() throws Exception {
    // Given
    ExchangeUserSetting exchangeUserSetting = new ExchangeUserSetting();
    exchangeUserSetting.setUsername("username");
    exchangeUserSetting.setPassword("password");
    when(exchangeConnectorStorage.getExchangeSetting(1)).thenReturn(exchangeUserSetting);
    System.setProperty("exo.exchange.server.url", "server.url");
    FindItemsResults<Item> exchangeEventsItems = new FindItemsResults<Item>();
    when(exchangeService.findItems(any(WellKnownFolderName.class), any(SearchFilter.class), any(ItemView.class))).thenReturn(exchangeEventsItems);
    
    // When
    ZoneId dstTimeZone = ZoneId.of("Europe/Paris");
    ZonedDateTime startDate =
            ZonedDateTime.of(LocalDate.now(), LocalTime.of(10, 0), dstTimeZone).withZoneSameInstant(dstTimeZone);
    ZonedDateTime endDate = startDate.plusHours(1);
    List<EventEntity> retrievedExchangeEvents = exchangeConnectorService.getExchangeEvents(1, AgendaDateUtils.toRFC3339Date(startDate), AgendaDateUtils.toRFC3339Date(endDate), ZoneId.of("Europe/Paris"));

    // Then
    assertEquals(exchangeEventsItems.getItems().size(), retrievedExchangeEvents.size());
  }

  @Test
  public void testCreateExchangeEvent() throws Exception {
    // Given
    ExchangeUserSetting exchangeUserSetting = new ExchangeUserSetting();
    exchangeUserSetting.setUsername("username");
    exchangeUserSetting.setPassword("password");
    when(exchangeConnectorStorage.getExchangeSetting(1)).thenReturn(exchangeUserSetting);
    System.setProperty("exo.exchange.server.url", "server.url");
    
    when(agendaRemoteEventService.findRemoteEvent(1, 1)).thenReturn(null);
    when(exchangeService.getRequestedServerVersion()).thenReturn(ExchangeVersion.Exchange2010_SP2);

    // When
    EventEntity eventEntity = new EventEntity();
    eventEntity.setId(1);
    eventEntity.setSummary("push created event");
    ZoneId dstTimeZone = ZoneId.of("Europe/Paris");
    ZonedDateTime startDate =
                            ZonedDateTime.of(LocalDate.now(), LocalTime.of(10, 0), dstTimeZone).withZoneSameInstant(dstTimeZone);
    ZonedDateTime endDate = startDate.plusHours(1);
    eventEntity.setStart(AgendaDateUtils.toRFC3339Date(startDate));
    eventEntity.setEnd(AgendaDateUtils.toRFC3339Date(endDate));
    eventEntity.setRemoteProviderId(1);
    eventEntity.setRemoteProviderName("agenda.exchangeCalendar");
    Event event = new Event();
    when(agendaEventService.getEventById(eventEntity.getId())).thenReturn(event);
    exchangeConnectorService.pushEventToExchange(1, eventEntity, dstTimeZone);
    verify(agendaRemoteEventService, times(1)).saveRemoteEvent(any());
    EventRecurrence eventRecurrence = new EventRecurrence();
    eventRecurrence.setType(EventRecurrenceType.DAILY);
    eventRecurrence.setOverallStart(ZonedDateTime.now());
    event.setRecurrence(eventRecurrence);
    when(agendaEventService.getEventById(eventEntity.getId())).thenReturn(event);
    exchangeConnectorService.pushEventToExchange(1, eventEntity, dstTimeZone);
    verify(agendaRemoteEventService, times(2)).saveRemoteEvent(any());

    eventRecurrence.setType(EventRecurrenceType.WEEK_DAYS);
    event.setRecurrence(eventRecurrence);
    when(agendaEventService.getEventById(eventEntity.getId())).thenReturn(event);
    exchangeConnectorService.pushEventToExchange(1, eventEntity, dstTimeZone);
    verify(agendaRemoteEventService, times(3)).saveRemoteEvent(any());

    eventRecurrence.setType(EventRecurrenceType.WEEKLY);
    eventRecurrence.setByDay(Collections.singletonList("WE"));
    event.setRecurrence(eventRecurrence);
    when(agendaEventService.getEventById(eventEntity.getId())).thenReturn(event);
    exchangeConnectorService.pushEventToExchange(1, eventEntity, dstTimeZone);
    verify(agendaRemoteEventService, times(4)).saveRemoteEvent(any());

    eventRecurrence.setType(EventRecurrenceType.MONTHLY);
    eventRecurrence.setByMonth(Collections.singletonList("1"));
    eventRecurrence.setByMonthDay(Collections.singletonList("1"));
    eventRecurrence.setInterval(1);
    event.setRecurrence(eventRecurrence);
    when(agendaEventService.getEventById(eventEntity.getId())).thenReturn(event);
    exchangeConnectorService.pushEventToExchange(1, eventEntity, dstTimeZone);
    verify(agendaRemoteEventService, times(5)).saveRemoteEvent(any());

    eventRecurrence.setType(EventRecurrenceType.YEARLY);
    event.setRecurrence(eventRecurrence);
    when(agendaEventService.getEventById(eventEntity.getId())).thenReturn(event);
    exchangeConnectorService.pushEventToExchange(1, eventEntity, dstTimeZone);
    verify(agendaRemoteEventService, times(6)).saveRemoteEvent(any());

    eventRecurrence.setType(EventRecurrenceType.CUSTOM);

    eventRecurrence.setFrequency(EventRecurrenceFrequency.YEARLY);
    event.setRecurrence(eventRecurrence);
    when(agendaEventService.getEventById(eventEntity.getId())).thenReturn(event);
    exchangeConnectorService.pushEventToExchange(1, eventEntity, dstTimeZone);
    verify(agendaRemoteEventService, times(7)).saveRemoteEvent(any());

    eventRecurrence.setFrequency(EventRecurrenceFrequency.MONTHLY);
    event.setRecurrence(eventRecurrence);
    when(agendaEventService.getEventById(eventEntity.getId())).thenReturn(event);
    exchangeConnectorService.pushEventToExchange(1, eventEntity, dstTimeZone);
    verify(agendaRemoteEventService, times(8)).saveRemoteEvent(any());

    eventRecurrence.setFrequency(EventRecurrenceFrequency.WEEKLY);
    event.setRecurrence(eventRecurrence);
    when(agendaEventService.getEventById(eventEntity.getId())).thenReturn(event);
    exchangeConnectorService.pushEventToExchange(1, eventEntity, dstTimeZone);
    verify(agendaRemoteEventService, times(9)).saveRemoteEvent(any());

    eventRecurrence.setFrequency(EventRecurrenceFrequency.DAILY);
    event.setRecurrence(eventRecurrence);
    when(agendaEventService.getEventById(eventEntity.getId())).thenReturn(event);
    exchangeConnectorService.pushEventToExchange(1, eventEntity, dstTimeZone);
    verify(agendaRemoteEventService, times(10)).saveRemoteEvent(any());
  }

  @Test
  public void testUpdateExchangeEvent() throws Exception {
    // Given
    ExchangeUserSetting exchangeUserSetting = new ExchangeUserSetting();
    exchangeUserSetting.setUsername("username");
    exchangeUserSetting.setPassword("password");
    when(exchangeConnectorStorage.getExchangeSetting(1)).thenReturn(exchangeUserSetting);
    System.setProperty("exo.exchange.server.url", "server.url");
    
    RemoteEvent remoteEvent = new RemoteEvent();
    remoteEvent.setEventId(1);
    remoteEvent.setRemoteId("remoteId");
    remoteEvent.setRemoteProviderId(1);
    remoteEvent.setRemoteProviderName("agenda.exchangeCalendar");
    when(agendaRemoteEventService.findRemoteEvent(1, 1)).thenReturn(remoteEvent);
    Appointment appointment = mock(Appointment.class);
    when(exchangeService.bindToItem(any(), any(), any())).thenReturn(appointment);

    // When
    EventEntity eventEntity = new EventEntity();
    eventEntity.setId(1);
    eventEntity.setSummary("push created event");
    ZoneId dstTimeZone = ZoneId.of("Europe/Paris");
    ZonedDateTime startDate =
                            ZonedDateTime.of(LocalDate.now(), LocalTime.of(10, 0), dstTimeZone).withZoneSameInstant(dstTimeZone);
    ZonedDateTime endDate = startDate.plusHours(1);
    eventEntity.setStart(AgendaDateUtils.toRFC3339Date(startDate));
    eventEntity.setEnd(AgendaDateUtils.toRFC3339Date(endDate));
    eventEntity.setRemoteProviderId(1);
    eventEntity.setRemoteProviderName("agenda.exchangeCalendar");
    Event event = new Event();
    when(agendaEventService.getEventById(eventEntity.getId())).thenReturn(event);
    exchangeConnectorService.pushEventToExchange(1, eventEntity, dstTimeZone);
    // Then
    verify(appointment, times(1)).update(any(), any());
  }

  @Test
  public void testDeleteExchangeEvent() throws Exception {
    // Given
    ExchangeUserSetting exchangeUserSetting = new ExchangeUserSetting();
    exchangeUserSetting.setUsername("username");
    exchangeUserSetting.setPassword("password");
    when(exchangeConnectorStorage.getExchangeSetting(1)).thenReturn(exchangeUserSetting);
    System.setProperty("exo.exchange.server.url", "server.url");

    RemoteEvent remoteEvent = new RemoteEvent();
    remoteEvent.setEventId(1);
    remoteEvent.setRemoteId("remoteId");
    remoteEvent.setRemoteProviderId(1);
    remoteEvent.setRemoteProviderName("agenda.exchangeCalendar");
    when(agendaRemoteEventService.findRemoteEvent(1, 1)).thenReturn(remoteEvent);
    Appointment appointment = mock(Appointment.class);
    when(exchangeService.bindToItem(any(), any(), any())).thenReturn(appointment);

    // When
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
    exchangeConnectorService.deleteExchangeEvent(1, 1);

    // Then
    verify(appointment, times(1)).delete(any());
  }
}
