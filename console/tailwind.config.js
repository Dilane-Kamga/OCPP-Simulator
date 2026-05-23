/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        nextower: {
          bg1: '#1a0d2e',
          bg2: '#2d1b4e',
          accent: '#e63946',
        },
        nexteracom: {
          bg: '#f5f5f5',
          text: '#1f2937',
          accent: '#0072ce',
        },
      },
      fontFamily: {
        mono: ['ui-monospace', 'SFMono-Regular', 'Menlo', 'monospace'],
      },
    },
  },
  plugins: [],
};
