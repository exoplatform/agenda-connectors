package org.exoplatform.agendaconnector.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
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
import org.mockito.ArgumentCaptor;
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
import microsoft.exchange.webservices.data.property.complex.ItemId;
import microsoft.exchange.webservices.data.search.CalendarView;
import microsoft.exchange.webservices.data.search.FindItemsResults;

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

    ZoneId dstTimeZone = ZoneId.of("Europe/Paris");
    ZonedDateTime periodStart = ZonedDateTime.of(LocalDate.now(), LocalTime.of(0, 0), dstTimeZone);
    ZonedDateTime periodEnd = ZonedDateTime.of(LocalDate.now(), LocalTime.of(23, 59), dstTimeZone);
    ZonedDateTime firstOccurrenceStart = periodStart.plusHours(10);
    ZonedDateTime secondOccurrenceStart = firstOccurrenceStart.plusDays(1);

    FindItemsResults<Appointment> exchangeAppointments = new FindItemsResults<>();
    // Two occurrences of the same recurring series and one all day event
    exchangeAppointments.getItems().add(mockAppointment("occurrenceId1",
                                                        "every day event",
                                                        firstOccurrenceStart,
                                                        firstOccurrenceStart.plusHours(1),
                                                        false));
    exchangeAppointments.getItems().add(mockAppointment("occurrenceId2",
                                                        "every day event",
                                                        secondOccurrenceStart,
                                                        secondOccurrenceStart.plusHours(1),
                                                        false));
    exchangeAppointments.getItems().add(mockAppointment("allDayEventId",
                                                        "all day event",
                                                        periodStart,
                                                        periodStart.plusDays(1),
                                                        true));
    when(exchangeService.findAppointments(any(WellKnownFolderName.class),
                                          any(CalendarView.class))).thenReturn(exchangeAppointments);

    // When
    List<EventEntity> retrievedExchangeEvents = exchangeConnectorService.getExchangeEvents(1,
                                                                                           AgendaDateUtils.toRFC3339Date(periodStart),
                                                                                           AgendaDateUtils.toRFC3339Date(periodEnd),
                                                                                           dstTimeZone);

    // Then
    // All the occurrences of a recurring event are retrieved and not only its recurring master
    assertEquals(exchangeAppointments.getItems().size(), retrievedExchangeEvents.size());
    assertEquals("occurrenceId1", retrievedExchangeEvents.get(0).getRemoteId());
    assertEquals("every day event", retrievedExchangeEvents.get(0).getSummary());
    assertEquals(AgendaDateUtils.toRFC3339Date(firstOccurrenceStart), retrievedExchangeEvents.get(0).getStart());
    assertFalse(retrievedExchangeEvents.get(0).isAllDay());
    assertEquals("occurrenceId2", retrievedExchangeEvents.get(1).getRemoteId());
    assertEquals(AgendaDateUtils.toRFC3339Date(secondOccurrenceStart), retrievedExchangeEvents.get(1).getStart());
    assertTrue(retrievedExchangeEvents.get(2).isAllDay());
    // The end date of an all day event is the last day of the event
    assertEquals(AgendaDateUtils.toRFC3339Date(periodStart), retrievedExchangeEvents.get(2).getEnd());

    // The events are searched over the whole requested period, kept in the user time zone
    ArgumentCaptor<CalendarView> calendarViewCaptor = ArgumentCaptor.forClass(CalendarView.class);
    verify(exchangeService).findAppointments(any(WellKnownFolderName.class), calendarViewCaptor.capture());
    assertEquals(AgendaDateUtils.toDate(periodStart), calendarViewCaptor.getValue().getStartDate());
    assertEquals(AgendaDateUtils.toDate(periodEnd), calendarViewCaptor.getValue().getEndDate());
  }

  private Appointment mockAppointment(String itemId,
                                      String subject,
                                      ZonedDateTime start,
                                      ZonedDateTime end,
                                      boolean allDay) throws Exception {
    Appointment appointment = mock(Appointment.class);
    when(appointment.getId()).thenReturn(new ItemId(itemId));
    when(appointment.getSubject()).thenReturn(subject);
    when(appointment.getStart()).thenReturn(AgendaDateUtils.toDate(start));
    when(appointment.getEnd()).thenReturn(AgendaDateUtils.toDate(end));
    when(appointment.getIsAllDayEvent()).thenReturn(allDay);
    return appointment;
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
