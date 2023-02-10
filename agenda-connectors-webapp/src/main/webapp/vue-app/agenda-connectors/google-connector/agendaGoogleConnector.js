/*
 * Copyright (C) 2022 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import jwt_decode from 'jwt-decode';

export default {
  name: 'agenda.googleCalendar',
  description: 'agenda.googleCalendar.description',
  avatar: '/agenda-connectors/skin/images/Google.png',
  isOauth: true,
  mandatorySecretKey: true,
  CLIENT_ID: null,
  SECRET_KEY: null,
  DISCOVERY_DOCS: ['https://www.googleapis.com/discovery/v1/apis/calendar/v3/rest'],
  SCOPE_WRITE: 'https://www.googleapis.com/auth/calendar.events',
  canConnect: true,
  canPush: false,
  initialized: false,
  isSignedIn: false,
  pushing: false,
  rank: 10,
  init(connectionStatusChangedCallback, loadingCallback, apiKey, secretKey) {
    if (!apiKey || !secretKey) {
      throw new Error('Google connector can\'t be enabled with empty Client API Key or empty Client Secret Key.');
    }
    this.CLIENT_ID = apiKey;
    this.SECRET_KEY = secretKey;
    // Already initialized
    if (this.initialized) {
      return;
    }
    this.initialized = true;
    this.connectionStatusChangedCallback = connectionStatusChangedCallback;
    this.loadingCallback = loadingCallback;

    initGoogleConnector(this);
  },
  authorize(refresh) {
    return new Promise((resolve, reject) => {
      try {
        this.codeClient.callback = (response) => {
          if (response && response.code) {
            return getToken(this.CLIENT_ID, this.SECRET_KEY, response.code, window.location.origin)
              .then(tokenResponse => {
                if (tokenResponse && tokenResponse.access_token) {
                  this.gapi.client.setToken(tokenResponse);
                  resolve(tokenResponse);
                }
              });
          }
        };
        const cookieSuffix = this.user && this.user.substring(0, this.user.indexOf('@'));
        const tokenResponse = getCookie(`g_connector_oauth_${cookieSuffix}`);
        if (tokenResponse) {
          setCookie(`g_connector_oauth_${cookieSuffix}`, JSON.stringify(tokenResponse), 90);
        }
        if (refresh && tokenResponse) {
          const response = JSON.parse(tokenResponse);
          return refreshToken(this.CLIENT_ID, this.SECRET_KEY, response.refresh_token)
            .then(refreshTokenResponse => {
              if (refreshTokenResponse && refreshTokenResponse.access_token) {
                response.access_token = refreshTokenResponse.access_token;
                setCookie(`g_connector_oauth_${cookieSuffix}`, JSON.stringify(response), 90);
                this.gapi.client.setToken(response);
                this.canPush = this.cientOauth.hasGrantedAllScopes(response, this.SCOPE_WRITE);
                resolve(response);
              }
            });
        } else if (tokenResponse && this.user) {
          const response = JSON.parse(tokenResponse);
          this.gapi.client.setToken(response);
          resolve(response);
        } else {
          this.codeClient.requestCode();
        }
      } catch (err) {
        reject(err);
      }
    });
  },
  authenticate() {
    return new Promise((resolve, reject) => {
      deleteCookie('g_state');
      try {
        this.identity.prompt(notification => {
          if (notification.getDismissedReason() === 'credential_returned') {
            resolve();
          } else
          if (notification.getDismissedReason() === 'user_cancel') {
            this.loadingCallback(this, false);
            this.connectionStatusChangedCallback(this, false, 'user_cancel');
            resolve();
          }
        });
      } catch (err) {
        reject(err);
      }
    });
  },
  connect(askWriteAccess) {
    this.loadingCallback(this, true);
    if (askWriteAccess && !this.canPush) {
      return this.authorize().then(tokenResponse => {
        if (tokenResponse && tokenResponse.access_token) {
          this.canPush = this.cientOauth.hasGrantedAllScopes(tokenResponse, this.SCOPE_WRITE);
          this.identity.prompt();
          return this.authenticate().then(() => {
            return new Promise((resolve, reject) => {
              if (this.credential) {
                const userEmail = this.credential.email;
                const cookieSuffix = userEmail.substring(0, userEmail.indexOf('@'));
                setCookie(`g_connector_oauth_${cookieSuffix}`, JSON.stringify(tokenResponse), 90);
                resolve(userEmail);
              } else {
                reject();
              }
            });
          });
        }
      });
    } else {
      return this.authenticate().then(() => {
        return new Promise((resolve, reject) => {
          if (this.credential) {
            resolve(this.credential.email);
          } else {
            reject();
          }
        });
      });
    }
  },
  disconnect() {
    this.loadingCallback(this, true);
    if (this.gapi.client.getToken() && this.cientOauth || this.user) {
      this.cientOauth.revoke(this.gapi.client.getToken());
      this.gapi.client.setToken('');
      if (this.user) {
        this.identity.revoke(this.user);
        this.identity.disableAutoSelect();
      }
      return Promise.resolve(true);
    } else {
      return Promise.resolve(null);
    }
  },
  getEvents(periodStartDate, periodEndDate) {
    if (this.gapi && this.gapi.client && this.gapi.client.calendar) {
      this.loadingCallback(this, true);
      return new Promise((resolve, reject) => {
        retrieveEvents(this, periodStartDate, periodEndDate)
          .then(gEvents => resolve(gEvents))
          .catch(e => {
            if (e.status === 403 || e.status === 401) {
              return this.authorize().then((tokenResponse) => {
                if (tokenResponse && tokenResponse.access_token) {
                  this.canPush = this.cientOauth.hasGrantedAllScopes(tokenResponse, this.SCOPE_WRITE);
                  retrieveEvents(this, periodStartDate, periodEndDate)
                    .then(gEvents => resolve(gEvents))
                    .catch((e) => {
                      if (e.status === 403 || e.status === 401) {
                        return this.authorize(true).then(() => {
                          retrieveEvents(this, periodStartDate, periodEndDate)
                            .then(gEvents => resolve(gEvents));
                        });
                      } else {
                        this.loadingCallback(this, false);
                        reject(e);
                      }
                    });
                }
              });
            } else {
              this.loadingCallback(this, false);
              reject(e);
            }
          });
      }).finally(() => this.loadingCallback(this, false));
    } else {
      return Promise.resolve(null);
    }
  },
  deleteEvent(event, connectorRecurringEventId) {
    return this.saveEvent(event, connectorRecurringEventId, true);
  },
  pushEvent(event, connectorRecurringEventId) {
    return this.saveEvent(event, connectorRecurringEventId, false);
  },
  saveEvent(event, connectorRecurringEventId, deleteEvent) {
    if (this.gapi) {
      this.pushing = true;
      return new Promise((resolve, reject) => {
        pushEventToGoogle(this, event, connectorRecurringEventId, deleteEvent)
          .then(gEvent => {
            resolve(gEvent);
          }).catch(error => {
            if (error.status === 403 || error.status === 401) {
              return this.authorize().then(() => {
                pushEventToGoogle(this, event, connectorRecurringEventId, deleteEvent)
                  .then(gEvent => {
                    resolve(gEvent);
                  }).catch(error => {
                    if (error.status === 403 || error.status === 401) {
                      return this.authorize(true).then(() => {
                        pushEventToGoogle(this, event, connectorRecurringEventId, deleteEvent)
                          .then(gEvent => {
                            resolve(gEvent);
                          });
                      });
                    } else {
                      this.loadingCallback(this, false);
                      reject(error);
                    }
                  });
              });
            } else {
              this.loadingCallback(this, false);
              reject(error);
            }
          });
      }).finally(() => this.pushing = false);
    }
    return Promise.reject(new Error('Not connected'));
  },
};

/**
 * @param {Object}
 *          connector Google Connector SPI
 * @param {Date}
 *          periodStartDate Start date of period of events to retrieve
 * @param {Date}
 *          periodEndDate End date of period of events to retrieve
 * @returns {Promise} a promise with list of Google events
 */
function retrieveEvents(connector, periodStartDate, periodEndDate) {
  return connector.gapi.client.calendar.events.list({
    'calendarId': 'primary',
    'timeMin': periodStartDate,
    'timeMax': periodEndDate,
    'singleEvents': true,
    'orderBy': 'startTime'
  }).then(events => events.result.items).then(events => {
    events.forEach(event => {
      event.allDay = !!event.start.date;
      event.start = event.start.dateTime || event.start.date;
      // Google api returns all day event with one day added for end date.
      const endDate = new Date(event.end.date);
      endDate.setDate(endDate.getDate()-1);
      event.end = event.allDay ? endDate : event.end.dateTime;
      event.name = event.summary;
      event.type = 'remoteEvent';
      event.color = '#FFFFFF';
    });
    connector.loadingCallback(connector, false);
    return events;
  });
}

function setCookie(name, value, days) {
  const date = new Date();
  date.setTime(date.getTime() + (days * 24 * 60 * 60 * 1000));
  const expires = `expires=${date.toUTCString()}`;
  document.cookie = `${name}=${value};${expires};path=/`;
}

function deleteCookie(name) {
  document.cookie = `${name}=; Max-Age=0; path=/`;
}

function getCookie(cname) {
  const name = `${cname}=`;
  const decodedCookie = decodeURIComponent(document.cookie);
  const ca = decodedCookie.split(';');
  for (const value of ca) {
    let c = value;
    while (c.charAt(0) === ' ') {
      c = c.substring(1);
    }
    if (c.indexOf(name) === 0) {
      return c.substring(name.length, c.length);
    }
  }
  return '';
}

function refreshToken(clientId, clientSecret, refreshToken) {
  const formData = new FormData();
  formData.append('client_id', encodeURIComponent(clientId));
  formData.append('client_secret', encodeURIComponent(clientSecret));
  formData.append('refresh_token', refreshToken);
  formData.append('grant_type', 'refresh_token');
  return fetch('https://oauth2.googleapis.com/token', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    body: new URLSearchParams(formData).toString(),
  }).then((resp) => {
    if (!resp || !resp.ok) {
      throw new Error('Error while refreshing access token');
    } else {
      return resp.json();
    }
  });
}

function getToken(clientId, clientSecret, code, redirect_uri) {
  const formData = new FormData();
  formData.append('client_id', encodeURIComponent(clientId));
  formData.append('client_secret', encodeURIComponent(clientSecret));
  formData.append('code', code);
  formData.append('grant_type', 'authorization_code');
  formData.append('redirect_uri', redirect_uri);
  return fetch('https://oauth2.googleapis.com/token', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    body: new URLSearchParams(formData).toString(),
  }).then((resp) => {
    if (!resp || !resp.ok) {
      throw new Error('Error while getting access token');
    } else {
      return resp.json();
    }
  });
}
/**
 * Load Google Connector API javascript and prepare user authentication and
 * authorization process
 * 
 * @param {Object}
 *          connector Google Connector SPI
 * @returns {void}
 */

function initGoogleConnector(connector) {
  connector.loadingCallback(connector, true);
  window.require(['https://apis.google.com/js/api.js', 'https://accounts.google.com/gsi/client'], () => {
    connector.identity = google.accounts.id;
    const cookie = getCookie('g_state');
    if (cookie && !JSON.parse(cookie).i_t) {
      connector.isSignedIn = true;
    }
    connector.identity.initialize({
      client_id: connector.CLIENT_ID,
      callback: (credResponse) => {
        if (credResponse && credResponse.credential) {
          const credential = jwt_decode(credResponse.credential);
          connector.isSignedIn = true;
          connector.connectionStatusChangedCallback(connector, {
            user: credential.email,
            id: credential.sub,
          });
          connector.credential = credential;
        } else {
          connector.connectionStatusChangedCallback(connector, false);
        }
      }
    });
    connector.gapi = gapi;
    connector.gapi.load('client', function() {
      gapi.client.init({
        discoveryDocs: connector.DISCOVERY_DOCS,
      }).then(function () {
        connector.cientOauth = google.accounts.oauth2;
        connector.codeClient = connector.cientOauth.initCodeClient({
          client_id: connector.CLIENT_ID,
          scope: connector.SCOPE_WRITE,
          ux_mode: 'popup',
          callback: (response) => {
            if (response && response.code) {
              getToken(connector.CLIENT_ID, connector.SECRET_KEY, response.code, window.location.origin)
                .then(tokenResponse => {
                  if (tokenResponse && tokenResponse.access_token) {
                    connector.canPush = connector.cientOauth.hasGrantedAllScopes(tokenResponse, this.SCOPE_WRITE);
                    gapi.client.setToken(tokenResponse);
                    const cookieSuffix = this.user && this.user.substring(0, this.user.indexOf('@'));
                    setCookie(`g_connector_oauth_${cookieSuffix}`, JSON.stringify(tokenResponse), 90);
                  }
                });
            }
          },
          error_callback: (error) => {
            connector.loadingCallback(connector, false);
            connector.connectionStatusChangedCallback(connector, false, error);
          }
        });
      }, function(error) {
        connector.loadingCallback(connector, false);
        connector.connectionStatusChangedCallback(connector, false, error);
      });
    });
    connector.loadingCallback(connector, false);
  }, (error) => {
    connector.canConnect = false;
    connector.loadingCallback(connector, false);
    console.error('Error retrieving Google API Javascript', error);
  });
}

/**
 * Push event into Google account
 * 
 * @param {Object}
 *          connector Google Connector SPI
 * @param {Object}
 *          event Agenda event
 * @param {String}
 *          connectorRecurringEventId Connector parent recurrent event
 *          Identifier
 * @param {Boolean}
 *          deleteEvent whether to delete or save event status
 * @returns {void}
 */
function pushEventToGoogle(connector, event, connectorRecurringEventId, deleteEvent) {
  const connectorEvent = buildConnectorEvent(event, connectorRecurringEventId);
  let retrievingEventPromise = null;
  const isExceptionalOccurrence = connectorRecurringEventId && event.occurrence && event.occurrence.id;
  const isRemoteEvent = event.remoteId && event.remoteProviderName === connector.name;
  const isDeleteEvent = deleteEvent || event.status.toLowerCase() === 'cancelled';

  if (isExceptionalOccurrence || isRemoteEvent || isDeleteEvent) {
    const options = {
      'calendarId': 'primary',
      'showDeleted': true,
    };
    if (isExceptionalOccurrence) {
      options.eventId = connectorRecurringEventId;
      options.recurringEventId = connectorRecurringEventId;
      options.originalStart = event.occurrence.id;
      retrievingEventPromise = connector.gapi.client.calendar.events.instances(options);
    } else if (isRemoteEvent) {
      options.eventId = event.remoteId;
      retrievingEventPromise = connector.gapi.client.calendar.events.get(options);
    } else {
      retrievingEventPromise = Promise.resolve(null);
    }
  } else {
    retrievingEventPromise = Promise.resolve(null);
  }

  return retrievingEventPromise
    .then(data => {
      const remoteConnectorEventResult = data && data.result;
      let remoteConnectorEvent = null;
      if (remoteConnectorEventResult) {
        if (remoteConnectorEventResult.items) {
          remoteConnectorEvent = remoteConnectorEventResult.items.length && remoteConnectorEventResult.items[0];
        } else if (remoteConnectorEventResult.id) {
          remoteConnectorEvent = remoteConnectorEventResult;
        }
      }
      const pushMethod = isDeleteEvent ?
        connector.gapi.client.calendar.events.delete
        :remoteConnectorEvent ?
          connector.gapi.client.calendar.events.patch:
          connector.gapi.client.calendar.events.insert;

      const options = {
        calendarId: 'primary',
      };

      if (isDeleteEvent) {
        if (!remoteConnectorEvent || remoteConnectorEvent.status === 'cancelled') {
          return null;
        }
        options.eventId = remoteConnectorEvent.id;
      } else {
        if (remoteConnectorEvent) {
          options.eventId = remoteConnectorEvent.id;
          connectorEvent.id = options.eventId;
          if (isExceptionalOccurrence) {
            connectorEvent.originalStartTime = remoteConnectorEvent.originalStartTime;
            connectorEvent.recurringEventId = remoteConnectorEvent.recurringEventId;
          }
        }
        options.resource = connectorEvent;
      }

      return pushMethod(options);
    })
    .then(resp => resp && resp.result);
}

/**
 * Build event to push into Google
 * 
 * @param {Object}
 *          event Agenda Event object
 * @param {String}
 *          connectorRecurringEventId Connector parent recurrent event
 *          Identifier
 * @returns {void}
 */
function buildConnectorEvent(event, connectorRecurringEventId) {
  const connectorEvent = {};
  if (event.recurrence) {
    connectorEvent.recurrence = [`RRULE:${event.recurrence.rrule}`];
  }
  if (connectorRecurringEventId) {
    connectorEvent.recurringEventId = connectorRecurringEventId;
    if (event.allDay) {
      connectorEvent.originalStartTime = {
        date: event.occurrence.id,
        timeZone: event.timeZoneId
      };
    } else {
      connectorEvent.originalStartTime = {
        dateTime: event.occurrence.id,
        timeZone: event.timeZoneId
      };
    }
  }
  connectorEvent.status = event.status.toLowerCase();

  if (event.allDay) {
    connectorEvent.start = {
      date: event.start,
    };
  } else {
    connectorEvent.start = {
      dateTime: event.start,
      timeZone: event.timeZoneId
    };
  }
  if (event.allDay) {
    const endDate = new Date(event.end);
    endDate.setDate(endDate.getDate() +1);
    const formattedEndDate = `${endDate.getFullYear()  }-${
      pad(endDate.getMonth() + 1)  }-${
      pad(endDate.getDate())}`;
    connectorEvent.end = {
      date: formattedEndDate
    };
  } else {
    connectorEvent.end = {
      dateTime: event.end,
      timeZone: event.timeZoneId,
    };
  }
  connectorEvent.description = event.description;
  connectorEvent.summary = event.summary;
  connectorEvent.location = event.location || (event.conferences && event.conferences.length && event.conferences[0].url) || '';
  connectorEvent.source =   {
    'url': `${window.location.origin}${eXo.env.portal.context}/${eXo.env.portal.portalName}/agenda?eventId=${event.id}`,
  };
  return connectorEvent;
}

function pad(n) {
  return n < 10 && `0${n}` || n;
}
