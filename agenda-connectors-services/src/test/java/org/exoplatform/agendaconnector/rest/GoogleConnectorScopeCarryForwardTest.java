/*
 * Copyright (C) 2026 eXo Platform SAS
 *
 *  This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <gnu.org/licenses>.
 */
package org.exoplatform.agendaconnector.rest;

import java.util.HashMap;
import java.util.Map;

import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.json.gson.GsonFactory;
import org.exoplatform.agenda.service.AgendaRemoteEventService;
import org.exoplatform.agendaconnector.service.GoogleConnectorService;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * The stored token blob is replaced wholesale by every refresh response, and
 * RFC 6749 §5.1 makes its scope OPTIONAL when unchanged. What the client can
 * do is derived from those scopes, so losing them switches capabilities off
 * — and the one that selects a silent fallback raises no error that would
 * ever reveal it.
 */
public class GoogleConnectorScopeCarryForwardTest {

  private static final String GRANTED =
      "https://www.googleapis.com/auth/calendar.readonly https://www.googleapis.com/auth/calendar.events";

  private Map<String, Object> storedToken(String scope) {
    Map<String, Object> stored = new HashMap<>();
    stored.put("refresh_token", "a-refresh-token");
    if (scope != null) {
      stored.put("scope", scope);
    }
    return stored;
  }

  @Test
  public void shouldKeepTheGrantedScopesWhenTheRefreshOmitsThem() {
    GoogleTokenResponse refreshed = new GoogleTokenResponse();

    GoogleConnectorRest.carryForwardScope(refreshed, storedToken(GRANTED));

    assertEquals(GRANTED, refreshed.getScope());
  }

  @Test
  public void shouldNotOverwriteTheScopesTheRefreshDidRestate() {
    GoogleTokenResponse refreshed = new GoogleTokenResponse();
    refreshed.setScope("https://www.googleapis.com/auth/calendar.events");

    GoogleConnectorRest.carryForwardScope(refreshed, storedToken(GRANTED));

    // The refresh response is authoritative when it speaks; a narrowed grant
    // must not be widened back by what was stored before it.
    assertEquals("https://www.googleapis.com/auth/calendar.events", refreshed.getScope());
  }

  @Test
  public void shouldLeaveTheScopeUnsetWhenNeitherTokenDeclaresOne() {
    GoogleTokenResponse refreshed = new GoogleTokenResponse();

    GoogleConnectorRest.carryForwardScope(refreshed, storedToken(null));

    assertNull(refreshed.getScope());
  }

  @Test
  public void shouldTolerateAStoredTokenThatIsMissingOrMalformed() {
    GoogleTokenResponse refreshed = new GoogleTokenResponse();

    GoogleConnectorRest.carryForwardScope(refreshed, null);
    assertNull(refreshed.getScope());

    Map<String, Object> notAString = new HashMap<>();
    notAString.put("scope", 42);
    GoogleConnectorRest.carryForwardScope(refreshed, notAString);
    assertNull(refreshed.getScope());

    GoogleConnectorRest.carryForwardScope(refreshed, storedToken("   "));
    assertNull(refreshed.getScope());
  }

  @Test
  public void shouldStoreATokenThatStillDeclaresTheGrantedScopes() throws java.io.IOException {
    GoogleConnectorService googleConnectorService = mock(GoogleConnectorService.class);
    GoogleConnectorRest rest = new GoogleConnectorRest(mock(AgendaRemoteEventService.class), googleConnectorService);
    GoogleTokenResponse refreshed = new GoogleTokenResponse();
    // Without a factory, toString() is a GenericData dump rather than JSON —
    // a shape production never stores, and one that would hide a save writing
    // the wrong thing entirely. Every reader of this blob parses it as JSON.
    refreshed.setFactory(new GsonFactory());
    refreshed.setAccessToken("a-fresh-access-token");

    rest.saveRefreshedToken("jdoe", refreshed, storedToken(GRANTED));

    // What has to be true of the stored blob is that it is JSON and that it
    // still declares its scopes: everything the client may do is derived from
    // them, and a capability that selects a silent fallback has no error path
    // to reveal their loss.
    ArgumentCaptor<String> saved = ArgumentCaptor.forClass(String.class);
    verify(googleConnectorService).saveTokenResponse(eq("jdoe"), saved.capture());
    GoogleTokenResponse reread = new GsonFactory().fromString(saved.getValue(), GoogleTokenResponse.class);
    assertEquals(GRANTED, reread.getScope());
    assertEquals("a-fresh-access-token", reread.getAccessToken());
  }
}
