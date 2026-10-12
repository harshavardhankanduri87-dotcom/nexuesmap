import type { Config } from "tailwindcss";

const config: Config = {
  content: [
    "./src/pages/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/components/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/app/**/*.{js,ts,jsx,tsx,mdx}",
  ],
  darkMode: "class",
  theme: {
    extend: {
      colors: {
        nexus: {
          50: "#e6fff9",
          100: "#b3ffe8",
          200: "#80ffd7",
          300: "#4dffc6",
          400: "#1affb5",
          500: "#00e69d",
          600: "#00b37a",
          700: "#008057",
          800: "#004d35",
          900: "#001a12",
        },
        cyber: {
          dark: "#0b0f19",
          panel: "#111827",
          card: "#182234",
          border: "#1f293d",
          neon: "#00f0ff",
        },
      },
      backgroundImage: {
        "radial-grid": "radial-gradient(circle, rgba(0,240,255,0.08) 1px, transparent 1px)",
        "gradient-radial": "radial-gradient(var(--tw-gradient-stops))",
      },
      animation: {
        "pulse-slow": "pulse 3s cubic-bezier(0.4, 0, 0.6, 1) infinite",
        "ping-slow": "ping 2.5s cubic-bezier(0, 0, 0.2, 1) infinite",
      },
    },
  },
  plugins: [],
};

export default config;
