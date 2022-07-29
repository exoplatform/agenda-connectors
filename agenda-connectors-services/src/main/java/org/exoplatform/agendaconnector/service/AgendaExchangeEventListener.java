package org.exoplatform.agendaconnector.service;

import org.exoplatform.agenda.model.EventAttendee;
import org.exoplatform.agenda.model.EventAttendeeList;
import org.exoplatform.agenda.service.AgendaEventAttendeeService;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.Listener;
public class AgendaExchangeEventListener extends Listener<Long, Long> {

  private final ExchangeConnectorService exchangeConnectorService;

  private final AgendaEventAttendeeService agendaEventAttendeeService;

  public AgendaExchangeEventListener(ExchangeConnectorService exchangeConnectorService, AgendaEventAttendeeService agendaEventAttendeeService) {
    this.exchangeConnectorService = exchangeConnectorService;
    this.agendaEventAttendeeService = agendaEventAttendeeService;
  }

  @Override
  public void onEvent(Event<Long, Long> event) throws Exception {
    long eventId = event.getSource();
    long identityId = event.getData();
    EventAttendeeList eventAttendees = agendaEventAttendeeService.getEventAttendees(eventId);
    for (EventAttendee eventAttendee : eventAttendees.getEventAttendees()) {
      if (eventAttendee.getIdentityId() != identityId){
        exchangeConnectorService.deleteExchangeEvent(eventAttendee.getIdentityId(), eventId);
      }
    }
  }
}
