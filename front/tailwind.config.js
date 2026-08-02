/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{vue,js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        brand: {
          DEFAULT: '#19bfae',
          hover: '#31cdbc',
          dark: '#109b8f',
          light: '#dff6f1',
        },
        sidebar: '#fffdf6',
        ui: {
          bg: '#f7f4e8',
          panel: '#fffdf6',
          card: '#fffdf6',
          soft: '#f3eddb',
          border: '#ddd2b9',
          text: '#4f3b2b',
          body: '#725d42',
          subtext: '#8f806d',
          success: '#69ad38',
          warning: '#e6ab20',
          error: '#d95656',
          info: '#6f91c8',
        },
      },
      fontFamily: {
        sans: ['Nunito', 'Noto Sans SC', 'PingFang SC', 'Microsoft YaHei', 'sans-serif'],
        mono: ['SFMono-Regular', 'Consolas', 'Liberation Mono', 'Menlo', 'monospace'],
      },
      borderRadius: {
        'app-control': '14px',
        'app-card': '20px',
        'app-page': '24px',
      },
      boxShadow: {
        'app-panel': '0 16px 40px -30px rgba(79, 59, 43, 0.36)',
        'app-soft': '0 10px 26px -24px rgba(79, 59, 43, 0.3)',
        'app-primary': '0 4px 0 #0f8f84',
      },
    },
  },
  plugins: [],
}
