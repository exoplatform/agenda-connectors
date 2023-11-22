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
  <v-app>
    <exchange-settings-drawer ref="exchangeSettingsDrawer" @display-alert="displayAlert" />
    <agenda-connectors-alert />
  </v-app>
</template>

<script>

export default {
  mounted() {
    this.openDrawer();
  },
  methods: {
    openDrawer() {
      this.$refs.exchangeSettingsDrawer.openDrawer();
    },
    closeDrawer() {
      this.$refs.exchangeSettingsDrawer.closeDrawer();
    },
    displayAlert(message, type) {
      this.$root.$emit('connectors-connection-alert', {
        message,
        type: type || 'success',
      });
    },
  }
};
</script>
