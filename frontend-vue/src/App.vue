<script setup lang="ts">
import { computed } from 'vue'
import { NConfigProvider, NDialogProvider, NMessageProvider, zhCN, dateZhCN } from 'naive-ui'

const themeOverrides = computed(() => ({
  common: {
    primaryColor: '#1877c9',
    primaryColorHover: '#166fbf',
    primaryColorPressed: '#135fa6',
    primaryColorSuppl: '#166fbf',
    infoColor: '#1d9bf0',
    successColor: '#047857',
    warningColor: '#c2410c',
    errorColor: '#d9272e',
    borderRadius: '8px',
    borderRadiusSmall: '6px',
    borderColor: '#eff3f4',
    dividerColor: '#eff3f4',
    fontFamily:
      '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif',
    textColorBase: '#0f1419',
    textColor1: '#0f1419',
    textColor2: '#536471',
    textColor3: '#6b7885',
    bodyColor: '#ffffff',
    cardColor: '#ffffff',
    modalColor: '#ffffff',
    popoverColor: '#ffffff',
    tableColor: '#ffffff',
    hoverColor: '#f7f9f9',
    boxShadow2: '0 8px 24px rgba(16, 24, 40, 0.08), 0 2px 6px rgba(16, 24, 40, 0.04)',
  },
  Layout: {
    color: 'transparent',
    siderColor: '#ffffff',
    headerColor: 'transparent',
    siderBorderColor: '#eff3f4',
    headerBorderColor: '#eff3f4',
  },
  Card: {
    color: '#ffffff',
    borderColor: '#eff3f4',
    borderRadius: '16px',
    titleFontSizeMedium: '17px',
    titleFontWeight: '800',
    titleTextColor: '#0f1419',
    textColor: '#536471',
  },
  Button: {
    fontWeight: '700',
    border: '1px solid #cfd9de',
    borderHover: '1px solid #cfd9de',
    borderPressed: '1px solid #cfd9de',
    borderFocus: '1px solid #cfd9de',
    textColor: '#0f1419',
    textColorHover: '#0f1419',
    textColorPressed: '#0f1419',
    textColorFocus: '#0f1419',
    colorHover: '#e7e9ea',
    colorPressed: '#d7dbdc',
    colorFocus: '#ffffff',
    borderRadiusMedium: '9999px',
    borderRadiusSmall: '9999px',
    textColorPrimary: '#ffffff',
    textColorHoverPrimary: '#ffffff',
    textColorPressedPrimary: '#ffffff',
    textColorFocusPrimary: '#ffffff',
  },
  Input: {
    borderRadius: '8px',
  },
  Select: {
    peers: {
      InternalSelection: {
        borderRadius: '8px',
      },
    },
  },
  Menu: {
    borderRadius: '9999px',
    itemHeight: '44px',
    itemColorActive: 'rgba(0, 0, 0, 0)',
    itemColorActiveHover: '#eff3f4',
    itemTextColor: '#0f1419',
    itemIconColor: '#536471',
    itemTextColorActive: '#0f1419',
    itemTextColorActiveHover: '#0f1419',
    itemIconColorActive: '#1d9bf0',
    itemIconColorActiveHover: '#1d9bf0',
    itemTextColorHover: '#0f1419',
    itemIconColorHover: '#0f1419',
    itemColorHover: '#eff3f4',
  },
  DataTable: {
    thColor: '#ffffff',
    thColorHover: '#f7f9f9',
    tdColor: '#ffffff',
    tdColorHover: '#f7f9f9',
    borderColor: '#eff3f4',
    thTextColor: '#536471',
    tdTextColor: '#0f1419',
    thFontWeight: '700',
    borderRadius: '12px',
  },
  Tag: {
    borderRadius: '6px',
  },
  Drawer: {
    color: '#ffffff',
  },
  Tooltip: {
    borderRadius: '6px',
  },
}))
</script>

<template>
  <n-config-provider :locale="zhCN" :date-locale="dateZhCN" :theme-overrides="themeOverrides">
    <n-message-provider>
      <n-dialog-provider>
        <router-view />
      </n-dialog-provider>
    </n-message-provider>
  </n-config-provider>
</template>
