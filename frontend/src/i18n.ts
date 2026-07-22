import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'

// Full bilingual coverage (EN / বাংলা) is a project-wide decision: every user-facing
// string flows through i18next, and an English-only string is treated as a bug. The
// chosen language is remembered across reloads.

const STORAGE_KEY = 'dms.lang'
export const LANGUAGES = ['en', 'bn'] as const
export type Language = (typeof LANGUAGES)[number]

const resources = {
  en: {
    translation: {
      appName: 'Disaster Management System',
      appNameShort: 'DMS',
      // Label shows the language you would switch TO.
      switchLanguage: 'বাংলা',
      languageName: 'English',

      login: {
        eyebrow: 'Jamuna Flood Response',
        title: 'Sign in to the operation',
        subtitle: 'One system, seven roles, one live picture of the disaster.',
        username: 'Username',
        password: 'Password',
        submit: 'Sign in',
        signingIn: 'Signing in…',
        error: 'That username and password did not match. Try again.',
        demoTitle: 'Demo accounts',
        demoHint: 'Every account uses the password below. Tap a role to fill it in.',
        demoPassword: 'Password for all demo accounts',
      },

      ribbon: {
        disaster: 'Jamuna Flood',
        region: 'Kurigram · Gaibandha',
        demo: 'DEMO',
        live: 'Live',
        clockLabel: 'Operation clock',
      },

      nav: {
        overview: 'Overview',
        camps: 'Camps',
        alerts: 'Alerts',
        resources: 'Resources',
        people: 'People',
        soon: 'Soon',
      },

      shell: {
        signedInAs: 'Signed in as',
        logout: 'Sign out',
        greeting: 'Welcome, {{name}}',
        placeholderTitle: 'Your workspace is coming online',
        placeholderBody:
          'This is the {{role}} workspace. The live map, data, and tools for this role arrive in the next slice — the sign-in, roles, and bilingual shell around them are working now.',
        primaryAction: 'Continue',
      },

      roles: {
        coordinator: 'Relief Coordinator',
        camp_manager: 'Camp Manager',
        donor: 'Donor',
        volunteer: 'Volunteer',
        victim: 'Victim / Family',
        ngo: 'NGO Partner',
        admin: 'Administrator',
      },

      roleBlurb: {
        coordinator: 'Oversee the whole operation — camps, resources, and alerts across the disaster.',
        camp_manager: 'Focus on the people and supplies in the camps you run.',
        donor: 'Follow your contribution from fund to camp, with nothing sensitive exposed.',
        volunteer: 'Pick up shifts and assignments from the field, on your phone.',
        victim: 'Register your family and check your shelter status under one calm view.',
        ngo: "Coordinate your organization's part of the response.",
        admin: 'Manage the system, its users, and the simulated world.',
      },

      map: {
        regionLabel: 'Live operations map',
        mapLabel: 'Map of relief camps',
        legend: 'Disasters',
        loading: 'Loading the live world…',
        error: 'Could not load the world. Check your connection and try again.',
        sheltered: 'Sheltered',
        campsCount: '{{n}} open camps',
        shelteredCount: '{{n}} sheltered',
        status: { open: 'Open', closed: 'Closed' },
        disasterStatus: { active: 'Active', stable: 'Stable' },
      },

      camp: {
        mine: 'Your camp',
        occupancy: 'sheltered',
        overCapacity: 'over capacity',
        resource: { WATER: 'Water', FOOD: 'Food', MEDICAL: 'Medical', SHELTER: 'Shelter' },
      },

      sim: {
        title: 'Simulation control',
        subtitle: 'Drives the scripted Jamuna scenario. Demo only.',
        open: 'Open simulation control',
        close: 'Close',
        pause: 'Pause',
        resume: 'Resume',
        reset: 'Reset to start',
        speed: 'Speed',
        tick: 'Tick',
        tickOf: 'Tick {{tick}} of {{total}}',
        simTime: 'Simulated time',
        running: 'Running',
        paused: 'Paused',
        ended: 'Scenario complete',
        storyline: 'Storyline',
        restricted: 'Only a Coordinator or Admin can drive the simulation.',
        failed: 'That control did not take effect. Try again.',
        phase: {
          SURGE: 'Surge',
          NEW_CAMP: 'Overflow camp opens',
          RELIEF_CONVOY: 'Relief convoy',
          RECOVERY: 'Recovery',
        },
      },

      locator: {
        title: 'Find a shelter',
        subtitle: 'Open relief camps across the current disasters. No account needed.',
        search: 'Search by camp name',
        signIn: 'Staff sign in',
        empty: 'No camps match that name.',
        error: 'Could not load camps right now. Please try again.',
        mapLabel: 'Map of relief camps',
      },

      alertLifecycle: {
        title: 'Alerts',
        empty: 'No alerts right now.',
        raise: 'Raise alert',
        demo: 'Demo alert',
        description: 'Description',
        submit: 'Submit',
        cancel: 'Cancel',
        camp: 'Camp',
        type: {
          RESOURCE_SHORTAGE: 'Resource shortage',
          MEDICAL_EMERGENCY: 'Medical emergency',
          SECURITY_INCIDENT: 'Security incident',
          INFRASTRUCTURE_DAMAGE: 'Infrastructure damage',
        },
        status: {
          NEW: 'New',
          ACKNOWLEDGED: 'Acknowledged',
          IN_PROGRESS: 'In progress',
          RESOLVED: 'Resolved',
          ESCALATED: 'Escalated',
          CLOSED: 'Closed',
        },
        action: {
          ACKNOWLEDGED: 'Acknowledge',
          IN_PROGRESS: 'Start work',
          RESOLVED: 'Resolve',
          ESCALATED: 'Escalate',
          CLOSED: 'Close',
        },
        notes: 'Case notes',
        addNote: 'Add a note',
        timeline: 'Timeline',
        systemActor: 'System (SLA)',
        slaBreached: 'SLA breached',
      },
    },
  },
  bn: {
    translation: {
      appName: 'দুর্যোগ ব্যবস্থাপনা সিস্টেম',
      appNameShort: 'ডিএমএস',
      switchLanguage: 'English',
      languageName: 'বাংলা',

      login: {
        eyebrow: 'যমুনা বন্যা সাড়াদান',
        title: 'অপারেশনে সাইন ইন করুন',
        subtitle: 'একটি সিস্টেম, সাতটি ভূমিকা, দুর্যোগের একটিই সরাসরি চিত্র।',
        username: 'ব্যবহারকারীর নাম',
        password: 'পাসওয়ার্ড',
        submit: 'সাইন ইন',
        signingIn: 'সাইন ইন হচ্ছে…',
        error: 'ব্যবহারকারীর নাম ও পাসওয়ার্ড মেলেনি। আবার চেষ্টা করুন।',
        demoTitle: 'ডেমো অ্যাকাউন্ট',
        demoHint: 'প্রতিটি অ্যাকাউন্টে নিচের পাসওয়ার্ডটি ব্যবহার হয়। ভূমিকা চাপলে তা বসে যাবে।',
        demoPassword: 'সব ডেমো অ্যাকাউন্টের পাসওয়ার্ড',
      },

      ribbon: {
        disaster: 'যমুনা বন্যা',
        region: 'কুড়িগ্রাম · গাইবান্ধা',
        demo: 'ডেমো',
        live: 'সরাসরি',
        clockLabel: 'অপারেশন ঘড়ি',
      },

      nav: {
        overview: 'সারসংক্ষেপ',
        camps: 'ক্যাম্প',
        alerts: 'সতর্কতা',
        resources: 'সম্পদ',
        people: 'মানুষ',
        soon: 'শীঘ্রই',
      },

      shell: {
        signedInAs: 'সাইন ইন করেছেন',
        logout: 'সাইন আউট',
        greeting: 'স্বাগতম, {{name}}',
        placeholderTitle: 'আপনার ওয়ার্কস্পেস প্রস্তুত হচ্ছে',
        placeholderBody:
          'এটি {{role}} ওয়ার্কস্পেস। এই ভূমিকার সরাসরি মানচিত্র, তথ্য ও সরঞ্জাম পরবর্তী ধাপে আসছে — চারপাশের সাইন-ইন, ভূমিকা ও দ্বিভাষিক শেল এখনই কাজ করছে।',
        primaryAction: 'এগিয়ে যান',
      },

      roles: {
        coordinator: 'ত্রাণ সমন্বয়কারী',
        camp_manager: 'ক্যাম্প ব্যবস্থাপক',
        donor: 'দাতা',
        volunteer: 'স্বেচ্ছাসেবক',
        victim: 'ভুক্তভোগী / পরিবার',
        ngo: 'এনজিও অংশীদার',
        admin: 'প্রশাসক',
      },

      roleBlurb: {
        coordinator: 'পুরো অপারেশন তদারকি করুন — দুর্যোগজুড়ে ক্যাম্প, সম্পদ ও সতর্কতা।',
        camp_manager: 'আপনার পরিচালিত ক্যাম্পের মানুষ ও সরবরাহে মনোযোগ দিন।',
        donor: 'কোনো সংবেদনশীল তথ্য প্রকাশ ছাড়াই তহবিল থেকে ক্যাম্প পর্যন্ত আপনার অবদান অনুসরণ করুন।',
        volunteer: 'মাঠ থেকে, ফোনেই শিফট ও দায়িত্ব গ্রহণ করুন।',
        victim: 'একটি শান্ত পর্দায় আপনার পরিবার নিবন্ধন করুন ও আশ্রয়ের অবস্থা দেখুন।',
        ngo: 'সাড়াদানে আপনার সংস্থার অংশ সমন্বয় করুন।',
        admin: 'সিস্টেম, ব্যবহারকারী ও সিমুলেটেড বিশ্ব পরিচালনা করুন।',
      },

      map: {
        regionLabel: 'সরাসরি অপারেশন মানচিত্র',
        mapLabel: 'ত্রাণ ক্যাম্পের মানচিত্র',
        legend: 'দুর্যোগ',
        loading: 'সরাসরি চিত্র লোড হচ্ছে…',
        error: 'চিত্র লোড করা যায়নি। সংযোগ পরীক্ষা করে আবার চেষ্টা করুন।',
        sheltered: 'আশ্রিত',
        campsCount: '{{n}}টি খোলা ক্যাম্প',
        shelteredCount: '{{n}} জন আশ্রিত',
        status: { open: 'খোলা', closed: 'বন্ধ' },
        disasterStatus: { active: 'সক্রিয়', stable: 'স্থিতিশীল' },
      },

      camp: {
        mine: 'আপনার ক্যাম্প',
        occupancy: 'আশ্রিত',
        overCapacity: 'ধারণক্ষমতার বেশি',
        resource: { WATER: 'পানি', FOOD: 'খাবার', MEDICAL: 'চিকিৎসা', SHELTER: 'আশ্রয়' },
      },

      sim: {
        title: 'সিমুলেশন নিয়ন্ত্রণ',
        subtitle: 'নির্ধারিত যমুনা দৃশ্যপট পরিচালনা করে। শুধুমাত্র ডেমো।',
        open: 'সিমুলেশন নিয়ন্ত্রণ খুলুন',
        close: 'বন্ধ করুন',
        pause: 'বিরতি',
        resume: 'চালু করুন',
        reset: 'শুরুতে ফিরুন',
        speed: 'গতি',
        tick: 'ধাপ',
        tickOf: '{{total}}টির মধ্যে {{tick}} নম্বর ধাপ',
        simTime: 'অনুকরণীয় সময়',
        running: 'চলছে',
        paused: 'থেমে আছে',
        ended: 'দৃশ্যপট সম্পন্ন',
        storyline: 'ঘটনাক্রম',
        restricted: 'শুধুমাত্র সমন্বয়কারী বা প্রশাসক সিমুলেশন পরিচালনা করতে পারেন।',
        failed: 'নিয়ন্ত্রণটি কার্যকর হয়নি। আবার চেষ্টা করুন।',
        phase: {
          SURGE: 'জলোচ্ছ্বাস',
          NEW_CAMP: 'অতিরিক্ত ক্যাম্প চালু',
          RELIEF_CONVOY: 'ত্রাণ বহর',
          RECOVERY: 'পুনরুদ্ধার',
        },
      },

      locator: {
        title: 'আশ্রয়কেন্দ্র খুঁজুন',
        subtitle: 'চলমান দুর্যোগের খোলা ত্রাণ ক্যাম্পসমূহ। কোনো অ্যাকাউন্ট লাগবে না।',
        search: 'ক্যাম্পের নাম দিয়ে খুঁজুন',
        signIn: 'কর্মী সাইন ইন',
        empty: 'ঐ নামের কোনো ক্যাম্প পাওয়া যায়নি।',
        error: 'এই মুহূর্তে ক্যাম্প লোড করা যায়নি। আবার চেষ্টা করুন।',
        mapLabel: 'ত্রাণ ক্যাম্পের মানচিত্র',
      },

      alertLifecycle: {
        title: 'সতর্কতা',
        empty: 'এই মুহূর্তে কোনো সতর্কতা নেই।',
        raise: 'সতর্কতা তৈরি করুন',
        demo: 'ডেমো সতর্কতা',
        description: 'বিবরণ',
        submit: 'জমা দিন',
        cancel: 'বাতিল',
        camp: 'ক্যাম্প',
        type: {
          RESOURCE_SHORTAGE: 'সম্পদ ঘাটতি',
          MEDICAL_EMERGENCY: 'চিকিৎসা জরুরি অবস্থা',
          SECURITY_INCIDENT: 'নিরাপত্তা ঘটনা',
          INFRASTRUCTURE_DAMAGE: 'অবকাঠামো ক্ষতি',
        },
        status: {
          NEW: 'নতুন',
          ACKNOWLEDGED: 'স্বীকৃত',
          IN_PROGRESS: 'চলমান',
          RESOLVED: 'সমাধান হয়েছে',
          ESCALATED: 'উর্ধ্বতনে প্রেরিত',
          CLOSED: 'বন্ধ',
        },
        action: {
          ACKNOWLEDGED: 'স্বীকার করুন',
          IN_PROGRESS: 'কাজ শুরু করুন',
          RESOLVED: 'সমাধান করুন',
          ESCALATED: 'উর্ধ্বতনে পাঠান',
          CLOSED: 'বন্ধ করুন',
        },
        notes: 'কেস নোট',
        addNote: 'নোট যোগ করুন',
        timeline: 'সময়রেখা',
        systemActor: 'সিস্টেম (এসএলএ)',
        slaBreached: 'এসএলএ লঙ্ঘিত হয়েছে',
      },
    },
  },
}

function initialLanguage(): Language {
  const stored = localStorage.getItem(STORAGE_KEY)
  return stored === 'en' || stored === 'bn' ? stored : 'en'
}

i18n.use(initReactI18next).init({
  resources,
  lng: initialLanguage(),
  fallbackLng: 'en',
  interpolation: { escapeValue: false },
})

function applyLanguage(lng: string) {
  document.documentElement.lang = lng
}

// Persist the choice and reflect it on <html lang> so screen readers and the
// :lang() typography rules pick the right script.
i18n.on('languageChanged', (lng) => {
  localStorage.setItem(STORAGE_KEY, lng)
  applyLanguage(lng)
})
applyLanguage(i18n.language)

export default i18n
