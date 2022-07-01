<!--
Copyright (C) 2022 eXo Platform SAS.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program. If not, see <http://www.gnu.org/licenses/>.
-->
<template>
  <exo-drawer
    id="exchangeSettingsDrawer"
    ref="exchangeSettingsDrawer"
    body-classes="hide-scroll decrease-z-index-more"
    :right="!$vuetify.rtl"
    @closed="cancelConnection"
    disable-pull-to-refresh>
    <template slot="title">
      {{ $t('agenda.exchangeCalendar.settings.connect.drawer.title') }}
    </template>
    <template slot="content">
      <v-form ref="form1" class="pa-2 ms-2 mt-4">
        <div class="d-flex flex-column flex-grow-1">
          <div class="d-flex flex-column mb-2">
            <label class="d-flex flex-row font-weight-bold my-2">{{ $t('agenda.exchangeCalendar.settings.connect.domain.label') }}</label>
            <div class="d-flex flex-row">
              <v-text-field
                id="domain"
                v-model="domain"
                type="text"
                name="domain"
                :placeholder="$t('agenda.exchangeCalendar.settings.connect.domain.placeholder')"
                class="input-block-level ignore-vuetify-classes pa-0"
                required
                outlined
                dense />
            </div>
          </div>
          <div class="d-flex flex-column mb-2">
            <label class="d-flex flex-row font-weight-bold my-2">{{ $t('agenda.exchangeCalendar.settings.connect.account.label') }}</label>
            <div class="d-flex flex-row">
              <v-text-field
                v-model="account"
                type="text"
                name="account"
                :placeholder="$t('agenda.exchangeCalendar.settings.connect.account.placeholder')"
                class="input-block-level ignore-vuetify-classes pa-0"
                outlined
                required
                dense />
            </div>
          </div>
          <div class="d-flex flex-column mb-2">
            <label class="d-flex flex-row font-weight-bold my-2">{{ $t('agenda.exchangeCalendar.settings.connect.password.label') }}</label>
            <div class="d-flex flex-row">
              <v-text-field
                v-model="password"
                :type="toggleFieldType"
                name="password"
                :append-icon="displayPasswordIcon"
                :placeholder="$t('agenda.exchangeCalendar.settings.connect.password.placeholder') "
                maxlength="100"
                class="input-block-level ignore-vuetify-classes pa-0"
                required
                outlined
                dense
                @click:append="showPassWord = !showPassWord" />
            </div>
          </div>
        </div>
      </v-form>
    </template>
    <template slot="footer">
      <div class="d-flex">
        <v-spacer />
        <v-btn
          class="btn me-2"
          @click="cancelConnection">
          {{ $t('agenda.exchangeCalendar.settings.connect.actions.cancel') }}
        </v-btn>
        <v-btn
          :loading="saving"
          :disabled="disableConnectButton"
          class="btn btn-primary"
          @click="saveSettings">
          {{ $t('agenda.exchangeCalendar.settings.connect.actions.connect') }}
        </v-btn>
      </div>
    </template>
  </exo-drawer>
</template>

<script>

const MAIL_PATTERN = /^(([^<>()[\]\\.,;:\s@"]+(\.[^<>()[\]\\.,;:\s@"]+)*)|(".+"))@((\[\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}])|(([a-zA-Z\-0-9]+\.)+[a-zA-Z]{2,}))$/;

export default {

  data: () => ({
    domain: '',
    account: '',
    password: '',
    showPassWord: false,
    connectionSuccess: false,
    saving: false,
    error: '',
  }),
  computed: {
    accountRule() {
      return this.account && this.account.toLowerCase().match(MAIL_PATTERN);
    },
    displayPasswordIcon() {
      return this.showPassWord ? 'mdi-eye': 'mdi-eye-off';
    },
    toggleFieldType() {
      return this.showPassWord ? 'text': 'password';
    },
    disableConnectButton() {
      return this.account === '' || this.password === '';
    }
  },
  watch: {
    saving() {
      if (this.saving) {
        this.$refs.exchangeSettingsDrawer.startLoading();
      } else {
        this.$refs.exchangeSettingsDrawer.endLoading();
      }
    },
  },
  methods: {
    openDrawer() {
      this.$refs.exchangeSettingsDrawer.open();
    },
    closeDrawer() {
      this.$refs.exchangeSettingsDrawer.close();
    },
    cancelConnection() {
      document.dispatchEvent(new CustomEvent('test-connection'));
      this.closeDrawer();
    },
    saveSettings() {
      if (!this.disableConnectButton) {
        this.saving = true;
        const exchangeSettings = {
          'domainName': this.domain,
          'username': this.account,
          'password': this.password
        };
        this.$agendaExchangeService.createExchangeSetting(exchangeSettings).then((respStatus) => {
          if (respStatus === 200) {
            this.$emit('display-alert', this.$t('agenda.exchangeCalendar.settings.connection.successMessage'));
          }
        }).then(() => {
          this.$agendaExchangeService.getExchangeSetting().then((settings) => {
            document.dispatchEvent(new CustomEvent('test-connection', {detail: settings}));
            this.reset();
            this.closeDrawer();
          });
        }).catch(() => {
          this.$emit('display-alert', this.$t('agenda.exchangeCalendar.settings.connection.errorMessage'), 'error');
        }).finally(() => {
          window.setTimeout(() => {
            this.saving = false;
          }, 200);
        });
      }
    },
    reset() {
      this.domain = '';
      this.account ='';
      this.password = '';
    }
  }

};
</script>