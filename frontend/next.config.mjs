/** @type {import('next').NextConfig} */
const backendHost = process.env.BACKEND_INTERNAL_URL || "http://localhost:8080";

const nextConfig = {
  reactStrictMode: true,
  output: "standalone",
  // Proxy /api requests to Spring Boot backend to enable seamless HttpOnly cookie handling
  async rewrites() {
    return [
      {
        source: "/api/:path*",
        destination: `${backendHost}/api/:path*`,
      },
      {
        source: "/ws/:path*",
        destination: `${backendHost}/ws/:path*`,
      },
      {
        source: "/ws",
        destination: `${backendHost}/ws`,
      },
    ];
  },
};

export default nextConfig;
