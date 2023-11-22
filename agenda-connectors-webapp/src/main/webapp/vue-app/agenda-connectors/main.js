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
import './initComponents.js';
import googleConnector from './google-connector/agendaGoogleConnector.js';
import officeConnector from './office-connector/agendaOfficeConnector.js';
import exchangeConnector from './exchange-connector/agendaExchangeConnector.js';
import * as agendaExchangeService from './js/agendaExchangeService.js';

extensionRegistry.registerExtension('agenda', 'connectors', googleConnector);
extensionRegistry.registerExtension('agenda', 'connectors', officeConnector);
extensionRegistry.registerExtension('agenda', 'connectors', exchangeConnector);

document.dispatchEvent(new CustomEvent('agenda-connectors-refresh'));

if (!Vue.prototype.$agendaExchangeService) {
  window.Object.defineProperty(Vue.prototype, '$agendaExchangeService', {
    value: agendaExchangeService,
  });
}

// getting language of the PLF
const lang = eXo.env.portal.language || 'en';
// init Vue app when locale resources are ready
const url = `${eXo.env.portal.context}/${eXo.env.portal.rest}/i18n/bundle/locale.portlet.AgendaConnectors-${lang}.json`;

const vuetify = new Vuetify(eXo.env.portal.vuetifyPreset);


// get overridden components if exists
if (extensionRegistry) {
  const components = extensionRegistry.loadComponents('AgendaConnectors');
  if (components && components.length > 0) {
    components.forEach(cmp => {
      Vue.component(cmp.componentName, cmp.componentOptions);
    });
  }
}

exoi18n.loadLanguageAsync(lang, url);
document.addEventListener('open-exchange-connector-settings-drawer',function() {
  const appId = 'agendaConnectorSettingsDrawer';
  const appElement = document.getElementById(appId);
  appElement.appendChild(document.createElement('div'));
  exoi18n.loadLanguageAsync(lang, url).then(i18n => {
    Vue.createApp({
      template: '<agenda-connectors />',
      vuetify,
      i18n
    }, `#${appId}>div`, 'Agenda Connectors Settings');
  });
});

