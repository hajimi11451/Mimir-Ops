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
          DEFAULT: '#4299e1', // 2.1 品牌蓝
          hover: '#3182ce',   // 2.4 悬停色
          dark: '#1a2530',    // 2.1 深蓝灰
          light: '#90cdf4',   // 2.2 浅蓝
        },
        sidebar: '#2d3748', // 2.1 侧边栏蓝灰
        ui: {
          bg: '#f7fafc',      // 2.3 背景浅灰
          card: '#ffffff',    // 2.3 内容卡片
          border: '#e2e8f0',  // 2.3 边框灰
          text: '#2d3748',    // 2.3 主要文字
          subtext: '#718096', // 2.3 次要文字
          success: '#48bb78', // 2.2 成功绿
          warning: '#ed8936', // 2.2 高负载/警告
          error: '#f56565'    // 2.2 故障/错误
        }
      },
      fontFamily: {
        sans: ['PingFang SC', 'Microsoft YaHei', 'Segoe UI', 'sans-serif'], // 4.1 字体族
      }
    },
  },
  plugins: [],
}
