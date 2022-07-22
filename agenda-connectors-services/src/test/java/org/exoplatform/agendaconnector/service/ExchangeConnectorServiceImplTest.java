package org.exoplatform.agendaconnector.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

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
import microsoft.exchange.webservices.data.core.service.item.Appointment;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ ExchangeConnectorUtils.class, ExchangeService.class })
public class ExchangeConnectorServiceImplTest {

  private ExchangeConnectorServiceImpl exchangeConnectorService;

  private AgendaRemoteEventService agendaRemoteEventService;

  private ExchangeService          exchangeService;
  
  private ExchangeConnectorStorage exchangeConnectorStorage;

  @Before
  public void setUp() throws Exception {
    agendaRemoteEventService = mock(AgendaRemoteEventService.class);
    exchangeConnectorStorage = mock(ExchangeConnectorStorage.class);
    exchangeService = PowerMockito.mock(ExchangeService.class);
    PowerMockito.whenNew(ExchangeService.class).withArguments(any()).thenReturn(exchangeService);
    exchangeConnectorService = new ExchangeConnectorServiceImpl(exchangeConnectorStorage, agendaRemoteEventService);
    // PowerMockito.doCallRealMethod().when(exchangeConnectorService).pushEventToExchange(any(),
    // any(), any());
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
    exchangeConnectorService.pushEventToExchange(1, eventEntity, dstTimeZone);

    // Then
    verify(agendaRemoteEventService, times(1)).saveRemoteEvent(any());
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
    exchangeConnectorService.pushEventToExchange(1, eventEntity, dstTimeZone);
    
    // Then
    verify(appointment, times(1)).update(any(), any());
  }
}
