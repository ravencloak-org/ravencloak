#!/usr/bin/env node

const puppeteer = require("puppeteer");

const url = process.argv[2];

if (!url) {
  console.error("Usage: plane-export <url>");
  process.exit(1);
}

(async () => {
  const browser = await puppeteer.launch({
    headless: false, // optional (debug)
    userDataDir: "/Users/jobinlawrance/Library/Application Support/Google/Chrome", // 👈 your Chrome profile
  });

  const page = await browser.newPage();

  console.log("Opening page...");
  await page.goto(url, { waitUntil: "networkidle0" });

  const currentUrl = page.url();
  console.log("Current URL:", currentUrl);

  if (currentUrl.includes("login")) {
    console.error("❌ Not logged in in this Chrome profile");
    process.exit(1);
  }

  console.log("Rendering PDF...");
  await page.pdf({
    path: "output.pdf",
    format: "A4",
    printBackground: true,
  });

  await browser.close();
  console.log("✅ Exported successfully");
})();
