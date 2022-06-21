<template>
  <exchange-settings-drawer ref="exchangeSettingsDrawer" />
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
    }
  }
};
</script>