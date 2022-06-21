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
    disable-pull-to-refresh>
    <template slot="title">
      Connect your Exchange Agenda
    </template>
    <template slot="content">
      <v-form ref="form1" class="pa-2 ms-2 mt-4">
        <div class="d-flex flex-column flex-grow-1">
          <div class="d-flex flex-column mb-2">
            <label class="d-flex flex-row font-weight-bold my-2">Domain</label>
            <div class="d-flex flex-row">
              <v-text-field
                id="domain"
                :v-model="domain"
                type="text"
                name="domain"
                placeholder="Domain"
                class="input-block-level ignore-vuetify-classes pa-0"
                required
                outlined
                dense />
            </div>
          </div>
          <div class="d-flex flex-column mb-2">
            <label class="d-flex flex-row font-weight-bold my-2">Account</label>
            <div class="d-flex flex-row">
              <v-text-field
                v-model="account"
                type="text"
                name="account"
                :error-messages="accountErrorMessage"
                placeholder="Account"
                class="input-block-level ignore-vuetify-classes pa-0"
                outlined
                required
                dense />
            </div>
          </div>
          <div class="d-flex flex-column mb-2">
            <label class="d-flex flex-row font-weight-bold my-2">Password</label>
            <div class="d-flex flex-row">
              <v-text-field
                v-model="password"
                :type="toggleFieldType"
                name="password"
                :append-icon="displayPasswordIcon"
                placeholder="Password"
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
          @click="close">
          Cancel
        </v-btn>
        <v-btn
          :loading="saving"
          :disabled="disabled"
          class="btn btn-primary"
          @click="checkConnection">
          Connect
        </v-btn>
      </div>
    </template>
  </exo-drawer>
</template>

<script>

const MAIL_PATTERN = /^(([^<>()[\]\\.,;:\s@"]+(\.[^<>()[\]\\.,;:\s@"]+)*)|(".+"))@((\[\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}])|(([a-zA-Z\-0-9]+\.)+[a-zA-Z]{2,}))$/;

export default {

  data: () => ({
    mailIntegrationSettingId: '',
    emailAccount: '',
    imapUrl: '',
    port: '',
    encryption: 'SSL',
    account: '',
    password: '',
    showPassWord: false,
    connectionSuccess: false,
    saving: false,
    error: '',
    disabled: false,
    domain: ''
  }),
  computed: {
    accountRule() {
      return this.account && this.account.toLowerCase().match(MAIL_PATTERN);
    },
    accountErrorMessage() {
      return this.accountRule || this.account.length === 0 ? '': this.$t('mailIntegration.settings.name.errorMail');
    },
    displayPasswordIcon() {
      return this.showPassWord ? 'mdi-eye': 'mdi-eye-off';
    },
    toggleFieldType() {
      return this.showPassWord ? 'text': 'password';
    }
  },
  watch: {
    saving() {
      if (this.saving) {
        this.$refs.mailIntegrationSettingDrawer.startLoading();
      } else {
        this.$refs.mailIntegrationSettingDrawer.endLoading();
      }
    },
  },
  methods: {
    openDrawer() {
      this.$refs.exchangeSettingsDrawer.open();
    },
    closeDrawer() {
      this.$refs.exchangeSettingsDrawer.close();
    }
  }

};
</script>