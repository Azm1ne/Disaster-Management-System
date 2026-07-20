import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'

// Full bilingual coverage (EN / বাংলা) is a project-wide decision; every user-facing
// string flows through i18next. This seeds the scaffold with the shell strings only.
const resources = {
  en: {
    translation: {
      appName: 'Disaster Management System',
      tagline: 'Coordinated relief across concurrent disasters',
      systemStatus: 'System status',
      apiHealthy: 'API healthy',
      apiUnreachable: 'API unreachable',
      checking: 'Checking…',
      language: 'বাংলা',
    },
  },
  bn: {
    translation: {
      appName: 'দুর্যোগ ব্যবস্থাপনা সিস্টেম',
      tagline: 'একযোগে দুর্যোগে সমন্বিত ত্রাণ',
      systemStatus: 'সিস্টেমের অবস্থা',
      apiHealthy: 'এপিআই সচল',
      apiUnreachable: 'এপিআই সংযোগহীন',
      checking: 'পরীক্ষা করা হচ্ছে…',
      language: 'English',
    },
  },
}

i18n.use(initReactI18next).init({
  resources,
  lng: 'en',
  fallbackLng: 'en',
  interpolation: { escapeValue: false },
})

export default i18n
