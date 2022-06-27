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
export const createExchangeSetting = (exchangeSettings) => {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/exchange`, {
    headers: {
      'Content-Type': 'application/json'
    },
    credentials: 'include',
    method: 'POST',
    body: JSON.stringify(exchangeSettings)
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Response code indicates a server error', resp);
    } else {
      return resp.status;
    }
  });
};

export const getExchangeSetting = () => {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/exchange`, {
    credentials: 'include',
    method: 'GET',
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Response code indicates a server error', resp);
    } else {
      return resp.json();
    }
  });
};

export const deleteExchangeSetting = () => {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/exchange`, {
    credentials: 'include',
    method: 'DELETE',
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Response code indicates a server error', resp);
    } else {
      return resp.status;
    }
  });
};

export const getExchangeEvents = (start, end) => {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/exchange/events?start="test"${start}&end=${end}&timeZoneId=Africa/Tunis`, {
    credentials: 'include',
    method: 'GET',
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Response code indicates a server error', resp);
    } else {
      return resp.json();
    }
  });
};