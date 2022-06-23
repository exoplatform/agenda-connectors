<template>
  <v-app>
    <exchange-settings-drawer ref="exchangeSettingsDrawer" @display-alert="displayAlert" />
    <agenda-connectors-alert />
  </v-app>
</template>

<script>

export default {
  created() {
    document.addEventListener('open-connector-settings-drawer',this.openDrawer);
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